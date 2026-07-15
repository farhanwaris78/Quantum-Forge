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

public class QEFXComboString extends QEFXComboBox<String> {

    public QEFXComboString(QEValueBuffer valueBuffer, ComboBox<String> controlItem) {
        super(valueBuffer, controlItem);
    }

    @Override
    protected void setToValueBuffer(String value) {
        if (value != null) {
            this.valueBuffer.setValue(value);
        }
    }

    @Override
    protected boolean setToControlItem(String value, QEValue qeValue, String item) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (value.equals(qeValue.getCharacterValue())) {
            this.controlItem.setValue(item);
            return true;
        }

        return false;
    }
}
