/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.qc;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewer;
import quantumforge.project.Project;

public class QEFXQCViewer extends QEFXGraphViewer<QEFXQCViewerController> {

    public QEFXQCViewer(QEFXProjectController projectController, Project project) throws IOException {
        super(new QEFXQCViewerController(projectController, project.getProperty()));
    }
}
