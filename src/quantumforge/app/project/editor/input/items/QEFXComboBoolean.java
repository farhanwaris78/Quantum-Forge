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

public class QEFXComboBoolean extends QEFXComboBox<Boolean> {

    public QEFXComboBoolean(QEValueBuffer valueBuffer, ComboBox<String> controlItem) {
        super(valueBuffer, controlItem);
    }

    @Override
    protected void setToValueBuffer(Boolean value) {
        if (value != null) {
            this.valueBuffer.setValue(value.booleanValue());
        }
    }

    @Override
    protected boolean setToControlItem(Boolean value, QEValue qeValue, String item) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (value.booleanValue() == qeValue.getLogicalValue()) {
            this.controlItem.setValue(item);
            return true;
        }

        return false;
    }
}
