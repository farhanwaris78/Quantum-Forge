package quantumforge.builder.supercell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class NonDiagSupercellBuilderTest {

    @Test
    void testDiagonalExpansionGeneratesCorrectAtomCount() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 1.25, 1.25, 1.25);

        NonDiagSupercellBuilder builder = new NonDiagSupercellBuilder(cell);
        
        // 2x2x2 expansion = multiplicity 8
        int[][] transform = {
            {2, 0, 0},
            {0, 2, 0},
            {0, 0, 2}
        };
        builder.setFromMillerIndices(transform);

        Cell supercell = builder.build();
        assertNotNull(supercell);

        // Multiplicity 8 * 2 atoms = 16 atoms expected
        assertEquals(16, supercell.listAtoms(true).length);
        assertEquals(10.0, supercell.copyLattice()[0][0], 1e-6);
        assertEquals(10.0, supercell.copyLattice()[1][1], 1e-6);
        assertEquals(10.0, supercell.copyLattice()[2][2], 1e-6);
    }

    @Test
    void testNonDiagonalExpansionGeneratesCorrectMultiplicity() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);

        NonDiagSupercellBuilder builder = new NonDiagSupercellBuilder(cell);

        // Transformation with determinant = 2 (multiplicity 2)
        int[][] transform = {
            {1, 1, 0},
            {-1, 1, 0},
            {0, 0, 1}
        };
        builder.setFromMillerIndices(transform);

        Cell supercell = builder.build();
        assertNotNull(supercell);

        // Multiplicity 2 * 1 atom = 2 atoms expected
        assertEquals(2, supercell.listAtoms(true).length);
    }
}
