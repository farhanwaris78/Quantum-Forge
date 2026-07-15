/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

/**
 * Exciton Binding Energy Analyzer.
 * Uses results from Bethe-Salpeter Equation (BSE) or simple models.
 */
public class ExcitonAnalyzer {

    /**
     * Estimate exciton binding energy from dielectric constant and effective masses.
     * Eb = 13.6 * mu / epsilon^2
     */
    public static double estimateEb(double effectiveMassUp, double effectiveMassDown, double epsilon) {
        if (epsilon <= 0) return 0.0;
        double mu = (effectiveMassUp * effectiveMassDown) / (effectiveMassUp + effectiveMassDown);
        return 13.6 * mu / (epsilon * epsilon);
    }
}
