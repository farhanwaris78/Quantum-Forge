package quantumforge.matapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

class QESdfParserTest {

    @Test
    void testParserProcessesStandardSdfMoleculeAndCentersCoordinates() throws Exception {
        // Mock standard V2000 SDF string for Carbon Monoxide (CO)
        String sdf = "\n" +
                "  PubChem/QuantumForge\n" +
                "\n" +
                "  2  1  0  0  0  0  0  0  0  0999 V2000\n" +
                "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0  0  0  0  0  0  0\n" +
                "    0.0000    0.0000    1.1280 O   0  0  0  0  0  0  0  0  0  0  0  0\n" +
                "  1  2  3  0     0  0\n" +
                "M  END\n";

        Cell cell = QESdfParser.parseSDF(sdf);
        assertNotNull(cell);

        // Verify lattice size = 15.0 Angstroms cubic box
        assertEquals(15.0, cell.copyLattice()[0][0], 1e-6);

        Atom[] atoms = cell.listAtoms(true);
        assertNotNull(atoms);
        assertEquals(2, atoms.length);

        // Carbon and Oxygen
        assertEquals("C", atoms[0].getName());
        assertEquals("O", atoms[1].getName());

        // Verify bond length is preserved = 1.128 Angstroms
        double dx = atoms[0].getX() - atoms[1].getX();
        double dy = atoms[0].getY() - atoms[1].getY();
        double dz = atoms[0].getZ() - atoms[1].getZ();
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        assertEquals(1.128, dist, 1e-4);

        // Verify molecule is centered at (7.5, 7.5, 7.5)
        double cx = (atoms[0].getX() + atoms[1].getX()) / 2.0;
        double cy = (atoms[0].getY() + atoms[1].getY()) / 2.0;
        double cz = (atoms[0].getZ() + atoms[1].getZ()) / 2.0;

        assertEquals(7.5, cx, 1e-6);
        assertEquals(7.5, cy, 1e-6);
        assertEquals(7.5, cz, 1e-6);
    }
}
