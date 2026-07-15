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

public class SlabModelBuilder {

    private Cell cell;

    public SlabModelBuilder(Cell cell) {
        if (cell == null) {
            throw new IllegalArgumentException("cell is null.");
        }

        this.cell = cell;
    }

    public SlabModel[] build(int h, int k, int l) {
        SlabModel slabModel = null;
        try {
            slabModel = new SlabModelStem(this.cell, h, k, l);
        } catch (MillerIndexException e) {
            //e.printStackTrace();
            return null;
        }

        SlabModel[] slabModels = slabModel.getSlabModels();
        if (slabModels == null || slabModels.length < 1) {
            return null;
        }

        return slabModels;
    }
}
