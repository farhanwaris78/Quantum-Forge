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
import quantumforge.project.property.ProjectProperty;

public class QEFXDosViewer extends QEFXGraphViewer<QEFXDosViewerController> {

    public QEFXDosViewer(QEFXProjectController projectController, ProjectProperty projectProperty) throws IOException {

        super(new QEFXDosViewerController(projectController, projectProperty));
    }

}
