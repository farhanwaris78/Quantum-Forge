/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #94 (draft slice): a typed, fail-closed SITE PROFILE draft - the
 * per-cluster values (scheduler family, launcher, modules, partitions,
 * account, scratch root, node ceiling) that let ONE portable project target
 * multiple clusters without editing its inputs.
 *
 * <p>Owned grammars (fail-closed, never coerced):</p>
 * <ul>
 *   <li>cluster name: required, {@code [A-Za-z][A-Za-z0-9._-]{0,63}}
 *       (SITE_NAME);</li>
 *   <li>scheduler and launcher are TYPED enums (slurm/pbs/pjm/sge and
 *       srun/mpirun/mpiexec) normalized to lowercase - a free-form scheduler
 *       string is refused rather than echoed (SITE_SCHEDULER / SITE_LAUNCHER);
 *       pairings are NOT judged here (srun-on-PBS is a site reality for some
 *       clusters and flagging it would invent policy);</li>
 *   <li>default partition (optional, blank = honest comment) and account
 *       (optional) use the owned token grammar
 *       {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}} (SITE_PARTITION /
 *       SITE_ACCOUNT);</li>
 *   <li>scratch root: REQUIRED absolute POSIX directory - trailing '/'
 *       normalized away and stated, no '..', no '//', no whitespace/quotes/
 *       expansion/separator characters (SITE_SCRATCH);</li>
 *   <li>max nodes: 1..100000 (SITE_MAXNODES); the draft records the ceiling
 *       only - enforcing it lives with the submit path;</li>
 *   <li>modules: optional csv, each token as in the SLURM draft grammar
 *       {@code [A-Za-z0-9][A-Za-z0-9._/+:-]{0,127}}; the rendered value joins
 *       tokens with plain commas - a whitespace-free value by construction
 *       (SITE_MODULE).</li>
 * </ul>
 *
 * <p>Format honesty: this draft renders an OWNED {@code qf-site-profile v1}
 * key=value block, explicitly labeled NOT-YAML - site-admin YAML with schema
 * validation and policy limits lands with the #94 loader/runtime. Nothing
 * about the profile is consulted by any submit path from this build.</p>
 */
public final class SiteProfileSpec {

    /** Node ceiling bound stated in the grammar. */
    public static final int MAX_NODES_BOUND = 100000;

    private SiteProfileSpec() {
    }

    /** One validated profile draft. */
    public static final class SiteProfile {
        private final String cluster;
        private final String scheduler;
        private final String launcher;
        private final String defaultPartition;   // "" = honestly omitted
        private final String account;            // "" = honestly omitted
        private final String scratchDir;         // normalized, no trailing '/'
        private final boolean scratchTrimmed;    // true when input had a trailing '/'
        private final int maxNodes;
        private final List<String> modules;      // possibly empty

        SiteProfile(String cluster, String scheduler, String launcher,
                String defaultPartition, String account, String scratchDir,
                boolean scratchTrimmed, int maxNodes, List<String> modules) {
            this.cluster = cluster;
            this.scheduler = scheduler;
            this.launcher = launcher;
            this.defaultPartition = defaultPartition;
            this.account = account;
            this.scratchDir = scratchDir;
            this.scratchTrimmed = scratchTrimmed;
            this.maxNodes = maxNodes;
            this.modules = modules;
        }

        public String getCluster() { return this.cluster; }
        public String getScheduler() { return this.scheduler; }
        public String getLauncher() { return this.launcher; }
        public String getDefaultPartition() { return this.defaultPartition; }
        public String getAccount() { return this.account; }
        public String getScratchDir() { return this.scratchDir; }
        public boolean isScratchTrimmed() { return this.scratchTrimmed; }
        public int getMaxNodes() { return this.maxNodes; }
        public List<String> getModules() { return this.modules; }

