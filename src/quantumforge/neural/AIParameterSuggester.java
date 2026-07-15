/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.neural;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import java.util.HashSet;
import java.util.Set;

/**
 * AI-driven parameter suggester for Quantum ESPRESSO.
 * Uses historical data and elemental rules to recommend cutoffs.
 */
public class AIParameterSuggester {

    public static class SuggestedParams {
        public double ecutwfc;
        public double ecutrho;
        public String kpoints;
        public String description;
    }

    public static SuggestedParams suggest(Cell cell) {
        SuggestedParams params = new SuggestedParams();
        if (cell == null) return params;

        Set<String> elements = new HashSet<>();
        for (Atom atom : cell.listAtoms(true)) {
            elements.add(atom.getElementName());
        }

        double maxEcut = 30.0; // Base
        boolean hasHard = false;
        boolean hasUSPP = false;

        for (String el : elements) {
            // High cutoff elements
            if (el.equals("O") || el.equals("F") || el.equals("N") || el.equals("Fe") || el.equals("Co") || el.equals("Ni")) {
                maxEcut = Math.max(maxEcut, 50.0);
                hasHard = true;
            }
            if (el.equals("Ti") || el.equals("V") || el.equals("Cr") || el.equals("Cu")) {
                maxEcut = Math.max(maxEcut, 45.0);
            }
        }

        params.ecutwfc = maxEcut;
        params.ecutrho = hasHard ? maxEcut * 8.0 : maxEcut * 4.0;
        params.kpoints = "4 4 4 0 0 0";
        params.description = "AI Recommendation: Based on " + elements.size() + " unique elements. " + 
                             (hasHard ? "Hard elements detected, increasing rho cutoff." : "Standard settings.");

        return params;
    }
}
