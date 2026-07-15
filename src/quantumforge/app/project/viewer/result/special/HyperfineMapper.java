/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

/**
 * Hyperfine Interaction Mapping tool.
 * Visualizes Fermi contact term and dipole-dipole interaction.
 */
public class HyperfineMapper {

    /**
     * Calculate hyperfine constant A_iso (MHz)
     * A_iso = (2/3) * mu0 * ge * gn * muN * |psi(0)|^2
     */
    public static double calculateAiso(double psi0sq, double gn) {
        double constant = 23.685; // Mock constant for ge, mu0, etc.
        return constant * gn * psi0sq;
    }
}
