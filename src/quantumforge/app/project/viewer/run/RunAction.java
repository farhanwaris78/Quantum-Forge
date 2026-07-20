/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.run;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
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
import quantumforge.ssh.RemoteSubmitChain;
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

    /**
     * Batch-139 (#96 FX-thread depth): the whole connect+submit phase runs on
     * a dedicated daemon (thread {@code qf-ssh-submit}) - a slow or unreachable
     * cluster previously FROZE the entire GUI because this ran on the FX event
     * thread. The host-key prompt inside {@link HostKeyAcceptance} is audited
     * thread-safe (it awaits on the worker and shows on FX). The FX thread only
     * hosts a closable wait dialog and the marshalled outcome handling; the
     * connect-then-submit ordering and the transport's exactly-once close are
     * owned (and headlessly pinned) by {@link RemoteSubmitChain}.
     */
    private void runOnSSHServer(SSHJob sshJob) {
        if (sshJob == null) {
            return;
        }
        Dialog<Void> waitDialog = buildSubmitWaitDialog(sshJob);
        java.util.concurrent.ExecutorService worker =
                java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "qf-ssh-submit");
                    thread.setDaemon(true);
                    return thread;
                });
        waitDialog.show();
        worker.submit(() -> {
            RemoteSubmitChain.SubmitOutcome outcome = null;
            try {
                outcome = RemoteSubmitChain.connectAndSubmit(sshJob.getSSHServer(), sshJob,
                        server -> HostKeyAcceptance.connectInteractive(server, true));
            } catch (RuntimeException unexpected) {
                // Never silently lost; outcome stays null and marshals as an
                // honest worker-failure error below.
                AppLog.error("ssh-run", "submit worker failed: " + unexpected);
            }
            final RemoteSubmitChain.SubmitOutcome finalOutcome = outcome;
            javafx.application.Platform.runLater(() -> {
                waitDialog.close();
                worker.shutdownNow();
                if (finalOutcome == null) {
                    showError("Remote submission unavailable",
                            "the submit worker failed unexpectedly - see the "
                                    + "application log; nothing was submitted.");
                    return;
                }
                OperationResult<SshTransport> connect = finalOutcome.getConnect();
                if (!connect.isSuccess() || connect.getValue().isEmpty()) {
                    AppLog.error("ssh-run", connect.toString());
                    showError("Remote submission unavailable", connect.getMessage());
                    return;
                }
                if (!finalOutcome.isSubmitted()) {
                    OperationResult<JobRecord> submit = finalOutcome.getSubmit();
                    AppLog.error("ssh-run", String.valueOf(submit));
                    showError("Remote submission failed", submit == null
                            ? "submission was not attempted - see the application log"
                            : submit.getMessage());
                    // The chain already closed the transport exactly once.
                    return;
                }
                JobRecord record = finalOutcome.getSubmit().getValue().orElse(null);
                String id = record == null ? "?" : String.valueOf(record.getSchedulerJobId());
                AppLog.info("ssh-run", "Submitted remote job " + id);
                SshTransport transport = finalOutcome.getTransport();
                if (record != null) {
                    // Batch-138 (#96 GUI slice): offer the polling monitor; YES hands the
                    // transport to the dialog, which owns and closes it exactly once.
                    boolean transportHandedOff = offerMonitoring(sshJob, transport,
                            finalOutcome.getSubmit().getMessage(), record);
                    if (!transportHandedOff) {
                        transport.close();
                    }
                } else {
                    transport.close();
                    Alert alert = new Alert(AlertType.INFORMATION);
                    QEFXMain.initializeDialogOwner(alert);
                    alert.setTitle("Remote job");
                    alert.setHeaderText("Job submitted");
                    alert.setContentText(finalOutcome.getSubmit().getMessage());
                    alert.showAndWait();
                }
            });
        });
    }

    /**
     * The wait dialog shown while the daemon connects and submits. It carries
     * NO cancel button on purpose - a cancel that cannot interrupt the wire
     * would be a lie; the title-bar close simply dismisses the wait (and says
     * that the submission continues and will report back).
     */
    private static Dialog<Void> buildSubmitWaitDialog(SSHJob sshJob) {
        Dialog<Void> dialog = new Dialog<>();
        QEFXMain.initializeDialogOwner(dialog);
        dialog.setTitle("Remote submission");
        String where = sshJob.getSSHServer() == null ? "the remote server"
                : String.valueOf(sshJob.getSSHServer().getUser()) + "@"
                + String.valueOf(sshJob.getSSHServer().getHost());
        dialog.setHeaderText("Submitting to " + where + " on a background daemon "
                + "(qf-ssh-submit); the GUI stays responsive.");
        javafx.scene.control.Label note = new javafx.scene.control.Label(
                "You may close this window: the submission continues and reports "
                        + "back. A host-key prompt, if needed, appears on top.");
        note.setWrapText(true);
        javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(10.0,
                new javafx.scene.control.ProgressIndicator(), note);
        box.setPadding(new javafx.geometry.Insets(10.0));
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        dialog.getDialogPane().setContent(box);
        return dialog;
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
