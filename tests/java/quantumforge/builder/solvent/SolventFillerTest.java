package quantumforge.builder.solvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class SolventFillerTest {

    @Test
    void testSolventFillerPacksRigidWaterMoleculesNonDestructively() throws Exception {
        // Solute cell: 10x10x10 Angstrom cubic cell with 1 central solute atom
        Cell solute = new Cell(Matrix3D.unit(10.0));
        solute.addAtom(new Atom("Pt", 5.0, 5.0, 5.0));

        SolventFiller filler = new SolventFiller(solute);
        filler.setSolventType(SolventFiller.SOLVENT_WATER);
        filler.setDensity(0.000003); // one-molecule smoke density for a 10 A box
        filler.setMargin(2.0);  // 2.0 A solute margin

        Cell solvated = filler.fill();
        assertNotNull(solvated);

        // Original solute must stay unmodified (exactly 1 atom)
        assertEquals(1, solute.listAtoms(true).length);

        // Solvated box must contain solute + water molecules
        Atom[] atoms = solvated.listAtoms(true);
        assertNotNull(atoms);
        assertTrue(atoms.length > 1);

        // Water molecules should be added rigidly. 
        // Let's assert that O-H bond length of newly added water is close to 0.96 Angstroms!
        boolean foundOH = false;
        for (int i = 0; i < atoms.length; i++) {
            if ("O".equals(atoms[i].getName())) {
                // Find nearest H atom
                double minDistance = Double.MAX_VALUE;
                for (int j = 0; j < atoms.length; j++) {
                    if ("H".equals(atoms[j].getName())) {
                        double dx = atoms[i].getX() - atoms[j].getX();
                        double dy = atoms[i].getY() - atoms[j].getY();
                        double dz = atoms[i].getZ() - atoms[j].getZ();
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (dist < minDistance) {
                            minDistance = dist;
                        }
                    }
                }
                if (minDistance < 1.1) {
                    assertEquals(0.96, minDistance, 0.05); // rigid O-H bond must be preserved!
                    foundOH = true;
                    break;
                }
            }
        }
        assertTrue(foundOH, "Water molecules must be packed rigidly, retaining molecular structures.");

        // Check diagnostics
        assertTrue(filler.getMoleculesPlacedCount() > 0);
        assertTrue(filler.getDiagnostics().get(0).contains("completed"));
        assertTrue(filler.getDiagnostics().get(1).contains("density"));
    }
}
