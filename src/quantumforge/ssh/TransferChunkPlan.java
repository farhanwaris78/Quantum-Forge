/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #92 (resumable-transfer slice, batch 146): the CHUNK LAYOUT and
 * the per-chunk sha256 pins for one local payload, computed BEFORE any byte
 * moves - the plan half of the resume protocol, so that an interrupted
 * chunked upload can resume at chunk granularity and so no interrupted
 * transfer can ever masquerade as complete (the roadmap's stated #92
 * barrier).
 *
 * <p>Honesty rules owned here:</p>
 * <ul>
 *   <li>ONE streaming pass pins EVERY chunk AND the whole-file digest, and
 *       the bytes read MUST total exactly {@code Files.size} measured at the
 *       start - a file that changed mid-plan refuses as
 *       TRANSFER_CHUNK_LOCAL (planning against a moving target would pin
 *       the wrong payload);</li>
 *   <li>a zero-byte payload refuses (TRANSFER_CHUNK_EMPTY): it has no chunks
 *       and the plain verified upload is its honest path - a 0-part plan
 *       would be ceremonial;</li>
 *   <li>chunk size is bounded [{@value #MIN_CHUNK_BYTES},
 *       {@value #MAX_CHUNK_BYTES}] (TRANSFER_CHUNK_BOUNDS): below the floor
 *       chunking gains nothing over the plain verified upload's own temp
 *       pattern, above the ceiling a "chunk" stops being a resume unit;</li>
 *   <li>{@value #MAX_CHUNK_COUNT} parts is the hard cap - the plan is a
 *       review payload, and a review with unbounded rows is a dump, not a
 *       plan;</li>
 *   <li>part names are OWNED here ({@code part-NNNNN}, 1-based, zero-padded)
 *       so the wire and any reviewer name the same scratch files.</li>
 * </ul>
 */
public final class TransferChunkPlan {

    /** Smallest honest resume unit (1 MiB). */
    public static final int MIN_CHUNK_BYTES = 1 << 20;
    /** Largest honest resume unit (64 MiB). */
    public static final int MAX_CHUNK_BYTES = 1 << 26;
    /** Default resume unit (8 MiB). */
    public static final int DEFAULT_CHUNK_BYTES = 1 << 23;
    /** Hard cap on part count: the plan stays a review payload. */
    public static final long MAX_CHUNK_COUNT = 4096L;

    private TransferChunkPlan() {
        // Utility
    }

    /** One pinned chunk: index (1-based), offset, length, sha256, part name. */
    public static final class Chunk {
        private final int index;
        private final long offset;
        private final long length;
        private final String sha256;
        private final String partName;

        Chunk(int index, long offset, long length, String sha256, String partName) {
            this.index = index;
            this.offset = offset;
            this.length = length;
            this.sha256 = sha256;
            this.partName = partName;
        }

        public int getIndex() { return this.index; }
        public long getOffset() { return this.offset; }
        public long getLength() { return this.length; }
        public String getSha256() { return this.sha256; }
        public String getPartName() { return this.partName; }
    }

    /** The whole plan: total bytes, chunk size, whole-file pin, all chunks. */
    public static final class ChunkPlan {
        private final long totalBytes;
        private final int chunkBytes;
        private final String wholeSha256;
        private final List<Chunk> chunks;

        ChunkPlan(long totalBytes, int chunkBytes, String wholeSha256, List<Chunk> chunks) {
            this.totalBytes = totalBytes;
            this.chunkBytes = chunkBytes;
            this.wholeSha256 = wholeSha256;
            this.chunks = List.copyOf(chunks);
        }

        public long getTotalBytes() { return this.totalBytes; }
        public int getChunkBytes() { return this.chunkBytes; }
        public String getWholeSha256() { return this.wholeSha256; }
        public List<Chunk> getChunks() { return this.chunks; }
        public int getChunkCount() { return this.chunks.size(); }
    }

    /** Chunk count for a size/layout pair (ceiling division, no file I/O). */
    public static long countFor(long totalBytes, long chunkBytes) {
        if (chunkBytes < 1) {
            throw new IllegalArgumentException("chunkBytes must be positive (got "
                    + chunkBytes + ")");
        }
        return (totalBytes + chunkBytes - 1L) / chunkBytes;
    }

    /**
     * Batch 151 (the DOWNLOAD half of #92's resume protocol): assemble a plan
     * from pins the REMOTE side reported (whole-file sha256, remote size, and
     * one sha256 per chunk in tiling order). The tiling arithmetic - offsets,
     * lengths, {@code part-NNNNN} names, size/count bounds - is the SAME the
     * upload side proves locally, so a resume unit names identical bytes on
     * both sides of the wire.
     *
     * <p>Codes: TRANSFER_CHUNK_BOUNDS (layout outside the owned bounds, or an
     * empty source, or a pin list longer than the part cap), TRANSFER_HASH_GRAMMAR
     * (a pin that is not 64 lowercase-able hex), TRANSFER_CHUNK_PIN_COUNT (the
     * pin list does not match the tiling count - the two sides would disagree
     * about what a "part" is).</p>
     */
    public static OperationResult<ChunkPlan> fromRemoteTiling(long totalBytes, int chunkBytes,
            String wholeSha256Hex, List<String> chunkSha256Hex) {
        if (chunkSha256Hex == null) {
            throw new NullPointerException("remote chunk pins are required - a chunked "
                    + "download without pins would be an unreviewable transfer");
        }
        if (chunkBytes < MIN_CHUNK_BYTES || chunkBytes > MAX_CHUNK_BYTES) {
            return OperationResult.failed("TRANSFER_CHUNK_BOUNDS",
                    "chunk size " + chunkBytes + " is outside [" + MIN_CHUNK_BYTES + ", "
                            + MAX_CHUNK_BYTES + "] - below " + MIN_CHUNK_BYTES
                            + " the plain verified download's temp pattern is the honest "
                            + "path, above " + MAX_CHUNK_BYTES + " a 'chunk' is no "
                            + "longer a resume unit.",
                    null);
        }
        if (totalBytes <= 0L) {
            return OperationResult.failed("TRANSFER_CHUNK_EMPTY",
                    "zero-byte remote source: there are no chunks to resume - the plain "
                            + "verified download is the honest path for an empty file.",
                    null);
        }
        long count = countFor(totalBytes, chunkBytes);
        if (count > MAX_CHUNK_COUNT) {
            return OperationResult.failed("TRANSFER_CHUNK_BOUNDS",
                    "the remote source needs " + count + " parts at " + chunkBytes
                            + " bytes/chunk, over the " + MAX_CHUNK_COUNT
                            + " cap - raise the chunk size; the cap keeps the plan a "
                            + "review payload, not a dump.",
                    null);
        }
        if (chunkSha256Hex.size() != count) {
            return OperationResult.failed("TRANSFER_CHUNK_PIN_COUNT",
                    "the remote reported " + chunkSha256Hex.size() + " chunk pins but a "
                            + totalBytes + "-byte source at " + chunkBytes + " bytes/chunk "
                            + "tiling needs " + count
                            + " - the two sides would disagree about what a 'part' is.",
                    null);
        }
        String whole = wholeSha256Hex == null ? ""
                : wholeSha256Hex.trim().toLowerCase(Locale.ROOT);
        if (!whole.matches("[0-9a-f]{64}")) {
            return OperationResult.failed("TRANSFER_HASH_GRAMMAR",
                    "the remote whole-file pin must be sha256:<64 lowercase hex> (got '"
                            + (whole.isEmpty() ? "<empty>" : wholeSha256Hex) + "').",
                    null);
        }
        List<Chunk> chunks = new ArrayList<>();
        long offset = 0L;
        for (int i = 0; i < count; i++) {
            String pin = chunkSha256Hex.get(i) == null ? ""
                    : chunkSha256Hex.get(i).trim().toLowerCase(Locale.ROOT);
            if (!pin.matches("[0-9a-f]{64}")) {
                return OperationResult.failed("TRANSFER_HASH_GRAMMAR",
                        "remote pin for chunk " + (i + 1) + " is not sha256:<64 lowercase "
                                + "hex> (got '" + (pin.isEmpty() ? "<empty>"
                                        : chunkSha256Hex.get(i)) + "') - resume refuses "
                                + "to pin a part it cannot verify.",
                        null);
            }
            long length = Math.min(chunkBytes, totalBytes - offset);
            chunks.add(new Chunk(i + 1, offset, length, pin,
                    String.format(Locale.ROOT, "part-%05d", i + 1)));
            offset += length;
        }
        ChunkPlan plan = new ChunkPlan(totalBytes, chunkBytes, whole, chunks);
        return OperationResult.success("TRANSFER_CHUNK_PLAN_OK",
                "Remote-pinned plan: " + chunks.size() + " chunk(s) of up to " + chunkBytes
                        + " bytes (" + totalBytes + " total) - resume granularity is the"
                        + " chunk, pins come from the source side of the wire.",
                plan);
    }

    /**
     * Plan the chunked staging of {@code localFile}. Codes:
     * TRANSFER_CHUNK_PLAN_OK on success; TRANSFER_CHUNK_LOCAL for a
     * missing/unreadable/not-regular/changed file; TRANSFER_CHUNK_EMPTY for a
     * zero-byte payload; TRANSFER_CHUNK_BOUNDS for out-of-range layout
     * requests.
     */
    public static OperationResult<ChunkPlan> plan(Path localFile, int chunkBytes) {
        if (localFile == null) {
            throw new NullPointerException("localFile is required - the plan pins real "
                    + "bytes, never a hypothetical file");
        }
        if (chunkBytes < MIN_CHUNK_BYTES || chunkBytes > MAX_CHUNK_BYTES) {
            return OperationResult.failed("TRANSFER_CHUNK_BOUNDS",
                    "chunk size " + chunkBytes + " is outside [" + MIN_CHUNK_BYTES + ", "
                            + MAX_CHUNK_BYTES + "] - below " + MIN_CHUNK_BYTES
                            + " the plain verified upload's temp pattern is the honest "
                            + "path, above " + MAX_CHUNK_BYTES + " a 'chunk' is no "
                            + "longer a resume unit.",
                    null);
        }
        if (!Files.isRegularFile(localFile)) {
            return OperationResult.failed("TRANSFER_CHUNK_LOCAL",
                    "local payload is missing or not a regular file: " + localFile, null);
        }
        final long totalBytes;
        try {
            totalBytes = Files.size(localFile);
        } catch (IOException sizeFail) {
            return OperationResult.failed("TRANSFER_CHUNK_LOCAL",
                    "could not measure the local payload: " + sizeFail.getMessage(),
                    sizeFail);
        }
        if (totalBytes == 0L) {
            return OperationResult.failed("TRANSFER_CHUNK_EMPTY",
                    "zero-byte payload: there are no chunks to pin - the plain verified "
                            + "upload is the honest path for an empty file.",
                    null);
        }
        long count = countFor(totalBytes, chunkBytes);
        if (count > MAX_CHUNK_COUNT) {
            return OperationResult.failed("TRANSFER_CHUNK_BOUNDS",
                    "the payload needs " + count + " parts at " + chunkBytes
                            + " bytes/chunk, over the " + MAX_CHUNK_COUNT
                            + " cap - raise the chunk size; the cap keeps the plan a "
                            + "review payload, not a dump.",
                    null);
        }
        try {
            MessageDigest whole = newSha256();
            List<Chunk> chunks = new ArrayList<>();
            byte[] buffer = new byte[chunkBytes];
            long offset = 0L;
            int index = 1;
            try (InputStream in = Files.newInputStream(localFile)) {
                while (true) {
                    int filled = 0;
                    while (filled < buffer.length) {
                        int read = in.read(buffer, filled, buffer.length - filled);
                        if (read < 0) {
                            break;
                        }
                        filled += read;
                    }
                    if (filled == 0) {
                        break;
                    }
                    MessageDigest part = newSha256();
                    part.update(buffer, 0, filled);
                    whole.update(buffer, 0, filled);
                    chunks.add(new Chunk(index, offset, filled,
                            hexOf(part), String.format(Locale.ROOT, "part-%05d", index)));
                    offset += filled;
                    index++;
                }
            }
            if (offset != totalBytes) {
                return OperationResult.failed("TRANSFER_CHUNK_LOCAL",
                        "the payload changed while planning (expected " + totalBytes
                                + " bytes, read " + offset
                                + ") - it must be still while it is pinned; plan again.",
                        null);
            }
            ChunkPlan plan = new ChunkPlan(totalBytes, chunkBytes, hexOf(whole), chunks);
            return OperationResult.success("TRANSFER_CHUNK_PLAN_OK",
                    "Planned " + chunks.size() + " chunk(s) of up to " + chunkBytes
                            + " bytes (" + totalBytes + " total) with per-chunk and "
                            + "whole-file sha256 pins - resume granularity is the chunk.",
                    plan);
        } catch (IOException readFail) {
            return OperationResult.failed("TRANSFER_CHUNK_LOCAL",
                    "could not read the local payload: " + readFail.getMessage(),
                    readFail);
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }

    private static String hexOf(MessageDigest digest) {
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
