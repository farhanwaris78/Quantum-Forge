/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.app.project.viewer.special;

/**
 * Solar-to-Hydrogen (STH) Efficiency Estimator.
 */
public class STHEstimator {

    /**
     * Approximate STH efficiency based on band gap and light absorption.
     */
    public static double calculateSTH(double bandGap, double conductionBandMin, double valenceBandMax) {
        // HER potential: 0.0V vs RHE, OER potential: 1.23V vs RHE
        double overpotentialHER = 0.0 - conductionBandMin;
        double overpotentialOER = valenceBandMax - 1.23;

        if (overpotentialHER < 0 || overpotentialOER < 0) return 0.0; // Thermodynamic mismatch

        // Use standard solar spectrum integration for Eg (simplified)
        if (bandGap < 1.23) return 0.0;
        
        // Efficiency scales with how well it captures the visible range while overcoming 1.23eV
        return (1.23 / bandGap) * 100.0 * 0.15; // Simplified factor
    }
}
