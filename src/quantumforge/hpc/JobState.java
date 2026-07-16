/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

/**
 * Persisted remote/local scheduler job states.
 */
public enum JobState {
    STAGED,
    SUBMITTED,
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN
}
