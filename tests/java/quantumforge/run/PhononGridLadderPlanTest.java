/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class PhononGridLadderPlanTest {

    @Test
    void validLadderPreservesOrder() {
        OperationResult<List<int[]>> result = PhononGridLadderPlan.parse("2 2 2; 4 4 4; 6 6 6");
        assertTrue(result.isSuccess(), result.toString());
        List<int[]> rungs = result.getValue().orElseThrow();
        assertEquals(3, rungs.size());
        assertEquals(2, rungs.get(0)[0]);
        assertEquals(6, rungs.get(2)[2]);
    }

    @Test
    void divisibilityNamesFailingDirections() {
        assertTrue(PhononGridLadderPlan.nonCommensurateDirections(
                new int[] {8, 8, 8}, new int[] {2, 2, 2}).isEmpty());
        assertTrue(PhononGridLadderPlan.nonCommensurateDirections(
                new int[] {8, 8, 8}, new int[] {4, 4, 4}).isEmpty());
        List<Integer> bad = PhononGridLadderPlan.nonCommensurateDirections(
                new int[] {8, 8, 8}, new int[] {3, 4, 6});
        assertEquals(List.of(1, 3), bad,
                "8%3 and 8%6 fail - the failing directions are NAMED, not aggregated away");
        assertTrue(PhononGridLadderPlan.nonCommensurateDirections(
                new int[] {12, 6, 6}, new int[] {3, 3, 3}).isEmpty(),
                "anisotropic k-grids verify per-direction");
    }

    @Test
    void coarseningFlatAndBoundViolationsRefuse() {
        assertEquals("PHONON_LADDER", PhononGridLadderPlan.parse(
                "4 4 4; 2 4 4").getCode(), "coarsening refuses - never re-sorted");
        assertEquals("PHONON_LADDER", PhononGridLadderPlan.parse(
                "4 4 4; 4 4 4").getCode(), "flat ladders carry no information");
        assertEquals("PHONON_LADDER", PhononGridLadderPlan.parse(
                "2 2 2").getCode(), "a single q-grid is not a study");
        assertEquals("PHONON_LADDER", PhononGridLadderPlan.parse(
                "2 2 2;3 3 3;4 4 4;5 5 5;6 6 6;7 7 7;8 8 8").getCode(),
                "more than 6 rungs per plan refuses");
        assertEquals("PHONON_GRID", PhononGridLadderPlan.parse(
                "2 2 0; 4 4 4").getCode());
        assertEquals("PHONON_GRID", PhononGridLadderPlan.parse(
                "2 2 33; 4 4 4").getCode());
        assertEquals("PHONON_GRID", PhononGridLadderPlan.parse(
                "2 2 x; 4 4 4").getCode());
        assertEquals("PHONON_GRID", PhononGridLadderPlan.parse(
                "2 2; 4 4 4").getCode(), "partial rungs refuse - no padding");
        OperationResult<List<int[]>> equal = PhononGridLadderPlan.parse(
                "2 2 2; 2 2 2; 4 4 4");
        assertTrue(equal.isSuccess(),
                "equal neighbors pass when the ladder never coarsens and rises once");
    }
}
