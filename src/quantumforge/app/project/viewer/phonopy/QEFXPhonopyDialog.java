/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.phonopy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import quantumforge.com.math.ChartGeometry;
import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyBandYaml;
import quantumforge.run.parser.QEPhonopyBandYaml.BandYaml;
import quantumforge.run.parser.QEPhonopyBorn;
import quantumforge.run.parser.QEPhonopyForceConstants;
import quantumforge.run.parser.QEPhonopyGruneisenYaml;
import quantumforge.run.parser.QEPhonopyQeBorn;
import quantumforge.run.parser.QEPhonopyQ2rFc;
import quantumforge.run.parser.QEPhonopyQ2rPlan;
import quantumforge.run.parser.QEPhonopyBandYaml.QRow;
import quantumforge.run.parser.QEPhonopyBandYaml.Segment;
import quantumforge.run.parser.QEPhonopyDos;
import quantumforge.run.parser.QEPhonopyDos.DosTable;
import quantumforge.run.parser.QEPhonopyPlan;
import quantumforge.run.parser.QEPhonopyThermalYaml;
import quantumforge.run.parser.QEPhonopyThermalYaml.ThermalRow;
import quantumforge.run.parser.QEPhonopyThermalYaml.ThermalYaml;

/**
 * Phonopy band/DOS studio (Roadmap: phonopy auxiliary-software integration).
 * Three cards, all pinned to upstream artifacts (phonopy commit 3a3e0f09):
 *
 * <p><b>WATCH (LIVE)</b> - pick a phonopy work directory once; a two-second
 * Timeline re-reads the pinned artifacts the moment they appear or grow:
 * {@code band.yaml} (band structure drawn segment by segment as the LAST
 * paths land), {@code total_dos.dat} / {@code partial_dos.dat} /
 * {@code projected_dos.dat} (DOS curves redrawing row by row), and
 * {@code thermal_properties.yaml} (F/S/Cv/E against T). Signature polling
 * re-parses only changed files; the parsers' last-line partial-hold rule
 * keeps a mid-write row off screen (and SAYS how many rows are held back);
 * a file that has not landed yet renders an explicit "waiting" state, never
 * a fake flat line. {@code FORCE_CONSTANTS} (tensor census card) and
 * {@code BORN} (NAC card: dielectric + Z* verbatim, batch 169) are parsed
 * the moment they land too; {@code gruneisen.yaml} /
 * {@code gruneisen_mesh.yaml} (batch 170) draw a LIVE gamma chart
 * (gamma-vs-distance segment lines, or gamma-vs-frequency scatter for the
 * mesh, the Gamma-divergence caveat named on the caption) as
 * phonopy-gruneisen writes them; any {@code *.fc} file (batch 171, the
 * q2r.x {@code flfrc} name convention) is scanned per poll and parsed
 * through {@link QEPhonopyQ2rFc} - q2r.x writes the file block by block,
 * so trailing incomplete blocks are held back and COUNTED while it is
 * still running; {@code band.hdf5} / {@code mesh.hdf5} /
 * {@code phonopy.yaml} / {@code FORCE_SETS} stay NAMED as present/absent
 * (binary/input artifacts are enumerated, not parsed). The timer always
 * stops when the dialog closes.</p>
 *
 * <p><b>OPEN</b> - chart one finished artifact picked explicitly
 * (band.yaml / any DOS table / thermal_properties.yaml / BORN /
 * FORCE_CONSTANTS / either gruneisen yaml), or EXTRACT a BORN file from a
 * {@code ph.x} output of an
 * {@code epsil = .true.} run (the raw half of upstream's
 * {@code phonopy-qe-born}, nat from the pw input asserted against the ph.x
 * print; RAW values, NOT symmetrized - stated on the card) with a
 * consent-gated one-file BORN save. Batch 171 adds the Experimental
 * q2r.x route: open a {@code .fc} flfrc file for the census card (blocks
 * parsed / expected, order census, unit note) - when its NAC block is
 * present the same BORN preview is filled from it (raw, upstream's
 * make_born_q2r.py shape) - and PREVIEW the doc-pinned q2r flow commands
 * (the SCF &rarr; ldisp ph.x &rarr; epsil=.false. Gamma swap &rarr;
 * q2r.x &rarr; make_fc_q2r.py &rarr; phonopy band sequence) for copy.</p>
 *
 * <p><b>BUILD</b> - fill-in phonopy settings (DIM, band path with labels,
 * band_connection, BAND_POINTS, MESH, DOS/PDOS, TPROP range, NAC toggle)
 * and preview the verbatim conf text plus the QE&rarr;phonopy run flow (per
 * doc/qe.md, with the doc's own pw.x &rarr; ph.x &rarr; phonopy-qe-born BORN
 * steps when NAC is on, and the v4 {@code --nac} removal note stated) and
 * the user's own {@code phonopy-load --band "..."}
 * --band_labels "..." --band_connection -p} one-liner. Copy, or save the
 * conf into a chosen folder (explicit consent, one file). QuantumForge does
 * not bundle phonopy: this card PREVIEWS commands, never executes them.</p>
 *
 * <p>Units doctrine: frequencies THz / temperatures K / free-energy kJ/mol
 * etc. come ONLY from the parsers' stated unit notes (band/DOS tables) or
 * from the file's own unit: block (thermal yaml). Imaginary modes (negative
 * frequencies) are named on the chart, never hidden. The viewer reads only
 * through the tested parsers; it writes nothing except the BUILD card's
 * explicitly consented conf file and the OPEN card's consented BORN file.</p>
 */
public final class QEFXPhonopyDialog extends Dialog<Void> {

    private static final String[] WATCHED = {
            "band.yaml", "total_dos.dat", "partial_dos.dat", "projected_dos.dat",
            "thermal_properties.yaml", "FORCE_CONSTANTS", "BORN",
            "gruneisen.yaml", "gruneisen_mesh.yaml"};
    /** How many '*.fc' q2r flfrc files one poll follows (bounded, name-sorted). */
    private static final int MAX_WATCHED_FC = 4;
    private static final String[] ENUMERATED = {
            "band.hdf5", "mesh.hdf5", "qpoints.hdf5", "phonopy.yaml",
            "phonopy_disp.yaml", "FORCE_SETS"};

    // ---------- WATCH (live) ----------
    private final Label dirLabel = new Label("(no run directory chosen)");
    private final Label liveBadge = new Label("NOT LIVE");
    private final ListView<String> artifactList = new ListView<>();
    private final Label statusLabel = new Label();
    private final Label heldLabel = new Label();
    private File runDir;
    private final Map<String, long[]> signatures = new HashMap<>();
    private final Map<String, Object> artifactCache = new HashMap<>();
    private final Map<String, String> refusalCache = new HashMap<>();
    private final Timeline timer;

    // ---------- OPEN ----------
    private final Label openLabel = new Label("(no file picked)");
    private final TextArea bornPreview = new TextArea();
    private final TextArea flowPreview = new TextArea();
    private QEPhonopyQeBorn.QeBornExtract currentQeBorn;

    // ---------- shared chart ----------
    private final Canvas canvas = new Canvas(760, 480);
    private final Label chartCaption = new Label();
    private final ComboBox<String> quantityCombo = new ComboBox<>();
    private final ComboBox<String> dosModeCombo = new ComboBox<>();
    private Object currentProduct;
    private String currentKind = "";

