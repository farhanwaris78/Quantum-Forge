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

import quantumforge.atoms.viewer.operation.ViewerEventEditorMenu;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import javafx.scene.input.ScrollEvent;

public class ScrollEditorMenu extends ViewerEventEditorMenu<ScrollEvent> {

    public ScrollEditorMenu() {
        super();
    }

    @Override
    public void perform(ViewerEventManager manager, ScrollEvent event) {
        // NOP
    }
}