        /** The owned key=value block - labeled NOT-YAML on its own header. */
        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# qf-site-profile v1 (QuantumForge, Roadmap #94 draft slice)\n");
            text.append("# Owned key=value grammar - REVIEW before adoption. NOT YAML: the\n");
            text.append("# site-admin YAML schema + policy loader lands with the #94 runtime.\n");
            text.append("cluster = ").append(this.cluster).append('\n');
            text.append("scheduler = ").append(this.scheduler).append('\n');
            text.append("launcher = ").append(this.launcher).append('\n');
            if (this.defaultPartition.isEmpty()) {
                text.append("# default_partition = (unset - honestly omitted; the "
                        + "scheduler default applies)\n");
            } else {
                text.append("default_partition = ").append(this.defaultPartition).append('\n');
            }
            if (this.account.isEmpty()) {
                text.append("# account = (unset - honestly omitted)\n");
            } else {
                text.append("account = ").append(this.account).append('\n');
            }
            text.append("scratch_dir = ").append(this.scratchDir);
            if (this.scratchTrimmed) {
                text.append("   # trailing '/' normalized away at validation");
            }
            text.append('\n');
            text.append("max_nodes = ").append(this.maxNodes).append('\n');
            if (this.modules.isEmpty()) {
                text.append("# modules = (none declared - the site environment is NOT "
                        + "assumed)\n");
            } else {
                text.append("modules = ").append(String.join(",", this.modules))
                        .append('\n');
            }
            text.append("# usage: NONE from this build - the submit path consumes profiles\n");
            text.append("# only when the #94 loader lands; until then this is a reviewed draft.\n");
            return text.toString();
        }
    }

    /** Validates one profile. Codes: SITE_* (see class javadoc). */
    public static OperationResult<SiteProfile> validate(String cluster, String scheduler,
            String launcher, String defaultPartition, String account, String scratchDir,
            int maxNodes, String modulesCsv) {
        String name = cluster == null ? "" : cluster.trim();
        if (!name.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            return OperationResult.failed("SITE_NAME",
                    "cluster must start with a letter and use only [A-Za-z0-9._-], "
                            + "up to 64 chars (got '" + name + "').",
                    null);
        }
        String sched = scheduler == null ? "" : scheduler.trim().toLowerCase(
                java.util.Locale.ROOT);
        if (!(sched.equals("slurm") || sched.equals("pbs") || sched.equals("pjm")
                || sched.equals("sge"))) {
            return OperationResult.failed("SITE_SCHEDULER",
                    "scheduler is TYPED: one of slurm/pbs/pjm/sge (got '" + sched
                            + "') - a free-form scheduler string is refused, not echoed.",
                    null);
        }
        String launch = launcher == null ? "" : launcher.trim().toLowerCase(
                java.util.Locale.ROOT);
        if (!(launch.equals("srun") || launch.equals("mpirun") || launch.equals("mpiexec"))) {
            return OperationResult.failed("SITE_LAUNCHER",
                    "launcher is TYPED: one of srun/mpirun/mpiexec (got '" + launch
                            + "'); pairings with the scheduler are NOT judged here.",
                    null);
        }
        String partition = defaultPartition == null ? "" : defaultPartition.trim();
        if (!partition.isEmpty() && !partition.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return OperationResult.failed("SITE_PARTITION",
                    "default partition uses the owned token grammar (got '" + partition
                            + "'); blank omits it honestly.",
                    null);
        }
        String acct = account == null ? "" : account.trim();
        if (!acct.isEmpty() && !acct.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return OperationResult.failed("SITE_ACCOUNT",
                    "account uses the owned token grammar (got '" + acct
                            + "'); blank omits it honestly.",
                    null);
        }
        String scratch = scratchDir == null ? "" : scratchDir.trim();
        boolean trimmed = false;
        if (scratch.length() > 1 && scratch.endsWith("/")) {
            scratch = scratch.substring(0, scratch.length() - 1);
            trimmed = true;
        }
        if (!scratch.startsWith("/") || scratch.contains("//") || scratch.contains("..")
                || scratch.contains(" ") || scratch.contains("\"") || scratch.contains("'")
                || scratch.contains("$") || scratch.contains("`") || scratch.contains(";")
                || scratch.contains("|") || scratch.contains("\\") || scratch.length() < 2) {
            return OperationResult.failed("SITE_SCRATCH",
                    "scratch_dir must be an absolute POSIX directory path - no '..', no "
                            + "'//', no whitespace/quotes/expansion/separator characters "
                            + "(got '" + (scratchDir == null ? "" : scratchDir.trim())
                            + "'); the scratch policy must be deliberate, never ambient.",
                    null);
        }
        if (maxNodes < 1 || maxNodes > MAX_NODES_BOUND) {
            return OperationResult.failed("SITE_MAXNODES",
                    "max_nodes must be 1.." + MAX_NODES_BOUND + " (got " + maxNodes
                            + "); the draft records the ceiling - enforcing it is the "
                            + "submit path's job.",
                    null);
        }
        List<String> modules = new ArrayList<>();
        String csv = modulesCsv == null ? "" : modulesCsv.trim();
        if (!csv.isEmpty()) {
            for (String token : csv.split(",", -1)) {
                String module = token.trim();
                if (!module.matches("[A-Za-z0-9][A-Za-z0-9._/+:-]{0,127}")) {
                    return OperationResult.failed("SITE_MODULE",
                            "module token '" + module + "' is outside the owned grammar "
                                    + "- profile values must stay whitespace-free so the "
                                    + "rendered line parses unambiguously.",
                            null);
                }
                modules.add(module);
            }
        }
        return OperationResult.success("SITE_OK", "Site profile draft validated.",
                new SiteProfile(name, sched, launch, partition, acct, scratch, trimmed,
                        maxNodes, List.copyOf(modules)));
    }
}
