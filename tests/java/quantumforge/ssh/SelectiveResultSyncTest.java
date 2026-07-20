package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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

    @Test
    void incompleteAttachesTheWholeTruthReport() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.remote.put("/scratch/jobs/a1/have.log", "ok\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        ResultSyncManifest manifest = ResultSyncManifest.of("have.log", "missing.req");
        OperationResult<SelectiveResultSync.SyncReport> result =
                sync.sync("jobs/a1", tempDir.resolve("o1"), manifest, false);
        assertFalse(result.isSuccess());
        assertEquals("SYNC_INCOMPLETE", result.getCode());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow(
                "a partial sync must ATTACH its report - the caller needs the"
                        + " whole truth, not a bare message");
        assertEquals(List.of("have.log"), report.getDownloaded(),
                "what DID download stays visible on the failure path");
        assertEquals(List.of("missing.req"), report.getMissingRequired());
        assertTrue(result.getMessage().contains("missing.req"), result.getMessage());
    }

    @Test
    void largeOptionalIsSkippedButNamed() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.remote.put("/scratch/jobs/a1/x.log", "ok\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        ResultSyncManifest manifest = new ResultSyncManifest(List.of(
                new ResultSyncManifest.Entry("big.dat",
                        ResultSyncManifest.Priority.LARGE_OPTIONAL),
                new ResultSyncManifest.Entry("x.log",
                        ResultSyncManifest.Priority.REQUIRED)));
        OperationResult<SelectiveResultSync.SyncReport> result =
                sync.sync("jobs/a1", tempDir.resolve("o2"), manifest, false);
        assertTrue(result.isSuccess(), result.toString());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow();
        assertEquals(List.of("big.dat"), report.getSkippedLarge(),
                "declared-but-not-fetched is information, not noise");
        assertTrue(result.getMessage().contains("skipped 1 declared large"),
                result.getMessage());
        assertFalse(Files.exists(tempDir.resolve("o2/big.dat")),
                "the large payload was not fetched without the explicit opt-in");
    }

    @Test
    void transportDeathStopsTheWalkWithoutFabricatingMissing() throws Exception {
        DyingTransport fake = new DyingTransport();
        fake.remote.put("/scratch/jobs/a1/a.log", "ok\n");
        fake.dieAt = "b.log";
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        ResultSyncManifest manifest = ResultSyncManifest.of("a.log", "b.log", "c.log");
        OperationResult<SelectiveResultSync.SyncReport> result =
                sync.sync("jobs/a1", tempDir.resolve("o3"), manifest, false);
        assertFalse(result.isSuccess());
        assertEquals("SYNC_TRANSPORT", result.getCode());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow(
                "the partial report must be attached here too");
        assertEquals(List.of("a.log"), report.getDownloaded());
        assertTrue(report.getMissingRequired().isEmpty(),
                "c.log was never probed - a dead channel fabricates no absence"
                        + " evidence, so NOTHING is declared missing");
        assertTrue(result.getMessage().contains("b.log"), result.getMessage());
        assertTrue(result.getMessage().contains("NOT declared missing"),
                result.getMessage());
    }

    @Test
    void checksumCacheFailuresDegradeToWarningsNeverVerdicts() throws Exception {
        FakeTransport fake = new FakeTransport();
        fake.remote.put("/scratch/jobs/a1/espresso.log", "ok\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        // A DIRECTORY as the cache file breaks load/save/probe-record in every
        // direction - the sync must still complete honestly.
        sync.setChecksumCache(new SyncChecksumCache(tempDir));
        ResultSyncManifest manifest = ResultSyncManifest.of("espresso.log");
        OperationResult<SelectiveResultSync.SyncReport> result =
                sync.sync("jobs/a1", tempDir.resolve("o4"), manifest, false);
        assertTrue(result.isSuccess(),
                "a cache is an optimization; its failure must never sink a sync: "
                        + result);
        assertEquals("SYNC_OK", result.getCode());
        assertTrue(Files.isRegularFile(tempDir.resolve("o4/espresso.log")));
    }

    /** Connected transport that drops the channel at a named file. */
    static final class DyingTransport extends FakeTransport {
        String dieAt;

        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            if (remotePath.endsWith("/" + this.dieAt)) {
                this.connected = false;
                return OperationResult.failed("SSH_EXEC_ERROR",
                        "channel broke mid-transfer", null);
            }
            return super.downloadFile(remotePath, localFile);
        }
    }

    /** Batch-146 wire: sha-aware fake for the pinned (verified) download path. */
    static final class ShaFakeTransport extends FakeTransport {
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            try {
                if ("sha256sum".equals(command[0])) {
                    String data = this.remote.get(command[1]);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                    }
                    Files.writeString(stdoutFile, sha256Hex(data) + "  " + command[1] + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                if ("test".equals(command[0])) {
                    return this.remote.containsKey(command[2])
                            ? OperationResult.success("SSH_EXEC_OK", "ok", 0)
                            : OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                }
                return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }

        static String sha256Hex(String text) {
            try {
                java.security.MessageDigest md =
                        java.security.MessageDigest.getInstance("SHA-256");
                return java.util.HexFormat.of().formatHex(
                        md.digest(text.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @Test
    void pinnedEntriesDownloadVerifiedAndThePostureIsStated() throws Exception {
        ShaFakeTransport fake = new ShaFakeTransport();
        fake.remote.put("/scratch/jobs/a1/espresso.log", "log v2\n");
        fake.remote.put("/scratch/jobs/a1/espresso.log.scf", "JOB DONE\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        ResultSyncManifest manifest = ResultSyncManifest.of(
                "espresso.log", "espresso.log.scf");
        OperationResult<SelectiveResultSync.SyncReport> result = sync.sync("jobs/a1",
                tempDir.resolve("o3"), manifest, false,
                Map.of("espresso.log", ShaFakeTransport.sha256Hex("log v2\n")));
        assertTrue(result.isSuccess(), result.toString());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow();
        assertTrue(report.getDownloaded().contains("espresso.log (pin-verified)"),
                "the pinned entry is named with its verification marker: "
                        + report.getDownloaded());
        assertTrue(report.getDownloaded().contains("espresso.log.scf"),
                "the unpinned sibling follows the batch-128 walk");
        assertTrue(result.getMessage().contains("1 of them were hash-verified"),
                result.getMessage());
        assertEquals("log v2\n", Files.readString(
                tempDir.resolve("o3").resolve("espresso.log")));
        assertFalse(Files.exists(tempDir.resolve("o3").resolve("espresso.log.qftmp")),
                "no verified temp debris survives the atomic rename");
    }

    @Test
    void unpinnedSyncStatesItWasNotHashVerified() throws Exception {
        ShaFakeTransport fake = new ShaFakeTransport();
        fake.remote.put("/scratch/jobs/a1/espresso.log", "log\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        OperationResult<SelectiveResultSync.SyncReport> result = sync.sync("jobs/a1",
                tempDir.resolve("o4"), ResultSyncManifest.of("espresso.log"), false);
        assertTrue(result.isSuccess(), result.toString());
        assertTrue(result.getMessage().contains(
                "Downloads were NOT hash-verified (no pins supplied)."),
                "the unpinned posture is stated, never implied: " + result.getMessage());
    }

    @Test
    void aVerificationMismatchIsASecurityFindingNeverAQuietMissing() throws Exception {
        ShaFakeTransport fake = new ShaFakeTransport();
        fake.remote.put("/scratch/jobs/a1/espresso.log", "log v2\n");
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        String wrongPin = "0".repeat(64);
        OperationResult<SelectiveResultSync.SyncReport> result = sync.sync("jobs/a1",
                tempDir.resolve("o5"), ResultSyncManifest.of("espresso.log"), false,
                Map.of("espresso.log", wrongPin));
        assertFalse(result.isSuccess());
        assertEquals("SYNC_INCOMPLETE", result.getCode());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow();
        assertTrue(report.getMissingRequired().isEmpty(),
                "a mismatch is NOT absence evidence: " + report.getMissingRequired());
        assertEquals(List.of("espresso.log (verification refused: "
                + "TRANSFER_SOURCE_MISMATCH)"), report.getFailed(),
                "the typed code names the security finding");
        assertTrue(result.getMessage().contains("SECURITY/failed entries"),
                result.getMessage());
        assertFalse(Files.exists(tempDir.resolve("o5").resolve("espresso.log")),
                "the wrong-source pre-check downloaded nothing");
    }

    @Test
    void anAbsentPinnedSourceStillSortsByPriority() throws Exception {
        ShaFakeTransport fake = new ShaFakeTransport();
        SelectiveResultSync sync = new SelectiveResultSync(fake, "/scratch");
        OperationResult<SelectiveResultSync.SyncReport> result = sync.sync("jobs/a1",
                tempDir.resolve("o6"), ResultSyncManifest.of("espresso.log"), false,
                Map.of("espresso.log", ShaFakeTransport.sha256Hex("x")));
        assertFalse(result.isSuccess());
        SelectiveResultSync.SyncReport report = result.getValue().orElseThrow();
        assertEquals(List.of("espresso.log"), report.getMissingRequired(),
                "TRANSFER_SOURCE_MISSING stays an absence verdict even when pinned");
        assertTrue(report.getFailed().isEmpty());
    }

    @Test
    void aNullPinsMapRefusesLoudly() {
        SelectiveResultSync sync = new SelectiveResultSync(new FakeTransport(), "/scratch");
        NullPointerException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> sync.sync("jobs/a1", tempDir.resolve("o7"),
                        ResultSyncManifest.of("x.log"), false, null));
        org.junit.jupiter.api.Assertions.assertTrue(
                thrown.getMessage().contains("Map.of()"),
                "the refusal names the honest unpinned posture: " + thrown.getMessage());
    }
}
