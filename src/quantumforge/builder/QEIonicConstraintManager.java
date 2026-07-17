/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import quantumforge.atoms.model.Atom;

/**
 * Manages per-axis atomic relaxation constraints (if_pos flags 0 or 1) for 
 * Quantum ESPRESSO calculations, allowing selective freezing of layers (Roadmap #80).
 */
public final class QEIonicConstraintManager {

    public static final class ConstraintRecord {
        private final int ifX; // 0 (frozen) or 1 (free)
        private final int ifY;
        private final int ifZ;

        public ConstraintRecord(int ifX, int ifY, int ifZ) {
            this.ifX = validateFlag(ifX);
            this.ifY = validateFlag(ifY);
            this.ifZ = validateFlag(ifZ);
        }

        public int getIfX() { return this.ifX; }
        public int getIfY() { return this.ifY; }
        public int getIfZ() { return this.ifZ; }

        private static int validateFlag(int flag) {
            return (flag == 1 || flag == 0) ? flag : 1; // Default to 1 (free)
        }

        @Override
        public String toString() {
            return String.format("%d  %d  %d", ifX, ifY, ifZ);
        }
    }

    private final Map<Integer, ConstraintRecord> constraints = new HashMap<>();

    public QEIonicConstraintManager() {
        // Constructor
    }

    /**
     * Sets relaxation constraints for a specific atom index.
     * 
     * @param atomIndex 0-based index of the target atom
     * @param ifX 1 to relax, 0 to freeze
     * @param ifY 1 to relax, 0 to freeze
     * @param ifZ 1 to relax, 0 to freeze
     */
    public void setConstraint(int atomIndex, int ifX, int ifY, int ifZ) {
        if (atomIndex < 0) {
            throw new IllegalArgumentException("atomIndex must be non-negative");
        }
        this.constraints.put(atomIndex, new ConstraintRecord(ifX, ifY, ifZ));
    }

    /**
     * Retrieves constraints for an atom. Returns a default fully-free [1, 1, 1] if not explicitly set.
     */
    public ConstraintRecord getConstraint(int atomIndex) {
        return this.constraints.getOrDefault(atomIndex, new ConstraintRecord(1, 1, 1));
    }

    public void removeConstraint(int atomIndex) {
        this.constraints.remove(atomIndex);
    }

    public void clear() {
        this.constraints.clear();
    }

    /**
     * Formats a line of the ATOMIC_POSITIONS card including the constraint flags:
     * e.g. "Fe    0.00000000    0.00000000    0.00000000   0  0  0"
     */
    public String formatAtomPositionLine(Atom atom, int atomIndex, String formatType) {
        Objects.requireNonNull(atom, "atom");
        ConstraintRecord record = getConstraint(atomIndex);

        // Standard coordinate formatting
        String coords = String.format(Locale.ROOT, "  %-4s  %14.9f  %14.9f  %14.9f", 
            atom.getName(), atom.getX(), atom.getY(), atom.getZ());

        // Append constraints only for relax / md modes
        if ("relax".equalsIgnoreCase(formatType) || "vc-relax".equalsIgnoreCase(formatType) || "md".equalsIgnoreCase(formatType)) {
            return String.format(Locale.ROOT, "%s   %d  %d  %d", coords, record.getIfX(), record.getIfY(), record.getIfZ());
        }
        
        return coords;
    }
}
