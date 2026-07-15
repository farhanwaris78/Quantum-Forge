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

public class QEFXToggleInteger extends QEFXToggleButton<Integer> {

    public QEFXToggleInteger(QEValueBuffer valueBuffer, ToggleButton controlItem, boolean defaultSelected) {
        super(valueBuffer, controlItem, defaultSelected);
    }

    public QEFXToggleInteger(QEValueBuffer valueBuffer,
            ToggleButton controlItem, boolean defaultSelected, int onValue, int offValue) {
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
    protected void setToValueBuffer(Integer value) {
        if (value != null) {
            this.valueBuffer.setValue(value.intValue());
        }
    }

    @Override
    protected boolean setToControlItem(Integer value, QEValue qeValue, boolean selected) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (value.intValue() == qeValue.getIntegerValue()) {
            this.controlItem.setSelected(selected);
            return true;
        }

        return false;
    }
}
