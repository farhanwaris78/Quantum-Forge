/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Windowed/decimated coordinate sampling on top of a trajectory byte index
 * (Roadmap #127 consumption layer): seeks to each sampled frame's byte offset,
 * parses exactly its atom rows, and reports per-frame bounding boxes/centroids
 * plus the global sampled bounding box - O(sampled frames * atoms) work, never
 * whole-file materialization. Fail-closed on bad windows, stale offsets,
 * truncation and non-finite values, with codes. Statistics are descriptive:
 * coordinates are used as printed (XYZ Angstrom convention, unvalidated), and
 * no periodic unwrapping is applied - MSD/diffusion consumers must unwrap
 * (#156).
 */
public final class TrajectoryWindowReader {

    /** Sampling never returns more frames than this, whatever the window. */
    public static final int MAX_SAMPLED_FRAMES = 10_000;

    /** Per-frame sampled statistics: 1-based frame index, bbox, and centroid. */
    public static final class FrameStats {
        private final int frameIndex;
        private final double[] min = new double[3];
        private final double[] max = new double[3];
        private final double[] centroid = new double[3];

        FrameStats(int frameIndex) {
            this.frameIndex = frameIndex;
        }

        public int getFrameIndex() { return this.frameIndex; }
        public double[] getMin() { return this.min.clone(); }
        public double[] getMax() { return this.max.clone(); }
        public double[] getCentroid() { return this.centroid.clone(); }
    }

    /** Sampling result: the per-frame rows plus the global sampled bounding box. */
    public static final class WindowStats {
        private final List<FrameStats> frames = new ArrayList<>();
        private final double[] globalMin = new double[3];
        private final double[] globalMax = new double[3];
        private final int stride;

        WindowStats(int stride) {
            this.stride = stride;
        }

        public List<FrameStats> getFrames() { return List.copyOf(this.frames); }
        public int getStride() { return this.stride; }
        public double[] getGlobalMin() { return this.globalMin.clone(); }
        public double[] getGlobalMax() { return this.globalMax.clone(); }
    }

    private TrajectoryWindowReader() { }

    /**
     * Samples frames from {@code file}: 1-based start, stride between sampled
     * frames, offsets/atomsPerFrame from the batch-56 index. Codes:
     * TRAJWIN_BOUNDS, TRAJWIN_OFFSET, TRAJWIN_TRUNCATED, TRAJWIN_SYNTAX,
     * TRAJWIN_VALUE, TRAJWIN_IO.
     */
    public static OperationResult<WindowStats> sample(File file, List<Long> offsets,
            int atomsPerFrame, int startFrame, int stride) {
        if (file == null || !file.isFile()) {
            return OperationResult.failed("TRAJWIN_IO",
                    "The trajectory file does not exist.", null);
        }
        if (offsets == null || offsets.isEmpty() || atomsPerFrame <= 0) {
            return OperationResult.failed("TRAJWIN_OFFSET",
                    "No frame offsets were supplied; index the trajectory first (the "
                            + "TRAJECTORY_INDEX analysis) so reads land on real frames.",
                    null);
        }
        long fileBytes = file.length();
        for (int i = 0; i < offsets.size(); i++) {
            long offset = offsets.get(i).longValue();
            if (offset < 0L || offset >= fileBytes) {
                return OperationResult.failed("TRAJWIN_OFFSET",
                        "Frame " + (i + 1) + " byte offset " + offset
                                + " is outside the file (" + fileBytes
                                + " bytes); the index is stale or foreign.", null);
            }
        }
        if (startFrame < 1 || startFrame > offsets.size()) {
            return OperationResult.failed("TRAJWIN_BOUNDS",
                    "The start frame must be within 1.." + offsets.size() + "; got "
                            + startFrame + ".", null);
        }
        if (stride < 1) {
            return OperationResult.failed("TRAJWIN_BOUNDS",
                    "The stride must be >= 1; got " + stride + ".", null);
        }
        List<Integer> sampled = new ArrayList<>();
        for (int frame = startFrame; frame <= offsets.size()
                && sampled.size() < MAX_SAMPLED_FRAMES; frame += stride) {
            sampled.add(frame);
        }
        if (sampled.isEmpty()) {
            return OperationResult.failed("TRAJWIN_BOUNDS",
                    "The window selected no frames (cap " + MAX_SAMPLED_FRAMES + ").",
                    null);
        }
        WindowStats stats = new WindowStats(stride);
        boolean[] globalInit = {false};
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            for (int frame : sampled) {
                reader.seek(offsets.get(frame - 1).longValue());
                String countLine = reader.readLine();
                String commentLine = reader.readLine();
                if (countLine == null || commentLine == null) {
                    return OperationResult.failed("TRAJWIN_TRUNCATED",
                            "Frame " + frame + " ends before its header.", null);
                }
                int declared;
                try {
                    declared = Integer.parseInt(countLine.trim());
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("TRAJWIN_SYNTAX",
                            "Frame " + frame + " atom-count line is not an integer: \""
                                    + countLine.trim() + "\".", null);
                }
                if (declared != atomsPerFrame) {
                    return OperationResult.failed("TRAJWIN_OFFSET",
                            "Frame " + frame + " declares " + declared + " atoms, expected "
                                    + atomsPerFrame + "; the offset list does not describe "
                                    + "this file.", null);
                }
                FrameStats row = new FrameStats(frame);
                boolean init = false;
                double sx = 0.0;
                double sy = 0.0;
                double sz = 0.0;
                for (int atom = 0; atom < atomsPerFrame; atom++) {
                    String line = reader.readLine();
                    if (line == null) {
                        return OperationResult.failed("TRAJWIN_TRUNCATED",
                                "Frame " + frame + " ends inside atom row "
                                        + (atom + 1) + ".", null);
                    }
                    String[] cells = line.trim().split("\\s+");
                    if (cells.length < 4) {
                        return OperationResult.failed("TRAJWIN_SYNTAX",
                                "Frame " + frame + " atom row " + (atom + 1)
                                        + " has fewer than 4 columns.", null);
                    }
                    double x;
                    double y;
                    double z;
                    try {
                        x = Double.parseDouble(cells[1]);
                        y = Double.parseDouble(cells[2]);
                        z = Double.parseDouble(cells[3]);
                    } catch (NumberFormatException ex) {
                        return OperationResult.failed("TRAJWIN_SYNTAX",
                                "Frame " + frame + " atom row " + (atom + 1)
                                        + " has a non-numeric coordinate.", null);
                    }
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        return OperationResult.failed("TRAJWIN_VALUE",
                                "Frame " + frame + " atom row " + (atom + 1)
                                        + " has a non-finite coordinate.", null);
                    }
                    if (!init) {
                        row.min[0] = x; row.min[1] = y; row.min[2] = z;
                        row.max[0] = x; row.max[1] = y; row.max[2] = z;
                        init = true;
                    } else {
                        row.min[0] = Math.min(row.min[0], x);
                        row.min[1] = Math.min(row.min[1], y);
                        row.min[2] = Math.min(row.min[2], z);
                        row.max[0] = Math.max(row.max[0], x);
                        row.max[1] = Math.max(row.max[1], y);
                        row.max[2] = Math.max(row.max[2], z);
                    }
                    sx += x; sy += y; sz += z;
                }
                row.centroid[0] = sx / atomsPerFrame;
                row.centroid[1] = sy / atomsPerFrame;
                row.centroid[2] = sz / atomsPerFrame;
                if (!globalInit[0]) {
                    System.arraycopy(row.min, 0, stats.globalMin, 0, 3);
                    System.arraycopy(row.max, 0, stats.globalMax, 0, 3);
                    globalInit[0] = true;
                } else {
                    for (int axis = 0; axis < 3; axis++) {
                        stats.globalMin[axis] = Math.min(stats.globalMin[axis],
                                row.min[axis]);
                        stats.globalMax[axis] = Math.max(stats.globalMax[axis],
                                row.max[axis]);
                    }
                }
                stats.frames.add(row);
            }
        } catch (IOException ex) {
            return OperationResult.failed("TRAJWIN_IO",
                    "Reading the trajectory failed: " + ex.getMessage(), ex);
        }
        return OperationResult.success("TRAJWIN_OK",
                String.format(Locale.ROOT, "Sampled %d frame(s) at stride %d.",
                        stats.frames.size(), stride),
                stats);
    }
}
