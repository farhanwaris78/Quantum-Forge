/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

/**
 * Cyclic-Jacobi eigenvalues of a real symmetric 3x3 matrix.
 *
 * <p>Used for small physical tensors (effective masses, inertial/stress
 * eigensystems) where a full LAPACK dependency would be unjustified. The
 * routine sweeps until the largest off-diagonal magnitude drops below a
 * relative tolerance or the sweep budget is exhausted, and reports its
 * convergence state so callers can fail closed. Eigenvectors are deliberately
 * not exposed yet; add them when a consumer needs axes, with the same honesty
 * about converged tolerances.</p>
 */
public final class SymmetricEigen3 {

    private static final int MAX_SWEEPS = 100;
    private static final double RELATIVE_TOLERANCE = 1.0e-13;

    /** Eigenvalue outcome: sorted eigenvalues plus the convergence verdict. */
    public static final class EigenResult {
        private final double[] eigenvalues;
        private final boolean converged;
        private final int sweeps;

        private EigenResult(double[] eigenvalues, boolean converged, int sweeps) {
            this.eigenvalues = eigenvalues;
            this.converged = converged;
            this.sweeps = sweeps;
        }

        /** Eigenvalues sorted ascending. */
        public double[] getEigenvalues() { return this.eigenvalues.clone(); }
        public boolean isConverged() { return this.converged; }
        public int getSweeps() { return this.sweeps; }
    }

    private SymmetricEigen3() {
        // Utility
    }

    /**
     * Computes the eigenvalues of a symmetric 3x3 matrix. Throws
     * {@link IllegalArgumentException} for wrong shape, non-finite entries, or
     * asymmetry beyond the relative tolerance.
     */
    public static EigenResult eigenvalues(double[][] matrix) {
        if (matrix == null || matrix.length < 3) {
            throw new IllegalArgumentException("A 3x3 matrix is required.");
        }
        double[][] a = new double[3][3];
        double scale = 0.0;
        for (int i = 0; i < 3; i++) {
            if (matrix[i] == null || matrix[i].length < 3) {
                throw new IllegalArgumentException("A 3x3 matrix is required.");
            }
            for (int j = 0; j < 3; j++) {
                if (!Double.isFinite(matrix[i][j])) {
                    throw new IllegalArgumentException("Matrix entries must be finite.");
                }
                a[i][j] = matrix[i][j];
                scale = Math.max(scale, Math.abs(matrix[i][j]));
            }
        }
        double tolerance = RELATIVE_TOLERANCE * Math.max(1.0, scale);
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (Math.abs(a[i][j] - a[j][i]) > tolerance) {
                    throw new IllegalArgumentException(
                            "The matrix must be symmetric (off-diagonal mismatch at " + i + ","
                                    + j + ").");
                }
            }
        }

        boolean converged = false;
        int sweep = 0;
        for (; sweep < MAX_SWEEPS && !converged; sweep++) {
            double off = maxOffDiagonal(a);
            if (off <= tolerance) {
                converged = true;
                break;
            }
            for (int p = 0; p < 3; p++) {
                for (int q = p + 1; q < 3; q++) {
                    if (Math.abs(a[p][q]) <= tolerance) {
                        continue;
                    }
                    rotate(a, p, q);
                }
            }
        }
        double[] values = {a[0][0], a[1][1], a[2][2]};
        java.util.Arrays.sort(values);
        return new EigenResult(values, converged, sweep);
    }

    private static double maxOffDiagonal(double[][] a) {
        return Math.max(Math.abs(a[0][1]), Math.max(Math.abs(a[0][2]), Math.abs(a[1][2])));
    }

    /** One Jacobi rotation that annihilates a[p][q] (symmetric, stable t formula). */
    private static void rotate(double[][] a, int p, int q) {
        double app = a[p][p];
        double aqq = a[q][q];
        double apq = a[p][q];
        if (apq == 0.0) {
            return;
        }
        double theta = (aqq - app) / (2.0 * apq);
        double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
        if (theta == 0.0) {
            t = 1.0;
        }
        double c = 1.0 / Math.sqrt(t * t + 1.0);
        double s = t * c;
        for (int k = 0; k < 3; k++) {
            double akp = a[k][p];
            double akq = a[k][q];
            a[k][p] = c * akp - s * akq;
            a[k][q] = s * akp + c * akq;
        }
        for (int k = 0; k < 3; k++) {
            double apk = a[p][k];
            double aqk = a[q][k];
            a[p][k] = c * apk - s * aqk;
            a[q][k] = s * apk + c * aqk;
        }
        // Re-impose exact symmetry after the two-sided rotation.
        a[p][q] = a[q][p] = 0.0;
        a[0][1] = a[1][0] = 0.5 * (a[0][1] + a[1][0]);
        a[0][2] = a[2][0] = 0.5 * (a[0][2] + a[2][0]);
        a[1][2] = a[2][1] = 0.5 * (a[1][2] + a[2][1]);
    }
}
