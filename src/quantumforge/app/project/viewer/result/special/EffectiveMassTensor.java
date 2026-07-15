/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

import quantumforge.project.property.BandData;

/**
 * Effective Mass Tensor calculation from band curvature.
 * m* = hbar^2 / (d^2E / dk^2)
 */
public class EffectiveMassTensor {

    public static double calculateMass(BandData bandData, int kIndex) {
        if (bandData == null || kIndex <= 0 || kIndex >= bandData.numPoints() - 1) {
            return 1.0; // Default to free electron mass
        }

        // Second order finite difference
        double dk = bandData.getCoordinate(kIndex + 1) - bandData.getCoordinate(kIndex);
        double e1 = bandData.getEnergy(kIndex - 1);
        double e2 = bandData.getEnergy(kIndex);
        double e3 = bandData.getEnergy(kIndex + 1);

        double d2Edk2 = (e1 - 2*e2 + e3) / (dk * dk);
        if (Math.abs(d2Edk2) < 1e-9) return 999.9;

        // Constants conversion for QE units (Ry, Bohr)
        return 1.0 / d2Edk2; 
    }
}
