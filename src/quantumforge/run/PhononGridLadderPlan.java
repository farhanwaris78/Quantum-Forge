/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #51 (plan slice): a fail-closed phonon q-GRID ladder with per-rung
 * k/q COMMENSURABILITY verdicts against the live SCF k-grid. The physics
 * honesty here: a phonon run on q-grid rungs whose points are NOT a subset
 * of the SCF k-grid costs fresh SCFs per q and is the classic wasted-queue
 * mistake; QE does not FORBID it, so the plan cannot refuse it - but it must
 * never PRETEND it away either. Hence: every rung earns an explicit
 * COMMENSURATE / NOT-COMMENSURATE verdict per direction (k_i % q_i == 0),
 * and when the deck's k-grid is unavailable the verdict is an honest
 * UNVERIFIABLE banner in caps - never a silent pass.
 *
 * <ul>
 *   <li>2..6 rungs of "q1 q2 q3" triples, each 1..32 (owned band, stated);
 *       never coarsening with at least one strict increase (PHONON_LADDER /
 *       PHONON_GRID) - order preserved, nothing re-sorted;</li>
 *   <li>verdict per rung per direction: exact divisibility arithmetic,
 *       with the failing directions NAMED (not a silent aggregate);</li>
 *   <li>downstream honesty (stated by the kind): q2r/matdyn conversions,
 *       acoustic-sum-rule diagnostics, non-analytic corrections and
 *       imaginary-mode reporting are the ph.x RESULT depth - this slice
 *       schedules grids only.</li>
 * </ul>
 */
public final class PhononGridLadderPlan {

    public static final int MAX_DIVISIONS = 32;
    public static final int MIN_RUNGS = 2;
    public static final int MAX_RUNGS = 6;

    /** Per-rung verdict against one k-grid. */
    public enum Verdict {
        COMMENSURATE, NOT_COMMENSURATE, UNVERIFIABLE
    }

    /** One rung with its divisibility verdict. */
    public static final class Rung {
        private final int index;
        private final int[] grid;             // q1 q2 q3
        private final Verdict verdict;
        private final List<Integer> badDirections;  // 1-based, named - empty when commensurate

        Rung(int index, int[] grid, Verdict verdict, List<Integer> badDirections) {
            this.index = index;
            this.grid = grid;
            this.verdict = verdict;
            this.badDirections = badDirections;
        }

        public int getIndex() { return this.index; }
        public int[] getGrid() { return this.grid.clone(); }
        public Verdict getVerdict() { return this.verdict; }
        /** 1-based directions where k_i % q_i != 0; empty unless NOT_COMMENSURATE. */
        public List<Integer> getBadDirections() { return this.badDirections; }
    }

    private PhononGridLadderPlan() {
    }

    /**
     * Parses the q-ladder. Codes: PHONON_LADDER/PHONON_GRID. Order preserved.
     */
    public static OperationResult<List<int[]>> parse(String ladderText) {
        String text = ladderText == null ? "" : ladderText.trim();
        if (text.isEmpty()) {
            return OperationResult.failed("PHONON_LADDER",
                    "A q-grid ladder of 2..6 rungs is required (e.g. '2 2 2; 4 4 4') - a "
                            + "single q-grid is not a study.",
                    null);
        }
        String[] rawRungs = text.split(";", -1);
        List<int[]> rungs = new ArrayList<>();
        for (String raw : rawRungs) {
            String[] tokens = raw.trim().split("[,\\s]+");
            if (tokens.length != 3) {
                return OperationResult.failed("PHONON_GRID",
                        "every rung needs exactly three divisions (got '" + raw.trim()
                                + "'); nothing is padded with invented values.",
                        null);
            }
            int[] rung = new int[3];
            for (int i = 0; i < 3; i++) {
                try {
                    rung[i] = Integer.parseInt(tokens[i]);
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("PHONON_GRID",
                            "divisions must be plain integers (got '" + tokens[i] + "').",
                            null);
                }
                if (rung[i] < 1 || rung[i] > MAX_DIVISIONS) {
                    return OperationResult.failed("PHONON_GRID",
                            "divisions must be 1.." + MAX_DIVISIONS + " per direction "
                                    + "(owned band, got " + rung[i] + ").",
                            null);
                }
            }
            rungs.add(rung);
        }
        if (rungs.size() < MIN_RUNGS || rungs.size() > MAX_RUNGS) {
            return OperationResult.failed("PHONON_LADDER",
                    "the ladder needs 2..6 rungs (got " + rungs.size() + ").", null);
        }
        boolean strictSomewhere = false;
        for (int r = 1; r < rungs.size(); r++) {
            for (int i = 0; i < 3; i++) {
                if (rungs.get(r)[i] < rungs.get(r - 1)[i]) {
                    return OperationResult.failed("PHONON_LADDER",
                            "rung " + (r + 1) + " coarsens direction " + (i + 1) + " ("
                                    + rungs.get(r - 1)[i] + " -> " + rungs.get(r)[i]
                                    + ") - refused, never re-sorted.",
                            null);
                }
                if (rungs.get(r)[i] > rungs.get(r - 1)[i]) {
                    strictSomewhere = true;
                }
            }
        }
        if (!strictSomewhere) {
            return OperationResult.failed("PHONON_LADDER",
                    "every rung equals its predecessor - an unchanging ladder carries no "
                            + "information.",
                    null);
        }
        return OperationResult.success("PHONON_OK",
                "q-ladder validated: " + rungs.size() + " rungs.", List.copyOf(rungs));
    }

    /** Divisibility per direction: kGrid[i] % qGrid[i] == 0. Names failures (1-based). */
    public static List<Integer> nonCommensurateDirections(int[] kGrid, int[] qGrid) {
        List<Integer> bad = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (kGrid[i] % qGrid[i] != 0) {
                bad.add(Integer.valueOf(i + 1));
            }
        }
        return List.copyOf(bad);
    }
}
