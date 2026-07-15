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

import quantumforge.atoms.viewer.operation.ViewerEventKernel;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import javafx.event.EventType;
import javafx.scene.input.MouseEvent;

public class MouseEventProxy implements ViewerEventKernel<MouseEvent> {

    private MouseEventHandler handler;

    private MouseEventKernel mouseKernel;

    private ViewerEventManager manager;

    public MouseEventProxy(MouseEventHandler handler, MouseEventKernel mouseKernel) {
        if (handler == null) {
            throw new IllegalArgumentException("handler is null.");
        }

        if (mouseKernel == null) {
            throw new IllegalArgumentException("mouseKernel is null.");
        }

        this.handler = handler;
        this.mouseKernel = mouseKernel;
        this.manager = null;
    }

    public MouseEventHandler getHandler() {
        return this.handler;
    }

    public ViewerEventManager getManager() {
        return this.manager;
    }

    @Override
    public boolean isToPerform(ViewerEventManager manager) {
        // NOP
        return false;
    }

    @Override
    public void perform(ViewerEventManager manager, MouseEvent event) {
        if (manager == null) {
            return;
        }

        if (event == null) {
            return;
        }

        EventType<? extends MouseEvent> eventType = event.getEventType();
        if (eventType == null) {
            return;
        }

        this.manager = manager;

        if (eventType == MouseEvent.MOUSE_PRESSED) {
            this.mouseKernel.performOnMousePressed(event);

        } else if (eventType == MouseEvent.MOUSE_DRAGGED) {
            this.mouseKernel.performOnMouseDragged(event);

        } else if (eventType == MouseEvent.MOUSE_RELEASED) {
            this.mouseKernel.performOnMouseReleased(event);
        }
    }
}
