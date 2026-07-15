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

public class QEFXPasteFileMenuItem extends QEFXMenuItem {

    public QEFXPasteFileMenuItem(QEFXIcon icon, QEFXExplorerBody body) {
        super("Paste file");

        //if (icon == null) {
        //    throw new IllegalArgumentException("icon is null.");
        //}

        if (body == null) {
            throw new IllegalArgumentException("body is null.");
        }

        if (body.isExplorerMode()) {
            this.setDisable(!body.hasClippedIcon());
            this.setOnAction(event -> {
                body.pasteIcon(icon);
            });

        } else if (body.isRecentlyUsedMode()) {
            this.setDisable(true);

        } else if (body.isCalculatingMode()) {
            this.setDisable(true);

        } else if (body.isSearchedMode()) {
            this.setDisable(true);

        } else if (body.isWebMode()) {
            this.setDisable(true);
        }
    }
}
