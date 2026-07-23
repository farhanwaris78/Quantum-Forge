/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.thermopw;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import quantumforge.com.math.ChartGeometry;
import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEThermoPwSeriesParser;
import quantumforge.run.parser.QEThermoPwRunScanner;
import quantumforge.run.parser.QEThermoPwRunScanner.Artifact;
import quantumforge.run.parser.QEThermoPwRunScanner.ThermoScan;
import quantumforge.run.parser.QEThermoPwSeriesParser.Series;
import quantumforge.run.parser.QEThermoPwSeriesParser.SeriesKind;

/**
 * thermo_pw LIVE monitor (Roadmap: thermo_pw doc integration, live-graphical
 * slice). Watches a running (or finished) thermo_pw run directory and redraws
 * the run's own plot-data files as they grow and appear: the mur_lc E(V)
 * arc point by point, per-geometry harmonic E/F/S/Cv(T) tables as each
 * geometry completes, the mur_lc_t quasi-harmonic beta/B_T/gamma/Ce series the
 * moment the anhar sidecars are written (including the _ph route
 * counterparts, dB/dp(T), the Grüneisen-route .aux_grun/.gamma_grun and the
 * mode-Grüneisen / mode-frequency pgrun path rows - every new census kind
 * flows into the same picker and chart without a dialog change), plus the
 * restart-token task counter and the verbatim EOS fit-summary card that
 * lights up line by line the moment the run's stdout writes it.
 *
 * <p>Live doctrine: a JavaFX {@link Timeline} polls every two seconds (the
 * user can pause/resume). Polling is signature-based - only files whose
 * size/mtime changed are re-parsed through {@link QEThermoPwSeriesParser},
 * whose last-line partial-write rule means an incomplete appended row is held
 * back until thermo_pw finishes writing it rather than drawn as a glitch.
 * Axis labels are the parser's verbatim unit strings; points connect only
 * parsed rows (nothing is interpolated to fill gaps); a selected series that
 * has not been written yet renders an explicit "waiting for file" state
 * instead of a fake flat line. The timer STOPS when the dialog closes.</p>
 *
 * <p>Consent doctrine: the panel reads only, through one explicit directory
 * pick. It never starts jobs, never writes files, never touches project
 * state.</p>
 */
public final class QEFXThermoPwLiveDialog extends Dialog<Void> {

    private final Label dirLabel = new Label("(no run directory chosen)");
    private final Label liveBadge = new Label("NOT LIVE");
    private final Label progressLabel = new Label();
    private final Label eosLabel = new Label();
    private final Label statusLabel = new Label();
    private final TextArea summaryArea = new TextArea();
    private final ListView<String> seriesList = new ListView<>();
    private final ComboBox<Integer> columnCombo = new ComboBox<>();
    private final Canvas canvas = new Canvas(720, 460);
    private final Timeline timer;

    private File runDir;
    private ThermoScan scan;
    private final Map<String, long[]> signatures = new HashMap<>();
    private final Map<String, Artifact> artifactByRow = new HashMap<>();
    private Series currentSeries;
    private String currentRefusal;
    private boolean dotPhase;

