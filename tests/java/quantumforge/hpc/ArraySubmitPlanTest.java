/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Batch-141 (#93/#100 guarded submit-loop slice): the two honest submission
 * shapes - the single-array draft built from an adapter's OWNED array spec,
 * and the per-task loop any adapter without owned array knowledge honestly
 * falls back to. Tokens come from the adapters only; this product assembles
 * none of its own.
 */
class ArraySubmitPlanTest {

    private static ArraySweepPlanner.SweepPlan sweep(int count) {
        return ArraySweepPlanner.plan("ecutwfc", 30.0, 10.0, count, "si-cut")
                .getValue().orElseThrow();
    }

    @Test
    void slurmSingleArrayDraftUsesOwnedFlagAndParseLane() {
        OperationResult<ArraySubmitPlan.SubmitPlan> drafted = ArraySubmitPlan.plan(
                sweep(3), new SlurmSchedulerAdapter());
        assertTrue(drafted.isSuccess(), drafted.getMessage());
        ArraySubmitPlan.SubmitPlan plan = drafted.getValue().orElseThrow();
        assertEquals(ArraySubmitPlan.Shape.SINGLE_ARRAY, plan.getShape());
        assertEquals("slurm", plan.getAdapterName());
        assertEquals("SLURM_ARRAY_TASK_ID", plan.getEnvVar());
        assertEquals(java.util.List.of(
                "sbatch --array=1-3 <staged-script-path>"), plan.getCommands(),
                "flag tokens come between the submit binary and the anchor, exactly");
        String block = plan.reviewBlock();
        assertTrue(block.contains("# shape: SINGLE ARRAY SUBMISSION (scheduler 'slurm' "
                + "OWNS array semantics)"), block);
        assertTrue(block.contains("the 'slurm' adapter's own parseJobId on THAT command's "
                + "stdout"), block + " | " + "the parse lane names its single owner");
        assertTrue(block.contains("$SLURM_ARRAY_TASK_ID selects tasks.jsonl line N "
                + "(1-based, exact)"), block);
        assertTrue(block.contains("#   [1] the staged script still carries its exit-2 "
                + "guard"), block + " | " + "the REQUIRED checklist is on every block");
        assertTrue(block.lines().allMatch(line -> line.startsWith("#")),
                "every review line is a comment - the block can never execute");
        assertFalse(plan.isLoopCapped());
    }

    @Test
    void sgeAndPjmOwnDistinctArrayForms() {
        ArraySubmitPlan.SubmitPlan sge = ArraySubmitPlan.plan(sweep(4),
                new SgeSchedulerAdapter()).getValue().orElseThrow();
        assertEquals(java.util.List.of("qsub -t 1-4 <staged-script-path>"),
                sge.getCommands());
        assertEquals("SGE_TASK_ID", sge.getEnvVar());
        ArraySubmitPlan.SubmitPlan pjm = ArraySubmitPlan.plan(sweep(4),
                new PjmSchedulerAdapter()).getValue().orElseThrow();
        assertEquals(java.util.List.of("pjsub --bulk --sparam 1-4 <staged-script-path>"),
                pjm.getCommands(),
                "PJM bulk form is distinctively NOT a SLURM --array clone");
        assertEquals("PJM_BULKNUM", pjm.getEnvVar());
    }

    @Test
    void pbsRefusesArrayKnowledgeAndFallsBackToTheHonestLoop() {
        OperationResult<ArraySubmitPlan.SubmitPlan> drafted = ArraySubmitPlan.plan(
                sweep(3), new PbsSchedulerAdapter());
        assertTrue(drafted.isSuccess(), drafted.getMessage());
        ArraySubmitPlan.SubmitPlan plan = drafted.getValue().orElseThrow();
        assertEquals(ArraySubmitPlan.Shape.PER_TASK_LOOP, plan.getShape());
        assertTrue(plan.getEnvVar() == null, "a loop has no task env variable");
        assertTrue(plan.getReason().contains("PBS Professional (-J flag, PBS_ARRAY_INDEX) "
                + "and Torque (-t flag, PBS_ARRAYID) diverge"),
                "the adapter's verbatim divergence reason rides the plan: "
                        + plan.getReason());
        assertEquals(java.util.List.of(
                "qsub <staged-script-path>/si-cut-001/job.sh",
                "qsub <staged-script-path>/si-cut-002/job.sh",
                "qsub <staged-script-path>/si-cut-003/job.sh"), plan.getCommands(),
                "each task dir gets its own documented submit - no flag is invented");
        String block = plan.reviewBlock();
        assertTrue(block.contains("# shape: PER-TASK SUBMIT LOOP - the 'pbs' adapter "
                + "deliberately owns no array form:"), block);
        assertTrue(block.contains("EACH submit's stdout feeds the 'pbs' adapter's own "
                + "parseJobId - 3 ids, one per task directory"), block);
    }

    @Test
    void longLoopsCapTheReviewDisplayAndDiscloseTheRest() {
        ArraySubmitPlan.SubmitPlan plan = ArraySubmitPlan.plan(sweep(10),
                new PbsSchedulerAdapter()).getValue().orElseThrow();
        assertEquals(ArraySubmitPlan.Shape.PER_TASK_LOOP, plan.getShape());
        assertEquals(ArraySubmitPlan.MAX_SHOWN_LOOP_LINES, plan.getCommands().size());
        assertTrue(plan.isLoopCapped());
        assertTrue(plan.reviewBlock().contains("... (4 more line(s) elided in review; "
                + "every task still gets its own submit - the cap is display-only)"),
                plan.reviewBlock());
        assertTrue(plan.getCommands().get(0).endsWith("si-cut-001/job.sh"));
        assertTrue(plan.getCommands().get(ArraySubmitPlan.MAX_SHOWN_LOOP_LINES - 1)
                .endsWith("si-cut-006/job.sh"));
    }

    @Test
    void specGuardsRefuseDegenerateRangesAndUnownedRenders() {
        ArraySubmitSpec spec = new SlurmSchedulerAdapter().arraySubmitSpec();
        assertEquals(1, spec.renderFlagTokens(1, 3).length);
        assertThrows(IllegalArgumentException.class, () -> spec.renderFlagTokens(0, 3),
                "array ranges are 1-based");
        assertThrows(IllegalArgumentException.class, () -> spec.renderFlagTokens(5, 3),
                "an inverted range can never render a flag silently");
        ArraySubmitSpec unowned = new PbsSchedulerAdapter().arraySubmitSpec();
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> unowned.renderFlagTokens(1, 3));
        assertTrue(refused.getMessage().contains("no owned array form"),
                refused.getMessage());
        assertEquals("sbatch(1) man page: --array=<index_list>", spec.getDocAnchor(),
                "every owned spec names its documentation anchor");
    }

    @Test
    void nullArgumentsFailLoudlyNotSilently() {
        assertThrows(NullPointerException.class,
                () -> ArraySubmitPlan.plan(null, new SlurmSchedulerAdapter()));
        NullPointerException noAdapter = assertThrows(NullPointerException.class,
                () -> ArraySubmitPlan.plan(sweep(2), null));
        assertTrue(noAdapter.getMessage().contains("never hand-assembled"),
                noAdapter.getMessage());
    }
}
