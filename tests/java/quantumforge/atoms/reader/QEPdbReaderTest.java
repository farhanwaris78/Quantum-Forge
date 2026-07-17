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

class QEPdbReaderTest {

    @Test
    void testPdbReaderParsesCryst1AndAtomsCorrectly() throws IOException {
        File tempFile = File.createTempFile("molecule", ".pdb");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("HEADER    WATER MOLECULE\n");
            // CRYST1 orthogonal lattice box: 10.0 x 12.0 x 15.0 Angstroms
            writer.write("CRYST1   10.000   12.000   15.000  90.00  90.00  90.00 P 1\n");
            writer.write("HETATM    1  O   HOH A   1       0.000   0.000   0.000  1.00  0.00           O\n");
            writer.write("HETATM    2  H1  HOH A   1       0.960   0.000   0.000  1.00  0.00           H\n");
            writer.write("HETATM    3  H2  HOH A   1      -0.240   0.930   0.000  1.00  0.00           H\n");
            writer.write("END\n");
        }

        QEPdbReader reader = new QEPdbReader(tempFile);
        Cell cell = reader.readCell();
        assertNotNull(cell);

        // Verify lattice dimensions are parsed correctly from CRYST1
        assertEquals(10.0, cell.copyLattice()[0][0], 1e-6);
        assertEquals(12.0, cell.copyLattice()[1][1], 1e-6);
        assertEquals(15.0, cell.copyLattice()[2][2], 1e-6);

        Atom[] atoms = cell.listAtoms(true);
        assertNotNull(atoms);
        assertEquals(3, atoms.length);

        // Verify element and coordinate assignments
        assertEquals("O", atoms[0].getName());
        assertEquals(0.0, atoms[0].getX(), 1e-6);

        assertEquals("H", atoms[1].getName());
        assertEquals(0.960, atoms[1].getX(), 1e-6);

        assertEquals("H", atoms[2].getName());
        assertEquals(-0.240, atoms[2].getX(), 1e-6);
    }
}
