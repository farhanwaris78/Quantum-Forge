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
 * Batch 151 (Roadmap #92 remaining depth: the DOWNLOAD half of the resume
 * protocol): {@link SSHFileTransfer#downloadChunkedVerifiedResult} and the
 * remote-pinned {@link TransferChunkPlan#fromRemoteTiling}, exercised against
 * a scripted wire that answers sha256sum/stat/dd probes honestly - or lies in
 * exactly one scripted place, to prove each refusal scope.
 */
class ChunkedDownloadTest {

    private static final String REMOTE = "espresso.tar.gz";
    private static final int CHUNK = 1 << 20; // 1 MiB floor

    @TempDir
    Path tempDir;

    private static byte[] payload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) ((i * 31 + i / 7) & 0xff);
        }
        return bytes;
    }

    private SSHFileTransfer transfer(ScriptedDownWire fake) {
        SSHFileTransfer transfers = new SSHFileTransfer(new SSHServer("chunk-dnl-test"));
        transfers.setTransport(fake);
        transfers.setStagingRoot("/scratch/jobs");
        return transfers;
    }

    // ---- happy path, resume, and the drift/lie refusals ----

    @Test
    void happyPathPinsSlicesVerifiesAssemblesAndRenames() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertTrue(done.isSuccess(), done.toString());
        assertEquals("TRANSFER_CHUNKED_VERIFIED", done.getCode());
        // pin pass: whole sha256 + stat + one dd|sha256sum per chunk; then the
        // drift re-probe at the end: 1 sha256sum(remote) BEFORE slices and 1 AFTER.
        long pinProbes = fake.steps.stream().filter(s -> s.equals("sha256sum")).count();
        assertEquals(2, pinProbes, "whole-file pin probe opens and closes (drift check)");
        assertEquals(1, fake.steps.stream().filter(s -> s.equals("stat")).count());
        long ddPins = fake.steps.stream().filter(s -> s.equals("sh-pin")).count();
        assertEquals(3, ddPins, "2.5 MiB at 1 MiB chunks -> 3 remote slice pins");
        long ddSlices = fake.steps.stream().filter(s -> s.equals("sh-slice")).count();
        assertEquals(3, ddSlices, "3 fresh slice downloads");
        assertTrue(done.getMessage().contains("0 resumed-skip"), done.getMessage());
        assertTrue(done.getMessage().contains("3 fresh"), done.getMessage());
        assertArrayEquals(payload(2_500_000), Files.readAllBytes(localFile),
                "the assembled payload landed byte-identical");
        assertFalse(Files.exists(this.tempDir.resolve("results/" + REMOTE + ".qftmp")),
                "no scratch temp remains after the rename");
        assertFalse(Files.isDirectory(this.tempDir.resolve("results/" + REMOTE + ".qftmp.parts")),
                "our own part scratch is cleaned after a successful rename");
    }

    @Test
    void aVerifiedPartIsReusedAndYesterdaysBytesAreRedownloaded() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        byte[] bytes = payload(2_500_000);
        fake.remote.put("/scratch/jobs/" + REMOTE, bytes);
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());
        Path partsDir = this.tempDir.resolve("results/" + REMOTE + ".qftmp.parts");
        Files.createDirectories(partsDir);
        // part-00002 already staged with today's bytes -> resume-skip
        byte[] part2 = new byte[CHUNK];
        System.arraycopy(bytes, CHUNK, part2, 0, CHUNK);
        Files.write(partsDir.resolve("part-00002"), part2);
        // part-00001 carries yesterday's garbage -> re-downloaded, never trusted
        Files.write(partsDir.resolve("part-00001"), payload(64));

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertTrue(done.isSuccess(), done.toString());
        assertTrue(done.getMessage().contains("1 resumed-skip"), done.getMessage());
        assertTrue(done.getMessage().contains("1 re-downloaded stale"), done.getMessage());
        assertTrue(done.getMessage().contains("1 fresh"), done.getMessage());
        long sliceDownloads = fake.steps.stream().filter(s -> s.equals("sh-slice")).count();
        assertEquals(2, sliceDownloads, "only the missing/stale chunks are sliced again");
        assertArrayEquals(bytes, Files.readAllBytes(localFile));
    }

    @Test
    void aSourceReadSliceMismatchRemovesOnlyTheOffendingPart() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        fake.corruptSliceForChunk = 2;
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(done.isSuccess());
        assertEquals("TRANSFER_PART_MISMATCH", done.getCode());
        assertTrue(done.getMessage().contains("chunk 2"), done.getMessage());
        Path partsDir = this.tempDir.resolve("results/" + REMOTE + ".qftmp.parts");
        assertFalse(Files.exists(partsDir.resolve("part-00002")),
                "ONLY the offending part was removed");
        assertTrue(Files.exists(partsDir.resolve("part-00001")),
                "the already-verified part stays for resume");
        assertFalse(Files.exists(this.tempDir.resolve("results/" + REMOTE + ".qftmp")),
                "nothing was assembled");
        assertFalse(Files.exists(localFile), "nothing was renamed into place");
    }

    @Test
    void aLyingWholePinIsCaughtAtAssemblyVerifyPartsStay() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        fake.lieWholePin = true; // pin pass reports a whole hash over other bytes
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(done.isSuccess());
        assertEquals("SFTP_VERIFY_MISMATCH", done.getCode());
        assertFalse(Files.exists(this.tempDir.resolve("results/" + REMOTE + ".qftmp")),
                "ONLY the assembled temp was removed");
        assertTrue(Files.isDirectory(this.tempDir.resolve("results/" + REMOTE + ".qftmp.parts")),
                "the verified parts remain for resume and forensics");
        assertFalse(Files.exists(localFile), "NOTHING was renamed into place");
    }

    @Test
    void aDriftingSourceIsCaughtAfterAssemblySuccessIsNeverFabricated() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        fake.lieDriftProbe = true; // the final re-probe sees a different source
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(done.isSuccess());
        assertEquals("TRANSFER_SOURCE_DRIFTED", done.getCode());
        assertTrue(done.getMessage().contains("mixed"), done.getMessage());
        assertFalse(Files.exists(this.tempDir.resolve("results/" + REMOTE + ".qftmp")),
                "the mixed-generation assembly is removed");
        assertTrue(Files.isDirectory(this.tempDir.resolve("results/" + REMOTE + ".qftmp.parts")),
                "parts stay for resume");
        assertFalse(Files.exists(localFile));
    }

    @Test
    void anAbsentSourceRefusesBeforeAnyByteMoves() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire(); // remote is EMPTY
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(done.isSuccess());
        assertEquals("TRANSFER_SOURCE_MISSING", done.getCode());
        assertEquals(1, fake.steps.size(), "one probe, then refusal: " + fake.steps);
    }

    @Test
    void unparseablePinsRefuseBlind() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        fake.garbageSha = true;
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());

        OperationResult<Void> done = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(done.isSuccess());
        assertEquals("TRANSFER_SOURCE_UNREADABLE", done.getCode());
        assertFalse(Files.exists(localFile));
    }

    @Test
    void localPostureIsCheckedBeforeAnyByteMoves() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        Files.createDirectories(localFile.getParent());
        Files.write(localFile, payload(16));
        OperationResult<Void> refused = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, false);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_LOCAL_EXISTS", refused.getCode());
        assertTrue(fake.steps.isEmpty(), "refused BEFORE a single probe");
        assertArrayEquals(payload(16), Files.readAllBytes(localFile),
                "the existing file is untouched");

        OperationResult<Void> allowed = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertTrue(allowed.isSuccess(), allowed.toString());
        assertArrayEquals(payload(2_500_000), Files.readAllBytes(localFile),
                "ALLOWED is the explicit analyst choice and does proceed");
    }

    @Test
    void aMissingDestinationDirectoryRefusesToCreateOne() {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        Path localFile = this.tempDir.resolve("no-such-dir/" + REMOTE);
        OperationResult<Void> result = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(result.isSuccess());
        assertEquals("TRANSFER_LOCAL_DIR", result.getCode());
        assertTrue(fake.steps.isEmpty(), "refused BEFORE a single probe");
    }

    @Test
    void transportGuardAndPathGuardFailClosed() throws Exception {
        ScriptedDownWire fake = new ScriptedDownWire();
        fake.connected = false;
        Path localFile = this.tempDir.resolve("results/" + REMOTE);
        OperationResult<Void> offline = transfer(fake).downloadChunkedVerifiedResult(
                REMOTE, localFile, CHUNK, true);
        assertFalse(offline.isSuccess());
        assertEquals("SSH_DOWNLOAD_UNAVAILABLE", offline.getCode());
        fake.connected = true;
        fake.remote.put("/scratch/jobs/" + REMOTE, payload(2_500_000));
        Files.createDirectories(localFile.getParent());
        OperationResult<Void> escape = transfer(fake).downloadChunkedVerifiedResult(
                "../outside.tar.gz", localFile, CHUNK, true);
        assertFalse(escape.isSuccess());
        assertEquals("SSH_PATH_INVALID", escape.getCode());
        assertTrue(fake.steps.isEmpty(), "path guard refused BEFORE a single probe");
    }

    // ---- the remote-pinned planning half, exercised directly ----

    @Test
    void remoteTilingPlansOffsetsBoundsAndGrammar() {
        OperationResult<TransferChunkPlan.ChunkPlan> planned =
                TransferChunkPlan.fromRemoteTiling(2_500_000, CHUNK,
                        ScriptedDownWire.sha(payload(2_500_000)),
                        List.of(ScriptedDownWire.sha(sliceOf(payload(2_500_000), 0)),
                                ScriptedDownWire.sha(sliceOf(payload(2_500_000), 1)),
                                ScriptedDownWire.sha(sliceOf(payload(2_500_000), 2))));
        assertTrue(planned.isSuccess(), planned.toString());
        TransferChunkPlan.ChunkPlan plan = planned.getValue().orElseThrow();
        assertEquals(3, plan.getChunkCount());
        assertEquals(0L, plan.getChunks().get(0).getOffset());
        assertEquals(CHUNK, plan.getChunks().get(1).getOffset());
        assertEquals(2L * CHUNK, plan.getChunks().get(2).getOffset());
        assertEquals(2_500_000L - 2L * CHUNK, plan.getChunks().get(2).getLength());
        assertEquals("part-00003", plan.getChunks().get(2).getPartName());

        // bounds are the upload side's own: the ceiling is shared arithmetic
        assertEquals("TRANSFER_CHUNK_BOUNDS",
                TransferChunkPlan.fromRemoteTiling(2_500_000, CHUNK - 1, "a".repeat(64),
                        List.of()).getCode());
        assertEquals("TRANSFER_CHUNK_EMPTY",
                TransferChunkPlan.fromRemoteTiling(0L, CHUNK, "a".repeat(64), List.of()).getCode());
        assertEquals("TRANSFER_CHUNK_PIN_COUNT",
                TransferChunkPlan.fromRemoteTiling(2_500_000, CHUNK, "a".repeat(64),
                        List.of("b".repeat(64))).getCode());
        assertEquals("TRANSFER_HASH_GRAMMAR",
                TransferChunkPlan.fromRemoteTiling(2_500_000, CHUNK, "not-hex",
                        List.of("b".repeat(64), "c".repeat(64), "d".repeat(64))).getCode());
        assertThrows(NullPointerException.class,
                () -> TransferChunkPlan.fromRemoteTiling(2_500_000, CHUNK, "a".repeat(64), null));
    }

    private static byte[] sliceOf(byte[] payloadBytes, int chunkIndex) {
        int offset = chunkIndex * CHUNK;
        int length = (int) Math.min(CHUNK, payloadBytes.length - offset);
        byte[] slice = new byte[length];
        System.arraycopy(payloadBytes, offset, slice, 0, length);
        return slice;
    }

    /**
     * Scripted remote: honest sha256sum/stat/dd answers over a byte map, with
     * exactly one scripted lie per test (whole pin, one slice, or the drift
     * re-probe). Steps record the probe kind so sequences are assertable.
     */
    static final class ScriptedDownWire implements SshTransport {
        final List<String> steps = new ArrayList<>();
        final Map<String, byte[]> remote = new HashMap<>();
        boolean connected = true;
        boolean lieWholePin = false;
        boolean garbageSha = false;
        int corruptSliceForChunk = -1;
        boolean lieDriftProbe = false;
        private int wholeShaProbes = 0;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            this.steps.add("sh".equals(command[0]) && command[2].contains("| sha256sum")
                    ? "sh-pin"
                    : "sh".equals(command[0]) && command[2].startsWith("dd if=")
                        ? "sh-slice" : command[0]);
            try {
                switch (command[0]) {
                case "sha256sum": {
                    byte[] data = this.remote.get(command[1]);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                    }
                    this.wholeShaProbes++;
                    if (this.garbageSha) {
                        Files.writeString(stdoutFile, "garbage-not-hex\n");
                    } else if (this.lieWholePin && this.wholeShaProbes == 1) {
                        Files.writeString(stdoutFile, sha(new byte[] {1, 2, 3})
                                + "  " + command[1] + "\n");
                    } else if (this.lieDriftProbe && this.wholeShaProbes > 1) {
                        Files.writeString(stdoutFile, sha(new byte[] {9, 9, 9})
                                + "  " + command[1] + "\n");
                    } else {
                        Files.writeString(stdoutFile, sha(data) + "  " + command[1] + "\n");
                    }
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                case "stat": {
                    // real wire: {"stat", "-c", "%s", <remote>} -> path is last argv
                    byte[] data = this.remote.get(command[command.length - 1]);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                    }
                    Files.writeString(stdoutFile, data.length + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                case "sh": {
                    return runDd(command[2], stdoutFile);
                }
                default:
                    return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }

        /** dd if='<remote>' bs=<n> skip=<i> count=1 2>/dev/null [| sha256sum] */
        private OperationResult<Integer> runDd(String script, Path stdoutFile)
                throws Exception {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "dd if='([^']+)' bs=(\\d+) skip=(\\d+) count=1").matcher(script);
            if (!matcher.find()) {
                return OperationResult.failed("SSH_EXEC_FAILED", "unsupported script", null);
            }
            byte[] data = this.remote.get(matcher.group(1));
            if (data == null) {
                return OperationResult.failed("SSH_EXEC_FAILED", "dd: no such file", null);
            }
            long bs = Long.parseLong(matcher.group(2));
            long skip = Long.parseLong(matcher.group(3));
            int offset = (int) (bs * skip);
            int length = (int) Math.min(bs, data.length - offset);
            byte[] slice = new byte[Math.max(0, length)];
            System.arraycopy(data, offset, slice, 0, slice.length);
            if (script.contains("| sha256sum")) {
                // the pin probe always answers the HONEST remote bytes - the
                // scripted corruption models the remote drifting BETWEEN pin
                // and fetch, so only the actual slice (below) carries the lie.
                Files.writeString(stdoutFile, sha(slice) + "  -\n");
            } else {
                if (this.corruptSliceForChunk == (int) (skip + 1) && slice.length > 0) {
                    slice[0] = (byte) (slice[0] + 1);
                }
                Files.write(stdoutFile, slice);
            }
            return OperationResult.success("SSH_EXEC_OK", "ok", 0);
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
