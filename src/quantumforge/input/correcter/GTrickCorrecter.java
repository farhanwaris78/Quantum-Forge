/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.input.correcter;

import quantumforge.input.QEInput;
import quantumforge.input.card.QEKPoints;

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
                // QE's final three K_POINTS automatic integers are 0/1 grid
                // offsets. Zero is the unshifted, Gamma-centred choice; setting
                // all offsets to 1, as the previous implementation did, applies
                // a half-grid shift and can remove Gamma from an odd mesh.
                int[] newGrid = new int[6];
                System.arraycopy(grid, 0, newGrid, 0, 6);
                newGrid[3] = 0;
                newGrid[4] = 0;
                newGrid[5] = 0;

                this.cardKPoints.setKGrid(newGrid);
            }
        }
    }
}
