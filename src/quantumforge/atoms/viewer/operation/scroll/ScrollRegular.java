/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer.operation.scroll;

import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.atoms.viewer.operation.ViewerEventRegular;
import javafx.scene.input.ScrollEvent;

public class ScrollRegular extends ViewerEventRegular<ScrollEvent> {

    public ScrollRegular() {
        super();
    }

    @Override
    public void perform(ViewerEventManager manager, ScrollEvent event) {
        double dy = event.getDeltaY();

        if (dy != 0.0) {
            double eta = 1.0 - Math.tanh(SCROLL_SCALE_SPEED * dy);
            manager.getAtomsViewer().appendCellScale(eta);
        }
    }
}
