/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.run.local;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;
import quantumforge.project.Project;

/**
 * Local Job Manager for persistent job execution.
 * 
 * The Local Job Manager allows jobs to run independently
 * of the GUI (surviving application restarts) using:
 * - Raw: direct execution with custom scripts
 * - PBS: local PBS/Torque execution
 * - SLURM: local SLURM execution
 * - PJM: local PJM (Fugaku) execution
 * 
 * On Linux, this enables jobs to continue even after
 * QuantumForge is closed.
 */
public class LocalJobManager {

    public static final String MANAGER_BUILTIN = "built-in";
    public static final String MANAGER_RAW = "raw";
    public static final String MANAGER_PBS = "pbs";
    public static final String MANAGER_SLURM = "slurm";
    public static final String MANAGER_PJM = "pjm";

    private static LocalJobManager instance = null;

    public static LocalJobManager getInstance() {
        if (instance == null) {
            instance = new LocalJobManager();
        }
        return instance;
    }

    public static class JobQueue {
        public final String name;
        public final String schedulerType;
        public final String script;
        public int numProcesses;
        public int numThreads;
        public List<String> environmentVars;

        public JobQueue(String name, String scheduler) {
            this.name = name;
            this.schedulerType = scheduler;
            this.script = "#!/bin/bash\n";
            this.numProcesses = 1;
            this.numThreads = 1;
            this.environmentVars = new ArrayList<>();
        }

        public String getSubmitCommand(String scriptFile) {
            switch (this.schedulerType) {
                case MANAGER_PBS:    return "qsub " + scriptFile;
                case MANAGER_SLURM:  return "sbatch " + scriptFile;
                case MANAGER_PJM:    return "pjsub " + scriptFile;
                default:             return "bash " + scriptFile;
            }
        }
    }

    private List<JobQueue> queues;
    private String activeManager;
    private boolean running;

    private LocalJobManager() {
        this.queues = new ArrayList<>();
        this.activeManager = MANAGER_BUILTIN;
        this.running = false;
    }

    public void setActiveManager(String manager) {
        if (manager != null) this.activeManager = manager;
    }

    public String getActiveManager() { return this.activeManager; }

    public void addQueue(JobQueue queue) {
        if (queue != null) this.queues.add(queue);
    }

    public void removeQueue(String name) {
        this.queues.removeIf(q -> q.name.equals(name));
    }

    public JobQueue getQueue(String name) {
        for (JobQueue q : this.queues) {
            if (q.name.equals(name)) return q;
        }
        return null;
    }

    public List<JobQueue> getQueues() { return new ArrayList<>(this.queues); }

    /**
     * Submit a project calculation to the specified queue
     */
    public OperationResult<String> submitToQueueResult(Project project, String queueName) {
        if (project == null) {
            return OperationResult.failed("JOB_PROJECT_MISSING", "No project was supplied.", null);
        }
        JobQueue queue = this.getQueue(queueName);
        if (queue == null) {
            return OperationResult.failed("JOB_QUEUE_UNKNOWN", "Unknown local queue: " + queueName, null);
        }
        // A future implementation must write a quoted script, submit it, parse
        // and persist a scheduler job ID before returning SUCCESS.
        return OperationResult.unsupported("LOCAL_SCHEDULER_UNAVAILABLE",
                "Local PBS/SLURM/PJM submission is not implemented; no job was submitted.");
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean submitToQueue(Project project, String queueName) {
        return this.submitToQueueResult(project, queueName).isSuccess();
    }

    public boolean isRunning() { return this.running; }
}
