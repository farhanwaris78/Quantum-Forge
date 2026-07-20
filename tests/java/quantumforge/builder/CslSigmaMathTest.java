/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.builder.CslSigmaMath.CslRotation;
import quantumforge.operation.OperationResult;

class CslSigmaMathTest {

    @Test
    void sigmaFiveStgbIsExactPythagorean() {
        OperationResult<CslRotation> result = CslSigmaMath.compute(0, 0, 1, 3, 1);
        assertTrue(result.isSuccess(), result.toString());
        CslRotation rotation = result.getValue().orElseThrow();
        assertEquals(5L, rotation.getSigma(), "the classic Sigma 5 STGB");
        assertEquals(10L, rotation.getSigmaRaw());
        assertEquals(1, rotation.getHalvings(), "10 divided once by 2");
        assertEquals(4L, rotation.getCosNumerator(), "exact cosine 4/5");
        assertEquals(5L, rotation.getCosDenominator());
        assertEquals(36.86989764584402, rotation.getAngleDeg(), 1e-9);
        assertEquals(1L, rotation.getAxisNormSquared());
        assertFalse(rotation.isLatticeSymmetry());
    }

    @Test
    void sigmaThree110AngleIsExactMinusThird() {
        OperationResult<CslRotation> result = CslSigmaMath.compute(1, 1, 0, 1, 1);
        assertTrue(result.isSuccess(), result.toString());
        CslRotation rotation = result.getValue().orElseThrow();
        assertEquals(2L, rotation.getAxisNormSquared());
        assertEquals(3L, rotation.getSigma());
        assertEquals(0, rotation.getHalvings(), "3 is already odd");
        assertEquals(-1L, rotation.getCosNumerator(), "exact cosine -1/3");
        assertEquals(3L, rotation.getCosDenominator());
        assertEquals(109.47122063449069, rotation.getAngleDeg(), 1e-9);
    }

    @Test
    void sigmaEleven011KeepsElevenOdd() {
        OperationResult<CslRotation> result = CslSigmaMath.compute(0, 1, 1, 3, 1);
        assertTrue(result.isSuccess(), result.toString());
        CslRotation rotation = result.getValue().orElseThrow();
        assertEquals(11L, rotation.getSigma());
        assertEquals(7L, rotation.getCosNumerator(), "exact cosine 7/11");
        assertEquals(11L, rotation.getCosDenominator());
        assertEquals(50.47880364135783, rotation.getAngleDeg(), 1e-9);
    }

    @Test
    void latticeSymmetryRotationsReportSigmaOne() {
        CslRotation right90 = CslSigmaMath.compute(0, 0, 1, 1, 1)
                .getValue().orElseThrow();
        assertEquals(1L, right90.getSigma(), "90 deg about [001] is a cubic symmetry");
        assertEquals(0L, right90.getCosNumerator(), "cos 90 deg = 0/1");
        assertEquals(1L, right90.getCosDenominator());
        assertEquals(90.0, right90.getAngleDeg(), 1e-12);
        assertTrue(right90.isLatticeSymmetry());

        CslRotation tetrad120 = CslSigmaMath.compute(1, 1, 1, 1, 1)
                .getValue().orElseThrow();
        assertEquals(1L, tetrad120.getSigma(), "120 deg about [111] is a cubic symmetry");
        assertEquals(-1L, tetrad120.getCosNumerator(), "exact cosine -1/2");
        assertEquals(2L, tetrad120.getCosDenominator());
        assertEquals(2, tetrad120.getHalvings(), "4 divided twice by 2");
        assertTrue(tetrad120.isLatticeSymmetry());
        assertEquals(120.0, tetrad120.getAngleDeg(), 1e-9);
    }

    @Test
    void commonFactorsAreNormalizedWithProvenance() {
        CslRotation axis = CslSigmaMath.compute(0, 2, 2, 3, 1)
                .getValue().orElseThrow();
        assertEquals(2, axis.getAxisCommonFactor(), "[0 2 2] IS [0 1 1]");
        assertArrayEquals(new int[] {0, 1, 1},
                new int[] {axis.getU(), axis.getV(), axis.getW()});
        assertEquals(11L, axis.getSigma());

        CslRotation pair = CslSigmaMath.compute(0, 0, 1, 6, 2)
                .getValue().orElseThrow();
        assertEquals(2, pair.getPairCommonFactor(), "(6,2) IS (3,1)");
        assertEquals(3, pair.getM());
        assertEquals(1, pair.getN());
        assertEquals(5L, pair.getSigma(), "common factors cannot change Sigma");
        assertEquals(36.86989764584402, pair.getAngleDeg(), 1e-9);

        CslRotation sign = CslSigmaMath.compute(0, 0, -3, 3, 1)
                .getValue().orElseThrow();
        assertEquals(3, sign.getAxisCommonFactor(), "gcd of |0,0,3|");
        assertEquals(5L, sign.getSigma(), "axis sense keeps the magnitude law");
    }

    @Test
    void invalidInputFailsClosed() {
        assertEquals("CSL_VECTOR", CslSigmaMath.compute(0, 0, 0, 3, 1).getCode(),
                "a zero axis is not a direction");
        assertEquals("CSL_BOUNDS", CslSigmaMath.compute(17, 0, 0, 3, 1).getCode(),
                "axis component beyond 16");
        assertEquals("CSL_BOUNDS", CslSigmaMath.compute(0, 0, 1, 0, 1).getCode(),
                "m = 0 would be a zero rotation - not a CSL generator");
        assertEquals("CSL_BOUNDS", CslSigmaMath.compute(0, 0, 1, 3, 1025).getCode(),
                "pair index beyond 1024");
    }

    @Test
    void everyKnownCubicEntryMatchesTheLiterature() {
        // Ranganathan table: [uvw] (m,n) -> Sigma, theta
        assertEquals(17L, CslSigmaMath.compute(0, 0, 1, 4, 1)
                .getValue().orElseThrow().getSigma());
        assertEquals(28.072486935852957, CslSigmaMath.compute(0, 0, 1, 4, 1)
                .getValue().orElseThrow().getAngleDeg(), 1e-9);
    }
}
