/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.transport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import quantumforge.com.math.ChartGeometry;
import quantumforge.project.property.ProjectProperty;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer.SeriesKind;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer.TemperatureSeries;
import quantumforge.run.parser.BoltzTrap2TraceParser;
import quantumforge.run.parser.BoltzTrap2TraceParser.FileKind;
import quantumforge.run.parser.BoltzTrap2TraceParser.TransportRow;

/**
 * Roadmap #109 chart slice: the BoltzTraP2 TRANSPORT CHART panel. Plots
 * per-temperature curves (S, sigma/tau, kappa/tau, the S^2.sigma power
 * factor slice, and the .condtens Seebeck diagonals) from the batch-152
 * tested parser through the batch-162 tested {@link BoltzTrap2SeriesSlicer}
 * and {@link ChartGeometry} - the panel owns only pixels.
 *
 * <p>Honesty rules (also in the header/status): single-series view draws
 * TRUE units with header-verbatim axis labels; overlay view is explicitly
 * labeled min-max-normalized (never presented as physical units); a mu
 * absent from the file says "no curve" (never a nearest-mu fake); the
 * k-mesh / scattering / mu-grid convergence burden stays with the user -
 * this channel draws the file's numbers, it does not certify them. The
 * panel reads only through the explicit Load action; it writes nothing,
 * starts nothing, changes no project state.</p>
 */
public final class QEFXTransportChartDialog extends Dialog<Void> {

    private static final Color[] SERIES_COLORS = {
            Color.STEELBLUE, Color.DARKORANGE, Color.SEAGREEN, Color.MEDIUMPURPLE,
            Color.CRIMSON, Color.DARKCYAN, Color.GOLDENROD};

    private final Button loadButton = new Button("Load .trace/.condtens file ...");
    private final ComboBox<String> muCombo = new ComboBox<>();
    private final ComboBox<String> seriesCombo = new ComboBox<>();
    private final ComboBox<String> viewCombo = new ComboBox<>();
    private final Canvas canvas = new Canvas(680, 440);
    private final Label fileLabel = new Label("(no file loaded)");
    private final Label unitsLabel = new Label();
    private final Label statusLabel = new Label();

    private BoltzTrap2TraceParser parser;
    private List<TransportRow> rows = List.of();
    private List<Double> mus = List.of();
    private List<SeriesKind> seriesKinds = List.of();

    public QEFXTransportChartDialog() {
        setTitle("BoltzTraP2 transport charts (S / sigma / kappa vs T)");
        setHeaderText("Temperature curves at one chemical potential from BoltzTraP2"
                + " transport tables. Single-series view draws TRUE header-verbatim units;"
                + " overlay view is min-max-normalized (display aid, not physical units)."
                + " Convergence in k-mesh / scattering model / mu grid is the user's"
                + " publication burden - this panel displays, it does not certify.");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        this.viewCombo.getItems().setAll("Single series (true units)",
                "Overlay (normalized 0..1)");
        this.viewCombo.setValue("Single series (true units)");

        HBox controls = new HBox(8, this.loadButton, new Label("mu:"), this.muCombo,
                new Label("Series:"), this.seriesCombo, this.viewCombo);
        VBox top = new VBox(6, controls, this.fileLabel, this.unitsLabel);
        VBox bottom = new VBox(4, this.statusLabel);
        BorderPane content = new BorderPane(this.canvas);
        content.setTop(top);
        content.setBottom(bottom);
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(760, 640);

        this.loadButton.setOnAction(event -> loadFromFile());
        this.muCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.seriesCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.viewCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        clearCanvas();
    }

