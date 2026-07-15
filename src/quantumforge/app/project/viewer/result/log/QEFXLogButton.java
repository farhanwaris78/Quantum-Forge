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
import quantumforge.app.project.editor.result.log.QEFXLogEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;

public abstract class QEFXLogButton extends QEFXResultButton<QEFXLogViewer, QEFXLogEditor> {

    private File file;

    protected QEFXLogButton(QEFXProjectController projectController, String title, String subTitle, File file) {
        super(projectController, title, subTitle);

        if (file == null) {
            throw new IllegalArgumentException("file is null.");
        }

        this.file = file;
    }

    @Override
    protected QEFXLogViewer createResultViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }

        return new QEFXLogViewer(this.projectController, this.file);
    }

    @Override
    protected QEFXLogEditor createResultEditor(QEFXLogViewer resultViewer) throws IOException {
        if (resultViewer == null) {
            return null;
        }

        if (this.projectController == null) {
            return null;
        }

        return new QEFXLogEditor(this.projectController, resultViewer);
    }
}
