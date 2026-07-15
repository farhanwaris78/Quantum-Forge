/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.explorer.body.menuitem;

import quantumforge.app.explorer.body.QEFXExplorerBody;
import quantumforge.app.icon.QEFXIcon;

public class QEFXOpenTabMenuItem extends QEFXMenuItem {

    public QEFXOpenTabMenuItem(QEFXIcon icon, QEFXExplorerBody body) {
        super("Open tab");

        if (icon == null) {
            throw new IllegalArgumentException("icon is null.");
        }

        if (body == null) {
            throw new IllegalArgumentException("body is null.");
        }

        this.setOnAction(event -> {
            body.openTabFromIcon(icon);
        });
    }
}
