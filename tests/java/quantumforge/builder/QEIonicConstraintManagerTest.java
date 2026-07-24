package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;

class QEIonicConstraintManagerTest {

    @Test
    void testConstraintManagerAppliesCorrectPerAxisFlags() {
        QEIonicConstraintManager manager = new QEIonicConstraintManager();

        // Atom index 0: completely frozen substrate atom
        manager.setConstraint(0, 0, 0, 0);
        // Atom index 1: partially constrained surface atom (free along z only)
        manager.setConstraint(1, 0, 0, 1);

        // Verify getters
        assertEquals(0, manager.getConstraint(0).getIfX());
        assertEquals(1, manager.getConstraint(1).getIfZ());

        // Default constraints for un-set atoms should be fully free [1, 1, 1]
        assertEquals(1, manager.getConstraint(5).getIfX());
        assertEquals(1, manager.getConstraint(5).getIfY());
        assertEquals(1, manager.getConstraint(5).getIfZ());

        // Format Atom coordinate lines
        Atom atom1 = new Atom("Fe", 1.25, 1.25, 1.25);
        
        // 1. In static "scf" calculation: flags are omitted to preserve standard inputs
        String lineScf = manager.formatAtomPositionLine(atom1, 1, "scf");
        assertNotNull(lineScf);
        assertEquals(4, lineScf.trim().split("\s+").length,
                "static scf line contains species + 3 coordinates only, no if_pos flags");

        // 2. In "relax" calculation: flags must be appended correctly!
        String lineRelax0 = manager.formatAtomPositionLine(atom1, 0, "relax");
        assertTrue(lineRelax0.contains("0  0  0")); // completely frozen

        String lineRelax1 = manager.formatAtomPositionLine(atom1, 1, "relax");
        assertTrue(lineRelax1.contains("0  0  1")); // partially free
    }

    private static void assertFalse(boolean val) {
        assertTrue(!val);
    }
}
