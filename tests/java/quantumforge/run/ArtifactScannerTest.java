package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsSaveDirectoryChargeDensity() throws Exception {
        Path save = tempDir.resolve("espresso.save");
        Files.createDirectories(save);
        Files.writeString(save.resolve("data-file-schema.xml"), "<xml/>\n", StandardCharsets.UTF_8);
        Files.writeString(save.resolve("charge-density.dat"), "x", StandardCharsets.UTF_8);
        Files.writeString(save.resolve("wfc1.dat"), "y", StandardCharsets.UTF_8);

        Set<String> tags = ArtifactScanner.scan(tempDir, "espresso");
        assertTrue(tags.contains("charge-density"));
        assertTrue(tags.contains("wavefunctions"));
    }

    @Test
    void detectsSuccessfulScfLog() throws Exception {
        Files.writeString(tempDir.resolve("espresso.log.scf"),
                "total energy = -1.0 Ry\n     JOB DONE.\n", StandardCharsets.UTF_8);
        Set<String> tags = ArtifactScanner.scan(tempDir, "espresso");
        assertTrue(tags.contains("charge-density"));
    }

    @Test
    void emptyDirectoryHasNoArtifacts() {
        Set<String> tags = ArtifactScanner.scan(tempDir, "espresso");
        assertFalse(tags.contains("charge-density"));
    }
}
