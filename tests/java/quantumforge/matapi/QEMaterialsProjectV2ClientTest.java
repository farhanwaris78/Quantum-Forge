package quantumforge.matapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;

class QEMaterialsProjectV2ClientTest {

    @Test
    void testMpStructureReconstructsCellFromFractionalPymatgenCoordinates() throws Exception {
        // Mock values matching Pymatgen-deserialized Silicon conventional cell
        double[][] lattice = {
            {5.43, 0.0, 0.0},
            {0.0, 5.43, 0.0},
            {0.0, 0.0, 5.43}
        };
        // Fractional site coordinates
        double[][] coords = {
            {0.0, 0.0, 0.0},
            {0.25, 0.25, 0.25}
        };
        String[] elements = {"Si", "Si"};

        QEMaterialsProjectV2Client.MpStructure structure = new QEMaterialsProjectV2Client.MpStructure(
            "mp-149", lattice, coords, elements
        );

        assertNotNull(structure);
        assertEquals("mp-149", structure.getMaterialId());

        Cell cell = structure.buildCell();
        assertNotNull(cell);

        // Verify Cartesian translation is correct
        // Cartesian_x = s0 * a1_x = 0.25 * 5.43 = 1.3575 Angstroms
        assertEquals(5.43, cell.copyLattice()[0][0], 1e-6);
        assertEquals(2, cell.listAtoms(true).length);
        assertEquals("Si", cell.listAtoms(true)[1].getName());
        assertEquals(1.3575, cell.listAtoms(true)[1].getX(), 1e-6);
    }
}
