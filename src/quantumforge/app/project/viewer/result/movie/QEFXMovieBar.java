/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.movie;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;

public class QEFXMovieBar extends QEFXAppComponent<QEFXMovieBarController> {

    public QEFXMovieBar(QEFXProjectController projectController, QEFXMovieViewerController viewerController) throws IOException {
        super("QEFXMovieBar.fxml", new QEFXMovieBarController(projectController, viewerController));
    }

}
