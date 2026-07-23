package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class QEAbinitInputBuilderTest {

    @Test
    void testAbinitBuilderGeneratesCleanInputStructure() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.43)); // Silicon conventional cell size
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 1.3575, 1.3575, 1.3575);

        // cutoff=20 Hartree, functional="PBE"
        QEAbinitInputBuilder builder = new QEAbinitInputBuilder(cell, 20.0, "PBE");

        assertNotNull(builder);
        assertEquals("PBE", builder.getXcFunctional());
        assertEquals(20.0, builder.getEcutHa(), 1e-6);

        String input = builder.generateInput();
        assertNotNull(input);

        // Verify key ABINIT syntax parameters
        assertTrue(input.contains("ecut     20.0"));
        assertTrue(input.contains("acell    1.0 1.0 1.0 Angstrom"));
        assertTrue(input.contains("rprim"));
        assertTrue(input.contains("  5.43000000    0.00000000    0.00000000"));

        assertTrue(input.contains("ntypat   1"));
        assertTrue(input.contains("znucl    14")); // Silicon atomic number

        assertTrue(input.contains("natom    2"));
        assertTrue(input.contains("typat    1 1"));

        assertTrue(input.contains("xred"));
        assertTrue(input.contains("  0.00000000    0.00000000    0.00000000")); // Fractional pos 1
        assertTrue(input.contains("  0.25000000    0.25000000    0.25000000")); // Fractional pos 2
    }
}
