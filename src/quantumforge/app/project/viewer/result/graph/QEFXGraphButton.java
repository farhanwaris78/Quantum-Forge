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

import java.io.File;
import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.graph.QEFXGraphEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;

public abstract class QEFXGraphButton<V extends QEFXGraphViewer<?>> extends QEFXResultButton<V, QEFXGraphEditor> {

    private File propertyFile;

    protected QEFXGraphButton(QEFXProjectController projectController, String title, String subTitle) {
        super(projectController, title, subTitle);
        this.propertyFile = null;
    }

    protected void setPropertyFile(File propertyFile) {
        this.propertyFile = propertyFile;
    }

    protected abstract V createGraphViewer() throws IOException;

    @Override
    protected final V createResultViewer() throws IOException {
        V viewer = this.createGraphViewer();
        if (viewer != null) {
            QEFXGraphViewerController controller = viewer.getController();
            if (controller != null) {
                controller.setPropertyFile(this.propertyFile);
            }
        }

        return viewer;
    }

    @Override
    protected final QEFXGraphEditor createResultEditor(V resultViewer) throws IOException {
        if (resultViewer == null) {
            return null;
        }

        if (this.projectController == null) {
            return null;
        }

        return new QEFXGraphEditor(this.projectController, resultViewer);
    }
}
