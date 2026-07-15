/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.opt;

import java.io.IOException;

import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorComponent;
import quantumforge.input.QEInput;

public class QEFXOpt extends QEFXEditorComponent<QEFXOptController> {

    public QEFXOpt(QEFXMainController mainController, QEInput input) throws IOException {
        super("QEFXOpt.fxml", new QEFXOptController(mainController, input));

        if (this.node != null) {
            this.node.setOnMouseReleased(event -> this.node.requestFocus());
        }
    }

    @Override
    public void notifyEditorOpened() {
        // TODO 自動生成されたメソ�?ド�?�スタ�?
    }

}
