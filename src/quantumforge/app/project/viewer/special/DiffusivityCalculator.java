/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.special;

/** Linear-regression Einstein diffusivity helper for an already-computed MSD. */
public final class DiffusivityCalculator {
    private DiffusivityCalculator() { }

    public static final class DiffusionFit {
        private final double diffusivity;
        private final double slope;
        private final double intercept;
        private final double rSquared;
        private final double slopeStandardError;
        private final int startIndex;
        private final int endIndexExclusive;
        private final int dimensions;

        private DiffusionFit(double diffusivity, double slope, double intercept,
                             double rSquared, double slopeStandardError,
                             int start, int end, int dimensions) {
            this.diffusivity = diffusivity;
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
            this.slopeStandardError = slopeStandardError;
            this.startIndex = start;
            this.endIndexExclusive = end;
            this.dimensions = dimensions;
        }

        public double getDiffusivity() { return this.diffusivity; }
        public double getSlope() { return this.slope; }
        public double getIntercept() { return this.intercept; }
        public double getRSquared() { return this.rSquared; }
        public double getSlopeStandardError() { return this.slopeStandardError; }
        public double getDiffusivityStandardError() {
            return this.slopeStandardError / (2.0 * this.dimensions);
        }
        public int getStartIndex() { return this.startIndex; }
        public int getEndIndexExclusive() { return this.endIndexExclusive; }
    }

    /** Fit the complete supplied interval. MSD/time units determine the output unit. */
    public static double calculateD(double[] msd, double[] time, int dimensions) {
        return fit(msd, time, dimensions, 0, time == null ? 0 : time.length).getDiffusivity();
    }

    /**
     * Fit MSD(t)=intercept+slope*t over [start,end), then D=slope/(2*d).
     * The caller must select a physically diffusive window and use unwrapped,
     * multi-time-origin MSD data; R² alone does not establish diffusion.
     */
    public static DiffusionFit fit(double[] msd, double[] time, int dimensions,
                                   int start, int endExclusive) {
        if (msd == null || time == null || msd.length != time.length) {
            throw new IllegalArgumentException("MSD and time arrays must be non-null with equal length");
        }
        if (dimensions < 1 || dimensions > 3) {
            throw new IllegalArgumentException("dimensions must be 1, 2, or 3");
        }
        if (start < 0 || endExclusive > time.length || endExclusive - start < 3) {
            throw new IllegalArgumentException("Fit window must contain at least three valid samples");
        }
        for (int i = 0; i < time.length; i++) {
            if (!Double.isFinite(time[i]) || !Double.isFinite(msd[i])) {
                throw new IllegalArgumentException("Non-finite trajectory statistic at index " + i);
            }
            if (i > 0 && time[i] <= time[i - 1]) {
                throw new IllegalArgumentException("Time values must be strictly increasing");
            }
        }

        int n = endExclusive - start;
        double meanTime = 0.0;
        double meanMsd = 0.0;
        for (int i = start; i < endExclusive; i++) {
            meanTime += time[i];
            meanMsd += msd[i];
        }
        meanTime /= n;
        meanMsd /= n;

        double sxx = 0.0;
        double sxy = 0.0;
        double syy = 0.0;
        for (int i = start; i < endExclusive; i++) {
            double dx = time[i] - meanTime;
            double dy = msd[i] - meanMsd;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        if (sxx == 0.0) {
            throw new IllegalArgumentException("Fit window has zero time variance");
        }
        double slope = sxy / sxx;
        double intercept = meanMsd - slope * meanTime;

        double residualSumSquares = 0.0;
        for (int i = start; i < endExclusive; i++) {
            double residual = msd[i] - (intercept + slope * time[i]);
            residualSumSquares += residual * residual;
        }
        double rSquared = syy == 0.0 ? (residualSumSquares == 0.0 ? 1.0 : 0.0)
                : Math.max(0.0, 1.0 - residualSumSquares / syy);
        double slopeStandardError = Math.sqrt((residualSumSquares / (n - 2)) / sxx);
        double diffusivity = slope / (2.0 * dimensions);
        return new DiffusionFit(diffusivity, slope, intercept, rSquared,
                slopeStandardError, start, endExclusive, dimensions);
    }
}
