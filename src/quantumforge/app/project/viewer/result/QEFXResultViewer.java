/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result;

import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.com.keys.PriorKeyEvent;
import quantumforge.project.Project;

public abstract class QEFXResultViewer<V extends QEFXResultViewerController> extends QEFXAppComponent<V> {

    protected QEFXProjectController projectController;
    protected Project project;

    public QEFXResultViewer(Node node, V controller) {
        super(node, controller);

        if (this.node != null) {
            this.setupKeys(this.node);
        }
    }

    public QEFXResultViewer(String fileFXML, V controller) throws IOException {
        super(fileFXML, controller);

        if (this.node != null) {
            this.setupKeys(this.node);
        }
    }

    public QEFXResultViewer(String fileFXML, V controller, QEFXProjectController projectController, Project project) throws IOException {
        super(fileFXML, controller);
        this.projectController = projectController;
        this.project = project;

        if (this.node != null) {
            this.setupKeys(this.node);
        }
    }

    public void reload() {
        if (this.controller != null) {
            this.controller.reload();
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

            if (KeyCode.F5.equals(event.getCode())) {
                // F5
                this.controller.reloadSafely();
            }
        });
    }
}
