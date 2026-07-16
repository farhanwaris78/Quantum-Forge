package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class RestartManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void missingSaveDirectoryRecommendsFromScratch() {
        OperationResult<RestartManager.RestartAssessment> result =
                RestartManager.assess(tempDir, "espresso");
        assertTrue(result.isSuccess());
        assertFalse(result.getValue().orElseThrow().isRestartSafe());
        assertEquals("from_scratch", result.getValue().orElseThrow().getRecommendedRestartMode());
    }

    @Test
    void completeSaveDirectoryIsRestartSafe() throws Exception {
        Path save = tempDir.resolve("espresso.save");
        Files.createDirectories(save);
        Files.writeString(save.resolve("data-file-schema.xml"), "<Root/>\n", StandardCharsets.UTF_8);
        Files.writeString(save.resolve("charge-density.dat"), "x", StandardCharsets.UTF_8);
        Files.writeString(save.resolve("wfc1.dat"), "y", StandardCharsets.UTF_8);

        OperationResult<RestartManager.RestartAssessment> result =
                RestartManager.assess(tempDir, "espresso");
        assertTrue(result.isSuccess());
        assertTrue(result.getValue().orElseThrow().isRestartSafe());
        assertEquals("restart", result.getValue().orElseThrow().getRecommendedRestartMode());
        assertTrue(RestartManager.namelistSnippet(result.getValue().orElseThrow())
                .contains("restart_mode = 'restart'"));
    }

    @Test
    void incompleteSaveIsUnsafe() throws Exception {
        Path save = tempDir.resolve("espresso.save");
        Files.createDirectories(save);
        Files.writeString(save.resolve("readme.txt"), "empty", StandardCharsets.UTF_8);
        OperationResult<RestartManager.RestartAssessment> result =
                RestartManager.assess(tempDir, "espresso");
        assertTrue(result.isSuccess());
        assertFalse(result.getValue().orElseThrow().isRestartSafe());
    }
}
