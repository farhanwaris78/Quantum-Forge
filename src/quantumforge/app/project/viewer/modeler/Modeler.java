/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.modeler;

import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import quantumforge.app.QEFXMain;
import quantumforge.app.project.viewer.modeler.slabmodel.SlabModel;
import quantumforge.app.project.viewer.modeler.slabmodel.SlabModelBuilder;
import quantumforge.app.project.viewer.modeler.supercell.SuperCellBuilder;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.logger.AtomsLoggerProperty;
import quantumforge.input.QEInput;
import quantumforge.input.card.QECard;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.project.Project;

public class Modeler extends ModelerBase {

    private static final int NATOMS_TO_AUTO_CENTER = 8;
    private static final double VOLUME_TO_AUTO_CENTER = 2.0;

    private static final double RMIN = 1.0e-3;
    private static final double RRMIN = RMIN * RMIN;

    private Project project;

    private double aOffset;
    private double bOffset;
    private double cOffset;
    private CellOffsetChanged onCellOffsetChanged;

    public Modeler(Project project) {
        super(project == null ? null : project.getCell());

        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        this.project = project;

        this.aOffset = 0.0;
        this.bOffset = 0.0;
        this.cOffset = 0.0;
        this.onCellOffsetChanged = null;
    }

    @Override
    public void setAtomsViewer(AtomsViewer atomsViewer) {
        super.setAtomsViewer(atomsViewer);

        if (this.atomsViewer != null) {
            this.atomsViewer.setLoggerPropertyFactory(() -> {
                return new CellOffsetProperty(this);
            });
        }
    }

    private static class CellOffsetProperty implements AtomsLoggerProperty {
        private Modeler parent;

        private double aOffset;
        private double bOffset;
        private double cOffset;

        private int numAtoms;
        private double volume;

        public CellOffsetProperty(Modeler parent) {
            this.parent = parent;
            this.aOffset = 0.0;
            this.bOffset = 0.0;
            this.cOffset = 0.0;
            this.numAtoms = 0;
            this.volume = 0.0;
        }

        @Override
        public void storeProperty() {
            this.aOffset = this.parent == null ? 0.0 : this.parent.aOffset;
            this.bOffset = this.parent == null ? 0.0 : this.parent.bOffset;
            this.cOffset = this.parent == null ? 0.0 : this.parent.cOffset;

            this.numAtoms = 0;
            this.volume = 0.0;
            if (this.parent != null && this.parent.dstCell != null) {
                this.numAtoms = this.parent.dstCell.numAtoms();
                this.volume = this.parent.dstCell.getVolume();
            }
        }

        @Override
        public void restoreProperty() {
            if (this.parent == null) {
                return;
            }

            this.parent.setCellOffset(this.aOffset, this.bOffset, this.cOffset);

            int deltaAtoms = 0;
            if (this.parent.dstCell != null) {
                deltaAtoms = this.parent.dstCell.numAtoms() - this.numAtoms;
            }

            double scaleVolume = 1.0;
            if (this.parent.dstCell != null) {
                double volume2 = this.parent.dstCell.getVolume();
                if (this.volume > 0.0 && volume2 > 0.0) {
                    if (this.volume > volume2) {
                        scaleVolume = this.volume / volume2;
                    } else {
                        scaleVolume = volume2 / this.volume;
                    }
                }
            }

            if (Math.abs(deltaAtoms) >= NATOMS_TO_AUTO_CENTER || scaleVolume >= VOLUME_TO_AUTO_CENTER) {
                Platform.runLater(() -> {
                    if (this.parent.atomsViewer != null) {
                        this.parent.atomsViewer.setCellToCenter();
                    }
                });
            }
        }
    }

    @Override
    public void initialize() {
        if (this.atomsViewer != null) {
            this.atomsViewer.storeCell();
        }

        super.initialize();

        this.setCellOffset(0.0, 0.0, 0.0);
    }

    @Override
    public void reflect() {
        boolean toCorrect = false;
        if (this.isToReflect()) {
            toCorrect = true;
        }

        super.reflect();

        if (toCorrect) {
            this.correctProjecAuto();
        }
    }

    public void undo() {
        if (this.atomsViewer != null) {
            this.atomsViewer.restoreCell();
        }
    }

    public void redo() {
        if (this.atomsViewer != null) {
            this.atomsViewer.subRestoreCell();
        }
    }

