/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Batch 153 extension coverage for {@link SymmetricEigen3}: beyond value
 * retrieval, each decomposition is verified by RECONSTRUCTION
 * (A == V.Lambda.V^T to tolerance) and by the directional-surface identity
 * n^T.A.n = lambda_i when n is eigenvector i - the exact identity the tensor
 * viewer (Roadmap #125) renders. Degenerate and 2D-embedded tensors get
 * their analytic eigensystems pinned.
 */
class SymmetricEigen3ResidualTest {

    private static final double TOL = 1.0e-9;

    private static double[][] reconstruct(SymmetricEigen3.EigenDecomposition d) {
        double[] w = d.getEigenvalues();
        double[][] v = d.getEigenvectors(); // row i = eigenvector i
        double[][] a = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sum = 0.0;
                for (int k = 0; k < 3; k++) {
                    sum += v[k][i] * w[k] * v[k][j];
                }
                a[i][j] = sum;
            }
        }
        return a;
    }

    private static double directional(double[][] a, double[] n) {
        double[] an = new double[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                an[i] += a[i][j] * n[j];
            }
        }
        double out = 0.0;
        for (int i = 0; i < 3; i++) {
            out += n[i] * an[i];
        }
        return out;
    }

    private static void assertResidualSmall(double[][] a, SymmetricEigen3.EigenDecomposition d) {
        double[][] rec = reconstruct(d);
        double worst = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                worst = Math.max(worst, Math.abs(rec[i][j] - a[i][j]));
            }
        }
        assertTrue(worst < 1.0e-8, "reconstruction |V.Lambda.V^T - A|_inf = " + worst);
    }

    @Test
    void genericTensorReconstructsWithinTolerance() {
        double[][] a = {{2.0, 0.4, -0.1}, {0.4, 3.0, 0.25}, {-0.1, 0.25, 1.5}};
        SymmetricEigen3.EigenDecomposition d = SymmetricEigen3.eigenvectors(a);
        assertTrue(d.isConverged(), "Jacobi sweep converged in " + d.getSweeps() + " sweeps");
        assertResidualSmall(a, d);
        // trace and determinant are similarity invariants
        double[] w = d.getEigenvalues();
        assertEquals(a[0][0] + a[1][1] + a[2][2], w[0] + w[1] + w[2], TOL,
                "trace is a similarity invariant");
        double detA = 2.0 * (3.0 * 1.5 - 0.25 * 0.25) - 0.4 * (0.4 * 1.5 + 0.1 * 0.25)
                + (-0.1) * (0.4 * 0.25 + 0.1 * 3.0);
        assertEquals(detA, w[0] * w[1] * w[2], 1.0e-8, "determinant = eigenvalue product");
    }

    @Test
    void directionalSurfaceHitsEigenvaluesAtEigenvectors() {
        double[][] a = {{2.0, 0.4, -0.1}, {0.4, 3.0, 0.25}, {-0.1, 0.25, 1.5}};
        SymmetricEigen3.EigenDecomposition d = SymmetricEigen3.eigenvectors(a);
        double[] w = d.getEigenvalues();
        double[][] v = d.getEigenvectors();
        for (int i = 0; i < 3; i++) {
            double dir = directional(a, v[i]);
            assertEquals(w[i], dir, 1.0e-9,
                    "n^T.A.n equals lambda_i at eigenvector " + i
                            + " - the viewer's directional surface anchor");
        }
        // an orthonormal basis: V.V^T == I
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double dot = 0.0;
                for (int k = 0; k < 3; k++) {
                    dot += v[i][k] * v[j][k];
                }
                assertEquals(i == j ? 1.0 : 0.0, dot, 1.0e-9,
                        "eigenvectors stay orthonormal (" + i + "," + j + ")");
            }
        }
    }

    @Test
    void knownDiagonalTensorReturnsItselfSorted() {
        double[][] a = {{9.0, 0.0, 0.0}, {0.0, -3.0, 0.0}, {0.0, 0.0, 2.5}};
        SymmetricEigen3.EigenDecomposition d = SymmetricEigen3.eigenvectors(a);
        assertTrue(d.isConverged());
        assertEquals(-3.0, d.getEigenvalues()[0], TOL);
        assertEquals(2.5, d.getEigenvalues()[1], TOL);
        assertEquals(9.0, d.getEigenvalues()[2], TOL);
        assertResidualSmall(a, d);
    }

    @Test
    void planarRotationEmbeddedIn3DHasAnalyticSpectrum() {
        // 2D rotation of diag(5,1) by theta around z: eigenvalues {5,1,lambda_z}
        double theta = Math.PI / 6.0;
        double c = Math.cos(theta);
        double s = Math.sin(theta);
        double[][] a = {
                {5.0 * c * c + 1.0 * s * s, (5.0 - 1.0) * s * c, 0.0},
                {(5.0 - 1.0) * s * c, 5.0 * s * s + 1.0 * c * c, 0.0},
                {0.0, 0.0, -2.0}};
        SymmetricEigen3.EigenDecomposition d = SymmetricEigen3.eigenvectors(a);
        assertTrue(d.isConverged());
        assertEquals(-2.0, d.getEigenvalues()[0], 1.0e-9, "z eigenvalue survives the 2D rotation");
        assertEquals(1.0, d.getEigenvalues()[1], 1.0e-9);
        assertEquals(5.0, d.getEigenvalues()[2], 1.0e-9);
        // the z-axis stays an eigenvector of the z eigenvalue
        double[] ez = {0.0, 0.0, 1.0};
        assertEquals(-2.0, directional(a, ez), 1.0e-12);
        assertResidualSmall(a, d);
    }

    @Test
    void degenerateRepeatedEigenvalueStillYieldsOrthonormalBasis() {
        double[][] a = {{2.0, 1.0, 0.0}, {1.0, 2.0, 0.0}, {0.0, 0.0, 3.0}};
        // eigenvalues: 1, 3, 3 (degenerate pair in x/y plane and z)
        SymmetricEigen3.EigenDecomposition d = SymmetricEigen3.eigenvectors(a);
        assertTrue(d.isConverged());
        assertEquals(1.0, d.getEigenvalues()[0], 1.0e-9);
        assertEquals(3.0, d.getEigenvalues()[1], 1.0e-9);
        assertEquals(3.0, d.getEigenvalues()[2], 1.0e-9);
        assertResidualSmall(a, d);
    }
}
