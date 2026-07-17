package quantumforge.builder.adsorption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class MoleculeAdsorberTest {

    @Test
    void testAdsorbMoleculeNonDestructivelyWithCollisionChecks() throws Exception {
        Cell slab = new Cell(Matrix3D.unit(10.0)); // 10x10x10 cubic cell
        slab.addAtom("Pt", 5.0, 5.0, 2.0);        // Platinum surface atom at z=2.0

        Cell co = MoleculeAdsorber.createMolecule("CO");
        assertNotNull(co);

        MoleculeAdsorber adsorber = new MoleculeAdsorber(slab);
        adsorber.setMolecule(co);
        adsorber.setPosition(0.5, 0.5); // center on surface

        // 1. Try a very small height of 0.5 Angstroms (triggers collision)
        adsorber.setHeight(0.5);
        Cell combinedColl = adsorber.adsorb();
        assertNotNull(combinedColl);
        
        // Original slab remains unmodified (1 Pt atom)
        assertEquals(1, slab.listAtoms(true).length);
        // Combined cell contains Pt + CO (3 atoms total)
        assertEquals(3, combinedColl.listAtoms(true).length);

        assertTrue(adsorber.isCollisionDetected());
        assertTrue(adsorber.getDiagnostics().get(0).contains("collision"));
        assertEquals(0.5, adsorber.getMinimumContactDistance(), 1e-4);

        // 2. Try a safe height of 2.5 Angstroms (collision resolved)
        adsorber.setHeight(2.5);
        Cell combinedSafe = adsorber.adsorb();
        assertNotNull(combinedSafe);

        assertFalse(adsorber.isCollisionDetected());
        assertTrue(adsorber.getDiagnostics().get(0).contains("optimized"));
        assertEquals(2.5, adsorber.getMinimumContactDistance(), 1e-4);
    }
}
