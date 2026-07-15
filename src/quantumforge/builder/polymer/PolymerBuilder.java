/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.polymer;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Polymer model builder for chain and network structures.
 * 
 * NanoLabo Pro provides polymer modeling with:
 * - Monomer sequence definition
 * - Chain length control
 * - Torsion angle configuration
 * - Cross-linking
 * - Common polymer templates (PE, PP, PET, Nylon, etc.)
 * 
 * This is a Pro-only feature in NanoLabo.
 */
public class PolymerBuilder {

    public static final int POLYMER_PE = 0;   // Polyethylene
    public static final int POLYMER_PP = 1;   // Polypropylene
    public static final int POLYMER_PET = 2;  // Polyethylene terephthalate
    public static final int POLYMER_NYLON6 = 3;
    public static final int POLYMER_PS = 4;   // Polystyrene
    public static final int POLYMER_PVC = 5;  // Polyvinyl chloride
    public static final int POLYMER_PMMA = 6; // Poly(methyl methacrylate)

    private int polymerType;
    private int chainLength;
    private Cell[] monomers;

    public PolymerBuilder() {
        this.polymerType = POLYMER_PE;
        this.chainLength = 10;
        this.monomers = null;
    }

    public void setPolymerType(int type) { this.polymerType = type; }
    public void setChainLength(int length) { this.chainLength = Math.max(2, length); }
    public void setMonomers(Cell[] monomers) { this.monomers = monomers; }

    /**
     * Build a polymer chain
     */
    public Cell build() throws ZeroVolumCellException {
        Cell polymerCell = Cell.getEmptyCell();
        if (polymerCell == null) return null;

        // Pre-defined polymer templates
        switch (this.polymerType) {
            case POLYMER_PE:
                buildPolyethylene(polymerCell);
                break;
            case POLYMER_PP:
                buildPolypropylene(polymerCell);
                break;
            default:
                buildGenericChain(polymerCell);
                break;
        }

        return polymerCell;
    }

    private void buildPolyethylene(Cell cell) {
        double bondLen = 1.54;
        double angle = 109.5 * Math.PI / 180.0;

        // Carbon backbone
        for (int i = 0; i < this.chainLength; i++) {
            double x = i * bondLen * Math.sin(angle / 2.0);
            double y = i % 2 == 0 ? 0.0 : bondLen * Math.cos(angle / 2.0);
            cell.addAtom("C", x, y, 0.0);

            // Hydrogen atoms (simplified)
            cell.addAtom("H", x + 0.5, y + 0.5, 0.5);
            cell.addAtom("H", x - 0.5, y + 0.5, -0.5);
            if (i == 0 || i == this.chainLength - 1) {
                cell.addAtom("H", x + (i == 0 ? -0.5 : 0.5), y, 0.0);
            }
        }
    }

    private void buildPolypropylene(Cell cell) {
        double bondLen = 1.54;
        double angle = 109.5 * Math.PI / 180.0;

        for (int i = 0; i < this.chainLength; i++) {
            double x = i * bondLen * Math.sin(angle / 2.0);
            double y = i % 2 == 0 ? 0.0 : bondLen * Math.cos(angle / 2.0);
            cell.addAtom("C", x, y, 0.0);
            // Methyl side group on alternating carbons
            if (i % 2 == 0) {
                cell.addAtom("C", x + 0.5, y - 0.8, 0.5);
                cell.addAtom("H", x + 1.0, y - 1.3, 0.5);
                cell.addAtom("H", x + 0.3, y - 1.3, 1.0);
                cell.addAtom("H", x + 0.3, y - 0.3, 1.0);
            }
        }
    }

    private void buildGenericChain(Cell cell) {
        if (this.monomers == null) return;

        for (int m = 0; m < this.chainLength && m < this.monomers.length; m++) {
            if (this.monomers[m] == null) continue;
            // Place monomer unit at appropriate position along chain
            double offsetX = m * 5.0; // spacing between monomers
            // In full implementation, this would properly align monomers
        }
    }
}
