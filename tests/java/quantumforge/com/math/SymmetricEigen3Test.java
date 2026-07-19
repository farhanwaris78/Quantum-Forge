package quantumforge.com.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Batch-53 coverage for the cyclic-Jacobi 3x3 eigensolver. */
class SymmetricEigen3Test {

    @Test
    void testDiagonalMatrixIsImmediate() {
        SymmetricEigen3.EigenResult result = SymmetricEigen3.eigenvalues(
                new double[][] {{3, 0, 0}, {0, 1, 0}, {0, 0, 2}});
        assertTrue(result.isConverged());
        assertEquals(0, result.getSweeps());
        double[] values = result.getEigenvalues();
        assertEquals(1.0, values[0], 1.0e-15);
        assertEquals(2.0, values[1], 1.0e-15);
        assertEquals(3.0, values[2], 1.0e-15);
    }

    @Test
    void testRotationPlaneEigenpaired() {
        // Eigenvalues 2, 2, 4 with a degenerate pair.
        SymmetricEigen3.EigenResult result = SymmetricEigen3.eigenvalues(
                new double[][] {{3, 1, 0}, {1, 3, 0}, {0, 0, 2}});
        assertTrue(result.isConverged());
        double[] values = result.getEigenvalues();
        assertEquals(2.0, values[0], 1.0e-13);
        assertEquals(2.0, values[1], 1.0e-13);
        assertEquals(4.0, values[2], 1.0e-13);
    }

    @Test
    void testGeneralSymmetricPreservesTraceAndDeterminant() {
        SymmetricEigen3.EigenResult result = SymmetricEigen3.eigenvalues(
                new double[][] {{4.0, 1.0, 2.0}, {1.0, 3.0, 0.5}, {2.0, 0.5, 1.0}});
        assertTrue(result.isConverged());
        double[] values = result.getEigenvalues();
        // Singular matrix: one eigenvalue at 0, trace 8.
        assertEquals(0.0, values[0], 1.0e-12);
        assertEquals(8.0, values[0] + values[1] + values[2], 1.0e-12);
    }

    @Test
    void testRotatedTensorRecoversPrincipalValues() {
        // R^T diag(0.5, 0.25, 0.125) R with a 30-degree z rotation.
        double c = Math.cos(Math.toRadians(30.0));
        double s = Math.sin(Math.toRadians(30.0));
        double d1 = 0.5;
        double d2 = 0.25;
        double m11 = c * c * d1 + s * s * d2;
        double m22 = s * s * d1 + c * c * d2;
        double m12 = c * s * (d1 - d2);
        SymmetricEigen3.EigenResult result = SymmetricEigen3.eigenvalues(
                new double[][] {{m11, m12, 0}, {m12, m22, 0}, {0, 0, 0.125}});
        assertTrue(result.isConverged());
        double[] values = result.getEigenvalues();
        assertEquals(0.125, values[0], 1.0e-14);
        assertEquals(0.25, values[1], 1.0e-14);
        assertEquals(0.5, values[2], 1.0e-14);
    }

    @Test
    void testRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> SymmetricEigen3.eigenvalues(new double[2][2]));
        assertThrows(IllegalArgumentException.class, () -> SymmetricEigen3.eigenvalues(
                new double[][] {{1, Double.NaN, 0}, {Double.NaN, 1, 0}, {0, 0, 1}}));
        assertThrows(IllegalArgumentException.class, () -> SymmetricEigen3.eigenvalues(
                new double[][] {{1, 0.5, 0}, {0.5001, 1, 0}, {0, 0, 1}}));
    }
}
