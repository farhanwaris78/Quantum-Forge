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

import javafx.scene.control.ContextMenu;
import quantumforge.app.explorer.body.QEFXExplorerBody;
import quantumforge.app.icon.QEFXFolderIcon;
import quantumforge.app.icon.QEFXIcon;
import quantumforge.app.icon.QEFXProjectIcon;
import quantumforge.app.icon.QEFXUPFIcon;
import quantumforge.app.icon.QEFXWebIcon;

public abstract class QEFXContextMenu<I extends QEFXIcon> extends ContextMenu {

    public static ContextMenu getContextMenu(QEFXIcon icon, QEFXExplorerBody body) {
        if (body == null) {
            return null;
        }

        if (icon == null) {
            return new QEFXBackgroundContextMenu(body);

        } else if (icon instanceof QEFXProjectIcon) {
            return new QEFXProjectContextMenu((QEFXProjectIcon) icon, body);

        } else if (icon instanceof QEFXWebIcon) {
            return new QEFXWebContextMenu((QEFXWebIcon) icon, body);

        } else if (icon instanceof QEFXUPFIcon) {
            return new QEFXUPFContextMenu((QEFXUPFIcon) icon, body);

        } else if (icon instanceof QEFXFolderIcon) {
            return new QEFXFolderContextMenu((QEFXFolderIcon) icon, body);
        }

        return null;
    }

    protected I icon;

    protected QEFXExplorerBody body;

    protected QEFXContextMenu(I icon, QEFXExplorerBody body) {
        super();

        //if (icon == null) {
        //    throw new IllegalArgumentException("icon is null.");
        //}

        if (body == null) {
            throw new IllegalArgumentException("body is null.");
        }

        this.icon = icon;
        this.body = body;
        this.getStyleClass().add("icon-context-menu");

        this.getItems().clear();
        this.createMenuItems();

        this.setOnShowing(event -> {
            this.getItems().clear();
            this.createMenuItems();
        });
    }

    protected abstract void createMenuItems();
}
