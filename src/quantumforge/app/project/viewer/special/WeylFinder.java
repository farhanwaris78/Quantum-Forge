/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** Weyl-node searches require an interpolated Hamiltonian and chirality-flux validation. */
public final class WeylFinder {
    private WeylFinder() { }

    public static void searchNodes(double[][] kGrid, double[] bandGaps) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Weyl-node search");
    }
}
