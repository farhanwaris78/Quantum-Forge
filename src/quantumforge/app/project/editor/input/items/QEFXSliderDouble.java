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

import javafx.scene.control.Slider;
import quantumforge.input.namelist.QEReal;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public class QEFXSliderDouble extends QEFXSlider {

    public QEFXSliderDouble(QEValueBuffer valueBuffer, Slider controlItem, double defaultValue) {
        super(valueBuffer, controlItem, new QEReal("x", defaultValue));
    }

    @Override
    protected void setToValueBuffer(double value) {
        this.valueBuffer.setValue(value);
    }

    @Override
    protected void setToControlItem(QEValue qeValue) {
        double maxValue = this.controlItem.getMax();
        double minValue = this.controlItem.getMin();
        double value = qeValue == null ? minValue : qeValue.getRealValue();
        value = Math.min(Math.max(minValue, value), maxValue);
        this.controlItem.setValue(value);
    }
}
