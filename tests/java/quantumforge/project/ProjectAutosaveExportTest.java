package quantumforge.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.project.property.ProjectProperty;

/**
 * Verifies export/autosave does not rebind the live project directory.
 */
class ProjectAutosaveExportTest {

    @TempDir
    Path tempDir;

    @Test
    void exportDoesNotChangeProjectDirectory() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        AtomicInteger exportCalls = new AtomicInteger();
        Project stub = new Project(null, projectDir.toString()) {
            @Override public void setNetProject(Project project) { }
            @Override public boolean isValid() { return true; }
            @Override public boolean isSameAs(Project project) { return false; }
            @Override public ProjectProperty getProperty() { return null; }
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
            @Override public boolean isQEInputChanged() { return true; }
            @Override public void saveQEInputs(String directoryPath) {
                throw new AssertionError("saveQEInputs must not be used by autosave");
            }
            @Override public void exportQEInputsTo(String directoryPath) {
                exportCalls.incrementAndGet();
                try {
                    Files.createDirectories(Path.of(directoryPath));
                    Files.writeString(Path.of(directoryPath, "espresso.scf.in"),
                            "&control\n calculation='scf'\n/\n", StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            @Override public Project cloneProject(String directoryPath) { return null; }
        };

        ProjectAutosave autosave = new ProjectAutosave(stub, 3, 500L);
        Path snap = autosave.snapshotNow();
        assertEquals(projectDir.toString(), stub.getDirectoryPath());
        assertTrue(Files.isRegularFile(snap.resolve("AUTOSAVE.txt")));
        assertTrue(Files.isRegularFile(snap.resolve("espresso.scf.in")));
        assertEquals(1, exportCalls.get());
        autosave.close();
    }

    @Test
    void recoveryListsAndRestoresSnapshots() throws Exception {
        Path projectDir = tempDir.resolve("proj2");
        Path snapRoot = projectDir.resolve(ProjectAutosave.SNAPSHOT_DIR).resolve("snapshot-1");
        Files.createDirectories(snapRoot);
        Files.writeString(projectDir.resolve("espresso.scf.in"), "OLD\n", StandardCharsets.UTF_8);
        Files.writeString(snapRoot.resolve("espresso.scf.in"), "NEW\n", StandardCharsets.UTF_8);
        Files.writeString(snapRoot.resolve("AUTOSAVE.txt"), "ok\n", StandardCharsets.UTF_8);

        assertEquals(1, ProjectRecovery.listSnapshots(projectDir).size());
        ProjectRecovery.restoreSnapshot(projectDir, snapRoot);
        assertEquals("NEW\n", Files.readString(projectDir.resolve("espresso.scf.in")));
        assertTrue(Files.list(projectDir)
                .anyMatch(path -> path.getFileName().toString().startsWith(".quantumforge.pre-restore")));
    }
}
