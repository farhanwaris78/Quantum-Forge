/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import quantumforge.operation.OperationResult;

/**
 * Exact hexagonal commensurate-twist mathematics (Roadmap #85 data layer):
 * for coprime integers m >= n >= 1 the coincidence rotation of identical
 * triangular lattices obeys
 *
 * <pre>  cos(theta) = (m^2 + 4mn + n^2) / ( 2 (m^2 + mn + n^2) )</pre>
 *
 * computed here as an EXACT long fraction. The CSL index (layer supercell
 * multiplicity) is Sigma = m^2+mn+n^2, divided by 3 when 3 divides (m-n)
 * ((1,1) is the untwisted Sigma=1, (4,1) the 38.21 deg Sigma=7 case). The
 * moire period uses the mismatch formula
 * L = a1 * (1+delta) / sqrt(2(1+delta)(1-cos theta) + delta^2) with the exact
 * theta; with delta = 0 it reduces to the well-known a1 / (2 sin(theta/2)).
 * Non-identical lattices (a2 != a1) are only quasi-commensurate: the exact
 * strain that WOULD restore commensuration is reported instead of hiding it.
 */
public final class MoireTwistMath {

    /** Integer bound keeps every square product inside exact long arithmetic. */
    public static final int MAX_INDEX = 128;
    /** Preview bound on the CSL multiplicity; larger cells belong to builders. */
    public static final long MAX_SIGMA = 1_000_000L;

    /** Immutable twist result. */
    public static final class MoireTwist {
        private final int m;
        private final int n;
        private final int commonFactor;       // g removed from the input pair
        private final long sigmaRaw;          // m^2 + mn + n^2
        private final long sigma;             // sigmaRaw, halved rule /3 when 3 | (m-n)
        private final long cosNumerator;      // m^2 + 4mn + n^2
        private final long cosDenominator;    // 2 * sigmaRaw
        private final double thetaDeg;
        private final double latticeRatio;    // a2/a1 (1.0 for identical)
        private final double moireLength;     // in units of a1
        private final double requiredStrainLayer2; // a1/a2 - 1 (exact)

        MoireTwist(int m, int n, int commonFactor, long sigmaRaw, long sigma,
                long cosNumerator, long cosDenominator, double thetaDeg,
                double latticeRatio, double moireLength, double requiredStrainLayer2) {
            this.m = m;
            this.n = n;
            this.commonFactor = commonFactor;
            this.sigmaRaw = sigmaRaw;
            this.sigma = sigma;
            this.cosNumerator = cosNumerator;
            this.cosDenominator = cosDenominator;
            this.thetaDeg = thetaDeg;
            this.latticeRatio = latticeRatio;
            this.moireLength = moireLength;
            this.requiredStrainLayer2 = requiredStrainLayer2;
        }

        public int getM() { return this.m; }
        public int getN() { return this.n; }
        public int getCommonFactor() { return this.commonFactor; }
        public long getSigmaRaw() { return this.sigmaRaw; }
        public long getSigma() { return this.sigma; }
        public long getCosNumerator() { return this.cosNumerator; }
        public long getCosDenominator() { return this.cosDenominator; }
        public double getThetaDeg() { return this.thetaDeg; }
        public double getLatticeRatio() { return this.latticeRatio; }
        public double getMoireLength() { return this.moireLength; }
        public double getRequiredStrainLayer2() { return this.requiredStrainLayer2; }
    }

    private MoireTwistMath() { }

    /**
     * Computes the twist data. Codes: MOIRE_BOUNDS (indices outside 1..MAX),
     * MOIRE_SIGMA (multiplicity beyond the preview bound), MOIRE_RATIO
     * (a2/a1 not in (0, 10]).
     */
    public static OperationResult<MoireTwist> compute(int rawM, int rawN,
            double latticeRatio) {
        if (!Double.isFinite(latticeRatio) || latticeRatio <= 0.0 || latticeRatio > 10.0) {
            return OperationResult.failed("MOIRE_RATIO",
                    "The second-layer lattice ratio a2/a1 must be finite in (0, 10], got: "
                            + latticeRatio,
                    null);
        }
        if (rawM < 1 || rawN < 1 || rawM > MAX_INDEX || rawN > MAX_INDEX) {
            return OperationResult.failed("MOIRE_BOUNDS",
                    "The (m, n) indices must be integers in 1.." + MAX_INDEX
                            + ", got (" + rawM + ", " + rawN + ").",
                    null);
        }
        int a = Math.max(rawM, rawN);
        int b = Math.min(rawM, rawN);
        int g = gcd(a, b);
        int m = a / g;
        int n = b / g;
        long mm = m;
        long nn = n;
        long sigmaRaw = mm * mm + mm * nn + nn * nn;
        long sigma = ((m - n) % 3 == 0) ? sigmaRaw / 3L : sigmaRaw;
        if (sigma > MAX_SIGMA || sigma < 1) {
            return OperationResult.failed("MOIRE_SIGMA",
                    "The CSL multiplicity " + sigma + " exceeds the " + MAX_SIGMA
                            + " preview bound; use a full builder.",
                    null);
        }
        long cosNum = mm * mm + 4L * mm * nn + nn * nn;
        long cosDen = 2L * sigmaRaw;
        double cosTheta = (double) cosNum / (double) cosDen;
        // Guard the float conversion before trig.
        if (cosTheta < -1.0 || cosTheta > 1.0) {
            return OperationResult.failed("MOIRE_BOUNDS",
                    "The exact cosine fraction " + cosNum + "/" + cosDen
                            + " leaves [-1, 1] in double arithmetic - refused.",
                    null);
        }
        double theta = Math.toDegrees(Math.acos(cosTheta));
        double delta = latticeRatio - 1.0;
        double denom = Math.sqrt(2.0 * (1.0 + delta) * (1.0 - cosTheta)
                + delta * delta);
        if (denom <= 0.0) {
            return OperationResult.failed("MOIRE_RATIO",
                    "The moire period diverges (identical lattice at theta = 0); the "
                            + "structure is the primitive one, no periodic moire.",
                    null);
        }
        double moireLength = latticeRatio / denom; // in units of a1
        double requiredStrain = 1.0 / latticeRatio - 1.0;
        return OperationResult.success("MOIRE_OK", "Twist computed.",
                new MoireTwist(m, n, g, sigmaRaw, sigma, cosNum, cosDen, theta,
                        latticeRatio, moireLength, requiredStrain));
    }

    private static int gcd(int a, int b) {
        int x = a;
        int y = b;
        while (y != 0) {
            int t = x % y;
            x = y;
            y = t;
        }
        return Math.max(1, x);
    }
}
