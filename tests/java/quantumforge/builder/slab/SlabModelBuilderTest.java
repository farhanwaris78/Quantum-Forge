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

        // Verify that atoms are centered and reside within the slab boundary along z:
        // Left vacuum region is roughly 6.0 Angstroms, Right is 6.0, slab sits from 6.0 to 13.5
        for (Atom atom : atoms) {
            assertTrue(atom.getZ() >= 4.0 && atom.getZ() <= 15.5, 
                "All atoms must lie in the centered slab region, leaving vacuum on the cell edges");
        }
    }
}
