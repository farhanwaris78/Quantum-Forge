package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.operation.OperationResult;

class CheckpointResubmitTest {

    @TempDir
    Path tempDir;

    @Test
    void plansRestartWhenSaveAndWalltimePresent() throws Exception {
        Path save = tempDir.resolve("espresso.save");
        Files.createDirectories(save);
        Files.writeString(save.resolve("data-file-schema.xml"), "<xml/>\n", StandardCharsets.UTF_8);
        Files.writeString(save.resolve("charge-density.dat"), "x", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("espresso.log.scf"),
                "JOB DONE was not reached\nslurmstepd: error: *** JOB cancelled due to time limit ***\n",
                StandardCharsets.UTF_8);
        JobRecord prev = new JobRecord("oldjob", "slurm", "site", tempDir.toString());
        prev.setSchedulerJobId("111");
        prev.transition(JobState.RUNNING, "running");

        OperationResult<CheckpointResubmit.Plan> plan =
                CheckpointResubmit.plan(tempDir, "espresso", prev);
        assertTrue(plan.isSuccess(), plan.toString());
        assertTrue(plan.getValue().orElseThrow().isRestartRecommended());
        assertTrue(plan.getValue().orElseThrow().getRestartMode().equals("restart"));
        assertTrue(Files.isRegularFile(plan.getValue().orElseThrow().getPlanFile()));
        assertTrue(plan.getValue().orElseThrow().getReason().contains("walltime")
                || plan.getValue().orElseThrow().getDiagnostics().toString().contains("walltime"));
    }

    @Test
    void fromScratchWhenNoCheckpoint() throws Exception {
        Files.writeString(tempDir.resolve("espresso.log"), "no checkpoint here\n", StandardCharsets.UTF_8);
        OperationResult<CheckpointResubmit.Plan> plan =
                CheckpointResubmit.plan(tempDir, "espresso", null);
        assertTrue(plan.isSuccess());
        assertFalse(plan.getValue().orElseThrow().isRestartRecommended());
        assertTrue(plan.getValue().orElseThrow().getRestartMode().equals("from_scratch"));
    }
}
