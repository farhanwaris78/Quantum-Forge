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

import java.util.Optional;

import quantumforge.app.QEFXMain;
import quantumforge.app.explorer.body.QEFXExplorerBody;
import quantumforge.app.icon.QEFXIcon;
import quantumforge.app.icon.QEFXRunningIcon;
import quantumforge.project.Project;
import quantumforge.run.RunningManager;
import quantumforge.run.RunningNode;
import quantumforge.run.RunningType;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

public class QEFXDeleteFileMenuItem extends QEFXMenuItem {

    public QEFXDeleteFileMenuItem(QEFXIcon icon, QEFXExplorerBody body) {
        super("Delete file");

        if (icon == null) {
            throw new IllegalArgumentException("icon is null.");
        }

        if (body == null) {
            throw new IllegalArgumentException("body is null.");
        }

        if (body.isExplorerMode()) {
            if (icon instanceof QEFXRunningIcon) {
                this.setDisable(true);

            } else {
                this.setOnAction(event -> {
                    body.deleteIcon(icon);
                });
            }

        } else if (body.isRecentlyUsedMode()) {
            this.setOnAction(event -> {
                body.deleteIcon(icon);
            });

        } else if (body.isCalculatingMode()) {
            final RunningNode runningNode;
            if (icon instanceof QEFXRunningIcon) {
                runningNode = ((QEFXRunningIcon) icon).getRunningNode();
            } else {
                runningNode = null;
            }

            if (runningNode != null) {
                this.setOnAction(event -> {
                    this.deleteRunningNode(runningNode);
                });

            } else {
                this.setDisable(true);
            }

        } else if (body.isSearchedMode()) {
            this.setDisable(true);

        } else if (body.isWebMode()) {
            this.setDisable(true);
        }
    }

    private void deleteRunningNode(RunningNode runningNode) {
        if (runningNode == null) {
            return;
        }

        String contentText = "";

        Project project = runningNode.getProject();
        if (project != null) {
            contentText = contentText + "Project: " + project.getRelatedFilePath();
        }

        RunningType runningType = runningNode.getType();
        if (runningType != null) {
            if (!contentText.isEmpty()) {
                contentText = contentText + System.lineSeparator();
            }
            contentText = contentText + "Job: " + runningType.toString();
        }

        int numProcess = runningNode.getNumProcesses();
        if (runningType != null && numProcess > 0) {
            if (!contentText.isEmpty()) {
                contentText = contentText + " ,  ";
            }
            contentText = contentText + "#process: " + numProcess;
        }

        int numThread = runningNode.getNumThreads();
        if (runningType != null && numThread > 0) {
            if (!contentText.isEmpty()) {
                contentText = contentText + " ,  ";
            }
            contentText = contentText + "#thread: " + numThread;
        }

        contentText = contentText + System.lineSeparator();

        Alert alert = new Alert(AlertType.CONFIRMATION);
        QEFXMain.initializeDialogOwner(alert);
        alert.setHeaderText("Calculation will be deleted.");
        alert.setContentText(contentText);

        Optional<ButtonType> optButtonType = alert.showAndWait();
        if (optButtonType == null || (!optButtonType.isPresent())) {
            return;
        }
        if (!ButtonType.OK.equals(optButtonType.get())) {
            return;
        }

        RunningManager.getInstance().removeNode(runningNode);
    }
}
