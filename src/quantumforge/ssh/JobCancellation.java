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
            // Best-effort post-cancel verification via status command.
            Path statusOut = Files.createTempFile("qf-status-", ".out");
            Path statusErr = Files.createTempFile("qf-status-", ".err");
            OperationResult<Integer> status = transport.exec(
                    adapter.statusCommand(schedulerJobId), statusOut, statusErr);
            String statusText = Files.isRegularFile(statusOut)
                    ? Files.readString(statusOut).trim() : "";
            if (!status.isSuccess() || statusText.isEmpty()
                    || statusText.toLowerCase().contains("invalid")
                    || statusText.toLowerCase().contains("unknown")) {
                record.transition(JobState.CANCELLED, "cancel accepted; job no longer listed");
            } else if (statusText.toUpperCase().contains("CANCEL")
                    || statusText.toUpperCase().contains("COMPLETING")
                    || statusText.toUpperCase().contains("CG")) {
                record.transition(JobState.CANCELLED, "scheduler reports cancelling/cancelled: " + statusText);
            } else {
                record.transition(JobState.UNKNOWN,
                        "cancel issued but job still visible: " + statusText);
                return OperationResult.failed("CANCEL_UNVERIFIED",
                        "Cancel command ran but job still appears active: " + statusText, null);
            }
            return OperationResult.success("JOB_CANCELLED",
                    "Cancelled scheduler job " + schedulerJobId, record);
        } catch (Exception ex) {
            return OperationResult.failed("CANCEL_ERROR",
                    "Cancellation failed: " + ex.getMessage(), ex);
        }
    }
}
