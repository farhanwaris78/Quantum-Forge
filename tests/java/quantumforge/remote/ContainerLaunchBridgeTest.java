/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.hpc.SchedulerResources;
import quantumforge.hpc.SiteProfile;
import quantumforge.operation.OperationResult;

/**
 * Batch-147 (#103 launch-bridge slice): the container profile and the site
 * profile compose through TYPED objects - launcher name and counts resolve
 * from the owner, flag spelling and pull source stay REQUIRED-EDIT, and the
 * embedded preview is the batch-132 owner's verbatim output (never a copy).
 */
class ContainerLaunchBridgeTest {

    private static final String DIGEST =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static ContainerProfileSpec.ContainerProfile container(String mpiAnswer) {
        return ContainerProfileSpec.validate("apptainer",
                "library/qe/qe:7.3@" + DIGEST, "/scratch,/opt/pseudo", mpiAnswer)
                .getValue().orElseThrow();
    }

    private static SiteProfile site(String id, String scheduler, String launcher,
            int ntasks, int cpusPerTask, int nodes) {
        return SiteProfile.builder()
                .id(id)
                .scheduler(scheduler)
                .stagingRoot("/scratch/stage")
                .scratchRoot("/scratch")
                .defaultPartition("compute")
                .defaultAccount("prj01")
                .mpiLauncher(launcher)
                .defaultResources(SchedulerResources.builder()
                        .nodes(nodes).ntasks(ntasks).cpusPerTask(cpusPerTask)
                        .walltime("01:00:00").partition("compute").account("prj01")
                        .build())
                .build();
    }

    @Test
    void hostMpiBridgeResolvesNameAndCountsFromTheSiteOwner() {
        OperationResult<ContainerLaunchBridge.LaunchBridge> bridged =
                ContainerLaunchBridge.bridge(container("yes"),
                        site("fugaku-dev", "slurm", "srun", 8, 4, 2),
                        List.of("pw.x", "-i", "pw.in"));
        assertTrue(bridged.isSuccess(), bridged.toString());
        assertEquals("CONTAINER_BRIDGE_OK", bridged.getCode());
        ContainerLaunchBridge.LaunchBridge bridge = bridged.getValue().orElseThrow();
        assertEquals("fugaku-dev", bridge.getSiteId());
        assertEquals("slurm", bridge.getScheduler());
        assertEquals("srun", bridge.getMpiLauncher());
        assertEquals(8, bridge.getNtasks());
        assertEquals(4, bridge.getCpusPerTask());
        assertEquals(2, bridge.getNodes());
        assertTrue(bridge.isHostMpiShape());
        String block = bridge.renderBlock();
        assertTrue(block.contains("# container+site launch bridge (REVIEW only - "
                + "launched = NO)"), block);
        assertTrue(block.contains("# site: fugaku-dev (scheduler: slurm, batch-134 "
                + "canonical)"), block);
        assertTrue(block.contains("# mpi launcher: srun (the site profile's own "
                + "declaration, verbatim)"), block);
        assertTrue(block.contains("# counts: ntasks=8, cpus-per-task=4, nodes=2 (site "
                + "defaultResources - stated, never hidden)"), block);
        assertTrue(block.contains("# SHAPE (DECLARED by you): host-MPI compatible -> "
                + "the runner stays OUTSIDE the container"), block);
        assertTrue(block.contains("# the <mpirun/srun + counts> anchor below thus "
                + "reads: 'srun' with ntasks=8, cpus-per-task=4"), block);
        assertTrue(block.contains("# REQUIRED-EDIT: the argument SPELLING for 'srun' "
                + "is its man page's (-n/-np family)"), block);
        // The no-drift pin: the embedded preview is the owner's verbatim output.
        String verbatim = container("yes").execPreview(List.of("pw.x", "-i", "pw.in"))
                .getValue().orElseThrow();
        assertTrue(block.contains(verbatim),
                "the embedded preview is execPreview UNCHANGED - bridge and preview "
                        + "can never diverge:\n" + block);
        assertTrue(block.contains("launched = NO - exec-shape review only"), block);
    }

