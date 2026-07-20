package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class SSHJobPrepareTest {

    @TempDir
    Path tempDir;

    @Test
    void prepareScriptWithoutTransportSucceedsAndSubmitFailsClosed() throws Exception {
        Files.createDirectories(tempDir);
        Project project = stub(tempDir);
        SSHServer server = new SSHServer("test");
        server.setSchedulerType(SSHServer.SCHEDULER_SLURM);
        server.setQueueName("compute");
        SSHJob job = new SSHJob(project, server);
        job.setType(RunningType.SCF);
        job.setNumProcesses(4);
        job.setNumThreads(2);

        OperationResult<String> prepared = job.prepareScriptResult();
        assertTrue(prepared.isSuccess(), prepared.toString());
        assertTrue(prepared.getValue().orElse("").contains("#SBATCH"));

        OperationResult<JobRecord> submitted = job.postJobToServerResult();
        assertFalse(submitted.isSuccess());
        assertTrue(submitted.getCode().contains("UNAVAILABLE")
                || submitted.getMessage().toLowerCase().contains("transport"));
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

    @Test
    void anUnsetSchedulerRefusesPrepareInsteadOfGuessingSlurm() throws Exception {
        Files.createDirectories(tempDir);
        Project project = stub(tempDir);
        SSHServer server = new SSHServer("legacy-none");
        // No setSchedulerType call: every GUI-created server entry keeps 'none',
        // and the batch-144 fix must refuse rather than default to slurm.
        SSHJob job = new SSHJob(project, server);
        job.setType(RunningType.SCF);

        OperationResult<String> prepared = job.prepareScriptResult();
        assertFalse(prepared.isSuccess(),
                "batch 144: an unset scheduler must never default to slurm");
        assertEquals("SSH_SCRIPT_FAILED", prepared.getCode());
        assertTrue(prepared.getMessage().contains("SSH_SCHEDULER_UNSET"),
                prepared.getMessage());
        assertTrue(prepared.getMessage().contains("no default is ever picked"),
                prepared.getMessage());
    }
}
