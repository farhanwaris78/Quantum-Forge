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

import quantumforge.app.project.viewer.modeler.ModelerBase;
import quantumforge.atoms.model.Cell;

public class SlabModeler extends ModelerBase {

    private static final double VALUE_THR = 1.0e-12;

    private SlabModel slabModel;

    public SlabModeler(Cell srcCell) {
        super(srcCell);

        this.slabModel = null;
    }

    public double getOffset() {
        return this.slabModel == null ? SlabModel.defaultOffset() : this.slabModel.getOffset();
    }

    public double getThickness() {
        return this.slabModel == null ? SlabModel.defaultThickness() : this.slabModel.getThickness();
    }

    public double getVacuum() {
        return this.slabModel == null ? SlabModel.defaultVacuum() : this.slabModel.getVacuum();
    }

    public int getScaleA() {
        return this.slabModel == null ? SlabModel.defaultScale() : this.slabModel.getScaleA();
    }

    public int getScaleB() {
        return this.slabModel == null ? SlabModel.defaultScale() : this.slabModel.getScaleB();
    }

    private boolean update() {
        boolean status = false;
        if (this.dstCell != null) {
            status = this.slabModel.putOnCell(this.dstCell);
        }

        if (!status) {
            this.slabModel.putOnLastCell(this.dstCell);
        }

        return status;
    }

    @Override
    public void initialize() {
        if (this.slabModel != null) {
            this.slabModel.setThickness(SlabModel.defaultThickness());
            this.slabModel.setVacuum(SlabModel.defaultVacuum());
            this.slabModel.setScaleA(SlabModel.defaultScale());
            this.slabModel.setScaleB(SlabModel.defaultScale());
            this.update();
        }

        if (this.atomsViewer != null) {
            this.atomsViewer.setCellToCenter();
        }
    }

    public boolean setSlabModel(SlabModel slabModel) {
        if (slabModel != null) {
            slabModel.setThickness(this.getThickness());
            slabModel.setVacuum(this.getVacuum());
            slabModel.setScaleA(this.getScaleA());
            slabModel.setScaleB(this.getScaleB());
        }

        this.slabModel = slabModel;
        if (this.slabModel == null) {
            return false;
        }

        boolean status = false;
        if (this.dstCell != null) {
            status = this.slabModel.putOnCell(this.dstCell);
        }

        if (!status) {
            this.slabModel.setThickness(SlabModel.defaultThickness());
            this.slabModel.setVacuum(SlabModel.defaultVacuum());
            this.slabModel.setScaleA(SlabModel.defaultScale());
            this.slabModel.setScaleB(SlabModel.defaultScale());
            this.update();
        }

        return status;
    }

    public boolean changeThickness(double thickness) {
        if (thickness < 0.0) {
            return false;
        }

        if (this.slabModel == null) {
            return false;
        }

        if (Math.abs(this.slabModel.getThickness() - thickness) < VALUE_THR) {
            return true;
        }

        this.slabModel.setThickness(thickness);

        return this.update();
    }

    public boolean changeVacuum(double vacuum) {
        if (vacuum < 0.0) {
            return false;
        }

        if (this.slabModel == null) {
            return false;
        }

        if (Math.abs(this.slabModel.getVacuum() - vacuum) < VALUE_THR) {
            return true;
        }

        this.slabModel.setVacuum(vacuum);

        return this.update();
    }

    public boolean changeArea(int na, int nb) {
        if (na < 1 || nb < 1) {
            return false;
        }

        if (this.slabModel == null) {
            return false;
        }

        if (this.slabModel.getScaleA() == na && this.slabModel.getScaleB() == nb) {
            return true;
        }

        this.slabModel.setScaleA(na);
        this.slabModel.setScaleB(nb);

        boolean status = this.update();

        if (status) {
            if (this.atomsViewer != null) {
                this.atomsViewer.setCellToCenter();
            }
        }

        return status;
    }
}
