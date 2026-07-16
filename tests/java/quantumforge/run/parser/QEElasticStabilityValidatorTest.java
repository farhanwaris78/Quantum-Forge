package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.run.parser.QEElasticStabilityValidator.StabilityResult;

class QEElasticStabilityValidatorTest {

    @Test
    void testComputeDeterminant() {
        double[][] matrix = {
            {2.0, 1.0},
            {1.0, 3.0}
        };
        // det = 2 * 3 - 1 * 1 = 5.0
        assertEquals(5.0, QEElasticStabilityValidator.computeDeterminant(matrix), 1e-6);
    }

    @Test
    void testValidateStabilityForStableCubicTensor() {
        // Construct a highly stable cubic crystal tensor: C11 = 120 GPa, C12 = 40 GPa, C44 = 60 GPa
        double[][] cij = new double[6][6];
        cij[0][0] = 120.0; cij[0][1] = 40.0;  cij[0][2] = 40.0;
        cij[1][0] = 40.0;  cij[1][1] = 120.0; cij[1][2] = 40.0;
        cij[2][0] = 40.0;  cij[2][1] = 40.0;  cij[2][2] = 120.0;
        cij[3][3] = 60.0;
        cij[4][4] = 60.0;
        cij[5][5] = 60.0;

        StabilityResult result = QEElasticStabilityValidator.validateStability(cij);
        assertNotNull(result);
        assertTrue(result.isMechanicallyStable(), "Leading principal minors must be positive for stable cubic Fe/Si phase");
        assertTrue(result.getDiagnostics().get(0).contains("satisfied"));
    }

    @Test
    void testValidateStabilityForUnstableTensor() {
        // Construct an unstable tensor (C11 < C12 -> shear instability, e.g., C11 = 40 GPa, C12 = 60 GPa)
        double[][] cij = new double[6][6];
        cij[0][0] = 40.0;  cij[0][1] = 60.0;  cij[0][2] = 40.0;
        cij[1][0] = 60.0;  cij[1][1] = 40.0;  cij[1][2] = 40.0;
        cij[2][0] = 40.0;  cij[2][1] = 40.0;  cij[2][2] = 40.0;
        cij[3][3] = 60.0;
        cij[4][4] = 60.0;
        cij[5][5] = 60.0;

        StabilityResult result = QEElasticStabilityValidator.validateStability(cij);
        assertNotNull(result);
        assertFalse(result.isMechanicallyStable(), "Negative eigenvalues/shear instability must be flagged unstable");
        assertTrue(result.getDiagnostics().get(0).contains("FAILED"));
    }
}
