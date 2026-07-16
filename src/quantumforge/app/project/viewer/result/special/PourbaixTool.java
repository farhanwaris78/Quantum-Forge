/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

/** Low-level computational-hydrogen-electrode correction, not a phase-diagram builder. */
public final class PourbaixTool {
    private static final double BOLTZMANN_EV_PER_K = 8.617333262145e-5;

    private PourbaixTool() { }

    /**
     * Apply G(U,pH)=G0-ne*U+nH*kB*T*ln(10)*pH.
     * Positive protonStoichiometry denotes protons consumed by the reaction as written.
     * Energies are eV and potential is volts per elementary charge.
     */
    public static double applyCheCorrection(double referenceFreeEnergyEv, int electronStoichiometry,
                                            double potentialV, int protonStoichiometry,
                                            double pH, double temperatureK) {
        if (!Double.isFinite(referenceFreeEnergyEv) || !Double.isFinite(potentialV)
                || !Double.isFinite(pH) || !Double.isFinite(temperatureK) || temperatureK <= 0.0) {
            throw new IllegalArgumentException("CHE inputs must be finite and temperature positive");
        }
        double phTerm = protonStoichiometry * BOLTZMANN_EV_PER_K
                * temperatureK * Math.log(10.0) * pH;
        return referenceFreeEnergyEv - electronStoichiometry * potentialV + phTerm;
    }

    /** @deprecated uses the fixed temperature 298.15 K. */
    @Deprecated
    public static double calculateFreeEnergy(double gDft, int electrons, double potential,
                                             int protons, double pH) {
        return applyCheCorrection(gDft, electrons, potential, protons, pH, 298.15);
    }
}
