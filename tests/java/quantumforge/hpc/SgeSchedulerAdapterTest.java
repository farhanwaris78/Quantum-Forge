package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SgeSchedulerAdapterTest {

    @Test
    void generatesSgeDirectives() {
        SgeSchedulerAdapter adapter = new SgeSchedulerAdapter();
        SchedulerResources resources = SchedulerResources.builder()
                .ntasks(4).cpusPerTask(2).partition("all.q").walltime("1:30:00")
                .build();
        String script = adapter.generateScript("qejob", resources,
                List.of(new String[] {"pw.x", "-in", "espresso.in"}));
        assertTrue(script.contains("#$ -N qejob"));
        assertTrue(script.contains("#$ -pe smp 8"));
        assertTrue(script.contains("#$ -q all.q"));
        assertTrue(script.contains("#$ -l h_rt=1:30:00"));
        assertEquals("qsub", adapter.submitCommand("/tmp/x.sh")[0]);
        assertEquals("123", adapter.parseJobId("Your job 123 (\"qejob\") has been submitted").orElseThrow());
    }

    @Test
    void documentedAbsenceNeedleIsOwned() {
        SgeSchedulerAdapter adapter = new SgeSchedulerAdapter();
        assertTrue(adapter.isJobAbsent("Following jobs do not exist: 8800"));
        assertFalse(adapter.isJobAbsent(""));
        assertFalse(adapter.isJobAbsent(null));
        assertFalse(adapter.isJobAbsent("error: commlib error"),
                "a commlib failure is never absence");
    }
}
