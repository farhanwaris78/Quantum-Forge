package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class ProcessTreeKillerTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void stopsAChildProcessTree() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sleep", "30");
        Process process = builder.start();
        assertTrue(process.isAlive());
        boolean stopped = ProcessTreeKiller.stop(process, Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertTrue(stopped);
        assertFalse(process.isAlive() || process.waitFor(1, TimeUnit.SECONDS) && process.isAlive());
    }

    @Test
    void collectTreeIncludesRoot() {
        ProcessHandle self = ProcessHandle.current();
        List<ProcessHandle> tree = ProcessTreeKiller.collectTree(self);
        assertFalse(tree.isEmpty());
        assertTrue(tree.stream().anyMatch(handle -> handle.pid() == self.pid()));
    }
}
