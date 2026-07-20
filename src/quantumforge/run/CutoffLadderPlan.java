/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.util.ArrayList;
import java.util.List;

import quantumforge.com.math.QEUnits;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #36 (plan slice): a fail-closed ecutwfc CONVERGENCE LADDER with its
 * implied ecutrho arithmetic spelled out. The ladder ASCENDS like the #37
 * k-mesh ladder (samples finer each rung); ecutrho is never listed separately
 * - it is IMPLIED from YOUR deck's ecutrho/ecutwfc ratio, echoed rung-by-rung
 * so the plan and the deck cannot disagree silently. The traps owned here:
 *
 * <ul>
 *   <li>2..8 rungs of ecutwfc in Ry, each finite within the owned sanity band
 *       5..500 Ry - the band is OURS and the render says so (QE itself has no
 *       such limit); (CUTOFF_VALUE)</li>
 *   <li>ascending, never coarsening, at least one strict increase
 *       (CUTOFF_LADDER) - a descending ladder inverts the sampling
 *       refinement and refuses rather than re-sorting;</li>
 *   <li>the rho ratio is a REQUIRED finite &gt;= 1.0 parameter with no
 *       invented default: ecutrho &lt; ecutwfc (ratio &lt; 1) means the charge
 *       density grid is coarser than the wavefunction grid, which the plan
 *       refuses as a specification error (CUTOFF_RATIO). Typical suggested
 *       ranges (4 for NC, 8-12 for US/PAW) are stated as LITERATURE HEARSAY
 *       in the render - the ratio itself is YOUR deck's setting;</li>
 *   <li>eV conversions use the shared {@link QEUnits#EV_PER_RY}; the cost
 *       column computes (wfc_k / wfc_{k-1})^1.5 exactly but LABELS the 1.5
 *       exponent a rule-of-thumb - basis-set cost scaling is a heuristic,
 *       never a measurement;</li>
 *   <li>convergence is NEVER declared by the plan - it is a delta-E (and
 *       delta-F/stress where relevant) comparison after the runs.</li>
 * </ul>
 */
public final class CutoffLadderPlan {

    /** Owned sanity band (Ry) - stated as ours in every render. */
    public static final double MIN_ECUT_RY = 5.0;
    public static final double MAX_ECUT_RY = 500.0;
    public static final int MIN_RUNGS = 2;
    public static final int MAX_RUNGS = 8;
    /** Rule-of-thumb basis-cost exponent - LABELED heuristic, not a measurement. */
    public static final double COST_EXPONENT = 1.5;

    /** One ladder rung. */
    public static final class Rung {
        private final int index;
        private final double wfcRy;
        private final double wfcEv;
        private final double impliedRhoRy;
        private final Double costFactorVsPrev;  // null on rung 1 (heuristic, exact math)

        Rung(int index, double wfcRy, double impliedRhoRy, Double costFactorVsPrev) {
            this.index = index;
            this.wfcRy = wfcRy;
            this.wfcEv = wfcRy * QEUnits.EV_PER_RY;
            this.impliedRhoRy = impliedRhoRy;
            this.costFactorVsPrev = costFactorVsPrev;
        }

        public int getIndex() { return this.index; }
        public double getWfcRy() { return this.wfcRy; }
        public double getWfcEv() { return this.wfcEv; }
        public double getImpliedRhoRy() { return this.impliedRhoRy; }
        public Double getCostFactorVsPrev() { return this.costFactorVsPrev; }
    }

    /** One validated ladder. */
    public static final class Ladder {
        private final double rhoRatio;
        private final List<Rung> rungs;

        Ladder(double rhoRatio, List<Rung> rungs) {
            this.rhoRatio = rhoRatio;
            this.rungs = rungs;
        }

        public double getRhoRatio() { return this.rhoRatio; }
        public List<Rung> getRungs() { return this.rungs; }
    }

    private CutoffLadderPlan() {
    }

    /** Validates one ladder. Codes: CUTOFF_LADDER/CUTOFF_VALUE/CUTOFF_RATIO. */
    public static OperationResult<Ladder> validate(String wfcLadderText, double rhoRatio) {
        if (!Double.isFinite(rhoRatio) || rhoRatio < 1.0) {
            return OperationResult.failed("CUTOFF_RATIO",
                    "ecutrho/ecutwfc must be finite and >= 1.0 (got " + rhoRatio + "): a "
                            + "ratio under 1 means the charge-density grid is COARSER "
                            + "than the wavefunction grid - a specification error, not "
                            + "something to echo. No default is invented (common "
                            + "settings like 4 for NC or 8-12 for US/PAW are literature "
                            + "hearsay, stated as such).",
                    null);
        }
        String text = wfcLadderText == null ? "" : wfcLadderText.trim();
        if (text.isEmpty()) {
            return OperationResult.failed("CUTOFF_LADDER",
                    "An ascending ecutwfc ladder of 2..8 rungs is required (Ry, e.g. "
                            + "'30; 40; 60') - a single cutoff is not a study.",
                    null);
        }
        String[] tokens = text.split(";", -1);
        if (tokens.length < MIN_RUNGS || tokens.length > MAX_RUNGS) {
            return OperationResult.failed("CUTOFF_LADDER",
                    "The ladder needs 2..8 rungs (got " + tokens.length + ").", null);
        }
        List<Double> values = new ArrayList<>();
        for (String token : tokens) {
            double value;
            try {
                value = Double.parseDouble(token.trim());
            } catch (NumberFormatException ex) {
                return OperationResult.failed("CUTOFF_VALUE",
                        "ecutwfc must be a plain number in Ry (got '" + token.trim()
                                + "').",
                        null);
            }
            if (!Double.isFinite(value) || value < MIN_ECUT_RY || value > MAX_ECUT_RY) {
                return OperationResult.failed("CUTOFF_VALUE",
                        "ecutwfc must be finite and within " + MIN_ECUT_RY + ".."
                                + MAX_ECUT_RY + " Ry (got " + value + ") - the band is "
                                + "OURS (QE imposes none), stated plainly.",
                        null);
            }
            values.add(Double.valueOf(value));
        }
        boolean strictSomewhere = false;
        List<Rung> rungs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            double current = values.get(i).doubleValue();
            Double costFactor = null;
            if (i > 0) {
                double previous = values.get(i - 1).doubleValue();
                if (current < previous) {
                    return OperationResult.failed("CUTOFF_LADDER",
                            "rung " + (i + 1) + " coarsens ecutwfc (" + previous + " -> "
                                    + current + " Ry) - the ladder must ASCEND; refused, "
                                    + "never re-sorted.",
                            null);
                }
                if (current > previous) {
                    strictSomewhere = true;
                }
                costFactor = Double.valueOf(Math.pow(current / previous, COST_EXPONENT));
            }
            rungs.add(new Rung(i + 1, current, current * rhoRatio, costFactor));
        }
        if (!strictSomewhere) {
            return OperationResult.failed("CUTOFF_LADDER",
                    "every rung equals its predecessor - an unchanging ladder carries no "
                            + "information.",
                    null);
        }
        return OperationResult.success("CUTOFF_OK", "Cutoff ladder validated.",
                new Ladder(rhoRatio, List.copyOf(rungs)));
    }
}
