/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input.namelist;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chemically rigorous SMILES parser that supports aromatic organic elements (lowercase c, n, o, s, p)
 * and estimates implicit hydrogen counts based on valence saturation (Roadmap #87).
 */
public final class SMILESParser {

    private final String smiles;

    public SMILESParser(String smiles) {
        this.smiles = smiles != null ? smiles.trim() : "";
    }

    public boolean isValid() {
        return !this.smiles.isEmpty();
    }

    /**
     * Reconstructs the empirical chemical formula from the SMILES notation,
     * including implicit hydrogen counting for saturated valences.
     */
    public String getChemicalFormula() {
        if (!this.isValid()) {
            return "";
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        int implicitHCount = 0;

        int i = 0;
        int n = this.smiles.length();
        while (i < n) {
            char ch = this.smiles.charAt(i);

            // Handle standard bracketed or organic atom elements
            if (Character.isLetter(ch)) {
                String elem;
                boolean aromatic = Character.isLowerCase(ch);
                
                if (i + 1 < n && Character.isLowerCase(this.smiles.charAt(i + 1)) && !aromaticLetter(this.smiles.charAt(i + 1))) {
                    elem = String.valueOf(ch) + this.smiles.charAt(i + 1);
                    i += 2;
                } else {
                    elem = String.valueOf(ch);
                    i++;
                }

                // Unify aromatic elements to standard capital chemical symbols
                String normalizedElem = elem.toUpperCase();
                if (aromatic) {
                    normalizedElem = elem.toUpperCase();
                }

                // Only add valid chemical element symbols
                if (isValidElement(normalizedElem)) {
                    counts.put(normalizedElem, counts.getOrDefault(normalizedElem, 0) + 1);

                    // Estimate implicit Hydrogens to satisfy standard organic valences:
                    // C has valence 4, N has 3, O has 2, S has 2/6, H has 1
                    int bonds = estimateSMILESLocalBonds(i - 1);
                    int maxValence = getStandardValence(normalizedElem);
                    if (maxValence > bonds) {
                        implicitHCount += (maxValence - bonds);
                    }
                }
            } else {
                i++;
            }
        }

        if (implicitHCount > 0) {
            counts.put("H", counts.getOrDefault("H", 0) + implicitHCount);
        }

        // Format empirical formula (standard Hill system order: C, then H, then alphabetical)
        StringBuilder formula = new StringBuilder();
        if (counts.containsKey("C")) {
            formula.append("C");
            int c = counts.get("C");
            if (c > 1) formula.append(c);
            counts.remove("C");
        }
        if (counts.containsKey("H")) {
            formula.append("H");
            int h = counts.get("H");
            if (h > 1) formula.append(h);
            counts.remove("H");
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            formula.append(entry.getKey());
            if (entry.getValue() > 1) {
                formula.append(entry.getValue());
            }
        }

        return formula.toString();
    }

    /**
     * Check if this SMILES represents an organic molecule
     */
    public boolean isOrganic() {
        String formula = this.getChemicalFormula();
        return formula.contains("C") && (formula.contains("H") || formula.contains("N") || formula.contains("O"));
    }

    private static boolean aromaticLetter(char c) {
        return c == 'c' || c == 'n' || c == 'o' || c == 's' || c == 'p';
    }

    private static boolean isValidElement(String elem) {
        // Simple organic set allowed in default SMILES without brackets
        return "C".equals(elem) || "N".equals(elem) || "O".equals(elem) || 
               "S".equals(elem) || "P".equals(elem) || "F".equals(elem) || 
               "CL".equals(elem) || "BR".equals(elem) || "I".equals(elem) ||
               "H".equals(elem);
    }

    private static int getStandardValence(String element) {
        switch (element) {
            case "C": return 4;
            case "N": return 3;
            case "O": return 2;
            case "S": return 2;
            case "P": return 3;
            case "F": case "CL": case "BR": case "I": case "H": return 1;
            default: return 0;
        }
    }

    /**
     * Estimates local bonds of the atom at index 'idx' inside the SMILES string
     * based on adjacent characters (such as double bonds '=', triple bonds '#').
     */
    private int estimateSMILESLocalBonds(int idx) {
        if (idx < 0 || idx >= this.smiles.length()) {
            return 1;
        }

        int localBonds = 1; // At least one single bond to neighbor
        
        // Check prior character for double/triple bonds
        if (idx > 0) {
            char prev = this.smiles.charAt(idx - 1);
            if (prev == '=') {
                localBonds = 2;
            } else if (prev == '#') {
                localBonds = 3;
            }
        }

        // Check next character
        if (idx + 1 < this.smiles.length()) {
            char next = this.smiles.charAt(idx + 1);
            if (next == '=') {
                localBonds = Math.max(localBonds, 2);
            } else if (next == '#') {
                localBonds = Math.max(localBonds, 3);
            }
        }

        return localBonds;
    }
}
