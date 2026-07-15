/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.designer;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.atoms.viewer.AtomsViewer;

public class QEFXDesignerWindow extends QEFXAppComponent<QEFXDesignerWindowController> {

    public QEFXDesignerWindow(QEFXProjectController projectController, AtomsViewer atomsViewer) throws IOException {
        super("QEFXDesignerWindow.fxml", new QEFXDesignerWindowController(projectController, atomsViewer));
    }

    public void setWidth(double width) {
        if (this.controller != null) {
            this.controller.setWidth(width);
        }
    }

    public void setHeight(double height) {
        if (this.controller != null) {
            this.controller.setHeight(height);
        }
    }

    public void setOnWindowMaximized(WindowMaximized onWindowMaximized) {
        if (this.controller != null) {
            this.controller.setOnWindowMaximized(onWindowMaximized);
        }
    }
}
