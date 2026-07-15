/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.modeler.slabmodel;

import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.modeler.slabmodel.SlabModel;
import quantumforge.app.project.viewer.modeler.slabmodel.SlabModeler;
import quantumforge.com.keys.PriorKeyEvent;

public class QEFXSlabEditor extends QEFXAppComponent<QEFXSlabEditorController> {

    public QEFXSlabEditor(QEFXProjectController projectController, SlabModeler modeler) throws IOException {
        super("QEFXSlabEditor.fxml", new QEFXSlabEditorController(projectController, modeler));

        if (this.node != null) {
            this.node.setOnMouseReleased(event -> this.node.requestFocus());
        }

        if (this.node != null) {
            this.setupKeys(this.node, modeler);
        }
    }

    private void setupKeys(Node node, SlabModeler modeler) {
        if (node == null) {
            return;
        }

        node.setOnKeyPressed(event -> {
            if (event == null) {
                return;
            }

            if (PriorKeyEvent.isPriorKeyEvent(event)) {
                return;
            }

            if (event.isShortcutDown() && KeyCode.C.equals(event.getCode())) {
                // Shortcut + C
                if (modeler != null) {
                    modeler.center();
                }
            }
        });
    }

    public void setSlabModels(SlabModel[] slabModels) {
        if (this.controller != null) {
            this.controller.setSlabModels(slabModels);
        }
    }

    public void cleanSlabModels() {
        if (this.controller != null) {
            this.controller.cleanSlabModels();
        }
    }
}
