/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.JobStateGuard.State;
import quantumforge.remote.JobStateGuard.Verdict;

class JobStateGuardTest {

    @Test
    void forwardEdgesPassAndBackwardEdgesRewriteHistoryRefused() {
        assertTrue(JobStateGuard.transition("staged", "submitted").isSuccess());
        assertTrue(JobStateGuard.transition("submitted", "pending").isSuccess());
        assertTrue(JobStateGuard.transition("pending", "running").isSuccess());
        assertTrue(JobStateGuard.transition("running", "completed").isSuccess());

        OperationResult<Verdict> backward = JobStateGuard.transition("running", "pending");
        assertFalse(backward.isSuccess());
        assertEquals("JOBSTATE_TRANSITION", backward.getCode(),
                "a job never moves backward - backward edges rewrite history");
        assertEquals("JOBSTATE_TRANSITION", JobStateGuard.transition(
                "submitted", "staged").getCode());
        assertEquals("JOBSTATE_TRANSITION", JobStateGuard.transition(
                "pending", "submitted").getCode());
    }

    @Test
    void terminalsNeverLeave() {
        for (String terminal : new String[] {"completed", "failed", "cancelled",
                "unknown"}) {
            OperationResult<Verdict> out = JobStateGuard.transition(terminal, "running");
            assertFalse(out.isSuccess(), terminal + " -> running must refuse");
            assertEquals("JOBSTATE_TRANSITION", out.getCode());
        }
        assertTrue(JobStateGuard.transition("failed", "running").getMessage()
                .contains("TERMINAL"));
    }

    @Test
    void unknownIsTheOnlySidewaysEdgeAndIsLabeledReconciliation() {
        OperationResult<Verdict> result = JobStateGuard.transition("running", "unknown");
        assertTrue(result.isSuccess(), result.toString());
        assertTrue(result.getValue().orElseThrow().isReconciliation(),
                "-> UNKNOWN is reconciliation, NOT progress - and says so");
        assertFalse(JobStateGuard.transition("unknown", "running").isSuccess(),
                "UNKNOWN is terminal: once recorded unknown you reconcile by re-reading, "
                        + "not by claiming progress");
        assertEquals("JOBSTATE_TRANSITION", JobStateGuard.transition(
                "running", "running").getCode(), "self-edges are caller bugs");
    }

    @Test
    void cancelledRequiresLiveAndSelfStagesRefuseIt() {
        assertTrue(JobStateGuard.transition("pending", "cancelled").isSuccess());
        assertTrue(JobStateGuard.transition("running", "cancelled").isSuccess());
        OperationResult<Verdict> stagedCancel = JobStateGuard.transition("staged",
                "cancelled");
        assertFalse(stagedCancel.isSuccess());
        assertEquals("JOBSTATE_TRANSITION", stagedCancel.getCode());
        assertTrue(stagedCancel.getMessage().contains("never live"),
                "a staged-only job was never submitted - it cannot be 'cancelled'");
    }

    @Test
    void signalMapsAcrossSchedulersAndUnknownSignalsStayUnknown() {
        assertEquals(State.PENDING, JobStateGuard.mapSignal(
                "slurm", "PD").getValue().orElseThrow());
        assertEquals(State.RUNNING, JobStateGuard.mapSignal(
                "slurm", "R").getValue().orElseThrow());
        assertEquals(State.COMPLETED, JobStateGuard.mapSignal(
                "slurm", "CD").getValue().orElseThrow());
        assertEquals(State.FAILED, JobStateGuard.mapSignal(
                "slurm", "TO").getValue().orElseThrow());
        assertEquals(State.CANCELLED, JobStateGuard.mapSignal(
                "slurm", "CA").getValue().orElseThrow());
        assertEquals(State.RUNNING, JobStateGuard.mapSignal(
                "pbs", "E").getValue().orElseThrow(), "PBS exiting is still LIVE");
        assertEquals(State.COMPLETED, JobStateGuard.mapSignal(
                "pbs", "F").getValue().orElseThrow());
        assertEquals(State.FAILED, JobStateGuard.mapSignal(
                "pbs", "X").getValue().orElseThrow());
        assertEquals(State.PENDING, JobStateGuard.mapSignal(
                "sge", "qw").getValue().orElseThrow(), "case-insensitive signal");
        assertEquals(State.RUNNING, JobStateGuard.mapSignal(
                "sge", "dr").getValue().orElseThrow(),
                "deletion-underway is still LIVE until verified gone");

        OperationResult<State> unknown = JobStateGuard.mapSignal("slurm", "XYZZY");
        assertTrue(unknown.isSuccess(),
                "an unrecognized signal is a SUCCESS carrying UNKNOWN - guessing errors");
        assertEquals(State.UNKNOWN, unknown.getValue().orElseThrow());
        assertTrue(unknown.getMessage().contains("never a guess"));

        OperationResult<State> freeScheduler = JobStateGuard.mapSignal("torque", "R");
        assertFalse(freeScheduler.isSuccess());
        assertEquals("JOBSTATE_SCHEDULER", freeScheduler.getCode());
    }
}
