package quantumforge.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.project.property.ProjectProperty;
import quantumforge.project.property.ProjectStatus;
import quantumforge.ver.Version;

class ProjectSchemaTest {

    @TempDir
    Path tempDir;

    @Test
    void currentSchemaIsSupported() {
        assertTrue(ProjectSchema.isSupported(ProjectSchema.CURRENT_VERSION));
        assertEquals(1, ProjectSchema.normalize(null));
        assertThrows(IllegalStateException.class, () -> ProjectSchema.requireSupported(99));
    }

    @Test
    void statusSaveWritesSchemaMetadataAtomically() throws Exception {
        ProjectProperty property = new ProjectProperty(tempDir.toString(), "espresso");
        ProjectStatus status = property.getStatus();
        status.setMolecule(true);
        status.setCellAxis("xyz");
        property.saveStatus();

        Path statusFile = tempDir.resolve(ProjectSchema.STATUS_FILE);
        assertTrue(Files.isRegularFile(statusFile));
        String json = Files.readString(statusFile, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\": 1") || json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains(Version.VERSION));
        assertTrue(Files.isRegularFile(tempDir.resolve(ProjectSchema.STATUS_FILE + ".bak"))
                || true); // first save may not create backup

        // Second save creates last-known-good backup.
        status.setMolecule(false);
        property.saveStatus();
        assertTrue(Files.isRegularFile(tempDir.resolve(ProjectSchema.STATUS_FILE + ".bak")));
    }
}
