/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Static k-point mesh quality assessment for an automatic {@code K_POINTS} grid
 * against the periodic cell (Roadmap #37 evidence layer).
 *
 * <p>For every lattice direction the exact reciprocal spacing
 * {@code |b_i| / n_i} in Angstrom^-1 is reported, where
 * {@code b_i = 2pi (a_j x a_k) / V} and {@code n_i} is the mesh division. The
 * derived effective k-range {@code L_i = 1 / spacing_i} (equivalent to
 * {@code n_i |a_i| / (2pi)} for orthogonal cells) is classified against the
 * QE-school heuristic k-range targets of 12 Angstrom (recommended) and
 * 24 Angstrom (accurate) that the input editor itself uses. This is a static
 * mesh-quality statement only: demonstrating convergence requires a grid
 * series with an observable, which this class deliberately does not fake.</p>
 */
public final class QEKpointMeshAdvisor {

    /** Effective k-range (Angstrom) above which a direction is "recommended". */
    public static final double RECOMMENDED_RANGE_ANG = 12.0;

    /** Effective k-range (Angstrom) above which a direction is "accurate". */
    public static final double ACCURATE_RANGE_ANG = 24.0;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double MIN_VOLUME = 1.0e-8;

    /** Coarse-vs-recommended-vs-accurate classification of one direction. */
    public enum MeshQuality {
        COARSE,
        RECOMMENDED,
        ACCURATE
    }

    /** Per-direction spacing report. */
    public static final class DirectionReport {
        private final int index;
        private final int divisions;
        private final double latticeVectorNormAng;
        private final double reciprocalNormInvAng;
        private final double spacingInvAng;
        private final double rangeAng;
        private final MeshQuality quality;

        DirectionReport(int index, int divisions, double latticeVectorNormAng,
                double reciprocalNormInvAng) {
            this.index = index;
            this.divisions = divisions;
            this.latticeVectorNormAng = latticeVectorNormAng;
            this.reciprocalNormInvAng = reciprocalNormInvAng;
            this.spacingInvAng = reciprocalNormInvAng / divisions;
            this.rangeAng = 1.0 / this.spacingInvAng;
            if (this.rangeAng >= ACCURATE_RANGE_ANG) {
                this.quality = MeshQuality.ACCURATE;
            } else if (this.rangeAng >= RECOMMENDED_RANGE_ANG) {
                this.quality = MeshQuality.RECOMMENDED;
            } else {
                this.quality = MeshQuality.COARSE;
            }
        }

        public int getIndex() { return this.index; }
        public int getDivisions() { return this.divisions; }
        public double getLatticeVectorNormAng() { return this.latticeVectorNormAng; }
        public double getReciprocalNormInvAng() { return this.reciprocalNormInvAng; }
        public double getSpacingInvAng() { return this.spacingInvAng; }
        public double getRangeAng() { return this.rangeAng; }
        public MeshQuality getQuality() { return this.quality; }
    }

    /** Whole-mesh verdict across the three lattice directions. */
    public static final class MeshReport {
        private final List<DirectionReport> directions;
        private final int[] offset;
        private final int totalGridPoints;
        private final double minSpacingInvAng;
        private final MeshQuality overallQuality;
        private final List<String> notes = new ArrayList<>();

        MeshReport(List<DirectionReport> directions, int[] offset) {
            this.directions = List.copyOf(directions);
            this.offset = offset.clone();
            int product = 1;
            double minSpacing = Double.POSITIVE_INFINITY;
            MeshQuality worst = MeshQuality.ACCURATE;
            for (DirectionReport direction : directions) {
                product *= direction.getDivisions();
                minSpacing = Math.min(minSpacing, direction.getSpacingInvAng());
                if (direction.getQuality() == MeshQuality.COARSE) {
                    worst = MeshQuality.COARSE;
                } else if (direction.getQuality() == MeshQuality.RECOMMENDED
                        && worst == MeshQuality.ACCURATE) {
                    worst = MeshQuality.RECOMMENDED;
                }
            }
            this.totalGridPoints = product;
            this.minSpacingInvAng = minSpacing;
            this.overallQuality = worst;
            boolean gamma = true;
            StringBuilder shifts = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                if (offset[i] != 0) {
                    gamma = false;
                }
                if (i > 0) {
                    shifts.append(' ');
                }
                shifts.append(offset[i]);
            }
            if (gamma) {
                this.notes.add("Offset 0 0 0: the mesh is Gamma-centred (unshifted).");
            } else {
                this.notes.add("Offset " + shifts
                        + ": shifted Monkhorst-Pack-style mesh (s=1 shifts the grid "
                        + "by half a step in that direction).");
            }
            this.notes.add("Grid points " + this.totalGridPoints
                    + " count the full mesh before symmetry reduction; the irreducible "
                    + "count is decided by the engine and is not claimed here.");
            this.notes.add("A single-mesh quality label is not a convergence proof; run "
                    + "a grid series against your target observable (roadmap #37).");
        }

        public List<DirectionReport> getDirections() { return this.directions; }
        public int[] getOffset() { return this.offset.clone(); }
        public int getTotalGridPoints() { return this.totalGridPoints; }
        public double getMinSpacingInvAng() { return this.minSpacingInvAng; }
        public MeshQuality getOverallQuality() { return this.overallQuality; }
        public List<String> getNotes() { return List.copyOf(this.notes); }
    }

    private QEKpointMeshAdvisor() {
        // Utility.
    }

    /**
     * Assesses an automatic k-grid against a lattice given as three row vectors
     * in Angstrom (the project's {@code Cell.copyLattice()} convention).
     *
     * @param lattice 3x3 lattice rows in Angstrom; volume must be positive
     * @param grid    mesh divisions n_i; every entry must be &gt;= 1
     * @param offset  QE shifts s_i; every entry must be exactly 0 or 1
     */
    public static OperationResult<MeshReport> assess(double[][] lattice, int[] grid, int[] offset) {
        if (lattice == null || lattice.length < 3 || lattice[0] == null || lattice[1] == null
                || lattice[2] == null || lattice[0].length < 3 || lattice[1].length < 3
                || lattice[2].length < 3) {
            return OperationResult.failed("KMESH_LATTICE",
                    "A full 3x3 lattice in Angstrom is required.", null);
        }
        if (grid == null || grid.length < 3 || offset == null || offset.length < 3) {
            return OperationResult.failed("KMESH_GRID", "Grid and offset need three entries each.", null);
        }
        for (int i = 0; i < 3; i++) {
            if (grid[i] < 1) {
                return OperationResult.failed("KMESH_DIVISIONS",
                        "Mesh divisions must be >= 1 in every direction (got " + grid[i] + ").", null);
            }
            if (offset[i] != 0 && offset[i] != 1) {
                return OperationResult.failed("KMESH_OFFSET",
                        "QE mesh shifts must be exactly 0 or 1 per direction (got " + offset[i] + ").", null);
            }
        }
        double[] a1 = lattice[0];
        double[] a2 = lattice[1];
        double[] a3 = lattice[2];
        for (double[] vector : new double[][] {a1, a2, a3}) {
            for (double component : vector) {
                if (!Double.isFinite(component)) {
                    return OperationResult.failed("KMESH_LATTICE_NAN",
                            "Lattice components must be finite numbers.", null);
                }
            }
        }
        double volume = dot(a1, cross(a2, a3));
        if (!(volume > MIN_VOLUME) || !Double.isFinite(volume)) {
            return OperationResult.failed("KMESH_VOLUME",
                    "The lattice volume must be positive and finite (got " + volume + ").", null);
        }
        double[] b1 = scale(cross(a2, a3), TWO_PI / volume);
        double[] b2 = scale(cross(a3, a1), TWO_PI / volume);
        double[] b3 = scale(cross(a1, a2), TWO_PI / volume);
        double[][] reciprocal = {b1, b2, b3};
        double[][] real = {a1, a2, a3};
        List<DirectionReport> directions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            directions.add(new DirectionReport(i, grid[i], norm(real[i]), norm(reciprocal[i])));
        }
        return OperationResult.success("KMESH_OK",
                String.format(Locale.ROOT, "k-mesh assessed: %d x %d x %d.",
                        grid[0], grid[1], grid[2]), new MeshReport(directions, offset));
    }

    private static double[] cross(double[] u, double[] v) {
        return new double[] {u[1] * v[2] - u[2] * v[1],
                u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
    }

    private static double dot(double[] u, double[] v) {
        return u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
    }

    private static double[] scale(double[] u, double factor) {
        return new double[] {u[0] * factor, u[1] * factor, u[2] * factor};
    }

    private static double norm(double[] u) {
        return Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
    }
}
