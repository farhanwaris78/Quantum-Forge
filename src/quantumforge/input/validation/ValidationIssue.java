/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import java.util.Objects;

/** One deterministic, documented preflight finding. */
public final class ValidationIssue {
    private final ValidationSeverity severity;
    private final String code;
    private final String message;
    private final String documentationUrl;

    public ValidationIssue(ValidationSeverity severity, String code,
                           String message, String documentationUrl) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.code = requireText(code, "code");
        this.message = requireText(message, "message");
        this.documentationUrl = documentationUrl == null ? "" : documentationUrl.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
        return value.trim();
    }

    public ValidationSeverity getSeverity() { return this.severity; }
    public String getCode() { return this.code; }
    public String getMessage() { return this.message; }
    public String getDocumentationUrl() { return this.documentationUrl; }

    @Override
    public String toString() {
        return this.severity + " [" + this.code + "] " + this.message;
    }
}
