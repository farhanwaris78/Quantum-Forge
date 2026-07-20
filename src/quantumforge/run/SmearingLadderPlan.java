/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.util.ArrayList;
import java.util.List;

import quantumforge.com.math.QEUnits;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #38 (plan slice): a fail-closed SMEARING (degauss) DOWN-LADDER
 * plan. Where #37's k-mesh ladder REFINES, this ladder DAMPENS toward zero
 * broadening - the direction is owned and enforced because a widening study
 * inverts the physical question (larger degauss = stronger artificial
 * metallic bias, not better sampling). The dishonesty patterns this plan
 * forbids:
 *
 * <ul>
 *   <li>a typed scheme only: gaussian / mp (Methfessel-Paxton) / mv
 *       (Marzari-Vanderbilt cold) / fd (Fermi-Dirac); free-form schemes
 *       refuse (SMEAR_SCHEME);</li>
 *   <li>2..8 rungs of degauss in RY, each finite &gt; 0 and within the owned
 *       sanity band 1e-4..1.0 Ry (SMEAR_VALUE) - a degauss of exactly 0 is
 *       not a rung, it is a different calculation, and refusing it here
 *       keeps 'converged at degauss=0' from being claimed by omission;</li>
 *   <li>the ladder NEVER INCREASES (each rung's degauss &lt;= the previous)
 *       and at least one strict decrease exists (SMEAR_LADDER);</li>
 *   <li>eV equivalents use {@link QEUnits#EV_PER_RY} - conversion constants
 *       are never re-typed per feature;</li>
 *   <li>the honesty block states what the plan does NOT judge: the entropy
 *       (-T*S) term must be monitored in the actual runs, different schemes
 *       bias forces differently, and convergence is NEVER declared by a
 *       schedule - it is a delta-E/delta-F comparison after the runs.</li>
 * </ul>
 */
public final class SmearingLadderPlan {

    /** Owned sanity band for one degauss rung (Ry). */
    public static final double MIN_DEGAUSS_RY = 1e-4;
    public static final double MAX_DEGAUSS_RY = 1.0;
    public static final int MIN_RUNGS = 2;
    public static final int MAX_RUNGS = 8;

    /** One ladder rung with both units pinned. */
    public static final class Rung {
        private final int index;
        private final double degaussRy;
        private final double degaussEv;
        private final Double reductionVsPrev;   // null on rung 1 (pure ratio, >= 1)

        Rung(int index, double degaussRy, Double reductionVsPrev) {
            this.index = index;
            this.degaussRy = degaussRy;
            this.degaussEv = degaussRy * QEUnits.EV_PER_RY;
            this.reductionVsPrev = reductionVsPrev;
        }

        public int getIndex() { return this.index; }
        public double getDegaussRy() { return this.degaussRy; }
        public double getDegaussEv() { return this.degaussEv; }
        public Double getReductionVsPrev() { return this.reductionVsPrev; }
    }

    /** One validated down-ladder. */
    public static final class Ladder {
        private final String scheme;
        private final List<Rung> rungs;

        Ladder(String scheme, List<Rung> rungs) {
            this.scheme = scheme;
            this.rungs = rungs;
        }

        public String getScheme() { return this.scheme; }
        public List<Rung> getRungs() { return this.rungs; }
    }

    private SmearingLadderPlan() {
    }

    /** Validates scheme + down-ladder. Codes: SMEAR_SCHEME/SMEAR_LADDER/SMEAR_VALUE. */
    public static OperationResult<Ladder> validate(String schemeText, String ladderText) {
        String scheme = schemeText == null ? "" : schemeText.trim()
                .toLowerCase(java.util.Locale.ROOT);
        String canonical;
        switch (scheme) {
        case "gaussian": case "gauss":
            canonical = "gaussian";
            break;
        case "mp": case "methfessel-paxton":
            canonical = "mp";
            break;
        case "mv": case "marzari-vanderbilt": case "cold":
            canonical = "mv";
            break;
        case "fd": case "fermi-dirac":
            canonical = "fd";
            break;
        default:
            return OperationResult.failed("SMEAR_SCHEME",
                    "smearing scheme is TYPED: gaussian/mp/mv/fd (got '" + scheme
                            + "') - free-form schemes refuse.",
                    null);
        }
        String text = ladderText == null ? "" : ladderText.trim();
        if (text.isEmpty()) {
            return OperationResult.failed("SMEAR_LADDER",
                    "A down-ladder of 2..8 degauss values is required (Ry, e.g. "
                            + "'0.02; 0.01; 0.005') - a single value is not a study.",
                    null);
        }
        String[] tokens = text.split(";", -1);
        if (tokens.length < MIN_RUNGS || tokens.length > MAX_RUNGS) {
            return OperationResult.failed("SMEAR_LADDER",
                    "The ladder needs 2..8 rungs (got " + tokens.length + ").", null);
        }
        List<Double> values = new ArrayList<>();
        for (String token : tokens) {
            double value;
            try {
                value = Double.parseDouble(token.trim());
            } catch (NumberFormatException ex) {
                return OperationResult.failed("SMEAR_VALUE",
                        "degauss must be a plain number in Ry (got '" + token.trim()
                                + "').",
                        null);
            }
            if (!Double.isFinite(value) || value < MIN_DEGAUSS_RY || value > MAX_DEGAUSS_RY) {
                return OperationResult.failed("SMEAR_VALUE",
                        "degauss must be finite and within " + MIN_DEGAUSS_RY + ".."
                                + MAX_DEGAUSS_RY + " Ry (got " + value + "); exactly 0 is "
                                + "a different calculation, not a rung.",
                        null);
            }
            values.add(Double.valueOf(value));
        }
        boolean strictSomewhere = false;
        List<Rung> rungs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            double current = values.get(i).doubleValue();
            Double reduction = null;
            if (i > 0) {
                double previous = values.get(i - 1).doubleValue();
                if (current > previous) {
                    return OperationResult.failed("SMEAR_LADDER",
                            "rung " + (i + 1) + " WIDENS degauss (" + previous + " -> "
                                    + current + " Ry) - a smearing study DAMPENS toward "
                                    + "zero broadening; a widening ladder inverts the "
                                    + "physical question and refuses rather than silently "
                                    + "re-sorting your input.",
                            null);
                }
                if (current < previous) {
                    strictSomewhere = true;
                }
                reduction = Double.valueOf(previous / current);
            }
            rungs.add(new Rung(i + 1, current, reduction));
        }
        if (!strictSomewhere) {
            return OperationResult.failed("SMEAR_LADDER",
                    "every rung equals its predecessor - an unchanging ladder carries no "
                            + "information and refuses honestly.",
                    null);
        }
        return OperationResult.success("SMEAR_OK", "Smearing down-ladder validated.",
                new Ladder(canonical, List.copyOf(rungs)));
    }
}
