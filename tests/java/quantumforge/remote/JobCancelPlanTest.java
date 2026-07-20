/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.JobCancelPlan.CancelPlan;

class JobCancelPlanTest {

    @Test
    void slurmArrayPlanRendersTheReviewBlockWithOnlySuccessSignal() {
        OperationResult<CancelPlan> result = JobCancelPlan.validate(
                "SLURM", "4521_3", "4521_3");
        assertTrue(result.isSuccess(), result.toString());
        CancelPlan plan = result.getValue().orElseThrow();
        assertEquals("slurm", plan.getScheduler(), "typed enum normalizes lowercase");
        assertEquals("scancel 4521_3", plan.getCommand());
        assertEquals("squeue -j 4521_3 -h -o %T", plan.getStatusCommand(),
                "the adapter's own status query is the ONLY accepted success signal");
        String text = plan.render();
        assertTrue(text.contains("NOTHING has been cancelled"), text);
        assertTrue(text.contains("cancel_command   = scancel 4521_3"), text);
        assertTrue(text.contains("status_command   = squeue -j 4521_3 -h -o %T"), text);
        assertTrue(text.contains("kill by process NAME"), text,
                "the forbidden lines are stated explicitly");
        assertTrue(text.contains("ONLY success signal"), text);
        assertTrue(text.contains("NEVER a successful cancellation"), text);
        assertTrue(text.contains("record CANCELLED"), text);
        assertTrue(text.contains("never carpet-FAILED"), text);
        assertTrue(text.contains("holds no"), text,
                "the grammar-owner line declares the no-drift architecture");
    }

    @Test
    void pbsServerSuffixAndSchedulerCommands() {
        OperationResult<CancelPlan> pbs = JobCancelPlan.validate(
                "pbs", "9156.hpc02", "9156.hpc02");
        assertTrue(pbs.isSuccess(), pbs.toString());
        assertEquals("qdel 9156.hpc02", pbs.getValue().orElseThrow().getCommand());
        // Batch-126 factual correction, pinned so it cannot regress: Fujitsu
        // PJM's cancel command is 'pjdel' (Fujitsu TCS manual; Kyushu
        // University Genkai docs) - an earlier draft of this plan said 'pdel'.
        assertEquals("pjdel 7712", JobCancelPlan.validate(
                "pjm", "7712", "7712").getValue().orElseThrow().getCommand());
        assertEquals("pjstat -S 7712", JobCancelPlan.validate(
                "pjm", "7712", "7712").getValue().orElseThrow().getStatusCommand());
        assertEquals("qdel 8800", JobCancelPlan.validate(
                "sge", "8800", "8800").getValue().orElseThrow().getCommand());
    }

    @Test
    void planCommandsAreTheAdapterCommandsVerbatim() {
        // THE no-drift proof: the review channel renders exactly what the
        // runtime channel would execute, per scheduler.
        String[][] cases = {
                {"slurm", "4521_3"}, {"pbs", "9156.hpc02"},
                {"pjm", "7712"}, {"sge", "8800"}};
        for (String[] pair : cases) {
            CancelPlan plan = JobCancelPlan.validate(pair[0], pair[1], pair[1])
                    .getValue().orElseThrow();
            quantumforge.hpc.SchedulerAdapter adapter =
                    quantumforge.hpc.SchedulerAdapters.forName(pair[0]).orElseThrow();
            assertEquals(String.join(" ", adapter.cancelCommand(pair[1])),
                    plan.getCommand(), pair[0] + " cancel review = adapter tokens");
            assertEquals(String.join(" ", adapter.statusCommand(pair[1])),
                    plan.getStatusCommand(), pair[0] + " status review = adapter tokens");
        }
    }

    @Test
    void freeFormSchedulersAndIdsRefuse() {
        assertEquals("CANCEL_SCHEDULER", JobCancelPlan.validate(
                "torque", "123", "123").getCode(),
                "free-form schedulers never reach a cancel command");
        assertEquals("CANCEL_JOBID", JobCancelPlan.validate(
                "slurm", "4521; rm -rf /", "4521; rm -rf /").getCode(),
                "a free-form id can never become a command fragment");
        assertEquals("CANCEL_JOBID", JobCancelPlan.validate(
                "pbs", "4521_3", "4521_3").getCode(),
                "array syntax is SLURM-only - the pbs adapter owns that verdict");
        assertEquals("CANCEL_JOBID", JobCancelPlan.validate(
                "pjm", "4521_3", "4521_3").getCode(),
                "array syntax is SLURM-only - the pjm adapter owns that verdict");
        assertEquals("CANCEL_JOBID", JobCancelPlan.validate(
                "pjm", "7712.srv", "7712.srv").getCode(),
                "the PBS-style .server suffix is not PJM grammar");
        assertEquals("CANCEL_JOBID", JobCancelPlan.validate(
                "slurm", "", "").getCode());
        assertEquals("CANCEL_JOBID", JobCancelPlan.validate(
                "sge", "job123", "job123").getCode());
        String refusal = JobCancelPlan.validate(
                "pjm", "4521_3", "4521_3").getMessage();
        assertTrue(refusal.contains("ADAPTER-OWNED"), refusal,
                "the refusal names the single grammar owner");
    }

    @Test
    void confirmationMustRetypeTheIdExactly() {
        OperationResult<CancelPlan> loose = JobCancelPlan.validate(
                "slurm", "4521", "4521 ");
        assertFalse(loose.isSuccess(), "near-matches are not identity");
        assertEquals("CANCEL_CONFIRM", loose.getCode());
        OperationResult<CancelPlan> wrong = JobCancelPlan.validate(
                "slurm", "4521", "4520");
        assertEquals("CANCEL_CONFIRM", wrong.getCode(),
                "an off-by-one confirmation protects the neighbouring job");
        OperationResult<CancelPlan> empty = JobCancelPlan.validate(
                "slurm", "4521", "");
        assertEquals("CANCEL_CONFIRM", empty.getCode(),
                "a button click (empty confirmation) is not identity confirmation");
    }
}
