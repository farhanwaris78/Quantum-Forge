/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.result.elastic;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.elastic.QEFXElasticViewer;

public class QEFXElasticEditor extends QEFXGraphEditor {

    public QEFXElasticEditor(QEFXProjectController projectController, QEFXElasticViewer resultViewer) {
        super(projectController, resultViewer);
    }
}
