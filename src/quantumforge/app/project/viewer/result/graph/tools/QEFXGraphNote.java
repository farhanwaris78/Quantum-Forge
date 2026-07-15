/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.graph.tools;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import javafx.scene.Node;

public class QEFXGraphNote extends QEFXAppComponent<QEFXGraphNoteController> {

    public QEFXGraphNote(QEFXProjectController projectController, Node content, boolean initMaximized) throws IOException {
        super("QEFXGraphNote.fxml", new QEFXGraphNoteController(projectController, content, initMaximized));
    }

    public void setOnNoteMaximized(NoteMaximized onNoteMaximized) {
        if (this.controller != null) {
            this.controller.setOnNoteMaximized(onNoteMaximized);
        }
    }

    public void minimize() {
        if (this.controller != null) {
            this.controller.minimize();
        }
    }

    public void maximize() {
        if (this.controller != null) {
            this.controller.maximize();
        }
    }

}
