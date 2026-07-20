/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

/**
 * Miller surface-plane geometry (Roadmap #82 first slice): for a plane
 * (h k l) and an arbitrary triclinic cell, the reciprocal vector
 * G_hkl = h*b1 + k*b2 + l*b3 (2*pi convention, reciprocal rows from the
 * cell's own lattice rows) gives EXACTLY
 *
 * <pre>  d_hkl = 2*pi / |G_hkl|,   surface normal n = G / |G|</pre>
 *
 * Indices are gcd-normalized WITH provenance ((2 2 2) IS (1 1 1)), the cell
 * volume is checked before any division, and the ESM alignment gate is the
 * same 1e-8 z-gate the QEEsmAuditor uses: a slab destined for QE's
 * assume_isolated='esm' must have its surface normal along +z, so a
 * misaligned preview says NO to ESM-readiness openly rather than letting a
 * rotated cell reach a run. Molecule/termination enumeration, stoichiometry,
 * polarity, atom construction and vacuum sizing are the remaining #82
 * builder work; this slice is plane geometry ONLY.
 */
public final class SlabMillerMath {

    /** Index bound keeps the geometry reviewable and overflow-safe. */
    public static final int MAX_MILLER_INDEX = 16;
    /** Degenerate-cell det bound (cubic Angstrom). */
    public static final double MIN_VOLUME_ANG3 = 1.0e-12;
    /** z-gate tolerance, mirrored from the ESM auditor's convention. */
    public static final double ESM_Z_GATE = 1.0e-8;

    /** Immutable plane-geometry result. */
    public static final class MillerPlane {
        private final int h;
        private final int k;
        private final int l;
        private final int commonFactor;      // g removed from the input triplet
        private final double volumeAng3;     // |det(lattice rows)|
        private final double recipNormInvAng; // |G_hkl| in 1/Ang
        private final double dSpacingAng;    // 2*pi / |G|
        private final double nx;
        private final double ny;
        private final double nz;             // unit normal components

        MillerPlane(int h, int k, int l, int commonFactor, double volumeAng3,
                double recipNormInvAng, double dSpacingAng, double nx, double ny,
                double nz) {
            this.h = h;
            this.k = k;
            this.l = l;
            this.commonFactor = commonFactor;
            this.volumeAng3 = volumeAng3;
            this.recipNormInvAng = recipNormInvAng;
            this.dSpacingAng = dSpacingAng;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        public int getH() { return this.h; }
        public int getK() { return this.k; }
        public int getL() { return this.l; }
        public int getCommonFactor() { return this.commonFactor; }
        public double getVolumeAng3() { return this.volumeAng3; }
        public double getRecipNormInvAng() { return this.recipNormInvAng; }
        public double getDSpacingAng() { return this.dSpacingAng; }
        public double getNx() { return this.nx; }
        public double getNy() { return this.ny; }
        public double getNz() { return this.nz; }

        /** True when the surface normal is along +z within the ESM z-gate. */
        public boolean isEsmAligned() {
            return Math.abs(this.nx) < ESM_Z_GATE && Math.abs(this.ny) < ESM_Z_GATE
                    && Math.abs(Math.abs(this.nz) - 1.0) < ESM_Z_GATE;
        }
    }

    private SlabMillerMath() { }

    /**
     * Computes the plane geometry for lattice ROWS a1, a2, a3 (Angstrom, as
     * returned by {@code Cell.copyLattice()}). Codes: SLAB_VECTOR (zero
     * plane), SLAB_BOUNDS (index beyond +/-MAX), SLAB_CELL (degenerate or
     * malformed lattice).
     */
    public static OperationResult<MillerPlane> compute(double[][] lattice, int rawH,
            int rawK, int rawL) {
        if (lattice == null || lattice.length < 3 || lattice[0] == null
                || lattice[1] == null || lattice[2] == null
                || lattice[0].length < 3 || lattice[1].length < 3
                || lattice[2].length < 3) {
            return OperationResult.failed("SLAB_CELL",
                    "The lattice must be three rows of three numbers.", null);
        }
        double[][] copy = Matrix3D.copy(lattice);
        double det = Matrix3D.determinant(copy);
        if (!Double.isFinite(det) || Math.abs(det) < MIN_VOLUME_ANG3) {
            return OperationResult.failed("SLAB_CELL",
                    "The cell lattice is degenerate (|det| below " + MIN_VOLUME_ANG3
                            + " Ang^3): no well-defined reciprocal geometry.",
                    null);
        }
        if (rawH == 0 && rawK == 0 && rawL == 0) {
            return OperationResult.failed("SLAB_VECTOR",
                    "The plane (0 0 0) is not a crystallographic plane.", null);
        }
        if (Math.abs(rawH) > MAX_MILLER_INDEX || Math.abs(rawK) > MAX_MILLER_INDEX
                || Math.abs(rawL) > MAX_MILLER_INDEX) {
            return OperationResult.failed("SLAB_BOUNDS",
                    "Miller indices must be within +/-" + MAX_MILLER_INDEX + ", got ("
                            + rawH + " " + rawK + " " + rawL + ").",
                    null);
        }
        int factor = gcd(Math.abs(rawH), Math.abs(rawK), Math.abs(rawL));
        int h = rawH / factor;
        int k = rawK / factor;
        int l = rawL / factor;
        double[] a1 = copy[0];
        double[] a2 = copy[1];
        double[] a3 = copy[2];
        double invDet = 2.0 * Math.PI / det;
        double[] c23 = cross(a2, a3);
        double[] c31 = cross(a3, a1);
        double[] c12 = cross(a1, a2);
        double[] g = new double[3];
        for (int i = 0; i < 3; i++) {
            g[i] = invDet * (h * c23[i] + k * c31[i] + l * c12[i]);
        }
        double norm = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
        if (!Double.isFinite(norm) || norm <= 0.0) {
            return OperationResult.failed("SLAB_CELL",
                    "The reciprocal geometry collapsed (zero |G|) - refused.", null);
        }
        double dSpacing = 2.0 * Math.PI / norm;
        return OperationResult.success("SLAB_OK", "Plane geometry computed.",
                new MillerPlane(h, k, l, factor, Math.abs(det), norm, dSpacing,
                        g[0] / norm, g[1] / norm, g[2] / norm));
    }

    private static int gcd(int... values) {
        int result = 0;
        for (int value : values) {
            int x = result;
            int y = Math.abs(value);
            while (y != 0) {
                int t = x % y;
                x = y;
                y = t;
            }
            result = Math.max(x, result);
        }
        return Math.max(1, result);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }
}
