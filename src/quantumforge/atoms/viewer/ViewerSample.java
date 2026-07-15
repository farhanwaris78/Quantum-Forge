/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.visible.AtomsSample;
import javafx.scene.transform.Affine;

public class ViewerSample extends ViewerComponent<AtomsSample> {

    private Cell cell;

    public ViewerSample(AtomsViewer atomsViewer, Cell cell) {
        super(atomsViewer);

        if (cell == null) {
            throw new IllegalArgumentException("cell is null.");
        }

        this.cell = cell;
    }

    @Override
    public void initialize() {
        double width = this.atomsViewer.getSceneWidth();
        double height = this.atomsViewer.getSceneHeight();
        double rangeScene = Math.min(width, height);

        this.scale = 0.05 * rangeScene;
        this.centerX = 0.065 * width;
        this.centerY = 0.065 * height;
        this.centerZ = -0.40 * rangeScene;

        if (this.affine == null) {
            this.affine = new Affine();
        }

        this.affine.setToIdentity();
        this.affine.prependScale(this.scale, this.scale, this.scale);
        this.affine.prependTranslation(this.centerX, this.centerY, this.centerZ);
    }

    @Override
    protected AtomsSample createNode() {
        return new AtomsSample(this.cell, this.atomsViewer.getDesign());
    }
}
