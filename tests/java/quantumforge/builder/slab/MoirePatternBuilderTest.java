package quantumforge.builder.slab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class MoirePatternBuilderTest {

    @Test
    void testMoireBuilderSolvesCommensurateAngleAndGeneratesAtoms() throws Exception {
        // Hexagonal primitive lattice vectors (representing graphene with a=2.46 Angstroms)
        double[][] lattice = {
            {2.46, 0.0, 0.0},
            {-1.23, 2.1304, 0.0},
            {0.0, 0.0, 10.0}
        };
        Cell layer1 = new Cell(lattice);
        layer1.addAtom("C", 0.0, 0.0, 0.0);
        layer1.addAtom(new Atom("C", 0.0, 1.42, 0.0)); // 2-atom primitive graphene basis

        Cell layer2 = new Cell(lattice);
        layer2.addAtom("C", 0.0, 0.0, 0.0);
        layer2.addAtom(new Atom("C", 0.0, 1.42, 0.0));

        MoirePatternBuilder builder = new MoirePatternBuilder(layer1, layer2);

        // Set commensurate integers (n=2, m=1) -> Multiplicity = 2^2 + 2*1 + 1^2 = 7
        builder.setCommensurateIntegers(2, 1);

        // Commensurate angle theta for (2,1) is approx. 21.787 degrees
        assertEquals(21.787, builder.getTwistAngle(), 1e-2);

        Cell moire = builder.build();
        assertNotNull(moire);

        // Expected atoms: Multiplicity (7) * 2 layers * 2 atoms/layer = 28 atoms total in the supercell!
        assertEquals(28, moire.listAtoms(true).length);
    }
}
