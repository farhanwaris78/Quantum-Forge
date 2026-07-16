/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

/** McMillan Tc estimate. Inputs must come from a converged electron-phonon workflow. */
public final class SuperconductingTcAnalyzer {
    private SuperconductingTcAnalyzer() { }

    /**
     * McMillan expression using Debye temperature (K), electron-phonon lambda,
     * and Coulomb pseudopotential mu*. This is not the full Allen-Dynes formula.
     */
    public static double calculateMcMillanTc(double lambda, double debyeTemperatureK, double muStar) {
        if (!Double.isFinite(lambda) || lambda <= 0.0) {
            throw new IllegalArgumentException("lambda must be finite and positive");
        }
        if (!Double.isFinite(debyeTemperatureK) || debyeTemperatureK <= 0.0) {
            throw new IllegalArgumentException("Debye temperature must be finite and positive");
        }
        if (!Double.isFinite(muStar) || muStar < 0.0 || muStar >= 1.0) {
            throw new IllegalArgumentException("mu* must be finite in [0, 1)");
        }
        double denominator = lambda - muStar * (1.0 + 0.62 * lambda);
        if (denominator <= 0.0) {
            throw new IllegalArgumentException("McMillan denominator is non-positive for these inputs");
        }
        double exponent = -1.04 * (1.0 + lambda) / denominator;
        return debyeTemperatureK / 1.45 * Math.exp(exponent);
    }

    /** @deprecated use calculateMcMillanTc; the second argument is Debye temperature in K. */
    @Deprecated
    public static double calculateTc(double lambda, double thetaD, double muStar) {
        return calculateMcMillanTc(lambda, thetaD, muStar);
    }
}
