/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Implements rigorous binary convex hull thermodynamic stability analysis, calculating
 * chemical-potential-referenced formation energies per atom, linear tie-line interpolations,
 * and stable decomposition energy distances (Roadmap #151).
 */
public final class QEHullThermodynamics {

    public static final class CompetingPhase {
        private final String formula;
        private final double fractionB;      // Fraction of element B (0.0 to 1.0)
        private final double formationEnergyEv; // eV/atom referenced to pure elements

        public CompetingPhase(String formula, double fractionB, double formationEnergyEv) {
            this.formula = Objects.requireNonNull(formula, "formula").trim();
            if (fractionB < 0.0 || fractionB > 1.0) {
                throw new IllegalArgumentException("fractionB must be between 0.0 and 1.0");
            }
            this.fractionB = fractionB;
            this.formationEnergyEv = formationEnergyEv;
        }

        public String getFormula() { return this.formula; }
        public double getFractionB() { return this.fractionB; }
        public double getFormationEnergyEv() { return this.formationEnergyEv; }
    }

    public static final class StabilityResult {
        private final boolean stable;
        private final double distanceToHullEv; // Energy above hull (eV/atom)
        private final String decompositionProducts;

        public StabilityResult(boolean stable, double dist, String products) {
            this.stable = stable;
            this.distanceToHullEv = Math.max(0.0, dist);
            this.decompositionProducts = products == null ? "" : products.trim();
        }

        public boolean isStable() { return this.stable; }
        public double getDistanceToHullEv() { return this.distanceToHullEv; }
        public String getDecompositionProducts() { return this.decompositionProducts; }

        public String getSummary() {
            if (stable) {
                return "Phase is thermodynamically stable (On the Convex Hull).";
            } else {
                return String.format(Locale.ROOT,
                    "Phase is thermodynamically metastable (Above the Convex Hull by %.4f eV/atom).\n" +
                    " - Decomposition path: -> %s",
                    distanceToHullEv, decompositionProducts);
            }
        }
    }

    private final List<CompetingPhase> phases = new ArrayList<>();

    public QEHullThermodynamics() {
        // Constructor
    }

    public void addPhase(String formula, double fractionB, double formationEnergyEv) {
        this.phases.add(new CompetingPhase(formula, fractionB, formationEnergyEv));
    }

    public List<CompetingPhase> getPhases() { return List.copyOf(this.phases); }

    /**
     * Constructs the convex hull and evaluates the stability of a target composition.
     */
    public StabilityResult evaluateStability(double targetFraction, double targetFormationEnergyEv) {
        if (targetFraction < 0.0 || targetFraction > 1.0) {
            throw new IllegalArgumentException("Target fraction must be between 0.0 and 1.0");
        }

        // Add the target as a candidate to construct the complete hull
        List<CompetingPhase> candidates = new ArrayList<>(this.phases);
        CompetingPhase target = new CompetingPhase("Target", targetFraction, targetFormationEnergyEv);
        candidates.add(target);

        // Sort candidates by fraction of element B
        candidates.sort(Comparator.comparingDouble(CompetingPhase::getFractionB));

        // 1. Construct the lower envelope (Convex Hull) using the Monotone Chain algorithm (Gift Wrapping style)
        List<CompetingPhase> hull = new ArrayList<>();
        for (CompetingPhase p : candidates) {
            while (hull.size() >= 2) {
                CompetingPhase p1 = hull.get(hull.size() - 2);
                CompetingPhase p2 = hull.get(hull.size() - 1);
                // Compute cross product: (p2.x - p1.x)*(p.y - p1.y) - (p2.y - p1.y)*(p.x - p1.x)
                double cross = (p2.fractionB - p1.fractionB) * (p.formationEnergyEv - p1.formationEnergyEv)
                             - (p2.formationEnergyEv - p1.formationEnergyEv) * (p.fractionB - p1.fractionB);
                if (cross <= 1.0e-9) { // Left turn or collinear -> pop
                    hull.remove(hull.size() - 1);
                } else {
                    break;
                }
            }
            hull.add(p);
        }

        // 2. Check if the target is on the constructed hull
        boolean isOnHull = false;
        for (CompetingPhase hp : hull) {
            if (hp == target) {
                isOnHull = true;
                break;
            }
        }

        if (isOnHull) {
            return new StabilityResult(true, 0.0, "Stable phase.");
        }

        // 3. If above the hull, find the two bracketing stable phases on the hull to interpolate the decomposition line
        CompetingPhase left = null;
        CompetingPhase right = null;

        for (int i = 0; i < hull.size(); i++) {
            CompetingPhase hp = hull.get(i);
            if (hp.fractionB <= targetFraction) {
                left = hp;
            }
            if (hp.fractionB >= targetFraction && right == null) {
                right = hp;
            }
        }

        if (left == null || right == null || left == right) {
            return new StabilityResult(false, 0.0, "Error during tie-line interpolation.");
        }

        // Linear interpolation of the tie-line
        double dx = right.fractionB - left.fractionB;
        double dy = right.formationEnergyEv - left.formationEnergyEv;
        double hullEnergy = left.formationEnergyEv + (targetFraction - left.fractionB) * dy / dx;

        double distance = targetFormationEnergyEv - hullEnergy;
        String products = String.format(Locale.ROOT, "%.1f%% %s + %.1f%% %s", 
            (1.0 - (targetFraction - left.fractionB)/dx) * 100.0, left.getFormula(),
            ((targetFraction - left.fractionB)/dx) * 100.0, right.getFormula());

        return new StabilityResult(false, distance, products);
    }
}
