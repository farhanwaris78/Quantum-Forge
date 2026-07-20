/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.util.ArrayList;
import java.util.List;

import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #90 (replay arithmetic slice): folds a verified
 * {@link TransformJournal} chain into the COMBINED transform matrix that
 * exact reconstruction needs, with every intermediate determinant on the
 * record. Apply-order semantics (stated so replay cannot be misread): the
 * first journal entry is applied FIRST, so for a column-vector structure
 * transform the combined matrix is
 * <pre>M_combined = M_n x ... x M_2 x M_1</pre>
 * Entries without a matrix (parameter-only operations such as
 * "symmetrize") CANNOT be folded into a matrix - they are skipped, counted,
 * and listed by sequence number so the user sees exactly which steps a
 * matrix-only replay would still have to reproduce by other means.
 *
 * <p>Load-bearing honesty:</p>
 * <ul>
 *   <li>a combined matrix whose determinant magnitude is below 1e-12 is
 *       reported SINGULAR (|det| printed): an inverse replay from the final
 *       structure back to the source does not exist - stated, never
 *       "fixed" by perturbation;</li>
 *   <li>matrix-only replay reconstructs the CELL metric chain; atom-scale
 *       parameters inside parameter-only entries remain the full-replay
 *       depth, and the report says so;</li>
 *   <li>an empty matrix set fails closed (JOURNAL_REPLAY_EMPTY) instead of
 *       returning a fake identity that would masquerade as provenance.</li>
 * </ul>
 *
 * <p>Refusal code: JOURNAL_REPLAY_EMPTY.</p>
 */
public final class JournalReplayMath {

    /** |det| below this is reported singular. */
    public static final double SINGULAR_DET_THRESHOLD = 1e-12;

    /** One folded result. */
    public static final class ReplaySummary {
        private final int matrixEntries;
        private final List<Integer> skippedSequences;
        private final double[][] combined;
        private final double combinedDet;
        private final List<Double> perEntryDet;

        ReplaySummary(int matrixEntries, List<Integer> skippedSequences,
                double[][] combined, List<Double> perEntryDet) {
            this.matrixEntries = matrixEntries;
            this.skippedSequences = new ArrayList<>(skippedSequences);
            this.combined = Matrix3D.copy(combined);
            this.perEntryDet = new ArrayList<>(perEntryDet);
            this.combinedDet = Matrix3D.determinant(this.combined);
        }

        /** Entries that carried a matrix and were folded. */
        public int getMatrixEntries() { return this.matrixEntries; }
        /** Sequence numbers of parameter-only entries (not folded). */
        public List<Integer> getSkippedSequences() {
            return List.copyOf(this.skippedSequences);
        }
        /** Combined 3x3 transform, rows. */
        public double[][] getCombined() { return Matrix3D.copy(this.combined); }
        /** det(M_combined) = product of per-entry determinants. */
        public double getCombinedDet() { return this.combinedDet; }
        /** Per-entry determinants in journal order (matrix entries only). */
        public List<Double> getPerEntryDet() { return List.copyOf(this.perEntryDet); }
        /** True when |det| < 1e-12 - no inverse replay exists. */
        public boolean isSingular() {
            return Math.abs(this.combinedDet) < SINGULAR_DET_THRESHOLD;
        }
        /** det > 0 means the chain preserved handedness; det < 0 inverted it. */
        public boolean preservedHandedness() {
            return this.combinedDet > 0.0;
        }
    }

    private JournalReplayMath() {
    }

    /**
     * Folds the matrix-carrying entries of a verified journal into the
     * combined transform. Code: JOURNAL_REPLAY_EMPTY.
     */
    public static OperationResult<ReplaySummary> combine(
            TransformJournal.JournalSummary journal) {
        if (journal == null || journal.getEntries().isEmpty()) {
            return OperationResult.failed("JOURNAL_REPLAY_EMPTY",
                    "The journal holds no entries to fold.", null);
        }
        double[][] combined = Matrix3D.unit();
        int matrixEntries = 0;
        List<Integer> skipped = new ArrayList<>();
        List<Double> dets = new ArrayList<>();
        for (TransformJournal.JournalEntry entry : journal.getEntries()) {
            double[] flat = entry.getMatrix();
            if (flat == null) {
                skipped.add(entry.getSeq());
                continue;
            }
            double[][] step = new double[3][3];
            for (int idx = 0; idx < 9; idx += 1) {
                step[idx / 3][idx % 3] = flat[idx];
            }
            dets.add(Matrix3D.determinant(step));
            // entry applied first => pre-multiply onto the running chain
            combined = Matrix3D.mult(step, combined);
            matrixEntries += 1;
        }
        if (matrixEntries == 0) {
            return OperationResult.failed("JOURNAL_REPLAY_EMPTY",
                    "No journal entry carries a transform matrix - there is NO "
                            + "combined transform to compute; an identity would be "
                            + "fabricated provenance, so this fails closed.",
                    null);
        }
        return OperationResult.success("JOURNAL_REPLAY_OK",
                "Folded " + matrixEntries + " matri(x/ces).",
                new ReplaySummary(matrixEntries, skipped, combined, dets));
    }
}
