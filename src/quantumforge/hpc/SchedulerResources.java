/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.util.Objects;

/**
 * Typed compute resources for scheduler script generation.
 */
public final class SchedulerResources {

    private final int nodes;
    private final int ntasks;
    private final int cpusPerTask;
    private final String walltime;
    private final String partition;
    private final String account;
    private final String qos;
    private final String memory;

    private SchedulerResources(Builder builder) {
        this.nodes = Math.max(1, builder.nodes);
        this.ntasks = Math.max(1, builder.ntasks);
        this.cpusPerTask = Math.max(1, builder.cpusPerTask);
        this.walltime = builder.walltime == null || builder.walltime.isBlank()
                ? "01:00:00" : builder.walltime.trim();
        this.partition = builder.partition == null ? "" : builder.partition.trim();
        this.account = builder.account == null ? "" : builder.account.trim();
        this.qos = builder.qos == null ? "" : builder.qos.trim();
        this.memory = builder.memory == null ? "" : builder.memory.trim();
    }

    public int getNodes() { return this.nodes; }
    public int getNtasks() { return this.ntasks; }
    public int getCpusPerTask() { return this.cpusPerTask; }
    public String getWalltime() { return this.walltime; }
    public String getPartition() { return this.partition; }
    public String getAccount() { return this.account; }
    public String getQos() { return this.qos; }
    public String getMemory() { return this.memory; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int nodes = 1;
        private int ntasks = 1;
        private int cpusPerTask = 1;
        private String walltime = "01:00:00";
        private String partition;
        private String account;
        private String qos;
        private String memory;

        public Builder nodes(int nodes) { this.nodes = nodes; return this; }
        public Builder ntasks(int ntasks) { this.ntasks = ntasks; return this; }
        public Builder cpusPerTask(int cpusPerTask) { this.cpusPerTask = cpusPerTask; return this; }
        public Builder walltime(String walltime) { this.walltime = walltime; return this; }
        public Builder partition(String partition) { this.partition = partition; return this; }
        public Builder account(String account) { this.account = account; return this; }
        public Builder qos(String qos) { this.qos = qos; return this; }
        public Builder memory(String memory) { this.memory = memory; return this; }
        public SchedulerResources build() { return new SchedulerResources(this); }
    }

    @Override
    public String toString() {
        return "nodes=" + this.nodes + " ntasks=" + this.ntasks
                + " cpusPerTask=" + this.cpusPerTask + " walltime=" + this.walltime;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SchedulerResources)) {
            return false;
        }
        SchedulerResources o = (SchedulerResources) obj;
        return this.nodes == o.nodes && this.ntasks == o.ntasks
                && this.cpusPerTask == o.cpusPerTask
                && Objects.equals(this.walltime, o.walltime)
                && Objects.equals(this.partition, o.partition)
                && Objects.equals(this.account, o.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.nodes, this.ntasks, this.cpusPerTask, this.walltime,
                this.partition, this.account);
    }
}
