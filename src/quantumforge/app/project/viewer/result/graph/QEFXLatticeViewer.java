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

public class QEFXLatticeViewer extends QEFXGraphViewer<QEFXLatticeViewerController> {

    public QEFXLatticeViewer(QEFXProjectController projectController,
            ProjectProperty projectProperty, LatticeViewerType lattVType, boolean mdMode) throws IOException {

        super(new QEFXLatticeViewerController(projectController, projectProperty, lattVType, mdMode));
    }

}
