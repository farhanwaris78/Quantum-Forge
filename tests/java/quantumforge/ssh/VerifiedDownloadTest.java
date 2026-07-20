/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

/**
 * Batch-136 (#92 verified-download slice): the remote source pre-check -&gt;
 * sink-to-temp -&gt; local sha256 post-verify -&gt; rename semantics, tested
 * end to end against a scripted fake. The two distinct wrongs a download can
 * commit - fetching the wrong source, and landing corrupted bytes - are
 * refused at different, separately pinned points.
 */
class VerifiedDownloadTest {

    @TempDir
    Path tempDir;

    private SSHFileTransfer newTransfer(ScriptedTransport fake) {
        SSHFileTransfer transfer = new SSHFileTransfer(fake.server);
        transfer.setTransport(fake);
        transfer.setStagingRoot("/scratch");
        return transfer;
    }

    @Test
    void happyPathIsPreCheckThenTempThenVerifyThenRename() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml", payload);
        String sha = ScriptedTransport.sha256Hex(payload);
        Path dest = this.tempDir.resolve("out.xml");
        OperationResult<Void> result = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, false);
        assertTrue(result.isSuccess(), result.toString());
        assertEquals("TRANSFER_VERIFIED", result.getCode());
        assertEquals(List.of("sha256sum", "download"), fake.steps,
                "exactly the mandated sequence: source pre-check, then bytes");
        assertTrue(Files.exists(dest), "the verified payload lands at the final path");
        assertEquals("payload\n", Files.readString(dest));
        assertFalse(Files.exists(this.tempDir.resolve("out.xml.qftmp")),
                "the temp name must not survive a clean rename");
        assertTrue(result.getMessage().contains("remote source pre-check"),
                "the success message states the two-sided verification: "
                        + result.getMessage());
    }

    @Test
    void wrongSourceRefusesBeforeAnyByteMoves() {
        ScriptedTransport fake = new ScriptedTransport();
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml", "other".getBytes(StandardCharsets.UTF_8));
        String sha = ScriptedTransport.sha256Hex("payload\n".getBytes(StandardCharsets.UTF_8));
        Path dest = this.tempDir.resolve("out.xml");
        OperationResult<Void> result = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, false);
        assertFalse(result.isSuccess());
        assertEquals("TRANSFER_SOURCE_MISMATCH", result.getCode());
        assertEquals(List.of("sha256sum"), fake.steps,
                "NO download step may run for a mismatched source");
        assertTrue(result.getMessage().contains("NOTHING was downloaded"),
                result.getMessage());
        assertFalse(Files.exists(dest));
        assertFalse(Files.exists(this.tempDir.resolve("out.xml.qftmp")),
                "refusal before bytes move means no temp was ever created");
    }

    @Test
    void absentSourceAndDeadProbeRefuseBlindDistinction() {
        ScriptedTransport absent = new ScriptedTransport();
        String sha = ScriptedTransport.sha256Hex("payload\n".getBytes(StandardCharsets.UTF_8));
        Path dest = this.tempDir.resolve("out.xml");
        OperationResult<Void> missing = newTransfer(absent).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, false);
        assertFalse(missing.isSuccess());
        assertEquals("TRANSFER_SOURCE_MISSING", missing.getCode());
        assertEquals(List.of("sha256sum"), absent.steps);

        ScriptedTransport dead = new ScriptedTransport();
        dead.shaProbeDies = true;
        Path dest2 = this.tempDir.resolve("out2.xml");
        OperationResult<Void> unreadable = newTransfer(dead).downloadVerifiedResult(
                "jobs/a1/out.xml", dest2, sha, false);
        assertFalse(unreadable.isSuccess());
        assertEquals("TRANSFER_PROBE_UNREADABLE", unreadable.getCode(),
                "a dead probe is a blind probe - refuse, never proceed");
        assertEquals(List.of("sha256sum"), dead.steps);
    }

    @Test
    void unparseableRemoteHashRefusesBlind() {
        ScriptedTransport fake = new ScriptedTransport();
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml",
                "payload\n".getBytes(StandardCharsets.UTF_8));
        fake.garbageProbeOutput = true;
        String sha = ScriptedTransport.sha256Hex("payload\n".getBytes(StandardCharsets.UTF_8));
        OperationResult<Void> result = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", this.tempDir.resolve("out.xml"), sha, false);
        assertFalse(result.isSuccess());
        assertEquals("TRANSFER_SOURCE_UNREADABLE", result.getCode());
        assertEquals(List.of("sha256sum"), fake.steps);
    }

    @Test
    void corruptionInFlightIsCaughtByTheLocalPostVerify() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml", payload);
        fake.downloadOverride = "corrupted!!".getBytes(StandardCharsets.UTF_8);
        String sha = ScriptedTransport.sha256Hex(payload);
        Path dest = this.tempDir.resolve("out.xml");
        OperationResult<Void> result = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, false);
        assertFalse(result.isSuccess());
        assertEquals("SFTP_VERIFY_MISMATCH", result.getCode(),
                "the remote pre-check passed but the landed bytes differ");
        assertEquals(List.of("sha256sum", "download"), fake.steps);
        assertFalse(Files.exists(dest), "NOTHING was renamed into place");
        assertFalse(Files.exists(this.tempDir.resolve("out.xml.qftmp")),
                "cleanup scope is exactly our own temp");
        assertTrue(result.getMessage().contains("NOTHING was renamed into place"),
                result.getMessage());
    }

    @Test
    void overwritePostureIsPreCheckedBeforeTheProbeAndBytes() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml", payload);
        String sha = ScriptedTransport.sha256Hex(payload);
        Path dest = this.tempDir.resolve("out.xml");
        Files.writeString(dest, "old\n");
        OperationResult<Void> refused = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, false);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_LOCAL_EXISTS", refused.getCode());
        assertTrue(fake.steps.isEmpty(),
                "the local overwrite posture is checked before even the remote probe");
        assertEquals("old\n", Files.readString(dest), "the existing file is untouched");

        OperationResult<Void> allowed = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, true);
        assertTrue(allowed.isSuccess(), allowed.toString());
        assertEquals("payload\n", Files.readString(dest),
                "explicit overwrite permission replaces the old bytes");
    }

    @Test
    void missingDestinationDirectoryRefusesWithoutSideEffects() {
        ScriptedTransport fake = new ScriptedTransport();
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml", payload);
        OperationResult<Void> result = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", this.tempDir.resolve("nope/out.xml"),
                ScriptedTransport.sha256Hex(payload), false);
        assertFalse(result.isSuccess());
        assertEquals("TRANSFER_LOCAL_DIR", result.getCode());
        assertTrue(fake.steps.isEmpty(),
                "a transfer never mkdir's silently - refusal precedes all sides");
    }

    @Test
    void renameFailurePreservesTheVerifiedTempForForensics() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        fake.remoteFiles.put("/scratch/jobs/a1/out.xml", payload);
        String sha = ScriptedTransport.sha256Hex(payload);
        Path dest = this.tempDir.resolve("out.xml");
        Files.createDirectory(dest);
        Files.writeString(dest.resolve("occupant.txt"), "x\n"); // non-empty directory
        OperationResult<Void> result = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", dest, sha, true);
        assertFalse(result.isSuccess(),
                "spec-guaranteed DirectoryNotEmptyException on REPLACE into a non-empty dir");
        assertEquals("SFTP_LOCAL_RENAME", result.getCode());
        assertTrue(Files.exists(this.tempDir.resolve("out.xml.qftmp")),
                "the verified temp is preserved for forensics: " + result.getMessage());
        assertTrue(result.getMessage().contains("preserved"), result.getMessage());
    }

    @Test
    void grammarPathAndOfflineRefusalsStayFailClosed() {
        ScriptedTransport fake = new ScriptedTransport();
        String sha = ScriptedTransport.sha256Hex("payload\n".getBytes(StandardCharsets.UTF_8));
        OperationResult<Void> badSha = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", this.tempDir.resolve("x.xml"), "not-a-hash", true);
        assertFalse(badSha.isSuccess());
        assertEquals("TRANSFER_HASH_GRAMMAR", badSha.getCode());
        OperationResult<Void> traversal = newTransfer(fake).downloadVerifiedResult(
                "../escape/out.xml", this.tempDir.resolve("x.xml"), sha, true);
        assertFalse(traversal.isSuccess());
        assertEquals("SSH_PATH_INVALID", traversal.getCode());
        fake.connected = false;
        OperationResult<Void> offline = newTransfer(fake).downloadVerifiedResult(
                "jobs/a1/out.xml", this.tempDir.resolve("x.xml"), sha, true);
        assertFalse(offline.isSuccess());
        assertNotEquals("TRANSFER_VERIFIED", offline.getCode());
    }

    /** Scripted transport: sha256sum answered from the staged map, download recorded. */
    static final class ScriptedTransport implements SshTransport {
        final Map<String, byte[]> remoteFiles = new HashMap<>();
        final List<String> steps = new ArrayList<>();
        final SSHServer server = new SSHServer("test");
        byte[] downloadOverride;      // in-flight corruption simulator
        boolean shaProbeDies;         // transport-death simulator for the probe
        boolean garbageProbeOutput;   // unparseable sha256 output simulator
        boolean connected = true;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            try {
                if (!"sha256sum".equals(command[0])) {
                    return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
                }
                this.steps.add("sha256sum");
                if (this.shaProbeDies) {
                    return OperationResult.failed("SSH_TRANSPORT_LOST",
                            "transport died mid-probe", null);
                }
                byte[] data = this.remoteFiles.get(command[1]);
                if (data == null) {
                    return OperationResult.failed("SSH_EXEC_FAILED", "no such file", null);
                }
                if (this.garbageProbeOutput) {
                    Files.writeString(stdoutFile, "garbage!! not-hex\n");
                } else {
                    Files.writeString(stdoutFile, sha256Hex(data) + "  " + command[1] + "\n");
                }
                return OperationResult.success("SSH_EXEC_OK", "ok", 0);
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }
        @Override public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
            this.steps.add("upload");
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            this.steps.add("download");
            byte[] data = this.downloadOverride != null
                    ? this.downloadOverride : this.remoteFiles.get(remotePath);
            if (data == null) {
                return OperationResult.failed("SFTP_NO_SUCH", "no such file", null);
            }
            try {
                Files.write(localFile, data);
            } catch (java.io.IOException ex) {
                return OperationResult.failed("SFTP_LOCAL", ex.getMessage(), null);
            }
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { this.connected = false; }

        static String sha256Hex(byte[] data) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(data);
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
                }
                return hex.toString();
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
