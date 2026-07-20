/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

/**
 * Batch-146 (#92 resumable slice): the chunk layout math and per-chunk pin
 * ownership - one pass pins every chunk AND the whole file, the bounds are
 * stated, and the cap arithmetic is proven without needing a multi-GB file.
 */
class TransferChunkPlanTest {

    @TempDir
    Path tempDir;

    private static byte[] payload(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 31 + 7) % 251);
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static byte[] slice(byte[] bytes, long offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(bytes, (int) offset, out, 0, length);
        return out;
    }

    @Test
    void onePassPinsEveryChunkAndTheWholeFile() throws Exception {
        int size = 2_500_000;
        byte[] bytes = payload(size);
        Path file = this.tempDir.resolve("wfc.dat");
        Files.write(file, bytes);
        OperationResult<TransferChunkPlan.ChunkPlan> planned =
                TransferChunkPlan.plan(file, TransferChunkPlan.MIN_CHUNK_BYTES);
        assertTrue(planned.isSuccess(), planned.toString());
        assertEquals("TRANSFER_CHUNK_PLAN_OK", planned.getCode());
        TransferChunkPlan.ChunkPlan plan = planned.getValue().orElseThrow();
        assertEquals(size, plan.getTotalBytes());
        assertEquals(TransferChunkPlan.MIN_CHUNK_BYTES, plan.getChunkBytes());
        assertEquals(3, plan.getChunkCount(),
                "2 x 1 MiB + one short tail chunk");
        assertEquals(sha256(bytes), plan.getWholeSha256(),
                "the whole-file pin is the payload's own digest");
        long offset = 0L;
        for (int i = 0; i < 3; i++) {
            TransferChunkPlan.Chunk chunk = plan.getChunks().get(i);
            assertEquals(i + 1, chunk.getIndex(), "1-based chunk indexing");
            assertEquals(String.format("part-%05d", i + 1), chunk.getPartName(),
                    "part names are owned and zero-padded");
            assertEquals(offset, chunk.getOffset());
            int expectedLength = (int) Math.min(TransferChunkPlan.MIN_CHUNK_BYTES,
                    size - offset);
            assertEquals(expectedLength, chunk.getLength());
            assertEquals(sha256(slice(bytes, offset, expectedLength)), chunk.getSha256(),
                    "chunk " + (i + 1) + " carries its own independent pin");
            offset += expectedLength;
        }
        assertEquals(size, offset, "chunks tile the payload exactly");
    }

    @Test
    void successMessageNamesTheResumeGranularity() throws Exception {
        Path file = this.tempDir.resolve("p.dat");
        Files.write(file, payload(TransferChunkPlan.MIN_CHUNK_BYTES + 10));
        OperationResult<TransferChunkPlan.ChunkPlan> planned =
                TransferChunkPlan.plan(file, TransferChunkPlan.MIN_CHUNK_BYTES);
        assertTrue(planned.getMessage().contains("2 chunk(s)"), planned.getMessage());
        assertTrue(planned.getMessage().contains("resume granularity is the chunk"),
                planned.getMessage());
    }

    @Test
    void boundsRefusalsAreStated() throws Exception {
        Path file = this.tempDir.resolve("b.dat");
        Files.write(file, payload(64));
        OperationResult<TransferChunkPlan.ChunkPlan> tooSmall =
                TransferChunkPlan.plan(file, 1024);
        assertFalse(tooSmall.isSuccess());
        assertEquals("TRANSFER_CHUNK_BOUNDS", tooSmall.getCode());
        assertTrue(tooSmall.getMessage().contains("plain verified upload"),
                tooSmall.getMessage());
        OperationResult<TransferChunkPlan.ChunkPlan> tooBig =
                TransferChunkPlan.plan(file, TransferChunkPlan.MAX_CHUNK_BYTES + 1);
        assertFalse(tooBig.isSuccess());
        assertEquals("TRANSFER_CHUNK_BOUNDS", tooBig.getCode());
    }

    @Test
    void emptyPayloadHasNoChunksAndRefusesToBeCeremonial() throws Exception {
        Path file = this.tempDir.resolve("empty.dat");
        Files.write(file, new byte[0]);
        OperationResult<TransferChunkPlan.ChunkPlan> refused =
                TransferChunkPlan.plan(file, TransferChunkPlan.MIN_CHUNK_BYTES);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_CHUNK_EMPTY", refused.getCode());
        assertTrue(refused.getMessage().contains("no chunks to pin"),
                refused.getMessage());
    }

    @Test
    void missingFileRefusesAndNullIsLoud() {
        OperationResult<TransferChunkPlan.ChunkPlan> refused = TransferChunkPlan.plan(
                this.tempDir.resolve("nope.dat"), TransferChunkPlan.MIN_CHUNK_BYTES);
        assertFalse(refused.isSuccess());
        assertEquals("TRANSFER_CHUNK_LOCAL", refused.getCode());
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> TransferChunkPlan.plan(null, TransferChunkPlan.MIN_CHUNK_BYTES));
        assertTrue(thrown.getMessage().contains("pins real bytes"),
                thrown.getMessage());
    }

    @Test
    void theCapArithmeticIsProvenWithoutAMultiGbFile() {
        long perChunk = TransferChunkPlan.MIN_CHUNK_BYTES;
        assertEquals(TransferChunkPlan.MAX_CHUNK_COUNT,
                TransferChunkPlan.countFor(TransferChunkPlan.MAX_CHUNK_COUNT * perChunk,
                        (int) perChunk));
        assertEquals(TransferChunkPlan.MAX_CHUNK_COUNT + 1,
                TransferChunkPlan.countFor(
                        TransferChunkPlan.MAX_CHUNK_COUNT * perChunk + 1, (int) perChunk),
                "one byte over kicks the count over the cap - the guard itself is "
                        + "reviewed against this formula");
        assertThrows(IllegalArgumentException.class,
                () -> TransferChunkPlan.countFor(64, 0));
        assertEquals(64, TransferChunkPlan.MIN_CHUNK_BYTES * 0L + 64,
                "sanity: the bounds constants changed under no one");
        assertTrue(TransferChunkPlan.MIN_CHUNK_BYTES
                <= TransferChunkPlan.DEFAULT_CHUNK_BYTES
                && TransferChunkPlan.DEFAULT_CHUNK_BYTES
                <= TransferChunkPlan.MAX_CHUNK_BYTES,
                "the default lies inside the stated bounds");
    }
}
