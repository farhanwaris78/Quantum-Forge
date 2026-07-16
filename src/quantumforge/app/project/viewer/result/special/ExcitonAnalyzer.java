/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

/** Explicit 3D Wannier-Mott hydrogenic estimate; this is not a BSE calculation. */
public final class ExcitonAnalyzer {
    private static final double RYDBERG_EV = 13.605693122994;

    private ExcitonAnalyzer() { }

    /**
     * @param electronMass electron effective mass in units of the free-electron mass
     * @param holeMass hole effective mass in units of the free-electron mass
     * @param relativePermittivity dimensionless static/effective dielectric constant
     * @return estimated binding energy in eV
     */
    public static double estimateWannierMottBindingEnergy(double electronMass, double holeMass,
                                                           double relativePermittivity) {
        if (!Double.isFinite(electronMass) || !Double.isFinite(holeMass)
                || electronMass <= 0.0 || holeMass <= 0.0) {
            throw new IllegalArgumentException("Electron and hole masses must be finite and positive");
        }
        if (!Double.isFinite(relativePermittivity) || relativePermittivity <= 0.0) {
            throw new IllegalArgumentException("Relative permittivity must be finite and positive");
        }
        double reducedMass = electronMass * holeMass / (electronMass + holeMass);
        return RYDBERG_EV * reducedMass / (relativePermittivity * relativePermittivity);
    }

    /** @deprecated use the explicitly named Wannier-Mott method. */
    @Deprecated
    public static double estimateEb(double effectiveMassUp, double effectiveMassDown, double epsilon) {
        return estimateWannierMottBindingEnergy(effectiveMassUp, effectiveMassDown, epsilon);
    }
}
