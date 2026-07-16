package quantumforge.com.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void replacesExistingFileWithoutLeavingStagingArtifacts() throws Exception {
        Path target = tempDir.resolve("project.txt");
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        AtomicFileWriter.writeUtf8(target, "new-content\n");
        assertEquals("new-content\n", Files.readString(target, StandardCharsets.UTF_8));
        try (var stream = Files.list(tempDir)) {
            long staging = stream.filter(path -> path.getFileName().toString().contains(".tmp.")).count();
            assertEquals(0L, staging);
        }
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        Path target = tempDir.resolve("nested").resolve("a").resolve("data.json");
        AtomicFileWriter.writeUtf8(target, "{\"ok\":true}");
        assertTrue(Files.isRegularFile(target));
        assertEquals("{\"ok\":true}", Files.readString(target, StandardCharsets.UTF_8));
    }
}
