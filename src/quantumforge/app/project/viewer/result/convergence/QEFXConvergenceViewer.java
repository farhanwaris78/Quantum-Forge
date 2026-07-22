/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.convergence;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewer;

public class QEFXConvergenceViewer extends QEFXGraphViewer<QEFXConvergenceViewerController> {

    public QEFXConvergenceViewer(QEFXProjectController projectController) throws IOException {
        super(new QEFXConvergenceViewerController(projectController));
    }
}
