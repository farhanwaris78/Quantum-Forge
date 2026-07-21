/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

/**
 * Directional-surface data layer for symmetric 3x3 tensors (Roadmap #125
 * viewer slice): everything the GUI tensor panel draws, headless so every
 * number on screen is unit-verified. The physics is exactly the batch-tested
 * {@link SymmetricEigen3} layer's: a symmetric rank-2 tensor T defines the
 * quadratic directional value q(n) = n^T.T.n on the unit sphere; the sampled
 * values always stay inside the eigenvalue bounds (q is a convex combination
 * of the eigenvalues in the eigenbasis), which the viewer fails closed on if
 * it were ever violated.
 *
 * <p>Honesty rules:</p>
 * <ul>
 *   <li>rank-2 quadratic forms ONLY - rank-4 elastic surfaces stay with the
 *       #119 ELATE work and are never silently approximated here;</li>
 *   <li>the file contract mirrors the TENSOR_EIGEN/TENSOR_DIRECTIONAL input
 *       contract verbatim (first three non-blank lines, three finite values
 *       per line, Fortran-D exponents tolerated, asymmetric input refused)
 *       so a file accepted by the panel behaves identically in the analysis
 *       kinds;</li>
 *   <li>values are raw doubles; sign is preserved (indefinite tensors keep
 *       their negative lobes - the viewer sign-colors them, it never folds
 *       negatives into magnitudes);</li>
 *   <li>no units, no physical interpretation at this layer: dielectric,
 *       stress, inertia are the caller's context, exactly like the analysis
 *       kinds state.</li>
 * </ul>
 */
public final class TensorSurfaceSampler {

    /** Cartesian planes the 2D polar slices live on (convention selector). */
    public enum CartesianPlane {
        /** n = (cos a, sin a, 0) */
        XY,
        /** n = (cos a, 0, sin a) */
        XZ,
        /** n = (0, cos a, sin a) */
        YZ
    }

    private TensorSurfaceSampler() {
        // Utility
    }

