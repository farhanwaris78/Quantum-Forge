/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** Low-level finite-difference helper; callers are responsible for units and branch tracking. */
public final class PiezoelectricTool {
    private PiezoelectricTool() { }

    public static double calculateCoefficient(double deltaPolarization, double strain) {
        if (!Double.isFinite(deltaPolarization) || !Double.isFinite(strain)) {
            throw new IllegalArgumentException("Polarization change and strain must be finite");
        }
        if (Math.abs(strain) < 1.0e-12) {
            throw new IllegalArgumentException("Strain is too small for a stable finite difference");
        }
        return deltaPolarization / strain;
    }

    public static double[][] getTensorStub() {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Piezoelectric tensor calculation");
    }
}
