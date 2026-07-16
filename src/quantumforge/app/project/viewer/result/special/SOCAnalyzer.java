/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** SOC mapping needs spinor wavefunction/matrix-element data, not grid dimensions alone. */
public final class SOCAnalyzer {
    public double[][] mapSOC(int nx, int ny) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Spin-orbit-coupling mapping");
    }
}
