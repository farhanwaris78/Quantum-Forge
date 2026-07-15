/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.qc;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewer;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectProperty;

public class QEFXQCViewer extends QEFXResultViewer<QEFXQCViewerController> {

    public QEFXQCViewer(QEFXProjectController projectController, Project project) {
        super("QEFXGraphViewer.fxml", new QEFXQCViewerController(projectController, project.getProperty()), projectController, project);
    }
}
