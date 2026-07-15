/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.band;

import java.io.IOException;

import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorComponent;
import quantumforge.input.QEInput;

public class QEFXBand extends QEFXEditorComponent<QEFXBandController> {

    public QEFXBand(QEFXMainController mainController, QEInput input) throws IOException {
        super("QEFXBand.fxml", new QEFXBandController(mainController, input));

        if (this.node != null) {
            this.node.setOnMouseReleased(event -> this.node.requestFocus());
        }
    }

    @Override
    public void notifyEditorOpened() {
        this.controller.updateNBandStatus();
    }

}
