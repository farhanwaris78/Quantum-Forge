/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer.operation;

import javafx.event.Event;

public abstract class ViewerEventRegular<T extends Event> implements ViewerEventKernel<T> {

    public ViewerEventRegular() {
        // NOP
    }

    @Override
    public final boolean isToPerform(ViewerEventManager manager) {
        if(manager == null) {
            return false;
        }

        return true;
    }
}
