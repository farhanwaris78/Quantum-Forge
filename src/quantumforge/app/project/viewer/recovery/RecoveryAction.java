/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.recovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import quantumforge.app.QEFXMain;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.com.log.AppLog;
import quantumforge.project.Project;
import quantumforge.project.ProjectRecovery;
import quantumforge.project.ProjectRecovery.SnapshotInfo;

/**
 * GUI action: list autosave snapshots and restore one after confirmation.
 */
public final class RecoveryAction {

    private final Project project;
    private final QEFXProjectController controller;

    public RecoveryAction(Project project, QEFXProjectController controller) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }
        this.project = project;
        this.controller = controller;
    }

    public boolean recover() {
        String dir = this.project.getDirectoryPath();
        if (dir == null || dir.isBlank()) {
            showInfo("This project has no directory yet. Save the project first so autosave can run.");
            return false;
        }
        Path projectDir = Path.of(dir);
        try {
            List<SnapshotInfo> snapshots = ProjectRecovery.listSnapshots(projectDir);
            if (snapshots.isEmpty()) {
                showInfo("No autosave snapshots were found under "
                        + projectDir.resolve(quantumforge.project.ProjectAutosave.SNAPSHOT_DIR));
                return false;
            }

            ChoiceDialog<SnapshotInfo> dialog = new ChoiceDialog<>(snapshots.get(0), snapshots);
            QEFXMain.initializeDialogOwner(dialog);
            dialog.setTitle("Recover autosave snapshot");
            dialog.setHeaderText("Select a snapshot to restore into the project directory.\n"
                    + "A backup of the current inputs will be written first.");
            dialog.setContentText("Snapshot:");
            Optional<SnapshotInfo> choice = dialog.showAndWait();
            if (choice.isEmpty()) {
                return false;
            }

            Alert confirm = new Alert(AlertType.CONFIRMATION);
            QEFXMain.initializeDialogOwner(confirm);
            confirm.setTitle("Confirm restore");
            confirm.setHeaderText("Restore " + choice.get().getPath().getFileName() + "?");
            confirm.setContentText("Current espresso.*.in files will be copied to a "
                    + ".quantumforge.pre-restore-* backup before overwrite.");
            Optional<ButtonType> answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.OK) {
                return false;
            }

            ProjectRecovery.restoreSnapshot(projectDir, choice.get().getPath());
            AppLog.info("recovery", "User restored snapshot " + choice.get().getPath());
            showInfo("Snapshot restored. Re-open or reload the project tab to refresh editors.");
            // Best-effort: ask controller to refresh if it has a public hook.
            try {
                this.controller.saveFile();
            } catch (RuntimeException ignored) {
                // saveFile may rewrite current memory state; ignore if project dirty handling differs.
            }
            return true;
        } catch (IOException ex) {
            AppLog.error("recovery", "Recovery failed", ex);
            Alert alert = new Alert(AlertType.ERROR);
            QEFXMain.initializeDialogOwner(alert);
            alert.setTitle("Recovery failed");
            alert.setHeaderText("Could not restore autosave snapshot.");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            return false;
        }
    }

    private static void showInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        QEFXMain.initializeDialogOwner(alert);
        alert.setTitle("Autosave recovery");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
