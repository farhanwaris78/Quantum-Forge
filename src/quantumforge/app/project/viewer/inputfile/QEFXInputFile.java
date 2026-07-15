/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.inputfile;

import java.io.IOException;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.project.Project;

public class QEFXInputFile extends QEFXAppComponent<QEFXInputFileController> {

    public QEFXInputFile(QEFXProjectController projectController, Project project) throws IOException {
        super("QEFXInputFile.fxml", new QEFXInputFileController(projectController, project));

        if (this.node != null) {
            this.setupKeys(this.node);
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

            if (event.isShortcutDown() && KeyCode.W.equals(event.getCode())) {
                // Shortcut + W
                this.controller.close();
                event.consume();
            }
        });
    }
}
