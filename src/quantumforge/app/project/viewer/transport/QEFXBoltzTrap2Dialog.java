/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.transport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import quantumforge.com.math.ChartGeometry;
import quantumforge.operation.OperationResult;
import quantumforge.project.property.ProjectProperty;
import quantumforge.run.parser.BoltzTrap2Btp2Plan;
import quantumforge.run.parser.BoltzTrap2Btp2Plan.Plan;
import quantumforge.run.parser.BoltzTrap2Btp2Plan.Request;
import quantumforge.run.parser.BoltzTrap2DopeDosParser;
import quantumforge.run.parser.BoltzTrap2DopeDosParser.DopeDosKind;
import quantumforge.run.parser.BoltzTrap2DopeDosParser.DopeDosTable;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer.MuCurve;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer.SeriesKind;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer.TemperatureSeries;
import quantumforge.run.parser.BoltzTrap2TraceParser;
import quantumforge.run.parser.BoltzTrap2TraceParser.FileKind;
import quantumforge.run.parser.BoltzTrap2TraceParser.HallRow;
import quantumforge.run.parser.BoltzTrap2TraceParser.TransportRow;

/**
 * Roadmap #109/#152 arc closure (batch 172): the BoltzTraP2 STUDIO - one
 * dialog that (a) WATCHes a btp2 output directory live, (b) OPENs the batch-152
 * + batch-172 parser grammars explicitly, drawing TRUE-unit or explicitly
 * normalized charts through the tested slicer + {@link ChartGeometry}, and
 * (c) PLANs {@code btp2} command lines through the tested
 * {@link BoltzTrap2Btp2Plan} PREVIEW - pinned against gitlab.com/yiwang62/
 * BoltzTraP2 branch 20210126 (the BoltzTraP2Y fork), the boltztrap2y
 * readthedocs install page, and the sousaw wiki tutorial.
 *
 * <p>Families (name + column grammar, per the batch-152/172 parsers):
 * .trace (10 cols, isotropic) / .condtens (30 cols, 3x3 tensors) /
 * .halltens (30 cols, rank-3 RH tensor) / .dope.trace (23 cols, mu-Ef[eV]
 * first column) / .dope.dos + .dope.vvdos (+ their _raw renames; 2/4 cols,
 * headerless) / .bt2 + .btj (xz-JSON containers - ENUMERATED, never parsed;
 * they belong to btp2 itself).</p>
 *
 * <p>Honesty rules (also stated on the header/status lines): charts read
 * ONLY the parsed rows - an absent mu/T says "no curve", never a nearest
 * substitute; single-series views draw TRUE header-verbatim units; overlay
 * views are labeled min-max-normalized display aids, not physical units;
 * the first-column unit semantics (Ef[Ry] vs mu-Ef[eV]) come from the
 * header certificate, never assumed; .bt2/.btj are enumerated (size/mtime)
 * and never cracked open; PLAN lines are a PREVIEW STUDY that QuantumForge
 * never executes, never writes into the project, and never saves to disk.
 * The dialog writes nothing, starts nothing, changes no project state; the
 * k-mesh / scattering-model / mu-grid convergence burden stays with the
 * user - the studio displays the files' numbers, it does not certify
 * them.</p>
 */
public final class QEFXBoltzTrap2Dialog extends Dialog<Void> {

    private static final Color[] SERIES_COLORS = {
            Color.STEELBLUE, Color.DARKORANGE, Color.SEAGREEN, Color.MEDIUMPURPLE,
            Color.CRIMSON, Color.DARKCYAN, Color.GOLDENROD};
    private static final int WATCH_POLL_SECONDS = 2;
    private static final int WATCH_SCAN_CAP = 128;

    /** What the chart side currently holds. */
    private enum ArtifactFamily { NONE, TRANSPORT, HALL, DOPE, BINARY }

    // ---- OPEN row + shared chart state ----
    private final ComboBox<String> openFamily = new ComboBox<>();
    private final Button openButton = new Button("Open file ...");

    private final Canvas canvas = new Canvas(760, 430);
    private final ComboBox<String> quantityCombo = new ComboBox<>();
    private final ComboBox<String> modeCombo = new ComboBox<>();
    private final ComboBox<String> fixedCombo = new ComboBox<>();
    private final ComboBox<String> hallCombo = new ComboBox<>();
    private final ComboBox<String> viewCombo = new ComboBox<>();
    private final Label fileLabel = new Label("(no artifact loaded)");
    private final Label provenanceLabel = new Label();
    private final Label statusLabel = new Label();

    private ArtifactFamily family = ArtifactFamily.NONE;
    private String sourceName = "";
    private BoltzTrap2TraceParser parser;
    private DopeDosTable dopeTable;
    private String binaryNote = "";
    private final List<SeriesKind> availableKinds = new ArrayList<>();
    private final List<Double> fixedValues = new ArrayList<>();

    // ---- WATCH tab ----
    private final Label watchDirLabel = new Label("(no directory picked)");
    private final Button watchPickButton = new Button("Pick output directory ...");
    private final Button watchToggleButton = new Button("Start watching");
    private final CheckBox autoChartBox = new CheckBox(
            "auto-chart the newest parsed artifact");
    private final ListView<String> censusList = new ListView<>();
    private final Label watchStatus = new Label("Pick a btp2 output directory"
            + " (the interpolate/integrate/dope working dir) to watch it live.");
    private File watchDir;
    private final Timeline watchTimer;
    private final Map<String, String> watchSignatures = new LinkedHashMap<>();
    private final Map<String, String> watchCensus = new LinkedHashMap<>();
    private String chartedByWatch = "";

    // ---- PLAN tab ----
    private final TextField planDirectory = new TextField();
    private final ComboBox<String> planGridMode = new ComboBox<>();
    private final TextField planGridValue = new TextField();
    private final TextField planTemps = new TextField();
    private final TextField planLevels = new TextField();
    private final TextField planVerbose = new TextField();
    private final TextField planWorkers = new TextField();
    private final CheckBox planDope = new CheckBox("dope");
    private final CheckBox planPlot = new CheckBox("plot");
    private final CheckBox planPlotBands = new CheckBox("plotbands");
    private final TextField planQuantity = new TextField();
    private final TextField planComponents = new TextField();
    private final Button planLiznsb = new Button("LiZnSb tutorial preset");
    private final Button planQe = new Button("QE-outdir preset (uses directory)");
    private final Button planBuild = new Button("Build preview from fields");
    private final Button planCopy = new Button("Copy steps to clipboard");
    private final TextArea planSteps = new TextArea();
    private final TextArea planNotes = new TextArea();
    private final Label planStatus = new Label("PLAN emits PREVIEW command lines"
            + " only - QuantumForge NEVER runs, writes, or saves them.");

