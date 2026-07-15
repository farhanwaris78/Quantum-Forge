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

import javafx.scene.input.ScrollEvent;
import quantumforge.atoms.viewer.operation.ViewerEventHandler;
import quantumforge.atoms.viewer.operation.ViewerEventManager;

public class ScrollHandler extends ViewerEventHandler<ScrollEvent> {

    public ScrollHandler(ViewerEventManager manager, boolean silent) {
        super(manager);

        if (!silent) {
            this.addKernel(new ScrollCompassPicking());
            this.addKernel(new ScrollCompass());
            this.addKernel(new ScrollEditorMenu());
            this.addKernel(new ScrollScope());
        }

        this.addKernel(new ScrollRegular());
    }

    @Override
    public void handle(ScrollEvent event) {
        if (event == null) {
            return;
        }

        event.consume();

        super.handle(event);
    }
}
