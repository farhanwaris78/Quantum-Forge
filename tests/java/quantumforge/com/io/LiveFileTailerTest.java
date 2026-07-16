package quantumforge.com.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveFileTailerTest {

    @TempDir
    Path tempDir;

    @Test
    void doesNotEmitPartialLinesUntilNewline() throws Exception {
        Path log = tempDir.resolve("run.log");
        Files.writeString(log, "total energy = -1.0", StandardCharsets.UTF_8);
        LiveFileTailer tailer = new LiveFileTailer(log);
        assertTrue(tailer.pollLines().isEmpty());

        Files.writeString(log, " Ry\nnext line\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        List<String> lines = tailer.pollLines();
        assertEquals(2, lines.size());
        assertEquals("total energy = -1.0 Ry", lines.get(0));
        assertEquals("next line", lines.get(1));
    }

    @Test
    void handlesTruncationByRewinding() throws Exception {
        Path log = tempDir.resolve("run.log");
        Files.writeString(log, "line-a\nline-b\n", StandardCharsets.UTF_8);
        LiveFileTailer tailer = new LiveFileTailer(log);
        assertEquals(2, tailer.pollLines().size());

        Files.writeString(log, "fresh\n", StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        List<String> after = tailer.pollLines();
        assertEquals(List.of("fresh"), after);
    }

    @Test
    void flushPartialEmitsTrailingBuffer() throws Exception {
        Path log = tempDir.resolve("run.log");
        Files.writeString(log, "no-newline-yet", StandardCharsets.UTF_8);
        LiveFileTailer tailer = new LiveFileTailer(log);
        assertTrue(tailer.pollLines().isEmpty());
        assertEquals(List.of("no-newline-yet"), tailer.flushPartial());
    }
}
