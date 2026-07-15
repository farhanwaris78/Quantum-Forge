/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.band;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.QEFXResultEditor;
import quantumforge.app.project.viewer.result.band.QEFXBandViewer;

public class QEFXBandEditor extends QEFXResultEditor<QEFXBandEditorController> {

    public QEFXBandEditor(QEFXProjectController projectController, QEFXBandViewer viewer) throws IOException {
        super("QEFXBandEditor.fxml",
                new QEFXBandEditorController(projectController, viewer == null ? null : viewer.getController()));
    }

}
