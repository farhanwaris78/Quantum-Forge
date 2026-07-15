/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.elastic;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewer;
import quantumforge.project.Project;

public class QEFXElasticViewer extends QEFXResultViewer<QEFXElasticViewerController> {

    public QEFXElasticViewer(QEFXProjectController projectController, Project project) {
        super("QEFXGraphViewer.fxml", new QEFXElasticViewerController(projectController), projectController, project);
    }
}
