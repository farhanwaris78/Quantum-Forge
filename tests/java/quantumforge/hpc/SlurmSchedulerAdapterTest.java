package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SlurmSchedulerAdapterTest {

    @Test
    void generatesTypedDirectives() {
        SlurmSchedulerAdapter adapter = new SlurmSchedulerAdapter();
        SchedulerResources resources = SchedulerResources.builder()
                .nodes(2).ntasks(64).cpusPerTask(2)
                .partition("compute").account("projA").walltime("02:00:00")
                .build();
        String script = adapter.generateScript("myjob", resources,
                List.of(new String[] {"pw.x", "-in", "espresso.in"}));
        assertTrue(script.startsWith("#!/usr/bin/env bash"));
        assertTrue(script.contains("#SBATCH --nodes=2"));
        assertTrue(script.contains("#SBATCH --ntasks=64"));
        assertTrue(script.contains("#SBATCH --partition=compute"));
        assertTrue(script.contains("#SBATCH --account=projA"));
        assertTrue(script.contains("'pw.x'"));
        assertFalse(script.contains("qsub"));
    }

    @Test
    void parsesJobId() {
        SlurmSchedulerAdapter adapter = new SlurmSchedulerAdapter();
        assertEquals("12345", adapter.parseJobId("Submitted batch job 12345\n").orElseThrow());
        assertEquals("9", adapter.parseJobId("9").orElseThrow());
        assertTrue(adapter.parseJobId("no id here").isEmpty());
    }

    @Test
    void cancelAndStatusCommandsAreArrays() {
        SlurmSchedulerAdapter adapter = new SlurmSchedulerAdapter();
        assertEquals("scancel", adapter.cancelCommand("42")[0]);
        assertEquals("squeue", adapter.statusCommand("42")[0]);
        assertEquals("sbatch", adapter.submitCommand("/tmp/job.sh")[0]);
    }

    @Test
    void singleArrayTaskGrammarIsSlurmsOwn() {
        SlurmSchedulerAdapter adapter = new SlurmSchedulerAdapter();
        // scancel genuinely accepts one array task as 'jobid_index' (batch 126:
        // the adapter became the single grammar owner the cancel plan delegates
        // to, so the plan's long-standing acceptance of 4521_3 had to land here).
        assertEquals("4521_3", adapter.cancelCommand("4521_3")[1]);
        assertEquals("4521_3", adapter.statusCommand("4521_3")[2]);
        try {
            adapter.cancelCommand("4521_3_1");
            org.junit.jupiter.api.Assertions.fail("double array index is not grammar");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("array"), expected.getMessage());
        }
    }
}
