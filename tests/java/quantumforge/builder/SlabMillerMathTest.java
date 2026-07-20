/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.builder.SlabMillerMath.MillerPlane;
import quantumforge.operation.OperationResult;

class SlabMillerMathTest {

    private static final double[][] CUBIC_543 = {{5.43, 0.0, 0.0}, {0.0, 5.43, 0.0},
            {0.0, 0.0, 5.43}};
    private static final double[][] ORTHO = {{10.0, 0.0, 0.0}, {0.0, 20.0, 0.0},
            {0.0, 0.0, 30.0}};
    private static final double[][] TILTED = {{4.0, 0.0, 0.0}, {1.0, 4.0, 0.0},
            {0.0, 0.0, 5.0}};

    @Test
    void cubic110SpacingAndNormalAreTextbook() {
        MillerPlane plane = SlabMillerMath.compute(CUBIC_543, 1, 1, 0)
                .getValue().orElseThrow();
        assertEquals(3.8395898218429534, plane.getDSpacingAng(), 1e-12,
                "d(110) = a/sqrt(2) for a = 5.43 Ang");
        assertEquals(1.6364209716973723, plane.getRecipNormInvAng(), 1e-12);
        assertEquals(0.7071067811865476, plane.getNx(), 1e-12);
        assertEquals(0.7071067811865476, plane.getNy(), 1e-12);
        assertEquals(0.0, plane.getNz(), 1e-15);
        assertFalse(plane.isEsmAligned(), "(110) is not a z surface");
        assertEquals(160.10300699999996, plane.getVolumeAng3(), 1e-9);
    }

    @Test
    void cubic001IsEsmAlignedWithDEqualToA() {
        MillerPlane plane = SlabMillerMath.compute(CUBIC_543, 0, 0, 1)
                .getValue().orElseThrow();
        assertEquals(5.43, plane.getDSpacingAng(), 1e-12);
        assertEquals(0.0, plane.getNx(), 1e-15);
        assertEquals(0.0, plane.getNy(), 1e-15);
        assertEquals(1.0, plane.getNz(), 1e-15);
        assertTrue(plane.isEsmAligned(), "QE ESM wants the normal along z");
    }

    @Test
    void commonFactorNormalizationKeepsThePlaneIdentity() {
        MillerPlane plane = SlabMillerMath.compute(CUBIC_543, 2, 2, 2)
                .getValue().orElseThrow();
        assertEquals(2, plane.getCommonFactor(), "(2 2 2) IS (1 1 1)");
        assertEquals(1, plane.getH());
        assertEquals(3.1350119616996674, plane.getDSpacingAng(), 1e-12,
                "d(111) = a/sqrt(3), not d of the doubled indices");
        assertEquals(0.5773502691896257, plane.getNx(), 1e-12);
    }

    @Test
    void orthorhombic123CrossesDirections() {
        MillerPlane plane = SlabMillerMath.compute(ORTHO, 1, 2, 3)
                .getValue().orElseThrow();
        assertEquals(5.773502691896258, plane.getDSpacingAng(), 1e-12,
                "1/d^2 = 1/10^2*1 + 4/20^2 + 9/30^2 style orthorhombic law");
        assertEquals(0.5773502691896258, plane.getNx(), 1e-12);
        assertEquals(0.5773502691896258, plane.getNy(), 1e-12);
        assertEquals(0.5773502691896258, plane.getNz(), 1e-12);
        assertFalse(plane.isEsmAligned());
        assertEquals(6000.0, plane.getVolumeAng3(), 1e-9);
    }

    @Test
    void tiltedCellsBendTheNormalAwayFromMillerAxes() {
        MillerPlane plane = SlabMillerMath.compute(TILTED, 1, 0, 0)
                .getValue().orElseThrow();
        assertEquals(3.880570000581327, plane.getDSpacingAng(), 1e-12);
        assertEquals(0.9701425001453318, plane.getNx(), 1e-12);
        assertEquals(-0.24253562503633294, plane.getNy(), 1e-12,
                "the (100) normal is NOT the x axis in a tilted cell - the whole "
                        + "point of reciprocal geometry");
        assertEquals(0.0, plane.getNz(), 1e-15);

        MillerPlane b = SlabMillerMath.compute(TILTED, 0, 1, 0).getValue().orElseThrow();
        assertEquals(4.0, b.getDSpacingAng(), 1e-12);
        assertEquals(80.0, b.getVolumeAng3(), 1e-9);
    }

    @Test
    void invalidPlanesAndCellsFailClosed() {
        assertEquals("SLAB_VECTOR", SlabMillerMath.compute(CUBIC_543, 0, 0, 0).getCode(),
                "(0 0 0) is not a plane");
        assertEquals("SLAB_BOUNDS", SlabMillerMath.compute(CUBIC_543, 17, 0, 0).getCode());
        assertEquals("SLAB_CELL", SlabMillerMath.compute(null, 1, 1, 0).getCode());
        double[][] degenerate = {{1.0, 0.0, 0.0}, {2.0, 0.0, 0.0}, {0.0, 0.0, 0.0}};
        assertEquals("SLAB_CELL", SlabMillerMath.compute(degenerate, 1, 1, 0).getCode(),
                "zero volume has no reciprocal geometry");
        OperationResult<MillerPlane> result = SlabMillerMath.compute(CUBIC_543, 1, 1, 0);
        assertTrue(result.isSuccess(), result.toString());
    }
}
