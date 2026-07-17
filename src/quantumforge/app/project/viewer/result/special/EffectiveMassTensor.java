/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */

package quantumforge.app.project.viewer.result.special;

import java.util.Objects;
import quantumforge.com.math.Matrix3D;

/**
 * Mathematically rigorous effective-mass tensor solver.
 * Fits 3D reciprocal-space band energies E(k) to a quadratic paraboloid using
 * multi-variable linear least-squares regression, extracting the full 3x3 
 * effective mass tensor as the inverse Hessian matrix (Roadmap #159).
 */
public final class EffectiveMassTensor {

    private EffectiveMassTensor() {
        // Utility
    }

    /**
     * Performs a 3D quadratic least-squares fit on K-points and energies:
     * E(kx, ky, kz) = c0 + c1*kx^2 + c2*ky^2 + c3*kz^2 + c4*kx*ky + c5*kx*kz + c6*ky*kz
     * 
     * Returns the 3x3 effective mass tensor m* = H^-1 (in atomic units).
     * 
     * @param kpoints k-point coordinates array [M][3] in inverse Bohr (1/bohr)
     * @param energies band energies array [M] in Rydberg (Ry)
     */
    public static double[][] calculateEffectiveMassTensor(double[][] kpoints, double[] energies) {
        if (kpoints == null || energies == null || kpoints.length < 7 || kpoints.length != energies.length) {
            return null;
        }

        int M = kpoints.length;
        int numCoeffs = 7; // [1, kx^2, ky^2, kz^2, kx*ky, kx*kz, ky*kz]

        // Construct design matrix A [M][7]
        double[][] A = new double[M][numCoeffs];
        for (int i = 0; i < M; i++) {
            double kx = kpoints[i][0];
            double ky = kpoints[i][1];
            double kz = kpoints[i][2];

            A[i][0] = 1.0;
            A[i][1] = kx * kx;
            A[i][2] = ky * ky;
            A[i][3] = kz * kz;
            A[i][4] = kx * ky;
            A[i][5] = kx * kz;
            A[i][6] = ky * kz;
        }

        // Formulate Normal Equations: (A^T * A) * c = A^T * b
        double[][] ATA = new double[numCoeffs][numCoeffs];
        double[] ATb = new double[numCoeffs];

        for (int i = 0; i < numCoeffs; i++) {
            for (int j = 0; j < numCoeffs; j++) {
                double sum = 0.0;
                for (int k = 0; k < M; k++) {
                    sum += A[k][i] * A[k][j];
                }
                ATA[i][j] = sum;
            }

            double sumb = 0.0;
            for (int k = 0; k < M; k++) {
                sumb += A[k][i] * energies[k];
            }
            ATb[i] = sumb;
        }

        // Solve the 7x7 system using Gaussian elimination with partial pivoting
        double[] coeff = solveLinearSystem(ATA, ATb);
        if (coeff == null) {
            return null; // Singular or ill-conditioned system
        }

        // Extract Hessian elements:
        // H_xx = 2*c1, H_yy = 2*c2, H_zz = 2*c3
        // H_xy = c4,   H_xz = c5,   H_yz = c6
        double[][] Hessian = new double[3][3];
        Hessian[0][0] = 2.0 * coeff[1];
        Hessian[1][1] = 2.0 * coeff[2];
        Hessian[2][2] = 2.0 * coeff[3];
        
        Hessian[0][1] = coeff[4];
        Hessian[1][0] = coeff[4];
        
        Hessian[0][2] = coeff[5];
        Hessian[2][0] = coeff[5];
        
        Hessian[1][2] = coeff[6];
        Hessian[2][1] = coeff[6];

        // The effective mass tensor is the inverse of the Hessian matrix
        return Matrix3D.inverse(Hessian);
    }

    private static double[] solveLinearSystem(double[][] matrix, double[] rhs) {
        int n = matrix.length;
        double[][] a = new double[n][n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
        }
        System.arraycopy(rhs, 0, b, 0, n);

        for (int i = 0; i < n; i++) {
            int pivot = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(a[j][i]) > Math.abs(a[pivot][i])) {
                    pivot = j;
                }
            }

            if (pivot != i) {
                double[] temp = a[i];
                a[i] = a[pivot];
                a[pivot] = temp;

                double t = b[i];
                b[i] = b[pivot];
                b[pivot] = t;
            }

            if (Math.abs(a[i][i]) < 1.0e-12) {
                return null; // Singular
            }

            for (int j = i + 1; j < n; j++) {
                double factor = a[j][i] / a[i][i];
                b[j] -= factor * b[i];
                for (int k = i; k < n; k++) {
                    a[j][k] -= factor * a[i][k];
                }
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += a[i][j] * x[j];
            }
            x[i] = (b[i] - sum) / a[i][i];
        }
        return x;
    }
}
