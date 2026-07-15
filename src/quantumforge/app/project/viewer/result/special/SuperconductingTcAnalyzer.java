/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

/**
 * Superconducting Tc Analyzer (McMillan-Allen-Dynes formula).
 */
public class SuperconductingTcAnalyzer {

    /**
     * Tc = (theta_D / 1.45) * exp[ -1.04(1+lambda) / (lambda - mu*(1+0.62lambda)) ]
     */
    public static double calculateTc(double lambda, double thetaD, double muStar) {
        if (lambda <= muStar) return 0.0;
        double numerator = -1.04 * (1.0 + lambda);
        double denominator = lambda - muStar * (1.0 + 0.62 * lambda);
        return (thetaD / 1.45) * Math.exp(numerator / denominator);
    }
}
