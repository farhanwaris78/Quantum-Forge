/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.KMeshConvergenceLadder.Ladder;

class KMeshConvergenceLadderTest {

    @Test
    void validLadderPreservesOrderAndShift() {
        OperationResult<Ladder> result = KMeshConvergenceLadder.parse(
                "4 4 4; 8 8 8; 12 12 12", "0,0,0");
        assertTrue(result.isSuccess(), result.toString());
        Ladder ladder = result.getValue().orElseThrow();
        List<int[]> rungs = ladder.getRungs();
        assertEquals(3, rungs.size());
        assertEquals(4, rungs.get(0)[0]);
        assertEquals(8, rungs.get(1)[1]);
        assertEquals(12, rungs.get(2)[2]);
        int[] offset = ladder.getOffset();
        assertEquals(0, offset[0]);
        assertEquals(0, offset[2]);
    }

    @Test
    void anisotropicRefinementIsAllowedAsLongAsNothingCoarsens() {
        OperationResult<Ladder> result = KMeshConvergenceLadder.parse(
                "4 4 2; 4 4 4; 8 8 4", "1 1 0");
        assertTrue(result.isSuccess(), result.toString());
        int[] offset = result.getValue().orElseThrow().getOffset();
        assertEquals(1, offset[0]);
        assertEquals(0, offset[2]);
    }

    @Test
    void coarseningRefusesAndNothingIsResorted() {
        OperationResult<Ladder> result = KMeshConvergenceLadder.parse(
                "8 8 8; 4 8 8", "0 0 0");
        assertFalse(result.isSuccess());
        assertEquals("KMESH_LADDER", result.getCode(),
                "a ladder that coarsens inverts the stopping logic - refuse, never re-sort");
    }

    @Test
    void rungCountAndDivisionBoundsRefuse() {
        assertEquals("KMESH_LADDER", KMeshConvergenceLadder.parse(
                "4 4 4", "0 0 0").getCode(), "one mesh cannot converge against anything");
        assertEquals("KMESH_LADDER", KMeshConvergenceLadder.parse(
                "2 2 2;4 4 4;6 6 6;8 8 8;10 10 10;12 12 12;14 14 14;16 16 16;18 18 18",
                "0 0 0").getCode(), "more than 8 rungs per plan refuses");
        assertEquals("KMESH_GRID", KMeshConvergenceLadder.parse(
                "4 4 0; 4 4 4", "0 0 0").getCode());
        assertEquals("KMESH_GRID", KMeshConvergenceLadder.parse(
                "4 4 4; 129 4 4", "0 0 0").getCode());
        assertEquals("KMESH_GRID", KMeshConvergenceLadder.parse(
                "4 4 x; 8 8 8", "0 0 0").getCode());
        assertEquals("KMESH_GRID", KMeshConvergenceLadder.parse(
                "4 4; 8 8 8", "0 0 0").getCode(),
                "a partial rung is never padded with invented values");
    }

    @Test
    void uninformativeAndBadShiftRefuse() {
        OperationResult<Ladder> flat = KMeshConvergenceLadder.parse(
                "6 6 6; 6 6 6", "0 0 0");
        assertFalse(flat.isSuccess(), "an unchanging ladder carries no information");
        assertEquals("KMESH_LADDER", flat.getCode());

        assertEquals("KMESH_OFFSET", KMeshConvergenceLadder.parse(
                "4 4 4; 8 8 8", "1 2 0").getCode(), "shifts are exactly 0 or 1");
        assertEquals("KMESH_OFFSET", KMeshConvergenceLadder.parse(
                "4 4 4; 8 8 8", "0 0").getCode());
        assertEquals("KMESH_OFFSET", KMeshConvergenceLadder.parse(
                "4 4 4; 8 8 8", "").getCode(),
                "an unspecified shift must refuse - defaults are never invented");
    }
}
