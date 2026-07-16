/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Cluster site profile (modules, partition defaults, scratch, launcher).
 *
 * <p>YAML-like simple key: value parser (no external YAML dependency). Nested
 * lists use repeated {@code module:} / {@code env:} lines.</p>
 */
public final class SiteProfile {

    private final String id;
    private final String scheduler;
    private final String stagingRoot;
    private final String scratchRoot;
    private final String defaultPartition;
    private final String defaultAccount;
    private final String mpiLauncher;
    private final List<String> modules;
    private final Map<String, String> environment;
    private final SchedulerResources defaultResources;

    private SiteProfile(Builder builder) {
        this.id = Objects.requireNonNull(builder.id).trim();
        this.scheduler = builder.scheduler == null ? "slurm" : builder.scheduler.trim().toLowerCase(Locale.ROOT);
        this.stagingRoot = builder.stagingRoot == null ? "" : builder.stagingRoot.trim();
        this.scratchRoot = builder.scratchRoot == null ? "" : builder.scratchRoot.trim();
        this.defaultPartition = builder.defaultPartition == null ? "" : builder.defaultPartition.trim();
        this.defaultAccount = builder.defaultAccount == null ? "" : builder.defaultAccount.trim();
        this.mpiLauncher = builder.mpiLauncher == null ? "srun" : builder.mpiLauncher.trim();
        this.modules = List.copyOf(builder.modules);
        this.environment = Map.copyOf(builder.environment);
        this.defaultResources = builder.defaultResources == null
                ? SchedulerResources.builder().partition(this.defaultPartition)
                .account(this.defaultAccount).build()
                : builder.defaultResources;
    }

    public String getId() { return this.id; }
    public String getScheduler() { return this.scheduler; }
    public String getStagingRoot() { return this.stagingRoot; }
    public String getScratchRoot() { return this.scratchRoot; }
    public String getDefaultPartition() { return this.defaultPartition; }
    public String getDefaultAccount() { return this.defaultAccount; }
    public String getMpiLauncher() { return this.mpiLauncher; }
    public List<String> getModules() { return this.modules; }
    public Map<String, String> getEnvironment() { return this.environment; }
    public SchedulerResources getDefaultResources() { return this.defaultResources; }

    public SchedulerAdapter schedulerAdapter() {
        if ("slurm".equals(this.scheduler)) {
            return new SlurmSchedulerAdapter();
        }
        throw new UnsupportedOperationException("Scheduler not implemented: " + this.scheduler);
    }

    public static SiteProfile load(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> modules = new ArrayList<>();
        Map<String, String> env = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(colon + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if ("module".equals(key)) {
                if (!value.isEmpty()) {
                    modules.add(value);
                }
            } else if ("env".equals(key) && value.contains("=")) {
                int eq = value.indexOf('=');
                env.put(value.substring(0, eq).trim(), value.substring(eq + 1).trim());
            } else {
                values.put(key, value);
            }
        }
        String id = values.getOrDefault("id", file.getFileName().toString().replaceAll("\\.ya?ml$", ""));
        SchedulerResources.Builder res = SchedulerResources.builder()
                .partition(values.getOrDefault("partition", values.getOrDefault("default_partition", "")))
                .account(values.getOrDefault("account", values.getOrDefault("default_account", "")))
                .walltime(values.getOrDefault("walltime", "01:00:00"));
        if (values.containsKey("nodes")) {
            res.nodes(Integer.parseInt(values.get("nodes")));
        }
        if (values.containsKey("ntasks")) {
            res.ntasks(Integer.parseInt(values.get("ntasks")));
        }
        if (values.containsKey("cpus_per_task")) {
            res.cpusPerTask(Integer.parseInt(values.get("cpus_per_task")));
        }
        return builder()
                .id(id)
                .scheduler(values.getOrDefault("scheduler", "slurm"))
                .stagingRoot(values.getOrDefault("staging_root", values.getOrDefault("stagingroot", "")))
                .scratchRoot(values.getOrDefault("scratch_root", values.getOrDefault("scratchroot", "")))
                .defaultPartition(values.getOrDefault("partition", values.getOrDefault("default_partition", "")))
                .defaultAccount(values.getOrDefault("account", values.getOrDefault("default_account", "")))
                .mpiLauncher(values.getOrDefault("mpi_launcher", values.getOrDefault("launcher", "srun")))
                .modules(modules)
                .environment(env)
                .defaultResources(res.build())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String scheduler = "slurm";
        private String stagingRoot;
        private String scratchRoot;
        private String defaultPartition;
        private String defaultAccount;
        private String mpiLauncher = "srun";
        private List<String> modules = new ArrayList<>();
        private Map<String, String> environment = new LinkedHashMap<>();
        private SchedulerResources defaultResources;

        public Builder id(String id) { this.id = id; return this; }
        public Builder scheduler(String scheduler) { this.scheduler = scheduler; return this; }
        public Builder stagingRoot(String stagingRoot) { this.stagingRoot = stagingRoot; return this; }
        public Builder scratchRoot(String scratchRoot) { this.scratchRoot = scratchRoot; return this; }
        public Builder defaultPartition(String defaultPartition) {
            this.defaultPartition = defaultPartition; return this;
        }
        public Builder defaultAccount(String defaultAccount) {
            this.defaultAccount = defaultAccount; return this;
        }
        public Builder mpiLauncher(String mpiLauncher) { this.mpiLauncher = mpiLauncher; return this; }
        public Builder modules(List<String> modules) {
            this.modules = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
            return this;
        }
        public Builder environment(Map<String, String> environment) {
            this.environment = environment == null ? new LinkedHashMap<>() : new LinkedHashMap<>(environment);
            return this;
        }
        public Builder defaultResources(SchedulerResources defaultResources) {
            this.defaultResources = defaultResources; return this;
        }
        public SiteProfile build() { return new SiteProfile(this); }
    }
}