    /** Explicit file pick: parse, kind-gate, populate the mu picker. */
    private void loadFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "BoltzTraP2 transport tables", "*.trace", "*.condtens", "*"));
        Window owner = getDialogPane().getScene() == null ? null
                : getDialogPane().getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        BoltzTrap2TraceParser parsed = new BoltzTrap2TraceParser(new ProjectProperty());
        try {
            parsed.parse(file);
        } catch (IOException problem) {
            showProblem("Could not read the transport table", problem.toString());
            return;
        }
        if (parsed.getFileKind() == FileKind.TENSOR_OTHER) {
            showProblem("Not transport data",
                    file.getName() + " is a 30-column tensor file whose header names"
                            + " RH[...] (a Hall tensor file), not sigma: no S(T)/kappa(T)"
                            + " curves exist in it. Provenance: " + parsed.getFamilyNote());
            return;
        }
        if (parsed.getRows().size() < 2) {
            showProblem("Too few clean rows",
                    "Fewer than two clean transport rows were parsed from " + file.getName()
                            + " (clean: " + parsed.getRows().size() + ", skipped: "
                            + parsed.getSkippedRowCount() + "); a single point is not a"
                            + " curve. Provenance: " + parsed.getFamilyNote());
            return;
        }
        this.parser = parsed;
        this.rows = parsed.getRows();
        this.mus = BoltzTrap2SeriesSlicer.distinctMuRy(this.rows);
        List<String> muLabels = new ArrayList<>();
        for (double mu : this.mus) {
            muLabels.add(String.format(Locale.ROOT, "%+.6f Ry (%+.5f eV)",
                    mu, mu * BoltzTrap2TraceParser.RY_TO_EV));
        }
        this.muCombo.getItems().setAll(muLabels);
        if (!muLabels.isEmpty()) {
            this.muCombo.getSelectionModel().select(0);
        }
        List<String> seriesLabels = new ArrayList<>();
        List<SeriesKind> kinds = new ArrayList<>();
        for (SeriesKind kind : SeriesKind.values()) {
            boolean diagonal = kind == SeriesKind.SEEBECK_XX || kind == SeriesKind.SEEBECK_YY
                    || kind == SeriesKind.SEEBECK_ZZ;
            if (diagonal && parsed.getFileKind() != FileKind.CONDTENS) {
                continue; // diagonals exist only on the tensor grammar
            }
            kinds.add(kind);
            seriesLabels.add(BoltzTrap2SeriesSlicer.axisLabel(kind,
                    parsed.getSigmaUnits(), parsed.getKappaUnits()));
        }
        this.seriesKinds = kinds;
        this.seriesCombo.getItems().setAll(seriesLabels);
        this.seriesCombo.getSelectionModel().select(0);
        this.fileLabel.setText(file.getName() + "   [" + parsed.getFileKind()
                + ", " + this.rows.size() + " clean rows, " + parsed.getSkippedRowCount()
                + " skipped, " + this.mus.size() + " chemical potential(s)]"
                + (parsed.getScatteringModel().isEmpty() ? ""
                        : "   scattering: " + parsed.getScatteringModel()));
        this.unitsLabel.setText("sigma units verbatim: "
                + (parsed.getSigmaUnits().isEmpty() ? "(no header token)"
                        : parsed.getSigmaUnits())
                + "  |  kappa units verbatim: "
                + (parsed.getKappaUnits().isEmpty() ? "(no header token)"
                        : parsed.getKappaUnits()));
        render();
    }

    private void showProblem(String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("BoltzTraP2 transport charts");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    private void clearCanvas() {
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
    }

    /** Render entry: empty slices state the no-curve honesty line. */
    private void render() {
        clearCanvas();
        if (this.parser == null || this.muCombo.getSelectionModel().getSelectedIndex() < 0) {
            this.statusLabel.setText("Load a transport table to draw curves.");
            return;
        }
        int muIndex = this.muCombo.getSelectionModel().getSelectedIndex();
        double mu = this.mus.get(muIndex);
        boolean overlay = this.viewCombo.getValue().startsWith("Overlay");
        List<SeriesKind> kinds = overlay ? this.seriesKinds
                : List.of(this.seriesKinds.get(Math.max(0,
                        this.seriesCombo.getSelectionModel().getSelectedIndex())));
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        double left = 70;
        double top = 30;
        double right = this.canvas.getWidth() - 30;
        double bottom = this.canvas.getHeight() - 60;
        boolean drew = false;
        int colorIndex = 0;
        StringBuilder legend = new StringBuilder();
        for (SeriesKind kind : kinds) {
            TemperatureSeries series = BoltzTrap2SeriesSlicer.slice(this.rows, mu, kind);
            Color color = SERIES_COLORS[colorIndex % SERIES_COLORS.length];
            colorIndex++;
            if (series.size() == 0) {
                continue;
            }
            drew = true;
            double[] xExtent = ChartGeometry.padded(ChartGeometry
                    .extent(series.getTemperatures())[0],
                    ChartGeometry.extent(series.getTemperatures())[1]);
            double[] yExtent = overlay ? new double[] {0.0, 1.0}
                    : ChartGeometry.padded(ChartGeometry.extent(series.getValues())[0],
                            ChartGeometry.extent(series.getValues())[1]);
            if (colorIndex == 1) {
                drawAxes(g, left, top, right, bottom, xExtent, yExtent, overlay, kind);
            }
            List<Double> temperatures = series.getTemperatures();
            List<Double> values = series.getValues();
            double yRefMin = overlay ? ChartGeometry.extent(values)[0] : yExtent[0];
            double yRefMax = overlay ? ChartGeometry.extent(values)[1] : yExtent[1];
            if (overlay) {
                double[] padded = ChartGeometry.padded(yRefMin, yRefMax);
                yRefMin = padded[0];
                yRefMax = padded[1];
            }
            g.setStroke(color);
            g.setLineWidth(1.8);
            double previousX = 0;
            double previousY = 0;
            for (int i = 0; i < series.size(); i++) {
                double sx = ChartGeometry.mapLinear(temperatures.get(i),
                        xExtent[0], xExtent[1], left, right);
                double sy = overlay
                        ? ChartGeometry.mapLinear(values.get(i), yRefMin, yRefMax,
                                bottom, top)
                        : ChartGeometry.mapLinear(values.get(i), yExtent[0], yExtent[1],
                                bottom, top);
                if (i > 0) {
                    g.strokeLine(previousX, previousY, sx, sy);
                }
                g.setFill(color);
                g.fillOval(sx - 3, sy - 3, 6, 6);
                previousX = sx;
                previousY = sy;
            }
            if (overlay) {
                legend.append(colorIndex == 1 ? "" : "   |   ");
                legend.append(kind);
            }
        }
        if (!drew) {
            this.statusLabel.setText(String.format(Locale.ROOT,
                    "No curve at mu = %+.6f Ry - that chemical potential is not in this"
                            + " file (an empty slice is shown honestly, never a"
                            + " nearest-mu substitute).",
                    mu));
            return;
        }
        this.statusLabel.setText(overlay
                ? "Overlay (each series min-max-normalized to 0..1 - display aid,"
                        + " NOT physical units): "
                        + legend + "   |   colors: " + colorLegend()
                : String.format(Locale.ROOT,
                        "mu = %+.6f Ry (%+.5f eV)   |   %s   |   points drawn: real"
                                + " clean rows only (skipped rows: %d).",
                        mu, mu * BoltzTrap2TraceParser.RY_TO_EV,
                        this.seriesCombo.getValue(), this.parser.getSkippedRowCount()));
    }

    private String colorLegend() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < this.seriesKinds.size() && i < SERIES_COLORS.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(SERIES_COLORS[i]);
        }
        return out.toString();
    }

    /** Axes box + nice ticks with the series' own units on the single view. */
    private void drawAxes(GraphicsContext g, double left, double top, double right,
            double bottom, double[] xExtent, double[] yExtent, boolean overlay,
            SeriesKind kind) {
        g.setStroke(Color.BLACK);
        g.setLineWidth(1.0);
        g.strokeLine(left, bottom, right, bottom);
        g.strokeLine(left, top, left, bottom);
        double[] xTicks = ChartGeometry.niceTicks(xExtent[0], xExtent[1], 6);
        double[] yTicks = overlay ? new double[] {0.0, 0.5, 1.0}
                : ChartGeometry.niceTicks(yExtent[0], yExtent[1], 5);
        g.setFill(Color.BLACK);
        for (double tick : xTicks) {
            double sx = ChartGeometry.mapLinear(tick, xExtent[0], xExtent[1], left, right);
            g.strokeLine(sx, bottom, sx, bottom + 5);
            g.fillText(String.format(Locale.ROOT, "%.0f", tick), sx - 12, bottom + 18);
        }
        for (double tick : yTicks) {
            double sy = overlay
                    ? ChartGeometry.mapLinear(tick, 0.0, 1.0, bottom, top)
                    : ChartGeometry.mapLinear(tick, yExtent[0], yExtent[1], bottom, top);
            g.strokeLine(left, sy, left - 5, sy);
            g.fillText(String.format(Locale.ROOT, "%.4g", tick), 6, sy + 4);
        }
        g.fillText(overlay ? "T [K]  (y: normalized, see status)" : "T [K]  (y: "
                + this.seriesCombo.getValue() + ")",
                left, this.canvas.getHeight() - 8);
    }
}
