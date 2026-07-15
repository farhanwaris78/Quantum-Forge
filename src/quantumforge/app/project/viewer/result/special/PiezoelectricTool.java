/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

/**
 * Piezoelectric Tensor Calculator.
 * e_ij = dP_i / de_j (Polarization change w.r.t strain)
 */
public class PiezoelectricTool {

    public static double calculateCoefficient(double deltaPolarization, double strain) {
        if (Math.abs(strain) < 1e-9) return 0.0;
        return deltaPolarization / strain;
    }

    /**
     * Get the piezoelectric tensor e_ij (C/m^2)
     */
    public static double[][] getTensorStub() {
        return new double[3][6]; // Standard Voigt notation
    }
}
