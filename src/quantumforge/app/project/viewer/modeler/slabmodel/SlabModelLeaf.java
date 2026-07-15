/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.modeler.slabmodel;

import quantumforge.atoms.model.Cell;

public class SlabModelLeaf extends SlabModel {

    private SlabModelStem stem;

    public SlabModelLeaf(SlabModelStem stem, double offset) {
        super();

        if (stem == null) {
            throw new IllegalArgumentException("stem is null.");
        }

        this.stem = stem;
        this.offset = offset;
    }

    @Override
    public SlabModel[] getSlabModels() {
        return new SlabModel[] { this };
    }

    @Override
    protected boolean updateCell(Cell cell) {
        return this.stem.updateCell(cell, this);
    }

}