    @Test
    void containerInternalShapeSaysTheRunnerLivesInside() {
        OperationResult<ContainerLaunchBridge.LaunchBridge> bridged =
                ContainerLaunchBridge.bridge(container("no"),
                        site("pbs-lab", "pbs", "mpirun", 16, 2, 4),
                        List.of("pw.x", "-i", "pw.in"));
        assertTrue(bridged.isSuccess(), bridged.toString());
        String block = bridged.getValue().orElseThrow().renderBlock();
        assertTrue(block.contains("container-internal MPI -> the runner lives INSIDE"),
                block);
        assertTrue(block.contains("reads: 'mpirun' with ntasks=16, cpus-per-task=2"),
                block);
        assertTrue(block.contains("# site: pbs-lab (scheduler: pbs, batch-134 "
                + "canonical)"), block);
    }

    @Test
    void pjmSitesResolveThroughTheRegistry() {
        OperationResult<ContainerLaunchBridge.LaunchBridge> bridged =
                ContainerLaunchBridge.bridge(container("yes"),
                        site("riken-mini", "pjm", "mpiexec", 4, 48, 2),
                        List.of("pw.x", "-i", "pw.in"));
        assertTrue(bridged.isSuccess(), bridged.toString());
        ContainerLaunchBridge.LaunchBridge bridge = bridged.getValue().orElseThrow();
        assertEquals("pjm", bridge.getScheduler(),
                "batch 134/135: pjm resolves first-class, never through a second table");
        assertTrue(bridge.renderBlock().contains("# site: riken-mini (scheduler: pjm"),
                bridge.renderBlock());
    }

    @Test
    void aSiteWithoutALauncherLeavesNoRunnerToPlace() {
        OperationResult<ContainerLaunchBridge.LaunchBridge> refused =
                ContainerLaunchBridge.bridge(container("yes"),
                        site("quiet-lab", "slurm", "  ", 8, 4, 2),
                        List.of("pw.x", "-i", "pw.in"));
        assertFalse(refused.isSuccess());
        assertEquals("CONTAINER_BRIDGE_MPI_BLANK", refused.getCode());
        assertTrue(refused.getMessage().contains("'quiet-lab'")
                        && refused.getMessage().contains("declare mpi_launcher"),
                refused.getMessage());
    }

    @Test
    void tokenGrammarRefusalsPassThroughFromTheOwner() {
        OperationResult<ContainerLaunchBridge.LaunchBridge> refused =
                ContainerLaunchBridge.bridge(container("yes"),
                        site("fugaku-dev", "slurm", "srun", 8, 4, 2),
                        List.of("pw.x", "-i", "a;b"));
        assertFalse(refused.isSuccess());
        assertEquals("CONTAINER_EXEC", refused.getCode(),
                "grammar ownership stays with batch 132 - the bridge never re-judges");
    }

    @Test
    void nullArgumentsFailLoudly() {
        NullPointerException noContainer = assertThrows(NullPointerException.class,
                () -> ContainerLaunchBridge.bridge(null,
                        site("x", "slurm", "srun", 1, 1, 1), List.of("pw.x")));
        assertTrue(noContainer.getMessage().contains("never strings"),
                noContainer.getMessage());
        NullPointerException noSite = assertThrows(NullPointerException.class,
                () -> ContainerLaunchBridge.bridge(container("yes"), null,
                        List.of("pw.x")));
        assertTrue(noSite.getMessage().contains("never a transcription"),
                noSite.getMessage());
    }

    @Test
    void anUnknownSchedulerIsTheTypedRefusalNeverAnUncheckedThrow() {
        OperationResult<ContainerLaunchBridge.LaunchBridge> refused =
                ContainerLaunchBridge.bridge(container("yes"),
                        site("odd-lab", "lsf", "srun", 8, 4, 2),
                        List.of("pw.x", "-i", "pw.in"));
        assertFalse(refused.isSuccess());
        assertEquals("CONTAINER_BRIDGE_SCHEDULER", refused.getCode());
        assertTrue(refused.getMessage().contains("'odd-lab'"), refused.getMessage());
    }
}
