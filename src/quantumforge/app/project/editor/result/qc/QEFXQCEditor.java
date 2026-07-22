/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.result.qc;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.qc.QEFXQCViewer;

public class QEFXQCEditor extends QEFXGraphEditor {

    public QEFXQCEditor(QEFXProjectController projectController, QEFXQCViewer resultViewer) throws IOException {
        super(projectController, resultViewer);
    }
}
