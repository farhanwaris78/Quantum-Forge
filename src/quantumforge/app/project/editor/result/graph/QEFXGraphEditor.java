/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.graph;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.QEFXResultEditor;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewer;

public class QEFXGraphEditor extends QEFXResultEditor<QEFXGraphEditorController> {

    public QEFXGraphEditor(QEFXProjectController projectController, QEFXGraphViewer<?> viewer) throws IOException {
        super("QEFXGraphEditor.fxml",
                new QEFXGraphEditorController(projectController, viewer == null ? null : viewer.getController()));
    }

}
