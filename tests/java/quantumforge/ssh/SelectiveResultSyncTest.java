package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.hpc.ResultSyncManifest;
import quantumforge.operation.OperationResult;

class SelectiveResultSyncTest {

    @TempDir
    Path tempDir;

    @Test
    void downloadsOnlyManifestFilesThroughFakeTransport() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.remote.put("/scratch/jobs/a1/espresso.log", "log\n");
        fake.remote.put("/scratch/jobs/a1/espresso.log.scf", "JOB DONE\n");
        fake.remote.put("/scratch/jobs/a1/espresso.err.scf", "warn\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        ResultSyncManifest manifest = ResultSyncManifest.forWorkflow(
                quantumforge.run.RunningType.SCF, "espresso");
        Path local = tempDir.resolve("out");
        OperationResult<SelectiveResultSync.SyncReport> result =
                sync.sync("jobs/a1", local, manifest, false);
        assertTrue(result.isSuccess(), result.toString());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow();
        assertTrue(report.getDownloaded().contains("espresso.log.scf"));
        assertTrue(Files.isRegularFile(local.resolve("espresso.log.scf")));
    }

    /** Minimal connected transport for unit tests. */
    static final class FakeTransport implements SshTransport {
        final Map<String, String> remote = new HashMap<>();
        boolean connected = true;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile) {
            return OperationResult.success("OK", "ok", 0);
        }
        @Override public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            if (!remote.containsKey(remotePath)) {
                return OperationResult.failed("MISSING", "missing " + remotePath, null);
            }
            try {
                Files.createDirectories(localFile.getParent());
                Files.writeString(localFile, remote.get(remotePath), StandardCharsets.UTF_8);
                return OperationResult.success("OK", "ok", null);
            } catch (Exception ex) {
                return OperationResult.failed("IO", ex.getMessage(), ex);
            }
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { this.connected = false; }
    }
}
