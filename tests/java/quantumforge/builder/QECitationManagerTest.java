package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

class QECitationManagerTest {

    @Test
    void testCitationManagerCollectsAndCompilesBibTexCorrectly() throws IOException {
        QECitationManager manager = new QECitationManager();

        // Register that our project uses Phonons and SSSP pseudopotentials
        manager.registerFeatureCitations(true, false, false, true);

        // Core Quantum ESPRESSO, SSSP, and Phonopy/Spglib keys must be active
        List<String> keys = manager.getActiveCitationKeys();
        assertNotNull(keys);
        assertTrue(keys.contains("QUANTUM_ESPRESSO"));
        assertTrue(keys.contains("PHONOPY"));
        assertTrue(keys.contains("SSSP"));

        // Compile BibTeX string block
        String bibTex = manager.compileBibTex();
        assertNotNull(bibTex);

        // Verify presence of standard academic authors/titles
        assertTrue(bibTex.contains("@article{giannozzi2009quantum"));
        assertTrue(bibTex.contains("Togo, Atsushi")); // Phonopy author
        assertTrue(bibTex.contains("Prandini, Gianluca")); // SSSP author

        // Write to file and check existence
        File tempFile = File.createTempFile("citations", ".bib");
        tempFile.deleteOnExit();

        manager.writeBibTexFile(tempFile);
        assertTrue(tempFile.exists());
        assertTrue(Files.size(tempFile.toPath()) > 500);
    }
}
