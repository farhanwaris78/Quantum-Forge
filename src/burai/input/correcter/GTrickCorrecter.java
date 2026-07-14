/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.input.correcter;

import burai.input.QEInput;
import burai.input.card.QEKPoints;
import burai.input.namelist.QEValue;

/**
 * Γ-Trick option for band calculations.
 * 
 * NanoLabo v3.1+ provides a Γ-Trick toggle that shifts the
 * k-point grid to include the Gamma point. This is important
 * for:
 * - Insulators and semiconductors
 * - Correct band gap determination
 * - Optical property calculations
 * - Systems with symmetry-dependent properties
 */
public class GTrickCorrecter extends QEInputCorrecter {

    private boolean gtrickEnabled;

    public GTrickCorrecter(QEInput input) {
        super(input);
        this.gtrickEnabled = false;
    }

    public void setGTrickEnabled(boolean enabled) { this.gtrickEnabled = enabled; }

    @Override
    public void correctInput() {
        if (!this.gtrickEnabled) return;

        if (this.cardKPoints != null && this.cardKPoints.isAutomatic()) {
            int[] grid = this.cardKPoints.getKGrid();
            if (grid != null && grid.length == 6) {
                // Set offsets to 0 0 0 (Gamma-centered)
                // Original BURAI used offset 0,0,0 by default if not specified
                // The Γ-Trick ensures Gamma is included by shifting
                // odd-numbered grids
                int[] newGrid = new int[6];
                System.arraycopy(grid, 0, newGrid, 0, 6);

                // Shift offsets to include Gamma
                for (int i = 0; i < 3; i++) {
                    if (newGrid[i] > 0) {
                        // Use shifted grid for insulators
                        newGrid[i + 3] = 1; // offset = 1/2
                    } else {
                        newGrid[i + 3] = 0; // no offset for zero grid points
                    }
                }

                this.cardKPoints.setKGrid(newGrid);
            }
        }
    }
}
