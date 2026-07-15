/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.visible;

import quantumforge.atoms.design.Design;
import quantumforge.atoms.model.Model;
import quantumforge.atoms.model.event.ModelEvent;
import quantumforge.atoms.model.event.ModelEventListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Group;

public abstract class Visible<M extends Model<? extends ModelEvent, ? extends ModelEventListener>> extends Group
        implements ModelEventListener {

    protected M model;

    protected Design design;

    private BooleanProperty toBeFlushed;

    protected Visible(M model, Design design) {
        super();

        if (model == null) {
            throw new IllegalArgumentException("model is null.");
        }

        this.model = model;
        this.design = design;
        this.toBeFlushed = null;
    }

    public M getModel() {
        return this.model;
    }

    public BooleanProperty toBeFlushedProperty() {
        if (this.toBeFlushed == null) {
            this.toBeFlushed = new SimpleBooleanProperty(false);
        }

        return this.toBeFlushed;
    }

    public void setToBeFlushed(boolean toBeFlushed) {
        this.toBeFlushedProperty().set(toBeFlushed);
    }

    @Override
    public boolean isToBeFlushed() {
        return this.toBeFlushedProperty().get();
    }

    @Override
    public void onModelDisplayed(ModelEvent event) {
        this.setVisible(true);
    }

    @Override
    public void onModelNotDisplayed(ModelEvent event) {
        this.setVisible(false);
    }
}
