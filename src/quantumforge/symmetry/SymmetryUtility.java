/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.symmetry;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.operation.OperationResult;

/**
 * Crystal lattice conversion helpers.
 *
 * <p>Primitive/conventional conversion requires a working spglib sidecar.
 * Until that service returns a transformed cell payload, methods remain
 * fail-closed and never silently return the original cell as if converted.</p>
 */
public final class SymmetryUtility {

    private SymmetryUtility() {
        // Utility.
    }

    public static Cell convertToPrimitive(Cell cell) throws ZeroVolumCellException {
        if (cell == null) {
            return null;
        }
        OperationResult<SpglibService.Dataset> dataset =
                SpglibService.detectDefault().getDataset(cell, 1.0e-5);
        if (!dataset.isSuccess()) {
            throw new UnsupportedOperationException(
                    "Primitive-cell conversion unavailable: " + dataset.getMessage());
        }
        // Protocol v1 only returns symmetry metadata, not a transformed cell.
        throw new UnsupportedOperationException(
                "spglib dataset is available (" + dataset.getValue().get().getInternationalSymbol()
                        + "), but cell standardization payload is not implemented in protocol v1.");
    }

    public static Cell convertToConventional(Cell cell) throws ZeroVolumCellException {
        if (cell == null) {
            return null;
        }
        OperationResult<SpglibService.Dataset> dataset =
                SpglibService.detectDefault().getDataset(cell, 1.0e-5);
        if (!dataset.isSuccess()) {
            throw new UnsupportedOperationException(
                    "Conventional-cell conversion unavailable: " + dataset.getMessage());
        }
        throw new UnsupportedOperationException(
                "spglib dataset is available (" + dataset.getValue().get().getInternationalSymbol()
                        + "), but conventional-cell payload is not implemented in protocol v1.");
    }
}
