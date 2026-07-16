/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.operation;

import java.util.Objects;
import java.util.Optional;

/** Immutable operation outcome; prevents no-op code from reporting a bare true. */
public final class OperationResult<T> {
    private final OperationStatus status;
    private final String code;
    private final String message;
    private final T value;
    private final Throwable cause;

    private OperationResult(OperationStatus status, String code, String message,
                            T value, Throwable cause) {
        this.status = Objects.requireNonNull(status, "status");
        this.code = requireText(code, "code");
        this.message = requireText(message, "message");
        this.value = value;
        this.cause = cause;
        if (status == OperationStatus.SUCCESS && cause != null) {
            throw new IllegalArgumentException("Successful result cannot have a failure cause");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is empty");
        }
        return value.trim();
    }

    public static <T> OperationResult<T> success(String code, String message, T value) {
        return new OperationResult<>(OperationStatus.SUCCESS, code, message, value, null);
    }

    public static <T> OperationResult<T> failed(String code, String message, Throwable cause) {
        return new OperationResult<>(OperationStatus.FAILED, code, message, null, cause);
    }

    public static <T> OperationResult<T> unsupported(String code, String message) {
        return new OperationResult<>(OperationStatus.UNSUPPORTED, code, message, null, null);
    }

    public static <T> OperationResult<T> cancelled(String code, String message) {
        return new OperationResult<>(OperationStatus.CANCELLED, code, message, null, null);
    }

    public OperationStatus getStatus() { return this.status; }
    public String getCode() { return this.code; }
    public String getMessage() { return this.message; }
    public Optional<T> getValue() { return Optional.ofNullable(this.value); }
    public Optional<Throwable> getCause() { return Optional.ofNullable(this.cause); }
    public boolean isSuccess() { return this.status == OperationStatus.SUCCESS; }

    @Override
    public String toString() {
        return this.status + " [" + this.code + "] " + this.message;
    }
}
