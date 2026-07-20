/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.ArrayJobPlan;

/**
 * Batch-131 pinned proof that the two #100 array products are intentionally
 * DIFFERENT products - the per-task directory mappings diverge by design and
 * the ARRAY_JOB_AUDIT kind renders that divergence rather than hiding it.
 */
class ArrayProductsConsistencyTest {

    @Test
    void sameInputMapsToDifferentDirectoriesByDesign() {
        ArraySweepPlanner.SweepPlan sweep = ArraySweepPlanner
                .plan("ecutwfc", 30.0, 5.0, 3, "sweep").getValue().orElseThrow();
        ArrayJobPlan.Plan plan = ArrayJobPlan
                .validate("sweep", "30.0,35.0,40.0", false).getValue().orElseThrow();
        assertFalse(sweep.taskDirectory(1).equals(plan.taskDirectory(1)),
                "two products, two mappings - do not mix artifacts in one study");
        assertEquals("sweep-001", sweep.taskDirectory(1));
        assertEquals("sweep/task_1", plan.taskDirectory(1));
        // What they DO share: the 1-based convention of SLURM --array=1-N.
        assertEquals("sweep-003", sweep.taskDirectory(3));
        assertEquals("sweep/task_3", plan.taskDirectory(3));
    }

    @Test
    void grammarsGenuinelyDisagreeAtTheNamedEdges() {
        // Leading digit: planner grammar allows it, plan grammar refuses it.
        OperationResult<ArraySweepPlanner.SweepPlan> plannerDigit =
                ArraySweepPlanner.plan("ecutwfc", 30.0, 5.0, 2, "1sweep");
        assertTrue(plannerDigit.isSuccess(), plannerDigit.toString());
        OperationResult<ArrayJobPlan.Plan> planDigit =
                ArrayJobPlan.validate("1sweep", "30.0,35.0", false);
        assertFalse(planDigit.isSuccess());
        assertEquals("ARRAY_NAME", planDigit.getCode());

        // Single task: plan grammar allows 1, planner demands at least 2.
        OperationResult<ArrayJobPlan.Plan> planOne =
                ArrayJobPlan.validate("sweep", "30.0", false);
        assertTrue(planOne.isSuccess(), planOne.toString());
        OperationResult<ArraySweepPlanner.SweepPlan> plannerOne =
                ArraySweepPlanner.plan("ecutwfc", 30.0, 5.0, 1, "sweep");
        assertFalse(plannerOne.isSuccess());
        assertEquals("SWEEP_COUNT", plannerOne.getCode());
    }
}
