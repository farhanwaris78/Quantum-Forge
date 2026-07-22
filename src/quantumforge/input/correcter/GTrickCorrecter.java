/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.input.correcter;

import quantumforge.input.QEInput;
import quantumforge.input.card.QEKPoints;

/**
 * Γ-Trick option for band calculations.
 * 
 * Provides a Γ-Trick toggle that shifts the
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
            if (grid == null || grid.length != 3) {
                throw new IllegalStateException("Automatic k-point grid must contain three dimensions.");
            }
            // QE stores the three mesh dimensions and three offsets separately.
            // Zero offsets select the unshifted, Gamma-centred mesh. The previous
            // implementation expected a nonexistent six-element getKGrid()
            // return value, so the option never changed anything.
            this.cardKPoints.setKOffset(new int[] {0, 0, 0});
        }
    }
}
