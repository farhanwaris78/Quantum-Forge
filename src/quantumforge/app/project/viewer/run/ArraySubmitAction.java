/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import quantumforge.app.QEFXMain;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.ssh.HostKeyAcceptance;
import quantumforge.app.ssh.QEFXJobMonitorDialog;
import quantumforge.com.log.AppLog;
import quantumforge.hpc.ArraySubmitPlan;
import quantumforge.hpc.ArraySweepPlanner;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.ssh.ArrayLoopSubmitExecutor;
import quantumforge.ssh.ArraySubmitExecutor;
import quantumforge.ssh.RemotePathGuard;
import quantumforge.ssh.SSHServer;
import quantumforge.ssh.SSHServerList;
import quantumforge.ssh.SSHServerScheduler;
import quantumforge.ssh.SshTransport;

/**
 * Roadmap #93 (batch-144 GUI slice): the array-sweep submission dialogue -
 * the first GUI path that can actually drive the batch-142/143 executors.
 * It is deliberately a THIN shell over the typed, headless products that own
 * every decision (fail-closed by construction, not by promise):
 *
 * <ol>
 *   <li>server list empty -&gt; an INFORMATION alert naming the SSH
 *       configuration dialog; there is no fake flow without a configured
 *       server;</li>
 *   <li>the scheduler is resolved ONLY through
 *       {@link SSHServerScheduler#resolveAdapter} (the batch-144 single
 *       owner) - 'none'/unknown entries refuse with the chooser-direction
 *       message riding along;</li>
 *   <li>sweep arithmetic is {@link ArraySweepPlanner}'s, the review block the
 *       user confirms is the analysis channel's own
 *       {@link ArraySubmitPlan} draft (same single owner - GUI and analysis
 *       can never diverge over what a submission WOULD run);</li>
 *   <li>the executor is picked by the adapter's OWN array spec: owned array
 *       form -&gt; single-array executor (script only); deliberate refusal
 *       (pbs) -&gt; per-task loop executor (deck template + script, staged
 *       per task);</li>
 *   <li>network work runs on a dedicated daemon ({@code qf-array-submit})
 *       exactly like batch 139's submit chain: the FX thread hosts only a
 *       closable, cancel-free wait dialog and the marshalled results; the
 *       batch-139 host-key prompt is thread-safe from a worker;</li>
 *   <li>transport ownership is exactly-once: connect failure closes nothing
 *       extra (the connector fail-closed its side), submit failure closes
 *       once, a declined monitor offer closes once, an accepted offer hands
 *       the transport to the batch-138 monitor dialog which owns its close;</li>
 *   <li>the consent model is unchanged: the exit-2 guard check lives in the
 *       executors and runs first - this dialog never pre-judges consent.</li>
 * </ol>
 */
public final class ArraySubmitAction {

    private final Project project;
    private final QEFXProjectController controller;

