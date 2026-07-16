/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;

/** Scalar work-function helper. Energies must use the same zero and unit (normally eV). */
public final class WorkFunctionMapper {
    private final double fermiEnergy;
    private final double vacuumLevel;

    public WorkFunctionMapper(double fermi, double vacuum) {
        if (!Double.isFinite(fermi) || !Double.isFinite(vacuum)) {
            throw new IllegalArgumentException("Fermi and vacuum energies must be finite");
        }
        this.fermiEnergy = fermi;
        this.vacuumLevel = vacuum;
    }

    public double getWorkFunction() {
        return this.vacuumLevel - this.fermiEnergy;
    }

    /**
     * Local maps require a parsed three-dimensional electrostatic potential;
     * random spatial variations are scientifically invalid.
     */
    public double[][] generateMap(int nx, int ny) {
        throw new ScientificFeatureUnavailableException(CapabilityRegistry.ADVANCED_SCIENCE,
                "Local work-function mapping");
    }
}
