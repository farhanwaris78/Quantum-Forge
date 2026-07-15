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

import javafx.scene.control.ToggleButton;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public class QEFXToggleString extends QEFXToggleButton<String> {

    public QEFXToggleString(QEValueBuffer valueBuffer, ToggleButton controlItem, boolean defaultSelected) {
        super(valueBuffer, controlItem, defaultSelected);
    }

    public QEFXToggleString(QEValueBuffer valueBuffer,
            ToggleButton controlItem, boolean defaultSelected, String onValue, String offValue) {
        this(valueBuffer, controlItem, defaultSelected);

        this.setValueFactory(selected -> {
            if (selected) {
                return onValue;
            } else {
                return offValue;
            }
        });
    }

    @Override
    protected void setToValueBuffer(String value) {
        if (value != null) {
            this.valueBuffer.setValue(value);
        }
    }

    @Override
    protected boolean setToControlItem(String value, QEValue qeValue, boolean selected) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (value.equals(qeValue.getCharacterValue())) {
            this.controlItem.setSelected(selected);
            return true;
        }

        return false;
    }
}
