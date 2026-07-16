/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** Hyperfine analysis requires parsed all-electron/GIPAW tensors and isotope metadata. */
public final class HyperfineMapper {
    private HyperfineMapper() { }

    public static double calculateAiso(double psi0sq, double gn) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Hyperfine contact calculation");
    }
}
