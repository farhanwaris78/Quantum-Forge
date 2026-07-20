/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import quantumforge.operation.OperationResult;

/**
 * Exact cubic coincidence-site-lattice (CSL) rotation mathematics (Roadmap
 * #86 data layer): Ranganathan's generation law produces, for a rotation axis
 * [u v w] with integer norm N = u^2+v^2+w^2 and integers m, n, the
 * coincidence rotation
 *
 * <pre>  tan(theta/2) = (n/m) * sqrt(N)
 *        Sigma raw    = m^2 + N n^2,   divided by 2 until odd</pre>
 *
 * The rotation is rigorous CSL theory for the SIMPLE CUBIC lattice; for
 * face-/body-centred Bravais lattices additional systematic extinctions can
 * lower the physical Sigma, which is stated honestly in the report. The
 * cosine of the rotation angle is kept as an EXACT reduced long fraction
 * cos(theta) = (m^2 - N n^2) / (m^2 + N n^2) - e.g. [001] (3,1) is exactly
 * 4/5 (36.869898 deg, the Sigma 5 STGB), [110] (1,1) exactly -1/3
 * (109.471221 deg, Sigma 3). A Sigma reduced to 1 means the rotation is a
 * lattice symmetry operation and no distinct boundary exists at all; that
 * case is reported, not hidden. Indices are bounds-checked so every square
 * product stays inside exact long arithmetic.
 */
public final class CslSigmaMath {

    /** Axis-index bound keeps N = u^2+v^2+w^2 small and exact in long. */
    public static final int MAX_AXIS_INDEX = 16;
    /** Pair-index bound keeps m^2 + N n^2 far inside exact long arithmetic. */
    public static final int MAX_PAIR_INDEX = 1024;
    /** Preview bound on the CSL multiplicity; larger cells belong to builders. */
    public static final long MAX_SIGMA = 999_999L;

    /** Immutable CSL rotation result. */
    public static final class CslRotation {
        private final int u;
        private final int v;
        private final int w;
        private final int axisCommonFactor;   // g removed from the input axis
        private final int m;
        private final int n;
        private final int pairCommonFactor;   // g removed from the input pair
        private final long axisNormSquared;   // N
        private final long sigmaRaw;          // m^2 + N n^2
        private final long sigma;             // odd part of sigmaRaw
        private final int halvings;           // number of /2 divisions applied
        private final long cosNumerator;      // reduced exact cos numerator
        private final long cosDenominator;    // reduced exact cos denominator
        private final double angleDeg;

        CslRotation(int u, int v, int w, int axisCommonFactor, int m, int n,
                int pairCommonFactor, long axisNormSquared, long sigmaRaw, long sigma,
                int halvings, long cosNumerator, long cosDenominator, double angleDeg) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.axisCommonFactor = axisCommonFactor;
            this.m = m;
            this.n = n;
            this.pairCommonFactor = pairCommonFactor;
            this.axisNormSquared = axisNormSquared;
            this.sigmaRaw = sigmaRaw;
            this.sigma = sigma;
            this.halvings = halvings;
            this.cosNumerator = cosNumerator;
            this.cosDenominator = cosDenominator;
            this.angleDeg = angleDeg;
        }

        public int getU() { return this.u; }
        public int getV() { return this.v; }
        public int getW() { return this.w; }
        public int getAxisCommonFactor() { return this.axisCommonFactor; }
        public int getM() { return this.m; }
        public int getN() { return this.n; }
        public int getPairCommonFactor() { return this.pairCommonFactor; }
        public long getAxisNormSquared() { return this.axisNormSquared; }
        public long getSigmaRaw() { return this.sigmaRaw; }
        public long getSigma() { return this.sigma; }
        public int getHalvings() { return this.halvings; }
        public long getCosNumerator() { return this.cosNumerator; }
        public long getCosDenominator() { return this.cosDenominator; }
        public double getAngleDeg() { return this.angleDeg; }

        /** Sigma 1 means a lattice symmetry operation - no distinct boundary. */
        public boolean isLatticeSymmetry() { return this.sigma == 1L; }
    }

    private CslSigmaMath() { }

    /**
     * Computes the CSL rotation. Codes: CSL_VECTOR (zero rotation axis),
     * CSL_BOUNDS (axis components outside +/-MAX_AXIS_INDEX or (m, n) outside
     * 1..MAX_PAIR_INDEX), CSL_SIGMA (multiplicity beyond the preview bound).
     */
    public static OperationResult<CslRotation> compute(int rawU, int rawV, int rawW,
            int rawM, int rawN) {
        if (rawU == 0 && rawV == 0 && rawW == 0) {
            return OperationResult.failed("CSL_VECTOR",
                    "The rotation axis [0 0 0] is not a direction.", null);
        }
        if (Math.abs(rawU) > MAX_AXIS_INDEX || Math.abs(rawV) > MAX_AXIS_INDEX
                || Math.abs(rawW) > MAX_AXIS_INDEX) {
            return OperationResult.failed("CSL_BOUNDS",
                    "Rotation-axis components must be integers within +/-"
                            + MAX_AXIS_INDEX + ", got [" + rawU + " " + rawV + " "
                            + rawW + "].",
                    null);
        }
        if (rawM < 1 || rawN < 1 || rawM > MAX_PAIR_INDEX || rawN > MAX_PAIR_INDEX) {
            return OperationResult.failed("CSL_BOUNDS",
                    "The (m, n) indices must be integers in 1.." + MAX_PAIR_INDEX
                            + ", got (" + rawM + ", " + rawN + ").",
                    null);
        }
        int axisG = gcd(gcd(Math.abs(rawU), Math.abs(rawV)), Math.abs(rawW));
        int u = rawU / axisG;
        int v = rawV / axisG;
        int w = rawW / axisG;
        int pairG = gcd(rawM, rawN);
        int m = rawM / pairG;
        int n = rawN / pairG;
        long nn = (long) u * u + (long) v * v + (long) w * w;
        long mm = m;
        long xx = n;
        long sigmaRaw = mm * mm + nn * xx * xx;
        long sigma = sigmaRaw;
        int halvings = 0;
        while (sigma % 2L == 0L) {
            sigma /= 2L;
            halvings += 1;
        }
        if (sigma > MAX_SIGMA) {
            return OperationResult.failed("CSL_SIGMA",
                    "The CSL multiplicity " + sigma + " exceeds the " + MAX_SIGMA
                            + " preview bound; use a full boundary builder.",
                    null);
        }
        long cosNum = mm * mm - nn * xx * xx;
        long cosDen = sigmaRaw;
        long g = gcd(Math.abs(cosNum), cosDen);
        cosNum /= g;
        cosDen /= g;
        double cosTheta = (double) cosNum / (double) cosDen;
        if (cosTheta < -1.0 || cosTheta > 1.0) {
            return OperationResult.failed("CSL_BOUNDS",
                    "The exact cosine fraction " + cosNum + "/" + cosDen
                            + " leaves [-1, 1] in double arithmetic - refused.",
                    null);
        }
        double angleDeg = Math.toDegrees(Math.acos(cosTheta));
        return OperationResult.success("CSL_OK", "CSL rotation computed.",
                new CslRotation(u, v, w, axisG, m, n, pairG, nn, sigmaRaw, sigma,
                        halvings, cosNum, cosDen, angleDeg));
    }

    private static long gcd(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0L) {
            long t = x % y;
            x = y;
            y = t;
        }
        return Math.max(1L, x);
    }
}
