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

import javafx.scene.input.MouseEvent;

public interface MouseEventKernel {

    public abstract void performOnMousePressed(MouseEvent event);

    public abstract void performOnMouseDragged(MouseEvent event);

    public abstract void performOnMouseReleased(MouseEvent event);

}
