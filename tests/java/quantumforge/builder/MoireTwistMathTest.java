/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.builder.MoireTwistMath.MoireTwist;
import quantumforge.operation.OperationResult;

class MoireTwistMathTest {

    @Test
    void classicGrapheneAnglesWithExactFractions() {
        OperationResult<MoireTwist> t21 = MoireTwistMath.compute(2, 1, 1.0);
        assertTrue(t21.isSuccess(), t21.toString());
        MoireTwist twist = t21.getValue().orElseThrow();
        assertEquals(7L, twist.getSigmaRaw());
        assertEquals(7L, twist.getSigma(), "(m-n) = 1 keeps sigma = sigmaRaw");
        assertEquals(13L, twist.getCosNumerator());
        assertEquals(14L, twist.getCosDenominator());
        assertEquals(21.786789, twist.getThetaDeg(), 1e-6);
        assertEquals(Math.sqrt(7.0), twist.getMoireLength(), 1e-12,
                "theta from (2,1) gives L = a / (2 sin(theta/2)) = sqrt(7) a");
        assertEquals(0.0, twist.getRequiredStrainLayer2(), 0.0);

        OperationResult<MoireTwist> t41 = MoireTwistMath.compute(4, 1, 1.0);
        MoireTwist sigma7 = t41.getValue().orElseThrow();
        assertEquals(21L, sigma7.getSigmaRaw());
        assertEquals(7L, sigma7.getSigma(), "3 divides (4-1), so sigma = sigmaRaw/3");
        assertEquals(33L, sigma7.getCosNumerator());
        assertEquals(42L, sigma7.getCosDenominator());
        assertEquals(38.213211, sigma7.getThetaDeg(), 1e-6);
    }

    @Test
    void commonFactorsNormalizeWithProvenance() {
        OperationResult<MoireTwist> result = MoireTwistMath.compute(6, 3, 1.0);
        assertTrue(result.isSuccess(), result.toString());
        MoireTwist twist = result.getValue().orElseThrow();
        assertEquals(2, twist.getM());
        assertEquals(1, twist.getN());
        assertEquals(3, twist.getCommonFactor(),
                "(6,3) is the SAME orientation family as (2,1) - stated, not hidden");
        assertEquals(7L, twist.getSigma());
        assertEquals(21.786789, twist.getThetaDeg(), 1e-6);
    }

    @Test
    void latticeMismatchReportsExactRequiredStrain() {
        OperationResult<MoireTwist> result = MoireTwistMath.compute(2, 1, 0.98);
        MoireTwist twist = result.getValue().orElseThrow();
        assertEquals(0.020408, twist.getRequiredStrainLayer2(), 1e-6,
                "1/0.98 - 1: the strain that WOULD restore commensuration");
        assertEquals(2.615427, twist.getMoireLength(), 1e-6,
                "mismatch formula with the exact theta");
    }

    @Test
    void untwistedIdenticalLatticesHaveNoMoire() {
        OperationResult<MoireTwist> result = MoireTwistMath.compute(1, 1, 1.0);
        assertTrue(!result.isSuccess(), "theta = 0 with delta = 0 diverges: primitive");
        assertEquals("MOIRE_RATIO", result.getCode());
    }

    @Test
    void outOfRangeInputsFailClosed() {
        assertEquals("MOIRE_BOUNDS", MoireTwistMath.compute(0, 1, 1.0).getCode());
        assertEquals("MOIRE_BOUNDS", MoireTwistMath.compute(200, 1, 1.0).getCode());
        assertEquals("MOIRE_RATIO", MoireTwistMath.compute(2, 1, -0.5).getCode());
        assertEquals("MOIRE_RATIO", MoireTwistMath.compute(2, 1, 20.0).getCode());
        assertEquals("MOIRE_RATIO", MoireTwistMath.compute(2, 1, Double.NaN).getCode());
    }
}
