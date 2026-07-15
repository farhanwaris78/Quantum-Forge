/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.app.project.viewer.special;

import java.util.List;

/**
 * Solid-State Diffusivity Calculator.
 */
public class DiffusivityCalculator {

    /**
     * Calculate D from MSD: D = lim (t->inf) <|r(t)-r(0)|^2> / (2*d*t)
     */
    public static double calculateD(double[] msd, double[] time, int dimensions) {
        if (msd.length < 2 || msd.length != time.length) return 0.0;
        
        // Simple slope calculation between start and end
        double slope = (msd[msd.length-1] - msd[0]) / (time[time.length-1] - time[0]);
        return slope / (2.0 * dimensions);
    }
}
