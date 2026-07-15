/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.geom;

import java.io.IOException;

import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorComponent;
import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;

public class QEFXGeom extends QEFXEditorComponent<QEFXGeomController> {

    private QEFXCell cellComponent;
    private QEFXElements elementsComponent;
    private QEFXAtoms atomsComponent;

    public QEFXGeom(QEFXMainController mainController, QEInput input, Cell cell) throws IOException {
        super("QEFXGeom.fxml", new QEFXGeomController(mainController));

        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        if (cell == null) {
            throw new IllegalArgumentException("cell is null.");
        }

        this.createComponents(input, cell);
    }

    private void createComponents(QEInput input, Cell cell) throws IOException {
        QEFXMainController mainController = null;
        if (this.controller != null) {
            mainController = this.controller.getMainController();
        }

        this.cellComponent = new QEFXCell(mainController, input, cell);
        this.elementsComponent = new QEFXElements(mainController, input, cell);
        this.atomsComponent = new QEFXAtoms(mainController, input, cell);

        if (this.controller != null) {
            this.controller.setCellPane(this.cellComponent.getNode());
            this.controller.setElementsPane(this.elementsComponent.getNode());
            this.controller.setAtomsPane(this.atomsComponent.getNode());
        }
    }

    @Override
    public void notifyEditorOpened() {
        this.cellComponent.notifyEditorOpened();
        this.elementsComponent.notifyEditorOpened();
        this.atomsComponent.notifyEditorOpened();
    }

}
