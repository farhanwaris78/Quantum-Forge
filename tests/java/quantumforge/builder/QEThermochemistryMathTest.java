package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/** Batch-51 coverage for the explicit-term thermochemistry arithmetic (Roadmap #152/#153). */
class QEThermochemistryMathTest {

    @Test
    void testDefectFormationArithmeticIsExact() {
        // E_def=-151.0, E_host=-100.0, mu=10.0, q=2 with E_VBM=2.0, dE_F=0.5, corr=0.1:
        // -151 + 100 - 10 + 2*(2.5) + 0.1 = -55.9
        OperationResult<Double> result = QEThermochemistryMath.defectFormationEnergy(
                -151.0, -100.0, 10.0, 2, 2.0, 0.5, 0.1);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(-55.9, result.getValue().orElseThrow(), 1.0e-12);

        // Neutral, zero-correction path (charge 0 ignores VBM/Fermi terms).
        OperationResult<Double> neutral = QEThermochemistryMath.defectFormationEnergy(
                -60.0, -55.0, -0.75, 0, 0.0, 0.0, 0.0);
        assertEquals(-4.25, neutral.getValue().orElseThrow(), 1.0e-12);

        // Negative charge flips sign of the electronic term.
        OperationResult<Double> negative = QEThermochemistryMath.defectFormationEnergy(
                -151.0, -100.0, 10.0, -1, 2.0, 0.5, 0.0);
        assertEquals(-63.5, negative.getValue().orElseThrow(), 1.0e-12);
    }

    @Test
    void testAdsorptionArithmeticIsExact() {
        // E_tot=-233.0, slab=-200.0, mol=-30.0, corr=-0.05: -233 + 200 + 30 - 0.05 = -3.05
        OperationResult<Double> result = QEThermochemistryMath.adsorptionEnergy(
                -233.0, -200.0, -30.0, -0.05);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(-3.05, result.getValue().orElseThrow(), 1.0e-12);
    }

    @Test
    void testNonFiniteTermsFailClosed() {
        assertFalse(QEThermochemistryMath.defectFormationEnergy(
                Double.NaN, -100.0, 1.0, 0, 0.0, 0.0, 0.0).isSuccess());
        assertFalse(QEThermochemistryMath.defectFormationEnergy(
                -1.0, -100.0, 1.0, 1, Double.POSITIVE_INFINITY, 0.0, 0.0).isSuccess());
        assertFalse(QEThermochemistryMath.adsorptionEnergy(
                -233.0, -200.0, Double.NaN, 0.0).isSuccess());
        OperationResult<Double> result = QEThermochemistryMath.adsorptionEnergy(
                -1.0, Double.NaN, -1.0, 0.0);
        assertEquals("ENERGY_NONFINITE", result.getCode());
    }
}
