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
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public abstract class QEFXSlider extends QEFXItem<Slider> {

    private QEValue defaultValue;

    protected QEFXSlider(QEValueBuffer valueBuffer, Slider controlItem, QEValue defaultValue) {
        super(valueBuffer, controlItem);

        this.defaultValue = defaultValue;
        this.setupSlider();
    }

    protected abstract void setToValueBuffer(double value);

    protected abstract void setToControlItem(QEValue qeValue);

    private void setupSlider() {
        if (this.valueBuffer.hasValue()) {
            this.onValueChanged(this.valueBuffer.getValue());
        } else {
            this.setToControlItem(this.defaultValue);
            this.setToValueBuffer(this.controlItem.getValue());
        }

        this.controlItem.valueProperty().addListener(o -> {
            double value = this.controlItem.getValue();
            this.setToValueBuffer(value);
        });
    }

    @Override
    protected void onValueChanged(QEValue qeValue) {
        if (qeValue == null) {
            this.setToControlItem(this.defaultValue);
        } else {
            this.setToControlItem(qeValue);
        }
    }
}
