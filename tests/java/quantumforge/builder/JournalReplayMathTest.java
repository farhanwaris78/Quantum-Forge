/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.builder.JournalReplayMath.ReplaySummary;
import quantumforge.operation.OperationResult;

class JournalReplayMathTest {

    @Test
    void combinedMatrixMatchesPythonForDiagonalChains() {
        TransformJournal journal = new TransformJournal();
        journal.append("cod:9011998", "supercell",
                new double[]{2.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 2.0},
                List.of("atoms=8"));
        journal.append("journal-chain", "strain",
                new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.5},
                List.of("axis=c"));
        OperationResult<TransformJournal.JournalSummary> parsed =
                TransformJournal.parse(journal.render());
        assertTrue(parsed.isSuccess(), parsed.toString());
        OperationResult<ReplaySummary> folded = JournalReplayMath.combine(
                parsed.getValue().orElseThrow());
        assertTrue(folded.isSuccess(), folded.toString());
        ReplaySummary replay = folded.getValue().orElseThrow();
        assertEquals(2, replay.getMatrixEntries());
        assertTrue(replay.getSkippedSequences().isEmpty());
        double[][] combined = replay.getCombined();
        assertEquals(2.0, combined[0][0], 1e-12);
        assertEquals(2.0, combined[1][1], 1e-12);
        assertEquals(3.0, combined[2][2], 1e-12);
        assertEquals(12.0, replay.getCombinedDet(), 1e-12,
                "det(diag(2,2,3)) = 12 = det(2I) * det(diag(1,1,1.5)) = 8 * 1.5");
        assertEquals(List.of(8.0, 1.5), replay.getPerEntryDet());
        assertFalse(replay.isSingular());
        assertTrue(replay.preservedHandedness(), "positive determinant");
    }

    @Test
    void applyOrderIsFirstEntryFirstWithHandednessOnRecord() {
        TransformJournal journal = new TransformJournal();
        journal.append("src", "swap-xy",
                new double[]{0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0},
                List.of());
        journal.append("src", "stretch-x",
                new double[]{2.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0},
                List.of());
        journal.append("src", "symmetrize", null, List.of("tolerance=1e-5"));
        OperationResult<ReplaySummary> folded = JournalReplayMath.combine(
                TransformJournal.parse(journal.render()).getValue().orElseThrow());
        assertTrue(folded.isSuccess(), folded.toString());
        ReplaySummary replay = folded.getValue().orElseThrow();
        double[][] combined = replay.getCombined();
        // Python-verified: stretch @ swap = [[0,2,0],[1,0,0],[0,0,1]], det = -2
        assertEquals(0.0, combined[0][0], 1e-12);
        assertEquals(2.0, combined[0][1], 1e-12);
        assertEquals(1.0, combined[1][0], 1e-12);
        assertEquals(0.0, combined[1][1], 1e-12);
        assertEquals(1.0, combined[2][2], 1e-12);
        assertEquals(-2.0, replay.getCombinedDet(), 1e-12);
        assertFalse(replay.preservedHandedness(),
                "the swap inverted handedness - on the record, never hidden");
        assertFalse(replay.isSingular());
        assertEquals(List.of(3), replay.getSkippedSequences(),
                "the parameter-only step is counted and listed, not folded");
        assertEquals(2, replay.getMatrixEntries());
    }

    @Test
    void emptyAndSingularChainsFailClosedOrSaySo() {
        TransformJournal paramOnly = new TransformJournal();
        paramOnly.append("src", "symmetrize", null, List.of());
        OperationResult<ReplaySummary> empty = JournalReplayMath.combine(
                TransformJournal.parse(paramOnly.render()).getValue().orElseThrow());
        assertFalse(empty.isSuccess());
        assertEquals("JOURNAL_REPLAY_EMPTY", empty.getCode(),
                "no fake identity masquerading as provenance");

        TransformJournal flat = new TransformJournal();
        flat.append("src", "flatten-z",
                new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0},
                List.of());
        OperationResult<ReplaySummary> singular = JournalReplayMath.combine(
                TransformJournal.parse(flat.render()).getValue().orElseThrow());
        assertTrue(singular.isSuccess(), singular.toString());
        assertTrue(singular.getValue().orElseThrow().isSingular(),
                "det 0: no inverse replay exists - stated, not perturbed");
        assertFalse(singular.getValue().orElseThrow().preservedHandedness());
        assertEquals(0.0, singular.getValue().orElseThrow().getCombinedDet(), 1e-12);
    }
}
