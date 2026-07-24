package quantumforge.builder.slab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class SlabModelBuilderTest {

    @Test
    void testSlabBuilderCreatesCorrectLatticeAndAtoms() throws Exception {
        Cell bulk = new Cell(Matrix3D.unit(5.0)); // 5x5x5 simple cubic cell
        bulk.addAtom("Si", 0.0, 0.0, 0.0);
        bulk.addAtom(new Atom("Si", 2.5, 2.5, 2.5));

        SlabModelBuilder builder = new SlabModelBuilder(bulk);
        builder.setMillerIndices(1, 1, 0); // Cut along (110) plane
        builder.setNumberOfLayers(3);
        builder.setVacuumThickness(12.0); // 12 Angstrom vacuum

        Cell slab = builder.build();
        assertNotNull(slab);

        // Perpendicular vector should be slab thickness (3 layers * 2.5 spacing = 7.5) + vacuum (12.0) = 19.5 Angstroms!
        double perpendicularLength = Matrix3D.norm(slab.copyLattice()[2]);
        assertEquals(19.5, perpendicularLength, 1e-6);

        Atom[] atoms = slab.listAtoms(true);
        assertNotNull(atoms);
        assertTrue(atoms.length > 0, "Slab must be populated with cloned and translated bulk atoms");

        // Verify that atoms reside inside the generated periodic slab cell. For a general
        // Miller cut such as (110), the surface normal is not necessarily the Cartesian z axis.
        for (Atom atom : atoms) {
            assertTrue(slab.isInCell(atom.getX(), atom.getY(), atom.getZ()),
                "All atoms must lie inside the generated slab cell");
        }
    }
}
