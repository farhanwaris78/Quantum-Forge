/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.atoms.model.Cell;
import quantumforge.hpc.JobRecord;
import quantumforge.input.QEInput;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectProperty;
import quantumforge.run.RunningType;
import quantumforge.ssh.RemoteSubmitChain.SubmitOutcome;

/**
 * Batch-139 (#96 FX-thread depth): the connect-then-submit chain's ordering,
 * ownership and cleanup rules, pinned headlessly - a failed connect never
 * submits, a failed submit closes the transport exactly once, and success
 * hands a live, un-closed transport to the caller.
 */
class RemoteSubmitChainTest {

    @TempDir
    Path tempDir;

    private SSHJob preparedJob(Project project, SSHServer server) {
        server.setSchedulerType(SSHServer.SCHEDULER_SLURM);
        server.setQueueName("compute");
        SSHJob job = new SSHJob(project, server);
        job.setType(RunningType.SCF);
        job.setNumProcesses(4);
        job.setNumThreads(2);
        return job;
    }

    @Test
    void failedConnectNeverAttemptsSubmit() {
        Project project = stub(this.tempDir);
        SSHServer server = new SSHServer("test");
        SSHJob job = preparedJob(project, server);
        SubmitOutcome outcome = RemoteSubmitChain.connectAndSubmit(server, job,
                srv -> OperationResult.failed("SSH_AUTH_FAILED", "auth refused", null));
        assertFalse(outcome.isSubmitted());
        assertEquals("SSH_AUTH_FAILED", outcome.getConnect().getCode());
        assertNull(outcome.getSubmit(),
                "submit must be null - a truthful 'not run', never an invented failure");
        assertNull(outcome.getTransport());
        assertFalse(outcome.wasTransportClosedByChain(),
                "a connector that never yielded a transport leaves nothing to close");
    }

    @Test
    void failedSubmitClosesTheTransportExactlyOnce() {
        Project project = stub(this.tempDir);
        SSHServer server = new SSHServer("test");
        server.setSchedulerType(SSHServer.SCHEDULER_SLURM);
        ScriptedTransport scripted = new ScriptedTransport();
        scripted.failUploads = true;
        SSHJob job = preparedJob(project, server);
        SubmitOutcome outcome = RemoteSubmitChain.connectAndSubmit(server, job,
                srv -> OperationResult.success("SSH_CONNECTED", "ok", scripted));
        assertFalse(outcome.isSubmitted());
        OperationResult<JobRecord> submit = outcome.getSubmit();
        assertTrue(submit != null && !submit.isSuccess());
        assertEquals("SFTP_UPLOAD", submit.getCode(),
                "the submit's own typed failure is carried through, not rewritten");
        assertNull(outcome.getTransport());
        assertTrue(outcome.wasTransportClosedByChain(),
                "the chain closes a dead-end transport inside itself, once");
        assertEquals(1, scripted.closeCount,
                "exactly-once: the caller must also close nothing");
    }

    @Test
    void successHandsAnOpenUnclosedTransportToTheCaller() {
        Project project = stub(this.tempDir);
        SSHServer server = new SSHServer("test");
        server.setSchedulerType(SSHServer.SCHEDULER_SLURM);
        ScriptedTransport scripted = new ScriptedTransport();
        SSHJob job = preparedJob(project, server);
        SubmitOutcome outcome = RemoteSubmitChain.connectAndSubmit(server, job,
                srv -> OperationResult.success("SSH_CONNECTED", "ok", scripted));
        assertTrue(outcome.isSubmitted(), outcome.getSubmit() == null
                ? "no submit" : outcome.getSubmit().toString());
        JobRecord record = outcome.getSubmit().getValue().orElseThrow();
        assertEquals("123456", record.getSchedulerJobId(),
                "the scheduler id is parsed from the scripted sbatch stdout");
        assertTrue(outcome.getTransport() == scripted,
                "the caller receives the very transport the connector yielded");
        assertFalse(outcome.wasTransportClosedByChain());
        assertEquals(0, scripted.closeCount,
                "success must not close - the caller owns the exactly-once close now");
        // Caller ownership exercised the honest way:
        outcome.getTransport().close();
        assertEquals(1, scripted.closeCount);
    }

    @Test
    void aNullConnectorResultRefusesToGuessLoudly() {
        Project project = stub(this.tempDir);
        SSHServer server = new SSHServer("test");
        SSHJob job = preparedJob(project, server);
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> RemoteSubmitChain.connectAndSubmit(server, job, srv -> null));
        assertTrue(error.getMessage().contains("refuses to guess"), error.getMessage());
    }

    /** Scripted transport answering mkdir/upload/sbatch; counts closes. */
    static final class ScriptedTransport implements SshTransport {
        int closeCount;
        boolean failUploads;
        private boolean connected = true;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            try {
                if ("sbatch".equals(command[0])) {
                    Files.writeString(stdoutFile, "Submitted batch job 123456\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                return OperationResult.failed("SSH_EXEC_FAILED", "unexpected command", null);
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }
        @Override public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
            if (this.failUploads) {
                return OperationResult.failed("SFTP_UPLOAD", "upload refused by script", null);
            }
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() {
            this.closeCount++;
            this.connected = false;
        }
    }

    private static Project stub(Path dir) {
        return new Project(null, dir.toString()) {
            @Override public void setNetProject(Project project) { }
            @Override public boolean isValid() { return true; }
            @Override public boolean isSameAs(Project project) { return false; }
            @Override public ProjectProperty getProperty() {
                return new ProjectProperty(dir.toString(), "espresso");
            }
            @Override public String getPrefixName() { return "espresso"; }
            @Override public String getInpFileName(String ext) {
                return ext == null || ext.isBlank() ? "espresso.in" : "espresso.in." + ext;
            }
            @Override public String getLogFileName(String ext) {
                return ext == null || ext.isBlank() ? "espresso.log" : "espresso.log." + ext;
            }
            @Override public String getErrFileName(String ext) {
                return ext == null || ext.isBlank() ? "espresso.err" : "espresso.err." + ext;
            }
            @Override public String getExitFileName() { return "espresso.EXIT"; }
            @Override public QEInput getQEInputGeometry() { return null; }
            @Override public QEInput getQEInputScf() { return null; }
            @Override public QEInput getQEInputOptimiz() { return null; }
            @Override public QEInput getQEInputMd() { return null; }
            @Override public QEInput getQEInputDos() { return null; }
            @Override public QEInput getQEInputBand() { return null; }
            @Override public Cell getCell() { return null; }
            @Override protected void loadQEInputs() { }
            @Override public void resolveQEInputs() { }
            @Override public void markQEInputs() { }
            @Override public boolean isQEInputChanged() { return false; }
            @Override public void saveQEInputs(String directoryPath) { }
            @Override public void exportQEInputsTo(String directoryPath) { }
            @Override public Project cloneProject(String directoryPath) { return null; }
        };
    }
}
