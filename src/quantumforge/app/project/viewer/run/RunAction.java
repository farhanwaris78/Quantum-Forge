/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.run;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import quantumforge.app.QEFXMain;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.ssh.HostKeyAcceptance;
import quantumforge.com.log.AppLog;
import quantumforge.hpc.JobRecord;
import quantumforge.operation.OperationResult;
import quantumforge.run.RunningManager;
import quantumforge.run.RunningNode;
import quantumforge.ssh.SSHJob;
import quantumforge.ssh.SshTransport;

public class RunAction {

    private QEFXProjectController controller;

    public RunAction(QEFXProjectController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }
        this.controller = controller;
    }

    public void runCalculation(RunEvent runEvent) {
        if (runEvent == null) {
            return;
        }
        if (runEvent.getRunningNode() != null) {
            this.runOnLocalMachine(runEvent.getRunningNode());
        } else if (runEvent.getSSHJob() != null) {
            this.runOnSSHServer(runEvent.getSSHJob());
        }
    }

    private void runOnLocalMachine(RunningNode runningNode) {
        if (runningNode == null) {
            return;
        }
        RunningManager.getInstance().addNode(runningNode);
        QEFXMainController mainController = this.controller.getMainController();
        if (mainController == null) {
            return;
        }
        mainController.offerOnHomeTabSelected(explorerFacade -> {
            if (explorerFacade != null && (!explorerFacade.isCalculatingMode())) {
                explorerFacade.setCalculatingMode();
            }
        });
        mainController.showHome();
    }

    private void runOnSSHServer(SSHJob sshJob) {
        if (sshJob == null) {
            return;
        }
        // Prefer typed API; never treat missing transport as success.
        OperationResult<SshTransport> connect =
                HostKeyAcceptance.connectInteractive(sshJob.getSSHServer(), true);
        if (!connect.isSuccess()) {
            AppLog.error("ssh-run", connect.toString());
            showError("Remote submission unavailable", connect.getMessage());
            return;
        }
        SshTransport transport = connect.getValue().orElse(null);
        try {
            sshJob.setTransport(transport);
            OperationResult<JobRecord> result = sshJob.postJobToServerResult();
            if (!result.isSuccess()) {
                AppLog.error("ssh-run", result.toString());
                showError("Remote submission failed", result.getMessage());
                return;
            }
            JobRecord record = result.getValue().orElse(null);
            String id = record == null ? "?" : String.valueOf(record.getSchedulerJobId());
            AppLog.info("ssh-run", "Submitted remote job " + id);
            Alert alert = new Alert(AlertType.INFORMATION);
            QEFXMain.initializeDialogOwner(alert);
            alert.setTitle("Remote job");
            alert.setHeaderText("Job submitted");
            alert.setContentText(result.getMessage()
                    + (record == null ? "" : "\nState: " + record.getState()));
            alert.showAndWait();
        } finally {
            if (transport != null) {
                transport.close();
            }
        }
    }

    private static void showError(String header, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        QEFXMain.initializeDialogOwner(alert);
        alert.setTitle("Remote job");
        alert.setHeaderText(header);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }
}
