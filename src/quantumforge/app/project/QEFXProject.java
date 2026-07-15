/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.EditorActions;
import quantumforge.app.project.editor.input.InputEditorActions;
import quantumforge.app.project.viewer.ViewerActions;
import quantumforge.project.Project;
import javafx.scene.input.KeyCode;

public class QEFXProject extends QEFXAppComponent<QEFXProjectController> {

    private Project project;

    private ViewerActions viewerActions;

    private EditorActions editorActions;

    public QEFXProject(QEFXMainController mainController, Project project) throws IOException {
        super("QEFXProject.fxml", new QEFXProjectController(mainController));

        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        this.project = project;

        this.viewerActions = new ViewerActions(this.project, this.controller);
        this.viewerActions.actionInitially();
        this.controller.setViewerActions(this.viewerActions);

        this.editorActions = new InputEditorActions(this.project, this.controller);
        this.editorActions.actionInitially();
        this.controller.setEditorActions(this.editorActions);

        this.setupKeyPressed();
    }

    private void setupKeyPressed() {
        this.node.setOnKeyPressed(event -> {
            if (event == null) {
                return;
            }

            if (event.isShortcutDown() && KeyCode.S.equals(event.getCode())) {
                // Shortcut + S
                if (this.controller.isNormalMode()) {
                    this.controller.saveFile();
                }

            } else if (event.isShortcutDown() && KeyCode.LEFT.equals(event.getCode())) {
                // Shortcut + <-
                if (!this.controller.isNormalMode()) {
                    this.controller.pushViewerButton();
                }

            } else if (KeyCode.PRINTSCREEN.equals(event.getCode())) {
                // PrintScreen
                if (!(this.controller.isResultExplorerMode() || this.controller.isDesignerMode())) {
                    this.controller.sceenShot();
                }
            }
        });
    }
}
