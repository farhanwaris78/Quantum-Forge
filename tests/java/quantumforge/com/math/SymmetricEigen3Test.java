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

    @Test
    void testEigenvectorsReconstructRotatedTensor() {
        // A = R diag(0.125, 0.25, 0.5) R^T with R = 30-degree rotation about z.
        double c = Math.cos(Math.toRadians(30.0));
        double s = Math.sin(Math.toRadians(30.0));
        double[][] r = {{c, -s, 0.0}, {s, c, 0.0}, {0.0, 0.0, 1.0}};
        double[] d = {0.125, 0.25, 0.5};
        double[][] a = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    a[i][j] += r[i][k] * d[k] * r[j][k];
                }
            }
        }
        SymmetricEigen3.EigenDecomposition eigen = SymmetricEigen3.eigenvectors(a);
        assertTrue(eigen.isConverged());
        double[] values = eigen.getEigenvalues();
        double[][] vectors = eigen.getEigenvectors();
        assertEquals(0.125, values[0], 1.0e-14);
        assertEquals(0.25, values[1], 1.0e-14);
        assertEquals(0.5, values[2], 1.0e-14);
        // Canonical-sign principal directions: (c,s,0), (-s,c,0), (0,0,1).
        assertEquals(c, vectors[0][0], 1.0e-12, "eigenvector 1 x");
        assertEquals(s, vectors[0][1], 1.0e-12, "eigenvector 1 y");
        assertEquals(-s, vectors[1][0], 1.0e-12, "eigenvector 2 x");
        assertEquals(c, vectors[1][1], 1.0e-12, "eigenvector 2 y");
        assertEquals(1.0, vectors[2][2], 1.0e-12, "eigenvector 3 z");
        // Residual and orthonormality checks.
        for (int i = 0; i < 3; i++) {
            double norm = 0.0;
            for (int k = 0; k < 3; k++) {
                norm += vectors[i][k] * vectors[i][k];
                double av = 0.0;
                for (int j = 0; j < 3; j++) {
                    av += a[k][j] * vectors[i][j];
                }
                assertEquals(values[i] * vectors[i][k], av, 1.0e-12,
                        "A*v must equal lambda*v");
            }
            assertEquals(1.0, norm, 1.0e-12, "eigenvectors are unit vectors");
        }
        // Diagonal input recovers the identity basis in ascending order.
        SymmetricEigen3.EigenDecomposition diag = SymmetricEigen3.eigenvectors(
                new double[][] {{5.0, 0, 0}, {0, 2.0, 0}, {0, 0, 8.0}});
        assertEquals(2.0, diag.getEigenvalues()[0], 1.0e-14);
        assertEquals(1.0, diag.getEigenvectors()[0][1], 1.0e-14);
        assertThrows(IllegalArgumentException.class, () -> SymmetricEigen3.eigenvectors(
                new double[][] {{1, 0.5, 0}, {0.5001, 1, 0}, {0, 0, 1}}));
    }
}
