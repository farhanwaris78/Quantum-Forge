/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.ssh;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import quantumforge.com.log.AppLog;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.RemoteJobMonitor;
import quantumforge.hpc.RemoteJobMonitor.StatusUpdate;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.operation.OperationResult;
import quantumforge.ssh.SshTransport;

/**
 * Roadmap #96 GUI slice: FX host for the headless {@link RemoteJobMonitor}.
 *
 * <p>THREAD DISCIPLINE - stated, and never violated:</p>
 * <ul>
 *   <li>monitor callbacks arrive on the monitor's own daemon scheduler
 *       thread; the ONLY bridge from that thread into this dialog is
 *       {@link Platform#runLater(Runnable)} - no control is touched
 *       directly from a poll callback;</li>
 *   <li>manual polls (the "Poll now" button) run on a dedicated daemon
 *       worker, never on the FX thread, and marshal their reporting back
 *       the same way - the scheduler command's network latency can freeze
 *       nothing;</li>
 *   <li>liveness honesty: the automatic loop surfaces only VERDICTS (the
 *       batch-127 semantics: QUERY_UNREADABLE and transport-shaped failures
 *       warn to the app log and are never statuses); the dialog says so
 *       verbatim, and the "Poll now" button is the explicit, typed way to
 *       see those failure codes on screen;</li>
 *   <li>ownership: this dialog OWNS the transport handed to it and closes
 *       it exactly once, after the dialog hides - the caller must never
 *       close the same transport (the RunAction wiring documents this);</li>
 *   <li>fail-closed guard: without a connected transport the dialog refuses
 *       to open and logs why - an offline monitor would be a fake one;</li>
 *   <li>display truth lives in {@link MonitorLogModel}: receipt-time
 *       stamps, empty-raw quoting, capped ring with a named eviction count.</li>
 * </ul>
 */
public final class QEFXJobMonitorDialog {

    private QEFXJobMonitorDialog() {
    }

    /**
     * Open the monitor dialog modally and block until it closes; the
     * transport is closed exactly once before this method returns.
     */
    public static void showAndMonitor(SshTransport transport, SchedulerAdapter adapter,
            JobRecord record) {
        Objects.requireNonNull(adapter, "adapter - monitor commands need the typed owner");
        Objects.requireNonNull(record, "record");
        if (transport == null || !transport.isConnected()) {
            // Fail closed; an offline monitor would only be a fake one.
            AppLog.warn("monitor-gui", "job monitor not opened: no connected transport "
                    + "- nothing was polled.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Remote job monitor");
        String jobLabel = record.getSchedulerJobId() == null
                ? "<no scheduler id yet>" : record.getSchedulerJobId();
        dialog.setHeaderText("Job " + record.getJobId() + "  (scheduler id: " + jobLabel
                + ", scheduler: " + record.getScheduler() + ", site: "
                + (record.getSiteId() == null || record.getSiteId().isEmpty()
                        ? "<unset>" : record.getSiteId()) + ")");

        Label stateLabel = new Label("State: " + record.getState());
        Label rawLabel = new Label("The automatic loop surfaces only VERDICTS; "
                + "query-unreadable and transport failures warn to the app log and are "
                + "never shown as statuses. 'Poll now' reports those codes explicitly.");
        rawLabel.setWrapText(true);
        Label terminalLabel = new Label("");
        Label droppedLabel = new Label("");

        MonitorLogModel model = new MonitorLogModel();
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(16);
        HBox.setHgrow(logArea, Priority.ALWAYS);

        Button startButton = new Button("Start polling");
        Button pollNowButton = new Button("Poll now");
        HBox buttons = new HBox(8.0, startButton, pollNowButton);
        buttons.setPadding(new Insets(4.0, 0.0, 0.0, 0.0));

        BorderPane content = new BorderPane();
        javafx.scene.layout.VBox header = new javafx.scene.layout.VBox(4.0,
                stateLabel, rawLabel, terminalLabel, droppedLabel);
        content.setTop(header);
        content.setCenter(logArea);
        content.setBottom(buttons);
        content.setPadding(new Insets(8.0));
        content.setPrefSize(640.0, 460.0);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        RemoteJobMonitor monitor = new RemoteJobMonitor(transport, adapter, record);
        ExecutorService pollWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "qf-monitor-poll-now");
            thread.setDaemon(true);
            return thread;
        });
        final java.util.concurrent.atomic.AtomicBoolean terminalSeen =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        monitor.addListener(update -> {
            // Arrives on the monitor daemon thread - the ONLY bridge is runLater.
            final StatusUpdate snapshot = update;
            Platform.runLater(() -> {
                model.appendUpdate(Instant.now(), snapshot);
                refreshLabels(model, logArea, stateLabel, droppedLabel, record);
                if (snapshot.isTerminal()) {
                    terminalSeen.set(true);
                    terminalLabel.setText("Terminal state reached (" + snapshot
                            .getRecord().getState() + ") - the poll loop stopped itself.");
                    startButton.setDisable(true);
                    pollNowButton.setDisable(true);
                }
            });
        });

        startButton.setOnAction(event -> {
            // start() only schedules; the polls run on the monitor's own thread.
            monitor.start();
            startButton.setDisable(true);
        });

        pollNowButton.setOnAction(event -> {
            pollNowButton.setDisable(true);
            pollWorker.submit(() -> {
                OperationResult<StatusUpdate> polled = monitor.pollOnce();
                Platform.runLater(() -> {
                    pollNowButton.setDisable(terminalSeen.get());
                    if (!polled.isSuccess()) {
                        // Failure codes are typed verdicts about the QUERY, not the job.
                        model.appendNote(Instant.now(), "poll now: [" + polled.getCode()
                                + "] " + polled.getMessage());
                        refreshLabels(model, logArea, stateLabel, droppedLabel, record);
                    }
                    // Successful polls surface through the listener above; appending
                    // them here too would render every verdict twice.
                });
            });
        });

        model.appendNote(Instant.now(), "monitor opened for job id " + jobLabel
                + "; nothing polled yet - press Start or Poll now.");
        refreshLabels(model, logArea, stateLabel, droppedLabel, record);
        try {
            dialog.showAndWait();
        } finally {
            // Exactly-once ownership: this dialog closes what it was handed.
            monitor.close();
            pollWorker.shutdownNow();
            transport.close();
        }
    }

    private static void refreshLabels(MonitorLogModel model, TextArea logArea,
            Label stateLabel, Label droppedLabel, JobRecord record) {
        logArea.setText(model.text());
        logArea.positionCaret(model.text().length());
        stateLabel.setText("State: " + record.getState());
        String notice = model.droppedNotice();
        droppedLabel.setText(notice == null ? "" : notice);
    }
}
