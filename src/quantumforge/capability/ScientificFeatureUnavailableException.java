/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.capability;

/** Raised instead of returning a fabricated numerical result from a prototype. */
public final class ScientificFeatureUnavailableException extends UnsupportedOperationException {
    private static final long serialVersionUID = 1L;

    private final String capabilityId;

    public ScientificFeatureUnavailableException(String capabilityId, String operation) {
        super(createMessage(capabilityId, operation));
        this.capabilityId = capabilityId;
    }

    private static String createMessage(String capabilityId, String operation) {
        Capability capability = CapabilityRegistry.get(capabilityId);
        String name = capability == null ? capabilityId : capability.getName();
        String required = capability == null ? "" : capability.getRequiredForSupport();
        return operation + " is unavailable in this release (" + name + ")."
                + (required.isEmpty() ? "" : " " + required);
    }

    public String getCapabilityId() {
        return this.capabilityId;
    }
}
