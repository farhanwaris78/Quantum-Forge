/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.elastic;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewer;
import quantumforge.project.Project;

public class QEFXElasticViewer extends QEFXGraphViewer<QEFXElasticViewerController> {

    public QEFXElasticViewer(QEFXProjectController projectController, Project project) {
        super(new QEFXElasticViewerController(projectController), projectController, project);
    }
}
