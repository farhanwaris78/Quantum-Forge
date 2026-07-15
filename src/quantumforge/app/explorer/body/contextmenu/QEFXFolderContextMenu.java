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
import quantumforge.app.explorer.body.menuitem.QEFXCopyFileMenuItem;
import quantumforge.app.explorer.body.menuitem.QEFXDeleteFileMenuItem;
import quantumforge.app.explorer.body.menuitem.QEFXMakeDirectoryMenuItem;
import quantumforge.app.explorer.body.menuitem.QEFXPasteFileMenuItem;
import quantumforge.app.explorer.body.menuitem.QEFXRenameFileMenuItem;
import quantumforge.app.icon.QEFXFolderIcon;

public class QEFXFolderContextMenu extends QEFXContextMenu<QEFXFolderIcon> {

    public QEFXFolderContextMenu(QEFXFolderIcon icon, QEFXExplorerBody body) {
        super(icon, body);
    }

    @Override
    protected void createMenuItems() {

        this.getItems().add(new QEFXRenameFileMenuItem(this.icon, this.body));

        this.getItems().add(new QEFXCopyFileMenuItem(this.icon, this.body));

        this.getItems().add(new QEFXPasteFileMenuItem(this.icon, this.body));

        this.getItems().add(new QEFXDeleteFileMenuItem(this.icon, this.body));

        this.getItems().add(new QEFXMakeDirectoryMenuItem(this.icon, this.body));
    }
}
