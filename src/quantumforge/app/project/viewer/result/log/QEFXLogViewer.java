/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.log;

import java.io.File;
import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewer;

public class QEFXLogViewer extends QEFXResultViewer<QEFXLogViewerController> {

    public QEFXLogViewer(QEFXProjectController projectController, File file) throws IOException {
        super("QEFXLogViewer.fxml", new QEFXLogViewerController(projectController, file));
    }

}
