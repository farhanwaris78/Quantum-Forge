/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.ArrayJobPlan.Plan;

class ArrayJobPlanTest {

    @Test
    void mappingIsOneBasedAndVerbatimWithReviewLine() {
        OperationResult<Plan> result = ArrayJobPlan.validate(
                "ecut-sweep", "30.00, 40, 60.000000", true);
        assertTrue(result.isSuccess(), result.toString());
        Plan plan = result.getValue().orElseThrow();
        assertEquals(3, plan.getTaskCount());
        assertEquals("ecut-sweep/task_1", plan.taskDirectory(1),
                "1-BASED mapping like --array=1-N, pinned");
        assertEquals("ecut-sweep/task_3", plan.taskDirectory(3));
        assertEquals("30.00", plan.taskValue(1),
                "tokens echo VERBATIM - never re-typed or re-rounded");
        assertEquals("60.000000", plan.taskValue(3));
        String block = plan.render();
        assertTrue(block.contains("slurm_array_line = #SBATCH --array=1-3"), block + " | " + "review line counts 1..N like SLURM");
        assertTrue(block.contains("task 2 = 40   (dir ecut-sweep/task_2)"), block);
        assertTrue(block.contains("NOT guessed here"), block + " | " + "pbs/pjm/sge array syntax is stated depth, not guessed");
        assertThrows(IllegalArgumentException.class, () -> plan.taskDirectory(0),
                "index 0 must throw - the mapping is 1-based");
        assertThrows(IllegalArgumentException.class, () -> plan.taskValue(4));
    }

    @Test
    void duplicatesAreNumericWhileEchoStaysVerbatim() {
        OperationResult<Plan> numericDup = ArrayJobPlan.validate("sweep", "30, 30.0", false);
        assertFalse(numericDup.isSuccess());
        assertEquals("ARRAY_DUPLICATE", numericDup.getCode(),
                "'30' and '30.0' differ textually but are the SAME point numerically - "
                        + "duplicate detection is numeric, echo stays verbatim");
        OperationResult<Plan> realDup = ArrayJobPlan.validate("sweep", "30, 30", false);
        assertFalse(realDup.isSuccess());
        assertEquals("ARRAY_DUPLICATE", realDup.getCode(),
                "two identical tasks is the silent-redundancy this plan exists to prevent");
        assertEquals("ARRAY_VALUES", ArrayJobPlan.validate("sweep", "", false).getCode());
        assertEquals("ARRAY_VALUES", ArrayJobPlan.validate(
                "sweep", "30, oops, 40", false).getCode(),
                "non-numeric templating is runtime depth, not silently accepted");
        assertEquals("ARRAY_VALUES", ArrayJobPlan.validate(
                "sweep", "30, NaN, 40", false).getCode());
    }

    @Test
    void baseNameGrammarRefusesPathFragments() {
        assertEquals("ARRAY_NAME", ArrayJobPlan.validate(
                "1sweep", "30", false).getCode());
        assertEquals("ARRAY_NAME", ArrayJobPlan.validate(
                "../sweep", "30", false).getCode());
        assertEquals("ARRAY_NAME", ArrayJobPlan.validate(
                "my sweep", "30", false).getCode(),
                "whitespace in a directory-seeding name refuses");
        assertEquals("ARRAY_NAME", ArrayJobPlan.validate(
                "sweep;rm", "30", false).getCode());
    }

    @Test
    void optOutOfSlurmLineStatesSo() {
        Plan plan = ArrayJobPlan.validate("sweep", "30, 40", false)
                .getValue().orElseThrow();
        String block = plan.render();
        assertFalse(plan.hasSlurmArrayLine());
        assertTrue(block.contains("(not incorporated"), block + " | " + "opting out is printed, not silent");
    }
}
