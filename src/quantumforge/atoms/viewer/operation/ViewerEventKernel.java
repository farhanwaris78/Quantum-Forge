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

public interface ViewerEventKernel<T extends Event> {

    public static final double KEY_SCALE_SPEED = 1.1;

    public static final double KEY_ROTATE_SPEED = 10.0;

    public static final double KEY_TRANS_SPEED = 10.0;

    public static final double MOUSE_SCALE_SPEED = 0.01;

    public static final double MOUSE_ROTATE_SPEED = 0.50;

    public static final double MOUSE_TRANS_SPEED = 1.50;

    public static final double SCROLL_SCALE_SPEED = 0.002;

    public abstract boolean isToPerform(ViewerEventManager manager);

    public abstract void perform(ViewerEventManager manager, T event);

}
