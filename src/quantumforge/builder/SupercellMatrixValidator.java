/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed validation of a general integer 3x3 supercell transformation
 * (Roadmap #81 data layer): rows of integers with bounded entries, an exactly
 * computed integer determinant (long arithmetic, never floating point), a
 * minimum multiplicity of 1 and a maximum preview multiplicity. Handedness
 * flips (negative determinant) are refused - an inversion changes chirality
 * and must be a conscious different operation, not a typo of a supercell.
 */
public final class SupercellMatrixValidator {

    /** Entry bound keeps atom-count previews honest and det computation exact. */
    public static final int MAX_ENTRY = 8;
    /** Multiplication bound for previews; larger cells belong to full builders. */
    public static final long MAX_DETERMINANT = 64L;

    /** Validated transformation: integer matrix plus its exact determinant. */
    public static final class SupercellTransform {
        private final int[][] matrix;
        private final long determinant;

        SupercellTransform(int[][] matrix, long determinant) {
            this.matrix = new int[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(matrix[i], 0, this.matrix[i], 0, 3);
            }
            this.determinant = determinant;
        }

        public int[][] getMatrix() {
            int[][] copy = new int[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(this.matrix[i], 0, copy[i], 0, 3);
            }
            return copy;
        }

        public long getDeterminant() { return this.determinant; }

        /**
         * Row-vector convention: new lattice row i = sum_j M[i][j] * a_j with
         * the direct lattice rows a_j in Angstrom.
         */
        public double[][] applyToLattice(double[][] lattice) {
            double[][] result = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    double sum = 0.0;
                    for (int j = 0; j < 3; j++) {
                        sum += this.matrix[i][j] * lattice[j][axis];
                    }
                    result[i][axis] = sum;
                }
            }
            return result;
        }
    }

    private SupercellMatrixValidator() { }

    /**
     * Parses "r1c1 r1c2 r1c3; r2c1 r2c2 r2c3; r3c1 r3c2 r3c3" (commas also
     * accepted between entries). Codes: SUPERCELL_SYNTAX, SUPERCELL_BOUND,
     * SUPERCELL_SINGULAR, SUPERCELL_HANDEDNESS, SUPERCELL_DET.
     */
    public static OperationResult<SupercellTransform> validate(String spec) {
        String text = spec == null ? "" : spec.trim();
        if (text.isEmpty()) {
            return OperationResult.failed("SUPERCELL_SYNTAX",
                    "Supply 9 integer matrix entries as three rows separated by ';'.",
                    null);
        }
        String[] rows = text.split(";");
        if (rows.length != 3) {
            return OperationResult.failed("SUPERCELL_SYNTAX",
                    "Exactly three matrix rows separated by ';' are required; got "
                            + rows.length + ".", null);
        }
        int[][] matrix = new int[3][3];
        for (int i = 0; i < 3; i++) {
            String[] cells = rows[i].trim().split("[,\\s]+");
            if (cells.length != 3) {
                return OperationResult.failed("SUPERCELL_SYNTAX",
                        "Row " + (i + 1) + " must carry exactly 3 integer entries; got \""
                                + rows[i].trim() + "\".", null);
            }
            for (int j = 0; j < 3; j++) {
                try {
                    matrix[i][j] = Integer.parseInt(cells[j]);
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("SUPERCELL_SYNTAX",
                            "Entry \"" + cells[j] + "\" in row " + (i + 1)
                                    + " is not an integer; supercell matrices are integer "
                                    + "by definition.", null);
                }
                if (Math.abs(matrix[i][j]) > MAX_ENTRY) {
                    return OperationResult.failed("SUPERCELL_BOUND",
                            "Entry " + matrix[i][j] + " exceeds the preview bound |m| <= "
                                    + MAX_ENTRY + "; larger transformations belong to the "
                                    + "full supercell builder.", null);
                }
            }
        }
        // Exact integer determinant in long arithmetic.
        long det = matrix[0][0] * (matrix[1][1] * (long) matrix[2][2]
                - matrix[1][2] * (long) matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * (long) matrix[2][2]
                        - matrix[1][2] * (long) matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * (long) matrix[2][1]
                        - matrix[1][1] * (long) matrix[2][0]);
        if (det == 0L) {
            return OperationResult.failed("SUPERCELL_SINGULAR",
                    "The matrix is singular (det = 0); it would collapse the cell - no "
                            + "supercell exists.", null);
        }
        if (det < 0L) {
            return OperationResult.failed("SUPERCELL_HANDEDNESS",
                    "The matrix has a negative determinant (" + det + "); a handedness "
                            + "flip changes chirality and is refused here - use a "
                            + "dedicated reflection/mirror operation instead.", null);
        }
        if (det > MAX_DETERMINANT) {
            return OperationResult.failed("SUPERCELL_DET",
                    String.format(Locale.ROOT,
                            "Multiplicity %d exceeds the preview bound %d; preview only up "
                                    + "to that size here.", det, MAX_DETERMINANT), null);
        }
        return OperationResult.success("SUPERCELL_OK",
                "Valid supercell matrix with exact multiplicity " + det + ".",
                new SupercellTransform(matrix, det));
    }
}
