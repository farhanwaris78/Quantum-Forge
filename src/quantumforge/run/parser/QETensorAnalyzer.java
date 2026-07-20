/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Engineering-modulus derivation from a symmetric positive-definite 6x6 elastic
 * stiffness tensor in Voigt convention (Roadmap #125 data layer).
 *
 * <p>Voigt averages come from the stiffness matrix directly; Reuss averages
 * need the compliance matrix {@code S = C^-1}, computed by Gauss-Jordan
 * elimination with partial pivoting. Hill averages are the arithmetic means.
 * Every unit stays exactly as printed by the producing engine (thermo_pw
 * prints kbar by default); derived Young's modulus and the Pugh ratio follow
 * the input units. Non-symmetric or non-positive-definite tensors fail closed.</p>
 */
public final class QETensorAnalyzer {

    private static final double SYMMETRY_TOLERANCE = 1.0e-8;

    /** Derived polycrystalline moduli in the input units of the Cij tensor. */
    public static final class ElasticModuli {
        private final double bulkVoigt;
        private final double bulkReuss;
        private final double bulkHill;
        private final double shearVoigt;
        private final double shearReuss;
        private final double shearHill;
        private final double youngHill;
        private final double poissonHill;
        private final double pughRatio;
        private final double cauchyPressure;
        private final double universalAnisotropy;

        ElasticModuli(double bv, double br, double gv, double gr, double cauchy) {
            this.bulkVoigt = bv;
            this.bulkReuss = br;
            this.bulkHill = 0.5 * (bv + br);
            this.shearVoigt = gv;
            this.shearReuss = gr;
            this.shearHill = 0.5 * (gv + gr);
            this.youngHill = 9.0 * this.bulkHill * this.shearHill
                    / (3.0 * this.bulkHill + this.shearHill);
            this.poissonHill = (3.0 * this.bulkHill - 2.0 * this.shearHill)
                    / (2.0 * (3.0 * this.bulkHill + this.shearHill));
            this.pughRatio = this.shearHill > 0.0 ? this.bulkHill / this.shearHill
                    : Double.NaN;
            this.cauchyPressure = cauchy;
            this.universalAnisotropy = gr > 0.0 && br > 0.0
                    ? 5.0 * (gv / gr) + (bv / br) - 6.0 : Double.NaN;
        }

        public double getBulkVoigt() { return this.bulkVoigt; }
        public double getBulkReuss() { return this.bulkReuss; }
        public double getBulkHill() { return this.bulkHill; }
        public double getShearVoigt() { return this.shearVoigt; }
        public double getShearReuss() { return this.shearReuss; }
        public double getShearHill() { return this.shearHill; }
        public double getYoungsModulusHill() { return this.youngHill; }
        public double getPoissonRatioHill() { return this.poissonHill; }
        public double getPughRatio() { return this.pughRatio; }
        public double getCauchyPressure() { return this.cauchyPressure; }
        public double getUniversalAnisotropy() { return this.universalAnisotropy; }
    }

    private QETensorAnalyzer() {
        // Utility.
    }

    /**
     * Computes Voigt/Reuss/Hill moduli from a 6x6 stiffness tensor.
     * The symmetric input must be positive definite (Sylvester leading-minors),
     * which is the same requirement as mechanical Born stability.
     */
    public static OperationResult<ElasticModuli> analyzeElastic(double[][] cij) {
        if (cij == null || cij.length < 6) {
            return OperationResult.failed("TENSOR_SHAPE",
                    "A full 6x6 Voigt stiffness tensor is required.", null);
        }
        for (double[] row : cij) {
            if (row == null || row.length < 6) {
                return OperationResult.failed("TENSOR_SHAPE",
                        "A full 6x6 Voigt stiffness tensor is required.", null);
            }
            for (double value : row) {
                if (!Double.isFinite(value)) {
                    return OperationResult.failed("TENSOR_NAN",
                            "Tensor components must be finite numbers.", null);
                }
            }
        }
        double scale = 0.0;
        for (int i = 0; i < 6; i++) {
            scale = Math.max(scale, Math.abs(cij[i][i]));
        }
        if (!(scale > 0.0)) {
            return OperationResult.failed("TENSOR_ZERO",
                    "The stiffness tensor is all zero.", null);
        }
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) {
                if (Math.abs(cij[i][j] - cij[j][i]) > SYMMETRY_TOLERANCE * scale) {
                    return OperationResult.failed("TENSOR_ASYMMETRIC",
                            String.format(Locale.ROOT,
                                    "C[%d][%d]=%.8g and C[%d][%d]=%.8g differ beyond the "
                                    + "symmetry tolerance; the Voigt tensor must be symmetric.",
                                    i, j, cij[i][j], j, i, cij[j][i]), null);
                }
            }
        }
        for (int size = 1; size <= 6; size++) {
            double[][] minor = new double[size][size];
            for (int i = 0; i < size; i++) {
                System.arraycopy(cij[i], 0, minor[i], 0, size);
            }
            double det = QEElasticStabilityValidator.computeDeterminant(minor);
            if (!(det > 0.0)) {
                return OperationResult.failed("TENSOR_NOT_SPD",
                        "Leading principal minor " + size + " is non-positive (det=" + det
                                + "); the tensor is not positive definite, so Reuss moduli "
                                + "cannot be derived. Check mechanical stability first.", null);
            }
        }

        double c123 = cij[0][0] + cij[1][1] + cij[2][2];
        double cOff = cij[0][1] + cij[1][2] + cij[0][2];
        double cShear = cij[3][3] + cij[4][4] + cij[5][5];
        double bulkVoigt = (c123 + 2.0 * cOff) / 9.0;
        double shearVoigt = (c123 - cOff + 3.0 * cShear) / 15.0;

        double[][] compliance = invert6(cij);
        double s123 = compliance[0][0] + compliance[1][1] + compliance[2][2];
        double sOff = compliance[0][1] + compliance[1][2] + compliance[0][2];
        double sShear = compliance[3][3] + compliance[4][4] + compliance[5][5];
        double bulkReuss = 1.0 / (s123 + 2.0 * sOff);
        double shearReuss = 15.0 / (4.0 * s123 - 4.0 * sOff + 3.0 * sShear);

        double cauchy = cij[0][1] - cij[3][3];
        return OperationResult.success("TENSOR_OK", "Voigt/Reuss/Hill moduli derived.",
                new ElasticModuli(bulkVoigt, bulkReuss, shearVoigt, shearReuss, cauchy));
    }

    /** Gauss-Jordan inversion with partial pivoting; caller guarantees SPD. */
    private static double[][] invert6(double[][] matrix) {
        int n = 6;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }
        for (int column = 0; column < n; column++) {
            int pivotRow = column;
            for (int row = column + 1; row < n; row++) {
                if (Math.abs(aug[row][column]) > Math.abs(aug[pivotRow][column])) {
                    pivotRow = row;
                }
            }
            double[] tmp = aug[column];
            aug[column] = aug[pivotRow];
            aug[pivotRow] = tmp;
            double pivot = aug[column][column];
            if (Math.abs(pivot) < 1.0e-300) {
                throw new ArithmeticException("Singular matrix in compliance inversion");
            }
            for (int j = 0; j < 2 * n; j++) {
                aug[column][j] /= pivot;
            }
            for (int row = 0; row < n; row++) {
                if (row == column) {
                    continue;
                }
                double factor = aug[row][column];
                if (factor == 0.0) {
                    continue;
                }
                for (int j = 0; j < 2 * n; j++) {
                    aug[row][j] -= factor * aug[column][j];
                }
            }
        }
        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(aug[i], n, inverse[i], 0, n);
        }
        return inverse;
    }

    /**
     * Returns the 6x6 Voigt engineering compliance tensor S = C^-1 (Roadmap #119
     * data layer). The stiffness is fully validated (shape, finite, symmetric,
     * SPD) exactly as in {@link #analyzeElastic} before inversion.
     */
    public static OperationResult<double[][]> complianceMatrix(double[][] cij) {
        OperationResult<ElasticModuli> check = analyzeElastic(cij);
        if (!check.isSuccess()) {
            return OperationResult.failed(check.getCode(), check.getMessage(), null);
        }
        return OperationResult.success("TENSOR_OK",
                "Compliance tensor inverted from the SPD stiffness.", invert6(cij));
    }

    /**
     * Directional Young's modulus E(l) = 1 / S'11 for a direction l = (l1,l2,l3)
     * in the tensor's own printed frame, using the engineering-Voigt compliance:
     * <pre>
     * S'11 = s11 l1^4 + s22 l2^4 + s33 l3^4
     *      + (2 s12 + s66) l1^2 l2^2 + (2 s13 + s55) l1^2 l3^2
     *      + (2 s23 + s44) l2^2 l3^2.
     * </pre>
     * The isotropic collapse (E independent of direction) is machine-exact. A
     * zero direction or a non-positive S'11 (impossible for SPD input but
     * guarded anyway) fails closed.
     */
    public static OperationResult<Double> youngsModulusInDirection(double[][] compliance,
            double l1, double l2, double l3) {
        if (compliance == null || compliance.length < 6) {
            return OperationResult.failed("TENSOR_SHAPE",
                    "A 6x6 compliance tensor is required.", null);
        }
        for (double[] row : compliance) {
            if (row == null || row.length < 6) {
                return OperationResult.failed("TENSOR_SHAPE",
                        "A 6x6 compliance tensor is required.", null);
            }
            for (double value : row) {
                if (!Double.isFinite(value)) {
                    return OperationResult.failed("TENSOR_NAN",
                            "Compliance components must be finite numbers.", null);
                }
            }
        }
        double norm = Math.sqrt(l1 * l1 + l2 * l2 + l3 * l3);
        if (!(norm > 0.0) || !Double.isFinite(norm)) {
            return OperationResult.failed("DIRECTION_ZERO",
                    "The direction vector must be finite and non-zero.", null);
        }
        double u1 = l1 / norm;
        double u2 = l2 / norm;
        double u3 = l3 / norm;
        double s = compliance[0][0] * u1 * u1 * u1 * u1
                + compliance[1][1] * u2 * u2 * u2 * u2
                + compliance[2][2] * u3 * u3 * u3 * u3
                + (2.0 * compliance[0][1] + compliance[5][5]) * u1 * u1 * u2 * u2
                + (2.0 * compliance[0][2] + compliance[4][4]) * u1 * u1 * u3 * u3
                + (2.0 * compliance[1][2] + compliance[3][3]) * u2 * u2 * u3 * u3;
        if (!(s > 0.0) || !Double.isFinite(s)) {
            return OperationResult.failed("DIRECTION_NONPOSITIVE",
                    "S'11 is non-positive in this direction; the compliance tensor is not "
                            + "physically invertible here (check the stiffness input).", null);
        }
        return OperationResult.success("DIRECTION_OK", "Directional modulus computed.",
                1.0 / s);
    }
}
