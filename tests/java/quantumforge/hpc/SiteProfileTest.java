package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteProfileTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSimpleYamlLikeProfile() throws Exception {
        Path file = tempDir.resolve("cluster.yaml");
        Files.writeString(file, """
                id: test-cluster
                scheduler: slurm
                staging_root: /scratch/u/qf
                partition: compute
                account: abc
                ntasks: 16
                module: qe/7.5
                module: openmpi
                env: OMP_NUM_THREADS=1
                """, StandardCharsets.UTF_8);
        SiteProfile profile = SiteProfile.load(file);
        assertEquals("test-cluster", profile.getId());
        assertEquals("slurm", profile.getScheduler());
        assertEquals("/scratch/u/qf", profile.getStagingRoot());
        assertEquals("compute", profile.getDefaultPartition());
        assertEquals(16, profile.getDefaultResources().getNtasks());
        assertEquals(2, profile.getModules().size());
        assertEquals("1", profile.getEnvironment().get("OMP_NUM_THREADS"));
        assertTrue(profile.schedulerAdapter() instanceof SlurmSchedulerAdapter);
    }
}
