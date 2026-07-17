package quantumforge.matapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;

class QEOptimadeClientTest {

    @Test
    void testOptimadeStructureReconstructsCellCorrectly() throws Exception {
        // Mock OPTIMADE values representing Silicon conventional unit cell
        double[][] lattice = {
            {5.43, 0.0, 0.0},
            {0.0, 5.43, 0.0},
            {0.0, 0.0, 5.43}
        };
        double[][] positions = {
            {0.0, 0.0, 0.0},
            {1.3575, 1.3575, 1.3575}
        };
        String[] species = {"Si", "Si"};

        QEOptimadeClient.OptimadeStructure structure = new QEOptimadeClient.OptimadeStructure(
            "mp-149", "A2B", lattice, positions, species
        );

        assertNotNull(structure);
        assertEquals("mp-149", structure.getId());
        assertEquals("A2B", structure.getFormula());

        Cell cell = structure.buildCell();
        assertNotNull(cell);

        // Verify lattice and coordinates are fully reconstructed
        assertEquals(5.43, cell.copyLattice()[0][0], 1e-6);
        assertEquals(2, cell.listAtoms(true).length);
        assertEquals("Si", cell.listAtoms(true)[1].getName());
        assertEquals(1.3575, cell.listAtoms(true)[1].getX(), 1e-6);
    }
}
