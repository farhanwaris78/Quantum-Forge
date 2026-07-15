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

public class QEFXComboInteger extends QEFXComboBox<Integer> {

    public QEFXComboInteger(QEValueBuffer valueBuffer, ComboBox<String> controlItem) {
        super(valueBuffer, controlItem);
    }

    @Override
    protected void setToValueBuffer(Integer value) {
        if (value != null) {
            this.valueBuffer.setValue(value.intValue());
        }
    }

    @Override
    protected boolean setToControlItem(Integer value, QEValue qeValue, String item) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (value.intValue() == qeValue.getIntegerValue()) {
            this.controlItem.setValue(item);
            return true;
        }

        return false;
    }
}
