/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.elastic;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewer;

public class QEFXElasticViewer extends QEFXGraphViewer<QEFXElasticViewerController> {

    public QEFXElasticViewer(QEFXProjectController projectController) throws IOException {
        super(new QEFXElasticViewerController(projectController));
    }
}
