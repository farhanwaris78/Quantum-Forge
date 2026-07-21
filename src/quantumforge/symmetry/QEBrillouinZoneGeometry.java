/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.symmetry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Reciprocal-lattice Wigner-Seitz (first Brillouin zone) polyhedron from pure
 * lattice geometry (Roadmap #126, geometry layer).
 *
 * <p>The construction enumerates nearby reciprocal lattice vectors G, builds
 * their Bragg bisector planes {@code G . x = |G|^2 / 2}, intersects plane
 * triples into candidate vertices, and keeps the vertices inside every
 * half-space. Two independent algebraic identities must hold before the result
 * is reported:</p>
 * <ul>
 *   <li>the polyhedron volume equals {@code (2 pi)^3 / V_cell} to better than
 *       1%, and</li>
 *   <li>the face/edge/vertex counts satisfy Euler {@code V - E + F = 2}.</li>
 * </ul>
 * <p>Either check failing closes the analysis rather than reporting a
 * suspicious mesh. The polyhedron carries no symmetry labels: high-symmetry
 * point naming is the SeeK-path analysis' job, not geometry's.</p>
 */
public final class QEBrillouinZoneGeometry {

    private static final int MAX_SHELL = 6;
    private static final double VOLUME_TOLERANCE_REL = 0.01;
    private static final double TWO_PI = 2.0 * Math.PI;

    private QEBrillouinZoneGeometry() {
        // Utility
    }

    /** A converged, algebraically cross-checked zone polyhedron. */
    public static final class BzReport {
        private final double[][] reciprocalRows;    // Ang^-1, includes 2*pi
        private final List<double[]> vertices;      // Cartesian Ang^-1
        private final int faceCount;
        private final int edgeCount;
        private final double volumeInvAng3;
        private final double expectedVolumeInvAng3;
        private final double volumeRelativeDeviation;
        private final int shellsUsed;
        private final boolean consistent;
        private final List<String> notes;
        private final double nearestFacetDistanceInvAng;

        private BzReport(double[][] reciprocalRows, List<double[]> vertices, int faceCount,
                         int edgeCount, double volumeInvAng3, double expectedVolumeInvAng3,
                         int shellsUsed, boolean consistent, List<String> notes,
                         double nearestFacetDistanceInvAng) {
            this.reciprocalRows = reciprocalRows;
            this.vertices = vertices;
            this.faceCount = faceCount;
            this.edgeCount = edgeCount;
            this.volumeInvAng3 = volumeInvAng3;
            this.expectedVolumeInvAng3 = expectedVolumeInvAng3;
            this.volumeRelativeDeviation = expectedVolumeInvAng3 > 0.0
                    ? Math.abs(volumeInvAng3 - expectedVolumeInvAng3) / expectedVolumeInvAng3
                    : Double.NaN;
            this.shellsUsed = shellsUsed;
            this.consistent = consistent;
            this.notes = notes;
            this.nearestFacetDistanceInvAng = nearestFacetDistanceInvAng;
        }

        public double[][] getReciprocalRows() { return this.reciprocalRows; }
        public List<double[]> getVertices() { return List.copyOf(this.vertices); }
        public int getVertexCount() { return this.vertices.size(); }
        public int getFaceCount() { return this.faceCount; }
        public int getEdgeCount() { return this.edgeCount; }
        public double getVolumeInvAng3() { return this.volumeInvAng3; }
        public double getExpectedVolumeInvAng3() { return this.expectedVolumeInvAng3; }
        public double getVolumeRelativeDeviation() { return this.volumeRelativeDeviation; }
        public int getShellsUsed() { return this.shellsUsed; }

        /**
         * Distance from the zone centre to the nearest accepted facet plane
         * (Angstrom^-1), i.e. the smallest half-norm of a Bragg plane that is a
         * true face - redundant planes are excluded. NaN when no face exists.
         */
        public double getNearestFacetDistanceInvAng() { return this.nearestFacetDistanceInvAng; }

        /** True only when the volume identity AND Euler characteristic both hold. */
        public boolean isConsistent() { return this.consistent; }
        public List<String> getNotes() { return this.notes; }
    }

    /**
     * Computes the zone polyhedron for a 3x3 lattice (rows = a1,a2,a3 in
     * Angstrom). Degenerate or non-finite lattices fail closed; a shell
     * enumeration that never converges also fails closed.
     */
    public static OperationResult<BzReport> compute(double[][] latticeRowsAng) {
        if (latticeRowsAng == null || latticeRowsAng.length < 3) {
            return OperationResult.failed("BZ_LATTICE_SHAPE",
                    "A 3x3 lattice with rows a1,a2,a3 in Angstrom is required.", null);
        }
        double[][] a = new double[3][3];
        for (int i = 0; i < 3; i++) {
            if (latticeRowsAng[i] == null || latticeRowsAng[i].length < 3) {
                return OperationResult.failed("BZ_LATTICE_SHAPE",
                        "A 3x3 lattice with rows a1,a2,a3 in Angstrom is required.", null);
            }
            for (int j = 0; j < 3; j++) {
                if (!Double.isFinite(latticeRowsAng[i][j])) {
                    return OperationResult.failed("BZ_LATTICE_NONFINITE",
                            "Lattice components must be finite numbers.", null);
                }
                a[i][j] = latticeRowsAng[i][j];
            }
        }
        double[] c23 = cross(a[1], a[2]);
        double[] c31 = cross(a[2], a[0]);
        double[] c12 = cross(a[0], a[1]);
        double volume = dot(a[0], c23);
        double absVolume = Math.abs(volume);
        if (!(absVolume > 1.0e-30)) {
            return OperationResult.failed("BZ_LATTICE_DEGENERATE",
                    "The lattice is degenerate (cell volume ~ 0); no reciprocal zone exists.",
                    null);
        }
        // Reciprocal rows b_i = 2*pi * (a_j x a_k) / V; sign follows the handedness.
        double[][] b = new double[3][];
        b[0] = scale(c23, TWO_PI / volume);
        b[1] = scale(c31, TWO_PI / volume);
        b[2] = scale(c12, TWO_PI / volume);

        List<String> notes = new ArrayList<>();
        BzReport report = null;
        for (int shell = 1; shell <= MAX_SHELL && report == null; shell++) {
            ShellVectors shellVectors = enumerateShell(b, shell);
            Polyhedron poly = buildPolyhedron(shellVectors.vectors);
            if (poly.vertices.isEmpty()) {
                continue;
            }
            // Convergence: no plane of the outermost shell may cut the polyhedron.
            boolean converged = true;
            for (int i = 0; i < shellVectors.vectors.size(); i++) {
                if (shellVectors.levels.get(i) != shell) {
                    continue;
                }
                double[] g = shellVectors.vectors.get(i);
                double halfPlane = 0.5 * dot(g, g);
                for (double[] vertex : poly.vertices) {
                    if (dot(g, vertex) > halfPlane * (1.0 - 1.0e-9)) {
                        converged = false;
                        break;
                    }
                }
                if (!converged) {
                    break;
                }
            }
            if (!converged) {
                continue;
            }
            double expected = Math.pow(TWO_PI, 3.0) / absVolume;
            double measured = polyhedronVolume(poly, shellVectors.vectors);
            double deviation = expected > 0.0 && Double.isFinite(measured)
                    ? Math.abs(measured - expected) / expected : Double.NaN;
            int euler = poly.vertices.size() - poly.edgeCount + poly.faceCount();
            boolean consistent = Double.isFinite(deviation) && deviation <= VOLUME_TOLERANCE_REL
                    && euler == 2;
            notes.add(String.format(java.util.Locale.ROOT,
                    "Volume identity |V_BZ - (2 pi)^3/V_cell| / (2 pi)^3/V_cell = %.3e "
                            + "(tolerance %.0f%%)", deviation, VOLUME_TOLERANCE_REL * 100.0));
            notes.add("Euler characteristic V - E + F = " + poly.vertices.size() + " - "
                    + poly.edgeCount + " + " + poly.faceCount() + " = " + euler
                    + (euler == 2 ? "" : " (expected 2)"));
            if (!consistent) {
                return OperationResult.failed("BZ_INCONSISTENT",
                        String.format(java.util.Locale.ROOT,
                                "The polyhedron failed its own algebraic checks (volume "
                                        + "deviation %.3e, Euler %d); not reporting a "
                                        + "suspicious zone.", deviation, euler),
                        null);
            }
            double nearestFacet = Double.POSITIVE_INFINITY;
            for (int face = 0; face < poly.faces.size(); face++) {
                int planeIndex = poly.facePlaneIndex.get(face);
                double[] g = shellVectors.vectors.get(planeIndex);
                nearestFacet = Math.min(nearestFacet, 0.5 * Math.sqrt(dot(g, g)));
            }
            if (poly.faces.isEmpty()) {
                nearestFacet = Double.NaN;
            }
            notes.add(String.format(java.util.Locale.ROOT,
                    "Nearest facet distance from the zone centre d* = %.8f Ang^-1 "
                            + "(smallest half-norm of an accepted Bragg-plane face).",
                    nearestFacet));
            report = new BzReport(b, poly.vertices, poly.faceCount(), poly.edgeCount, measured,
                    expected, shell, true, notes, nearestFacet);
        }
        if (report == null) {
            return OperationResult.failed("BZ_NO_CONVERGENCE",
                    "The reciprocal-lattice shell enumeration did not converge within "
                            + MAX_SHELL + " shells (very acute cell?); refusing to guess the "
                            + "zone geometry.", null);
        }
        return OperationResult.success("BZ_OK", "Zone polyhedron cross-checked.", report);
    }

    /** Vertices, faces (with their plane indices) and edges of the intersection. */
    private static final class Polyhedron {
        private final List<double[]> vertices;
        private final List<List<Integer>> faces;          // vertex indices per face
        private final List<Integer> facePlaneIndex;       // parallel to faces
        private final int edgeCount;

        private Polyhedron(List<double[]> vertices, List<List<Integer>> faces,
                           List<Integer> facePlaneIndex, int edgeCount) {
            this.vertices = vertices;
            this.faces = faces;
            this.facePlaneIndex = facePlaneIndex;
            this.edgeCount = edgeCount;
        }

        private int faceCount() {
            return this.faces.size();
        }
    }

    /** Reciprocal vectors with their analytic shell (Miller sup-norm) levels. */
    private static final class ShellVectors {
        private final List<double[]> vectors;
        private final List<Integer> levels;

        private ShellVectors(List<double[]> vectors, List<Integer> levels) {
            this.vectors = vectors;
            this.levels = levels;
        }
    }

    /** Enumerates all nonzero G = h b1 + k b2 + l b3 within the inclusive shell cube. */
    private static ShellVectors enumerateShell(double[][] b, int shell) {
        List<double[]> vectors = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (int h = -shell; h <= shell; h++) {
            for (int k = -shell; k <= shell; k++) {
                for (int l = -shell; l <= shell; l++) {
                    if (h == 0 && k == 0 && l == 0) {
                        continue;
                    }
                    vectors.add(new double[] {
                            h * b[0][0] + k * b[1][0] + l * b[2][0],
                            h * b[0][1] + k * b[1][1] + l * b[2][1],
                            h * b[0][2] + k * b[1][2] + l * b[2][2]});
                    levels.add(Math.max(Math.abs(h), Math.max(Math.abs(k), Math.abs(l))));
                }
            }
        }
        return new ShellVectors(vectors, levels);
    }

    /** Builds the half-space intersection polyhedron from Bragg-bisector planes. */
    private static Polyhedron buildPolyhedron(List<double[]> latticeVectors) {
        int planeCount = latticeVectors.size();
        double[] halfLengths = new double[planeCount];
        double maxHalf = 0.0;
        for (int i = 0; i < planeCount; i++) {
            halfLengths[i] = 0.5 * dot(latticeVectors.get(i), latticeVectors.get(i));
            maxHalf = Math.max(maxHalf, halfLengths[i]);
        }
        double insideTol = 1.0e-9 * maxHalf;
        double onPlaneTol = 1.0e-7 * maxHalf;
        double vertexTol = 1.0e-6 * Math.sqrt(maxHalf);

        // Vertices: plane-triple intersections that satisfy every half-space.
        List<double[]> vertices = new ArrayList<>();
        for (int i = 0; i < planeCount; i++) {
            for (int j = i + 1; j < planeCount; j++) {
                for (int k = j + 1; k < planeCount; k++) {
                    double[] x = intersect(latticeVectors.get(i), halfLengths[i],
                            latticeVectors.get(j), halfLengths[j],
                            latticeVectors.get(k), halfLengths[k]);
                    if (x == null) {
                        continue;
                    }
                    boolean inside = true;
                    for (int p = 0; p < planeCount && inside; p++) {
                        if (dot(latticeVectors.get(p), x) - halfLengths[p] > insideTol) {
                            inside = false;
                        }
                    }
                    if (!inside) {
                        continue;
                    }
                    boolean duplicate = false;
                    for (double[] existing : vertices) {
                        if (distance(existing, x) <= vertexTol) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        vertices.add(x);
                    }
                }
            }
        }

        // Normalize the vertex ordering BEFORE faces and edges capture indices:
        // sorting here keeps every downstream index list consistent (sorting
        // after face construction would silently rebind faces to wrong points).
        vertices.sort(Comparator.comparingDouble((double[] v) -> v[0])
                .thenComparingDouble(v -> v[1]).thenComparingDouble(v -> v[2]));

        // Faces: planes carrying >= 3 vertices.
        List<List<Integer>> faces = new ArrayList<>();
        List<Integer> facePlaneIndex = new ArrayList<>();
        for (int p = 0; p < planeCount; p++) {
            List<Integer> onFace = new ArrayList<>();
            for (int v = 0; v < vertices.size(); v++) {
                if (Math.abs(dot(latticeVectors.get(p), vertices.get(v)) - halfLengths[p])
                        <= onPlaneTol) {
                    onFace.add(v);
                }
            }
            if (onFace.size() >= 3) {
                faces.add(onFace);
                facePlaneIndex.add(p);
            }
        }

        // Edges: vertex pairs sharing at least two faces.
        int edgeCount = 0;
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                int shared = 0;
                for (List<Integer> face : faces) {
                    if (face.contains(i) && face.contains(j)) {
                        shared++;
                    }
                }
                if (shared >= 2) {
                    edgeCount++;
                }
            }
        }
        return new Polyhedron(vertices, faces, facePlaneIndex, edgeCount);
    }

    /**
     * Convex volume by outward face fans (divergence theorem):
     * V = (1/3) sum_over_faces (centroid . n_hat) * area. Each face's vertices
     * are cyclically ordered around the face centroid in the plane spanned by an
     * in-plane orthonormal basis before the fan triangulation.
     */
    private static double polyhedronVolume(Polyhedron poly, List<double[]> vectors) {
        double volume = 0.0;
        for (int f = 0; f < poly.faces.size(); f++) {
            List<Integer> indices = poly.faces.get(f);
            double[] normal = vectors.get(poly.facePlaneIndex.get(f));
            double normalLen = Math.sqrt(dot(normal, normal));
            if (normalLen <= 0.0) {
                return Double.NaN;
            }
            double[] nhat = scale(normal, 1.0 / normalLen);

            double[] centroid = new double[3];
            for (int vi : indices) {
                double[] p = poly.vertices.get(vi);
                centroid[0] += p[0];
                centroid[1] += p[1];
                centroid[2] += p[2];
            }
            centroid = scale(centroid, 1.0 / indices.size());
            final double[] faceCentroid = centroid;

            double[] ref = minus(poly.vertices.get(indices.get(0)), faceCentroid);
            double refLen = Math.sqrt(dot(ref, ref));
            if (refLen <= 0.0) {
                return Double.NaN;
            }
            double[] uAxis = scale(ref, 1.0 / refLen);
            double[] wAxis = cross(nhat, uAxis);

            List<Integer> cyclic = new ArrayList<>(indices);
            cyclic.sort(Comparator.comparingDouble(vi -> {
                double[] d = minus(poly.vertices.get(vi), faceCentroid);
                return Math.atan2(dot(d, wAxis), dot(d, uAxis));
            }));

            double area = 0.0;
            double[] p0 = poly.vertices.get(cyclic.get(0));
            for (int i = 1; i + 1 < cyclic.size(); i++) {
                double[] edgeU = minus(poly.vertices.get(cyclic.get(i)), p0);
                double[] edgeV = minus(poly.vertices.get(cyclic.get(i + 1)), p0);
                double[] normalVector = cross(edgeU, edgeV);
                area += 0.5 * Math.sqrt(dot(normalVector, normalVector));
            }
            volume += dot(centroid, nhat) * area / 3.0;
        }
        return volume;
    }

    /** Solves g1.x = h1, g2.x = h2, g3.x = h3; returns null when singular. */
    private static double[] intersect(double[] g1, double h1, double[] g2, double h2,
                                      double[] g3, double h3) {
        double[][] m = {g1.clone(), g2.clone(), g3.clone()};
        double det = determinant3(m);
        double scale = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                scale = Math.max(scale, Math.abs(m[i][j]));
            }
        }
        if (scale <= 0.0 || Math.abs(det) <= 1.0e-12 * scale * scale * scale) {
            return null;
        }
        double[] rhs = {h1, h2, h3};
        double[] x = new double[3];
        for (int col = 0; col < 3; col++) {
            double[][] swapped = {m[0].clone(), m[1].clone(), m[2].clone()};
            for (int row = 0; row < 3; row++) {
                swapped[row][col] = rhs[row];
            }
            x[col] = determinant3(swapped) / det;
        }
        if (!Double.isFinite(x[0]) || !Double.isFinite(x[1]) || !Double.isFinite(x[2])) {
            return null;
        }
        return x;
    }

    private static double determinant3(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    private static double[] cross(double[] u, double[] v) {
        return new double[] {u[1] * v[2] - u[2] * v[1],
                             u[2] * v[0] - u[0] * v[2],
                             u[0] * v[1] - u[1] * v[0]};
    }

    private static double dot(double[] u, double[] v) {
        return u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
    }

    private static double[] scale(double[] u, double factor) {
        return new double[] {u[0] * factor, u[1] * factor, u[2] * factor};
    }

    private static double[] minus(double[] u, double[] v) {
        return new double[] {u[0] - v[0], u[1] - v[1], u[2] - v[2]};
    }

    private static double distance(double[] u, double[] v) {
        double dx = u[0] - v[0];
        double dy = u[1] - v[1];
        double dz = u[2] - v[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
