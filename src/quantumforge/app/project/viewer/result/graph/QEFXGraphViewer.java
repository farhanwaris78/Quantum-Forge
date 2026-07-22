/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.graph;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewer;
import quantumforge.project.Project;

public abstract class QEFXGraphViewer<V extends QEFXGraphViewerController> extends QEFXResultViewer<V> {

    public QEFXGraphViewer(V controller) throws IOException {
        super("QEFXGraphViewer.fxml", controller);
    }

    protected QEFXGraphViewer(V controller, QEFXProjectController projectController,
            Project project) throws IOException {
        super("QEFXGraphViewer.fxml", controller, projectController, project);
    }

}
