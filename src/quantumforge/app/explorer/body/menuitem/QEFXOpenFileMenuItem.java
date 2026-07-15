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

import quantumforge.app.fileview.QEFXFileViewDialog;

public class QEFXOpenFileMenuItem extends QEFXMenuItem {

    public QEFXOpenFileMenuItem(String filePath) {
        super("Open file");

        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath is empty.");
        }

        this.setOnAction(event -> {
            QEFXFileViewDialog fileDialog = new QEFXFileViewDialog(filePath);
            fileDialog.show();
        });
    }
}
