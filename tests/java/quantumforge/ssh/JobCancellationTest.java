package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.hpc.SlurmSchedulerAdapter;
import quantumforge.operation.OperationResult;

class JobCancellationTest {

    @TempDir
    Path tempDir;

    @Test
    void refusesCancelWithoutJobId() {
        JobRecord record = new JobRecord("local1", "slurm", "site", "/tmp/p");
        FakeTransport fake = new FakeTransport();
        OperationResult<JobRecord> result = JobCancellation.cancel(
                fake, new SlurmSchedulerAdapter(), record);
        assertFalse(result.isSuccess());
        assertTrue(result.getCode().contains("JOB_ID"));
    }

    @Test
    void cancelsByParsedIdOnly() throws Exception {
        JobRecord record = new JobRecord("local1", "slurm", "site", "/tmp/p");
        record.setSchedulerJobId("42");
        record.transition(JobState.RUNNING, "running");
        FakeTransport fake = new FakeTransport();
        fake.statusShape = Shape.ABSENT_NEEDLE;
        OperationResult<JobRecord> result = JobCancellation.cancel(
                fake, new SlurmSchedulerAdapter(), record);
        assertTrue(result.isSuccess(), result.toString());
        assertEquals("JOB_CANCELLED", result.getCode());
        assertTrue(Arrays.equals(fake.lastCommand, new String[] {"squeue", "-j", "42", "-h", "-o", "%T"})
                || (fake.lastCommand != null && fake.lastCommand[0].equals("scancel")),
                "cancel then verify is the command order");
        assertEquals(JobState.CANCELLED, record.getState(),
                "absence is verified by the adapter needle -> CANCELLED is earned");
    }

    @Test
    void transportFailureOnTheVerifyingQueryIsNeverACancellation() throws Exception {
        JobRecord record = new JobRecord("local1", "slurm", "site", "/tmp/p");
        record.setSchedulerJobId("42");
        record.transition(JobState.RUNNING, "running");
        FakeTransport fake = new FakeTransport();
        fake.statusShape = Shape.TRANSPORT_ERROR;
        OperationResult<JobRecord> result = JobCancellation.cancel(
                fake, new SlurmSchedulerAdapter(), record);
        assertFalse(result.isSuccess(),
                "a broken verification query can never prove absence");
        assertEquals("CANCEL_UNVERIFIED", result.getCode());
        assertEquals(JobState.UNKNOWN, record.getState());
        assertTrue(result.getMessage().contains("NOT declared cancelled"),
                result.getMessage());
    }

    @Test
    void emptyStatusOutputIsNotAVerdict() throws Exception {
        JobRecord record = new JobRecord("local1", "slurm", "site", "/tmp/p");
        record.setSchedulerJobId("42");
        record.transition(JobState.RUNNING, "running");
        FakeTransport fake = new FakeTransport();
        fake.statusShape = Shape.EMPTY_SUCCESS;
        OperationResult<JobRecord> result = JobCancellation.cancel(
                fake, new SlurmSchedulerAdapter(), record);
        assertFalse(result.isSuccess(),
                "exit 0 with empty output is not a documented absence shape");
        assertEquals("CANCEL_UNVERIFIED", result.getCode());
        assertEquals(JobState.UNKNOWN, record.getState());
        assertTrue(result.getMessage().contains("absence is NOT proven"), result.getMessage());
    }

    @Test
    void schedulerCancelSignalIsAcceptedByTheOwnedMapping() throws Exception {
        JobRecord record = new JobRecord("local1", "slurm", "site", "/tmp/p");
        record.setSchedulerJobId("42");
        record.transition(JobState.RUNNING, "running");
        FakeTransport fake = new FakeTransport();
        fake.statusShape = Shape.CANCEL_SIGNAL;
        OperationResult<JobRecord> result = JobCancellation.cancel(
                fake, new SlurmSchedulerAdapter(), record);
        assertTrue(result.isSuccess(), result.toString());
        assertEquals(JobState.CANCELLED, record.getState(),
                "CA is the SLURM cancel-in-progress signal owned by the guard table");

        JobRecord alive = new JobRecord("local2", "slurm", "site", "/tmp/p");
        alive.setSchedulerJobId("43");
        alive.transition(JobState.RUNNING, "running");
        FakeTransport fake2 = new FakeTransport();
        fake2.statusShape = Shape.STILL_RUNNING;
        OperationResult<JobRecord> result2 = JobCancellation.cancel(
                fake2, new SlurmSchedulerAdapter(), alive);
        assertFalse(result2.isSuccess());
        assertEquals("CANCEL_UNVERIFIED", result2.getCode(),
                "a job that still answers the status query is not cancelled");
        assertEquals(JobState.UNKNOWN, alive.getState());
    }

    enum Shape { ABSENT_NEEDLE, TRANSPORT_ERROR, EMPTY_SUCCESS, CANCEL_SIGNAL, STILL_RUNNING }

    static final class FakeTransport implements SshTransport {
        String[] lastCommand;
        Shape statusShape = Shape.ABSENT_NEEDLE;

        @Override public OperationResult<Void> connect() {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return true; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile) {
            this.lastCommand = command;
            boolean status = command[0].equals("squeue");
            try {
                if (!status) {
                    // The cancel command itself succeeds (exit 0, empty output).
                    if (stdoutFile != null) Files.writeString(stdoutFile, "");
                    if (stderrFile != null) Files.writeString(stderrFile, "");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                switch (this.statusShape) {
                case ABSENT_NEEDLE:
                    if (stdoutFile != null) Files.writeString(stdoutFile, "");
                    if (stderrFile != null) Files.writeString(stderrFile,
                            "slurm_load_jobs error: Invalid job id specified");
                    return OperationResult.failed("SSH_EXEC_FAILED",
                            "Remote command exited 1: squeue ...", null);
                case TRANSPORT_ERROR:
                    return OperationResult.failed("SSH_EXEC_ERROR",
                            "Remote exec failed: channel broke", null);
                case EMPTY_SUCCESS:
                    if (stdoutFile != null) Files.writeString(stdoutFile, "");
                    if (stderrFile != null) Files.writeString(stderrFile, "");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case CANCEL_SIGNAL:
                    if (stdoutFile != null) Files.writeString(stdoutFile, "CA\n");
                    if (stderrFile != null) Files.writeString(stderrFile, "");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case STILL_RUNNING:
                default:
                    if (stdoutFile != null) Files.writeString(stdoutFile, "R\n");
                    if (stderrFile != null) Files.writeString(stderrFile, "");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", "fake IO: " + ex, null);
            }
        }
        @Override public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { }
    }
}
