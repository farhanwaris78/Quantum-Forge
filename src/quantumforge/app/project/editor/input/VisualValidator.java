/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.app.project.editor.input;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Visual validation for input fields.
 *
 * Production-grade scientific input editors provide:
 * - Yellow input fields: value is not optimal/appropriate
 * - Red input fields: value will cause calculation error
 * - Tooltips showing valid ranges
 * - Default value buttons
 * - Extended slider domain options
 * - Right-click context menus on fields
 */
public class VisualValidator {

    public static final int VALID_OK = 0;
    public static final int VALID_WARNING = 1;  // Yellow
    public static final int VALID_ERROR = 2;     // Red

    public static class ValidationRule {
        public final String fieldName;
        public final Predicate<Double> doubleTest;
        public final Predicate<Integer> intTest;
        public final String warningMessage;
        public final String errorMessage;
        public final double minValue;
        public final double maxValue;
        public final double defaultValue;

        public ValidationRule(String name, double min, double max, double def,
                               String warning, String error) {
            this.fieldName = name;
            this.minValue = min;
            this.maxValue = max;
            this.defaultValue = def;
            this.warningMessage = warning;
            this.errorMessage = error;
            this.doubleTest = v -> v >= min && v <= max;
            this.intTest = v -> v >= (int)min && v <= (int)max;
        }
    }

    private Map<String, ValidationRule> rules;

    public VisualValidator() {
        this.rules = new HashMap<>();
        this.initDefaultRules();
    }

    private void initDefaultRules() {
        // SCF parameters
        this.rules.put("ecutwfc", new ValidationRule("ecutwfc", 5, 200, 50,
            "Low cutoff may affect accuracy", "Cutoff too extreme"));
        this.rules.put("ecutrho", new ValidationRule("ecutrho", 20, 2000, 400,
            "Charge density cutoff should be ≥ 4× ecutwfc", "Charge density cutoff too low"));
        this.rules.put("degauss", new ValidationRule("degauss", 0.001, 0.5, 0.01,
            "Small degauss may cause convergence issues", "Degauss out of range"));
        this.rules.put("conv_thr", new ValidationRule("conv_thr", 1e-12, 1e-2, 1e-6,
            "Very tight convergence may be slow", "Convergence threshold too loose"));

        // k-points
        this.rules.put("kpoints", new ValidationRule("kpoints", 1, 20, 4,
            "Very dense k-mesh may be slow", "Invalid k-point grid"));

        // Optimization
        this.rules.put("forc_conv_thr", new ValidationRule("forc_conv_thr", 1e-5, 1.0, 1e-3,
            "Very tight force convergence", "Force convergence too loose"));
        this.rules.put("press_conv_thr", new ValidationRule("press_conv_thr", 0.01, 100, 0.5,
            "Tight pressure convergence", "Pressure convergence too loose"));
        this.rules.put("nstep", new ValidationRule("nstep", 1, 10000, 100,
            "Very large number of steps", "No steps specified"));
    }

    public void addRule(ValidationRule rule) {
        if (rule != null) this.rules.put(rule.fieldName, rule);
    }

    /**
     * Validate a double value and return status (OK/WARNING/ERROR)
     */
    public int validate(String fieldName, double value) {
        ValidationRule rule = this.rules.get(fieldName);
        if (rule == null) return VALID_OK;

        if (value < rule.minValue * 0.5 || value > rule.maxValue * 1.5) {
            return VALID_ERROR;
        }
        if (value < rule.minValue || value > rule.maxValue) {
            return VALID_WARNING;
        }
        return VALID_OK;
    }

    /**
     * Get the default value for a field
     */
    public double getDefaultValue(String fieldName) {
        ValidationRule rule = this.rules.get(fieldName);
        return rule != null ? rule.defaultValue : 0.0;
    }

    /**
     * Get the tooltip message for a field
     */
    public String getTooltip(String fieldName) {
        ValidationRule rule = this.rules.get(fieldName);
        if (rule == null) return "";
        return String.format("Range: [%.4f, %.4f] | Default: %.4f\n%s\n%s",
            rule.minValue, rule.maxValue, rule.defaultValue,
            rule.warningMessage, rule.errorMessage);
    }
}
