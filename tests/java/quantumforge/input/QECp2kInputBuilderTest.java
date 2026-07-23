package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class QECp2kInputBuilderTest {

    @Test
    void testCp2kBuilderGeneratesCleanNestedInputStructure() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.43)); // Silicon conventional cell size
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 1.3575, 1.3575, 1.3575);

        // project="bulk_si", functional="PBE", cutoff=280 Ry
        QECp2kInputBuilder builder = new QECp2kInputBuilder(cell, "bulk_si", "PBE", 280.0);

        assertNotNull(builder);
        assertEquals("bulk_si", builder.getProjectLabel());
        assertEquals("PBE", builder.getXcFunctional());
        assertEquals(280.0, builder.getCutoffRy(), 1e-6);

        String input = builder.generateInput();
        assertNotNull(input);

        // Verify key CP2K syntax sections
        assertTrue(input.contains("&GLOBAL"));
        assertTrue(input.contains("PROJECT bulk_si"));
        assertTrue(input.contains("&END GLOBAL"));

        assertTrue(input.contains("&FORCE_EVAL"));
        assertTrue(input.contains("METHOD Quickstep"));
        assertTrue(input.contains("CUTOFF 280.0"));
        assertTrue(input.contains("&XC_FUNCTIONAL PBE"));

        assertTrue(input.contains("&SUBSYS"));
        assertTrue(input.contains("&CELL"));
        assertTrue(input.contains("A       5.430000     0.000000     0.000000"));
        
        assertTrue(input.contains("&COORD"));
        assertTrue(input.contains("Si       0.000000     0.000000     0.000000"));
        assertTrue(input.contains("Si       1.357500     1.357500     1.357500"));
    }
}
