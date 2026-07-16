package quantumforge.builder.supercell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class SQSBuilderTest {

    @Test
    void testSQSBuilderOptimizesStoichiometricSpeciesAverages() throws Exception {
        // Supercell containing 8 atoms along a line
        Cell supercell = new Cell(Matrix3D.unit(12.0));
        for (int i = 0; i < 8; i++) {
            supercell.addAtom("Fe", i * 1.5, 0.0, 0.0);
        }

        String[] elements = {"Fe", "Ni"};
        double[] concentrations = {0.5, 0.5}; // 50/50 binary alloy

        // SQS optimization must enforce strict stoichiometric counts:
        // 8 * 0.5 = 4 Fe and 4 Ni atoms EXACTLY! (no random fluctuations)
        double error = SQSBuilder.generateSQS(supercell, elements, concentrations);
        assertTrue(error < 1.0, "SQS optimization loop should successfully minimize the correlation error");

        quantumforge.atoms.model.Atom[] atoms = supercell.listAtoms(true);
        assertNotNull(atoms);
        assertEquals(8, atoms.length);

        int feCount = 0;
        int niCount = 0;
        for (quantumforge.atoms.model.Atom atom : atoms) {
            if ("Fe".equals(atom.getName())) feCount++;
            if ("Ni".equals(atom.getName())) niCount++;
        }

        assertEquals(4, feCount, "Stoichiometry must preserve exactly 50% Fe atoms");
        assertEquals(4, niCount, "Stoichiometry must preserve exactly 50% Ni atoms");
    }
}
