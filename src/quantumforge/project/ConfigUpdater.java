/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.project;

import quantumforge.operation.OperationResult;

/**
 * Final-geometry update API retained in a fail-closed state.
 *
 * <p>The removed prototype cleared all ATOMIC_POSITIONS cards without inserting
 * parsed coordinates and reused the input lattice instead of parsing output.
 * A future implementation must parse the last complete QE geometry into a new
 * immutable snapshot, preview it, update copies transactionally, and roll back
 * on any failure.</p>
 */
public final class ConfigUpdater {
    private final Project project;

    public ConfigUpdater(Project project) {
        this.project = project;
    }

    public OperationResult<Void> updateFromOutputResult() {
        if (this.project == null) {
            return OperationResult.failed("PROJECT_MISSING", "No project was supplied.", null);
        }
        // Preview path is available; apply remains fail-closed until card rewrite lands.
        return quantumforge.run.parser.FinalGeometryUpdater.apply(this.project);
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean updateFromOutput() {
        return this.updateFromOutputResult().isSuccess();
    }
}
