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

import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.util.Callback;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public class QEFXTextField extends QEFXItem<TextField> {

    private boolean busyText;

    protected Callback<String, String> valueFactory;

    protected Callback<String, String> textFactory;

    public QEFXTextField(QEValueBuffer valueBuffer, TextField controlItem) {
        super(valueBuffer, controlItem);

        this.busyText = false;
        this.valueFactory = null;
        this.textFactory = null;
        this.setupTextField();
    }

    private void setupTextField() {
        if (this.valueBuffer.hasValue()) {
            this.onValueChanged(this.valueBuffer.getValue());
        } else {
            this.controlItem.setText("");
        }

        this.controlItem.textProperty().addListener(o -> {

            this.busyText = true;

            String text = this.controlItem.getText();
            String value = text;
            if (this.valueFactory != null) {
                value = this.valueFactory.call(value);
            }

            if (value != null && !value.trim().isEmpty()) {
                this.valueBuffer.setValue(value);
            } else {
                this.valueBuffer.removeValue();
            }

            this.busyText = false;
        });
    }

    @Override
    protected void onValueChanged(QEValue value) {
        if (this.busyText) {
            return;
        }

        String text = null;
        if (value != null) {
            text = value.getCharacterValue();
        }
        if (this.textFactory != null) {
            text = this.textFactory.call(text);
        }

        if (text == null) {
            this.controlItem.setText("");
        } else {
            this.controlItem.setText(text);
        }
    }

    public void setHintMessage(String message) {
        this.controlItem.setTooltip(new Tooltip(message));
    }

    public void setValueFactory(Callback<String, String> valueFactory) {
        this.valueFactory = valueFactory;
    }

    public void setTextFactory(Callback<String, String> textFactory) {
        this.textFactory = textFactory;

        if (this.valueBuffer.hasValue()) {
            this.onValueChanged(this.valueBuffer.getValue());
        }

        this.pullAllTriggers();
    }
}
