/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.items;

import javafx.scene.control.ComboBox;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public class QEFXComboDouble extends QEFXComboBox<Double> {

    private double DELTA = 1.0e-20;

    public QEFXComboDouble(QEValueBuffer valueBuffer, ComboBox<String> controlItem) {
        super(valueBuffer, controlItem);
    }

    @Override
    protected void setToValueBuffer(Double value) {
        if (value != null) {
            this.valueBuffer.setValue(value.doubleValue());
        }
    }

    @Override
    protected boolean setToControlItem(Double value, QEValue qeValue, String item) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (Math.abs(value.doubleValue() - qeValue.getRealValue()) < DELTA) {
            this.controlItem.setValue(item);
            return true;
        }

        return false;
    }
}
