/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.remote;

import java.util.List;

import quantumforge.hpc.SiteProfile;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #103 (launch-bridge slice, batch 147): the typed composition of a
 * validated {@link ContainerProfileSpec.ContainerProfile} with a loaded
 * {@link SiteProfile} - the batch-132 exec preview's
 * {@code <mpirun/srun + counts from site profile>} anchor finally gets its
 * values from the OWNER of those values, never transcribed by hand.
 *
 * <p>What is RESOLVED here (stated, one-to-one, no invention):</p>
 * <ul>
 *   <li>the MPI runner NAME - {@code SiteProfile.getMpiLauncher()} verbatim
 *       (a blank launcher refuses as CONTAINER_BRIDGE_MPI_BLANK: a container
 *       with no declared runner has nothing to even place);</li>
 *   <li>the COUNT VALUES - {@code defaultResources}' ntasks / cpus-per-task /
 *       nodes, rendered as values;</li>
 *   <li>the site's id and its batch-134 canonical scheduler name, so a
 *       review line names WHERE these numbers live.</li>
 * </ul>
 *
 * <p>What stays REQUIRED-EDIT BY DESIGN: the argument SPELLING of the runner
 * ({@code -n} vs {@code -np} vs launcher-specific forms are the launcher's
 * man page, and a bridge that guessed flags would mislead the very review
 * the draft family exists for), and the image pull-source prefix (batch-132
 * rule, unchanged). The exec SHAPE itself is NOT re-rendered here: the
 * block embeds {@code execPreview}'s output verbatim - the single owner of
 * the shape, so bridge and preview can never diverge. Nothing pulls, execs,
 * binds or launches: {@code launched = NO} is pinned in every render.</p>
 */
public final class ContainerLaunchBridge {

    private ContainerLaunchBridge() {
        // Utility
    }

    /** One resolved bridge view over a container profile and a site profile. */
    public static final class LaunchBridge {
        private final String siteId;
        private final String scheduler;
        private final String mpiLauncher;
        private final int ntasks;
        private final int cpusPerTask;
        private final int nodes;
        private final boolean hostMpiShape;
        private final String execPreview;

        LaunchBridge(String siteId, String scheduler, String mpiLauncher, int ntasks,
                int cpusPerTask, int nodes, boolean hostMpiShape, String execPreview) {
            this.siteId = siteId;
            this.scheduler = scheduler;
            this.mpiLauncher = mpiLauncher;
            this.ntasks = ntasks;
            this.cpusPerTask = cpusPerTask;
            this.nodes = nodes;
            this.hostMpiShape = hostMpiShape;
            this.execPreview = execPreview;
        }

        public String getSiteId() { return this.siteId; }
        public String getScheduler() { return this.scheduler; }
        public String getMpiLauncher() { return this.mpiLauncher; }
        public int getNtasks() { return this.ntasks; }
        public int getCpusPerTask() { return this.cpusPerTask; }
        public int getNodes() { return this.nodes; }
        public boolean isHostMpiShape() { return this.hostMpiShape; }
        public String getExecPreview() { return this.execPreview; }

        /**
         * The review block: the RESOLUTION header first (what the anchor now
         * means with this site), then the batch-132 preview verbatim.
         */
        public String renderBlock() {
            StringBuilder block = new StringBuilder();
            block.append("# container+site launch bridge (REVIEW only - launched = NO)\n");
            block.append("# site: ").append(this.siteId)
                    .append(" (scheduler: ").append(this.scheduler)
                    .append(", batch-134 canonical)\n");
            block.append("# mpi launcher: ").append(this.mpiLauncher)
                    .append(" (the site profile's own declaration, verbatim)\n");
            block.append("# counts: ntasks=").append(this.ntasks)
                    .append(", cpus-per-task=").append(this.cpusPerTask)
                    .append(", nodes=").append(this.nodes)
                    .append(" (site defaultResources - stated, never hidden)\n");
            block.append("# SHAPE (DECLARED by you): ")
                    .append(this.hostMpiShape ? "host-MPI compatible -> the runner "
                            + "stays OUTSIDE the container"
                            : "container-internal MPI -> the runner lives INSIDE")
                    .append('\n');
            block.append("# the <mpirun/srun + counts> anchor below thus reads: '")
                    .append(this.mpiLauncher).append("' with ntasks=").append(this.ntasks)
                    .append(", cpus-per-task=").append(this.cpusPerTask).append('\n');
            block.append("# REQUIRED-EDIT: the argument SPELLING for '")
                    .append(this.mpiLauncher)
                    .append("' is its man page's (-n/-np family) - this bridge\n")
                    .append("#   resolves the NAME and the COUNTS only, never flags; "
                            + "the pull-source prefix stays a REQUIRED-EDIT too.\n");
            block.append("# ---- exec-shape preview (batch-132 single owner, verbatim)"
                    + " ----\n");
            block.append(this.execPreview);
            return block.toString();
        }
    }

    /**
     * Compose the bridge. Codes: CONTAINER_BRIDGE_OK on success;
     * CONTAINER_BRIDGE_MPI_BLANK when the site declares no launcher. The
     * exec-preview token grammar refusals pass through unchanged
     * (CONTAINER_EXEC) - grammar ownership stays with batch 132.
     */
    public static OperationResult<LaunchBridge> bridge(
            ContainerProfileSpec.ContainerProfile container, SiteProfile site,
            List<String> commandTokens) {
        if (container == null) {
            throw new NullPointerException("container profile is required - the bridge "
                    + "composes real objects, never strings");
        }
        if (site == null) {
            throw new NullPointerException("site profile is required - the counts come "
                    + "from its owner, never a transcription");
        }
        OperationResult<String> preview = container.execPreview(commandTokens);
        if (!preview.isSuccess() || preview.getValue().isEmpty()) {
            return OperationResult.failed(preview.getCode(), preview.getMessage(), null);
        }
        String launcher = site.getMpiLauncher() == null ? "" : site.getMpiLauncher().trim();
        if (launcher.isEmpty()) {
            return OperationResult.failed("CONTAINER_BRIDGE_MPI_BLANK",
                    "site profile '" + site.getId() + "' declares no MPI launcher - "
                            + "declare mpi_launcher in the site profile; a container "
                            + "with no runner to place is not a launch shape.",
                    null);
        }
        // Adapter resolution is the batch-134 typed proof that the site's
        // scheduler is real; its typed IllegalArgumentException is converted
        // to a typed refusal so the bridge NEVER fails unchecked.
        final String scheduler;
        try {
            scheduler = site.schedulerAdapter().name();
        } catch (IllegalArgumentException unknown) {
            return OperationResult.failed("CONTAINER_BRIDGE_SCHEDULER",
                    "site profile '" + site.getId() + "' declares a scheduler the "
                            + "registry refuses: " + unknown.getMessage(),
                    unknown);
        }
        LaunchBridge bridge = new LaunchBridge(site.getId(), scheduler, launcher,
                site.getDefaultResources().getNtasks(),
                site.getDefaultResources().getCpusPerTask(),
                site.getDefaultResources().getNodes(),
                container.isHostMpiCompatible(), preview.getValue().get());
        return OperationResult.success("CONTAINER_BRIDGE_OK",
                "Launch bridge resolved against site '" + site.getId() + "' (launcher "
                        + launcher + "; " + bridge.ntasks + " task(s) x "
                        + bridge.cpusPerTask + " cpu(s)/task on " + bridge.nodes
                        + " node(s)); flag spelling and pull source remain "
                        + "REQUIRED-EDIT by design.",
                bridge);
    }
}
