/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input;

import quantumforge.app.QEFXAppController;
import quantumforge.app.QEFXMainController;
import quantumforge.input.QEInput;

public abstract class QEFXInputController extends QEFXAppController {

    protected QEInput input;

    public QEFXInputController(QEFXMainController mainController, QEInput input) {
        super(mainController);

        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        this.input = input;
    }
}
