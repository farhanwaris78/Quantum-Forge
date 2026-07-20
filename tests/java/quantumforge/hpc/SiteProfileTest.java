package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void pjmSchedulerIsFirstClassAndResolvesToTypedAdapter() throws Exception {
        Path file = tempDir.resolve("pjm.yaml");
        Files.writeString(file, "id: fugaku\nscheduler: pjm\n", StandardCharsets.UTF_8);
        SiteProfile profile = SiteProfile.load(file);
        assertEquals("pjm", profile.getScheduler(),
                "pjm must be a first-class canonical name, never sidelined");
        assertTrue(profile.schedulerAdapter() instanceof PjmSchedulerAdapter,
                "The Fugaku/TCS adapter must resolve exactly like slurm/pbs/sge");
    }

    @Test
    void vernacularAliasesAreCanonicalizedAtConstruction() throws Exception {
        Path file = tempDir.resolve("aliases.yaml");
        Files.writeString(file, "id: a\nscheduler: torque\n", StandardCharsets.UTF_8);
        SiteProfile torque = SiteProfile.load(file);
        assertEquals("pbs", torque.getScheduler(), "torque canonicalizes to pbs");
        assertTrue(torque.schedulerAdapter() instanceof PbsSchedulerAdapter);

        Files.writeString(file, "id: a\nscheduler: UGE\n", StandardCharsets.UTF_8);
        SiteProfile uge = SiteProfile.load(file);
        assertEquals("sge", uge.getScheduler(), "UGE (any case) canonicalizes to sge");
        assertTrue(uge.schedulerAdapter() instanceof SgeSchedulerAdapter);

        SiteProfile built = SiteProfile.builder().id("b").scheduler(" ge ").build();
        assertEquals("sge", built.getScheduler(), "the builder path canonicalizes too");
        assertTrue(built.schedulerAdapter() instanceof SgeSchedulerAdapter);
    }

    @Test
    void unknownSchedulerPassesThroughThenRefusesTypedResolution() {
        SiteProfile profile = SiteProfile.builder().id("x").scheduler("lsf").build();
        assertEquals("lsf", profile.getScheduler(),
                "unknown names pass through unchanged so the validator can flag them");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                profile::schedulerAdapter,
                "adapter resolution must fail closed with a typed, supported-set-naming error");
        assertTrue(error.getMessage().contains("slurm, pbs, pjm, sge"),
                "the refusal must name the registry's supported set: " + error.getMessage());
    }

    @Test
    void defaultSchedulerRemainsSlurm() {
        SiteProfile profile = SiteProfile.builder().id("x").build();
        assertEquals("slurm", profile.getScheduler());
        assertTrue(profile.schedulerAdapter() instanceof SlurmSchedulerAdapter);
    }
}
