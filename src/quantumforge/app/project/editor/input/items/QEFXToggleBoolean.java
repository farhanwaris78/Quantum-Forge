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

public class QEFXToggleBoolean extends QEFXToggleButton<Boolean> {

    public QEFXToggleBoolean(QEValueBuffer valueBuffer, ToggleButton controlItem, boolean defaultSelected) {
        this(valueBuffer, controlItem, defaultSelected, false);
    }

    public QEFXToggleBoolean(
            QEValueBuffer valueBuffer, ToggleButton controlItem, boolean defaultSelected, boolean inverse) {
        super(valueBuffer, controlItem, defaultSelected);

        if (inverse) {
            this.setValueFactory(selected -> {
                return !selected;
            });
        }
    }

    @Override
    protected void setToValueBuffer(Boolean value) {
        if (value != null) {
            this.valueBuffer.setValue(value.booleanValue());
        }
    }

    @Override
    protected boolean setToControlItem(Boolean value, QEValue qeValue, boolean selected) {
        if (value == null || qeValue == null) {
            return false;
        }

        if (value.booleanValue() == qeValue.getLogicalValue()) {
            this.controlItem.setSelected(selected);
            return true;
        }

        return false;
    }
}