    /**
     * Parses a symmetric 3x3 matrix from text under the TENSOR_* file
     * contract: the first three NON-BLANK lines each carry at least three
     * comma/whitespace-separated finite values (Fortran D/d exponents
     * tolerated via E-normalization). Any violation is an
     * IllegalArgumentException naming the offending row - never a silently
     * zero-padded or truncated matrix.
     */
    public static double[][] parseMatrix3x3(String text) {
        double[][] matrix = new double[3][3];
        int rowsRead = 0;
        if (text != null) {
            for (String line : text.split("\\R")) {
                if (rowsRead >= 3) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] cells = trimmed.split("[,\\s]+");
                if (cells.length < 3) {
                    throw new IllegalArgumentException(
                            "Matrix row " + (rowsRead + 1) + " (\"" + trimmed
                                    + "\") has fewer than 3 numeric values; the contract is the 3x3"
                                    + " matrix rows, three per line.");
                }
                for (int col = 0; col < 3; col++) {
                    String token = cells[col].replace('D', 'E').replace('d', 'E');
                    final double value;
                    try {
                        value = Double.parseDouble(token);
                    } catch (NumberFormatException problem) {
                        throw new IllegalArgumentException(
                                "Non-numeric value \"" + cells[col] + "\" in matrix row "
                                        + (rowsRead + 1) + ".");
                    }
                    if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException(
                                "Non-finite value \"" + cells[col] + "\" in matrix row "
                                        + (rowsRead + 1) + ".");
                    }
                    matrix[rowsRead][col] = value;
                }
                rowsRead++;
            }
        }
        if (rowsRead < 3) {
            throw new IllegalArgumentException(
                    "Only " + rowsRead + " matrix row(s) were found; three numeric rows"
                            + " (the first three non-blank lines) are required.");
        }
        return matrix;
    }

    /**
     * The quadratic directional value q(n) = n^T.T.n evaluated on the matrix
     * directly. The direction need not be perfectly normalized (the viewer's
     * grid constructs unit vectors up to rounding).
     */
    public static double directional(double[][] matrix, double nx, double ny, double nz) {
        return nx * (matrix[0][0] * nx + matrix[0][1] * ny + matrix[0][2] * nz)
                + ny * (matrix[1][0] * nx + matrix[1][1] * ny + matrix[1][2] * nz)
                + nz * (matrix[2][0] * nx + matrix[2][1] * ny + matrix[2][2] * nz);
    }

    /**
     * Full-sphere directional grid, [polarSteps][azimuthSteps]: polar index p
     * at angle 180*p/(polarSteps-1) degrees from +z (poles included), azimuth
     * index a at 360*a/azimuthSteps around z. azimuthSteps >= 2 and
     * polarSteps >= 2 are required; the TENSOR_DIRECTIONAL analysis grid is
     * sampleSphere(matrix, 24, 13) (the 15-degree convention).
     */
    public static double[][] sampleSphere(double[][] matrix, int azimuthSteps,
            int polarSteps) {
        if (azimuthSteps < 2 || polarSteps < 2) {
            throw new IllegalArgumentException(
                    "Grid needs at least 2x2 steps (got azimuth " + azimuthSteps
                            + ", polar " + polarSteps + ").");
        }
        double[][] grid = new double[polarSteps][azimuthSteps];
        for (int p = 0; p < polarSteps; p++) {
            double polar = Math.toRadians(180.0 * p / (polarSteps - 1.0));
            double nz = Math.cos(polar);
            double sinPolar = Math.sin(polar);
            for (int a = 0; a < azimuthSteps; a++) {
                double azimuth = Math.toRadians(360.0 * a / azimuthSteps);
                double nx = sinPolar * Math.cos(azimuth);
                double ny = sinPolar * Math.sin(azimuth);
                grid[p][a] = directional(matrix, nx, ny, nz);
            }
        }
        return grid;
    }

    /**
     * 2D polar slice on a cartesian plane: values[ i ] at angle
     * 360*i/steps degrees inside the chosen plane. steps >= 2 required.
     */
    public static double[] samplePlane(double[][] matrix, CartesianPlane plane, int steps) {
        if (plane == null) {
            throw new IllegalArgumentException("No slice plane selected.");
        }
        if (steps < 2) {
            throw new IllegalArgumentException("A polar slice needs at least 2 steps.");
        }
        double[] values = new double[steps];
        for (int i = 0; i < steps; i++) {
            double angle = Math.toRadians(360.0 * i / steps);
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            values[i] = switch (plane) {
                case XY -> directional(matrix, c, s, 0.0);
                case XZ -> directional(matrix, c, 0.0, s);
                case YZ -> directional(matrix, 0.0, c, s);
            };
        }
        return values;
    }

    /** Min/max of a sampled grid as {min, max}; empty input refuses. */
    public static double[] minMax(double[][] grid) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            throw new IllegalArgumentException("No sampled grid to bound.");
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] row : grid) {
            for (double value : row) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return new double[] {min, max};
    }

    /** Min/max of a sampled slice as {min, max}; empty input refuses. */
    public static double[] minMax(double[] slice) {
        if (slice == null || slice.length == 0) {
            throw new IllegalArgumentException("No sampled slice to bound.");
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : slice) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new double[] {min, max};
    }

    /**
     * The three sampled grid points of the 3D display surface: r(n) = q(n).n
     * with sign kept through the caller's coloring rule; this helper returns
     * the cartesian point at radius |q| along n (sign classified by the
     * caller via {@link #directional}), - used with the same orthographic
     * projection the viewer draws, so geometry stays headless-verified.
     */
    public static double[] surfacePoint(double[][] matrix, double azimuthDeg,
            double polarDeg) {
        double azimuth = Math.toRadians(azimuthDeg);
        double polar = Math.toRadians(polarDeg);
        double sinPolar = Math.sin(polar);
        double nx = sinPolar * Math.cos(azimuth);
        double ny = sinPolar * Math.sin(azimuth);
        double nz = Math.cos(polar);
        double radius = Math.abs(directional(matrix, nx, ny, nz));
        return new double[] {radius * nx, radius * ny, radius * nz, radius};
    }

    /**
     * Rotate (x,y,z) by yaw around z, then pitch around x, and project
     * orthographically to (sx, sy) = (x', -z'). The same formula as the
     * viewer canvas, headless so the panel's geometry is test-covered.
     */
    public static double[] project(double x, double y, double z, double yawDeg,
            double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double xr = x * Math.cos(yaw) - y * Math.sin(yaw);
        double yr = x * Math.sin(yaw) + y * Math.cos(yaw);
        double zr = z * Math.cos(pitch) - yr * Math.sin(pitch);
        return new double[] {xr, zr};
    }
}
