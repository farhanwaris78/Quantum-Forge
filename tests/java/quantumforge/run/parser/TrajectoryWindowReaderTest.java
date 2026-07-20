/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class TrajectoryWindowReaderTest {

    private static final String THREE_FRAMES =
            "2\nframe 1\nSi 0.0 0.0 0.0\nSi 1.0 0.0 0.0\n"
            + "2\nframe 2\nSi 0.0 1.0 0.0\nSi 2.0 1.0 0.0\n"
            + "2\nframe 3\nSi 0.0 0.0 2.0\nSi 4.0 0.0 2.0\n";

    @TempDir
    Path tempDir;

    private File write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }

    private List<Long> offsets(File file) {
        return TrajectoryIndexReader.index(file.toPath()).getValue().orElseThrow()
                .getOffsets();
    }

    @Test
    void samplesExactPerFrameAndGlobalStatistics() throws IOException {
        File file = write("traj.xyz", THREE_FRAMES);
        OperationResult<TrajectoryWindowReader.WindowStats> result =
                TrajectoryWindowReader.sample(file, offsets(file), 2, 1, 2);
        assertTrue(result.isSuccess(), result.getMessage());
        TrajectoryWindowReader.WindowStats stats = result.getValue().orElseThrow();
        assertEquals(2, stats.getFrames().size(), "stride 2 over 3 frames -> frames 1,3");
        assertEquals(1, stats.getFrames().get(0).getFrameIndex());
        assertEquals(3, stats.getFrames().get(1).getFrameIndex());
        assertEquals(0.5, stats.getFrames().get(0).getCentroid()[0], 1e-12);
        assertEquals(2.0, stats.getFrames().get(1).getCentroid()[0], 1e-12);
        assertEquals(2.0, stats.getFrames().get(1).getCentroid()[2], 1e-12);
        assertEquals(4.0, stats.getFrames().get(1).getMax()[0], 1e-12);
        assertEquals(0.0, stats.getGlobalMin()[0], 1e-12);
        assertEquals(4.0, stats.getGlobalMax()[0], 1e-12);
        assertEquals(2.0, stats.getGlobalMax()[2], 1e-12);
    }

    @Test
    void failsClosedOnBadWindowsAndStaleOffsets() throws IOException {
        File file = write("traj2.xyz", THREE_FRAMES);
        List<Long> offsets = offsets(file);
        assertEquals("TRAJWIN_BOUNDS",
                TrajectoryWindowReader.sample(file, offsets, 2, 0, 1).getCode());
        assertEquals("TRAJWIN_BOUNDS",
                TrajectoryWindowReader.sample(file, offsets, 2, 4, 1).getCode());
        assertEquals("TRAJWIN_BOUNDS",
                TrajectoryWindowReader.sample(file, offsets, 2, 1, 0).getCode());
        assertEquals("TRAJWIN_OFFSET",
                TrajectoryWindowReader.sample(file, List.of(), 2, 1, 1).getCode());
        assertEquals("TRAJWIN_OFFSET",
                TrajectoryWindowReader.sample(file, List.of(0L), 9, 1, 1).getCode(),
                "A stale index (wrong atom count) must be refused");
        assertEquals("TRAJWIN_OFFSET",
                TrajectoryWindowReader.sample(file,
                        List.of(file.length() + 100L), 2, 1, 1).getCode(),
                "Offsets outside the file must be refused");
        assertEquals("TRAJWIN_IO",
                TrajectoryWindowReader.sample(new File(file.getParent(), "nope.xyz"),
                        offsets, 2, 1, 1).getCode());
    }

    @Test
    void failsClosedOnTruncationAndNonNumericRows() throws IOException {
        File truncated = write("trunc.xyz", "2\nf1\nSi 0.0 0.0 0.0\n");
        // Offset 0 points at a frame that ends inside its atom rows.
        assertEquals("TRAJWIN_TRUNCATED",
                TrajectoryWindowReader.sample(truncated, List.of(0L), 2, 1, 1).getCode());
        File nonNumeric = write("bad.xyz", "2\nf1\nSi 0.0 x 0.0\nSi 1.0 0.0 0.0\n");
        assertEquals("TRAJWIN_SYNTAX",
                TrajectoryWindowReader.sample(nonNumeric, List.of(0L), 2, 1, 1).getCode());
    }
}
