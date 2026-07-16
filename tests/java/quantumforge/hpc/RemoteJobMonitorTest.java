package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;
import quantumforge.ssh.SshTransport;

class RemoteJobMonitorTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsSchedulerStatuses() {
        assertEquals(JobState.PENDING, RemoteJobMonitor.mapStatus("PD"));
        assertEquals(JobState.RUNNING, RemoteJobMonitor.mapStatus("R"));
        assertEquals(JobState.COMPLETED, RemoteJobMonitor.mapStatus("CD"));
        assertEquals(JobState.CANCELLED, RemoteJobMonitor.mapStatus("CA"));
        assertEquals(JobState.FAILED, RemoteJobMonitor.mapStatus("F"));
        assertTrue(RemoteJobMonitor.isTerminal(JobState.COMPLETED));
        assertFalse(RemoteJobMonitor.isTerminal(JobState.RUNNING));
    }

    @Test
    void pollOnceUpdatesRecord() throws Exception {
        JobRecord record = new JobRecord("local", "slurm", "site", tempDir.toString());
        record.setSchedulerJobId("77");
        FakeTransport fake = new FakeTransport("R\n");
        RemoteJobMonitor monitor = new RemoteJobMonitor(
                fake, new SlurmSchedulerAdapter(), record, null,
                java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(50));
        AtomicReference<RemoteJobMonitor.StatusUpdate> seen = new AtomicReference<>();
        monitor.addListener(seen::set);
        OperationResult<RemoteJobMonitor.StatusUpdate> result = monitor.pollOnce();
        assertTrue(result.isSuccess(), result.toString());
        assertEquals(JobState.RUNNING, record.getState());
        assertTrue(seen.get() != null);
        monitor.close();
    }

    static final class FakeTransport implements SshTransport {
        private final String status;

        FakeTransport(String status) {
            this.status = status;
        }

        @Override public OperationResult<Void> connect() {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return true; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile) {
            try {
                if (stdoutFile != null) {
                    Files.writeString(stdoutFile, status);
                }
                if (stderrFile != null) {
                    Files.writeString(stderrFile, "");
                }
            } catch (Exception ignored) {
            }
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
