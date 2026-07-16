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
import quantumforge.com.log.AppLog;
import quantumforge.project.Project;
import quantumforge.project.ProjectAutosave;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

public class QEFXProject extends QEFXAppComponent<QEFXProjectController> {

    private Project project;

    private ViewerActions viewerActions;

    private EditorActions editorActions;

    private ProjectAutosave autosave;

    private Timeline autosaveProbe;

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

        this.setupAutosave();
        this.setupKeyPressed();
    }

    private void setupAutosave() {
        if (this.project.getDirectoryPath() == null || this.project.getDirectoryPath().isBlank()) {
            return;
        }
        try {
            this.autosave = new ProjectAutosave(this.project, 10, 8_000L);
            // Poll dirty state without requiring every editor widget to call us.
            this.autosaveProbe = new Timeline(new KeyFrame(Duration.seconds(12), event -> {
                try {
                    if (this.project.isQEInputChanged()) {
                        this.autosave.requestSnapshot();
                    }
                } catch (RuntimeException ex) {
                    AppLog.warn("autosave", "Probe failed: " + ex.getMessage());
                }
            }));
            this.autosaveProbe.setCycleCount(Timeline.INDEFINITE);
            this.autosaveProbe.play();
            this.controller.setOnDetached(controller -> {
                if (this.autosaveProbe != null) {
                    this.autosaveProbe.stop();
                }
                if (this.autosave != null) {
                    this.autosave.close();
                }
            });
        } catch (RuntimeException ex) {
            AppLog.warn("autosave", "Autosave disabled: " + ex.getMessage());
        }
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
