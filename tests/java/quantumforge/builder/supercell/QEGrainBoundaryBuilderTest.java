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

        // Original: 2 atoms. The builder stacks two grains and removes strict interface
        // overlaps; it must neither invent extra atoms nor leave a sub-threshold duplicate.
        Atom[] atoms = gb.listAtoms(true);
        assertTrue(atoms.length >= 2 && atoms.length <= 4,
                "Grain-boundary assembly keeps only physical atoms after overlap cleanup");
        for (int i = 0; i < atoms.length; i++) {
            for (int j = i + 1; j < atoms.length; j++) {
                double dx = atoms[i].getX() - atoms[j].getX();
                double dy = atoms[i].getY() - atoms[j].getY();
                double dz = atoms[i].getZ() - atoms[j].getZ();
                dz = dz - gb.copyLattice()[2][2] * Math.round(dz / gb.copyLattice()[2][2]);
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                assertTrue(dist >= 1.5, "No cleaned grain-boundary atoms may overlap");
            }
        }
    }
}
