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

public class QEFXCopyFileMenuItem extends QEFXMenuItem {

    public QEFXCopyFileMenuItem(QEFXIcon icon, QEFXExplorerBody body) {
        super("Copy file");

        if (icon == null) {
            throw new IllegalArgumentException("icon is null.");
        }

        if (body == null) {
            throw new IllegalArgumentException("body is null.");
        }

        if (body.isExplorerMode()) {
            this.setOnAction(event -> {
                body.copyIcon(icon);
            });

        } else if (body.isRecentlyUsedMode()) {
            this.setOnAction(event -> {
                body.copyIcon(icon);
            });

        } else if (body.isCalculatingMode()) {
            this.setOnAction(event -> {
                body.copyIcon(icon);
            });

        } else if (body.isSearchedMode()) {
            this.setOnAction(event -> {
                body.copyIcon(icon);
            });

        } else if (body.isWebMode()) {
            this.setDisable(true);
        }
    }
}
