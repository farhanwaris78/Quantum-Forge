/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.run;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import quantumforge.app.QEFXMain;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.ssh.HostKeyAcceptance;
import quantumforge.app.ssh.QEFXJobMonitorDialog;
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
        boolean transportHandedOff = false;
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
            if (record != null) {
                // Batch-138 (#96 GUI slice): offer the polling monitor; YES hands the
                // transport to the dialog, which owns and closes it exactly once.
                transportHandedOff = offerMonitoring(sshJob, transport,
                        result.getMessage(), record);
            } else {
                Alert alert = new Alert(AlertType.INFORMATION);
                QEFXMain.initializeDialogOwner(alert);
                alert.setTitle("Remote job");
                alert.setHeaderText("Job submitted");
                alert.setContentText(result.getMessage());
                alert.showAndWait();
            }
        } finally {
            if (transport != null && !transportHandedOff) {
                transport.close();
            }
        }
    }

    /**
     * Offer the job monitor after a successful submit. Returns true when the
     * transport's ownership moved to the monitor dialog (which closes it);
     * otherwise the caller must close the transport itself.
     */
    private static boolean offerMonitoring(SSHJob sshJob, SshTransport transport,
            String submitMessage, JobRecord record) {
        ButtonType monitorButton = new ButtonType("Monitor job", ButtonBar.ButtonData.YES);
        Alert alert = new Alert(AlertType.INFORMATION,
                submitMessage + "\nState: " + record.getState()
                        + "\n\nMonitor this job now? The monitor polls the scheduler on a "
                        + "background daemon with stated backoff and then owns this "
                        + "connection until you close it.",
                monitorButton, ButtonType.CLOSE);
        QEFXMain.initializeDialogOwner(alert);
        alert.setTitle("Remote job");
        alert.setHeaderText("Job submitted");
        boolean monitor = alert.showAndWait()
                .filter(button -> button == monitorButton).isPresent();
        if (!monitor) {
            return false;
        }
        try {
            QEFXJobMonitorDialog.showAndMonitor(transport, sshJob.monitorAdapter(), record);
            return true;
        } catch (RuntimeException ex) {
            // Typed failures (e.g. an unknown site-profile scheduler) stay honest.
            AppLog.error("ssh-run", "monitor unavailable: " + ex.getMessage());
            showError("Monitoring unavailable", String.valueOf(ex.getMessage()));
            return false;
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
