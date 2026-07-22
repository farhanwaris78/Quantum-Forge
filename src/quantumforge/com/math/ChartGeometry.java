/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Chart axis/geometry helpers for the result-channel panels (Roadmap #109
 * chart slice): the mapping from data space to canvas pixels and the "nice
 * number" tick selection, headless so the panel's coordinate math is
 * unit-verified instead of eyeballed. Pure linear algebra; no rendering.
 *
 * <p>Honesty rules: degenerate ranges are PADDED (never divided through),
 * non-finite or inverted extents REFUSE, and ticks are generated only
 * inside the data extent (the caller pads first if it wants margin ticks).</p>
 */
public final class ChartGeometry {

    private ChartGeometry() {
        // Utility
    }

    /**
     * Linear data -> screen mapping. dataMin == dataMax, inverted extents or
     * non-finite bounds refuse (a silent NaN pixel is worse than a loud
     * refusal). Works for flipped screen axes (screenMax < screenMin).
     */
    public static double mapLinear(double value, double dataMin, double dataMax,
            double screenMin, double screenMax) {
        if (!Double.isFinite(dataMin) || !Double.isFinite(dataMax)
                || !Double.isFinite(screenMin) || !Double.isFinite(screenMax)) {
            throw new IllegalArgumentException("Non-finite extent handed to the mapper.");
        }
        if (dataMax <= dataMin) {
            throw new IllegalArgumentException(
                    "Degenerate or inverted data extent [" + dataMin + ", " + dataMax
                            + "] - pad it first (ChartGeometry.padded).");
        }
        return screenMin + (value - dataMin) * (screenMax - screenMin) / (dataMax - dataMin);
    }

    /**
     * Padded data range {lo, hi}: a 5% margin on both ends (the polynomial
     * case), or a symmetric pad around zero/equal values so a constant
     * series still has a drawable extent. Inverted input refuses.
     */
    public static double[] padded(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("Non-finite range to pad.");
        }
        if (max < min) {
            throw new IllegalArgumentException(
                    "Inverted range [" + min + ", " + max + "]; padding a lie is refused.");
        }
        if (min == max) {
            double pad = min == 0.0 ? 0.5 : Math.abs(min) * 0.05;
            return new double[] {min - pad, max + pad};
        }
        double margin = (max - min) * 0.05;
        return new double[] {min - margin, max + margin};
    }

    /**
     * "Nice number" ticks inside [min, max]: step = 10^k x {1, 2, 5, 10}
     * chosen so the un-padded extent carries about targetCount intervals,
     * ticks landing ON multiples of the step within the extent (ticks never
     * appear outside the data - that would draw margins as measurements).
     * Degenerate min==max is padded first via {@link #padded}.
     */
    public static double[] niceTicks(double min, double max, int targetCount) {
        if (targetCount < 2) {
            throw new IllegalArgumentException("A tick layout needs at least 2 intervals.");
        }
        double lo = min;
        double hi = max;
        if (lo == hi) {
            double[] padded = padded(lo, hi);
            lo = padded[0];
            hi = padded[1];
        }
        if (hi < lo) {
            throw new IllegalArgumentException(
                    "Inverted extent [" + lo + ", " + hi + "] handed to the ticker.");
        }
        double raw = (hi - lo) / targetCount;
        double decade = Math.pow(10.0, Math.floor(Math.log10(raw)));
        double norm = raw / decade;
        double step;
        if (norm < 1.5) {
            step = decade;
        } else if (norm < 3.0) {
            step = 2.0 * decade;
        } else if (norm < 7.0) {
            step = 5.0 * decade;
        } else {
            step = 10.0 * decade;
        }
        List<Double> ticks = new ArrayList<>();
        double first = Math.ceil(lo / step - 1.0e-12) * step;
        for (double tick = first; tick <= hi + 1.0e-12; tick += step) {
            // normalize -0.0 to 0.0 so "-0" never reaches a label
            double value = tick == 0.0 ? 0.0 : Math.rint(tick / step) * step;
            if (value >= lo - 1.0e-12 && value <= hi + 1.0e-12
                    && (ticks.isEmpty() || Math.abs(value - ticks.get(ticks.size() - 1))
                            > 1.0e-12 * step)) {
                ticks.add(value + 0.0);
            }
        }
        double[] out = new double[ticks.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ticks.get(i);
        }
        return out;
    }

    /** Extent of a series as {min, max}; empty input refuses. */
    public static double[] extent(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No values to bound.");
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Non-finite series value " + value + "; the chart draws only clean"
                                + " rows (parser already counted the skipped ones).");
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new double[] {min, max};
    }
}
