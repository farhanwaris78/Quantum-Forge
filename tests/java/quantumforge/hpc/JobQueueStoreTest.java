package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobQueueStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsJobs() throws Exception {
        Path file = tempDir.resolve("job-queue.jsonl");
        JobQueueStore store = new JobQueueStore(file);
        JobRecord record = new JobRecord("abc123", "slurm", "siteA", "/tmp/proj");
        record.setSchedulerJobId("999");
        record.transition(JobState.RUNNING, "started");
        store.put(record);

        assertTrue(Files.isRegularFile(file));

        JobQueueStore reloaded = new JobQueueStore(file);
        reloaded.load();
        assertEquals(1, reloaded.list().size());
        JobRecord loaded = reloaded.get("abc123").orElseThrow();
        assertEquals("999", loaded.getSchedulerJobId());
        assertEquals(JobState.RUNNING, loaded.getState());
        assertEquals(1, reloaded.listActive().size());
    }
}
