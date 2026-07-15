/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.explorer.body.contextmenu;

import quantumforge.app.explorer.body.QEFXExplorerBody;
import quantumforge.app.explorer.body.menuitem.QEFXMakeDirectoryMenuItem;
import quantumforge.app.explorer.body.menuitem.QEFXPasteFileMenuItem;
import quantumforge.app.icon.QEFXIcon;

public class QEFXBackgroundContextMenu extends QEFXContextMenu<QEFXIcon> {

    public QEFXBackgroundContextMenu(QEFXExplorerBody body) {
        super(null, body);
    }

    @Override
    protected void createMenuItems() {
        this.getItems().clear();

        this.getItems().add(new QEFXPasteFileMenuItem(null, this.body));

        this.getItems().add(new QEFXMakeDirectoryMenuItem(null, this.body));
    }
}
