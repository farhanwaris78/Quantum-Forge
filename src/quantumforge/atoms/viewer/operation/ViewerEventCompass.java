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

import quantumforge.atoms.viewer.AtomsViewer;
import javafx.event.Event;

public abstract class ViewerEventCompass<T extends Event> implements ViewerEventKernel<T> {

    public ViewerEventCompass() {
        // NOP
    }

    @Override
    public final boolean isToPerform(ViewerEventManager manager) {
        if (manager == null) {
            return false;
        }

        AtomsViewer atomsViewer = manager.getAtomsViewer();
        if (atomsViewer == null) {
            return false;
        }

        return atomsViewer.isCompassMode();
    }
}
