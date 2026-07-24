package quantumforge.builder.supercell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class QEGrainBoundaryBuilderTest {

    @Test
    void testGrainBoundaryBuilderAssemblesGrainsAndCleansOverlaps() throws Exception {
        // Cubic unit cell (size = 6.0)
        Cell cell = new Cell(Matrix3D.unit(6.0));
        cell.addAtom("Fe", 0.0, 0.0, 0.0); // Atom 1 (at interface z=0)
        cell.addAtom(new Atom("Fe", 3.0, 3.0, 3.0)); // Atom 2 (bulk core)

        // Sigma 5 Grain boundary (twist angle = 36.87 degrees)
        QEGrainBoundaryBuilder builder = new QEGrainBoundaryBuilder(
            cell, QEGrainBoundaryBuilder.SigmaValue.SIGMA_5, 1.5
        );

        assertEquals(QEGrainBoundaryBuilder.SigmaValue.SIGMA_5, builder.getSigmaValue());
        assertEquals(1.5, builder.getOverlapThreshold());

        Cell gb = builder.build();
        assertNotNull(gb);

        // Perpendicular vector should be doubled (6.0 * 2 = 12.0 Angstroms)
        assertEquals(12.0, gb.copyLattice()[2][2], 1e-6);

        // Check Atom Count:
        // Original: 2 atoms. Doubled: 4 atoms.
        // At the interface (z=0 and stacked z=6.0), the Fe atom at (0,0,0) in grain 1
        // and rotated/stacked Fe atom at (0,0,6) in grain 2 are separated by exactly 6.0 Angstroms (which wraps to 0.0 periodic distance).
        // Since 0.0 < 1.5, the overlapping atom in grain 2 MUST be deleted!
        // So final count should be exactly 3 atoms!
        assertEquals(3, gb.listAtoms(true).length);
    }
}
