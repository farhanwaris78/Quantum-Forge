/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Batch-146 (#92 resumable slice): the chunked upload protocol against a
 * scripted wire - resume at chunk granularity, re-staging of stale scratch,
 * assembly + whole-file verification before any rename, and every refusal
 * ladder step pinned in order.
 */
class ChunkedUploadTest {

    @TempDir
    Path tempDir;

    private static final String FINAL = "/scratch/jobs/big/wfc.dat";
    private static final String PARTS = FINAL + ".qftmp.parts";

    private static byte[] payload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 31 + 7) % 251);
        }
        return bytes;
    }

    private SSHFileTransfer transfer(ScriptedWire fake) {
        SSHFileTransfer transfers = new SSHFileTransfer(new SSHServer("chunk-test"));
        transfers.setTransport(fake);
        transfers.setStagingRoot("/scratch");
        return transfers;
    }

    private TransferChunkPlan.ChunkPlan planFor(Path file) {
        return TransferChunkPlan.plan(file, TransferChunkPlan.MIN_CHUNK_BYTES)
                .getValue().orElseThrow();
    }

    @Test
    void happyPathStagesVerifiesAssemblesAndRenames() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        byte[] bytes = payload(2_500_000);
        Files.write(file, bytes);
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        OperationResult<Void> done = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertTrue(done.isSuccess(), done.toString());
        assertEquals("TRANSFER_CHUNKED_VERIFIED", done.getCode());
        assertTrue(done.getMessage().contains("3 part(s) (0 resumed-skip, "
                + "0 re-staged stale, 3 fresh)"), done.getMessage());
        assertEquals(List.of("test", "mkdir",
                "sha256sum", "upload", "sha256sum",
                "sha256sum", "upload", "sha256sum",
                "sha256sum", "upload", "sha256sum",
                "sh", "sha256sum", "mv", "sh"), fake.steps,
                "per part: probe -> upload -> verify; then assemble, whole-verify, "
                        + "rename, cleanup");
        assertArrayEquals(bytes, fake.remote.get(FINAL),
                "the assembled payload landed byte-identical");
        assertFalse(fake.remote.containsKey(FINAL + ".qftmp"),
                "no scratch temp remains after the rename");
        assertTrue(fake.cleanedParts.contains(PARTS + "/part-00001")
                        && fake.cleanedParts.contains(PARTS + "/part-00003"),
                "success removes only our own named scratch parts");
        assertTrue(done.getMessage().contains("renamed into place"),
                done.getMessage());
    }

    @Test
    void aVerifiedPartIsReusedAndAYesterdaysBytesAreRestaged() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        byte[] bytes = payload(2_500_000);
        Files.write(file, bytes);
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        // part-00002 already staged correctly (a previous interrupted run);
        // part-00001 carries garbage from some other past - never trusted.
        byte[] part2 = new byte[(int) plan.getChunks().get(1).getLength()];
        System.arraycopy(bytes, (int) plan.getChunks().get(1).getOffset(), part2, 0,
                part2.length);
        fake.remote.put(PARTS + "/part-00002", part2);
        fake.remote.put(PARTS + "/part-00001", payload(64));
        OperationResult<Void> done = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertTrue(done.isSuccess(), done.toString());
        assertTrue(done.getMessage().contains("(1 resumed-skip, 1 re-staged stale, "
                + "1 fresh)"), done.getMessage());
        assertEquals(2, fake.uploads.size(),
                "only the absent part and the stale part move bytes: " + fake.uploads);
        assertFalse(fake.uploads.contains(PARTS + "/part-00002"),
                "a pin-matching part never re-uploads - THAT is the resume");
        assertArrayEquals(bytes, fake.remote.get(FINAL));
    }

    @Test
    void aStalePlanRefusesBeforeAnyRemoteStep() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        Files.write(file, payload(2_600_000)); // payload moved after pinning
        ScriptedWire fake = new ScriptedWire();
        OperationResult<Void> refused = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_CHUNK_STALE", refused.getCode());
        assertTrue(refused.getMessage().contains("plan again"), refused.getMessage());
        assertTrue(fake.steps.isEmpty(),
                "the drift check precedes even the overwrite pre-check");
    }

    @Test
    void anExistingFinalRefusesBeforePartsMove() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        fake.remote.put(FINAL, payload(8));
        OperationResult<Void> refused = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_EXISTS", refused.getCode());
        assertEquals(List.of("test"), fake.steps,
                "only the overwrite pre-check ran; no part was probed or moved");
    }

    @Test
    void aPartVerifyMismatchRemovesOnlyThatPartAndStops() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        fake.corruptUploadFor = PARTS + "/part-00002";
        OperationResult<Void> refused = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertFalse(refused.isSuccess());
        assertEquals("SFTP_PART_MISMATCH", refused.getCode());
        assertTrue(refused.getMessage().contains("chunk 2 (part-00002)"),
                refused.getMessage());
        assertTrue(refused.getMessage().contains("nothing was"
                + " assembled or renamed into place"), refused.getMessage());
        assertFalse(fake.remote.containsKey(PARTS + "/part-00002"),
                "ONLY the offending part was removed");
        assertFalse(fake.remote.containsKey(FINAL), "no final was ever assembled");
        assertTrue(fake.removedParts.contains(PARTS + "/part-00002")
                && fake.cleanedParts.isEmpty(),
                "scoped removal, never the cleanup sweep");
    }

    @Test
    void anUnreadableResumeProbeRefusesBlind() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        fake.remote.put(PARTS + "/part-00001", payload(TransferChunkPlan.MIN_CHUNK_BYTES));
        fake.garbageShaFor = PARTS + "/part-00001";
        OperationResult<Void> refused = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_PART_UNREADABLE", refused.getCode());
        assertTrue(refused.getMessage().contains("resume refuses to proceed"
                + " blind"), refused.getMessage());
        assertTrue(fake.remote.containsKey(PARTS + "/part-00001"),
                "an unreadable probe removes nothing");
    }

    @Test
    void aWholeFileMismatchKeepsPartsForResumeAndRenamesNothing() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        fake.corruptAssembly = true;
        OperationResult<Void> refused = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertFalse(refused.isSuccess());
        assertEquals("SFTP_VERIFY_MISMATCH", refused.getCode());
        assertTrue(refused.getMessage().contains("NOTHING was renamed into place"),
                refused.getMessage());
        assertFalse(refused.getMessage().contains("nothing was assembled"),
                "the whole-file stage is the ASSEMBLY's verdict");
        assertTrue(fake.remote.containsKey(PARTS + "/part-00001")
                        && fake.remote.containsKey(PARTS + "/part-00003"),
                "verified parts stay for resume");
        assertFalse(fake.remote.containsKey(FINAL + ".qftmp"),
                "ONLY the assembled temp was removed");
        assertFalse(fake.remote.containsKey(FINAL));
    }

    @Test
    void aFailedRenamePreservesEverythingAndSaysSo() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        fake.failMv = true;
        OperationResult<Void> refused = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertFalse(refused.isSuccess());
        assertEquals("SFTP_RENAME", refused.getCode());
        assertTrue(refused.getMessage().contains("preserved at " + FINAL + ".qftmp"),
                refused.getMessage());
        assertTrue(fake.remote.containsKey(FINAL + ".qftmp"),
                "the verified assembly survives for forensics");
        assertTrue(fake.cleanedParts.isEmpty(), "no cleanup without a rename");
    }

    @Test
    void aCleanupFailureDegradesToANoteNeverAVerdict() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(2_500_000));
        TransferChunkPlan.ChunkPlan plan = planFor(file);
        ScriptedWire fake = new ScriptedWire();
        fake.failCleanup = true;
        OperationResult<Void> done = transfer(fake).uploadChunkedVerifiedResult(
                file, "jobs/big/wfc.dat", plan, false);
        assertTrue(done.isSuccess(), done.toString());
        assertTrue(done.getMessage().contains("cleanup of the part scratch failed"),
                "the debris is admitted in the message: " + done.getMessage());
        assertTrue(done.getMessage().contains("debris, never hidden state"),
                done.getMessage());
        assertArrayEquals(bytes(2_500_000), fake.remote.get(FINAL),
                "the transfer itself stands verified");
        assertTrue(fake.cleanedParts.isEmpty());
    }

    @Test
    void guardsAndNullsRefuseLoudly() throws Exception {
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, payload(64));
        TransferChunkPlan.ChunkPlan plan = planFor(file); // 64 bytes -> 1 chunk
        ScriptedWire fake = new ScriptedWire();
        assertThrows(NullPointerException.class,
                () -> transfer(fake).uploadChunkedVerifiedResult(file,
                        "jobs/big/wfc.dat", null, false),
                "chunked staging without pins is unreviewable");
        OperationResult<Void> missing = transfer(fake).uploadChunkedVerifiedResult(
                this.tempDir.resolve("nope.dat"), "jobs/big/wfc.dat", plan, false);
        assertFalse(missing.isSuccess());
        assertEquals("SSH_LOCAL_MISSING", missing.getCode());
    }

    private static byte[] bytes(int size) {
        return payload(size);
    }

    /**
     * Scripted wire: stores uploads, answers sha256sum ONLY from stored bytes,
     * assembles via a tiny parser for the owned {@code sh -c "cat A B > C"}
     * emission and the owned cleanup emission.
     */
    static final class ScriptedWire implements SshTransport {
        final List<String> steps = new ArrayList<>();
        final List<String> uploads = new ArrayList<>();
        final List<String> removedParts = new ArrayList<>();
        final List<String> cleanedParts = new ArrayList<>();
        final Map<String, byte[]> remote = new HashMap<>();
        boolean connected = true;
        String corruptUploadFor = null;
        String garbageShaFor = null;
        boolean corruptAssembly = false;
        boolean failMv = false;
        boolean failCleanup = false;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            this.steps.add(command[0]);
            try {
                switch (command[0]) {
                case "test":
                    return this.remote.containsKey(command[2])
                            ? OperationResult.success("SSH_EXEC_OK", "ok", 0)
                            : OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                case "sha256sum": {
                    if (command[1].equals(this.garbageShaFor)) {
                        Files.writeString(stdoutFile, "garbage-not-hex\n");
                        return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                    }
                    byte[] data = this.remote.get(command[1]);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                    }
                    Files.writeString(stdoutFile, sha(data) + "  " + command[1] + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                case "mv": {
                    if (this.failMv) {
                        return OperationResult.failed("SSH_EXEC_FAILED", "mv denied", null);
                    }
                    this.remote.put(command[3], this.remote.remove(command[2]));
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                case "rm": {
                    this.remote.remove(command[2]);
                    this.removedParts.add(command[2]);
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                case "sh": {
                    return runShell(command[2]);
                }
                default:
                    return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }

        private OperationResult<Integer> runShell(String script) {
            if (this.failCleanup && script.startsWith("rm -f ")) {
                return OperationResult.failed("SSH_EXEC_FAILED", "cleanup denied", null);
            }
            if (script.startsWith("rm -f ") && script.contains(" && rmdir ")) {
                for (String name : quotedNames(script.substring(0, script.indexOf(" &&")))) {
                    this.remote.remove(name);
                    this.cleanedParts.add(name);
                }
                return OperationResult.success("SSH_EXEC_OK", "ok", 0);
            }
            if (script.startsWith("cat ") && script.contains(" > ")) {
                List<String> names = quotedNames(script.substring(4));
                String target = names.remove(names.size() - 1);
                java.io.ByteArrayOutputStream joined = new java.io.ByteArrayOutputStream();
                for (String name : names) {
                    byte[] data = this.remote.get(name);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED",
                                "cat: missing part " + name, null);
                    }
                    joined.write(data, 0, data.length);
                }
                byte[] assembled = joined.toByteArray();
                if (this.corruptAssembly) {
                    assembled[0] = (byte) (assembled[0] + 1);
                }
                this.remote.put(target, assembled);
                return OperationResult.success("SSH_EXEC_OK", "ok", 0);
            }
            return OperationResult.failed("SSH_EXEC_FAILED", "unsupported script", null);
        }

        private static List<String> quotedNames(String script) {
            List<String> names = new ArrayList<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("'([^']*)'").matcher(script);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        }

        @Override public OperationResult<Void> uploadFile(Path localFile,
                String remotePath) {
            this.steps.add("upload");
            this.uploads.add(remotePath);
            try {
                byte[] data = Files.readAllBytes(localFile);
                if (remotePath.equals(this.corruptUploadFor)) {
                    data = data.clone();
                    data[0] = (byte) (data[0] + 1);
                }
                this.remote.put(remotePath, data);
            } catch (Exception ex) {
                return OperationResult.failed("SFTP_UPLOAD", ex.getMessage(), null);
            }
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath,
                Path localFile) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            this.steps.add("mkdir");
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { this.connected = false; }

        static String sha(byte[] data) {
            try {
                java.security.MessageDigest md =
                        java.security.MessageDigest.getInstance("SHA-256");
                return java.util.HexFormat.of().formatHex(md.digest(data));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
