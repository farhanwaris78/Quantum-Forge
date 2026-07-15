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
import quantumforge.input.namelist.QEInteger;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public class QEFXSliderInteger extends QEFXSlider {

    public QEFXSliderInteger(QEValueBuffer valueBuffer, Slider controlItem, int defaultValue) {
        super(valueBuffer, controlItem, new QEInteger("i", defaultValue));
    }

    @Override
    protected void setToValueBuffer(double value) {
        int i = (int) (Math.rint(value) + 0.1);
        this.valueBuffer.setValue(i);
    }

    @Override
    protected void setToControlItem(QEValue qeValue) {
        double maxValue = this.controlItem.getMax();
        double minValue = this.controlItem.getMin();
        double value = qeValue == null ? minValue : qeValue.getIntegerValue();
        value = Math.min(Math.max(minValue, value), maxValue);
        this.controlItem.setValue(value);
    }
}
