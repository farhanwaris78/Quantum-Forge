/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result;

import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import quantumforge.app.QEFXAppComponent;
import quantumforge.com.keys.PriorKeyEvent;

public abstract class QEFXResultEditor<E extends QEFXResultEditorController<?>> extends QEFXAppComponent<E> {

    public QEFXResultEditor(String fileFXML, E controller) throws IOException {
        super(fileFXML, controller);

        if (this.node != null) {
            this.setupMouse(this.node);
            this.setupKeys(this.node);
        }
    }

    private void setupMouse(Node node) {
        if (node == null) {
            return;
        }

        node.setOnMouseReleased(event -> {
            this.node.requestFocus();
        });
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

            if (KeyCode.F5.equals(event.getCode())) {
                // F5
                this.controller.reload();
            }
        });
    }
}
