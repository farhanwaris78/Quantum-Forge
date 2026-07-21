/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.elate;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEElateAnalyzer;
import quantumforge.run.parser.QEElateAnalyzer.AverageRow;
import quantumforge.run.parser.QEElateAnalyzer.Band;
import quantumforge.run.parser.QEElateAnalyzer.ElateReport;
import quantumforge.run.parser.QEElateAnalyzer.ElateUnit;
import quantumforge.run.parser.QEElateAnalyzer.Extremum;
import quantumforge.run.parser.QEElateAnalyzer.Plane;
import quantumforge.run.parser.QEThermoPwElasticParser;
import quantumforge.run.parser.QEThermoPwElasticParser.ElasticConstantsFile;
import quantumforge.run.parser.QEThermoPwElasticParser.ElasticStdout;
import quantumforge.run.parser.QEThermoPwElasticParser.StdoutScheme;

/**
 * ELATE elastic-tensor analysis (Roadmap: ELATE integration chunk). One
 * dialog feeds a 6x6 elastic-stiffness tensor into {@link QEElateAnalyzer}
 * through one of three explicit routes:
 *
 * <ol>
 *   <li>PASTE the 6x6 matrix (full or triangular, as upstream ELATE accepts)
 *       plus an explicitly DECLARED unit (GPa, or kbar with the stated
 *       0.1 GPa-per-kbar conversion - thermo_pw prints '10 kbar = 1 GPa' as
 *       its own conversion aid);</li>
 *   <li>pick a thermo_pw {@code output_el_cons.dat[.gN]} constants file
 *       (12-line alternating 4+2 grammar; unit kbar cross-pinned from the
 *       sibling run stdout - stated, never guessed);</li>
 *   <li>pick a thermo_pw run stdout ({@code *.out}) holding the 'Elastic
 *       constants C_ij (kbar)' block; the LAST block feeds the analysis and
 *       the printed Voigt/Reuss/Hill rows, sound velocities and Debye
 *       temperatures are shown verbatim with line numbers.</li>
 * </ol>
 *
 * <p>The report panel shows the mechanical-stability verdict with the six
 * eigenvalues, the Voigt/Reuss/Hill averages, the spatial-variation extrema
 * (Young's modulus, linear compressibility, shear modulus, Poisson ratio)
 * with their directions, and the anisotropy strings. The chart draws the
 * polar variations on the xy / xz / yz planes (single Young/LC curves,
 * shear/Poisson as min-max bands), and the direction probe evaluates any
 * (theta, phi) direction. When the tensor is not positive definite the
 * extrema are NOT computed - ELATE's own 'No further analysis will be
 * performed.' gate, mirrored deliberately.</p>
 *
 * <p>Provenance/honesty: the numerics are a clean-room Java mirror of the
 * published ELATE workflow (github.com/coudertlab/elate, commit
 * 0627e636a7c97e8678f71aea44d0851455650d3a); no upstream code is vendored.
 * thermo_pw's own Hill print is the MEAN of its two scheme rows while ELATE
 * computes the Hill row as a closure of K_H and G_H - on the upstream Si
 * reference both differ by ~0.012% (159.958 vs 159.977 GPa), a CONVENTION
 * difference that is stated wherever both appear. The viewer reads only; it
 * never writes files, never starts jobs, never touches project state.</p>
 */
public final class QEFXElateDialog extends Dialog<Void> {

    private static final String[] MODES = {
            "Young's modulus E (GPa)",
            "Linear compressibility (TPa^-1)",
            "Shear modulus band (GPa)",
            "Poisson ratio band"};

    private final ComboBox<String> routeCombo = new ComboBox<>();
    private final ComboBox<ElateUnit> unitCombo = new ComboBox<>();
    private final TextArea pasteArea = new TextArea();
    private final Button fileButton = new Button("Choose file ...");
    private final Label fileLabel = new Label("(no file chosen)");
    private final Button analyzeButton = new Button("Analyze tensor");
    private final Label statusLabel = new Label();

