package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

class QEPhonopyForceSetsWriterTest {

    @Test
    void testForceSetsWriterGeneratesValidPhonopyStructure() throws IOException {
        QEPhonopyForceSetsWriter writer = new QEPhonopyForceSetsWriter();

        // 3 atoms supercell
        int numAtoms = 3;

        // Displacement 1: Atom 2 displaced along x by 0.01 A
        double[] disp1 = {0.01, 0.0, 0.0};
        double[][] forces1 = {
            {-0.1500, 0.0, 0.0}, // forces on atom 1
            {0.1400, 0.0, 0.0},  // forces on atom 2
            {0.0100, 0.0, 0.0}   // forces on atom 3 (sum is 0, satisfying sum rule!)
        };
        writer.addRecord(2, disp1, forces1);

        // Displacement 2: Atom 3 displaced along y by 0.01 A
        double[] disp2 = {0.0, 0.01, 0.0};
        double[][] forces2 = {
            {0.0, -0.2500, 0.0},
            {0.0, 0.0500, 0.0},
            {0.0, 0.2000, 0.0}
        };
        writer.addRecord(3, disp2, forces2);

        assertEquals(2, writer.size());

        String forceSetsText = writer.generateForceSetsText(numAtoms);
        assertNotNull(forceSetsText);

        // Verify key phonopy format constraints
        String[] lines = forceSetsText.split("\n");
        assertTrue(lines.length >= 10);

        // Line 1: total atoms
        assertEquals("3", lines[0].trim());
        // Line 2: total displacements
        assertEquals("2", lines[1].trim());

        // First displacement block
        assertEquals("2", lines[3].trim()); // displaced atom index (2)
        assertTrue(lines[4].contains("0.0100000000")); // displacement vector

        // Forces on atom 1 under displacement 1
        assertTrue(lines[5].contains("-0.1500000000"));

        // Second displacement block (blank separator line 8, then atom index at 9)
        assertEquals("3", lines[9].trim()); // displaced atom index (3)
        assertTrue(lines[10].contains("0.0100000000")); // displacement vector along y

        // Write to file and check existence
        File tempFile = File.createTempFile("FORCE_SETS", "");
        tempFile.deleteOnExit();

        writer.writeForceSetsFile(tempFile, numAtoms);
        assertTrue(tempFile.exists());
        assertTrue(Files.size(tempFile.toPath()) > 100);
    }

    @Test
    void rejectsMalformedOrIncompleteForceSetsInsteadOfWritingShiftedBlocks() {
        QEPhonopyForceSetsWriter writer = new QEPhonopyForceSetsWriter();
        assertThrows(IllegalArgumentException.class,
                () -> writer.addRecord(1, new double[] {0.01, 0.0}, new double[][] {{0.0, 0.0, 0.0}}));
        writer.addRecord(1, new double[] {0.01, 0.0, 0.0}, new double[][] {{0.0, 0.0, 0.0}});
        assertThrows(IllegalArgumentException.class, () -> writer.generateForceSetsText(2));
        assertThrows(IllegalArgumentException.class, () -> writer.generateForceSetsText(0));
    }

    @Test
    void convertsQeRyPerBohrForcesExplicitly() {
        QEPhonopyForceSetsWriter writer = new QEPhonopyForceSetsWriter();
        writer.addRecordFromQeRyPerBohr(1, new double[] {0.01, 0.0, 0.0},
                new double[][] {{1.0, 0.0, 0.0}});
        String text = writer.generateForceSetsText(1);
        assertTrue(text.contains(String.format(java.util.Locale.ROOT, "%.10f",
                QEPhonopyForceSetsWriter.RY_PER_BOHR_TO_EV_PER_ANGSTROM)));
    }
}
