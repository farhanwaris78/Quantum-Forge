/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One scheduler job with transition history.
 */
public final class JobRecord {

    public static final class Transition {
        private final JobState state;
        private final Instant at;
        private final String note;

        public Transition(JobState state, Instant at, String note) {
            this.state = Objects.requireNonNull(state);
            this.at = Objects.requireNonNull(at);
            this.note = note == null ? "" : note;
        }

        public JobState getState() { return this.state; }
        public Instant getAt() { return this.at; }
        public String getNote() { return this.note; }
    }

    private final String jobId;
    private final String scheduler;
    private final String siteId;
    private final String projectPath;
    private String schedulerJobId;
    private JobState state;
    private final List<Transition> history = new ArrayList<>();

    public JobRecord(String jobId, String scheduler, String siteId, String projectPath) {
        this.jobId = Objects.requireNonNull(jobId);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.siteId = siteId == null ? "" : siteId;
        this.projectPath = projectPath == null ? "" : projectPath;
        this.state = JobState.STAGED;
        this.history.add(new Transition(JobState.STAGED, Instant.now(), "created"));
    }

    public String getJobId() { return this.jobId; }
    public String getScheduler() { return this.scheduler; }
    public String getSiteId() { return this.siteId; }
    public String getProjectPath() { return this.projectPath; }
    public String getSchedulerJobId() { return this.schedulerJobId; }
    public JobState getState() { return this.state; }
    public List<Transition> getHistory() { return Collections.unmodifiableList(this.history); }

    public void setSchedulerJobId(String schedulerJobId) {
        this.schedulerJobId = schedulerJobId;
    }

    public void transition(JobState next, String note) {
        Objects.requireNonNull(next, "next");
        this.state = next;
        this.history.add(new Transition(next, Instant.now(), note));
    }
}
