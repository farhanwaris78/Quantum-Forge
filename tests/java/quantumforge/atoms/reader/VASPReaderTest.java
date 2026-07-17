package quantumforge.atoms.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

class VASPReaderTest {

    @Test
    void testVASPReaderParsesVasp5FormatCorrectly() throws IOException {
        File tempFile = File.createTempFile("vasp5-poscar", "");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("Silicon Bulk (VASP 5 format)\n");
            writer.write("   1.00000000000000\n"); // scale
            writer.write("     5.43000000    0.00000000    0.00000000\n");
            writer.write("     0.00000000    5.43000000    0.00000000\n");
            writer.write("     0.00000000    0.00000000    5.43000000\n");
            writer.write("   Si\n"); // VASP 5 lists elements on line 6!
            writer.write("   2\n");  // Counts on line 7
            writer.write("Direct\n");
            writer.write("  0.00000000  0.00000000  0.00000000\n");
            writer.write("  0.25000000  0.25000000  0.25000000\n");
        }

        VASPReader reader = new VASPReader(tempFile);
        Cell cell = reader.readCell();
        assertNotNull(cell);

        assertEquals(5.43, cell.copyLattice()[0][0], 1e-6);
        assertEquals(2, cell.listAtoms(true).length);
        assertEquals("Si", cell.listAtoms(true)[0].getName());
        assertEquals(1.3575, cell.listAtoms(true)[1].getX(), 1e-6);
    }

    @Test
    void testVASPReaderParsesVasp4FormatWithoutCrashing() throws IOException {
        File tempFile = File.createTempFile("vasp4-poscar", "");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("Iron Bulk (VASP 4 format)\n");
            writer.write("   1.00000000000000\n"); // scale
            writer.write("     2.86000000    0.00000000    0.00000000\n");
            writer.write("     0.00000000    2.86000000    0.00000000\n");
            writer.write("     0.00000000    0.00000000    2.86000000\n");
            writer.write("   2\n");  // VASP 4 lists ONLY counts on line 6 (no elements line!)
            writer.write("Direct\n");
            writer.write("  0.00000000  0.00000000  0.00000000\n");
            writer.write("  0.50000000  0.50000000  0.50000000\n");
        }

        VASPReader reader = new VASPReader(tempFile);
        Cell cell = reader.readCell();
        assertNotNull(cell);

        assertEquals(2.86, cell.copyLattice()[0][0], 1e-6);
        assertEquals(2, cell.listAtoms(true).length);
        // Elements should be auto-generated as X1, X2
        assertEquals("X1", cell.listAtoms(true)[0].getName());
        assertEquals(1.43, cell.listAtoms(true)[1].getX(), 1e-6);
    }
}