    private final Label stabilityLabel = new Label("(no tensor analysed yet)");
    private final Label eigenLabel = new Label();
    private final TextArea averagesArea = new TextArea();
    private final TextArea extremaArea = new TextArea();
    private final Label anisotropyLabel = new Label();
    private final TextArea stdoutArea = new TextArea();

    private final ComboBox<String> modeCombo = new ComboBox<>();
    private final ComboBox<Plane> planeCombo = new ComboBox<>();
    private final Canvas canvas = new Canvas(460, 430);
    private final Label chartCaption = new Label();

    private final TextField thetaField = new TextField("45");
    private final TextField phiField = new TextField("45");
    private final Label probeLabel = new Label("(probe a direction after analysis)");

    private final TextArea honestyArea = new TextArea();

    private File chosenFile;
    private ElateReport report;

    public QEFXElateDialog() {
        setTitle("ELATE elastic tensor analysis");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        this.routeCombo.getItems().addAll(
                "Paste 6x6 stiffness matrix (ELATE grammar)",
                "thermo_pw constants file (output_el_cons.dat[.gN])",
                "thermo_pw run stdout (*.out with the C_ij block)");
        this.routeCombo.getSelectionModel().select(0);
        this.routeCombo.setMaxWidth(Double.MAX_VALUE);

        this.unitCombo.getItems().addAll(ElateUnit.values());
        this.unitCombo.getSelectionModel().select(ElateUnit.GPA);

        this.pasteArea.setPromptText("Six rows: full 6x6, or triangular (6,5,4,3,2,1 /"
                + " 1..6 tokens per row), e.g.\n1588.860492 603.0012079 603.0012079 0 0 0\n"
                + "603.0012079 1588.860492 603.0012079 0 0 0\n...");
        this.pasteArea.setPrefRowCount(8);

        Label unitNote = new Label("Declared unit (never inferred); kbar converts by"
                + " exactly 0.1 - the run's own printed conversion aid.");

        Button analyze = this.analyzeButton;
        analyze.setMaxWidth(Double.MAX_VALUE);
        analyze.setDefaultButton(true);

        this.averagesArea.setEditable(false);
        this.averagesArea.setPrefRowCount(5);
        this.extremaArea.setEditable(false);
        this.extremaArea.setPrefRowCount(9);
        this.stdoutArea.setEditable(false);
        this.stdoutArea.setPrefRowCount(7);
        this.honestyArea.setEditable(false);
        this.honestyArea.setPrefRowCount(8);
        this.honestyArea.setWrapText(true);
        this.honestyArea.setText(
                "Honesty / provenance:\n"
                + "- Numerics mirror the published ELATE workflow (coudertlab/elate,\n"
                + "  commit 0627e636a7c97e8678f71aea44d0851455650d3a) re-implemented in\n"
                + "  Java; no upstream code is vendored here.\n"
                + "- Units: GPa natively; a kbar input converts by the DECLARED 0.1 factor\n"
                + "  only (thermo_pw's own '10 kbar = 1 GPa' print). The thermo_pw\n"
                + "  constants file carries NO unit text - kbar is cross-pinned from the\n"
                + "  sibling run stdout of upstream example13 (commit b73edd6d).\n"
                + "- Hill convention: thermo_pw prints Hill as the mean of its two scheme\n"
                + "  rows; ELATE closes Hill from K_H, G_H. On upstream Si: 159.958 vs\n"
                + "  159.977 GPa (~0.012%) - a convention difference, both shown honestly.\n"
                + "- Unstable tensors (non-positive-definite) stop after averages and\n"
                + "  eigenvalues - ELATE's own gate, no extrema guessed.\n"
                + "- This viewer reads only: it writes nothing and starts no jobs.");

        this.modeCombo.getItems().addAll(MODES);
        this.modeCombo.getSelectionModel().select(0);
        this.planeCombo.getItems().addAll(Plane.values());
        this.planeCombo.getSelectionModel().select(Plane.XY);

        VBox inputCard = new VBox(6,
                new Label("Input route (one explicit choice):"), this.routeCombo,
                this.pasteArea,
                new HBox(8, this.fileButton, this.fileLabel),
                new HBox(8, new Label("Unit of the pasted/file numbers:"), this.unitCombo),
                unitNote, analyze, this.statusLabel);
        inputCard.setPadding(new Insets(8));
        this.fileLabel.setMaxWidth(420);

        VBox reportCard = new VBox(6,
                this.stabilityLabel, this.eigenLabel,
                new Label("Voigt / Reuss / Hill averages (GPa; Poisson dimensionless):"),
                this.averagesArea,
                new Label("Spatial-variation extrema (directions in degrees):"),
                this.extremaArea, this.anisotropyLabel,
                new Label("verbatim thermo_pw stdout prints (when that route is used):"),
                this.stdoutArea);
        reportCard.setPadding(new Insets(8));

        this.thetaField.setPrefColumnCount(5);
        this.phiField.setPrefColumnCount(5);
        Button probeButton = new Button("Probe direction");
        HBox probeRow = new HBox(8, new Label("theta (deg, from z):"), this.thetaField,
                new Label("phi (deg, from x in xy):"), this.phiField, probeButton);

        VBox chartCard = new VBox(6,
                new HBox(8, new Label("Quantity:"), this.modeCombo,
                        new Label("Plane:"), this.planeCombo),
                this.canvas, this.chartCaption, probeRow, this.probeLabel);
        chartCard.setPadding(new Insets(8));

        VBox root = new VBox(10, inputCard, reportCard, chartCard, this.honestyArea);
        root.setPadding(new Insets(10));
        VBox.setVgrow(this.stdoutArea, Priority.ALWAYS);
        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        getDialogPane().setContent(scroller);
        getDialogPane().setPrefSize(880, 980);

        this.routeCombo.getSelectionModel().selectedIndexProperty()
                .addListener((obs, oldV, newV) -> updateRouteVisibility());
        updateRouteVisibility();
        this.fileButton.setOnAction(e -> chooseFile());
        analyze.setOnAction(e -> runAnalysis());
        this.modeCombo.getSelectionModel().selectedIndexProperty()
                .addListener((obs, o, n) -> drawChart());
        this.planeCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> drawChart());
        probeButton.setOnAction(e -> probeDirection());
    }

    private void updateRouteVisibility() {
        int route = this.routeCombo.getSelectionModel().getSelectedIndex();
        boolean paste = route == 0;
        this.pasteArea.setVisible(paste);
        this.pasteArea.setManaged(paste);
        this.fileButton.setVisible(!paste);
        this.fileButton.setManaged(!paste);
        this.fileLabel.setVisible(!paste);
        this.fileLabel.setManaged(!paste);
        this.unitCombo.setDisable(route != 0);
        if (route == 0) {
            this.fileButton.setText("Choose file ...");
        } else if (route == 1) {
            this.fileButton.setText("Choose output_el_cons.dat[.gN] ...");
        } else {
            this.fileButton.setText("Choose thermo_pw stdout (*.out) ...");
        }
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        int route = this.routeCombo.getSelectionModel().getSelectedIndex();
        if (route == 1) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "thermo_pw elastic constants", "output_el_cons.dat*"));
            chooser.setTitle("Pick the run's output_el_cons.dat[.gN]");
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "thermo_pw stdout", "*.out", "*.log", "*"));
            chooser.setTitle("Pick the thermo_pw run stdout");
        }
        File picked = chooser.showOpenDialog(getOwner());
        if (picked != null) {
            this.chosenFile = picked;
            this.fileLabel.setText(picked.getAbsolutePath());
        }
    }

    private void runAnalysis() {
        int route = this.routeCombo.getSelectionModel().getSelectedIndex();
        this.stdoutArea.setText("");
        OperationResult<ElateReport> result;
        if (route == 0) {
            result = QEElateAnalyzer.analyze(this.pasteArea.getText(),
                    this.unitCombo.getSelectionModel().getSelectedItem());
        } else if (route == 1) {
            if (this.chosenFile == null) {
                this.statusLabel.setText("Pick an output_el_cons.dat[.gN] file first.");
                return;
            }
            OperationResult<ElasticConstantsFile> parsed =
                    QEThermoPwElasticParser.parseElasticConstantsFile(
                            this.chosenFile.toPath());
            if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
                refuse(parsed.getCode(), parsed.getMessage());
                return;
            }
            ElasticConstantsFile file = parsed.getValue().orElseThrow();
            result = QEElateAnalyzer.analyze(file.toElateMatrixText(), ElateUnit.KBAR);
            if (result.isSuccess()) {
                this.stdoutArea.setText(
                        "constants file: " + file.getSourceName()
                                + (file.getGeometryTag() != null
                                        ? " (geometry " + file.getGeometryTag() + ")" : "")
                                + "\nunit provenance: " + file.getUnitProvenance()
                                + "\ncompliance block: "
                                + (file.hasCompliance() ? "present (1/Mbar)"
                                        : "not written (yet)") + "\n");
            }
        } else {
            if (this.chosenFile == null) {
                this.statusLabel.setText("Pick a thermo_pw stdout (*.out) first.");
                return;
            }
            OperationResult<ElasticStdout> parsed =
                    QEThermoPwElasticParser.parseElasticStdout(this.chosenFile.toPath());
            if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
                refuse(parsed.getCode(), parsed.getMessage());
                return;
            }
            ElasticStdout stdout = parsed.getValue().orElseThrow();
            result = QEElateAnalyzer.analyze(stdout.toElateMatrixText(), ElateUnit.KBAR);
            fillStdout(stdout);
        }
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            refuse(result.getCode(), result.getMessage());
            return;
        }
        this.report = result.getValue().orElseThrow();
        this.statusLabel.setText("[" + result.getCode() + "] " + result.getMessage());
        renderReport();
        drawChart();
    }

    private void refuse(String code, String message) {
        this.report = null;
        this.statusLabel.setText("[" + code + "] " + message);
        this.stabilityLabel.setText("Refused - " + code);
        this.eigenLabel.setText("");
        this.averagesArea.setText("");
        this.extremaArea.setText("");
        this.anisotropyLabel.setText("");
        this.chartCaption.setText("");
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
        gc.setFill(Color.DARKRED);
        gc.fillText("No chart - " + code, 20, 30);
    }

    private void fillStdout(ElasticStdout stdout) {
        StringBuilder text = new StringBuilder();
        text.append(stdout.getSourceName()).append(": LAST 'Elastic constants C_ij (kbar)'"
                + " block at line ").append(stdout.getStiffnessFirstLine())
                .append(" (stdout digits are the run's ROUNDED prints - pinned as-is).\n");
        for (StdoutScheme scheme : stdout.getSchemes()) {
            text.append(String.format(Locale.ROOT,
                    "line %d  %s:  B=%s kbar  E=%s kbar  G=%s kbar  n=%s%n",
                    scheme.getFirstLine(), scheme.getScheme(), scheme.getBulkKbar(),
                    scheme.getYoungKbar(), scheme.getShearKbar(), scheme.getPoisson()));
        }
        if (!stdout.getSoundTokens().isEmpty()) {
            text.append("sound velocities (m/s, verbatim): ")
                    .append(String.join(", ", stdout.getSoundTokens())).append('\n');
        }
        if (!stdout.getDebyeTokens().isEmpty()) {
            text.append("Debye prints (K / m/s tokens, verbatim): ")
                    .append(String.join(", ", stdout.getDebyeTokens())).append('\n');
        }
        text.append("Note: thermo_pw's Hill row is the MEAN of its scheme rows; ELATE's"
                + " Hill (above) closes from K_H, G_H - a ~0.01% convention difference,"
                + " stated, not reconciled.\n");
        this.stdoutArea.setText(text.toString());
    }

    private void renderReport() {
        ElateReport r = this.report;
        double[] eigen = r.getEigenvaluesGpa();
        StringBuilder eigenText = new StringBuilder("eigenvalues (GPa):");
        for (double value : eigen) {
            eigenText.append(String.format(Locale.ROOT, "  %.6g", value));
        }
        this.eigenLabel.setText(eigenText.toString());
        if (r.isMechanicallyStable()) {
            this.stabilityLabel.setText("MECHANICALLY STABLE (smallest eigenvalue "
                    + String.format(Locale.ROOT, "%.6g", eigen[0]) + " GPa > 0)");
            this.stabilityLabel.setStyle("-fx-text-fill: #1a6b1a; -fx-font-weight: bold;");
        } else {
            this.stabilityLabel.setText("MECHANICALLY UNSTABLE (smallest eigenvalue "
                    + String.format(Locale.ROOT, "%.6g", eigen[0])
                    + " GPa <= 0) - ELATE's gate: no spatial extrema computed.");
            this.stabilityLabel.setStyle("-fx-text-fill: #a01010; -fx-font-weight: bold;");
        }
        StringBuilder averages = new StringBuilder(String.format(Locale.ROOT,
                "%-18s %12s %12s %12s %10s%n", "scheme", "K (bulk)", "E (Young)",
                "G (shear)", "nu"));
        for (AverageRow row : r.getAverages()) {
            averages.append(String.format(Locale.ROOT, "%-18s %12.5f %12.5f %12.5f %10.5f%n",
                    row.getScheme(), row.getBulkGpa(), row.getYoungGpa(),
                    row.getShearGpa(), row.getPoisson()));
        }
        averages.append("Hill row = ELATE closure 1/(1/3G+1/9K) - see honesty note.\n");
        this.averagesArea.setText(averages.toString());

        StringBuilder extrema = new StringBuilder();
        if (r.getMinYoung() == null) {
            extrema.append("(not computed - the stability gate fired)\n");
        } else {
            appendExtremum(extrema, r.getMinYoung(), "GPa");
            appendExtremum(extrema, r.getMaxYoung(), "GPa");
            appendExtremum(extrema, r.getMinLc(), "TPa^-1");
            appendExtremum(extrema, r.getMaxLc(), "TPa^-1");
            appendExtremum(extrema, r.getMinShear(), "GPa");
            appendExtremum(extrema, r.getMaxShear(), "GPa");
            appendExtremum(extrema, r.getMinPoisson(), "");
            appendExtremum(extrema, r.getMaxPoisson(), "");
        }
        this.extremaArea.setText(extrema.toString());
        this.anisotropyLabel.setText(r.getMinYoung() == null ? ""
                : "anisotropy: E " + r.getYoungAnisotropy() + " | LC " + r.getLcAnisotropy()
                        + " | G " + r.getShearAnisotropy() + " | nu "
                        + r.getPoissonAnisotropy());
    }

    private static void appendExtremum(StringBuilder text, Extremum extremum,
            String unit) {
        double[] axis = extremum.getFirstAxis();
        text.append(String.format(Locale.ROOT,
                "%s %s = %.6g %s  at (theta=%.2f, phi=%.2f)%s  axis=(%.3f, %.3f, %.3f)%n",
                extremum.getQuantity(), extremum.isMaximum() ? "max" : "min",
                extremum.getValue(), unit,
                Math.toDegrees(extremum.getTheta()), Math.toDegrees(extremum.getPhi()),
                !Double.isNaN(extremum.getChi())
                        ? String.format(Locale.ROOT, ", chi=%.2f",
                                Math.toDegrees(extremum.getChi()))
                        : "",
                axis[0], axis[1], axis[2]));
    }

    private void drawChart() {
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        double w = this.canvas.getWidth();
        double h = this.canvas.getHeight();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);
        ElateReport r = this.report;
        if (r == null) {
            gc.setFill(Color.DARKGRAY);
            gc.fillText("Analyse a tensor to draw its polar variation.", 20, 30);
            this.chartCaption.setText("");
            return;
        }
        if (r.getMinYoung() == null) {
            gc.setFill(Color.DARKRED);
            gc.fillText("No chart: the tensor is mechanically unstable and ELATE"
                    + " computes no spatial extrema.", 20, 30);
            this.chartCaption.setText("");
            return;
        }
        int mode = this.modeCombo.getSelectionModel().getSelectedIndex();
        Plane plane = this.planeCombo.getSelectionModel().getSelectedItem();
        int samples = 360;
        double cx = w / 2.0;
        double cy = h / 2.0 + 8.0;
        double radiusMax = Math.min(w, h) / 2.0 - 46.0;

        if (mode <= 1) {
            double[] values = mode == 0 ? r.polarYoung(plane, samples)
                    : r.polarLc(plane, samples);
            double vmin = Double.POSITIVE_INFINITY;
            double vmax = Double.NEGATIVE_INFINITY;
            for (double v : values) {
                vmin = Math.min(vmin, v);
                vmax = Math.max(vmax, v);
            }
            drawPolarCurve(gc, cx, cy, radiusMax, values, vmin, vmax, Color.DARKBLUE,
                    samples);
            this.chartCaption.setText(String.format(Locale.ROOT,
                    "%s on %s: min %.6g, max %.6g - radial axis renormalized to"
                            + " [min,max] (stated, not to scale from 0).",
                    MODES[mode], plane.getLabel(), vmin, vmax));
        } else {
            Band[] bands = mode == 2 ? r.polarShearBand(plane, samples)
                    : r.polarPoissonBand(plane, samples);
            double vmin = Double.POSITIVE_INFINITY;
            double vmax = Double.NEGATIVE_INFINITY;
            for (Band band : bands) {
                vmin = Math.min(vmin, band.getMin());
                vmax = Math.max(vmax, band.getMax());
            }
            double[] mins = new double[samples];
            double[] maxs = new double[samples];
            for (int i = 0; i < samples && i < bands.length; i++) {
                mins[i] = bands[i].getMin();
                maxs[i] = bands[i].getMax();
            }
            drawPolarBand(gc, cx, cy, radiusMax, mins, maxs, vmin, vmax, samples);
            this.chartCaption.setText(String.format(Locale.ROOT,
                    "%s on %s: band [%.6g, %.6g] - the chi-sweep min/max envelope,"
                            + " radial axis renormalized (stated).",
                    MODES[mode], plane.getLabel(), vmin, vmax));
        }
        gc.setStroke(Color.LIGHTGRAY);
        gc.strokeOval(cx - radiusMax, cy - radiusMax, 2 * radiusMax, 2 * radiusMax);
        gc.strokeLine(cx - radiusMax - 6, cy, cx + radiusMax + 6, cy);
        gc.strokeLine(cx, cy - radiusMax - 6, cx, cy + radiusMax + 6);
    }

    private static void drawPolarCurve(GraphicsContext gc, double cx, double cy,
            double radiusMax, double[] values, double vmin, double vmax, Color color,
            int samples) {
        gc.setStroke(color);
        gc.setLineWidth(1.8);
        double prevX = Double.NaN;
        double prevY = Double.NaN;
        double firstX = Double.NaN;
        double firstY = Double.NaN;
        double span = (vmax - vmin) == 0.0 ? 1.0 : (vmax - vmin);
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i / samples;
            double frac = 0.15 + 0.85 * (values[i] - vmin) / span;
            double px = cx + frac * radiusMax * Math.cos(angle);
            double py = cy - frac * radiusMax * Math.sin(angle);
            if (i == 0) {
                firstX = px;
                firstY = py;
            } else {
                gc.strokeLine(prevX, prevY, px, py);
            }
            prevX = px;
            prevY = py;
        }
        gc.strokeLine(prevX, prevY, firstX, firstY);
    }

    private static void drawPolarBand(GraphicsContext gc, double cx, double cy,
            double radiusMax, double[] mins, double[] maxs, double vmin, double vmax,
            int samples) {
        double span = (vmax - vmin) == 0.0 ? 1.0 : (vmax - vmin);
        double[] xs = new double[2 * samples];
        double[] ys = new double[2 * samples];
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i / samples;
            double fracOut = 0.15 + 0.85 * (maxs[i] - vmin) / span;
            xs[i] = cx + fracOut * radiusMax * Math.cos(angle);
            ys[i] = cy - fracOut * radiusMax * Math.sin(angle);
        }
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * (samples - 1 - i) / samples;
            double fracIn = 0.15 + 0.85 * (mins[samples - 1 - i] - vmin) / span;
            xs[samples + i] = cx + fracIn * radiusMax * Math.cos(angle);
            ys[samples + i] = cy - fracIn * radiusMax * Math.sin(angle);
        }
        gc.setFill(Color.rgb(70, 110, 200, 0.45));
        gc.fillPolygon(xs, ys, 2 * samples);
        gc.setStroke(Color.DARKBLUE);
        gc.setLineWidth(1.2);
        gc.strokePolygon(xs, ys, 2 * samples);
    }

    private void probeDirection() {
        ElateReport r = this.report;
        if (r == null) {
            this.probeLabel.setText("Analyse a tensor first.");
            return;
        }
        if (r.getMinYoung() == null) {
            this.probeLabel.setText("Probe unavailable: stability gate fired (no extrema).");
            return;
        }
        double thetaDeg;
        double phiDeg;
        try {
            thetaDeg = Double.parseDouble(this.thetaField.getText().trim());
            phiDeg = Double.parseDouble(this.phiField.getText().trim());
        } catch (NumberFormatException ex) {
            this.probeLabel.setText("theta/phi must be plain numbers (degrees).");
            return;
        }
        double theta = Math.toRadians(thetaDeg);
        double phi = Math.toRadians(phiDeg);
        double young = r.youngGpa(theta, phi);
        double lc = r.lcTPa(theta, phi);
        double shearMin = Double.POSITIVE_INFINITY;
        double shearMax = Double.NEGATIVE_INFINITY;
        double poisMin = Double.POSITIVE_INFINITY;
        double poisMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < QEElateAnalyzer.BAND_CHI_STEPS; i++) {
            double chi = Math.PI * i / (QEElateAnalyzer.BAND_CHI_STEPS - 1);
            double shear = r.shearGpa(theta, phi, chi);
            shearMin = Math.min(shearMin, shear);
            shearMax = Math.max(shearMax, shear);
            double pois = r.poisson(theta, phi, chi);
            poisMin = Math.min(poisMin, pois);
            poisMax = Math.max(poisMax, pois);
        }
        double[] axis = QEElateAnalyzer.dirVec(theta, phi);
        this.probeLabel.setText(String.format(Locale.ROOT,
                "axis (%.4f, %.4f, %.4f):  E=%.5f GPa,  LC=%.5f TPa^-1,  "
                        + "G in [%.5f, %.5f] GPa,  nu in [%.5f, %.5f]  (chi swept 0..pi).",
                axis[0], axis[1], axis[2], young, lc, shearMin, shearMax,
                poisMin, poisMax));
    }
}
