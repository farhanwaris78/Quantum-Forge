package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectGeometry;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;

class FinalGeometryUpdaterTest {

    @Test
    void previewFailsWithoutGeometry() {
        Project project = stubProject(null);
        OperationResult<FinalGeometryUpdater.GeometryPreview> result =
                FinalGeometryUpdater.preview(project);
        assertFalse(result.isSuccess());
    }

    @Test
    void previewSucceedsForConvergedListButApplyRemainsUnsupported() throws Exception {
        // Use a real temp property directory via reflection-free lightweight double.
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("qf-geom");
        ProjectProperty property = new ProjectProperty(dir.toString(), "espresso");
        ProjectGeometryList list = property.getOptList();
        ProjectGeometry geometry = new ProjectGeometry();
        geometry.setEnergy(-15.0);
        geometry.setTotalForce(0.0001);
        geometry.setConverged(true);
        list.addGeometry(geometry);
        list.setConverged(true);
        property.saveOptList();

        Project project = new Project(null, dir.toString()) {
            @Override public void setNetProject(Project project) { }
            @Override public boolean isValid() { return true; }
            @Override public boolean isSameAs(Project project) { return false; }
            @Override public ProjectProperty getProperty() { return property; }
            @Override public String getPrefixName() { return "espresso"; }
            @Override public String getInpFileName(String ext) { return "espresso.in"; }
            @Override public String getLogFileName(String ext) { return "espresso.log"; }
            @Override public String getErrFileName(String ext) { return "espresso.err"; }
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

        OperationResult<FinalGeometryUpdater.GeometryPreview> preview =
                FinalGeometryUpdater.preview(project);
        assertTrue(preview.isSuccess(), preview.toString());
        OperationResult<Void> apply = FinalGeometryUpdater.apply(project);
        assertFalse(apply.isSuccess());
        assertTrue(apply.getMessage().toLowerCase().contains("not yet implemented")
                || apply.getCode().contains("UNAVAILABLE"));
    }

    private static Project stubProject(ProjectProperty property) {
        return new Project(null, "/tmp/qf-none") {
            @Override public void setNetProject(Project project) { }
            @Override public boolean isValid() { return true; }
            @Override public boolean isSameAs(Project project) { return false; }
            @Override public ProjectProperty getProperty() { return property; }
            @Override public String getPrefixName() { return "espresso"; }
            @Override public String getInpFileName(String ext) { return "espresso.in"; }
            @Override public String getLogFileName(String ext) { return "espresso.log"; }
            @Override public String getErrFileName(String ext) { return "espresso.err"; }
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
