/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.modeler;

import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.modeler.Modeler;
import quantumforge.com.keys.PriorKeyEvent;

public class QEFXModelerEditor extends QEFXAppComponent<QEFXModelerEditorController> {

    public QEFXModelerEditor(QEFXProjectController projectController, Modeler modeler) throws IOException {
        super("QEFXModelerEditor.fxml", new QEFXModelerEditorController(projectController, modeler));

        if (this.node != null) {
            this.node.setOnMouseReleased(event -> this.node.requestFocus());
        }

        if (this.node != null) {
            this.setupKeys(this.node, modeler);
        }
    }

    private void setupKeys(Node node, Modeler modeler) {
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

            if (event.isShortcutDown() && KeyCode.Z.equals(event.getCode())) {
                if (!event.isShiftDown()) {
                    // Shortcut + Z
                    if (modeler != null) {
                        modeler.undo();
                    }

                } else {
                    // Shortcut + Shift + Z
                    if (modeler != null) {
                        modeler.redo();
                    }
                }

            } else if (event.isShortcutDown() && KeyCode.C.equals(event.getCode())) {
                // Shortcut + C
                if (modeler != null) {
                    modeler.center();
                }
            }
        });
    }
}
