package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEPhononFreqParserTest {

    @Test
    void testParserProcessesStablePhononDispersion() throws IOException {
        File tempFile = File.createTempFile("matdyn-freq", ".freq");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            // Rows: q-distance, mode1_freq, mode2_freq
            writer.write("   0.0000000       120.0000      240.0000\n");
            writer.write("   0.1000000       122.0000      241.0000\n");
            writer.write("   0.2000000       125.0000      243.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEPhononFreqParser parser = new QEPhononFreqParser(property);
        parser.parse(tempFile);

        List<QEPhononFreqParser.PhononBranch> branches = parser.getBranches();
        assertNotNull(branches);
        assertEquals(2, branches.size(), "Should parse exactly 2 phonon branches");

        // First branch assertions
        assertEquals(3, branches.get(0).size());
        assertEquals(122.0, branches.get(0).getFrequencyCm1()[1], 1e-6);

        // Second branch assertions
        assertEquals(241.0, branches.get(1).getFrequencyCm1()[1], 1e-6);

        assertTrue(parser.isLatticeStable());
        assertTrue(parser.getDiagnostics().get(0).contains("stability verified"));
    }

    @Test
    void testParserDetectsImaginaryUnstableLatticePhononModes() throws IOException {
        File tempFile = File.createTempFile("matdyn-freq-fail", ".freq");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            // Unstable: negative frequency -45.0 cm-1 represents imaginary mode!
            writer.write("   0.0000000       -45.0000      240.0000\n");
            writer.write("   0.1000000       122.0000      241.0000\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEPhononFreqParser parser = new QEPhononFreqParser(property);
        parser.parse(tempFile);

        assertFalse(parser.isLatticeStable(), "Lattice must be unstable because of imaginary negative modes");
        assertTrue(parser.getDiagnostics().get(0).contains("mechanical instability"));
        assertTrue(parser.getDiagnostics().get(1).contains("re-run geometry relaxation"));
    }
}
