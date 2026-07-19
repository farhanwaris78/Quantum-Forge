package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/** Batch-52 coverage for the Arrhenius hop-link arithmetic (Roadmap #157). */
class QEDiffusionBarrierLinkTest {

    @Test
    void testAttemptFrequencyPrefactorIsExact() {
        // a=2 Ang, nu=1 THz, d=3: D0 = 4/(2*3) * 1e-4 = 6.666666...e-5 cm^2/s.
        OperationResult<Double> d0 = QEDiffusionBarrierLink.preFactorCm2PerS(2.0, 1.0, 3);
        assertTrue(d0.isSuccess(), d0.getMessage());
        assertEquals(4.0 / 6.0 * 1.0e-4, d0.getValue().orElseThrow(), 1.0e-20);

        // Dimensionality matters: d=2 doubles the prefactor relative to d=3/2 scaling.
        OperationResult<Double> d2 = QEDiffusionBarrierLink.preFactorCm2PerS(3.0, 12.0, 2);
        assertEquals(9.0 * 12.0 / 4.0 * 1.0e-4, d2.getValue().orElseThrow(), 1.0e-20);
    }

    @Test
    void testArrheniusActivationIsExact() {
        // Zero barrier: D = D0 exactly.
        OperationResult<Double> zero = QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                0.0, 300.0, 2.0, 1.0, 3);
        assertTrue(zero.isSuccess(), zero.getMessage());
        assertEquals(4.0 / 6.0 * 1.0e-4, zero.getValue().orElseThrow(), 1.0e-20);

        // Choose T so Ea/(kB T) = ln 2: D = D0/2 exactly.
        double temperature = 0.5 / (QEDiffusionBarrierLink.KB_EV_PER_K * Math.log(2.0));
        OperationResult<Double> half = QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                0.5, temperature, 2.0, 1.0, 3);
        assertTrue(half.isSuccess(), half.getMessage());
        assertEquals(0.5 * 4.0 / 6.0 * 1.0e-4, half.getValue().orElseThrow(), 1.0e-20);
    }

    @Test
    void testInvalidInputsFailClosed() {
        assertFalse(QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                -0.1, 300.0, 2.0, 1.0, 3).isSuccess(), "Negative barriers are refused");
        assertFalse(QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                0.5, 0.0, 2.0, 1.0, 3).isSuccess(), "Zero temperature is refused");
        assertFalse(QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                0.5, 300.0, 2.0, 1.0, 4).isSuccess(), "Dimension 4 is refused");
        assertFalse(QEDiffusionBarrierLink.preFactorCm2PerS(0.0, 1.0, 3).isSuccess(),
                "Zero hop length is refused");
        assertFalse(QEDiffusionBarrierLink.preFactorCm2PerS(2.0, Double.NaN, 3).isSuccess(),
                "NaN attempt frequency is refused");
        OperationResult<Double> negative = QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                -1.0, 300.0, 2.0, 1.0, 3);
        assertEquals("BARRIER_INVALID", negative.getCode());
    }
}