    public ArraySubmitAction(Project project, QEFXProjectController controller) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }
        this.project = project;
        this.controller = controller;
    }

    public Project getProject() {
        return this.project;
    }

    /** Run the whole interactive flow; every early exit leaves nothing behind. */
    public void submitInteractively() {
        SSHServer[] servers = SSHServerList.getInstance().listSSHServers();
        if (servers == null || servers.length == 0) {
            showInfo("No SSH servers configured",
                    "There is no SSH server to submit an array sweep to yet. Configure "
                            + "one in the SSH configuration dialog (it now asks for the "
                            + "cluster's job scheduler) and come back - nothing was "
                            + "attempted.");
            return;
        }
        ChoiceDialog<SSHServer> picker = new ChoiceDialog<>(servers[0], servers);
        QEFXMain.initializeDialogOwner(picker);
        picker.setTitle("Array sweep submission");
        picker.setHeaderText("Choose the SSH server to stage and submit to.");
        picker.setContentText("SSH server:");
        SSHServer server = picker.showAndWait().orElse(null);
        if (server == null) {
            return;
        }
        OperationResult<SchedulerAdapter> resolved = SSHServerScheduler.resolveAdapter(server);
        if (!resolved.isSuccess() || resolved.getValue().isEmpty()) {
            AppLog.warn("array-submit", resolved.toString());
            showError("Scheduler not resolvable", resolved.getMessage());
            return;
        }
        SchedulerAdapter adapter = resolved.getValue().get();

        String keyword = askText("QE keyword to sweep (e.g. ecutwfc)", "ecutwfc");
        if (keyword == null) {
            return;
        }
        Double start = askDouble("Start value", "30.0");
        if (start == null) {
            return;
        }
        Double step = askDouble("Step between tasks (non-zero)", "10.0");
        if (step == null) {
            return;
        }
        Integer count = askInteger("Number of tasks (2-50)", "6");
        if (count == null) {
            return;
        }
        String base = askText("Job base name ([A-Za-z0-9._-], 1-32 chars)", "sweep");
        if (base == null) {
            return;
        }
        String staging = askText("Remote staging root (absolute path that already "
                + "exists and that you trust; the executor refuses anything else)",
                "/tmp/quantumforge");
        if (staging == null) {
            return;
        }
        try {
            RemotePathGuard.normalizeStagingRoot(staging);
        } catch (RuntimeException invalid) {
            showError("Staging root refused", String.valueOf(invalid.getMessage()));
            return;
        }
        OperationResult<ArraySweepPlanner.SweepPlan> planned = ArraySweepPlanner.plan(
                keyword, start.doubleValue(), step.doubleValue(), count.intValue(), base);
        if (!planned.isSuccess() || planned.getValue().isEmpty()) {
            showError("Sweep refused", planned.getMessage());
            return;
        }
        ArraySweepPlanner.SweepPlan sweep = planned.getValue().get();

        boolean singleArray = adapter.arraySubmitSpec().isSupported();
        String deckText = null;
        if (!singleArray) {
            showInfo("Per-task loop submission",
                    "The '" + adapter.name() + "' adapter deliberately owns no array "
                            + "form:\n" + adapter.arraySubmitSpec().getUnsupportedReason()
                            + "\n\nThe honest PER-TASK LOOP submits every task separately, "
                            + "so each task needs its own input deck. Next you will pick "
                            + "the QE input deck to template - it must assign '"
                            + sweep.getKeyword() + "' exactly once outside comments.");
            Path deckFile = chooseFile("QE input deck to template (assigns '"
                    + sweep.getKeyword() + "' exactly once)");
            if (deckFile == null) {
                return;
            }
            try {
                deckText = Files.readString(deckFile);
            } catch (IOException readFail) {
                showError("Deck unreadable",
                        "Could not read the deck: " + readFail.getMessage());
                return;
            }
        }
        Path script = chooseFile("Reviewed " + (singleArray ? "array" : "loop")
                + " script - its exit-2 REQUIRED-EDIT guard must be removed already");
        if (script == null) {
            return;
        }
        OperationResult<ArraySubmitPlan.SubmitPlan> drafted =
                ArraySubmitPlan.plan(sweep, adapter);
        if (!drafted.isSuccess() || drafted.getValue().isEmpty()) {
            showError("Submit draft refused", drafted.getMessage());
            return;
        }
        if (!confirmSubmission(adapter, drafted.getValue().get(), staging)) {
            return; // an explicit cancel is not an error; nothing happened
        }
        runSubmitWorker(server, adapter, sweep, staging.trim(), script, deckText,
                singleArray);
    }

    /** The review-and-confirm step: shows the SAME draft the analysis channel renders. */
    private boolean confirmSubmission(SchedulerAdapter adapter,
            ArraySubmitPlan.SubmitPlan draft, String staging) {
        ButtonType submitButton = new ButtonType("Stage and submit", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        QEFXMain.initializeDialogOwner(dialog);
        dialog.setTitle("Array sweep submission - review then confirm");
        dialog.setHeaderText("Review the guarded draft below (the single-owner block "
                + "the analysis channel renders verbatim). Confirming stages under "
                + staging.trim() + "/jobs/<unique dir>" + " and executes the shown "
                + "command(s) via the '" + adapter.name() + "' adapter.");
        TextArea review = new TextArea(draft.reviewBlock());
        review.setEditable(false);
        review.setWrapText(false);
        review.setStyle("-fx-font-family: monospace;");
        review.setPrefRowCount(24);
        review.setPrefColumnCount(96);
        dialog.getDialogPane().setContent(review);
        dialog.getDialogPane().getButtonTypes().setAll(submitButton, ButtonType.CANCEL);
        return dialog.showAndWait().filter(button -> button == submitButton).isPresent();
    }

    /** Daemon phase: connect (batch-139 connector), execute, marshal outcomes. */
    private void runSubmitWorker(SSHServer server, SchedulerAdapter adapter,
            ArraySweepPlanner.SweepPlan sweep, String staging, Path script, String deckText,
            boolean singleArray) {
        Dialog<Void> waitDialog = buildArrayWaitDialog(server);
        ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "qf-array-submit");
            thread.setDaemon(true);
            return thread;
        });
        waitDialog.show();
        worker.submit(() -> {
            OperationResult<SshTransport> connect = null;
            OperationResult<JobRecord> single = null;
            OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> loop = null;
            String executorFailure = null;
            try {
                connect = HostKeyAcceptance.connectInteractive(server, true);
            } catch (RuntimeException unexpected) {
                // The connector owns its own failed transport; nothing to close here.
                AppLog.error("array-submit", "connect failed unexpectedly: " + unexpected);
                connect = null;
            }
            if (connect != null && connect.isSuccess() && connect.getValue().isPresent()) {
                SshTransport transport = connect.getValue().get();
                try {
                    if (singleArray) {
                        single = new ArraySubmitExecutor(transport, staging)
                                .submitArray(sweep, adapter, script);
                    } else {
                        loop = new ArrayLoopSubmitExecutor(transport, staging)
                                .submitLoop(sweep, adapter, script, deckText);
                    }
                } catch (RuntimeException executorBlewUp) {
                    // An UNEXPECTED executor throw must not leak the wire: close it
                    // exactly once, here, and say so in the marshalled report.
                    transport.close();
                    executorFailure = String.valueOf(executorBlewUp.getMessage());
                    AppLog.error("array-submit", "executor failed unexpectedly (transport "
                            + "closed exactly once): " + executorBlewUp);
                }
            }
            final OperationResult<SshTransport> finalConnect = connect;
            final OperationResult<JobRecord> finalSingle = single;
            final OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> finalLoop = loop;
            final String finalExecutorFailure = executorFailure;
            Platform.runLater(() -> {
                waitDialog.close();
                worker.shutdownNow();
                handleOutcome(adapter, finalConnect, finalSingle, finalLoop,
                        finalExecutorFailure);
            });
        });
    }

    /** FX-thread outcome handling with batch-139 exactly-once transport discipline. */
    private void handleOutcome(SchedulerAdapter adapter,
            OperationResult<SshTransport> connect, OperationResult<JobRecord> single,
            OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> loop,
            String executorFailure) {
        if (connect == null) {
            showError("Array submission unavailable",
                    "the submit worker failed unexpectedly - see the application log; "
                            + "no transport was handed anywhere, so the attempt is dead.");
            return;
        }
        if (!connect.isSuccess() || connect.getValue().isEmpty()) {
            AppLog.error("array-submit", connect.toString());
            showError("Array submission unavailable", connect.getMessage());
            return;
        }
        if (executorFailure != null) {
            // The worker already closed the transport exactly once for this case.
            showError("Array submission failed unexpectedly",
                    "the executor threw unexpectedly: " + executorFailure
                            + "\n\nThe transport was closed exactly once by the worker; "
                            + "check the queue by hand before retrying (nothing is "
                            + "auto-cancelled blind).");
            return;
        }
        SshTransport transport = connect.getValue().get();
        OperationResult<?> outcome = single != null ? single : loop;
        if (outcome == null || !outcome.isSuccess()) {
            transport.close(); // exactly once; nothing else will
            AppLog.error("array-submit", String.valueOf(outcome));
            showError("Array submission failed", outcome == null
                    ? "submission was not attempted - see the application log"
                    : "[" + outcome.getCode() + "] " + outcome.getMessage());
            return;
        }
        if (single != null && single.isSuccess()) {
            JobRecord record = single.getValue().orElse(null);
            String id = record == null ? "?" : String.valueOf(record.getSchedulerJobId());
            AppLog.info("array-submit", "Submitted array job " + id);
            if (record != null) {
                boolean handedOff = offerArrayMonitoring(adapter, transport,
                        single.getMessage(), record);
                if (!handedOff) {
                    transport.close();
                }
                return;
            }
            transport.close();
            showInfo("Array submitted", single.getMessage());
            return;
        }
        transport.close();
        AppLog.info("array-submit", "Per-task loop submitted");
        showInfo("Per-task loop submitted",
                loop.getMessage() + "\n\nThe monitor dialog hosts ONE job, so it is "
                        + "not offered for a per-task loop; poll the queue for each "
                        + "listed id (the typed JOB_MONITOR_AUDIT surface documents the "
                        + "poll semantics).");
    }

    /**
     * Offer the batch-138 monitor for the submitted array job. Returns true
     * when the transport's ownership moved to the dialog (which closes it).
     */
    private boolean offerArrayMonitoring(SchedulerAdapter adapter, SshTransport transport,
            String submitMessage, JobRecord record) {
        ButtonType monitorButton = new ButtonType("Monitor array job",
                ButtonBar.ButtonData.YES);
        Alert alert = new Alert(AlertType.INFORMATION,
                submitMessage + "\nState: " + record.getState()
                        + "\n\nMonitor this array job now? The monitor polls the scheduler "
                        + "on a background daemon with stated backoff and then owns this "
                        + "connection until you close it. It reports the ARRAY job as one "
                        + "unit (per-task states live behind its id grammar).",
                monitorButton, ButtonType.CLOSE);
        QEFXMain.initializeDialogOwner(alert);
        alert.setTitle("Array sweep submission");
        alert.setHeaderText("Array job submitted");
        boolean monitor = alert.showAndWait()
                .filter(button -> button == monitorButton).isPresent();
        if (!monitor) {
            return false;
        }
        try {
            QEFXJobMonitorDialog.showAndMonitor(transport, adapter, record);
            return true;
        } catch (RuntimeException ex) {
            AppLog.error("array-submit", "monitor unavailable: " + ex.getMessage());
            showError("Monitoring unavailable", String.valueOf(ex.getMessage()));
            return false;
        }
    }

    /**
     * The closable, deliberately cancel-free wait dialog (batch-139 doctrine:
     * a cancel that cannot interrupt the wire would be a lie).
     */
    private static Dialog<Void> buildArrayWaitDialog(SSHServer server) {
        Dialog<Void> dialog = new Dialog<>();
        QEFXMain.initializeDialogOwner(dialog);
        dialog.setTitle("Array sweep submission");
        String where = server == null ? "the remote server"
                : String.valueOf(server.getUser()) + "@" + String.valueOf(server.getHost());
        dialog.setHeaderText("Staging and submitting the array sweep to " + where
                + " on a background daemon (qf-array-submit); the GUI stays responsive.");
        Label note = new Label(
                "You may close this window: the submission continues and reports back. "
                        + "A host-key prompt, if needed, appears on top.");
        note.setWrapText(true);
        HBox box = new HBox(10.0, new ProgressIndicator(), note);
        box.setPadding(new Insets(10.0));
        box.setAlignment(Pos.CENTER_LEFT);
        dialog.getDialogPane().setContent(box);
        return dialog;
    }

    private String askText(String label, String preset) {
        TextInputDialog dialog = new TextInputDialog(preset);
        QEFXMain.initializeDialogOwner(dialog);
        dialog.setTitle("Array sweep submission");
        dialog.setHeaderText(label);
        String answer = dialog.showAndWait().orElse(null);
        return answer == null || answer.isBlank() ? null : answer;
    }

    private Double askDouble(String label, String preset) {
        String answer = askText(label, preset);
        if (answer == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(answer.trim());
            if (!Double.isFinite(value)) {
                showError("Not a finite number", label + ": '" + answer + "'");
                return null;
            }
            return Double.valueOf(value);
        } catch (NumberFormatException notNumber) {
            showError("Not a number", label + ": '" + answer + "' is not a number.");
            return null;
        }
    }

    private Integer askInteger(String label, String preset) {
        String answer = askText(label, preset);
        if (answer == null) {
            return null;
        }
        try {
            return Integer.valueOf(answer.trim());
        } catch (NumberFormatException notNumber) {
            showError("Not an integer", label + ": '" + answer + "' is not an integer.");
            return null;
        }
    }

    private Path chooseFile(String description) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(description);
        Stage stage = this.controller.getMainController() == null ? null
                : this.controller.getMainController().getStage();
        java.io.File picked = chooser.showOpenDialog(stage);
        return picked == null ? null : picked.toPath();
    }

    private static void showInfo(String header, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        QEFXMain.initializeDialogOwner(alert);
        alert.setTitle("Array sweep submission");
        alert.setHeaderText(header);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }

    private static void showError(String header, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        QEFXMain.initializeDialogOwner(alert);
        alert.setTitle("Array sweep submission");
        alert.setHeaderText(header);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }
}
