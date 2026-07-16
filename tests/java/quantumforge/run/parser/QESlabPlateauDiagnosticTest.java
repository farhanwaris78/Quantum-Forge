package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.run.parser.QESlabPlateauDiagnostic.PlateauResult;

class QESlabPlateauDiagnosticTest {

    @Test
    void testPotentialAnalysisIdentifiesPlateausAndDipoleStep() {
        // Construct a mock 1D planar potential along z representing a symmetric slab with vacuum
        // Grid spacing dz = 0.2 Angstroms, total 50 points (10 Angstroms box)
        double[] potential = new double[50];
        
        // Left vacuum region (indices 0 to 14): flat plateau at 5.0 eV
        for (int i = 0; i <= 14; i++) {
            potential[i] = 5.0;
        }
        // Slab region (indices 15 to 34): potential drops in the core of the metal slab
        for (int i = 15; i <= 34; i++) {
            potential[i] = -2.0 + Math.pow((i - 24.5) / 10.0, 2); // parabolic metal well
        }
        // Right vacuum region (indices 35 to 49): flat plateau at 5.5 eV (asymmetric terminations / dipole)
        for (int i = 35; i <= 49; i++) {
            potential[i] = 5.5;
        }

        double fermiEnergy = 0.5; // eV
        double dz = 0.2; // Angstroms

        PlateauResult result = QESlabPlateauDiagnostic.analyzePotential(potential, dz, fermiEnergy, 1.0e-3);
        assertNotNull(result);
        assertTrue(result.isPlateauFound(), "Should successfully detect plateaus in both halves");
        
        assertEquals(5.0, result.getLeftVacuumLevel(), 1e-4);
        assertEquals(5.5, result.getRightVacuumLevel(), 1e-4);
        assertEquals(0.5, result.getDipoleStep(), 1e-4);

        // Work functions: Left = 5.0 - 0.5 = 4.5 eV, Right = 5.5 - 0.5 = 5.0 eV
        assertEquals(4.5, result.getLeftWorkFunction(), 1e-4);
        assertEquals(5.0, result.getRightWorkFunction(), 1e-4);
        assertTrue(result.getWarnings().isEmpty(), "No warnings expected for thick, flat plateaus");
    }

    @Test
    void testPotentialAnalysisTriggersWarningForUnconvergedVacuum() {
        // Construct an unconverged potential (slopes never become flat)
        double[] potential = new double[50];
        for (int i = 0; i < 50; i++) {
            potential[i] = Math.sin(i / 10.0); // oscillating slope, never flat
        }

        double fermiEnergy = 0.0;
        double dz = 0.2;

        PlateauResult result = QESlabPlateauDiagnostic.analyzePotential(potential, dz, fermiEnergy, 1.0e-4);
        assertNotNull(result);
        assertFalse(result.isPlateauFound(), "Symmetry/slopes should not satisfy strict flat plateau condition");
        assertTrue(result.getWarnings().size() > 0, "Should generate warnings for unconverged vacuum plateaus");
        assertTrue(result.getWarnings().get(0).contains("interactions") || result.getWarnings().get(1).contains("interactions"));
    }
}
