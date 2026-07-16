/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.neb;

import java.io.IOException;

import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorComponent;
import quantumforge.input.QEInput;

public class QEFXNeb extends QEFXEditorComponent<QEFXNebController> {

    public QEFXNeb(QEFXMainController mainController, QEInput input) throws IOException {
        super("QEFXNeb.fxml", new QEFXNebController(mainController));

        if(input == null) {
            throw new IllegalArgumentException("input is null.");
        }
    }

    @Override
    public void notifyEditorOpened() {
        // Legacy generated TODO removed; no action is required on editor open.
    }

}