    public QEFXBoltzTrap2Dialog() {
        setTitle("BoltzTraP2 studio - WATCH outputs / OPEN tables / PLAN btp2 runs");
        setHeaderText("BoltzTraP2 (BoltzTraP2Y fork, gitlab.com/yiwang62/BoltzTraP2"
                + " branch 20210126; install page: boltztrap2y.readthedocs.io)."
                + " Charts draw the files' own numbers with header-verbatim"
                + " units (overlays are labeled normalized display aids); PLAN"
                + " lines are a preview that QuantumForge never executes."
                + " Convergence in k-mesh / scattering model / mu grid stays"
                + " the user's publication burden.");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        this.openFamily.getItems().setAll(
                "Auto-detect from name/columns",
                "Transport table (.trace / .condtens / .dope.trace)",
                "Hall tensor (.halltens)",
                "Dope DOS tables (.dope.dos / .dope.vvdos / _raw)",
                "Container (enumerate only: .bt2 / .btj)");
        this.openFamily.getSelectionModel().select(0);
        HBox openRow = new HBox(8, new Label("OPEN:"), this.openFamily,
                this.openButton);

        this.modeCombo.getItems().setAll("T-curves at fixed mu (plot -u style)",
                "mu-curves at fixed T (plot -T style)");
        this.modeCombo.getSelectionModel().select(0);
        this.viewCombo.getItems().setAll("Single series (true units)",
                "Overlay (min-max normalized 0..1)");
        this.viewCombo.getSelectionModel().select(0);
        this.quantityCombo.setPrefWidth(340);
        HBox controls1 = new HBox(8, new Label("Quantity:"), this.quantityCombo,
                this.modeCombo);
        HBox controls2 = new HBox(8, new Label("fixed:"), this.fixedCombo,
                new Label("Hall component:"), this.hallCombo, this.viewCombo);

        VBox topBox = new VBox(6, openRow, controls1, controls2, this.fileLabel);
        VBox centerBox = new VBox(4, this.canvas, this.provenanceLabel);

        Tab watchTab = new Tab("WATCH (live 2s poll)", buildWatchPane());
        watchTab.setClosable(false);
        Tab planTab = new Tab("PLAN (btp2 preview)", buildPlanPane());
        planTab.setClosable(false);
        TabPane tabs = new TabPane(watchTab, planTab);

        BorderPane content = new BorderPane(centerBox);
        content.setTop(topBox);
        content.setBottom(new VBox(4, this.statusLabel, tabs));
        ScrollPane scroller = new ScrollPane(content);
        scroller.setFitToWidth(true);
        getDialogPane().setContent(scroller);
        getDialogPane().setPrefSize(840, 1020);

        this.watchTimer = new Timeline(new KeyFrame(
                Duration.seconds(WATCH_POLL_SECONDS), event -> poll()));
        this.watchTimer.setCycleCount(Animation.INDEFINITE);
        setOnCloseRequest(event -> this.watchTimer.stop());

        this.openButton.setOnAction(event -> openExplicit());
        this.quantityCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.modeCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    refillFixedCombo();
                    render();
                });
        this.fixedCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.hallCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.viewCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.autoChartBox.setSelected(true);
        clearCanvas();
    }

    // ============================ family routing ============================

    /** Family decision from the file NAME (the parsers then certify columns). */
    private static String familyOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".bt2") || n.endsWith(".btj")) {
            return "BINARY";
        }
        if (n.contains("halltens")) {
            return "HALL";
        }
        if (n.endsWith(".dope.dos") || n.endsWith(".dope.vvdos")
                || n.endsWith(".dope.dos_raw") || n.endsWith(".dope.vvdos_raw")) {
            return "DOPE";
        }
        if (n.endsWith(".trace") || n.contains("condtens")
                || n.contains("seebeck")) {
            return "TRANSPORT";
        }
        return null;
    }

    // ============================ OPEN (explicit) ============================

    private void openExplicit() {
        FileChooser chooser = new FileChooser();
        int pick = this.openFamily.getSelectionModel().getSelectedIndex();
        switch (pick) {
            case 1 -> chooser.getExtensionFilters().add(new FileChooser
                    .ExtensionFilter("BoltzTraP2 transport tables", "*.trace",
                            "*condtens*", "*seebeck*", "*"));
            case 2 -> chooser.getExtensionFilters().add(new FileChooser
                    .ExtensionFilter("BoltzTraP2 Hall tensor", "*halltens*", "*"));
            case 3 -> chooser.getExtensionFilters().add(new FileChooser
                    .ExtensionFilter("BoltzTraP2 dope DOS tables", "*.dope.dos",
                            "*.dope.vvdos", "*_raw", "*"));
            case 4 -> chooser.getExtensionFilters().add(new FileChooser
                    .ExtensionFilter("BoltzTraP2 containers (enumerated only)",
                            "*.bt2", "*.btj", "*"));
            default -> chooser.getExtensionFilters().add(new FileChooser
                    .ExtensionFilter("BoltzTraP2 artifacts", "*.trace",
                            "*condtens*", "*halltens*", "*.dope.dos",
                            "*.dope.vvdos", "*.bt2", "*.btj", "*"));
        }
        if (this.watchDir != null && this.watchDir.isDirectory()) {
            chooser.setInitialDirectory(this.watchDir);
        }
        Window owner = getDialogPane().getScene() == null ? null
                : getDialogPane().getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        String forced = pick == 1 ? "TRANSPORT" : pick == 2 ? "HALL"
                : pick == 3 ? "DOPE" : pick == 4 ? "BINARY" : null;
        String detected = familyOf(file.getName());
        loadArtifact(file, forced != null ? forced
                : detected != null ? detected : "TRANSPORT");
        // an explicit OPEN wins over the watcher until watching restarts
        this.chartedByWatch = "";
        this.autoChartBox.setSelected(false);
    }

    /**
     * Parse + chart one artifact through the tested parsers. Every refusal
     * path shows the parser's own message; nothing is guessed or skipped
     * quietly.
     */
    private void loadArtifact(File file, String wanted) {
        switch (wanted) {
            case "BINARY" -> {
                this.family = ArtifactFamily.BINARY;
                this.sourceName = file.getName();
                this.parser = null;
                this.dopeTable = null;
                this.binaryNote = file.getName() + " is an xz-compressed JSON"
                        + " container written for btp2 itself (interpolate"
                        + " coefficients / integrated results). This studio"
                        + " enumerates it (size " + file.length()
                        + " bytes, mtime " + file.lastModified()
                        + ") and PLANs the btp2 lines that consume it;"
                        + " parsing it is btp2's job, so nothing is cracked"
                        + " open here.";
                this.fileLabel.setText(file.getName()
                        + "   [container - enumerated, never parsed]");
                this.provenanceLabel.setText(this.binaryNote);
                this.quantityCombo.getItems().clear();
                this.fixedCombo.getItems().clear();
                this.fixedValues.clear();
                render();
            }
            case "HALL" -> loadTraceFamily(file, true);
            case "DOPE" -> {
                OperationResult<DopeDosTable> result =
                        BoltzTrap2DopeDosParser.parse(file.toPath());
                if (!result.isSuccess() || result.getValue().isEmpty()) {
                    showProblem("Dope DOS table refused",
                            result.getCode() + ": " + result.getMessage());
                    return;
                }
                DopeDosTable table = result.getValue().get();
                if (table.getRows() < 2) {
                    showProblem("Too few clean rows", file.getName() + " gave "
                            + table.getRows() + " clean rows; a single point is"
                            + " not a curve.");
                    return;
                }
                this.family = ArtifactFamily.DOPE;
                this.sourceName = file.getName();
                this.parser = null;
                this.dopeTable = table;
                List<String> labels = new ArrayList<>();
                if (table.getKind() == DopeDosKind.DOS) {
                    labels.add("dos*Volt (column 2 as written)");
                } else {
                    labels.add("vvdos_11*Volt (as written)");
                    labels.add("vvdos_22*Volt (as written)");
                    labels.add("vvdos_33*Volt (as written)");
                }
                this.quantityCombo.getItems().setAll(labels);
                this.quantityCombo.getSelectionModel().select(0);
                this.fixedCombo.getItems().clear();
                this.fixedValues.clear();
                this.fileLabel.setText(file.getName() + "   [" + table.getKind()
                        + ", " + table.getRows() + " clean rows"
                        + (table.getPartialTailHeld() > 0
                                ? ", partial tail held back: "
                                        + table.getPartialTailHeld()
                                : "")
                        + "]");
                this.provenanceLabel.setText(String.join("  |  ", table.getNotes()));
                this.availableKinds.clear();
                render();
            }
            default -> loadTraceFamily(file, false);
        }
    }

    /** .trace/.condtens/.halltens through the batch-152/172 trace parser. */
    private void loadTraceFamily(File file, boolean expectHall) {
        BoltzTrap2TraceParser parsed = new BoltzTrap2TraceParser(new ProjectProperty());
        try {
            parsed.parse(file);
        } catch (IOException problem) {
            showProblem("Could not read the BoltzTraP2 table", problem.toString());
            return;
        }
        FileKind kind = parsed.getFileKind();
        if (expectHall && kind != FileKind.HALLTENS) {
            showProblem("Not a certified Hall tensor file",
                    file.getName() + " parsed as " + kind + " - the .halltens"
                            + " grammar needs the RH[m**3/C] 30-column header."
                            + " Provenance: " + parsed.getFamilyNote());
            return;
        }
        if (kind == FileKind.TENSOR_OTHER) {
            showProblem("Tensor table without a certified header",
                    file.getName() + " is a 30-column tensor table whose header"
                            + " certifies neither sigma..kappa (.condtens) nor"
                            + " RH[m**3/C] (.halltens): the studio refuses to"
                            + " re-interpret it. Provenance: "
                            + parsed.getFamilyNote());
            return;
        }
        if (kind != FileKind.HALLTENS && parsed.getRows().size() < 2) {
            showProblem("Too few clean rows",
                    "Fewer than two clean transport rows were parsed from "
                            + file.getName() + " (clean: " + parsed.getRows().size()
                            + ", skipped: " + parsed.getSkippedRowCount() + "); a"
                            + " single point is not a curve. Provenance: "
                            + parsed.getFamilyNote());
            return;
        }
        if (kind == FileKind.HALLTENS && parsed.getHallRows().size() < 2) {
            showProblem("Too few clean rows",
                    "Fewer than two clean Hall rows were parsed from "
                            + file.getName() + " (clean: "
                            + parsed.getHallRows().size() + ", skipped: "
                            + parsed.getSkippedRowCount() + "). Provenance: "
                            + parsed.getFamilyNote());
            return;
        }
        this.parser = parsed;
        this.dopeTable = null;
        this.sourceName = file.getName();
        this.family = kind == FileKind.HALLTENS
                ? ArtifactFamily.HALL : ArtifactFamily.TRANSPORT;
        if (kind == FileKind.HALLTENS) {
            this.availableKinds.clear();
            List<String> hall = new ArrayList<>();
            hall.add("even-permutation average (save_halltens ohall)");
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        hall.add("RH[" + i + "," + j + "," + k + "]  (Fortran"
                                + " ravel index " + (i + 3 * j + 9 * k) + ")");
                    }
                }
            }
            this.hallCombo.getItems().setAll(hall);
            this.hallCombo.getSelectionModel().select(0);
            this.quantityCombo.getItems().setAll("RH[m**3/C] (header token)"
                    + "  -  component picked in the Hall combo");
            this.quantityCombo.getSelectionModel().select(0);
        } else {
            this.availableKinds.clear();
            for (SeriesKind seriesKind : SeriesKind.values()) {
                if (!kindAvailable(seriesKind, kind)) {
                    continue;
                }
                this.availableKinds.add(seriesKind);
            }
            List<String> labels = new ArrayList<>();
            for (SeriesKind seriesKind : this.availableKinds) {
                labels.add(BoltzTrap2SeriesSlicer.axisLabel(seriesKind,
                        parsed.getSigmaUnits(), parsed.getKappaUnits()));
            }
            this.quantityCombo.getItems().setAll(labels);
            this.quantityCombo.getSelectionModel().select(0);
        }
        refillFixedCombo();
        boolean columnOneEv = kind == FileKind.TRACE_DOPE_EXT;
        this.fileLabel.setText(file.getName() + "   [" + kind + ", "
                + (kind == FileKind.HALLTENS ? parsed.getHallRows().size()
                        : parsed.getRows().size())
                + " clean rows, " + parsed.getSkippedRowCount() + " skipped]"
                + (parsed.getScatteringModel().isEmpty() ? ""
                        : "   scattering: " + parsed.getScatteringModel()));
        this.provenanceLabel.setText(parsed.getFamilyNote() + "  |  column-1: "
                + parsed.getColumnOneNote()
                + (kind == FileKind.HALLTENS ? ""
                        : "  |  sigma units verbatim: " + parsed.getSigmaUnits()
                                + "  |  kappa units verbatim: "
                                + parsed.getKappaUnits())
                + (columnOneEv
                        ? "  |  mu labels below are eV AS WRITTEN (the fork's"
                                + " .dope.trace first column) - no Ry conversion"
                                + " is invented"
                        : ""));
        render();
    }

    /** Series availability per file kind (mirrors the writer grammars). */
    private static boolean kindAvailable(SeriesKind seriesKind, FileKind kind) {
        switch (seriesKind) {
            case SEEBECK_XX, SEEBECK_YY, SEEBECK_ZZ -> {
                return kind == FileKind.CONDTENS;
            }
            case CV_X, S_X, N_X -> {
                return kind == FileKind.TRACE_DOPE_X
                        || kind == FileKind.TRACE_DOPE_EXT;
            }
            default -> {
                return kind != FileKind.HALLTENS;
            }
        }
    }

    /** Fill the fixed-coordinate picker (mu or T) for the current mode+family. */
    private void refillFixedCombo() {
        this.fixedValues.clear();
        List<String> labels = new ArrayList<>();
        boolean muFixed = this.modeCombo.getSelectionModel().getSelectedIndex() == 0;
        if (this.family == ArtifactFamily.HALL && this.parser != null) {
            LinkedHashSet<Double> mus = new LinkedHashSet<>();
            if (muFixed) {
                for (HallRow row : this.parser.getHallRows()) {
                    mus.add(row.getMuRy());
                }
                List<Double> sorted = new ArrayList<>(mus);
                sorted.sort(Comparator.naturalOrder());
                for (double mu : sorted) {
                    this.fixedValues.add(mu);
                    labels.add(String.format(Locale.ROOT, "mu = %+.6f Ry (%+.5f eV)",
                            mu, mu * BoltzTrap2TraceParser.RY_TO_EV));
                }
            } else {
                LinkedHashSet<Double> temps = new LinkedHashSet<>();
                for (HallRow row : this.parser.getHallRows()) {
                    temps.add(row.getTemperatureK());
                }
                List<Double> sorted = new ArrayList<>(temps);
                sorted.sort(Comparator.naturalOrder());
                for (double temperature : sorted) {
                    this.fixedValues.add(temperature);
                    labels.add(String.format(Locale.ROOT, "T = %.1f K", temperature));
                }
            }
        } else if (this.family == ArtifactFamily.TRANSPORT && this.parser != null) {
            List<TransportRow> rows = this.parser.getRows();
            boolean evLabels = !rows.isEmpty() && rows.get(0).isColumnOneEv();
            if (muFixed) {
                for (double mu : BoltzTrap2SeriesSlicer.distinctMuRy(rows)) {
                    this.fixedValues.add(mu);
                    labels.add(evLabels
                            ? String.format(Locale.ROOT,
                                    "mu = %+.6f eV (mu-Ef as written)", mu)
                            : String.format(Locale.ROOT, "mu = %+.6f Ry (%+.5f eV)",
                                    mu, mu * BoltzTrap2TraceParser.RY_TO_EV));
                }
            } else {
                for (double temperature
                        : BoltzTrap2SeriesSlicer.distinctTemperatures(rows)) {
                    this.fixedValues.add(temperature);
                    labels.add(String.format(Locale.ROOT, "T = %.1f K", temperature));
                }
            }
        }
        this.fixedCombo.getItems().setAll(labels);
        if (!labels.isEmpty()) {
            this.fixedCombo.getSelectionModel().select(0);
        }
    }

    // ============================ chart rendering ============================

    private void clearCanvas() {
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
    }

    /** Draw one x/y series with the dialog's axis idiom. */
    private void drawSeries(GraphicsContext g, List<Double> xs, List<Double> ys,
            double left, double top, double right, double bottom,
            double[] xExtent, double[] yExtent, Color color,
            boolean normalize) {
        double yLo = yExtent[0];
        double yHi = yExtent[1];
        if (normalize && !ys.isEmpty()) {
            double[] padded = ChartGeometry.padded(ChartGeometry.extent(ys)[0],
                    ChartGeometry.extent(ys)[1]);
            yLo = padded[0];
            yHi = padded[1];
        }
        g.setStroke(color);
        g.setLineWidth(1.8);
        double previousX = 0;
        double previousY = 0;
        for (int i = 0; i < xs.size() && i < ys.size(); i++) {
            double sx = ChartGeometry.mapLinear(xs.get(i), xExtent[0], xExtent[1],
                    left, right);
            double sy = normalize
                    ? ChartGeometry.mapLinear(ys.get(i), yLo, yHi, bottom, top)
                    : ChartGeometry.mapLinear(ys.get(i), yExtent[0], yExtent[1],
                            bottom, top);
            if (i > 0) {
                g.strokeLine(previousX, previousY, sx, sy);
            }
            g.setFill(color);
            g.fillOval(sx - 3, sy - 3, 6, 6);
            previousX = sx;
            previousY = sy;
        }
    }

    /** Axes box + nice ticks; the caller passes the x/y captions verbatim. */
    private void drawAxes(GraphicsContext g, double left, double top, double right,
            double bottom, double[] xExtent, double[] yExtent,
            String xCaption, String yCaption, boolean normalizedY) {
        g.setStroke(Color.BLACK);
        g.setLineWidth(1.0);
        g.strokeLine(left, bottom, right, bottom);
        g.strokeLine(left, top, left, bottom);
        double[] xTicks = ChartGeometry.niceTicks(xExtent[0], xExtent[1], 6);
        double[] yTicks = normalizedY ? new double[] {0.0, 0.5, 1.0}
                : ChartGeometry.niceTicks(yExtent[0], yExtent[1], 5);
        g.setFill(Color.BLACK);
        for (double tick : xTicks) {
            double sx = ChartGeometry.mapLinear(tick, xExtent[0], xExtent[1],
                    left, right);
            g.strokeLine(sx, bottom, sx, bottom + 5);
            g.fillText(String.format(Locale.ROOT, "%.4g", tick), sx - 12,
                    bottom + 18);
        }
        for (double tick : yTicks) {
            double sy = normalizedY
                    ? ChartGeometry.mapLinear(tick, 0.0, 1.0, bottom, top)
                    : ChartGeometry.mapLinear(tick, yExtent[0], yExtent[1],
                            bottom, top);
            g.strokeLine(left, sy, left - 5, sy);
            g.fillText(String.format(Locale.ROOT, "%.4g", tick), 6, sy + 4);
        }
        g.fillText(yCaption, 6, top - 6);
        g.fillText(xCaption, left, this.canvas.getHeight() - 8);
    }

    /** Render dispatch by family; empty slices state the no-curve line. */
    private void render() {
        clearCanvas();
        switch (this.family) {
            case NONE -> {
                this.statusLabel.setText("OPEN a BoltzTraP2 artifact or WATCH a"
                        + " btp2 output directory to draw charts.");
                return;
            }
            case BINARY -> {
                GraphicsContext g = this.canvas.getGraphicsContext2D();
                g.setFill(Color.BLACK);
                g.fillText(this.sourceName
                        + ": xz-JSON container - enumerated, never parsed.",
                        40, 200);
                g.fillText("PLAN tab emits the btp2 lines that consume it"
                        + " (integrate / dope / plot / describe); the displayed"
                        + " physics stays btp2's job.", 40, 224);
                this.statusLabel.setText("container enumerated (size/mtime in"
                        + " the provenance line); nothing cracked open.");
                return;
            }
            case DOPE -> {
                renderDope();
                return;
            }
            case HALL -> {
                renderHall();
                return;
            }
            default -> {
            }
        }
        if (this.parser == null || this.availableKinds.isEmpty()
                || this.fixedCombo.getSelectionModel().getSelectedIndex() < 0) {
            this.statusLabel.setText("Pick a quantity + fixed coordinate to draw.");
            return;
        }
        int fixedIndex = this.fixedCombo.getSelectionModel().getSelectedIndex();
        if (fixedIndex >= this.fixedValues.size()) {
            return;
        }
        double fixed = this.fixedValues.get(fixedIndex);
        boolean muFixed = this.modeCombo.getSelectionModel().getSelectedIndex() == 0;
        boolean overlay = this.viewCombo.getValue().startsWith("Overlay");
        List<SeriesKind> kinds = overlay ? this.availableKinds
                : List.of(this.availableKinds.get(Math.max(0,
                        this.quantityCombo.getSelectionModel().getSelectedIndex())));
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        double left = 70;
        double top = 30;
        double right = this.canvas.getWidth() - 30;
        double bottom = this.canvas.getHeight() - 60;
        List<TransportRow> rows = this.parser.getRows();
        boolean drew = false;
        int colorIndex = 0;
        StringBuilder legend = new StringBuilder();
        String yCaption = "";
        for (SeriesKind kind : kinds) {
            List<Double> xs;
            List<Double> ys;
            String label = BoltzTrap2SeriesSlicer.axisLabel(kind,
                    this.parser.getSigmaUnits(), this.parser.getKappaUnits());
            try {
                if (muFixed) {
                    TemperatureSeries series =
                            BoltzTrap2SeriesSlicer.slice(rows, fixed, kind);
                    if (series.size() == 0) {
                        continue;
                    }
                    xs = series.getTemperatures();
                    ys = series.getValues();
                } else {
                    MuCurve curve =
                            BoltzTrap2SeriesSlicer.sliceMuCurve(rows, fixed, kind);
                    if (curve.size() == 0) {
                        continue;
                    }
                    xs = curve.getMuRy();
                    ys = curve.getValues();
                }
            } catch (IllegalArgumentException guard) {
                this.statusLabel.setText("slicer refusal stated honestly: "
                        + guard.getMessage());
                return;
            }
            double[] xExtent = ChartGeometry.padded(
                    ChartGeometry.extent(xs)[0], ChartGeometry.extent(xs)[1]);
            double[] yExtent = overlay ? new double[] {0.0, 1.0}
                    : ChartGeometry.padded(ChartGeometry.extent(ys)[0],
                            ChartGeometry.extent(ys)[1]);
            if (colorIndex == 0) {
                yCaption = overlay ? "normalized 0..1 (display aid)" : label;
                drawAxes(g, left, top, right, bottom, xExtent, yExtent,
                        muFixed ? "T [K]"
                                : "mu [" + this.parser.getColumnOneNote() + "]",
                        yCaption, overlay);
            }
            drawSeries(g, xs, ys, left, top, right, bottom, xExtent, yExtent,
                    SERIES_COLORS[colorIndex % SERIES_COLORS.length], overlay);
            drew = true;
            legend.append(colorIndex == 0 ? "" : "   |   ").append(kind);
            colorIndex++;
        }
        if (!drew) {
            this.statusLabel.setText(String.format(Locale.ROOT,
                    "No curve at the fixed %s - an empty slice is shown honestly,"
                            + " never a nearest-value substitute.",
                    muFixed ? "chemical potential" : "temperature"));
            return;
        }
        this.statusLabel.setText(overlay
                ? "Overlay (each series min-max-normalized to 0..1 - display aid,"
                        + " NOT physical units): " + legend
                : this.fixedCombo.getValue() + "  |  " + yCaption
                        + "  |  points: clean rows only (skipped: "
                        + this.parser.getSkippedRowCount() + ")");
    }

    /** Hall channel: one component (or the even-perm average) vs T or mu. */
    private void renderHall() {
        if (this.parser == null
                || this.fixedCombo.getSelectionModel().getSelectedIndex() < 0) {
            this.statusLabel.setText("Pick a fixed coordinate for the Hall curve.");
            return;
        }
        int fixedIndex = this.fixedCombo.getSelectionModel().getSelectedIndex();
        if (fixedIndex >= this.fixedValues.size()) {
            return;
        }
        double fixed = this.fixedValues.get(fixedIndex);
        boolean muFixed = this.modeCombo.getSelectionModel().getSelectedIndex() == 0;
        int hallPick = Math.max(0,
                this.hallCombo.getSelectionModel().getSelectedIndex());
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<HallRow> rows = new ArrayList<>(this.parser.getHallRows());
        if (muFixed) {
            rows.removeIf(row -> row.getMuRy() != fixed);
            rows.sort(Comparator.comparingDouble(HallRow::getTemperatureK));
        } else {
            rows.removeIf(row -> row.getTemperatureK() != fixed);
            rows.sort(Comparator.comparingDouble(HallRow::getMuRy));
        }
        for (HallRow row : rows) {
            xs.add(muFixed ? row.getTemperatureK() : row.getMuRy());
            if (hallPick == 0) {
                ys.add(row.getRhEvenPermutationAverage());
            } else {
                int component = hallPick - 1;
                ys.add(row.getRhComponent(component % 3,
                        (component / 3) % 3, component / 9));
            }
        }
        if (ys.isEmpty()) {
            this.statusLabel.setText(String.format(Locale.ROOT,
                    "No Hall curve at the fixed %s - absent values are stated,"
                            + " never substituted.",
                    muFixed ? "chemical potential" : "temperature"));
            return;
        }
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        double left = 70;
        double top = 30;
        double right = this.canvas.getWidth() - 30;
        double bottom = this.canvas.getHeight() - 60;
        double[] xExtent = ChartGeometry.padded(
                ChartGeometry.extent(xs)[0], ChartGeometry.extent(xs)[1]);
        double[] yExtent = ChartGeometry.padded(
                ChartGeometry.extent(ys)[0], ChartGeometry.extent(ys)[1]);
        String yCaption = (hallPick == 0
                ? "RH even-perm avg" : "RH" + this.hallCombo.getValue()
                        .substring(2, this.hallCombo.getValue().indexOf(']' ) + 1))
                + " [m**3/C, header token]";
        drawAxes(g, left, top, right, bottom, xExtent, yExtent,
                muFixed ? "T [K]" : "mu [Ef[Ry] as written]", yCaption, false);
        drawSeries(g, xs, ys, left, top, right, bottom, xExtent, yExtent,
                SERIES_COLORS[0], false);
        StringBuilder sample = new StringBuilder();
        for (int i = 0; i < Math.min(3, ys.size()); i++) {
            sample.append(i == 0 ? "" : ", ")
                    .append(String.format(Locale.ROOT, "%.4g", ys.get(i)));
        }
        this.statusLabel.setText(this.fixedCombo.getValue() + "  |  " + yCaption
                + "  |  " + ys.size() + " pts, first values: " + sample
                + (ys.size() > 3 ? ", ..." : "") + "  |  skipped rows: "
                + this.parser.getSkippedRowCount());
    }

    /** dope.dos / dope.vvdos channels vs (E-Ef)[eV]; Fermi marker at 0. */
    private void renderDope() {
        if (this.dopeTable == null) {
            return;
        }
        boolean overlay = this.viewCombo.getValue().startsWith("Overlay")
                && this.dopeTable.getKind() == DopeDosKind.VVDOS;
        double[] energy = this.dopeTable.getEnergy();
        List<double[]> channels = this.dopeTable.getChannels();
        List<Integer> picked = new ArrayList<>();
        if (overlay) {
            for (int c = 0; c < channels.size(); c++) {
                picked.add(c);
            }
        } else {
            picked.add(Math.max(0, Math.min(
                    this.quantityCombo.getSelectionModel().getSelectedIndex(),
                    channels.size() - 1)));
        }
        List<Double> xs = new ArrayList<>(energy.length);
        for (double e : energy) {
            xs.add(e);
        }
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        double left = 70;
        double top = 30;
        double right = this.canvas.getWidth() - 30;
        double bottom = this.canvas.getHeight() - 60;
        double[] xExtent = ChartGeometry.padded(
                ChartGeometry.extent(xs)[0], ChartGeometry.extent(xs)[1]);
        int colorIndex = 0;
        StringBuilder legend = new StringBuilder();
        boolean axesDrawn = false;
        for (int c : picked) {
            List<Double> ys = new ArrayList<>(channels.get(c).length);
            for (double v : channels.get(c)) {
                ys.add(v);
            }
            double[] yExtent = overlay ? new double[] {0.0, 1.0}
                    : ChartGeometry.padded(ChartGeometry.extent(ys)[0],
                            ChartGeometry.extent(ys)[1]);
            if (!axesDrawn) {
                String yCaption = overlay
                        ? "normalized 0..1 (display aid)"
                        : this.dopeTable.getKind() == DopeDosKind.DOS
                                ? "dos*Volt (column 2 as written)"
                                : "vvdos_" + (c == 0 ? "11" : c == 1 ? "22" : "33")
                                        + "*Volt (as written)";
                drawAxes(g, left, top, right, bottom, xExtent, yExtent,
                        "(E - Ef) [eV] - dope table column 1 as written",
                        yCaption, overlay);
                axesDrawn = true;
            }
            drawSeries(g, xs, ys, left, top, right, bottom, xExtent, yExtent,
                    SERIES_COLORS[colorIndex % SERIES_COLORS.length], overlay);
            legend.append(colorIndex == 0 ? "" : "   |   ")
                    .append(this.dopeTable.getKind() == DopeDosKind.DOS ? "dos"
                            : "vvdos_" + (c == 0 ? "11" : c == 1 ? "22" : "33"));
            colorIndex++;
        }
        if (xExtent[0] < 0.0 && xExtent[1] > 0.0) {
            double zeroX = ChartGeometry.mapLinear(0.0, xExtent[0], xExtent[1],
                    left, right);
            g.setStroke(Color.GRAY);
            g.setLineDashes(4.0);
            g.setLineWidth(1.0);
            g.strokeLine(zeroX, top, zeroX, bottom);
            g.setLineDashes(null);
            g.setFill(Color.DIMGRAY);
            g.fillText("E = Ef", zeroX + 4, top + 14);
        }
        this.statusLabel.setText((overlay
                ? "Overlay (each channel min-max-normalized - display aid, NOT"
                        + " physical units): "
                : "True units as written: ")
                + legend + "  |  " + this.dopeTable.getRows() + " rows"
                + (this.dopeTable.getPartialTailHeld() > 0
                        ? ", partial tail held back: "
                                + this.dopeTable.getPartialTailHeld()
                        : ""));
    }

    // ============================ WATCH tab ============================

    private VBox buildWatchPane() {
        HBox controls = new HBox(8, this.watchPickButton, this.watchToggleButton,
                this.autoChartBox);
        this.censusList.setPrefHeight(190);
        this.watchPickButton.setOnAction(event -> pickWatchDirectory());
        this.watchToggleButton.setOnAction(event -> toggleWatch());
        return new VBox(6, controls, this.watchDirLabel, this.censusList,
                this.watchStatus);
    }

    private void pickWatchDirectory() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Pick the btp2 output directory to watch");
        Window owner = getDialogPane().getScene() == null ? null
                : getDialogPane().getScene().getWindow();
        File dir = chooser.showDialog(owner);
        if (dir == null) {
            return;
        }
        if (!dir.isDirectory()) {
            showProblem("Not a directory", dir.getAbsolutePath());
            return;
        }
        this.watchDir = dir;
        this.watchDirLabel.setText(dir.getAbsolutePath()
                + "   (poll every " + WATCH_POLL_SECONDS
                + "s; signatures = size+mtime, changed files re-parsed through"
                + " the tested parsers; vanished files drop off the census)");
        this.watchSignatures.clear();
        this.watchCensus.clear();
        this.censusList.getItems().clear();
        this.watchStatus.setText("Directory picked; start watching (or wait -"
                + " the next Start press re-scans).");
    }

    private void toggleWatch() {
        if (this.watchTimer.getStatus() == Animation.Status.RUNNING) {
            this.watchTimer.stop();
            this.watchToggleButton.setText("Start watching");
            this.watchStatus.setText("Watcher stopped; the census above is the"
                    + " last live state (nothing else is held).");
            return;
        }
        if (this.watchDir == null) {
            this.watchStatus.setText("Pick a directory first - watching without"
                    + " a target would poll a lie.");
            return;
        }
        this.watchToggleButton.setText("Stop watching");
        this.autoChartBox.setSelected(true);
        poll();
        this.watchTimer.play();
    }

    /** One poll: scan, signature-diff, reparse changed files, prune stale. */
    private void poll() {
        if (this.watchDir == null) {
            return;
        }
        File[] files = this.watchDir.listFiles(File::isFile);
        if (files == null) {
            this.watchStatus.setText("watch directory is no longer readable -"
                    + " refusing to guess; stopping.");
            toggleWatch();
            return;
        }
        List<File> interesting = new ArrayList<>();
        for (File file : files) {
            if (familyOf(file.getName()) != null) {
                interesting.add(file);
            }
        }
        interesting.sort(Comparator.comparing(File::getName));
        int shown = Math.min(interesting.size(), WATCH_SCAN_CAP);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String newestChanged = "";
        long newestStamp = -1;
        for (int i = 0; i < shown; i++) {
            File file = interesting.get(i);
            seen.add(file.getName());
            String signature = file.length() + ":" + file.lastModified();
            String old = this.watchSignatures.put(file.getName(), signature);
            if (signature.equals(old)) {
                continue;
            }
            refreshCensus(file);
            if (familyOf(file.getName()) != null
                    && !"BINARY".equals(familyOf(file.getName()))
                    && file.lastModified() >= newestStamp) {
                newestStamp = file.lastModified();
                newestChanged = file.getName();
            }
        }
        // stale entries vanish from the census honestly (never kept as ghosts)
        this.watchSignatures.keySet().retainAll(seen);
        this.watchCensus.keySet().retainAll(seen);
        List<String> rows = new ArrayList<>(this.watchCensus.values());
        if (rows.isEmpty()) {
            rows.add("[waiting] no BoltzTraP2 artifacts yet - expected:"
                    + " *.trace, *condtens*, *halltens*, *.dope.trace,"
                    + " *.dope.dos / *.dope.vvdos (+ _raw renames);"
                    + " *.bt2 / *.btj are enumerated, never parsed");
        }
        this.censusList.getItems().setAll(rows);
        this.watchStatus.setText("watching: " + seen.size() + " artifact(s)"
                + (interesting.size() > shown
                        ? " (scan capped at " + WATCH_SCAN_CAP
                                + " - the rest wait for the next poll)"
                        : "")
                + "  |  dir: " + this.watchDir.getName());
        if (!newestChanged.isEmpty() && this.autoChartBox.isSelected()
                && !newestChanged.equals(this.chartedByWatch)) {
            File target = new File(this.watchDir, newestChanged);
            loadArtifact(target, familyOf(newestChanged));
            if (this.family != ArtifactFamily.NONE) {
                this.chartedByWatch = newestChanged;
            }
        }
    }

    /** Re-parse one changed artifact and refresh its census line. */
    private void refreshCensus(File file) {
        String name = file.getName();
        String family = familyOf(name);
        if (family == null) {
            return;
        }
        switch (family) {
            case "BINARY" -> this.watchCensus.put(name,
                    "[enumerated] " + name + "   xz-JSON container, "
                            + file.length() + " bytes (btp2's own; never parsed)");
            case "DOPE" -> {
                OperationResult<DopeDosTable> result =
                        BoltzTrap2DopeDosParser.parse(file.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    DopeDosTable table = result.getValue().get();
                    this.watchCensus.put(name, "[fresh] " + name + "   "
                            + table.getKind() + ", " + table.getRows()
                            + " clean rows"
                            + (table.getPartialTailHeld() > 0
                                    ? ", partial tail held: "
                                            + table.getPartialTailHeld()
                                    : ""));
                } else {
                    this.watchCensus.put(name, "[refused] " + name + "   "
                            + result.getCode() + ": " + result.getMessage());
                }
            }
            default -> {
                BoltzTrap2TraceParser parsed =
                        new BoltzTrap2TraceParser(new ProjectProperty());
                try {
                    parsed.parse(file);
                    int clean = parsed.getFileKind() == FileKind.HALLTENS
                            ? parsed.getHallRows().size() : parsed.getRows().size();
                    this.watchCensus.put(name, "[fresh] " + name + "   "
                            + parsed.getFileKind() + ", " + clean + " clean rows, "
                            + parsed.getSkippedRowCount() + " skipped"
                            + (parsed.getFileKind() == FileKind.TRACE_DOPE_EXT
                                    ? "   (mu-Ef[eV] first column, certified)"
                                    : ""));
                } catch (IOException problem) {
                    this.watchCensus.put(name, "[unreadable] " + name + "   "
                            + problem);
                }
            }
        }
    }

    // ============================ PLAN tab ============================

    private VBox buildPlanPane() {
        this.planDirectory.setPromptText("QE outdir (qes-1.0 XML inside) or the"
                + " LiZnSb data dir - positional argument of 'interpolate'");
        this.planDirectory.setPrefColumnCount(30);
        this.planGridMode.getItems().setAll("multiplier -m", "kpoints -k");
        this.planGridMode.getSelectionModel().select(0);
        this.planGridValue.setText("3");
        this.planGridValue.setPrefColumnCount(7);
        this.planTemps.setText("300:1001:100");
        this.planTemps.setPrefColumnCount(12);
        this.planLevels.setPromptText("1e19,1e20,-1e20  [1/cm^3]");
        this.planLevels.setPrefColumnCount(16);
        this.planVerbose.setText("2");
        this.planVerbose.setPrefColumnCount(3);
        this.planWorkers.setText("4");
        this.planWorkers.setPrefColumnCount(3);
        this.planQuantity.setText("S");
        this.planQuantity.setPrefColumnCount(6);
        this.planComponents.setText("xx, zz");
        this.planComponents.setPrefColumnCount(9);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("directory:"), this.planDirectory);
        grid.addRow(1, new Label("grid:"), new HBox(6, this.planGridMode,
                this.planGridValue), new Label("temperatures:"), this.planTemps);
        grid.addRow(2, new Label("-v:"), this.planVerbose, new Label("-n:"),
                this.planWorkers);
        grid.addRow(3, new Label("steps:"), new HBox(10, this.planDope,
                this.planPlot, this.planPlotBands), new Label("doping levels:"),
                this.planLevels);
        grid.addRow(4, new Label("plot quantity:"), this.planQuantity,
                new Label("components:"), this.planComponents);
        HBox buttons = new HBox(8, this.planLiznsb, this.planQe, this.planBuild,
                this.planCopy);
        this.planSteps.setEditable(false);
        this.planSteps.setPrefRowCount(14);
        this.planNotes.setEditable(false);
        this.planNotes.setPrefRowCount(7);
        this.planLiznsb.setOnAction(event -> showPlan(
                BoltzTrap2Btp2Plan.build(BoltzTrap2Btp2Plan.liznsbPreset()),
                "LiZnSb wiki-tutorial preset (byte-pinned by the tested plan)"));
        this.planQe.setOnAction(event -> {
            String dir = this.planDirectory.getText().trim();
            if (dir.isEmpty()) {
                this.planStatus.setText("type the QE outdir first - the preset"
                        + " takes it verbatim as the interpolate positional.");
                return;
            }
            showPlan(BoltzTrap2Btp2Plan.build(BoltzTrap2Btp2Plan.qePreset(dir)),
                    "QE-outdir preset (-m 20, -vv, -n 4, 300:1001:100)");
        });
        this.planBuild.setOnAction(event -> showPlan(
                BoltzTrap2Btp2Plan.build(requestFromFields()),
                "built from the PLAN fields above"));
        this.planCopy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(this.planSteps.getText());
            Clipboard.getSystemClipboard().setContent(content);
            this.planStatus.setText("steps copied to the clipboard - still"
                    + " PREVIEW lines; QuantumForge will not run them for you.");
        });
        return new VBox(6, grid, buttons, this.planSteps, this.planNotes,
                this.planStatus);
    }

    /** Assemble the Request from the PLAN fields (never silently defaulted). */
    private Request requestFromFields() {
        Request request = new Request()
                .directory(this.planDirectory.getText().trim())
                .temperature(this.planTemps.getText().trim());
        try {
            request.verbose(Integer.parseInt(this.planVerbose.getText().trim()));
        } catch (NumberFormatException problem) {
            request.verbose(-1); // let validation state the refusal verbatim
        }
        try {
            request.nworkers(Integer.parseInt(this.planWorkers.getText().trim()));
        } catch (NumberFormatException problem) {
            request.nworkers(-1);
        }
        try {
            if (this.planGridMode.getSelectionModel().getSelectedIndex() == 0) {
                request.multiplier(Integer.valueOf(
                        Integer.parseInt(this.planGridValue.getText().trim())));
            } else {
                request.kpoints(Integer.valueOf(
                        Integer.parseInt(this.planGridValue.getText().trim())));
            }
        } catch (NumberFormatException problem) {
            request.multiplier(Integer.valueOf(0)); // validation refuses 0
        }
        if (this.planDope.isSelected()) {
            request.dope(true).dopingLevels(this.planLevels.getText().trim());
        }
        if (this.planPlot.isSelected()) {
            request.plot(true).quantity(this.planQuantity.getText().trim());
            for (String component : this.planComponents.getText().split(",")) {
                if (!component.trim().isEmpty()) {
                    request.component(component.trim());
                }
            }
        }
        if (this.planPlotBands.isSelected()) {
            request.plotBands(true)
                    .kpathVertex("0.0", "0.0", "0.0")
                    .kpathVertex("0.5", "0.0", "0.0")
                    .kpathVertex("0.5", "0.5", "0.0");
        }
        return request;
    }

    /** Show a built plan: steps + warnings/notes, or the counted refusal. */
    private void showPlan(OperationResult<Plan> result, String label) {
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            this.planSteps.setText("");
            this.planNotes.setText("");
            this.planStatus.setText(label + " - REFUSED (stated honestly): "
                    + result.getCode() + ": " + result.getMessage());
            return;
        }
        Plan plan = result.getValue().get();
        this.planSteps.setText(String.join("\n", plan.getSteps()));
        StringBuilder notes = new StringBuilder();
        if (!plan.getWarnings().isEmpty()) {
            notes.append("WARNINGS:\n");
            for (String warning : plan.getWarnings()) {
                notes.append("- ").append(warning).append('\n');
            }
            notes.append('\n');
        }
        notes.append("NOTES:\n");
        for (String note : plan.getNotes()) {
            notes.append("- ").append(note).append('\n');
        }
        this.planNotes.setText(notes.toString());
        this.planStatus.setText(label + " - " + plan.getSteps().size()
                + " preview step(s); QuantumForge NEVER runs them.");
    }

    private void showProblem(String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("BoltzTraP2 studio");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }
}
