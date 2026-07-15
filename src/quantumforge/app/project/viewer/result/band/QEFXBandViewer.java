/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.band;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewer;
import quantumforge.project.property.ProjectProperty;

public class QEFXBandViewer extends QEFXResultViewer<QEFXBandViewerController> {

    public QEFXBandViewer(QEFXProjectController projectController, ProjectProperty projectProperty) throws IOException {

        super("QEFXBandViewer.fxml", new QEFXBandViewerController(projectController, projectProperty));
    }

}
