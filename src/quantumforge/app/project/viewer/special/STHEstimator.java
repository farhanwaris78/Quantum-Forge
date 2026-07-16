/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** STH efficiency requires a documented solar-spectrum and electrochemical model. */
public final class STHEstimator {
    private STHEstimator() { }

    public static double calculateSTH(double bandGap, double conductionBandMin, double valenceBandMax) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Solar-to-hydrogen efficiency estimation");
    }
}
