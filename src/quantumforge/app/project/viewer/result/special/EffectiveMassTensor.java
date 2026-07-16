/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;
import quantumforge.project.property.BandData;

/**
 * Placeholder API retained for source compatibility.
 * A path coordinate does not define a 3D reciprocal-space Hessian or its units.
 */
public final class EffectiveMassTensor {
    private EffectiveMassTensor() { }

    public static double calculateMass(BandData bandData, int kIndex) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Effective-mass tensor fitting");
    }
}
