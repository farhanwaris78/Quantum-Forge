/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.symmetry;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Symmetry Utility for crystal lattice conversion.
 */
public class SymmetryUtility {

    /**
     * Convert to primitive cell.
     * Stub for spglib standardize_cell.
     */
    public static Cell convertToPrimitive(Cell cell) throws ZeroVolumCellException {
        if (cell == null) return null;
        
        throw new UnsupportedOperationException(
                "Primitive-cell conversion requires spglib and is not implemented in this release.");
    }

    /**
     * Convert to conventional cell.
     */
    public static Cell convertToConventional(Cell cell) throws ZeroVolumCellException {
        if (cell == null) return null;
        throw new UnsupportedOperationException(
                "Conventional-cell conversion requires spglib and is not implemented in this release.");
    }
}
