/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.project;

import quantumforge.operation.OperationResult;

/**
 * Final-geometry update API, now implemented transactionally (#40).
 *
 * <p>The removed prototype cleared all ATOMIC_POSITIONS cards without inserting
 * parsed coordinates and reused the input lattice instead of parsing output.
 * The current implementation parses the last complete QE geometry into the
 * property snapshot, previews it via {@link FinalGeometryUpdater}, and commits
 * through {@link FinalGeometryTransaction} (staged copies, per-mode audit
 * artifacts under {@code .quantumforge/}, verified write-through and
 * best-effort rollback).</p>
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
        // The transactional apply contains every failure mode as a typed result.
        OperationResult<quantumforge.run.parser.FinalGeometryTransaction.Plan> applied =
                quantumforge.run.parser.FinalGeometryTransaction.apply(this.project);
        if (!applied.isSuccess()) {
            return OperationResult.failed(applied.getCode(), applied.getMessage(),
                    applied.getCause().orElse(null));
        }
        return OperationResult.success("CONFIG_UPDATED", applied.getMessage(), null);
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean updateFromOutput() {
        return this.updateFromOutputResult().isSuccess();
    }
}