    public void setOnCellOffsetChanged(CellOffsetChanged onCellOffsetChanged) {
        this.onCellOffsetChanged = onCellOffsetChanged;
    }

    private void setCellOffset(double a, double b, double c) {
        this.aOffset = a;
        this.bOffset = b;
        this.cOffset = c;

        if (this.onCellOffsetChanged != null) {
            this.onCellOffsetChanged.onCellOffsetChanged(a, b, c);
        }
    }

    public void translateCell(double a, double b, double c) {
        if (this.dstCell == null) {
            return;
        }

        double da = a - this.aOffset;
        double db = b - this.bOffset;
        double dc = c - this.cOffset;
        if (Math.abs(da) <= 0.0 && Math.abs(db) <= 0.0 && Math.abs(dc) <= 0.0) {
            return;
        }

        double[] trans = this.dstCell.convertToCartesianPosition(da, db, dc);
        if (trans == null || trans.length < 3) {
            return;
        }

        double dx = trans[0];
        double dy = trans[1];
        double dz = trans[2];
        double rr = dx * dx + dy * dy + dz * dz;
        if (rr <= RRMIN) {
            return;
        }

        Atom[] atoms = this.dstCell.listAtoms(true);
        if (atoms != null) {
            this.dstCell.stopResolving();
            for (Atom atom : atoms) {
                if (atom != null) {
                    atom.moveBy(dx, dy, dz);
                }
            }
            this.dstCell.restartResolving();
        }

        this.setCellOffset(a, b, c);
    }

    public boolean buildSuperCell(int na, int nb, int nc) {
        SuperCellBuilder builder = this.dstCell == null ? null : new SuperCellBuilder(this.dstCell);
        if (builder == null) {
            return false;
        }

        if (this.atomsViewer != null) {
            this.atomsViewer.storeCell();
        }

        boolean status = builder.build(na, nb, nc);

        if (!status) {
            if (this.atomsViewer != null) {
                this.atomsViewer.restoreCell();
            }

            return false;
        }

        if (this.atomsViewer != null) {
            this.atomsViewer.setCellToCenter();
        }

        this.setCellOffset(0.0, 0.0, 0.0);

        return true;
    }

    public SlabModel[] buildSlabModel(int i, int j, int k) {
        SlabModelBuilder builder = this.dstCell == null ? null : new SlabModelBuilder(this.dstCell);
        if (builder == null) {
            return null;
        }

        if (this.atomsViewer != null) {
            this.atomsViewer.storeCell();
        }

        SlabModel[] slabModels = builder.build(i, j, k);

        if (slabModels == null || slabModels.length < 1) {
            if (this.atomsViewer != null) {
                this.atomsViewer.restoreCell();
            }

            return null;
        }

        if (this.atomsViewer != null) {
            this.atomsViewer.setCellToCenter();
        }

        this.setCellOffset(0.0, 0.0, 0.0);

        return slabModels;
    }

    private boolean showCorrectDialog() {
        String items = "";
        items = items + System.lineSeparator() + "  - K-Points";
        items = items + System.lineSeparator() + "  - Number of Bands";
        items = items + System.lineSeparator() + "  - Walking on B.Z.";

        Alert alert = new Alert(AlertType.CONFIRMATION);
        QEFXMain.initializeDialogOwner(alert);
        alert.setHeaderText("Correct the input-file ?");
        alert.setContentText("Following items will be corrected:" + items);
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> optButtonType = alert.showAndWait();
        if (optButtonType == null || (!optButtonType.isPresent())) {
            return false;
        }
        if (!ButtonType.YES.equals(optButtonType.get())) {
            return false;
        }

        return true;
    }

    private void correctProjecAuto() {
        if (!this.showCorrectDialog()) {
            return;
        }

        QEInput[] inputs = {
                this.project.getQEInputScf(),
                this.project.getQEInputDos(),
                this.project.getQEInputBand()
        };

        for (QEInput input : inputs) {
            if (input == null) {
                continue;
            }

            QECard card = input.getCard(QEKPoints.CARD_NAME);
            QEKPoints cardKPoints = (card != null && card instanceof QEKPoints) ? ((QEKPoints) card) : null;
            if (cardKPoints != null) {
                cardKPoints.clear();
            }

            QENamelist nmlSystem = input.getNamelist(QEInput.NAMELIST_SYSTEM);
            if (nmlSystem != null) {
                nmlSystem.removeValue("nbnd");
            }
        }

        this.project.resolveQEInputs();
    }
}
