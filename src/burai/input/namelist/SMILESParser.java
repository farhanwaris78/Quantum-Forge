/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.input.namelist;

/**
 * SMILES parser for importing molecular structures.
 * 
 * NanoLabo supports SMILES notation for molecule search
 * and import. This parser converts SMILES strings to
 * atomic coordinates for use in Quantum ESPRESSO.
 * 
 * For example: "CN=C=O" for methyl isocyanate
 */
public class SMILESParser {

    private String smiles;

    public SMILESParser(String smiles) {
        this.smiles = smiles != null ? smiles.trim() : "";
    }

    public boolean isValid() {
        return this.smiles != null && !this.smiles.isEmpty();
    }

    /**
     * Get the chemical formula from SMILES
     */
    public String getChemicalFormula() {
        if (!this.isValid()) return "";

        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        // Count elements from SMILES string
        // Elements are: uppercase letter optionally followed by lowercase
        for (int i = 0; i < this.smiles.length(); i++) {
            if (Character.isUpperCase(this.smiles.charAt(i))) {
                String elem = String.valueOf(this.smiles.charAt(i));
                if (i + 1 < this.smiles.length() && Character.isLowerCase(this.smiles.charAt(i + 1))) {
                    elem += this.smiles.charAt(i + 1);
                }
                counts.put(elem, counts.getOrDefault(elem, 0) + 1);
            }
        }

        StringBuilder formula = new StringBuilder();
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
}
