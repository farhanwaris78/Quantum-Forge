/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.convergence;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewer;
import quantumforge.project.Project;

public class QEFXConvergenceViewer extends QEFXGraphViewer<QEFXConvergenceViewerController> {

    public QEFXConvergenceViewer(QEFXProjectController projectController, Project project) {
        super(new QEFXConvergenceViewerController(projectController), projectController, project);
    }
}
