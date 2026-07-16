/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** Orbital magnetization requires a gauge-consistent Berry/Wannier workflow. */
public final class OrbitalMagnetization {
    private OrbitalMagnetization() { }

    public static double calculateM_orb(double[][] berryCurvature, double[] occNumbers) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Orbital magnetization");
    }
}
