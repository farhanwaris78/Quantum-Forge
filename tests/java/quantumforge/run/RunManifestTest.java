package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunManifestTest {

    @TempDir
    Path tempDir;

    @Test
    void serializesAndAppendsJsonLines() throws Exception {
        Path input = tempDir.resolve("espresso.in");
        Files.writeString(input, "&control\n calculation='scf'\n/\n", StandardCharsets.UTF_8);
        Path out = tempDir.resolve("espresso.log");
        Files.writeString(out, "JOB DONE", StandardCharsets.UTF_8);

        RunManifest manifest = new RunManifest("job1", "SCF-stage-0",
                List.of("pw.x", "-in", "espresso.in"), tempDir);
        manifest.setInput(input);
        manifest.setOutputs(out, null);
        manifest.setQeVersion("7.5");
        manifest.finish(0, "COMPLETED");
        manifest.appendToProject(tempDir);

        Path file = tempDir.resolve(RunManifest.FILE_NAME);
        assertTrue(Files.isRegularFile(file));
        String line = Files.readString(file, StandardCharsets.UTF_8).trim();
        assertTrue(line.startsWith("{"));
        assertTrue(line.contains("\"jobId\":\"job1\""));
        assertTrue(line.contains("\"status\":\"COMPLETED\""));
        assertTrue(line.contains("\"qeVersion\":\"7.5\""));
        assertTrue(line.contains("\"exitCode\":0"));
        assertTrue(line.contains("pw.x"));
        assertTrue(line.contains("inputSha256"));

        // Append a second line without corrupting the first.
        RunManifest second = new RunManifest("job2", "BAND-stage-0", List.of("bands.x"), tempDir);
        second.finish(1, "FAILED");
        second.appendToProject(tempDir);
        long lines = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(s -> !s.isBlank()).count();
        assertEquals(2L, lines);
    }
}
