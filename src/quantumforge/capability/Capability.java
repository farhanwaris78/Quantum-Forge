/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.capability;

import java.util.Objects;

/** Immutable description of an end-user capability and the evidence behind it. */
public final class Capability {
    private final String id;
    private final String name;
    private final CapabilityStatus status;
    private final String summary;
    private final String requiredForSupport;

    public Capability(String id, String name, CapabilityStatus status,
                      String summary, String requiredForSupport) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.status = Objects.requireNonNull(status, "status");
        this.summary = requireText(summary, "summary");
        this.requiredForSupport = requiredForSupport == null ? "" : requiredForSupport.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
        return value.trim();
    }

    public String getId() { return this.id; }
    public String getName() { return this.name; }
    public CapabilityStatus getStatus() { return this.status; }
    public String getSummary() { return this.summary; }
    public String getRequiredForSupport() { return this.requiredForSupport; }

    public boolean isProductionSupported() {
        return this.status == CapabilityStatus.SUPPORTED;
    }

    public String toDisplayString() {
        StringBuilder text = new StringBuilder();
        text.append('[').append(this.status.getLabel()).append("] ")
            .append(this.name).append(": ").append(this.summary);
        if (!this.requiredForSupport.isEmpty()) {
            text.append(" Required next: ").append(this.requiredForSupport);
        }
        return text.toString();
    }
}
