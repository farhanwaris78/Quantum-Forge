/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.run.RunningType;

/**
 * Remote-job request model.
 *
 * <p>Submission remains intentionally unavailable. The previous private
 * implementation generated local files and assembled unquoted shell strings,
 * but never opened SSH/SFTP; it has been removed so a future adapter starts
 * from a strict-host-key, typed-argument design.</p>
 */
public final class SSHJob {
    private final Project project;
    private final SSHServer sshServer;
    private RunningType type;
    private int numProcesses;
    private int numThreads;

    public SSHJob(Project project, SSHServer sshServer) {
        if (project == null) throw new IllegalArgumentException("project is null.");
        if (sshServer == null) throw new IllegalArgumentException("sshServer is null.");
        this.project = project;
        this.sshServer = sshServer;
        this.type = RunningType.SCF;
        this.numProcesses = 1;
        this.numThreads = 1;
    }

    public Project getProject() { return this.project; }
    public SSHServer getSSHServer() { return this.sshServer; }
    public RunningType getType() { return this.type; }

    public void setType(RunningType type) {
        if (type != null) this.type = type;
    }

    public int getNumProcesses() { return this.numProcesses; }
    public void setNumProcesses(int numProcesses) {
        if (numProcesses > 0) this.numProcesses = numProcesses;
    }

    public int getNumThreads() { return this.numThreads; }
    public void setNumThreads(int numThreads) {
        if (numThreads > 0) this.numThreads = numThreads;
    }

    public OperationResult<String> postJobToServerResult() {
        return OperationResult.unsupported("SSH_SUBMISSION_UNAVAILABLE",
                "No secure SSH/SFTP transport is implemented; no job or file was prepared or sent.");
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean postJobToServer() {
        return this.postJobToServerResult().isSuccess();
    }
}
