/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import java.util.Map;

/** Low-level formation-energy arithmetic with explicit reference completeness checks. */
public final class StabilityAnalyzer {
    private StabilityAnalyzer() { }

    /** E_form = E_total - sum(n_i * mu_i), with all energies in the same unit. */
    public static double calculateFormationEnergy(double totalEnergy,
                                                   Map<String, Double> chemicalPotentials,
                                                   Map<String, Integer> atomCounts) {
        if (!Double.isFinite(totalEnergy) || chemicalPotentials == null || atomCounts == null
                || atomCounts.isEmpty()) {
            throw new IllegalArgumentException("Total energy, references, and atom counts are required");
        }
        double referenceEnergy = 0.0;
        for (Map.Entry<String, Integer> entry : atomCounts.entrySet()) {
            String element = entry.getKey();
            Integer count = entry.getValue();
            Double chemicalPotential = chemicalPotentials.get(element);
            if (element == null || element.trim().isEmpty() || count == null || count < 0) {
                throw new IllegalArgumentException("Invalid atom count entry: " + element);
            }
            if (chemicalPotential == null || !Double.isFinite(chemicalPotential)) {
                throw new IllegalArgumentException("Missing/invalid chemical potential for " + element);
            }
            referenceEnergy += count * chemicalPotential;
        }
        return totalEnergy - referenceEnergy;
    }
}
