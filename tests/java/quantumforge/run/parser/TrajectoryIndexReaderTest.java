package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

/** Batch-56 coverage for the streaming XYZ trajectory indexer (Roadmap #127). */
class TrajectoryIndexReaderTest {

    @TempDir
    private Path tempDir;

    private static final String FRAME =
            "2\n"
            + "comment\n"
            + "Si 0.0 0.0 0.0\n"
            + "Si 1.0 1.0 1.0\n";

    @Test
    void indexesOffsetsExactly() throws IOException {
        String three = FRAME + FRAME + FRAME;
        Path file = write("traj.xyz", three);
        OperationResult<TrajectoryIndexReader.TrajectoryIndex> result =
                TrajectoryIndexReader.index(file);
        assertTrue(result.isSuccess(), result.getMessage());
        TrajectoryIndexReader.TrajectoryIndex index = result.getValue().orElseThrow();
        assertEquals(3, index.getFrameCount());
        assertEquals(2, index.getAtomsPerFrame());
        assertEquals(Files.size(file), index.getFileBytes());
        assertFalse(index.isTruncatedTail());
        assertTrue(index.isOffsetsComplete());
        assertEquals(3, index.getOffsets().size());
        assertEquals(0L, index.getOffsets().get(0));
        int frameBytes = FRAME.getBytes(StandardCharsets.UTF_8).length;
        assertEquals((long) frameBytes, index.getOffsets().get(1));
        assertEquals(2L * frameBytes, index.getOffsets().get(2));
    }

    @Test
    void truncatedTailIsReportedNotDropped() throws IOException {
        String cut = FRAME + FRAME + "2\ncomment\nSi 0.0 0.0\n"; // missing atom row
        Path file = write("traj-cut.xyz", cut);
        OperationResult<TrajectoryIndexReader.TrajectoryIndex> result =
                TrajectoryIndexReader.index(file);
        assertTrue(result.isSuccess(), result.getMessage());
        TrajectoryIndexReader.TrajectoryIndex index = result.getValue().orElseThrow();
        assertEquals(2, index.getFrameCount(), "Only complete frames are indexed");
        assertTrue(index.isTruncatedTail());
    }

    @Test
    void refusesInconsistentTopologyAndBadHeaders() throws IOException {
        Path mixed = write("traj-mixed.xyz",
                FRAME + "3\ncomment\nH 0 0 0\nH 0 0 0\nH 0 0 0\n");
        OperationResult<TrajectoryIndexReader.TrajectoryIndex> inconsistent =
                TrajectoryIndexReader.index(mixed);
        assertFalse(inconsistent.isSuccess());
        assertEquals("TRAJ_INCONSISTENT", inconsistent.getCode());

        Path noCount = write("traj-bad.xyz", "not-a-number\ncomment\n");
        assertEquals("TRAJ_SYNTAX", TrajectoryIndexReader.index(noCount).getCode());

        Path empty = write("traj-empty.xyz", "");
        assertEquals("TRAJ_EMPTY", TrajectoryIndexReader.index(empty).getCode());

        assertEquals("TRAJ_IO",
                TrajectoryIndexReader.index(this.tempDir.resolve("absent.xyz")).getCode());
    }

    private Path write(String name, String content) throws IOException {
        Path file = this.tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
