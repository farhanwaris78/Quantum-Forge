/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.convergence;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewer;
import quantumforge.project.Project;

public class QEFXConvergenceViewer extends QEFXResultViewer<QEFXConvergenceViewerController> {

    public QEFXConvergenceViewer(QEFXProjectController projectController, Project project) {
        super("QEFXGraphViewer.fxml", new QEFXConvergenceViewerController(projectController), projectController, project);
    }
}
