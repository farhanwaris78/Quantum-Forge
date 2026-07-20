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

    @Test
    void delegatedMapStatusFixesTheOldDivergences() {
        // The substring mapper misread these; the OWNED guard table fixes them.
        assertEquals(JobState.COMPLETED, RemoteJobMonitor.mapStatus("pbs", "F"),
                "PBS F means FINISHED - the substring mapper declared FAILED");
        assertEquals(JobState.RUNNING, RemoteJobMonitor.mapStatus("pjm", "RUN"),
                "PJM codes were entirely unmapped before");
        assertEquals(JobState.CANCELLED, RemoteJobMonitor.mapStatus("slurm", "PR"),
                "SLURM PR (preempted) dies by cancel semantics per the guard");
        assertEquals(JobState.FAILED, RemoteJobMonitor.mapStatus("sge", "EQW"));
        assertEquals(JobState.PENDING, RemoteJobMonitor.mapStatus("slurm", "PD"));
        // The one-arg legacy pass is intentionally preserved (labeled fallback):
        assertEquals(JobState.FAILED, RemoteJobMonitor.mapStatus("F"),
                "scheduler-less legacy keeps its historic reading - the two-arg"
                        + " form with 'pbs' above shows the correction explicitly");
        // Unknown signals stay UNKNOWN (honest), never guessed:
        assertEquals(JobState.UNKNOWN, RemoteJobMonitor.mapStatus("slurm", "ZZ"));
        // Multi-token dumps fall back to the labeled legacy substring pass:
        assertEquals(JobState.RUNNING,
                RemoteJobMonitor.mapStatus("pbs", "job_state = R running"),
                "a qstat-dump shape the guard owns no exact token for falls to the"
                        + " legacy pass, which reads the R token");
    }

    @Test
    void goneRequiresTheDocumentedAbsenceNeedle() {
        JobRecord record = new JobRecord("local", "slurm", "site", tempDir.toString());
        record.setSchedulerJobId("77");
        FailingTransport fake = new FailingTransport("SSH_EXEC_FAILED", "",
                "slurm_load_jobs error: Invalid job id specified");
        RemoteJobMonitor monitor = new RemoteJobMonitor(
                fake, new SlurmSchedulerAdapter(), record, null,
                java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(50));
        AtomicReference<RemoteJobMonitor.StatusUpdate> seen = new AtomicReference<>();
        monitor.addListener(seen::set);
        OperationResult<RemoteJobMonitor.StatusUpdate> result = monitor.pollOnce();
        assertTrue(result.isSuccess(), result.toString());
        assertEquals("MONITOR_GONE", result.getCode());
        assertEquals(JobState.UNKNOWN, record.getState());
        assertTrue(seen.get() != null && seen.get().isTerminal());
        assertTrue(result.getMessage().contains("Invalid job id"), result.getMessage());
        monitor.close();
    }

    @Test
    void unexplainedSchedulerExitIsUnreadableNeverGone() {
        JobRecord record = new JobRecord("local", "slurm", "site", tempDir.toString());
        record.setSchedulerJobId("77");
        JobState before = record.getState();
        FailingTransport fake = new FailingTransport("SSH_EXEC_FAILED", "",
                "squeue: some grumble this build owns no needle for");
        RemoteJobMonitor monitor = new RemoteJobMonitor(
                fake, new SlurmSchedulerAdapter(), record, null,
                java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(50));
        OperationResult<RemoteJobMonitor.StatusUpdate> result = monitor.pollOnce();
        assertFalse(result.isSuccess(), "an unexplained exit can never be a verdict");
        assertEquals("MONITOR_QUERY_UNREADABLE", result.getCode());
        assertEquals(before, record.getState(), "no transition without a verdict");
        assertTrue(result.getMessage().contains("grumble"), result.getMessage());
        monitor.close();
    }

    @Test
    void transportFailureIsNeverAStatusVerdict() {
        JobRecord record = new JobRecord("local", "pjm", "site", tempDir.toString());
        record.setSchedulerJobId("77");
        JobState before = record.getState();
        FailingTransport fake = new FailingTransport("SSH_EXEC_ERROR", "", "");
        RemoteJobMonitor monitor = new RemoteJobMonitor(
                fake, new PjmSchedulerAdapter(), record, null,
                java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(50));
        OperationResult<RemoteJobMonitor.StatusUpdate> result = monitor.pollOnce();
        assertFalse(result.isSuccess());
        assertEquals("MONITOR_ERROR", result.getCode());
        assertTrue(result.getMessage().contains("never a status verdict"),
                result.getMessage());
        assertEquals(before, record.getState(),
                "a transport failure must not touch the state machine");
        monitor.close();
    }

    @Test
    void pjmStaysFailClosedBecauseNoNeedleIsPinned() {
        // Even with SSH_EXEC_FAILED + empty stdout, a pjstat failure can never
        // be absence for this build: no documented needle exists.
        JobRecord record = new JobRecord("local", "pjm", "site", tempDir.toString());
        record.setSchedulerJobId("77");
        FailingTransport fake = new FailingTransport("SSH_EXEC_FAILED", "",
                "pjstat: ERR. <anything>");
        RemoteJobMonitor monitor = new RemoteJobMonitor(
                fake, new PjmSchedulerAdapter(), record, null,
                java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(50));
        OperationResult<RemoteJobMonitor.StatusUpdate> result = monitor.pollOnce();
        assertFalse(result.isSuccess());
        assertEquals("MONITOR_QUERY_UNREADABLE", result.getCode());
        monitor.close();
    }

    static final class FailingTransport implements SshTransport {
        private final String code;
        private final String out;
        private final String err;

        FailingTransport(String code, String out, String err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }

        @Override public OperationResult<Void> connect() {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return true; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile) {
            try {
                if (stdoutFile != null) Files.writeString(stdoutFile, this.out);
                if (stderrFile != null) Files.writeString(stderrFile, this.err);
            } catch (Exception ignored) { }
            if ("SSH_EXEC_OK".equals(this.code)) {
                return OperationResult.success("SSH_EXEC_OK", "ok", 0);
            }
            return OperationResult.failed(this.code, "synthetic " + this.code, null);
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
