/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Streaming index builder for multi-frame XYZ trajectories (Roadmap #127 data
 * layer). The reader makes ONE linear pass in O(1) heap: frame atom counts and
 * byte offsets are recorded, coordinate lines are only skipped (never parsed),
 * so a multi-gigabyte trajectory does not exhaust the GUI heap. A truncated
 * final frame is reported, not silently dropped.
 */
public final class TrajectoryIndexReader {

    /** Cap on stored offsets; further frames are still counted. */
    public static final int MAX_STORED_OFFSETS = 100_000;

    /** The completed index. */
    public static final class TrajectoryIndex {
        private final int frameCount;
        private final int atomsPerFrame;
        private final long fileBytes;
        private final List<Long> offsets;
        private final boolean offsetsComplete;
        private final boolean truncatedTail;

        private TrajectoryIndex(int frameCount, int atomsPerFrame, long fileBytes,
                List<Long> offsets, boolean offsetsComplete, boolean truncatedTail) {
            this.frameCount = frameCount;
            this.atomsPerFrame = atomsPerFrame;
            this.fileBytes = fileBytes;
            this.offsets = List.copyOf(offsets);
            this.offsetsComplete = offsetsComplete;
            this.truncatedTail = truncatedTail;
        }

        public int getFrameCount() { return this.frameCount; }
        /** Atom count shared by every stored frame. */
        public int getAtomsPerFrame() { return this.atomsPerFrame; }
        public long getFileBytes() { return this.fileBytes; }
        /** Byte offsets of complete frames (capped by {@link #MAX_STORED_OFFSETS}). */
        public List<Long> getOffsets() { return this.offsets; }
        /** False when more frames exist than stored offsets. */
        public boolean isOffsetsComplete() { return this.offsetsComplete; }
        /** True when a partial frame follows the last complete one. */
        public boolean isTruncatedTail() { return this.truncatedTail; }
    }

    private TrajectoryIndexReader() {
        // Utility
    }

    /**
     * Builds the index. Fails closed on a missing/unreadable file, on a missing
     * or non-integer atom-count line, on a missing comment line, or on an
     * atom-count change between frames - a wrong index would be worse than none.
     */
    public static OperationResult<TrajectoryIndex> index(Path file) {
        if (file == null) {
            return OperationResult.failed("TRAJ_IO", "No trajectory file was provided.", null);
        }
        long fileBytes;
        try {
            fileBytes = java.nio.file.Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("TRAJ_IO",
                    "Could not stat " + file.getFileName() + ": " + ex.getMessage(), ex);
        }
        List<Long> offsets = new ArrayList<>();
        int atomsPerFrame = -1;
        int frameCount = 0;
        boolean truncatedTail = false;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            while (true) {
                long offset = raf.getFilePointer();
                String countLine = readBlankSkipped(raf);
                if (countLine == null) {
                    break; // clean end exactly on a frame boundary
                }
                int atoms;
                try {
                    atoms = Integer.parseInt(countLine.trim());
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("TRAJ_SYNTAX",
                            "Frame " + (frameCount + 1) + " at byte " + offset
                                    + " does not start with an atom-count line: \""
                                    + countLine.trim() + "\"", null);
                }
                if (atoms <= 0) {
                    return OperationResult.failed("TRAJ_SYNTAX",
                            "Frame " + (frameCount + 1) + " declares " + atoms
                                    + " atoms; the count must be positive.", null);
                }
                if (atomsPerFrame < 0) {
                    atomsPerFrame = atoms;
                } else if (atoms != atomsPerFrame) {
                    return OperationResult.failed("TRAJ_INCONSISTENT",
                            "Frame " + (frameCount + 1) + " has " + atoms + " atoms but "
                                    + "earlier frames have " + atomsPerFrame
                                    + "; a fixed-topology trajectory is required.", null);
                }
                String comment = raf.readLine();
                if (comment == null) {
                    truncatedTail = true;
                    break; // comment line missing: partial frame
                }
                boolean complete = true;
                for (int atom = 0; atom < atoms; atom++) {
                    if (raf.readLine() == null) {
                        complete = false;
                        break;
                    }
                }
                if (!complete) {
                    truncatedTail = true;
                    break;
                }
                frameCount++;
                if (offsets.size() < MAX_STORED_OFFSETS) {
                    offsets.add(offset);
                }
            }
        } catch (IOException ex) {
            return OperationResult.failed("TRAJ_IO",
                    "Could not read " + file.getFileName() + ": " + ex.getMessage(), ex);
        }
        if (frameCount == 0) {
            return OperationResult.failed("TRAJ_EMPTY",
                    "No complete frame was found in " + file.getFileName() + ".", null);
        }
        return OperationResult.success("TRAJ_OK",
                "Indexed " + frameCount + " complete frame(s) of " + atomsPerFrame
                        + " atoms" + (truncatedTail ? " with a truncated tail frame" : ""),
                new TrajectoryIndex(frameCount, atomsPerFrame, fileBytes, offsets,
                        offsets.size() == frameCount, truncatedTail));
    }

    /** Reads lines, skipping blank separators; null at end of file. */
    private static String readBlankSkipped(RandomAccessFile raf) throws IOException {
        String line;
        while ((line = raf.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }
}
