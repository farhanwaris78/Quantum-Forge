package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectProperty;

class DryRunPreflightTest {

    @TempDir
    Path tempDir;

    @Test
    void nullProjectReportsError() {
        DryRunPreflight.Report report = DryRunPreflight.run(null, RunningType.SCF, 1);
        assertTrue(report.hasErrors());
    }

    @Test
    void projectWithoutInputStillBuildsReport() throws Exception {
        Files.createDirectories(tempDir);
        Project project = stubProject(tempDir);
        DryRunPreflight.Report report = DryRunPreflight.run(project, RunningType.SCF, 1);
        assertNotNull(report.getIssues());
        // Missing QE binaries and empty input should produce errors, not crash.
        assertTrue(report.hasErrors() || !report.getIssues().isEmpty());
    }

    private static Project stubProject(Path dir) {
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
