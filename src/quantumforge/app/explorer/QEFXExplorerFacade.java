/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.explorer;

import javafx.scene.control.TextField;
import quantumforge.app.explorer.body.QEFXExplorerBody;
import quantumforge.matapi.MaterialsAPILoader;
import quantumforge.project.Project;

public class QEFXExplorerFacade {

    private boolean singleReloadMode;
    private boolean hasNotReloaded;

    private QEFXExplorerController controller;

    public QEFXExplorerFacade(QEFXExplorerController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }

        this.singleReloadMode = false;
        this.hasNotReloaded = false;

        this.controller = controller;
    }

    public String getLocation() {
        TextField locationField = this.controller.getLocationField();
        return locationField == null ? null : locationField.getText();
    }

    public void setLocation(String location) {
        TextField locationField = this.controller.getLocationField();
        if (locationField != null) {
            locationField.setText(location == null ? "" : location);
            this.controller.updateExplorerBody();
        }
    }

    public void startSingleReloadMode() {
        this.singleReloadMode = true;
        this.hasNotReloaded = true;
    }

    public void endSingleReloadMode() {
        this.singleReloadMode = false;
        this.hasNotReloaded = false;
    }

    public void reloadLocation() {
        if (this.singleReloadMode) {
            if (this.hasNotReloaded) {
                this.hasNotReloaded = false;
                this.controller.updateExplorerBody();
            }

        } else {
            this.controller.updateExplorerBody();
        }
    }

    public void goBackward() {
        this.controller.pushBackwardButton();
    }

    public void goForward() {
        this.controller.pushForwardButton();
    }

    public void goUpward() {
        this.controller.pushUpwardButton();
    }

    public void refreshProject(Project project) {
        this.controller.refreshProject(project);
    }

    public boolean isExplorerMode() {
        QEFXExplorerBody explorerBody = this.controller.getExplorerBody();
        return explorerBody == null ? false : explorerBody.isExplorerMode();
    }

    public boolean isRecentlyUsedMode() {
        QEFXExplorerBody explorerBody = this.controller.getExplorerBody();
        return explorerBody == null ? false : explorerBody.isRecentlyUsedMode();
    }

    public boolean isCalculatingMode() {
        QEFXExplorerBody explorerBody = this.controller.getExplorerBody();
        return explorerBody == null ? false : explorerBody.isCalculatingMode();
    }

    public boolean isSearchedMode() {
        QEFXExplorerBody explorerBody = this.controller.getExplorerBody();
        return explorerBody == null ? false : explorerBody.isSearchedMode();
    }

    public boolean isWebMode() {
        QEFXExplorerBody explorerBody = this.controller.getExplorerBody();
        return explorerBody == null ? false : explorerBody.isWebMode();
    }

    public void setRecentlyUsedMode() {
        this.setLocation(QEFXExplorerBody.CODE_RECENTLY_USED);
    }

    public void setCalculatingMode() {
        this.setLocation(QEFXExplorerBody.CODE_CALCULATING);
    }

    public void setSearchedMode() {
        this.setLocation(QEFXExplorerBody.CODE_SEARCHED);
    }

    public void setWebMode() {
        this.setLocation(QEFXExplorerBody.CODE_WEB);
    }

    public void setMaterialsAPILoader(MaterialsAPILoader matApiLoader) {
        this.controller.setMaterialsAPILoader(matApiLoader);
    }
}
