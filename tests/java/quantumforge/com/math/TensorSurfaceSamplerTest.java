/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TensorSurfaceSamplerTest {

    private static final double[][] DIAG123 = {{1, 0, 0}, {0, 2, 0}, {0, 0, 3}};

    /** Independent quadratic-form implementation (never calls the unit). */
    private static double qIndependent(double[][] m, double nx, double ny, double nz) {
        double[] n = {nx, ny, nz};
        double sum = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                sum += n[i] * m[i][j] * n[j];
            }
        }
        return sum;
    }

    @Test
    void parseMirrorsTheTensorFileContract() {
        double[][] m = TensorSurfaceSampler.parseMatrix3x3(
                "  1.0D0, 0.0  0.0\n"
                + "\n"
                + "  0.0 2.5 0.0   trailing is never read\n"
                + "  0.0,0.0, 4.0d0\n"
                + "  9 9 9 9\n");
        assertEquals(1.0, m[0][0], 0.0);
        assertEquals(2.5, m[1][1], 0.0);
        assertEquals(4.0, m[2][2], 0.0,
                "Fortran D/d exponents tolerated, blanks skipped, row 4 ignored");
        IllegalArgumentException truncated = assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.parseMatrix3x3("1 0 0\n0 2 0\n"));
        assertTrue(truncated.getMessage().contains("2 matrix row"),
                truncated.getMessage());
        IllegalArgumentException shortRow = assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.parseMatrix3x3("1 0 0\n0 2\n0 0 3\n"));
        assertTrue(shortRow.getMessage().contains("row 2"), shortRow.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.parseMatrix3x3("1 0 0\n0 x 0\n0 0 3\n"),
                "non-numeric refuses with the row, never zero-healed");
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.parseMatrix3x3("1 0 0\n0 1e999 0\n0 0 3\n"),
                "non-finite refuses, never displayed");
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.parseMatrix3x3(null),
                "null text audits as zero rows, not a zero matrix");
    }

    @Test
    void quadraticFormMatchesAnIndependentLoop() {
        double[][] m = {{2.0, 0.5, -0.25}, {0.5, 3.0, 0.75}, {-0.25, 0.75, 1.5}};
        double[][] directions = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1},
                {0.2672612419124244, 0.5345224838248488, 0.8017837257372732}};
        for (double[] n : directions) {
            assertEquals(qIndependent(m, n[0], n[1], n[2]),
                    TensorSurfaceSampler.directional(m, n[0], n[1], n[2]), 1e-12);
        }
    }

    @Test
    void isotropicTensorRendersAConstantSphere() {
        double[][] sphere = TensorSurfaceSampler.sampleSphere(new double[][]{
                {7, 0, 0}, {0, 7, 0}, {0, 0, 7}}, 24, 13);
        assertEquals(13, sphere.length);
        assertEquals(24, sphere[0].length);
        for (double[] row : sphere) {
            for (double v : row) {
                assertEquals(7.0, v, 1e-12, "every direction of cI is c");
            }
        }
        double[] slice = TensorSurfaceSampler.samplePlane(new double[][]{
                {7, 0, 0}, {0, 7, 0}, {0, 0, 7}},
                TensorSurfaceSampler.CartesianPlane.XZ, 8);
        for (double v : slice) {
            assertEquals(7.0, v, 1e-12);
        }
    }

    @Test
    void sphereGridHitsEigenBoundsExactlyForAxisAlignedDiagonal() {
        double[][] grid = TensorSurfaceSampler.sampleSphere(DIAG123, 24, 13);
        assertEquals(3.0, grid[0][0], 1e-12, "pole +z");
        assertEquals(3.0, grid[12][7], 1e-12, "pole -z, azimuth degenerate");
        assertEquals(1.0, grid[6][0], 1e-12, "equator x");
        assertEquals(2.0, grid[6][6], 1e-12, "equator y (azimuth 90)");
        double[] bounds = TensorSurfaceSampler.minMax(grid);
        assertEquals(1.0, bounds[0], 1e-12);
        assertEquals(3.0, bounds[1], 1e-12);
        // the eigen-bounds theorem the viewer fails closed on, pinned here:
        SymmetricEigen3.EigenDecomposition eigen = SymmetricEigen3.eigenvectors(DIAG123);
        for (double[] row : grid) {
            for (double v : row) {
                assertTrue(v >= eigen.getEigenvalues()[0] - 1e-12
                        && v <= eigen.getEigenvalues()[2] + 1e-12,
                        "sampled value " + v + " escaped the eigen-bounds");
            }
        }
    }

    @Test
    void planeSlicesSelectTheRightCartesianPairs() {
        double[] xy = TensorSurfaceSampler.samplePlane(DIAG123,
                TensorSurfaceSampler.CartesianPlane.XY, 4);
        assertArrayEquals(new double[] {1.0, 2.0, 1.0, 2.0}, xy, 1e-12);
        double[] xz = TensorSurfaceSampler.samplePlane(DIAG123,
                TensorSurfaceSampler.CartesianPlane.XZ, 4);
        assertArrayEquals(new double[] {1.0, 3.0, 1.0, 3.0}, xz, 1e-12);
        double[] yz = TensorSurfaceSampler.samplePlane(DIAG123,
                TensorSurfaceSampler.CartesianPlane.YZ, 4);
        assertArrayEquals(new double[] {2.0, 3.0, 2.0, 3.0}, yz, 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.samplePlane(DIAG123, null, 4));
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.samplePlane(DIAG123,
                        TensorSurfaceSampler.CartesianPlane.XY, 1));
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.sampleSphere(DIAG123, 1, 13));
    }

    @Test
    void indefiniteTensorsKeepTheirSignLobes() {
        double[][] indef = {{1, 0, 0}, {0, -1, 0}, {0, 0, 2}};
        double[] xy = TensorSurfaceSampler.samplePlane(indef,
                TensorSurfaceSampler.CartesianPlane.XY, 4);
        assertEquals(-1.0, xy[1], 1e-12, "negative lobe stays negative");
        double[][] grid = TensorSurfaceSampler.sampleSphere(indef, 24, 13);
        double[] bounds = TensorSurfaceSampler.minMax(grid);
        assertEquals(-1.0, bounds[0], 1e-12);
        assertEquals(2.0, bounds[1], 1e-12);
    }

    @Test
    void rotatedTensorEigenvaluesFollowTheRotation() {
        double theta = Math.toRadians(30.0);
        double c = Math.cos(theta);
        double s = Math.sin(theta);
        // T = R diag(1,2,3) R^T with R a +30-degree rotation about z:
        double[][] t = new double[3][3];
        double[][] r = {{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                t[i][j] = r[i][0] * r[j][0] * 1.0 + r[i][1] * r[j][1] * 2.0
                        + r[i][2] * r[j][2] * 3.0;
            }
        }
        assertEquals(1.0, TensorSurfaceSampler.directional(t, c, s, 0), 1e-12,
                "along the rotated x-axis");
        assertEquals(2.0, TensorSurfaceSampler.directional(t, -s, c, 0), 1e-12,
                "along the rotated y-axis");
        SymmetricEigen3.EigenDecomposition eigen = SymmetricEigen3.eigenvectors(t);
        assertTrue(eigen.isConverged());
        double[][] grid = TensorSurfaceSampler.sampleSphere(t, 24, 13);
        for (double[] row : grid) {
            for (double v : row) {
                assertTrue(v >= 1.0 - 1e-9 && v <= 3.0 + 1e-9, "bounds after rotation");
            }
        }
    }

    @Test
    void surfaceGeometryIsHeadlessVerified() {
        double[] top = TensorSurfaceSampler.surfacePoint(DIAG123, 0, 0);
        assertEquals(3.0, top[2], 1e-12, "|q|.z at the pole");
        double[] equator = TensorSurfaceSampler.surfacePoint(DIAG123, 0, 90);
        assertEquals(1.0, equator[0], 1e-12);
        assertEquals(1.0, equator[3], 1e-12);
        double[][] indef = {{1, 0, 0}, {0, -1, 0}, {0, 0, 2}};
        double[] lobe = TensorSurfaceSampler.surfacePoint(indef, 90, 90);
        assertEquals(1.0, lobe[3], 1e-12, "radius is |q|; the SIGN stays host-side");
        double[] flat = TensorSurfaceSampler.project(1, 2, 3, 0, 0);
        assertArrayEquals(new double[] {1.0, 3.0}, flat, 1e-12,
                "no rotation: (x, z) straight through");
        double[] yawed = TensorSurfaceSampler.project(0, 1, 0, 90, 0);
        assertArrayEquals(new double[] {-1.0, 0.0}, yawed, 1e-12);
        double[] pitched = TensorSurfaceSampler.project(0, 1, 0, 0, 90);
        assertArrayEquals(new double[] {0.0, -1.0}, pitched, 1e-12);
    }

    @Test
    void minMaxGuardsRefuseEmptyGrids() {
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.minMax(new double[0][0]));
        assertThrows(IllegalArgumentException.class,
                () -> TensorSurfaceSampler.minMax(new double[0]));
    }
}
