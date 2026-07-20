/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.SiteProfileSpec.SiteProfile;

class SiteProfileSpecTest {

    @Test
    void fullProfileRendersTheOwnedKeyValueBlock() {
        OperationResult<SiteProfile> result = SiteProfileSpec.validate(
                "atlas", "SLURM", "srun", "main", "phys2026", "/scratch/farhan",
                256, "qe/7.3,mpi/openmpi");
        assertTrue(result.isSuccess(), result.toString());
        SiteProfile profile = result.getValue().orElseThrow();
        assertEquals("slurm", profile.getScheduler(),
                "typed enums normalize to lowercase, never echo the raw casing");
        assertFalse(profile.isScratchTrimmed());
        String block = profile.render();
        assertTrue(block.contains("# qf-site-profile v1"), block);
        assertTrue(block.contains("NOT YAML"), block);
        assertTrue(block.contains("cluster = atlas\n"), block);
        assertTrue(block.contains("scheduler = slurm\n"), block);
        assertTrue(block.contains("launcher = srun\n"), block);
        assertTrue(block.contains("default_partition = main\n"), block);
        assertTrue(block.contains("account = phys2026\n"), block);
        assertTrue(block.contains("scratch_dir = /scratch/farhan\n"), block);
        assertTrue(block.contains("max_nodes = 256\n"), block);
        assertTrue(block.contains("modules = qe/7.3,mpi/openmpi\n"), block,
                "the modules value stays whitespace-free by construction");
    }

    @Test
    void omissionsAndTrailingSlashRenderHonestly() {
        OperationResult<SiteProfile> result = SiteProfileSpec.validate(
                "atlas", "sge", "mpirun", "", "", "/scratch/farhan/", 8, "");
        assertTrue(result.isSuccess(), result.toString());
        SiteProfile profile = result.getValue().orElseThrow();
        String block = profile.render();
        assertTrue(block.contains("# default_partition = (unset - honestly omitted"),
                block);
        assertTrue(block.contains("# account = (unset - honestly omitted)"), block);
        assertTrue(block.contains("scratch_dir = /scratch/farhan   # trailing '/' "
                + "normalized away at validation"), block);
        assertTrue(block.contains("# modules = (none declared"), block);
        assertFalse(block.contains("default_partition = main"), block);
        assertFalse(block.contains("\ndefault_partition = "), block,
                "an omitted partition must not materialize as a key");
    }

    @Test
    void refusalsAreFailClosed() {
        assertEquals("SITE_NAME", SiteProfileSpec.validate(
                "1atlas", "slurm", "srun", "", "", "/scratch/x", 8, "").getCode());
        assertEquals("SITE_SCHEDULER", SiteProfileSpec.validate(
                "atlas", "torque-ish", "srun", "", "", "/scratch/x", 8, "").getCode(),
                "free-form scheduler strings refuse, they are not echoed");
        assertEquals("SITE_LAUNCHER", SiteProfileSpec.validate(
                "atlas", "slurm", "ibrun", "", "", "/scratch/x", 8, "").getCode());
        assertEquals("SITE_PARTITION", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "bad part", "", "/scratch/x", 8, "").getCode());
        assertEquals("SITE_ACCOUNT", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "acct;rm", "/scratch/x", 8, "").getCode());
        assertEquals("SITE_SCRATCH", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "", "scratch/farhan", 8, "").getCode(),
                "relative scratch paths refuse - the policy must be deliberate");
        assertEquals("SITE_SCRATCH", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "", "/scratch/../etc", 8, "").getCode());
        assertEquals("SITE_SCRATCH", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "", "/scratch/$USER", 8, "").getCode(),
                "expansion characters refuse - the draft is literal");
        assertEquals("SITE_MAXNODES", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "", "/scratch/x", 0, "").getCode());
        assertEquals("SITE_MAXNODES", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "", "/scratch/x", 100001, "").getCode());
        assertEquals("SITE_MODULE", SiteProfileSpec.validate(
                "atlas", "slurm", "srun", "", "", "/scratch/x", 8, "qe 7.3").getCode(),
                "whitespace inside a module token refuses so values stay unambiguous");
    }
}
