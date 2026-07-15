/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.model.property;

import java.util.ArrayList;
import java.util.List;

public class ModelProperty {

    private Object property;

    private List<ModelPropertyListener> listeners;

    public ModelProperty() {
        this.property = null;
        this.listeners = null;
    }

    public Object getProperty() {
        return this.property;
    }

    public void setProperty(Object property) {
        this.property = property;

        if (this.listeners != null) {
            for (ModelPropertyListener listener : this.listeners) {
                if (listener != null) {
                    listener.onPropertyChanged(this.property);
                }
            }
        }
    }

    public void addListener(ModelPropertyListener listener) {
        if (listener == null) {
            return;
        }

        if (this.listeners == null) {
            this.listeners = new ArrayList<ModelPropertyListener>();
        }

        this.listeners.add(listener);
    }

    public void removeListener(ModelPropertyListener listener) {
        if (listener == null) {
            return;
        }

        if (this.listeners == null) {
            return;
        }

        this.listeners.remove(listener);
    }
}
