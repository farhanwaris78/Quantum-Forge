/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

import java.util.Map;

/**
 * Thermodynamic Stability & Formation Energy Calculator.
 */
public class StabilityAnalyzer {

    /**
     * E_form = E_total - sum(n_i * mu_i)
     */
    public static double calculateFormationEnergy(double totalEnergy, Map<String, Double> chemicalPotentials, Map<String, Integer> atomCounts) {
        double eSum = 0.0;
        for (String element : atomCounts.keySet()) {
            if (chemicalPotentials.containsKey(element)) {
                eSum += atomCounts.get(element) * chemicalPotentials.get(element);
            }
        }
        return totalEnergy - eSum;
    }
}
