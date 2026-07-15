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

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorController;

public class QEFXGeomController extends QEFXEditorController {

    @FXML
    private ScrollPane cellPane;

    @FXML
    private ScrollPane elementsPane;

    @FXML
    private ScrollPane atomsPane;

    public QEFXGeomController(QEFXMainController mainController) {
        super(mainController);
    }

    public void setCellPane(Node node) {
        if (node == null) {
            return;
        }

        if (this.cellPane != null) {
            this.cellPane.setContent(node);
        }
    }

    public void setElementsPane(Node node) {
        if (node == null) {
            return;
        }

        if (this.elementsPane != null) {
            this.elementsPane.setContent(node);
        }
    }

    public void setAtomsPane(Node node) {
        if (node == null) {
            return;
        }

        if (this.atomsPane != null) {
            this.atomsPane.setContent(node);
        }
    }
}
