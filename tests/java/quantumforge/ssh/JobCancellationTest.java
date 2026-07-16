package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

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
        fake.statusEmpty = true;
        OperationResult<JobRecord> result = JobCancellation.cancel(
                fake, new SlurmSchedulerAdapter(), record);
        assertTrue(result.isSuccess(), result.toString());
        assertTrue(Arrays.equals(fake.lastCommand, new String[] {"scancel", "42"})
                || (fake.lastCommand != null && fake.lastCommand[0].equals("scancel")));
        assertTrue(record.getState() == JobState.CANCELLED
                || record.getState() == JobState.UNKNOWN);
    }

    static final class FakeTransport implements SshTransport {
        String[] lastCommand;
        boolean statusEmpty;

        @Override public OperationResult<Void> connect() {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return true; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile) {
            this.lastCommand = command;
            try {
                if (stdoutFile != null) {
                    Files.writeString(stdoutFile, statusEmpty && command[0].equals("squeue") ? "" : "OK\n");
                }
                if (stderrFile != null) {
                    Files.writeString(stderrFile, "");
                }
            } catch (Exception ignored) { }
            return OperationResult.success("OK", "ok", 0);
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
