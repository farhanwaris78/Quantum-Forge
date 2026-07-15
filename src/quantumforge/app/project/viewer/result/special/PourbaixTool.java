/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

/**
 * Pourbaix Diagram (Potential-pH) stability tool.
 */
public class PourbaixTool {

    /**
     * Calculate free energy of a species under electrochemical conditions.
     * G = G_dft + n*e*U - m*pH*kT*ln(10)
     */
    public static double calculateFreeEnergy(double gDft, int electrons, double potential, int protons, double pH) {
        double kTln10 = 0.0592; // at 298K
        return gDft - electrons * potential + protons * kTln10 * pH;
    }
}
