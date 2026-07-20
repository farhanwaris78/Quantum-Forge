/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #37 (plan slice): fail-closed parsing of a k-mesh CONVERGENCE
 * LADDER - the ordered list of automatic meshes a convergence study will
 * climb. This class owns only the ladder grammar and its honesty invariants;
 * the per-rung spacing arithmetic is delegated to the already-tested
 * {@link QEKpointMeshAdvisor} by the analyzer.
 *
 * <p>Owned invariants (each one fails closed):</p>
 * <ul>
 *   <li>2..8 rungs - one mesh cannot converge against anything, and a ladder
 *       longer than 8 rungs per plan is scheduling noise (KMESH_LADDER);</li>
 *   <li>every rung is exactly three integers, each 1..128 - the per-rung
 *       bounds the advisor also enforces (KMESH_GRID);</li>
 *   <li>the ladder must NEVER COARSEN in any direction
 *       (n_i(rung+1) &gt;= n_i(rung) for i=1..3) - a "convergence study"
 *       whose ladder coarsens is a trap that inverts the stopping logic,
 *       and it refuses rather than silently reordering the user's list
 *       (KMESH_LADDER); the user's explicit order is preserved exactly;</li>
 *   <li>at least one direction must STRICTLY increase somewhere (an
 *       all-equal ladder carries no information) (KMESH_LADDER);</li>
 *   <li>the shift triple, when supplied, is exactly three 0/1 values,
 *       matching the advisor's own grammar (KMESH_OFFSET); shift semantics
 *       belong to the deck's K_POINTS card - this plan prompts for them
 *       explicitly and never invents a default.</li>
 * </ul>
 *
 * <p>What is NOT decided here (stated by the analyzer): energies, forces and
 * the delta-threshold stopping criterion are runtime results; this plan
 * schedules rungs and reports spacing/cost arithmetic only.</p>
 */
public final class KMeshConvergenceLadder {

    /** Maximum mesh divisions per direction, mirrored from the advisor bounds. */
    public static final int MAX_DIVISIONS = 128;
    /** Rung-count bounds for one plan. */
    public static final int MIN_RUNGS = 2;
    public static final int MAX_RUNGS = 8;

    /** One validated ladder. */
    public static final class Ladder {
        private final List<int[]> rungs;
        private final int[] offset;

        Ladder(List<int[]> rungs, int[] offset) {
            this.rungs = rungs;
            this.offset = offset;
        }

        /** Unmodifiable rung list, each element a fresh 3-int array order preserved. */
        public List<int[]> getRungs() {
            List<int[]> copy = new ArrayList<>();
            for (int[] rung : this.rungs) {
                copy.add(rung.clone());
            }
            return List.copyOf(copy);
        }

        /** Fresh copy of the 0/1 shift triple. */
        public int[] getOffset() {
            return this.offset.clone();
        }
    }

    private KMeshConvergenceLadder() {
    }

    /**
     * Parses "n1 n2 n3 ; m1 m2 m3 ; ..." rungs (comma or whitespace inside a
     * rung, semicolons between rungs). Order is preserved exactly - rungs are
     * NEVER re-sorted. Codes: KMESH_LADDER / KMESH_GRID / KMESH_OFFSET.
     */
    public static OperationResult<Ladder> parse(String ladderText, String offsetText) {
        int[] offset = parseOffset(offsetText);
        if (offset == null) {
            return OperationResult.failed("KMESH_OFFSET",
                    "The shift must be exactly three 0/1 values (e.g. '0 0 0'); shift "
                            + "semantics are not invented - 1 shifts the grid by half a "
                            + "step in that direction, 0 keeps it Gamma-inclusive.",
                    null);
        }
        String text = ladderText == null ? "" : ladderText.trim();
        if (text.isEmpty()) {
            return OperationResult.failed("KMESH_LADDER",
                    "A ladder of 2..8 rungs is required - a single mesh cannot converge "
                            + "against anything and is refused rather than dressed up as "
                            + "a study.",
                    null);
        }
        String[] rawRungs = text.split(";", -1);
        List<int[]> rungs = new ArrayList<>();
        for (String rawRung : rawRungs) {
            String[] tokens = rawRung.trim().split("[,\\s]+");
            if (tokens.length != 3) {
                return OperationResult.failed("KMESH_GRID",
                        "Every rung needs exactly three divisions (got '" + rawRung.trim()
                                + "'); no partial mesh is padded with invented values.",
                        null);
            }
            int[] rung = new int[3];
            for (int i = 0; i < 3; i++) {
                try {
                    rung[i] = Integer.parseInt(tokens[i]);
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("KMESH_GRID",
                            "Divisions must be plain integers (got '" + tokens[i] + "').",
                            null);
                }
                if (rung[i] < 1 || rung[i] > MAX_DIVISIONS) {
                    return OperationResult.failed("KMESH_GRID",
                            "Divisions must be 1.." + MAX_DIVISIONS + " per direction (got "
                                    + rung[i] + ").",
                            null);
                }
            }
            rungs.add(rung);
        }
        if (rungs.size() < MIN_RUNGS || rungs.size() > MAX_RUNGS) {
            return OperationResult.failed("KMESH_LADDER",
                    "A ladder needs " + MIN_RUNGS + ".." + MAX_RUNGS + " rungs (got "
                            + rungs.size() + ").",
                    null);
        }
        boolean strictSomewhere = false;
        for (int r = 1; r < rungs.size(); r++) {
            int[] prev = rungs.get(r - 1);
            int[] cur = rungs.get(r);
            for (int i = 0; i < 3; i++) {
                if (cur[i] < prev[i]) {
                    return OperationResult.failed("KMESH_LADDER",
                            "Rung " + (r + 1) + " coarsens direction " + (i + 1) + " ("
                                    + prev[i] + " -> " + cur[i] + ") - a convergence ladder "
                                    + "must never coarsen, and the list is NOT re-sorted "
                                    + "behind your back.",
                            null);
                }
                if (cur[i] > prev[i]) {
                    strictSomewhere = true;
                }
            }
        }
        if (!strictSomewhere) {
            return OperationResult.failed("KMESH_LADDER",
                    "Every rung equals its predecessor - an unchanging ladder carries no "
                            + "information and is refused honestly.",
                    null);
        }
        return OperationResult.success("KMESH_OK",
                "Ladder validated: " + rungs.size() + " rungs, never coarsening.",
                new Ladder(rungs, offset));
    }

    private static int[] parseOffset(String offsetText) {
        String text = offsetText == null ? "" : offsetText.trim();
        String[] tokens = text.split("[,\\s]+");
        if (tokens.length != 3) {
            return null;
        }
        int[] offset = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!tokens[i].equals("0") && !tokens[i].equals("1")) {
                return null;
            }
            offset[i] = tokens[i].charAt(0) - '0';
        }
        return offset;
    }
}