    public QEFXThermoPwLiveDialog() {
        setTitle("thermo_pw live monitor (mur_lc / mur_lc_t / scf_disp series)");
        setHeaderText("Watch a thermo_pw run directory redraw itself: E(V) points as"
                + " geometries complete, harmonic E/F/S/Cv(T) per geometry, the"
                + " quasi-harmonic beta/B_T/gamma/heat series when mur_lc_t writes them"
                + " (plus dB/dp, the Grüneisen-route sidecars, _ph counterparts and the"
                + " mode-Grüneisen/frequency path rows), and the EOS fit summary as the"
                + " stdout prints it. Units are verbatim from the files' own headers;"
                + " partial write rows are held back, never drawn as fake points.");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        this.timer = new Timeline(new KeyFrame(Duration.seconds(2.0), event -> pollLive()));
        this.timer.setCycleCount(Timeline.INDEFINITE);

        Button chooseButton = new Button("Choose run directory ...");
        chooseButton.setOnAction(event -> chooseDirectory());
        Button pauseButton = new Button("Pause live");
        pauseButton.setOnAction(event -> {
            if (this.timer.getStatus() == Timeline.Status.RUNNING) {
                this.timer.pause();
                pauseButton.setText("Resume live");
                renderLiveBadge();
            } else {
                if (this.runDir != null) {
                    this.timer.play();
                    pauseButton.setText("Pause live");
                } else {
                    this.statusLabel.setText("Choose a run directory first.");
                }
                renderLiveBadge();
            }
        });

        this.summaryArea.setEditable(false);
        this.summaryArea.setPrefRowCount(6);
        this.summaryArea.setStyle("-fx-font-family: monospace;");
        this.summaryArea.setText("Choose a thermo_pw run directory (the folder holding"
                + " thermo_control). The scanner pins the layout of user-guide §2.5:"
                + " thermo_control [+ ph_control], energy_files/, therm_files/,"
                + " anhar_files/, restart/.");

        this.seriesList.setPrefWidth(300);
        this.seriesList.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldV, newV) -> selectRow(newV.intValue()));
        this.columnCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldV, newV) -> draw());
        this.columnCombo.setVisibleRowCount(12);

        HBox top = new HBox(10, chooseButton, pauseButton, this.liveBadge);
        top.setPadding(new Insets(6));
        this.eosLabel.setWrapText(true);
        this.eosLabel.setMaxWidth(300);
        this.eosLabel.setText("EOS summary: waiting for a run directory (the stdout"
                + " line-block is read verbatim, never assumed)");
        VBox left = new VBox(6, this.dirLabel, this.summaryArea,
                new Label("Series (arrive live, uninterpreted named):"),
                this.seriesList, this.progressLabel, this.eosLabel);
        left.setPadding(new Insets(6));
        VBox.setVgrow(this.seriesList, Priority.ALWAYS);

        VBox center = new VBox(6, this.columnCombo, this.canvas, this.statusLabel);
        center.setPadding(new Insets(6));
        VBox.setVgrow(this.canvas, Priority.ALWAYS);

        BorderPane content = new BorderPane(center, top, null,
                new Label("  Read-only monitor. Points connect parsed rows only - no"
                        + " interpolation, no zero-fill. Quantity picker and axes carry"
                        + " the files' verbatim units; provenance per series in the summary."),
                null);
        content.setRight(left);
        content.setPrefWidth(1080);
        getDialogPane().setContent(content);

        renderLiveBadge();
        draw();
        setOnHidden(event -> this.timer.stop());
    }

    private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the thermo_pw run directory (with thermo_control)");
        if (this.runDir != null && this.runDir.isDirectory()) {
            chooser.setInitialDirectory(this.runDir);
        } else if (getDialogPane().getScene() != null) {
            // default initial dir: none - an explicit pick is the consent
        }
        File chosen = chooser.showDialog(getDialogPane().getScene() == null
                ? null : getDialogPane().getScene().getWindow());
        if (chosen == null) {
            return;
        }
        this.runDir = chosen;
        this.dirLabel.setText("Run: " + chosen.getAbsolutePath());
        this.signatures.clear();
        rescanNow();
        this.timer.play();
        renderLiveBadge();
    }

    /** One live tick: cheap signatures, rescan census, re-parse what changed. */
    private void pollLive() {
        if (this.runDir == null) {
            return;
        }
        rescanNow();
        renderLiveBadge();
    }

    private void rescanNow() {
        this.scan = QEThermoPwRunScanner.scan(this.runDir == null
                ? null : this.runDir.toPath());
        this.summaryArea.setText(QEThermoPwRunScanner.describe(this.scan)
                + "\nlast poll: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " (every 2 s while LIVE)"
                + (this.scan.getUninterpreted().isEmpty() ? ""
                        : "\nuninterpreted: " + String.join(", ", limit(this.scan.getUninterpreted()))));
        String selectedKey = currentSelectedKey();
        List<String> rows = new ArrayList<>();
        this.artifactByRow.clear();
        int restoreIndex = -1;
        for (Artifact artifact : this.scan.getArtifacts()) {
            String key = artifact.getPath().toString();
            String row = describe(artifact);
            this.artifactByRow.put(row, artifact);
            rows.add(row);
            if (key.equals(selectedKey)) {
                restoreIndex = rows.size() - 1;
            }
        }
        this.seriesList.getItems().setAll(rows);
        if (restoreIndex >= 0) {
            this.seriesList.getSelectionModel().select(restoreIndex); // listener resyncs the chart
        } else {
            this.seriesList.getSelectionModel().clearSelection();
            refreshCurrentSeries();
        }
        this.progressLabel.setText("restart tasks: " + this.scan.getRestartCount()
                + (this.scan.getControl().getExplicitNgeoProduct() > 0
                        ? " / ngeo product " + this.scan.getControl().getExplicitNgeoProduct()
                        : " (ngeo total not explicit - no fabricated total)"));
        renderEosCard();
    }

    /**
     * The verbatim EOS fit-summary card: picks the stdout file with the most
     * EOS lines so far (ties resolve to the alphabetically first name, the
     * scanner's sort order) and renders EXACTLY the tokens the run printed,
     * with 1-based line numbers. A live partial block states n/4 - the card
     * never completes lines by guesswork.
     */
    private void renderEosCard() {
        if (this.scan == null || this.scan.getStdoutSummaries().isEmpty()) {
            this.eosLabel.setText("EOS summary: no *.out stdout at the run-directory"
                    + " top level yet");
            return;
        }
        QEThermoPwRunScanner.StdoutSummary best = null;
        for (QEThermoPwRunScanner.StdoutSummary summary : this.scan.getStdoutSummaries()) {
            if (summary.isOversized()) {
                continue;
            }
            if (best == null || summary.getEosLineCount() > best.getEosLineCount()) {
                best = summary;
            }
        }
        if (best == null) {
            this.eosLabel.setText("EOS summary: the only *.out stdout exceeds the 32 MiB"
                    + " bound (named, not parsed)");
            return;
        }
        String name = best.getPath().getFileName().toString();
        if (best.getEosLineCount() == 0) {
            this.eosLabel.setText("EOS summary: not written yet in " + name + " ("
                    + best.getUnitCellVolumeCount() + " unit-cell-volume print(s) so"
                    + " far) - waiting, nothing faked");
            return;
        }
        StringBuilder card = new StringBuilder("EOS summary (verbatim from " + name);
        if (best.getEosFirstLine() > 0) {
            card.append(", from line ").append(best.getEosFirstLine());
        }
        card.append("):");
        if (best.getLatticeConstantToken() != null) {
            card.append(" a0=").append(best.getLatticeConstantToken()).append(" a.u. [L")
                    .append(best.getLatticeConstantLine()).append(']');
        }
        if (best.getBulkModulusToken() != null) {
            card.append(" B0=").append(best.getBulkModulusToken()).append(" kbar [L")
                    .append(best.getBulkModulusLine()).append(']');
        }
        if (best.getDerivativeToken() != null) {
            card.append(" B0'=").append(best.getDerivativeToken()).append(" [L")
                    .append(best.getDerivativeLine()).append(']');
        }
        if (best.getMinEnergyToken() != null) {
            card.append(" Emin=").append(best.getMinEnergyToken()).append(" Ry [L")
                    .append(best.getMinEnergyLine()).append(']');
        }
        if (!best.isEosComplete()) {
            card.append("  (live: ").append(best.getEosLineCount())
                    .append("/4 lines so far)");
        }
        this.eosLabel.setText(card.toString());
    }

    private static List<String> limit(List<String> names) {
        if (names.size() <= 4) {
            return names;
        }
        List<String> head = new ArrayList<>(names.subList(0, 4));
        head.add("...(+" + (names.size() - 4) + " more)");
        return head;
    }

    private String describe(Artifact artifact) {
        StringBuilder row = new StringBuilder();
        row.append(artifact.getRole()).append('/')
                .append(artifact.getPath().getFileName());
        if (artifact.getKind() == null) {
            row.append("  [control]");
            return row.toString();
        }
        row.append("  [").append(artifact.getKind().getLabel());
        if (artifact.getGeometryTag() != null) {
            row.append(", geometry ").append(artifact.getGeometryTag());
        }
        if (artifact.isPhVariant()) {
            row.append(", ph variant");
        }
        if (artifact.getSuffixTag() != null) {
            row.append(", plot-tag ").append(artifact.getSuffixTag())
                    .append(" (verbatim)");
        }
        row.append(']');
        return row.toString();
    }

    private String currentSelectedKey() {
        String row = this.seriesList.getSelectionModel().getSelectedItem();
        Artifact artifact = row == null ? null : this.artifactByRow.get(row);
        return artifact == null ? null : artifact.getPath().toString();
    }

    private void selectRow(int index) {
        if (index < 0 || index >= this.seriesList.getItems().size()) {
            return;
        }
        refreshCurrentSeries();
    }

    /** Re-parses the selected artifact only when its signature changed. */
    private void refreshCurrentSeries() {
        String row = this.seriesList.getSelectionModel().getSelectedItem();
        Artifact artifact = row == null ? null : this.artifactByRow.get(row);
        this.currentSeries = null;
        this.currentRefusal = null;
        this.columnCombo.getItems().clear();
        if (artifact == null) {
            draw();
            return;
        }
        if (artifact.getKind() == null) {
            this.currentRefusal = "thermo_control is enumerated for provenance, not charted.";
            draw();
            return;
        }
        String key = artifact.getPath().toString();
        long[] signature = QEThermoPwRunScanner.signature(artifact.getPath());
        long[] previous = this.signatures.put(key, signature);
        OperationResult<Series> result = QEThermoPwRunScanner.loadSeries(artifact);
        if (result.isSuccess() && result.getValue().isPresent()) {
            this.currentSeries = result.getValue().orElseThrow();
            for (int column = 1; column < this.currentSeries.getColumns().size(); column++) {
                this.columnCombo.getItems().add(Integer.valueOf(column));
            }
            this.columnCombo.getSelectionModel().select(
                    this.columnCombo.getItems().get(this.columnCombo.getItems().size() - 1));
            // items can only be empty when a kind ever gains a one-column table;
            // the kinds pinned today all carry >= 2 columns (x plus quantities)
            String change = previous == null ? "first read"
                    : (previous[0] != signature[0] || previous[1] != signature[1]
                            ? "updated" : "unchanged");
            this.statusLabel.setText(change + " - " + this.currentSeries.getRowCount()
                    + " rows from " + this.currentSeries.getSourceName()
                    + (this.currentSeries.getPartialTailRows() > 0
                            ? "; holding back " + this.currentSeries.getPartialTailRows()
                                    + " partial write row(s)" : ""));
        } else {
            this.currentRefusal = result.getMessage();
            this.statusLabel.setText("refused: " + result.getCode());
        }
        draw();
    }

    private void renderLiveBadge() {
        boolean live = this.timer.getStatus() == Timeline.Status.RUNNING && this.runDir != null;
        this.dotPhase = !this.dotPhase;
        this.liveBadge.setText(live ? (this.dotPhase ? "\u25CF LIVE (2 s)" : "\u25CB LIVE (2 s)")
                : "NOT LIVE");
        this.liveBadge.setTextFill(live ? Color.DARKGREEN : Color.GRAY);
    }

    /** Draws the current series, or the honest waiting/refusal state. */
    private void draw() {
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
        if (this.runDir == null) {
            centered(g, "Choose a thermo_pw run directory to begin watching.");
            return;
        }
        if (this.currentRefusal != null) {
            centered(g, "Not charted: " + this.currentRefusal);
            return;
        }
        if (this.currentSeries == null) {
            centered(g, "Waiting for a series selection - files appear here as"
                    + " thermo_pw writes them. Nothing is faked meanwhile.");
            return;
        }
        Integer column = this.columnCombo.getValue();
        int yColumn = column == null ? 1 : column.intValue();
        Series series = this.currentSeries;

        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int row = 0; row < series.getRowCount(); row++) {
            xs.add(series.getX(row));
            ys.add(series.getY(row, yColumn));
        }
        double[] xe = ChartGeometry.padded(ChartGeometry.extent(xs)[0], ChartGeometry.extent(xs)[1]);
        double[] ye = ChartGeometry.padded(ChartGeometry.extent(ys)[0], ChartGeometry.extent(ys)[1]);
        double left = 88, right = 16, top = 26, bottom = 52;
        double plotW = this.canvas.getWidth() - left - right;
        double plotH = this.canvas.getHeight() - top - bottom;

        g.setStroke(Color.LIGHTGRAY);
        g.strokeRect(left, top, plotW, plotH);
        g.setFill(Color.BLACK);
        for (double tick : ChartGeometry.niceTicks(xe[0], xe[1], 6)) {
            double px = ChartGeometry.mapLinear(tick, xe[0], xe[1], left, left + plotW);
            g.setStroke(Color.GAINSBORO);
            g.strokeLine(px, top, px, top + plotH);
            g.setFill(Color.BLACK);
            g.fillText(QEThermoPwSeriesParser.formatValue(tick), px - 14, top + plotH + 16);
        }
        for (double tick : ChartGeometry.niceTicks(ye[0], ye[1], 6)) {
            double py = ChartGeometry.mapLinear(tick, ye[0], ye[1], top + plotH, top);
            g.setStroke(Color.GAINSBORO);
            g.strokeLine(left, py, left + plotW, py);
            g.setFill(Color.BLACK);
            g.fillText(QEThermoPwSeriesParser.formatValue(tick), 4, py + 4);
        }
        g.fillText(series.getXLabel(), left + plotW / 2 - 40, this.canvas.getHeight() - 16);
        g.fillText(series.getYLabel(yColumn), 4, 16);
        g.setStroke(Color.DARKBLUE);
        int n = xs.size();
        double[] px = new double[n];
        double[] py = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = ChartGeometry.mapLinear(xs.get(i), xe[0], xe[1], left, left + plotW);
            py[i] = ChartGeometry.mapLinear(ys.get(i), ye[0], ye[1], top + plotH, top);
        }
        if (n >= 2) {
            g.strokePolyline(px, py, n);
        }
        g.setFill(Color.CRIMSON);
        for (int i = 0; i < n; i++) {
            g.fillOval(px[i] - 2.5, py[i] - 2.5, 5, 5);
        }
        g.setFill(Color.DIMGRAY);
        g.fillText("units: " + series.getUnitProvenance(), left, 14);
    }

    private void centered(GraphicsContext g, String message) {
        g.setFill(Color.DIMGRAY);
        g.fillText(message, 30, this.canvas.getHeight() / 2);
    }
}
