/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.hpc.SchedulerResources;
import quantumforge.hpc.SiteProfile;
import quantumforge.hpc.ResultSyncManifest;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.run.QECommandDag;
import quantumforge.run.QECommandStage;
import quantumforge.run.RunningType;

/**
 * Remote-job request model with staged script generation.
 *
 * <p>Actual network submission requires a connected {@link SshTransport}. Without
 * transport, operations remain fail-closed and never report false success.</p>
 */
public final class SSHJob {
    private final Project project;
    private final SSHServer sshServer;
    private RunningType type;
    private int numProcesses;
    private int numThreads;
    private SiteProfile siteProfile;
    private SshTransport transport;
    private String stagingRoot;

    public SSHJob(Project project, SSHServer sshServer) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        if (sshServer == null) {
            throw new IllegalArgumentException("sshServer is null.");
        }
        this.project = project;
        this.sshServer = sshServer;
        this.type = RunningType.SCF;
        this.numProcesses = 1;
        this.numThreads = 1;
        this.stagingRoot = "/tmp/quantumforge";
    }

    public Project getProject() {
        return this.project;
    }

    public SSHServer getSSHServer() {
        return this.sshServer;
    }

    public RunningType getType() {
        return this.type;
    }

    public void setType(RunningType type) {
        if (type != null) {
            this.type = type;
        }
    }

    public int getNumProcesses() {
        return this.numProcesses;
    }

    public void setNumProcesses(int numProcesses) {
        if (numProcesses > 0) {
            this.numProcesses = numProcesses;
        }
    }

    public int getNumThreads() {
        return this.numThreads;
    }

    public void setNumThreads(int numThreads) {
        if (numThreads > 0) {
            this.numThreads = numThreads;
        }
    }

    public void setSiteProfile(SiteProfile siteProfile) {
        this.siteProfile = siteProfile;
        if (siteProfile != null && siteProfile.getStagingRoot() != null
                && !siteProfile.getStagingRoot().isBlank()) {
            this.stagingRoot = siteProfile.getStagingRoot();
        }
    }

    public void setTransport(SshTransport transport) {
        this.transport = transport;
    }

    public void setStagingRoot(String stagingRoot) {
        this.stagingRoot = RemotePathGuard.normalizeStagingRoot(stagingRoot);
    }

    public OperationResult<String> prepareScriptResult() {
        try {
            SchedulerAdapter adapter = resolveAdapter();
            SchedulerResources resources = resolveResources();
            QECommandDag dag = this.type.getCommandDag(this.project, this.numProcesses);
            List<String[]> commands = new ArrayList<>();
            for (QECommandStage stage : dag.getStages()) {
                commands.add(stage.getCommand());
            }
            String jobName = this.project.getDirectoryName() == null
                    ? "quantumforge" : this.project.getDirectoryName();
            String script = adapter.generateScript(jobName, resources, commands);
            return OperationResult.success("SSH_SCRIPT_PREPARED",
                    "Prepared " + adapter.name() + " script for " + dag.size() + " stage(s).", script);
        } catch (RuntimeException ex) {
            return OperationResult.failed("SSH_SCRIPT_FAILED",
                    "Could not prepare remote script: " + ex.getMessage(), ex);
        }
    }

    public OperationResult<JobRecord> postJobToServerResult() {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_SUBMISSION_UNAVAILABLE",
                    "No connected SSH transport; no job or file was prepared or sent. "
                            + "Configure SshTransport with strict host-key verification first.");
        }
        try {
            OperationResult<String> prepared = prepareScriptResult();
            if (!prepared.isSuccess()) {
                return OperationResult.failed(prepared.getCode(), prepared.getMessage(), null);
            }
            String jobId = UUID.randomUUID().toString().substring(0, 8);
            String remoteDir = RemotePathGuard.uniqueJobDirectory(this.stagingRoot, jobId);
            String remoteScript = remoteDir + "/job.sh";
            OperationResult<Void> mkdir = this.transport.mkdirRemote(remoteDir);
            if (!mkdir.isSuccess()) {
                return OperationResult.failed(mkdir.getCode(), mkdir.getMessage(), null);
            }
            Path localScript = Path.of(System.getProperty("java.io.tmpdir"),
                    "qf-job-" + jobId + ".sh");
            AtomicFileWriter.writeUtf8(localScript, prepared.getValue().orElse(""));
            OperationResult<Void> upload = this.transport.uploadFile(localScript, remoteScript);
            if (!upload.isSuccess()) {
                return OperationResult.failed(upload.getCode(), upload.getMessage(), null);
            }
            SchedulerAdapter adapter = resolveAdapter();
            Path stdout = localScript.resolveSibling(localScript.getFileName() + ".out");
            Path stderr = localScript.resolveSibling(localScript.getFileName() + ".err");
            OperationResult<Integer> exec = this.transport.exec(
                    adapter.submitCommand(remoteScript), stdout, stderr);
            JobRecord record = new JobRecord(jobId, adapter.name(),
                    this.siteProfile == null ? "" : this.siteProfile.getId(),
                    this.project.getDirectoryPath());
            record.transition(JobState.SUBMITTED, "submit invoked");
            if (!exec.isSuccess()) {
                record.transition(JobState.FAILED, exec.getMessage());
                return OperationResult.failed(exec.getCode(), exec.getMessage(), null);
            }
            String output = "";
            if (Files.isRegularFile(stdout)) {
                output = Files.readString(stdout);
            }
            adapter.parseJobId(output).ifPresent(record::setSchedulerJobId);
            if (record.getSchedulerJobId() == null || record.getSchedulerJobId().isBlank()) {
                record.transition(JobState.UNKNOWN, "submit succeeded but job id was not parsed");
            } else {
                record.transition(JobState.PENDING, "scheduler job " + record.getSchedulerJobId());
            }
            return OperationResult.success("SSH_SUBMITTED",
                    "Submitted remote job " + record.getSchedulerJobId(), record);
        } catch (Exception ex) {
            return OperationResult.failed("SSH_SUBMIT_ERROR",
                    "Remote submission failed: " + ex.getMessage(), ex);
        }
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean postJobToServer() {
        return this.postJobToServerResult().isSuccess();
    }

    public OperationResult<JobRecord> cancelJobResult(JobRecord record) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_NOT_CONNECTED",
                    "No connected transport; job was not cancelled.");
        }
        return JobCancellation.cancel(this.transport, resolveAdapter(), record);
    }

    public OperationResult<SelectiveResultSync.SyncReport> syncResultsResult(
            String remoteJobRelativeDir, Path localDir, boolean includeLarge) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_NOT_CONNECTED",
                    "No connected transport; nothing was downloaded.");
        }
        ResultSyncManifest manifest = ResultSyncManifest.forWorkflow(
                this.type, this.project.getPrefixName());
        SelectiveResultSync sync = new SelectiveResultSync(this.transport, this.stagingRoot);
        return sync.sync(remoteJobRelativeDir, localDir, manifest, includeLarge);
    }

    /**
     * Batch-146 (#92 hash pinning): the pinned variant - every manifest entry
     * whose relative path appears in {@code pinsByRelativePath} downloads
     * through the batch-136 two-sided verified download; entries without a
     * pin follow the batch-128 walk and the verdict message states the
     * posture either way. Pins are CALLER-attested (from a draft channel or
     * an earlier probe), never invented here.
     */
    public OperationResult<SelectiveResultSync.SyncReport> syncResultsResult(
            String remoteJobRelativeDir, Path localDir, boolean includeLarge,
            java.util.Map<String, String> pinsByRelativePath) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_NOT_CONNECTED",
                    "No connected transport; nothing was downloaded.");
        }
        ResultSyncManifest manifest = ResultSyncManifest.forWorkflow(
                this.type, this.project.getPrefixName());
        SelectiveResultSync sync = new SelectiveResultSync(this.transport, this.stagingRoot);
        return sync.sync(remoteJobRelativeDir, localDir, manifest, includeLarge,
                pinsByRelativePath);
    }

    /**
     * Adapter for GUI monitoring of a submitted job (Roadmap #96 GUI slice).
     * Delegates to the single private owner ({@link #resolveAdapter()}) so
     * the site-profile / legacy-server fallback graph is never duplicated by
     * any caller. May throw the batch-134 typed {@link IllegalArgumentException}
     * when a site profile names an unknown scheduler - callers fail closed.
     */
    public SchedulerAdapter monitorAdapter() {
        return resolveAdapter();
    }

    /**
     * Batch-144 single-owner fix: server-scheduler resolution now delegates
     * to {@link SSHServerScheduler} (the one owner, itself delegating to the
     * batch-126 registry). The private copy this replaced silently returned
     * SLURM for every non-pbs/sge value - including the {@code pjm} constant
     * and the out-of-the-box {@code none} every GUI-created server keeps, so
     * real submissions could go out with the wrong scheduler's script. An
     * unset/unknown declaration now fails typed ({@link IllegalArgumentException}
     * carrying the resolver's code and message), and every caller surfaces it
     * through its existing honest channel (script-prepare failure, submit
     * error, or the monitor-offer refusal).
     */
    private SchedulerAdapter resolveAdapter() {
        if (this.siteProfile != null) {
            return this.siteProfile.schedulerAdapter();
        }
        OperationResult<SchedulerAdapter> resolved =
                SSHServerScheduler.resolveAdapter(this.sshServer);
        if (resolved.isSuccess() && resolved.getValue().isPresent()) {
            return resolved.getValue().get();
        }
        throw new IllegalArgumentException("[" + resolved.getCode() + "] "
                + resolved.getMessage());
    }

    private SchedulerResources resolveResources() {
        if (this.siteProfile != null) {
            SchedulerResources base = this.siteProfile.getDefaultResources();
            return SchedulerResources.builder()
                    .nodes(base.getNodes())
                    .ntasks(Math.max(base.getNtasks(), this.numProcesses))
                    .cpusPerTask(Math.max(base.getCpusPerTask(), this.numThreads))
                    .walltime(base.getWalltime())
                    .partition(base.getPartition())
                    .account(base.getAccount())
                    .qos(base.getQos())
                    .memory(base.getMemory())
                    .build();
        }
        return SchedulerResources.builder()
                .ntasks(this.numProcesses)
                .cpusPerTask(this.numThreads)
                .partition(this.sshServer.getQueueName())
                .walltime(this.sshServer.getWalltime())
                .account(this.sshServer.getGroupList())
                .build();
    }
}
