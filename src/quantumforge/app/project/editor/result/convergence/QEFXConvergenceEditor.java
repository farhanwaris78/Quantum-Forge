/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.result.convergence;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.convergence.QEFXConvergenceViewer;

public class QEFXConvergenceEditor extends QEFXGraphEditor {

    public QEFXConvergenceEditor(QEFXProjectController projectController, QEFXConvergenceViewer resultViewer) throws IOException {
        super(projectController, resultViewer);
    }
}
