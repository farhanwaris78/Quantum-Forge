/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Batch-133 (#92 verified-upload slice): the temp-upload -&gt; sha256 verify
 * -&gt; atomic rename semantics, tested end to end against a scripted fake.
 */
class VerifiedUploadTest {

    @TempDir
    Path tempDir;

    private SSHFileTransfer newTransfer(ScriptedTransport fake) {
        SSHFileTransfer transfer = new SSHFileTransfer(fake.server);
        transfer.setTransport(fake);
        transfer.setStagingRoot("/scratch");
        return transfer;
    }

    @Test
    void happyPathIsTempThenVerifyThenAtomicRename() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        fake.remote.put("/scratch/jobs/a1/espresso.log.qftmp", null); // upload target
        Path local = tempDir.resolve("espresso.log");
        Files.writeString(local, "payload\n");
        String sha = ScriptedTransport.sha256Hex("payload\n");
        fake.remoteSha = sha;
        OperationResult<Void> result = newTransfer(fake).uploadVerifiedResult(
                local, "jobs/a1/espresso.log", sha, true);
        assertTrue(result.isSuccess(), result.toString());
        assertEquals("TRANSFER_VERIFIED", result.getCode());
        assertEquals(List.of("upload", "sha256sum", "mv"), fake.steps,
                "exactly the mandated sequence in the mandated order");
        assertEquals("/scratch/jobs/a1/espresso.log.qftmp", fake.uploadedPath,
                "bytes travel under our own scratch name first");
        assertTrue(fake.moves.contains(
                "/scratch/jobs/a1/espresso.log.qftmp->/scratch/jobs/a1/espresso.log"),
                fake.moves.toString());
        assertTrue(fake.removals.isEmpty(), "nothing is removed on the happy path");
    }

    @Test
    void hashMismatchRemovesOnlyOwnTempAndNeverRenames() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        fake.remoteSha = "f".repeat(64); // not the real payload hash
        Path local = tempDir.resolve("pw.in");
        Files.writeString(local, "&CONTROL\n/\n");
        String sha = ScriptedTransport.sha256Hex("&CONTROL\n/\n");
        OperationResult<Void> result = newTransfer(fake).uploadVerifiedResult(
                local, "jobs/a2/pw.in", sha, true);
        assertFalse(result.isSuccess());
        assertEquals("SFTP_VERIFY_MISMATCH", result.getCode());
        assertEquals(List.of("/scratch/jobs/a2/pw.in.qftmp"), fake.removals,
                "cleanup scope is exactly our own temp - nothing else, ever");
        assertTrue(fake.moves.isEmpty(), "a corrupt upload is never renamed into place");
        assertTrue(result.getMessage().contains("NOTHING was renamed into place"),
                result.getMessage());
    }

    @Test
    void overwritePostureIsPreCheckedBeforeAnyByteMoves() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        fake.remoteExists = true;
        Path local = tempDir.resolve("x.in");
        Files.writeString(local, "x");
        String sha = ScriptedTransport.sha256Hex("x");
        OperationResult<Void> refused = newTransfer(fake).uploadVerifiedResult(
                local, "jobs/a3/x.in", sha, false);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_EXISTS", refused.getCode());
        assertTrue(fake.steps.isEmpty(),
                "REFUSE-IF-EXISTS aborts before a single byte moves");

        fake.remoteSha = sha; // the remote checksum now matches the pinned target
        OperationResult<Void> allowed = newTransfer(fake).uploadVerifiedResult(
                local, "jobs/a3/x.in", sha, true);
        assertTrue(allowed.isSuccess(), allowed.toString(),
                "ALLOWED is the explicit analyst choice and does proceed");
    }

    @Test
    void grammarAndConnectionRefusalsMoveNothing() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        Path local = tempDir.resolve("y.in");
        Files.writeString(local, "y");
        OperationResult<Void> badSha = newTransfer(fake).uploadVerifiedResult(
                local, "jobs/a4/y.in", "not-a-hash", true);
        assertFalse(badSha.isSuccess());
        assertEquals("TRANSFER_HASH_GRAMMAR", badSha.getCode());
        OperationResult<Void> traversal = newTransfer(fake).uploadVerifiedResult(
                local, "../escape/y.in", ScriptedTransport.sha256Hex("y"), true);
        assertFalse(traversal.isSuccess());
        assertEquals("SSH_PATH_INVALID", traversal.getCode());
        fake.connected = false;
        OperationResult<Void> offline = newTransfer(fake).uploadVerifiedResult(
                local, "jobs/a4/y.in", ScriptedTransport.sha256Hex("y"), true);
        assertFalse(offline.isSuccess());
    }

    /** Scripted transport: sha256sum/test/mv/rm answered by shape, upload recorded. */
    static final class ScriptedTransport implements SshTransport {
        final Map<String, String> remote = new HashMap<>();
        final List<String> steps = new ArrayList<>();
        final List<String> moves = new ArrayList<>();
        final List<String> removals = new ArrayList<>();
        final SSHServer server = new SSHServer("test");
        String uploadedPath;
        String remoteSha = "";
        boolean remoteExists;
        boolean connected = true;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            try {
                switch (command[0]) {
                case "test":
                    if (this.remoteExists) {
                        return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                    }
                    return OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                case "sha256sum":
                    this.steps.add("sha256sum");
                    Files.writeString(stdoutFile, this.remoteSha + "  " + command[1] + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case "mv":
                    this.steps.add("mv");
                    this.moves.add(command[2] + "->" + command[3]);
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case "rm":
                    this.removals.add(command[2]);
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                default:
                    return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }
        @Override public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
            this.steps.add("upload");
            this.uploadedPath = remotePath;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { this.connected = false; }

        static String sha256Hex(String text) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
