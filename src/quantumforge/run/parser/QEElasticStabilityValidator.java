/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces rigorous Born mechanical stability criteria and positive-definiteness checks 
 * (Sylvester's leading principal minors criterion) on parsed 6x6 elastic constant matrices 
 * (Cij) to validate crystal lattice mechanical stability (Roadmap #119).
 */
public final class QEElasticStabilityValidator {

    public static final class StabilityResult {
        private final boolean mechanicallyStable;
        private final List<String> diagnostics = new ArrayList<>();

        public StabilityResult(boolean stable) {
            this.mechanicallyStable = stable;
        }

        public boolean isMechanicallyStable() { return this.mechanicallyStable; }
        public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

        public void addDiagnostic(String diagnostic) {
            this.diagnostics.add(diagnostic);
        }
    }

    private QEElasticStabilityValidator() {
        // Utility
    }

    /**
     * Checks if a 6x6 elastic constant tensor C_ij is mechanically stable.
     * Evaluates Sylvester's Criterion: all leading principal minors of the symmetric 
     * 6x6 matrix must be strictly positive.
     */
    public static StabilityResult validateStability(double[][] cij) {
        if (cij == null || cij.length < 6 || cij[0].length < 6) {
            StabilityResult fail = new StabilityResult(false);
            fail.addDiagnostic("Elastic stability check skipped: Incomplete or null 6x6 elastic matrix.");
            return fail;
        }

        StabilityResult result = new StabilityResult(true);
        boolean stable = true;

        // Apply Sylvester's leading principal minors check for sizes 1 to 6
        for (int size = 1; size <= 6; size++) {
            double[][] submatrix = getLeadingSubmatrix(cij, size);
            double det = computeDeterminant(submatrix);
            if (det <= 0.0) {
                stable = false;
                result.addDiagnostic(String.format("Born mechanical stability check FAILED: Leading principal minor of size %d is non-positive (det = %.4f).", size, det));
                break;
            }
        }

        if (stable) {
            result.addDiagnostic("Lattice mechanical stability verified: All leading principal minors are strictly positive (Sylvester's Criterion satisfied).");
            
            // Check specific Born criteria for Cubic systems as a secondary diagnostic:
            // C11 - C12 > 0, C11 + 2*C12 > 0, C44 > 0
            double c11 = cij[0][0];
            double c12 = cij[0][1];
            double c44 = cij[3][3];

            if (c11 - c12 <= 0.0 || c11 + 2.0 * c12 <= 0.0 || c44 <= 0.0) {
                result.addDiagnostic("Note: Cubic symmetry Born criteria are violated. This tensor likely represents a non-cubic crystal or an unstable cubic phase.");
            } else {
                result.addDiagnostic("Cubic Born mechanical criteria satisfied (C11 > |C12|, C11 + 2*C12 > 0, C44 > 0).");
            }
        } else {
            result.addDiagnostic("The crystal phase is mechanically unstable under infinitesimal strain (negative elastic eigenvalues).");
        }

        // Create final result
        StabilityResult finalResult = new StabilityResult(stable);
        for (String diag : result.getDiagnostics()) {
            finalResult.addDiagnostic(diag);
        }
        return finalResult;
    }

    private static double[][] getLeadingSubmatrix(double[][] matrix, int size) {
        double[][] sub = new double[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(matrix[i], 0, sub[i], 0, size);
        }
        return sub;
    }

    /**
     * Compute determinant of a square matrix of size N x N using Gaussian elimination.
     */
    public static double computeDeterminant(double[][] matrix) {
        int n = matrix.length;
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
        }

        double det = 1.0;
        for (int i = 0; i < n; i++) {
            // Pivot selection
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
                det = -det;
            }

            if (Math.abs(a[i][i]) < 1.0e-12) {
                return 0.0; // singular
            }

            det *= a[i][i];

            for (int j = i + 1; j < n; j++) {
                double factor = a[j][i] / a[i][i];
                for (int k = i; k < n; k++) {
                    a[j][k] -= factor * a[i][k];
                }
            }
        }
        return det;
    }
}
