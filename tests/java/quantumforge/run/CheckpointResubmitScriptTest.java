package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class CheckpointResubmitScriptTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsExecutableResubmitScript() throws Exception {
        Files.writeString(tempDir.resolve("espresso.log"),
                "slurmstepd: error: *** JOB cancelled due to time limit ***\n",
                StandardCharsets.UTF_8);
        Path save = tempDir.resolve("espresso.save");
        Files.createDirectories(save);
        Files.writeString(save.resolve("data-file-schema.xml"), "<xml/>\n", StandardCharsets.UTF_8);
        Files.writeString(save.resolve("charge-density.dat"), "x", StandardCharsets.UTF_8);

        OperationResult<CheckpointResubmit.Plan> plan =
                CheckpointResubmit.plan(tempDir, "espresso", null);
        assertTrue(plan.isSuccess(), plan.toString());
        OperationResult<Path> script = CheckpointResubmit.exportScript(
                tempDir, plan.getValue().orElseThrow(),
                new String[] {"pw.x", "-in", "espresso.in"});
        assertTrue(script.isSuccess(), script.toString());
        String body = Files.readString(script.getValue().orElseThrow(), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("#!/usr/bin/env bash"));
        assertTrue(body.contains("restart_mode="));
        assertTrue(body.contains("'pw.x'"));
    }
}
