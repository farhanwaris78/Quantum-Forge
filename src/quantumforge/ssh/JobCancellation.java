/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.operation.OperationResult;

/**
 * Safe scheduler cancellation by parsed job ID only (never by process name).
 *
 * <p>Batch-127 tightening of the post-cancel verification (Roadmap #97's own
 * runtime): a cancellation is declared ONLY on proof of absence - either the
 * status query's stderr carries the adapter's DOCUMENTED absence needle, or
 * the scheduler is actively reporting its cancel-in-progress signal through
 * the owned {@code JobStateGuard} mapping. A transport failure on the
 * verifying query, an unexplained non-zero exit, or an empty output is
 * {@code CANCEL_UNVERIFIED} and the job is explicitly NOT declared
 * cancelled.</p>
 */
public final class JobCancellation {

    private JobCancellation() {
        // Utility.
    }

    public static OperationResult<JobRecord> cancel(SshTransport transport, SchedulerAdapter adapter,
                                                    JobRecord record) {
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(record, "record");
        if (!transport.isConnected()) {
            return OperationResult.unsupported("SSH_NOT_CONNECTED",
                    "No connected transport; job was not cancelled.");
        }
        String schedulerJobId = record.getSchedulerJobId();
        if (schedulerJobId == null || schedulerJobId.isBlank()) {
            return OperationResult.failed("JOB_ID_MISSING",
                    "No scheduler job id is recorded; refusing cancel-by-name.", null);
        }
        try {
            String[] command = adapter.cancelCommand(schedulerJobId);
            Path stdout = Files.createTempFile("qf-cancel-", ".out");
            Path stderr = Files.createTempFile("qf-cancel-", ".err");
            OperationResult<Integer> exec = transport.exec(command, stdout, stderr);
            if (!exec.isSuccess()) {
                record.transition(JobState.UNKNOWN, "cancel failed: " + exec.getMessage());
                return OperationResult.failed(exec.getCode(), exec.getMessage(), null);
            }
            // Post-cancel verification: the adapter's status query MUST prove
            // the job gone - that query is the ONLY success signal (Roadmap
            // #97). A transport failure or an empty output is NEVER a
            // successful cancellation: it is CANCEL_UNVERIFIED.
            Path statusOut = Files.createTempFile("qf-status-", ".out");
            Path statusErr = Files.createTempFile("qf-status-", ".err");
            OperationResult<Integer> status = transport.exec(
                    adapter.statusCommand(schedulerJobId), statusOut, statusErr);
            String statusText = Files.isRegularFile(statusOut)
                    ? Files.readString(statusOut).trim() : "";
            String statusErrText = Files.isRegularFile(statusErr)
                    ? Files.readString(statusErr).trim() : "";
            if (!status.isSuccess()) {
                boolean remoteNonZero = "SSH_EXEC_FAILED".equals(status.getCode());
                if (remoteNonZero && statusText.isEmpty()
                        && adapter.isJobAbsent(statusErrText)) {
                    // VERIFIED ABSENT: the scheduler itself says, in its
                    // documented words, that no such job exists.
                    record.transition(JobState.CANCELLED,
                            "cancel accepted; verified absent per the " + adapter.name()
                                    + " adapter's documented needle");
                } else {
                    // The verifying query itself is unreadable (transport
                    // failure, missing binary, permission, or an exit shape we
                    // own no needle for) - that is NOT a cancellation.
                    record.transition(JobState.UNKNOWN,
                            "cancel issued; verification unreadable: " + status.getMessage());
                    return OperationResult.failed("CANCEL_UNVERIFIED",
                            "Cancel command ran but the verifying status query is"
                                    + " unreadable (" + status.getMessage()
                                    + ") - the job is NOT declared cancelled.", null);
                }
            } else if (statusText.isEmpty()) {
                // Exit 0 with empty output: no pinned grammar declares that
                // absence, so there is no verdict.
                record.transition(JobState.UNKNOWN,
                        "cancel issued; status query returned empty output");
                return OperationResult.failed("CANCEL_UNVERIFIED",
                        "The status query returned empty output - absence is NOT proven,"
                                + " so the job is NOT declared cancelled.", null);
            } else {
                // The job answered the status query: still visible. The owned
                // signal table decides whether the scheduler is actively
                // cancelling it (CA on SLURM) or plainly alive.
                quantumforge.operation.OperationResult<quantumforge.remote.JobStateGuard.State>
                        mapped = quantumforge.remote.JobStateGuard.mapSignal(
                                adapter.name(), statusText.trim());
                if (mapped.isSuccess() && mapped.getValue().isPresent()
                        && mapped.getValue().get()
                            == quantumforge.remote.JobStateGuard.State.CANCELLED) {
                    record.transition(JobState.CANCELLED,
                            "scheduler reports the cancel-in-progress signal: " + statusText);
                } else {
                    record.transition(JobState.UNKNOWN,
                            "cancel issued but job still visible: " + statusText);
                    return OperationResult.failed("CANCEL_UNVERIFIED",
                            "Cancel command ran but job still appears active: "
                                    + statusText, null);
                }
            }
            return OperationResult.success("JOB_CANCELLED",
                    "Cancelled scheduler job " + schedulerJobId
                            + " (verified by the adapter's status query)", record);
        } catch (Exception ex) {
            return OperationResult.failed("CANCEL_ERROR",
                    "Cancellation failed: " + ex.getMessage(), ex);
        }
    }
}
