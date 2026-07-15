/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.model.event;

public abstract class ModelEvent {

    protected Object source;

    protected ModelEvent(Object source) {
        this.source = source;
    }

    public Object getSource() {
        return this.source;
    }
}
