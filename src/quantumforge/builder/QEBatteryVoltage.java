/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Open-circuit voltage profile of a single-valence (or explicitly z-valent)
 * ion-insertion electrode from a binary convex hull (Roadmap #155).
 *
 * <p>Given formation energies per atom {@code E_form(x)} at fractions of the
 * inserted species B in [0,1], the two-phase plateau between adjacent hull
 * vertices {@code (x1,E1) -> (x2,E2)} is
 * {@code V = -(E2 - E1 - (x2-x1)*mu_B) / (z*(x2-x1))}, with the metal
 * chemical potential {@code mu_B = E_form(1)}. Only vertices of the lower
 * convex hull (Monotone Chain, same construction as the stability analysis)
 * produce plateaus; metastable phases above the hull never do. Values are 0 K
 * DFT formation-energy plateaus: temperature, volume, entropy, and overpotential
 * corrections are explicitly out of scope and stated as such.</p>
 */
public final class QEBatteryVoltage {

    private static final double COLLINEAR_TOLERANCE = 1.0e-9;
    private static final double REFERENCE_WARN_TOLERANCE = 1.0e-4;

    /** One lower-hull vertex surviving the Monotone Chain construction. */
    public static final class HullVertex {
        private final String formula;
        private final double fractionB;
        private final double formationEnergyEv;

        HullVertex(String formula, double fractionB, double formationEnergyEv) {
            this.formula = formula;
            this.fractionB = fractionB;
            this.formationEnergyEv = formationEnergyEv;
        }

        public String getFormula() { return this.formula; }
        public double getFractionB() { return this.fractionB; }
        public double getFormationEnergyEv() { return this.formationEnergyEv; }
    }

    /** One two-phase voltage plateau between adjacent hull vertices. */
    public static final class Plateau {
        private final HullVertex left;
        private final HullVertex right;
        private final double voltageV;

        Plateau(HullVertex left, HullVertex right, double voltageV) {
            this.left = left;
            this.right = right;
            this.voltageV = voltageV;
        }

        public HullVertex getLeft() { return this.left; }
        public HullVertex getRight() { return this.right; }
        public double getVoltageV() { return this.voltageV; }
    }

    /** The derived profile: surviving vertices, plateaus, and assumption notes. */
    public static final class VoltageProfile {
        private final double ionCharge;
        private final List<HullVertex> vertices;
        private final List<Plateau> plateaus;
        private final List<String> notes = new ArrayList<>();

        VoltageProfile(double ionCharge, List<HullVertex> vertices, List<Plateau> plateaus) {
            this.ionCharge = ionCharge;
            this.vertices = List.copyOf(vertices);
            this.plateaus = List.copyOf(plateaus);
        }

        public double getIonCharge() { return this.ionCharge; }
        public List<HullVertex> getVertices() { return this.vertices; }
        public List<Plateau> getPlateaus() { return this.plateaus; }
        public List<String> getNotes() { return List.copyOf(this.notes); }

        void addNote(String note) { this.notes.add(note); }
    }

    private QEBatteryVoltage() {
        // Utility.
    }

    /**
     * Builds the voltage profile from formation-energy records.
     *
     * @param phases    competing phases; must include the host (fraction 0) and
     *                  the metal reference (fraction 1); duplicates at one
     *                  fraction keep the lowest formation energy only
     * @param ionCharge insertion charge z (1 for Li/Na/K, ~2 for Mg/Zn)
     */
    public static OperationResult<VoltageProfile> build(
            List<QEHullThermodynamics.CompetingPhase> phases, double ionCharge) {
        if (phases == null || phases.size() < 3) {
            return OperationResult.failed("BATTERY_PHASES",
                    "A voltage profile needs at least three competing phases including both "
                            + "endmembers (got " + (phases == null ? 0 : phases.size()) + ").", null);
        }
        if (!(ionCharge > 0.0) || !Double.isFinite(ionCharge)) {
            return OperationResult.failed("BATTERY_Z",
                    "The insertion charge z must be a positive finite value (got " + ionCharge
                            + ").", null);
        }
        List<QEHullThermodynamics.CompetingPhase> sorted = new ArrayList<>(phases);
        for (QEHullThermodynamics.CompetingPhase phase : sorted) {
            if (!Double.isFinite(phase.getFormationEnergyEv())
                    || !Double.isFinite(phase.getFractionB())) {
                return OperationResult.failed("BATTERY_NAN",
                        "Composition fractions and formation energies must be finite.", null);
            }
            if (phase.getFractionB() < 0.0 || phase.getFractionB() > 1.0) {
                return OperationResult.failed("BATTERY_RANGE",
                        "Composition " + phase.getFormula() + " has fraction "
                                + phase.getFractionB() + " outside [0,1].", null);
            }
        }
        sorted.sort(Comparator.comparingDouble(QEHullThermodynamics.CompetingPhase::getFractionB));
        List<QEHullThermodynamics.CompetingPhase> dedup = new ArrayList<>();
        for (QEHullThermodynamics.CompetingPhase phase : sorted) {
            if (!dedup.isEmpty() && Math.abs(dedup.get(dedup.size() - 1).getFractionB()
                    - phase.getFractionB()) < 1.0e-12) {
                QEHullThermodynamics.CompetingPhase kept = dedup.get(dedup.size() - 1);
                if (phase.getFormationEnergyEv() < kept.getFormationEnergyEv()) {
                    dedup.set(dedup.size() - 1, phase);
                }
            } else {
                dedup.add(phase);
            }
        }
        if (Math.abs(dedup.get(0).getFractionB()) > 1.0e-12) {
            return OperationResult.failed("BATTERY_HOST",
                    "The host endmember (fraction 0) is missing from the phase list.", null);
        }
        if (Math.abs(dedup.get(dedup.size() - 1).getFractionB() - 1.0) > 1.0e-12) {
            return OperationResult.failed("BATTERY_REFERENCE",
                    "The pure-metal reference endmember (fraction 1) is missing; the "
                            + "chemical potential anchor cannot be established.", null);
        }

        // Monotone Chain lower hull, identical tolerance to the stability analysis.
        List<QEHullThermodynamics.CompetingPhase> lower = new ArrayList<>();
        for (QEHullThermodynamics.CompetingPhase phase : dedup) {
            while (lower.size() >= 2) {
                QEHullThermodynamics.CompetingPhase p1 = lower.get(lower.size() - 2);
                QEHullThermodynamics.CompetingPhase p2 = lower.get(lower.size() - 1);
                double cross = (p2.getFractionB() - p1.getFractionB())
                        * (phase.getFormationEnergyEv() - p1.getFormationEnergyEv())
                        - (p2.getFormationEnergyEv() - p1.getFormationEnergyEv())
                        * (phase.getFractionB() - p1.getFractionB());
                if (cross <= COLLINEAR_TOLERANCE) {
                    lower.remove(lower.size() - 1);
                } else {
                    break;
                }
            }
            lower.add(phase);
        }

        List<HullVertex> vertices = new ArrayList<>();
        for (QEHullThermodynamics.CompetingPhase phase : lower) {
            vertices.add(new HullVertex(phase.getFormula(), phase.getFractionB(),
                    phase.getFormationEnergyEv()));
        }
        double muB = lower.get(lower.size() - 1).getFormationEnergyEv();

        List<Plateau> plateaus = new ArrayList<>();
        boolean negative = false;
        for (int i = 1; i < vertices.size(); i++) {
            HullVertex left = vertices.get(i - 1);
            HullVertex right = vertices.get(i);
            double dx = right.getFractionB() - left.getFractionB();
            if (!(dx > 0.0)) {
                continue;
            }
            double voltage = -(right.getFormationEnergyEv() - left.getFormationEnergyEv()
                    - dx * muB) / (ionCharge * dx);
            negative |= voltage < 0.0;
            plateaus.add(new Plateau(left, right, voltage));
        }
        if (plateaus.isEmpty()) {
            return OperationResult.failed("BATTERY_PLATEAU",
                    "No hull interval spans a positive composition step in this phase list.",
                    null);
        }

        VoltageProfile profile = new VoltageProfile(ionCharge, vertices, plateaus);
        if (Math.abs(muB) > REFERENCE_WARN_TOLERANCE) {
            profile.addNote(String.format(Locale.ROOT,
                    "The fraction-1 reference carries formation energy %.6f eV/atom; it was "
                    + "used as mu_B. If your formation energies are not referenced to the pure "
                    + "metal, re-reference them before trusting absolute voltages.", muB));
        }
        if (negative) {
            profile.addNote("At least one plateau voltage is negative; this indicates a "
                    + "reference inconsistency (wrong mu_B, or a metastable 'reference' "
                    + "phase) and is reported as-is, never clipped.");
        }
        profile.addNote("0 K DFT formation-energy plateaus: temperature, vibrational entropy, "
                + "volume change, and overpotentials are outside this analysis (per-ion "
                + "per-atom eV becomes volts only via the stated single-ion charge z).");
        return OperationResult.success("BATTERY_OK", "Voltage profile built.", profile);
    }
}
