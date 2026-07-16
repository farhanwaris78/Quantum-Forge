/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.capability;

/** Scientific and integration maturity exposed consistently by the CLI and GUI. */
public enum CapabilityStatus {
    SUPPORTED("Supported"),
    PARTIAL("Partial"),
    EXPERIMENTAL("Experimental"),
    UNAVAILABLE("Unavailable");

    private final String label;

    CapabilityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }
}
