/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer.operation.mouse;

import quantumforge.atoms.viewer.operation.ViewerEventEditorMenu;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import javafx.scene.input.MouseEvent;

public class MouseEventEditorMenu extends ViewerEventEditorMenu<MouseEvent> implements MouseEventKernel {

    private MouseEventProxy proxy;

    public MouseEventEditorMenu(MouseEventHandler handler) {
        super();
        this.proxy = new MouseEventProxy(handler, this);
    }

    @Override
    public void perform(ViewerEventManager manager, MouseEvent event) {
        this.proxy.perform(manager, event);
    }

    @Override
    public void performOnMousePressed(MouseEvent event) {
        // NOP
    }

    @Override
    public void performOnMouseDragged(MouseEvent event) {
        // NOP
    }

    @Override
    public void performOnMouseReleased(MouseEvent event) {
        // NOP
    }
}
