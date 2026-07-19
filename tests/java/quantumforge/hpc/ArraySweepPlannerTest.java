/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class ArraySweepPlannerTest {

    @Test
    void plansExactValuesAndDirectories() {
        OperationResult<ArraySweepPlanner.SweepPlan> result =
                ArraySweepPlanner.plan("ecutwfc", 30.0, 10.0, 4, "si-cut");
        assertTrue(result.isSuccess(), result.getMessage());
        ArraySweepPlanner.SweepPlan plan = result.getValue().orElseThrow();
        assertEquals(4, plan.getValues().size());
        assertEquals(30.0, plan.getValues().get(0), 0.0);
        assertEquals(60.0, plan.getValues().get(3), 0.0);
        assertEquals("si-cut-001", plan.taskDirectory(1));
        assertEquals("si-cut-004", plan.taskDirectory(4));
        String jsonl = plan.toJsonLines();
        assertEquals(4, jsonl.trim().split("\n").length);
        assertTrue(jsonl.contains("{\"task_index\":2,\"keyword\":\"ecutwfc\",\"value\":40.0,"
                + "\"directory\":\"si-cut-002\"}"), jsonl);
        String sbatch = plan.sbatchPreview();
        assertTrue(sbatch.contains("exit 2  # REQUIRED-EDIT guard"), sbatch);
        assertTrue(sbatch.contains("#SBATCH --array=1-4"), sbatch);
        assertTrue(sbatch.contains("ecutwfc = $VALUE"), sbatch);
    }

    @Test
    void allowsIndexedKeywordsAndNegativeSteps() {
        OperationResult<ArraySweepPlanner.SweepPlan> result =
                ArraySweepPlanner.plan("starting_magnetization(1)", 0.5, -0.25, 3,
                        "fe-mag");
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(0.0, result.getValue().orElseThrow().getValues().get(2), 1e-15);
    }

    @Test
    void failsClosedOnInvalidInputs() {
        assertEquals("SWEEP_KEYWORD",
                ArraySweepPlanner.plan("9bad", 0.0, 1.0, 2, "x").getCode());
        assertEquals("SWEEP_KEYWORD",
                ArraySweepPlanner.plan(" ", 0.0, 1.0, 2, "x").getCode());
        assertEquals("SWEEP_NAME",
                ArraySweepPlanner.plan("ecutwfc", 0.0, 1.0, 2, "bad name!").getCode());
        assertEquals("SWEEP_NAME",
                ArraySweepPlanner.plan("ecutwfc", 0.0, 1.0, 2, "").getCode());
        assertEquals("SWEEP_VALUE",
                ArraySweepPlanner.plan("ecutwfc", 0.0, 0.0, 2, "x").getCode());
        assertEquals("SWEEP_VALUE",
                ArraySweepPlanner.plan("ecutwfc", Double.NaN, 1.0, 2, "x").getCode());
        assertEquals("SWEEP_VALUE",
                ArraySweepPlanner.plan("ecutwfc", 0.0, Double.POSITIVE_INFINITY, 2, "x")
                        .getCode());
        assertEquals("SWEEP_COUNT",
                ArraySweepPlanner.plan("ecutwfc", 0.0, 1.0,
                        ArraySweepPlanner.MIN_TASKS - 1, "x").getCode());
        assertEquals("SWEEP_COUNT",
                ArraySweepPlanner.plan("ecutwfc", 0.0, 1.0,
                        ArraySweepPlanner.MAX_TASKS + 1, "x").getCode());
        // A step far below half the ulp of the start value cannot change it.
        assertEquals("SWEEP_VALUE",
                ArraySweepPlanner.plan("ecutwfc", 1.0e300, 1.0e280, 3, "x").getCode(),
                "A step that cannot change the value must be refused");
    }
}
