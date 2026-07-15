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
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultExplorer;
import quantumforge.com.keys.PriorKeyEvent;
import quantumforge.project.Project;

public class QEFXResultFileTree extends QEFXAppComponent<QEFXResultFileTreeController> {

    public QEFXResultFileTree(QEFXProjectController projectController, Project project) throws IOException {
        super("QEFXResultFileTree.fxml", new QEFXResultFileTreeController(projectController, project));

        if (this.node != null) {
            this.node.setOnMouseReleased(event -> this.node.requestFocus());
            this.setupKey(this.node);
        }
    }

    public void setResultExplorer(QEFXResultExplorer explorer) {
        this.controller.setResultExplorer(explorer);
    }

    public void reload() {
        this.controller.reload();
    }

    private void setupKey(Node node) {
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
                this.controller.reloadAll();
            }
        });
    }
}
