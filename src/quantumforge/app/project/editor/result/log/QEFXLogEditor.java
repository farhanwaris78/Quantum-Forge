/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.log;

import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.QEFXResultEditor;
import quantumforge.app.project.viewer.result.log.QEFXLogViewer;
import quantumforge.com.keys.PriorKeyEvent;

public class QEFXLogEditor extends QEFXResultEditor<QEFXLogEditorController> {

    public QEFXLogEditor(QEFXProjectController projectController, QEFXLogViewer viewer) throws IOException {
        super("QEFXLogEditor.fxml",
                new QEFXLogEditorController(projectController, viewer == null ? null : viewer.getController()));

        if (this.node != null) {
            this.setupKeys(this.node);
        }

        Node viewerNode = viewer == null ? null : viewer.getNode();
        if (viewerNode != null) {
            this.setupKeys(viewerNode);
        }
    }

    private void setupKeys(Node node) {
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

            if (event.isShortcutDown() && KeyCode.F.equals(event.getCode())) {
                // Shortcut + F
                this.controller.focusSearchingField();

            } else if (KeyCode.F5.equals(event.getCode())) {
                // F5
                this.controller.reload();
            }
        });
    }
}
