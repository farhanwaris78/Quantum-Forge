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

import quantumforge.app.QEFXMainController;
import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;

public abstract class QEFXInputModelController extends QEFXInputController {

    protected Cell modelCell;

    public QEFXInputModelController(QEFXMainController mainController, QEInput input, Cell modelCell) {
        super(mainController, input);

        if (modelCell == null) {
            throw new IllegalArgumentException("modelCell is null.");
        }

        this.modelCell = modelCell;
    }

}
