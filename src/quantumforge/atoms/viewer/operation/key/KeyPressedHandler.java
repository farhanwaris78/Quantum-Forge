/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer.operation.key;

import javafx.scene.input.KeyEvent;
import quantumforge.atoms.viewer.operation.ViewerEventHandler;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.com.keys.PriorKeyEvent;

public class KeyPressedHandler extends ViewerEventHandler<KeyEvent> {

    public KeyPressedHandler(ViewerEventManager manager, boolean silent) {
        super(manager);

        if (!silent) {
            this.addKernel(new KeyPressedCompassPicking());
            this.addKernel(new KeyPressedCompass());
            this.addKernel(new KeyPressedEditorMenu());
            this.addKernel(new KeyPressedScope());
        }

        this.addKernel(new KeyPressedRegular(silent));
    }

    @Override
    public void handle(KeyEvent event) {
        if (event == null) {
            return;
        }

        if (PriorKeyEvent.isPriorKeyEvent(event)) {
            return;
        }

        event.consume();

        super.handle(event);
    }
}
