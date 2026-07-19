package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class QETensorAnalyzerTest {

    /** Isotropic engineering constants: C11=600, C12=200, C44=200 (C11-C12 = 2*C44). */
    private static double[][] isotropicCubic() {
        double[][] cij = new double[6][6];
        cij[0][0] = cij[1][1] = cij[2][2] = 600.0;
        cij[0][1] = cij[1][0] = cij[0][2] = cij[2][0] = cij[1][2] = cij[2][1] = 200.0;
        cij[3][3] = cij[4][4] = cij[5][5] = 200.0;
        return cij;
    }

    @Test
    void isotropicTensorCollapsesVoigtReussToSingleModuli() {
        OperationResult<QETensorAnalyzer.ElasticModuli> result =
                QETensorAnalyzer.analyzeElastic(isotropicCubic());
        assertTrue(result.isSuccess(), result.toString());
        QETensorAnalyzer.ElasticModuli moduli = result.getValue().orElseThrow();

        // B = (C11 + 2*C12)/3 = 1000/3 exactly for isotropic solids.
        assertEquals(1000.0 / 3.0, moduli.getBulkVoigt(), 1.0e-9);
        assertEquals(1000.0 / 3.0, moduli.getBulkReuss(), 1.0e-6);
        // G = C44 = 200 exactly; Voigt and Reuss bounds must collapse.
        assertEquals(200.0, moduli.getShearVoigt(), 1.0e-9);
        assertEquals(200.0, moduli.getShearReuss(), 1.0e-6);
        // E = 9BG/(3B+G) = 9*333.333*200/1200 = 500.
        assertEquals(500.0, moduli.getYoungsModulusHill(), 1.0e-6);
        // nu = (3B - 2G)/(2(3B+G)) = 600/2400 = 0.25.
        assertEquals(0.25, moduli.getPoissonRatioHill(), 1.0e-9);
        assertEquals(0.0, moduli.getUniversalAnisotropy(), 1.0e-6);
        assertEquals(0.0, moduli.getCauchyPressure(), 1.0e-12);
        assertTrue(Math.abs(moduli.getPughRatio() - (1000.0 / 3.0) / 200.0) < 1.0e-9);
    }

    @Test
    void anisotropicTensorBoundsBracketHillAverage() {
        // Face-centred-cubic-like anisotropic tensor: C11=500, C12=150, C44=200.
        double[][] cij = new double[6][6];
        cij[0][0] = cij[1][1] = cij[2][2] = 500.0;
        cij[0][1] = cij[1][0] = cij[0][2] = cij[2][0] = cij[1][2] = cij[2][1] = 150.0;
        cij[3][3] = cij[4][4] = cij[5][5] = 200.0;
        OperationResult<QETensorAnalyzer.ElasticModuli> result =
                QETensorAnalyzer.analyzeElastic(cij);
        assertTrue(result.isSuccess(), result.toString());
        QETensorAnalyzer.ElasticModuli moduli = result.getValue().orElseThrow();

        assertEquals((500.0 + 2.0 * 150.0) / 3.0, moduli.getBulkVoigt(), 1.0e-9);
        // For cubic crystals B_V = B_R exactly.
        assertEquals(moduli.getBulkVoigt(), moduli.getBulkReuss(), 1.0e-6);
        // G_V = (C11-C12+3*C44)/5 = (350+600)/5 = 190; G_R = 5(C11-C12)C44/(4C44+3(C11-C12)).
        assertEquals(190.0, moduli.getShearVoigt(), 1.0e-9);
        double expectedGR = 5.0 * 350.0 * 200.0 / (4.0 * 200.0 + 3.0 * 350.0);
        assertEquals(expectedGR, moduli.getShearReuss(), 1.0e-6);
        assertTrue(moduli.getShearHill() > moduli.getShearReuss()
                && moduli.getShearHill() < moduli.getShearVoigt(),
                "Hill must lie between the Reuss and Voigt bounds");
        assertTrue(moduli.getUniversalAnisotropy() > 0.0,
                "Zener ratio 2*C44/(C11-C12) = 8/7 > 1 implies positive A^U");
        assertEquals(150.0 - 200.0, moduli.getCauchyPressure(), 1.0e-12);
    }

    @Test
    void failsClosedOnAsymmetricNonSpdAndDegenerateInput() {
        double[][] asymmetric = isotropicCubic();
        asymmetric[0][1] = 200.5;
        assertFalse(QETensorAnalyzer.analyzeElastic(asymmetric).isSuccess(),
                "An asymmetric Voigt tensor must be rejected");

        double[][] nonSpd = isotropicCubic();
        nonSpd[0][1] = nonSpd[1][0] = 1000.0; // det of the 2x2 leading minor < 0
        OperationResult<QETensorAnalyzer.ElasticModuli> unstable =
                QETensorAnalyzer.analyzeElastic(nonSpd);
        assertFalse(unstable.isSuccess(), "A non-positive-definite tensor must be rejected");
        assertTrue(unstable.getMessage().contains("positive definite"), unstable.getMessage());

        assertFalse(QETensorAnalyzer.analyzeElastic(new double[6][6]).isSuccess(),
                "An all-zero tensor must be rejected");
        double[][] nan = isotropicCubic();
        nan[2][2] = Double.NaN;
        assertFalse(QETensorAnalyzer.analyzeElastic(nan).isSuccess());
        assertFalse(QETensorAnalyzer.analyzeElastic(new double[3][3]).isSuccess());
        assertFalse(QETensorAnalyzer.analyzeElastic(null).isSuccess());
    }
}