    // ---------- BUILD ----------
    private final TextField cellField = new TextField("pw.in");
    private final TextField confField = new TextField("band.conf");
    private final TextField dimField = new TextField("2 2 2");
    private final TextArea verticesArea = new TextArea();
    private final TextField labelsField = new TextField("G X R M G R");
    private final CheckBox connectionBox = new CheckBox("BAND_CONNECTION");
    private final TextField bandPointsField = new TextField("101");
    private final CheckBox dosBox = new CheckBox("DOS");
    private final TextField meshField = new TextField("8 8 8");
    private final TextField pdosField = new TextField();
    private final CheckBox tpropBox = new CheckBox("TPROP");
    private final CheckBox nacBox = new CheckBox(
            "NAC (BORN file: LO-TO correction)");
    private final TextField tminField = new TextField("0");
    private final TextField tmaxField = new TextField("1000");
    private final TextField tstepField = new TextField("10");
    private final TextArea confPreview = new TextArea();
    private final TextArea commandsPreview = new TextArea();
    private final Label planStatus = new Label();
    private QEPhonopyPlan.Plan currentPlan;

    public QEFXPhonopyDialog() {
        setTitle("Phonopy band / DOS studio");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        this.timer = new Timeline(new KeyFrame(Duration.seconds(2), e -> poll()));
        this.timer.setCycleCount(Timeline.INDEFINITE);
        setOnCloseRequest(e -> this.timer.stop());

        TabPane tabs = new TabPane();
        Tab watchTab = new Tab("WATCH run (live)", buildWatchPane());
        Tab openTab = new Tab("OPEN artifact", buildOpenPane());
        Tab buildTab = new Tab("BUILD conf + commands", buildBuildPane());
        watchTab.setClosable(false);
        openTab.setClosable(false);
        buildTab.setClosable(false);
        tabs.getTabs().addAll(watchTab, openTab, buildTab);

        VBox chartColumn = new VBox(6,
                new HBox(8, new Label("thermal quantity:"), this.quantityCombo,
                        new Label("DOS series:"), this.dosModeCombo),
                this.canvas, this.chartCaption);
        chartColumn.setPadding(new Insets(8));
        HBox root = new HBox(10, tabs, chartColumn);
        root.setPadding(new Insets(10));
        tabs.setTabMinWidth(120);
        HBox.setHgrow(chartColumn, Priority.ALWAYS);
        tabs.setPrefWidth(560);
        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        getDialogPane().setContent(scroller);
        getDialogPane().setPrefSize(1380, 760);

        this.quantityCombo.getItems().addAll(
                "free_energy", "entropy", "heat_capacity", "energy");
        this.quantityCombo.getSelectionModel().select("heat_capacity");
        this.quantityCombo.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> redraw());
        this.dosModeCombo.getItems().addAll("interleaved", "stacked sum");
        this.dosModeCombo.getSelectionModel().select("interleaved");
        this.dosModeCombo.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> redraw());
        this.artifactList.getSelectionModel().selectedItemProperty()
                .addListener((o, a, b) -> renderSelectedArtifact());

        setResultConverter(b -> null);
        redrawEmpty("Pick a run directory (WATCH), an artifact file (OPEN), or"
                + " build a plan (BUILD) - nothing here invents data.");
        buildPlanPreview();
    }

    // ============================ WATCH pane ============================
    private javafx.scene.Node buildWatchPane() {
        Button dirButton = new Button("Choose phonopy run directory ...");
        dirButton.setMaxWidth(Double.MAX_VALUE);
        Button pause = new Button("Pause live polling");
        Label help = new Label("Watched artifacts (parsed live): band.yaml,"
                + " total_dos.dat, partial_dos.dat, projected_dos.dat,"
                + " thermal_properties.yaml. Enumerated only: band.hdf5,"
                + " mesh.hdf5, phonopy.yaml, phonopy_disp.yaml, FORCE_SETS,"
                + " FORCE_SETS.");
        help.setWrapText(true);
        this.statusLabel.setWrapText(true);
        this.heldLabel.setWrapText(true);
        VBox pane = new VBox(8, dirButton, this.dirLabel, new HBox(8, this.liveBadge,
                pause), help, new Label("artifacts:"), this.artifactList,
                this.statusLabel, this.heldLabel);
        pane.setPadding(new Insets(8));
        VBox.setVgrow(this.artifactList, Priority.ALWAYS);
        dirButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Pick the phonopy work directory");
            File picked = chooser.showDialog(getOwner());
            if (picked != null) {
                this.runDir = picked;
                this.dirLabel.setText(picked.getAbsolutePath());
                this.signatures.clear();
                this.artifactCache.clear();
                this.refusalCache.clear();
                this.liveBadge.setText("LIVE (2 s)");
                this.liveBadge.setStyle("-fx-text-fill: #1a6b1a; -fx-font-weight: bold;");
                this.timer.stop();
                this.timer.playFromStart();
                poll();
            }
        });
        pause.setOnAction(e -> {
            if (this.timer.getStatus() == Timeline.Status.RUNNING) {
                this.timer.stop();
                this.liveBadge.setText("PAUSED");
                this.liveBadge.setStyle("-fx-text-fill: #a06a10;");
            } else if (this.runDir != null) {
                this.timer.play();
                this.liveBadge.setText("LIVE (2 s)");
                this.liveBadge.setStyle("-fx-text-fill: #1a6b1a; -fx-font-weight: bold;");
            }
        });
        return pane;
    }

    private void poll() {
        if (this.runDir == null || !this.runDir.isDirectory()) {
            return;
        }
        List<String> rows = new ArrayList<>();
        String selected = this.artifactList.getSelectionModel().getSelectedItem();
        for (String name : WATCHED) {
            File file = new File(this.runDir, name);
            if (!file.isFile()) {
                rows.add("[waiting] " + name);
                this.signatures.remove(name);
                this.artifactCache.remove(name);
                this.refusalCache.remove(name);
                continue;
            }
            pollOne(file, name, rows);
        }
        // batch 171: q2r.x flfrc files keep the user-chosen name from q2r.in
        // ('.fc' by convention) - scan the directory. q2r.x writes blocks
        // progressively, so the parser holds a partial tail block back.
        List<String> fcNames = new ArrayList<>();
        File[] fcFiles = this.runDir.listFiles((dir, n) -> n.endsWith(".fc"));
        if (fcFiles != null) {
            Arrays.sort(fcFiles, (a, b) -> a.getName().compareTo(b.getName()));
            for (File fcFile : fcFiles) {
                if (fcNames.size() >= MAX_WATCHED_FC || !fcFile.isFile()) {
                    continue;
                }
                fcNames.add(fcFile.getName());
                pollOne(fcFile, fcFile.getName(), rows);
            }
        }
        if (fcNames.isEmpty()) {
            rows.add("[waiting] *.fc (q2r.x flfrc name per q2r.in)");
        }
        // drop stale .fc cache entries for files that vanished between polls
        this.signatures.keySet().removeIf(n -> n.endsWith(".fc") && !fcNames.contains(n));
        this.artifactCache.keySet().removeIf(n -> n.endsWith(".fc") && !fcNames.contains(n));
        this.refusalCache.keySet().removeIf(n -> n.endsWith(".fc") && !fcNames.contains(n));
        int enumerated = 0;
        StringBuilder names = new StringBuilder();
        for (String name : ENUMERATED) {
            if (new File(this.runDir, name).isFile()) {
                enumerated++;
                if (names.length() < 120) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(name);
                }
            }
        }
        rows.add("[present, enumerated-not-parsed] " + enumerated + "/"
                + ENUMERATED.length + (enumerated > 0 ? ": " + names : ""));
        this.artifactList.getItems().setAll(rows);
        if (selected != null) {
            this.artifactList.getSelectionModel().select(selected);
        }
        if (this.artifactList.getSelectionModel().getSelectedIndex() < 0) {
            this.artifactList.getSelectionModel().select(0);
        }
        this.statusLabel.setText("poll: " + java.time.LocalTime.now()
                .withNano(0) + " - only [changed] files are re-parsed; a '[waiting]'"
                + " artifact renders its waiting state, never a placeholder curve.");
        renderSelectedArtifact();
    }

    /** Signature check + conditional reparse for one present artifact file. */
    private void pollOne(File file, String name, List<String> rows) {
        long[] signature = new long[] {file.length(), file.lastModified()};
        long[] previous = this.signatures.get(name);
        boolean changed = previous == null || previous[0] != signature[0]
                || previous[1] != signature[1];
        rows.add((changed ? "[changed] " : "[fresh] ") + name + " ("
                + file.length() + " B)");
        if (changed) {
            this.signatures.put(name, signature);
            reparse(file.toPath(), name);
        }
    }

    private void reparse(Path file, String name) {
        if ("band.yaml".equals(name)) {
            OperationResult<BandYaml> result = QEPhonopyBandYaml.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        } else if ("thermal_properties.yaml".equals(name)) {
            OperationResult<ThermalYaml> result = QEPhonopyThermalYaml.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        } else if ("BORN".equals(name)) {
            OperationResult<QEPhonopyBorn.BornFile> result = QEPhonopyBorn.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        } else if ("gruneisen.yaml".equals(name) || "gruneisen_mesh.yaml".equals(name)) {
            OperationResult<QEPhonopyGruneisenYaml.GruneisenYaml> result =
                    QEPhonopyGruneisenYaml.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        } else if ("FORCE_CONSTANTS".equals(name)) {
            OperationResult<QEPhonopyForceConstants.ForceConstants> result =
                    QEPhonopyForceConstants.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        } else if (name.endsWith(".fc")) {
            OperationResult<QEPhonopyQ2rFc.Q2rFc> result = QEPhonopyQ2rFc.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        } else {
            OperationResult<DosTable> result = QEPhonopyDos.parse(file);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.artifactCache.put(name, result.getValue().orElseThrow());
                this.refusalCache.remove(name);
            } else {
                this.refusalCache.put(name, "[" + result.getCode() + "] "
                        + result.getMessage());
            }
        }
    }

    private void renderSelectedArtifact() {
        String row = this.artifactList.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        String name = row.replace("[changed] ", "").replace("[fresh] ", "")
                .replace("[waiting] ", "");
        if (row.startsWith("[present")) {
            redrawEmpty("binary / input artifacts are enumerated, not parsed: "
                    + "band.hdf5, phonopy.yaml, FORCE_SETS ... (band.yaml is the"
                    + " chartable sibling when it lands).");
            this.currentProduct = null;
            this.heldLabel.setText("");
            return;
        }
        int cut = name.indexOf(" (");
        if (cut > 0) {
            name = name.substring(0, cut);
        }
        this.currentKind = name;
        Object product = this.artifactCache.get(name);
        String refusal = this.refusalCache.get(name);
        if (row.startsWith("[waiting]")) {
            this.currentProduct = null;
            redrawEmpty("waiting for " + name + " - the run has not written it yet.");
            this.heldLabel.setText("");
            return;
        }
        if (product == null) {
            this.currentProduct = null;
            redrawEmpty("no chartable rows yet for " + name);
            this.heldLabel.setText(refusal == null ? "" : refusal);
            return;
        }
        this.currentProduct = product;
        this.heldLabel.setText(refusal == null ? "" : refusal);
        redraw();
    }

    // ============================ OPEN pane ============================
    private javafx.scene.Node buildOpenPane() {
        Button bandButton = new Button("Open band.yaml ...");
        bandButton.setMaxWidth(Double.MAX_VALUE);
        Button dosButton = new Button("Open a DOS table (total/partial/projected) ...");
        dosButton.setMaxWidth(Double.MAX_VALUE);
        Button tpropButton = new Button("Open thermal_properties.yaml ...");
        tpropButton.setMaxWidth(Double.MAX_VALUE);
        Button gruButton = new Button("Open gruneisen.yaml / gruneisen_mesh.yaml ...");
        gruButton.setMaxWidth(Double.MAX_VALUE);
        Button bornButton = new Button("Open BORN (NAC) ...");
        bornButton.setMaxWidth(Double.MAX_VALUE);
        Button fcButton = new Button("Open FORCE_CONSTANTS ...");
        fcButton.setMaxWidth(Double.MAX_VALUE);
        TextField natomField = new TextField();
        natomField.setPromptText("nat from the pw input (phonopy-qe-born's check)");
        natomField.setPrefColumnCount(6);
        Button qebornButton = new Button(
                "Extract BORN from a ph.x output (epsil run) ...");
        qebornButton.setMaxWidth(Double.MAX_VALUE);
        Button q2rButton = new Button(
                "Open a q2r .fc force-constants file (Experimental PH_Q2R) ...");
        q2rButton.setMaxWidth(Double.MAX_VALUE);
        this.bornPreview.setEditable(false);
        this.bornPreview.setPrefRowCount(6);
        Button saveBorn = new Button("Save the preview as BORN (a folder I choose) ...");
        saveBorn.setMaxWidth(Double.MAX_VALUE);
        Button q2rFlowButton = new Button(
                "PREVIEW the q2r.x flow commands (doc's pinned NaCl example) ...");
        q2rFlowButton.setMaxWidth(Double.MAX_VALUE);
        this.flowPreview.setEditable(false);
        this.flowPreview.setPrefRowCount(10);
        this.flowPreview.setWrapText(true);
        Button copyFlow = new Button("Copy flow commands");
        this.openLabel.setWrapText(true);
        VBox pane = new VBox(8, bandButton, dosButton, tpropButton,
                gruButton, q2rButton,
                new HBox(8, bornButton, fcButton),
                new HBox(8, new Label("nat:"), natomField, qebornButton),
                new Label("BORN PREVIEW (raw values, NOT symmetrized - upstream's"
                        + " phonopy-qe-born symmetrizes by default):"),
                this.bornPreview,
                saveBorn,
                q2rFlowButton,
                new Label("q2r.x flow PREVIEW (Experimental route: SCF -> ldisp"
                        + " ph.x -> epsil=.false. Gamma swap -> q2r.x ->"
                        + " make_fc_q2r.py -> phonopy band; QuantumForge never"
                        + " runs these lines):"),
                this.flowPreview,
                copyFlow,
                this.openLabel);
        pane.setPadding(new Insets(8));
        bandButton.setOnAction(e -> {
            File picked = pickFile("band.yaml");
            if (picked != null) {
                OperationResult<BandYaml> result = QEPhonopyBandYaml.parse(
                        picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    this.currentProduct = result.getValue().orElseThrow();
                    this.currentKind = "band.yaml";
                    this.openLabel.setText(picked.getAbsolutePath()
                            + "\n[" + result.getCode() + "] " + result.getMessage());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        dosButton.setOnAction(e -> {
            File picked = pickFile("total_dos.dat / partial_dos.dat / projected_dos.dat");
            if (picked != null) {
                OperationResult<DosTable> result = QEPhonopyDos.parse(picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    this.currentProduct = result.getValue().orElseThrow();
                    this.currentKind = picked.getName();
                    this.openLabel.setText(picked.getAbsolutePath()
                            + "\n[" + result.getCode() + "] " + result.getMessage());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        tpropButton.setOnAction(e -> {
            File picked = pickFile("thermal_properties.yaml");
            if (picked != null) {
                OperationResult<ThermalYaml> result = QEPhonopyThermalYaml.parse(
                        picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    this.currentProduct = result.getValue().orElseThrow();
                    this.currentKind = "thermal_properties.yaml";
                    this.openLabel.setText(picked.getAbsolutePath()
                            + "\n[" + result.getCode() + "] " + result.getMessage());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        bornButton.setOnAction(e -> {
            File picked = pickFile("BORN");
            if (picked != null) {
                OperationResult<QEPhonopyBorn.BornFile> result =
                        QEPhonopyBorn.parse(picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    this.currentProduct = result.getValue().orElseThrow();
                    this.currentKind = "BORN";
                    this.openLabel.setText(picked.getAbsolutePath()
                            + "\n[" + result.getCode() + "] " + result.getMessage());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        fcButton.setOnAction(e -> {
            File picked = pickFile("FORCE_CONSTANTS");
            if (picked != null) {
                OperationResult<QEPhonopyForceConstants.ForceConstants> result =
                        QEPhonopyForceConstants.parse(picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    this.currentProduct = result.getValue().orElseThrow();
                    this.currentKind = "FORCE_CONSTANTS";
                    this.openLabel.setText(picked.getAbsolutePath()
                            + "\n[" + result.getCode() + "] " + result.getMessage());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        gruButton.setOnAction(e -> {
            File picked = pickFile("gruneisen.yaml / gruneisen_mesh.yaml");
            if (picked != null) {
                OperationResult<QEPhonopyGruneisenYaml.GruneisenYaml> result =
                        QEPhonopyGruneisenYaml.parse(picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    this.currentProduct = result.getValue().orElseThrow();
                    this.currentKind = picked.getName();
                    this.openLabel.setText(picked.getAbsolutePath()
                            + "\n[" + result.getCode() + "] " + result.getMessage());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        qebornButton.setOnAction(e -> {
            File picked = pickFile("ph.x output of an epsil=.true. run");
            if (picked == null) {
                return;
            }
            int natom;
            try {
                natom = Integer.parseInt(natomField.getText().trim());
            } catch (NumberFormatException ex) {
                this.openLabel.setText("nat must be a plain integer (from the pw"
                        + " input's nat card) - upstream's phonopy-qe-born takes the"
                        + " same count from the pw input and asserts against the"
                        + " ph.x print.");
                return;
            }
            OperationResult<QEPhonopyQeBorn.QeBornExtract> result =
                    QEPhonopyQeBorn.parse(picked.toPath(), natom);
            if (result.isSuccess() && result.getValue().isPresent()) {
                this.currentQeBorn = result.getValue().orElseThrow();
                this.bornPreview.setText(this.currentQeBorn.toBornText());
                StringBuilder notes = new StringBuilder(picked.getAbsolutePath());
                notes.append("\n[").append(result.getCode()).append("] ")
                        .append(result.getMessage());
                for (String note : this.currentQeBorn.getNotes()) {
                    notes.append("\n- ").append(note);
                }
                this.openLabel.setText(notes.toString());
            } else {
                this.currentQeBorn = null;
                this.bornPreview.setText("");
                this.openLabel.setText("[" + result.getCode() + "] "
                        + result.getMessage());
            }
        });
        q2rButton.setOnAction(e -> {
            File picked = pickFile("q2r.x flfrc output (e.g. NaCl.fc)");
            if (picked != null) {
                OperationResult<QEPhonopyQ2rFc.Q2rFc> result =
                        QEPhonopyQ2rFc.parse(picked.toPath());
                if (result.isSuccess() && result.getValue().isPresent()) {
                    QEPhonopyQ2rFc.Q2rFc fc = result.getValue().orElseThrow();
                    this.currentProduct = fc;
                    this.currentKind = picked.getName();
                    StringBuilder label = new StringBuilder(picked.getAbsolutePath());
                    label.append("\n[").append(result.getCode()).append("] ")
                            .append(result.getMessage());
                    if (fc.hasNac()) {
                        this.bornPreview.setText(fc.toBornText());
                        label.append("\nNAC block present -> the BORN preview above"
                                + " was filled from it (raw values,"
                                + " make_born_q2r.py's print shape, NOT"
                                + " symmetrized).");
                    }
                    this.openLabel.setText(label.toString());
                    redraw();
                } else {
                    this.openLabel.setText("[" + result.getCode() + "] "
                            + result.getMessage());
                }
            }
        });
        q2rFlowButton.setOnAction(e -> {
            OperationResult<QEPhonopyQ2rPlan.Plan> result =
                    QEPhonopyQ2rPlan.build(QEPhonopyQ2rPlan.naclPreset());
            if (result.isSuccess() && result.getValue().isPresent()) {
                QEPhonopyQ2rPlan.Plan plan = result.getValue().orElseThrow();
                StringBuilder text = new StringBuilder();
                for (String step : plan.getSteps()) {
                    text.append(step).append('\n');
                }
                this.flowPreview.setText(text.toString());
                StringBuilder label = new StringBuilder("[" + result.getCode()
                        + "] " + result.getMessage());
                for (String warning : plan.getWarnings()) {
                    label.append("\nWARNING: ").append(warning);
                }
                for (String note : plan.getNotes()) {
                    label.append("\nnote: ").append(note);
                }
                label.append("\n(preset: the doc's pinned NaCl example - adjust"
                        + " prefix / grid / masses in your own copy; PREVIEW only,"
                        + " nothing was run)");
                this.openLabel.setText(label.toString());
            } else {
                this.openLabel.setText("[" + result.getCode() + "] "
                        + result.getMessage());
            }
        });
        copyFlow.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(this.flowPreview.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            this.openLabel.setText("q2r flow commands copied to the clipboard.");
        });
        saveBorn.setOnAction(e -> saveBorn());
        return pane;
    }

    /** Consent-gated single-file write of the BORN preview. Nothing else is written. */
    private void saveBorn() {
        if (this.bornPreview.getText().isBlank()) {
            this.openLabel.setText("nothing to save: fill the BORN preview first"
                    + " (extract a ph.x epsil output, or open a q2r .fc whose NAC"
                    + " block is present).");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the folder for the BORN file");
        File dir = chooser.showDialog(getOwner());
        if (dir == null) {
            this.openLabel.setText("BORN save cancelled - nothing was written.");
            return;
        }
        File target = new File(dir, "BORN");
        try {
            Files.writeString(target.toPath(), this.bornPreview.getText());
            this.openLabel.setText("WROTE " + target.getAbsolutePath()
                    + " - exactly ONE file, with your folder consent. phonopy v4"
                    + " picks it up automatically from the run directory"
                    + " ('NAC params were read from \"BORN\".' in the doc's own"
                    + " log); pre-v4 needs --nac on the post-process command.");
        } catch (IOException ex) {
            this.openLabel.setText("BORN save FAILED: " + ex.getMessage()
                    + " (nothing else was touched)");
        }
    }

    private File pickFile(String description) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pick " + description);
        return chooser.showOpenDialog(getOwner());
    }

    // ============================ BUILD pane ============================
    private javafx.scene.Node buildBuildPane() {
        this.verticesArea.setText("0.0 0.0 0.0\n0.5 0.0 0.0\n0.5 0.5 0.5\n"
                + "0.5 0.5 0.0\n0.0 0.0 0.0\n0.5 0.5 0.5");
        this.verticesArea.setPrefRowCount(7);
        this.connectionBox.setSelected(true);
        this.confPreview.setEditable(false);
        this.confPreview.setPrefRowCount(12);
        this.commandsPreview.setEditable(false);
        this.commandsPreview.setPrefRowCount(8);
        this.commandsPreview.setWrapText(true);
        Button presetBcc = new Button("Preset: cubic BCC (your command #4)");
        Button presetClear = new Button("Clear path");
        Button build = new Button("Update preview");
        build.setDefaultButton(true);
        build.setMaxWidth(Double.MAX_VALUE);
        Button copyConf = new Button("Copy conf text");
        Button copyCommands = new Button("Copy command lines");
        Button saveConf = new Button("Save conf into a folder I choose ...");
        Label doctrine = new Label("PREVIEW ONLY: QuantumForge does not bundle"
                + " phonopy and never runs these lines for you - paste them into"
                + " your own terminal / job file.");
        doctrine.setWrapText(true);
        doctrine.setStyle("-fx-font-weight: bold;");
        this.planStatus.setWrapText(true);

        VBox form = new VBox(6,
                new HBox(8, new Label("cell file:"), this.cellField,
                        new Label("conf name:"), this.confField),
                new HBox(8, new Label("DIM (3 or 9 ints):"), this.dimField),
                new HBox(8, presetBcc, presetClear),
                new Label("band vertices, one per line 'x y z' "
                        + "(ints / decimals / fractions 1/2):"),
                this.verticesArea,
                new HBox(8, new Label("labels:"), this.labelsField,
                        this.connectionBox, new Label("BAND_POINTS:"),
                        this.bandPointsField),
                new HBox(8, this.dosBox, new Label("MESH:"), this.meshField,
                        new Label("PDOS:"), this.pdosField),
                new HBox(8, this.tpropBox, new Label("TMIN"), this.tminField,
                        new Label("TMAX"), this.tmaxField,
                        new Label("TSTEP"), this.tstepField),
                new HBox(8, this.nacBox),
                build, doctrine,
                new Label("setting-file PREVIEW (verbatim phonopy tag grammar):"),
                this.confPreview,
                new HBox(8, copyConf, copyCommands, saveConf),
                new Label("run flow (doc/qe.md) + phonopy-load one-liners:"),
                this.commandsPreview,
                this.planStatus);
        form.setPadding(new Insets(8));
        this.cellField.setPrefColumnCount(8);
        this.confField.setPrefColumnCount(9);
        this.dimField.setPrefColumnCount(14);
        this.labelsField.setPrefColumnCount(9);
        this.bandPointsField.setPrefColumnCount(4);
        this.meshField.setPrefColumnCount(6);
        this.pdosField.setPrefColumnCount(7);
        this.tminField.setPrefColumnCount(4);
        this.tmaxField.setPrefColumnCount(5);
        this.tstepField.setPrefColumnCount(4);

        presetBcc.setOnAction(e -> {
            this.dimField.setText("2 2 2");
            this.verticesArea.setText("0.0 0.0 0.0\n0.5 0.0 0.0\n0.5 0.5 0.5\n"
                    + "0.5 0.5 0.0\n0.0 0.0 0.0\n0.5 0.5 0.5");
            this.labelsField.setText("G X R M G R");
            this.connectionBox.setSelected(true);
            buildPlanPreview();
        });
        presetClear.setOnAction(e -> {
            this.verticesArea.setText("");
            this.labelsField.setText("");
            buildPlanPreview();
        });
        build.setOnAction(e -> buildPlanPreview());
        copyConf.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(this.confPreview.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            this.planStatus.setText("conf text copied to the clipboard.");
        });
        copyCommands.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(this.commandsPreview.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            this.planStatus.setText("command lines copied to the clipboard.");
        });
        saveConf.setOnAction(e -> saveConf());
        return form;
    }

    private void saveConf() {
        if (this.currentPlan == null) {
            this.planStatus.setText("Nothing valid to save - fix the plan first.");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Pick the folder for " + this.confField.getText().trim()
                + " (ONE file is written; nothing else is touched)");
        File dir = chooser.showDialog(getOwner());
        if (dir == null) {
            return;
        }
        Path target = dir.toPath().resolve(this.confField.getText().trim());
        try {
            Files.writeString(target, this.currentPlan.getConfText());
            this.planStatus.setText("wrote " + target.toAbsolutePath() + " ("
                    + Files.size(target) + " bytes). Nothing else was written;"
                    + " the commands were NOT executed.");
        } catch (IOException ex) {
            this.planStatus.setText("could not write " + target + ": "
                    + ex.getMessage());
        }
    }

    private void buildPlanPreview() {
        QEPhonopyPlan.Request request = new QEPhonopyPlan.Request()
                .cellFilename(this.cellField.getText().trim())
                .confName(this.confField.getText().trim());
        try {
            String[] parts = this.dimField.getText().trim().split("\\s+");
            int[] values = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i]);
            }
            request.supercellDim(values);
        } catch (NumberFormatException ex) {
            this.currentPlan = null;
            this.planStatus.setText("DIM entries must be plain integers.");
            this.confPreview.setText("");
            this.commandsPreview.setText("");
            return;
        }
        for (String line : this.verticesArea.getText().split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length != 3) {
                this.currentPlan = null;
                this.planStatus.setText("each vertex line needs exactly 3 tokens: '"
                        + line.trim() + "'");
                this.confPreview.setText("");
                this.commandsPreview.setText("");
                return;
            }
            request.bandVertex(tokens[0], tokens[1], tokens[2]);
        }
        String labelsText = this.labelsField.getText().trim();
        if (!labelsText.isEmpty()) {
            request.bandLabels(labelsText.split("\\s+"));
        }
        request.bandConnection(this.connectionBox.isSelected());
        try {
            request.bandPoints(Integer.parseInt(this.bandPointsField.getText().trim()));
        } catch (NumberFormatException ex) {
            request.bandPoints(-1); // let the builder enumerate the issue
        }
        if (this.dosBox.isSelected()) {
            try {
                String[] m = this.meshField.getText().trim().split("\\s+");
                request.mesh(Integer.parseInt(m[0]),
                        m.length > 1 ? Integer.parseInt(m[1]) : -1,
                        m.length > 2 ? Integer.parseInt(m[2]) : -1);
            } catch (NumberFormatException ex) {
                request.mesh(-1, -1, -1); // enumerated by the builder
            }
            request.dos(true);
        }
        String pdos = this.pdosField.getText().trim();
        if (!pdos.isEmpty()) {
            String[] tokens = pdos.split("\\s+");
            if (tokens.length > 0 && tokens[0].isEmpty()) {
                tokens = new String[0];
            }
            if (tokens.length == 1 && tokens[0].contains(",")) {
                request.pdos(tokens[0].split(",", -1));
            } else {
                request.pdos(tokens);
            }
        }
        if (this.tpropBox.isSelected()) {
            try {
                request.tprop(Double.parseDouble(this.tminField.getText().trim()),
                        Double.parseDouble(this.tmaxField.getText().trim()),
                        Double.parseDouble(this.tstepField.getText().trim()));
            } catch (NumberFormatException ex) {
                request.tprop(0, -1, 0); // enumerated by the builder
            }
        }
        request.nac(this.nacBox.isSelected());
        OperationResult<QEPhonopyPlan.Plan> result = QEPhonopyPlan.build(request);
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            this.currentPlan = null;
            this.planStatus.setText("[" + result.getCode() + "] " + result.getMessage());
            this.confPreview.setText("");
            this.commandsPreview.setText("");
            return;
        }
        this.currentPlan = result.getValue().orElseThrow();
        this.confPreview.setText(this.currentPlan.getConfText());
        StringBuilder commands = new StringBuilder();
        commands.append("# four-step QE -> phonopy flow (doc/qe.md):\n");
        for (String command : this.currentPlan.getFlowCommands()) {
            commands.append(command).append('\n');
        }
        commands.append("\n# phonopy-load one-liners (your command #4 shape):\n");
        for (String command : this.currentPlan.getLoadCommands()) {
            commands.append(command).append('\n');
        }
        this.commandsPreview.setText(commands.toString());
        StringBuilder status = new StringBuilder("[" + result.getCode() + "] "
                + result.getMessage());
        for (String warning : this.currentPlan.getWarnings()) {
            status.append("\nWARNING: ").append(warning);
        }
        for (String note : this.currentPlan.getNotes()) {
            status.append("\nnote: ").append(note);
        }
        this.planStatus.setText(status.toString());
    }

    // ============================ chart ============================
    private void redrawEmpty(String message) {
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
        gc.setFill(Color.DARKGRAY);
        gc.fillText(message, 20, 30);
        this.chartCaption.setText("");
    }

    private void redraw() {
        Object product = this.currentProduct;
        if (product instanceof BandYaml) {
            drawBand((BandYaml) product);
        } else if (product instanceof DosTable) {
            drawDos((DosTable) product);
        } else if (product instanceof ThermalYaml) {
            drawThermal((ThermalYaml) product);
        } else if (product instanceof QEPhonopyGruneisenYaml.GruneisenYaml) {
            drawGruneisen((QEPhonopyGruneisenYaml.GruneisenYaml) product);
        } else if (product instanceof QEPhonopyBorn.BornFile) {
            drawBorn((QEPhonopyBorn.BornFile) product);
        } else if (product instanceof QEPhonopyForceConstants.ForceConstants) {
            drawForceConstants((QEPhonopyForceConstants.ForceConstants) product);
        } else if (product instanceof QEPhonopyQ2rFc.Q2rFc) {
            drawQ2rFc((QEPhonopyQ2rFc.Q2rFc) product);
        } else {
            redrawEmpty("no chartable product selected.");
        }
    }

    /** gamma chart: BAND mode gamma-vs-distance, MESH mode gamma-vs-frequency. */
    private void drawGruneisen(QEPhonopyGruneisenYaml.GruneisenYaml yaml) {
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        double w = this.canvas.getWidth();
        double h = this.canvas.getHeight();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);
        double leftPad = 64;
        double bottomPad = 56;
        double topPad = 18;
        double rightPad = 18;
        java.util.List<QEPhonopyGruneisenYaml.GammaQ> rows = yaml.flatRows();
        if (rows.isEmpty()) {
            redrawEmpty("no complete q rows yet (live write)");
            return;
        }
        boolean bandMode = yaml.getMode() == QEPhonopyGruneisenYaml.Mode.BAND;
        // x: cumulative distance (band) or frequency (mesh); y: gamma
        int bands = yaml.getBandCount();
        double[][] xs = new double[bands][rows.size()];
        double[][] ys = new double[bands][rows.size()];
        double xLo = Double.POSITIVE_INFINITY;
        double xHi = Double.NEGATIVE_INFINITY;
        double gLo = Double.POSITIVE_INFINITY;
        double gHi = Double.NEGATIVE_INFINITY;
        double cumDist = 0.0;
        Double lastDistance = null;
        for (int r = 0; r < rows.size(); r++) {
            QEPhonopyGruneisenYaml.GammaQ row = rows.get(r);
            double x;
            if (bandMode) {
                double d = row.getDistance() == null ? 0.0 : row.getDistance();
                if (lastDistance != null && d < lastDistance) {
                    cumDist = lastDistance; // path break: monotonic continuation
                }
                x = Math.max(cumDist, d);
                cumDist = x;
                lastDistance = d;
            } else {
                x = row.getBands().isEmpty() ? 0.0
                        : row.getBands().get(0).getFrequency();
            }
            for (int b = 0; b < bands && b < row.getBands().size(); b++) {
                double g = row.getBands().get(b).getGruneisen();
                double xb = bandMode ? x : row.getBands().get(b).getFrequency();
                xs[b][r] = xb;
                ys[b][r] = g;
                xLo = Math.min(xLo, xb);
                xHi = Math.max(xHi, xb);
                gLo = Math.min(gLo, g);
                gHi = Math.max(gHi, g);
            }
        }
        if (!(xHi > xLo)) {
            xHi = xLo + 1.0;
        }
        if (!(gHi > gLo)) {
            gHi = gLo + 1.0;
        }
        double[] padded = ChartGeometry.padded(gLo, gHi);
        gLo = padded[0];
        gHi = padded[1];
        double zeroY = mapY(0.0, gLo, gHi, h, topPad, bottomPad);
        gc.setStroke(Color.LIGHTGRAY);
        gc.strokeLine(leftPad, zeroY, w - rightPad, zeroY);
        gc.setFill(Color.DARKGRAY);
        gc.fillText("gamma = 0", leftPad + 4, zeroY - 4);
        Color[] palette = {Color.CRIMSON, Color.ROYALBLUE, Color.SEAGREEN,
                Color.DARKORANGE, Color.PURPLE, Color.TEAL, Color.BROWN, Color.NAVY};
        for (int b = 0; b < bands; b++) {
            gc.setStroke(palette[b % palette.length]);
            gc.setFill(palette[b % palette.length]);
            double prevX = -1;
            double prevY = -1;
            for (int r = 0; r < rows.size(); r++) {
                double px = ChartGeometry.mapLinear(xs[b][r], xLo, xHi,
                        leftPad, w - rightPad);
                double py = mapY(ys[b][r], gLo, gHi, h, topPad, bottomPad);
                gc.fillOval(px - 2, py - 2, 4, 4);
                if (prevX >= 0 && bandMode) {
                    gc.strokeLine(prevX, prevY, px, py);
                }
                prevX = px;
                prevY = py;
            }
        }
        gc.setFill(Color.DARKSLATEBLUE);
        gc.fillText("mode Gruneisen parameter gamma(q,nu)"
                + (bandMode ? " vs path distance" : " vs frequency")
                + " - finite difference over THREE volumes (doc-verbatim);"
                + " gamma may diverge at Gamma", leftPad, 14);
        StringBuilder caption = new StringBuilder(bandMode ? "gamma(q) along the path"
                : "gamma vs frequency scatter (mesh)");
        double[] extent = yaml.gammaExtent();
        if (extent != null) {
            caption.append(String.format(Locale.ROOT, "  gamma in [%.4f, %.4f]",
                    extent[0], extent[1]));
        }
        if (yaml.getNegativeGammaCount() > 0) {
            caption.append("  - ").append(yaml.getNegativeGammaCount())
                    .append(" negative-gamma entries (compressed modes)");
        }
        if (yaml.getGammaPointRowCount() > 0) {
            caption.append("  - ").append(yaml.getGammaPointRowCount())
                    .append(" exact-Gamma row(s): upstream's own plot skips those"
                            + " values (doc), this chart shows them plainly");
        }
        if (yaml.getPartialRowsHeld() > 0) {
            caption.append("  - live: ").append(yaml.getPartialRowsHeld())
                    .append(" partial block held");
        }
        this.chartCaption.setText(caption.toString());
    }

    private void drawBorn(QEPhonopyBorn.BornFile born) {
        List<String> lines = new ArrayList<>();
        lines.add("line 1 (verbatim): " + born.getFirstLine());
        lines.add("factor: " + (born.getFactor().isPresent()
                ? born.getFactor().orElseThrow().toString()
                + (born.getGCutoff().isPresent()
                        ? "  G_cutoff=" + born.getGCutoff().orElseThrow() : "")
                + (born.getLambda().isPresent()
                        ? "  Lambda=" + born.getLambda().orElseThrow() : "")
                : "(phonopy calculator default - line 1 is not a float, exactly"
                        + " how upstream reads it)"));
        double[] eps = born.getDielectric();
        lines.add(String.format(Locale.ROOT,
                "dielectric: [ %.6f %.6f %.6f ][ %.6f %.6f %.6f ][ %.6f %.6f %.6f ]",
                eps[0], eps[1], eps[2], eps[3], eps[4], eps[5], eps[6], eps[7],
                eps[8]));
        lines.add("Born charge tensors: " + born.getChargeCount()
                + " (one per symmetry-independent atom of the primitive cell is"
                + " phonopy's OWN expectation - it counts via its Symmetry, we"
                + " report verbatim)");
        for (int i = 0; i < born.getCharges().size(); i++) {
            double[] z = born.getCharges().get(i);
            lines.add(String.format(Locale.ROOT,
                    "Z*[atom line %d]: diag %.6f %.6f %.6f",
                    i + 1, z[0], z[4], z[8]));
        }
        if (born.getPartialLinesHeld() > 0) {
            lines.add(born.getPartialLinesHeld()
                    + " trailing partial line held (live write)");
        }
        for (String note : born.getNotes()) {
            lines.add("- " + note);
        }
        drawTextCard("BORN (non-analytical term correction; THz frequencies get"
                + " LO-TO splitting when phonopy reads this file)", lines);
    }

    private void drawForceConstants(QEPhonopyForceConstants.ForceConstants fc) {
        List<String> lines = new ArrayList<>();
        for (String line : QEPhonopyForceConstants.describe(fc).split("\n")) {
            lines.add(line);
        }
        drawTextCard("FORCE_CONSTANTS " + fc.getDimI() + " x " + fc.getDimJ()
                + " (tensor census; written by phonopy --writefc, consumed by"
                + " --readfc / phonopy-load)", lines);
    }

    /** q2r.x flfrc census card (batch 171) - a 9N x 9N matrix is not a curve. */
    private void drawQ2rFc(QEPhonopyQ2rFc.Q2rFc fc) {
        List<String> lines = new ArrayList<>();
        for (String line : QEPhonopyQ2rFc.describe(fc).split("\n")) {
            lines.add(line);
        }
        drawTextCard("q2r.x flfrc grid " + fc.getDim()[0] + " x " + fc.getDim()[1]
                + " x " + fc.getDim()[2] + " (Experimental PH_Q2R grammar; census"
                + " only - a real-space force-constant matrix is not a curve)",
                lines);
    }

    /** Text-card renderer for tensor artifacts (no curve would be honest). */
    private void drawTextCard(String title, List<String> lines) {
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
        gc.setFill(Color.DARKSLATEBLUE);
        gc.fillText(title, 20, 26);
        gc.setFill(Color.BLACK);
        int y = 52;
        for (String line : lines) {
            if (y > this.canvas.getHeight() - 14) {
                gc.fillText("... (card truncated to canvas)", 20, y);
                break;
            }
            gc.fillText(line, 20, y);
            y += 16;
        }
        this.chartCaption.setText(title);
    }

    private void drawBand(BandYaml yaml) {
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        double w = this.canvas.getWidth();
        double h = this.canvas.getHeight();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);
        double leftPad = 64;
        double bottomPad = 56;
        double topPad = 18;
        double rightPad = 18;
        double total = yaml.getTotalDistance();
        double fMin = yaml.getMinFrequency();
        double fMax = yaml.getMaxFrequency();
        double[] padded = ChartGeometry.padded(fMin, fMax);
        double fLo = Math.min(padded[0], fMin < 0 ? fMin * 1.05 : padded[0]);
        double fHi = padded[1];

        // zero-frequency reference
        double zeroY = mapY(0.0, fLo, fHi, h, topPad, bottomPad);
        if (zeroY > topPad && zeroY < h - bottomPad) {
            gc.setStroke(Color.LIGHTGRAY);
            gc.setLineWidth(1.0);
            gc.strokeLine(leftPad, zeroY, w - rightPad, zeroY);
        }
        // segment separators + labels
        gc.setStroke(Color.LIGHTGRAY);
        gc.setFill(Color.BLACK);
        for (Segment segment : yaml.getSegments()) {
            double x0 = mapX(segment.getStartDistance(), total, leftPad, w, rightPad);
            gc.strokeLine(x0, topPad, x0, h - bottomPad);
            double x1 = mapX(segment.getEndDistance(), total, leftPad, w, rightPad);
            gc.strokeLine(x1, topPad, x1, h - bottomPad);
            if (segment.getStartLabel() != null) {
                gc.fillText(segment.getStartLabel(), x0 - 3, h - bottomPad + 16);
            }
            if (segment.getEndLabel() != null) {
                gc.fillText(segment.getEndLabel(), x1 - 3, h - bottomPad + 16);
            }
        }
        // y ticks
        double[] ticks = ChartGeometry.niceTicks(fLo, fHi, 8);
        gc.setFill(Color.GRAY);
        for (double tick : ticks) {
            double y = mapY(tick, fLo, fHi, h, topPad, bottomPad);
            gc.fillText(String.format(Locale.ROOT, "%.2f", tick), 8, y + 4);
            gc.setStroke(Color.rgb(230, 230, 230));
            gc.strokeLine(leftPad, y, w - rightPad, y);
        }
        // bands
        gc.setStroke(Color.DARKBLUE);
        gc.setLineWidth(1.4);
        int bands = yaml.getBandCount();
        for (Segment segment : yaml.getSegments()) {
            List<QRow> rows = segment.getRows();
            for (int band = 0; band < bands; band++) {
                double prevX = Double.NaN;
                double prevY = Double.NaN;
                for (QRow row : rows) {
                    double x = mapX(row.getDistance(), total, leftPad, w, rightPad);
                    double y = mapY(row.getFrequencies()[band], fLo, fHi, h, topPad,
                            bottomPad);
                    if (!Double.isNaN(prevX)) {
                        gc.strokeLine(prevX, prevY, x, y);
                    }
                    prevX = x;
                    prevY = y;
                }
            }
        }
        this.chartCaption.setText(yaml.getSourceName() + ": " + yaml.getParsedRowCount()
                + " q rows, " + bands + " bands, frequency axis THz (phonopy"
                + " default - no unit text in file; stated)"
                + (yaml.getNegativeFrequencyCount() > 0
                        ? " | " + yaml.getNegativeFrequencyCount()
                                + " negative value(s) = IMAGINARY modes (phonopy"
                                + " convention), shown not hidden"
                        : "")
                + (yaml.getPartialRowsHeld() > 0 ? " | " + yaml.getPartialRowsHeld()
                        + " trailing partial row(s) held back" : "")
                + " | segments: " + yaml.getSegmentMethod());
    }

    private void drawDos(DosTable table) {
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        double w = this.canvas.getWidth();
        double h = this.canvas.getHeight();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);
        double leftPad = 64;
        double bottomPad = 40;
        double topPad = 18;
        double rightPad = 18;
        double[] freq = table.getFrequencies();
        double[][] series = table.getSeries();
        double fMin = table.getMinFrequency();
        double fMax = table.getMaxFrequency();
        boolean sum = "stacked sum".equals(this.dosModeCombo.getSelectionModel()
                .getSelectedItem());
        double yMax = 0.0;
        List<double[]> stacked = new ArrayList<>();
        for (int s = 0; s < series.length; s++) {
            double[] cumulative = new double[freq.length];
            for (int r = 0; r < freq.length; r++) {
                if (sum) {
                    for (int prev = 0; prev <= s; prev++) {
                        cumulative[r] += series[prev][r];
                    }
                } else {
                    cumulative[r] = series[s][r];
                }
                yMax = Math.max(yMax, cumulative[r]);
            }
            stacked.add(cumulative);
        }
        yMax = ChartGeometry.padded(0.0, yMax)[1];

        // zero-frequency reference
        double zeroX = mapX(0.0, fMax - fMin, leftPad, w, rightPad);
        if (fMin < 0) {
            gc.setStroke(Color.LIGHTGRAY);
            gc.strokeLine(zeroX, topPad, zeroX, h - bottomPad);
            gc.setFill(Color.GRAY);
            gc.fillText("0 (imaginary <- -> real)", zeroX + 4, topPad + 12);
        }
        double[] ticks = ChartGeometry.niceTicks(fMin, fMax, 10);
        gc.setFill(Color.GRAY);
        for (double tick : ticks) {
            double x = mapX(tick - fMin, fMax - fMin, leftPad, w, rightPad);
            gc.fillText(String.format(Locale.ROOT, "%.2f", tick), x - 8, h - 12);
        }
        Color[] palette = {Color.DARKBLUE, Color.DARKRED, Color.DARKGREEN,
                Color.DARKORANGE, Color.PURPLE, Color.TEAL, Color.BROWN,
                Color.MAGENTA, Color.NAVY, Color.OLIVE};
        for (int s = stacked.size() - 1; s >= 0; s--) {
            double[] values = stacked.get(s);
            gc.setStroke(palette[s % palette.length]);
            gc.setLineWidth(s == 0 && sum ? 1.8 : 1.2);
            double prevX = Double.NaN;
            double prevY = Double.NaN;
            for (int r = 0; r < freq.length; r++) {
                double x = mapX(freq[r] - fMin, fMax - fMin, leftPad, w, rightPad);
                double y = mapY(values[r], 0.0, yMax, h, topPad, bottomPad);
                if (!Double.isNaN(prevX)) {
                    gc.strokeLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
        }
        List<String> labels = QEPhonopyDos.seriesLabels(table);
        this.chartCaption.setText(table.getSourceName() + ": " + freq.length
                + " rows x " + table.getSeriesCount() + " series ("
                + (sum ? "stacked-cumulative" : "interleaved") + " view)"
                + (table.getComments().isEmpty() ? ""
                        : " | " + String.join("; ", table.getComments()))
                + " | THz-stated, DOS unit = frequency unit (stated)"
                + (table.getPartialTailRows() > 0 ? " | " + table.getPartialTailRows()
                        + " trailing partial line(s) held" : "")
                + " | " + (labels.size() <= 4 ? String.join(", ", labels)
                        : labels.size() + " series (atom columns in file order)"));
    }

    private void drawThermal(ThermalYaml yaml) {
        String quantity = this.quantityCombo.getSelectionModel().getSelectedItem();
        if (quantity == null) {
            quantity = "heat_capacity";
        }
        GraphicsContext gc = this.canvas.getGraphicsContext2D();
        double w = this.canvas.getWidth();
        double h = this.canvas.getHeight();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, w, h);
        double leftPad = 80;
        double bottomPad = 40;
        double topPad = 18;
        double rightPad = 18;
        List<ThermalRow> rows = yaml.getRows();
        double tMax = 0.0;
        double yMin = Double.POSITIVE_INFINITY;
        double yMax = Double.NEGATIVE_INFINITY;
        for (ThermalRow row : rows) {
            tMax = Math.max(tMax, row.getTemperature());
            double v = thermalValue(row, quantity);
            yMin = Math.min(yMin, v);
            yMax = Math.max(yMax, v);
        }
        double[] padded = ChartGeometry.padded(yMin, yMax);
        if (yMin >= 0.0) {
            padded[0] = 0.0;
        }
        double[] ticks = ChartGeometry.niceTicks(padded[0], padded[1], 9);
        gc.setFill(Color.GRAY);
        for (double tick : ticks) {
            double y = mapY(tick, padded[0], padded[1], h, topPad, bottomPad);
            gc.fillText(String.format(Locale.ROOT, "%.4g", tick), 8, y + 4);
            gc.setStroke(Color.rgb(230, 230, 230));
            gc.strokeLine(leftPad, y, w - rightPad, y);
        }
        gc.setStroke(Color.DARKBLUE);
        gc.setLineWidth(1.6);
        double prevX = Double.NaN;
        double prevY = Double.NaN;
        for (ThermalRow row : rows) {
            double x = mapX(row.getTemperature(), tMax == 0.0 ? 1.0 : tMax,
                    leftPad, w, rightPad);
            double y = mapY(thermalValue(row, quantity), padded[0], padded[1], h,
                    topPad, bottomPad);
            if (!Double.isNaN(prevX)) {
                gc.strokeLine(prevX, prevY, x, y);
            }
            gc.setFill(Color.DARKBLUE);
            gc.fillOval(x - 2, y - 2, 4, 4);
            prevX = x;
            prevY = y;
        }
        this.chartCaption.setText(yaml.getSourceName() + ": " + rows.size()
                + " T rows | " + quantity + " axis unit from the FILE's own unit:"
                + " block = " + (yaml.unitOf(quantity) == null ? "(absent)"
                        : yaml.unitOf(quantity)) + " | x axis K (verbatim)"
                + (yaml.getPartialRowsHeld() > 0 ? " | "
                        + yaml.getPartialRowsHeld() + " partial entry held" : "")
                + " | mol = N_A x primitive cell (phonopy doc caveat, stated)");
    }

    private static double thermalValue(ThermalRow row, String quantity) {
        switch (quantity) {
            case "free_energy": return row.getFreeEnergy();
            case "entropy": return row.getEntropy();
            case "energy": return row.getEnergy();
            default: return row.getHeatCapacity();
        }
    }

    private static double mapX(double value, double dataSpan, double leftPad,
            double w, double rightPad) {
        return ChartGeometry.mapLinear(value, 0.0, dataSpan <= 0.0 ? 1.0 : dataSpan,
                leftPad, w - rightPad);
    }

    private static double mapY(double value, double lo, double hi, double h,
            double topPad, double bottomPad) {
        return ChartGeometry.mapLinear(value, lo, hi == lo ? lo + 1.0 : hi,
                h - bottomPad, topPad);
    }
}
