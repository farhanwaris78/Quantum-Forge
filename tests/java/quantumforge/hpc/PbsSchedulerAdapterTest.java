package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PbsSchedulerAdapterTest {

    @Test
    void generatesPbsDirectives() {
        PbsSchedulerAdapter adapter = new PbsSchedulerAdapter();
        SchedulerResources resources = SchedulerResources.builder()
                .nodes(1).ntasks(8).cpusPerTask(2)
                .partition("batch").account("grp").walltime("01:30:00")
                .build();
        String script = adapter.generateScript("job1", resources,
                List.<String[]>of(new String[] {"pw.x", "-in", "espresso.in"}));
        assertTrue(script.contains("#PBS -N job1"));
        assertTrue(script.contains("#PBS -q batch"));
        assertTrue(script.contains("mpiprocs=8"));
        assertTrue(script.contains("ompthreads=2"));
        assertTrue(script.contains("'pw.x'"));
        assertEquals("qsub", adapter.submitCommand("/tmp/job.sh")[0]);
        assertEquals("qdel", adapter.cancelCommand("12345")[0]);
    }

    @Test
    void parsesJobIdWithHostSuffix() {
        PbsSchedulerAdapter adapter = new PbsSchedulerAdapter();
        assertEquals("98765", adapter.parseJobId("98765.cluster.local\n").orElseThrow());
    }

    @Test
    void documentedAbsenceNeedleIsOwned() {
        PbsSchedulerAdapter adapter = new PbsSchedulerAdapter();
        assertTrue(adapter.isJobAbsent("qstat: Unknown Job Id 9156.hpc02"));
        assertFalse(adapter.isJobAbsent(""));
        assertFalse(adapter.isJobAbsent(null));
        assertFalse(adapter.isJobAbsent("qstat: cannot connect to server"),
                "a server-communication failure is never absence");
    }
}
