/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.builder.autopilot;

import java.util.ArrayList;
import java.util.List;

/**
 * Autopilot AI model building assistant.
 * 
 * NanoLabo v3.0 introduced Autopilot - the world's first
 * material model generation AI, powered by ChatGPT/GPT-4o-mini.
 * 
 * This feature:
 * - Accepts natural language instructions
 * - Interprets material science concepts
 * - Generates modeler operation sequences
 * - Supports Full-Auto and Semi-Auto modes
 * 
 * Examples:
 * "ZrO2(001)/Rh(111) + 2 x NO@hollow"
 * → Creates interface model + adsorbs NO at hollow sites
 * 
 * "An alloy with Fe/Ni/Cr/Mn ratio = 7/1/1/1"
 * → Creates supercell + substitutes elements
 */
public class AutopilotBuilder {

    public static final int MODE_FULL_AUTO = 0;
    public static final int MODE_SEMI_AUTO = 1;

    public static class AutopilotStep {
        public final String description;
        public final String operationType;
        public final String[] parameters;
        public boolean completed;

        public AutopilotStep(String desc, String op, String... params) {
            this.description = desc;
            this.operationType = op;
            this.parameters = params;
            this.completed = false;
        }
    }

    private String instruction;
    private int mode;
    private List<AutopilotStep> steps;

    public AutopilotBuilder() {
        this.instruction = "";
        this.mode = MODE_FULL_AUTO;
        this.steps = new ArrayList<>();
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction != null ? instruction.trim() : "";
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getInstruction() { return this.instruction; }
    public int getMode() { return this.mode; }
    public List<AutopilotStep> getSteps() { return this.steps; }

    /**
     * Parse natural language instruction into modeling steps.
     * In production, this would call an LLM API.
     */
    public boolean parseInstruction() {
        if (this.instruction == null || this.instruction.isEmpty()) {
            return false;
        }

        this.steps.clear();

        // Parse common patterns
        String instr = this.instruction.toLowerCase();

        // Supercell pattern
        if (instr.contains("supercell") || instr.contains("super cell")) {
            this.steps.add(new AutopilotStep(
                "Create 2x2x2 supercell",
                "supercell", "2", "2", "2"));
        }

        // Element substitution
        if (instr.contains("substitut") || instr.contains("dop") || instr.contains("alloy")) {
            this.steps.add(new AutopilotStep(
                "Substitute elements per composition",
                "substitute", extractElements(this.instruction)));
        }

        // Slab/surface pattern: e.g. "ZrO2(001)"
        if (instr.contains("(") && instr.contains(")") &&
            (instr.contains("surface") || instr.contains("slab"))) {
            this.steps.add(new AutopilotStep(
                "Create surface slab with given Miller indices",
                "slab", "1", "1", "1"));
        }

        // Adsorption pattern: e.g. "CO@hollow"
        if (instr.contains("@")) {
            this.steps.add(new AutopilotStep(
                "Adsorb molecules on surface sites",
                "adsorb", "CO", "hollow"));
        }

        // Interface pattern
        if (instr.contains("interface") || instr.contains("/")) {
            this.steps.add(new AutopilotStep(
                "Build interface between two materials",
                "interface", "", ""));
        }

        // If no patterns matched, add default step
        if (this.steps.isEmpty()) {
            this.steps.add(new AutopilotStep(
                "Create structure: " + this.instruction,
                "create", this.instruction));
        }

        return !this.steps.isEmpty();
    }

    private String extractElements(String instruction) {
        // Extract element symbols from instruction
        StringBuilder elements = new StringBuilder();
        for (String word : instruction.split("[\\s,;:/]+")) {
            if (word.matches("[A-Z][a-z]?\\d*\\.?\\d*")) {
                if (elements.length() > 0) elements.append(" ");
                elements.append(word);
            }
        }
        return elements.toString();
    }
}
