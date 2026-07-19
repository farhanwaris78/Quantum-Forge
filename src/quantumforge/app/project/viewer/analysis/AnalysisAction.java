/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */

package quantumforge.app.project.viewer.analysis;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.project.Project;
import quantumforge.run.ResultAnalysisService;
import quantumforge.run.ResultAnalysisService.AnalysisKind;
import quantumforge.run.ResultAnalysisService.AnalysisReport;
import quantumforge.run.ResultAnalysisService.AnalysisParameters;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Project-viewer action "Analyze QE results": a single, honest entry point that
 * binds every tested result-analysis backend in {@link ResultAnalysisService} to
 * the GUI. The action never executes a command and never writes a file unless the
 * user explicitly saves a CSV export or a generated pp.x input preview.
 */
public final class AnalysisAction {

    private static final double REPORT_WIDTH = 960.0;
    private static final double REPORT_HEIGHT = 640.0;

    private final Project project;
    private final Stage stage;

    public AnalysisAction(Project project, Stage stage) {
        this.project = project;
        this.stage = stage;
    }

    /** Lets the user choose an analysis kind, collect parameters, and show the report. */
    public void showAnalysisChooser() {
        if (this.project == null) {
            showMessage("Result analysis", "No project is open.", AlertType.WARNING);
            return;
        }
        List<AnalysisKind> kinds = List.of(AnalysisKind.values());
        ChoiceDialog<AnalysisKind> chooser = new ChoiceDialog<>(kinds.get(0), kinds);
        chooser.setTitle("Analyze QE results");
        chooser.setHeaderText("Choose a deterministic result analysis");
        chooser.setContentText("Analysis:");
        // AnalysisKind.toString() feeds the list labels below.
        Optional<AnalysisKind> selected = chooser.showAndWait();
        if (selected.isEmpty()) {
            return;
        }
        run(selected.get());
    }

    private void run(AnalysisKind kind) {
        try {
            AnalysisParameters parameters = collectParameters(kind);
            if (parameters == null) {
                return; // User cancelled parameter entry.
            }
            if (kind.isProjectBound()) {
                File file = null;
                if (kind.needsDataFile()) {
                    file = resolveFile(kind);
                    if (file == null) {
                        return; // User cancelled the trajectory/phase file choice.
                    }
                }
                showReport(ResultAnalysisService.analyze(kind, this.project, file, parameters));
                return;
            }
            File file = resolveFile(kind);
            if (file == null && requiresFile(kind)) {
                return; // User cancelled the explicit file choice.
            }
            AnalysisReport report = ResultAnalysisService.analyze(kind, this.project.getProperty(),
                    this.project.getDirectory(), this.project.getPrefixName(),
                    this.project.getLogFileName(), file, parameters);
            showReport(report);
        } catch (RuntimeException ex) {
            showMessage("Result analysis", "Analysis failed closed: " + ex.getMessage(),
                    AlertType.ERROR);
        }
    }

    /** True when an analysis cannot proceed with the service's own failure report alone. */
    private static boolean requiresFile(AnalysisKind kind) {
        return !(kind.isInputPreview() || kind.usesProjectLog() || kind.isProjectBound());
    }

    /** File selection: discovered candidates first, explicit chooser when discovery fails. */
    private File resolveFile(AnalysisKind kind) {
        if (kind.isInputPreview()) {
            return null;
        }
        File directory = this.project.getDirectory();
        String logName = this.project.getLogFileName();
        List<File> candidates = ResultAnalysisService.discover(kind, directory, logName);
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.size() > 1) {
            ChoiceDialog<File> dialog = new ChoiceDialog<>(candidates.get(0), candidates);
            dialog.setTitle("Analyze QE results");
            dialog.setHeaderText("Several candidate files were found for:\n" + kind.getLabel());
            dialog.setContentText("File:");
            Optional<File> picked = dialog.showAndWait();
            if (picked.isPresent()) {
                return picked.get();
            }
            return null;
        }
        if (kind.usesProjectLog()) {
            return null; // Honest failure is produced by the service.
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select data file for: " + kind.getLabel());
        if (directory != null && directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
        return chooser.showOpenDialog(this.stage);
    }

    /** Collects only the parameters an analysis kind actually consumes. */
    private AnalysisParameters collectParameters(AnalysisKind kind) {
        AnalysisParameters parameters = new AnalysisParameters();
        switch (kind) {
        case PP_WAVEFUNCTION_INPUT: {
            Integer kpoint = askInteger("Wavefunction k-point index (1-based)", 1);
            if (kpoint == null) {
                return null;
            }
            Integer band = askInteger("Wavefunction band index (1-based)", 1);
            if (band == null) {
                return null;
            }
            Integer spin = askInteger("Spin component (0 unpolarized, 1 up, 2 down)", 0);
            if (spin == null) {
                return null;
            }
            parameters.withKpointIndex(kpoint).withBandIndex(band).withSpinComponent(spin);
            break;
        }
        case ELIASHBERG_TC: {
            Double muStar = askDouble("Coulomb pseudopotential mu* (typically 0.10-0.15)", "0.10");
            if (muStar == null) {
                return null;
            }
            parameters.withMuStar(muStar);
            break;
        }
        case BANDS_DATA: {
            Double fermi = askDouble(
                    "Fermi reference in eV, or leave empty to use the stored/log value", "");
            if (fermi != null) {
                parameters.withFermiEv(fermi);
            }
            break;
        }
        case WORK_FUNCTION: {
            Double fermi = askDouble(
                    "Slab Fermi energy in eV, or leave empty to use the stored/log value", "");
            if (fermi != null) {
                parameters.withFermiEv(fermi);
            }
            break;
        }
        case RESOURCE_ESTIMATE: {
            Integer ranks = askInteger("Total MPI ranks for the layout advice", 1);
            if (ranks == null) {
                return null;
            }
            parameters.withTotalRanks(ranks);
            break;
        }
        case GEOMETRY_MEASURE: {
            int[] indices = askAtomIndices();
            if (indices == null) {
                return null;
            }
            parameters.withAtomIndices(indices[0], indices[1], indices[2], indices[3]);
            break;
        }
        case MD_MSD: {
            Double dt = askDouble("Time between stored trajectory frames (ps)", "1.0");
            if (dt == null) {
                return null;
            }
            parameters.withFrameTimeStepPs(dt);
            break;
        }
        case PHONON_DOS_THERMO: {
            Double temperature = askDouble(
                    "Temperature in kelvin for the harmonic phonon integration", "300.0");
            if (temperature != null) {
                parameters.withTemperatureK(temperature);
            }
            break;
        }
        case VOLTAGE_PROFILE: {
            Double charge = askDouble("Insertion-ion charge z (1 = Li/Na/K, ~2 = Mg/Zn)", "1.0");
            if (charge != null) {
                parameters.withIonCharge(charge);
            }
            break;
        }
        case ADSORPTION_PREVIEW: {
            String molecule = askText("Molecule template (CO, H2O, NH3, OH, NO)", "CO");
            if (molecule == null) {
                return null;
            }
            Double height = askDouble("Height above the topmost slab atom in Angstrom (>= 1.0)",
                    "2.0");
            if (height == null) {
                return null;
            }
            Double x = askDouble("Fractional surface position x (0-1)", "0.5");
            if (x == null) {
                return null;
            }
            Double y = askDouble("Fractional surface position y (0-1)", "0.5");
            if (y == null) {
                return null;
            }
            parameters.withMoleculeName(molecule).withAdsorbHeight(height).withAdsorbX(x)
                    .withAdsorbY(y);
            break;
        }
        case SMEARING_ANALYSIS: {
            int defaultAtoms = 1;
            if (this.project.getCell() != null && this.project.getCell().numAtoms() > 0) {
                defaultAtoms = this.project.getCell().numAtoms();
            }
            Integer atoms = askInteger("Number of atoms in the simulation cell "
                    + "(needed for the per-atom entropy safety check)", defaultAtoms);
            if (atoms == null) {
                return null;
            }
            parameters.withAtomCount(atoms);
            break;
        }
        case GEOMETRY_CONVERGENCE: {
            Double force = askDouble("Total-force threshold in Ry/bohr "
                    + "(the 'optimized' verdict is only granted below it)", "0.001");
            if (force != null) {
                parameters.withForceThresholdRyBohr(force);
            }
            Double pressure = askDouble("Pressure threshold in kbar, or leave empty "
                    + "to skip the pressure check", "");
            if (pressure != null) {
                parameters.withPressureThresholdKbar(pressure);
            }
            break;
        }
        case SYMMETRY_KPATH: {
            Double tolerance = askDouble("spglib symmetry tolerance in Angstrom", "1.0e-5");
            if (tolerance != null) {
                parameters.withSymmetryTolerance(tolerance);
            }
            break;
        }
        case CONVERGENCE_REVIEW: {
            int defaultAtoms = 1;
            if (this.project.getCell() != null && this.project.getCell().numAtoms() > 0) {
                defaultAtoms = this.project.getCell().numAtoms();
            }
            Integer atoms = askInteger("Number of atoms in the simulation cell "
                    + "(per-atom energy criterion)", defaultAtoms);
            if (atoms == null) {
                return null;
            }
            parameters.withAtomCount(atoms);
            Double tolerance = askDouble("Energy-change tolerance in Ry/atom"
                    + " (default 0.001 Ry ~ 13.6 meV/atom)", "0.001");
            if (tolerance != null) {
                parameters.withEnergyToleranceRyPerAtom(tolerance);
            }
            break;
        }
        case SERIES_PLAN: {
            String keyword = askText("QE keyword to vary (e.g. ecutwfc)", "ecutwfc");
            if (keyword == null) {
                return null;
            }
            Double start = askDouble("Start value", "30.0");
            if (start == null) {
                return null;
            }
            Double step = askDouble("Step between points", "10.0");
            if (step == null) {
                return null;
            }
            Integer count = askInteger("Number of points (2-20)", 6);
            if (count == null) {
                return null;
            }
            parameters.withSeriesKeyword(keyword).withSeriesStart(start).withSeriesStep(step)
                    .withSeriesCount(count);
            break;
        }
        case DEFECT_PREVIEW: {
            String type = askText("Defect type: 'vacancy' or 'substitution'", "vacancy");
            if (type == null) {
                return null;
            }
            int defaultIndex = 1;
            Integer index = askInteger("Target atom index (1-based)", defaultIndex);
            if (index == null) {
                return null;
            }
            parameters.withDefectType(type).withAtomIndices(index, 0, 0, 0);
            if ("substitution".equals(type.trim().toLowerCase(java.util.Locale.ROOT))) {
                String element = askText("Replacement element symbol (e.g. B, N, Al)", "B");
                if (element == null) {
                    return null;
                }
                parameters.withDefectElement(element);
            }
            Integer charge = askInteger("Defect charge state (metadata only, not written "
                    + "into the input)", 0);
            if (charge == null) {
                return null;
            }
            parameters.withDefectCharge(charge);
            break;
        }
        default:
            break;
        }
        return parameters;
    }

    /** Free-text prompt; returns null on cancel. */
    private String askText(String prompt, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle("Analysis parameter");
        dialog.setHeaderText(prompt);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        return result.isEmpty() ? null : result.get();
    }

    /** Parses "A,B[,C[,D]]" 1-based atom indices; absent C/D become 0. */
    private int[] askAtomIndices() {
        TextInputDialog dialog = new TextInputDialog("1,2,3,4");
        dialog.setTitle("Geometry measurement");
        dialog.setHeaderText("Atom indices A,B,C,D (1-based); omit C/D for a bond measurement");
        dialog.setContentText("Indices:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        String[] parts = result.get().split(",");
        if (parts.length < 2 || parts.length > 4) {
            showMessage("Geometry measurement", "Provide 2 to 4 comma-separated indices.",
                    AlertType.ERROR);
            return null;
        }
        int[] indices = {0, 0, 0, 0};
        for (int i = 0; i < parts.length; i++) {
            String token = parts[i].trim();
            if (token.isEmpty() && i >= 2) {
                continue; // trailing ", ," means absent C/D
            }
            try {
                indices[i] = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                showMessage("Geometry measurement", "Not an integer index: '" + token + "'",
                        AlertType.ERROR);
                return null;
            }
        }
        return indices;
    }

    private Integer askInteger(String prompt, int defaultValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(defaultValue));
        dialog.setTitle("Analysis parameter");
        dialog.setHeaderText(prompt);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(result.get().trim());
        } catch (NumberFormatException ex) {
            showMessage("Analysis parameter", "Not an integer: " + result.get(), AlertType.ERROR);
            return null;
        }
    }

    /** Returns null when cancelled; empty input means "not supplied". */
    private Double askDouble(String prompt, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle("Analysis parameter");
        dialog.setHeaderText(prompt);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        String text = result.get().trim();
        if (text.isEmpty()) {
            return null; // Interpreted by the caller as "use defaults".
        }
        try {
            return Double.valueOf(text.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            showMessage("Analysis parameter", "Not a number: " + text, AlertType.ERROR);
            return null;
        }
    }

    /**
     * Shows the report (including its provenance section when present, Roadmap
     * #128). CSV export, pp.x saving, and whole-report saving all stay explicit
     * user actions; the dialog itself writes nothing.
     */
    private void showReport(AnalysisReport report) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Analyze QE results");
        dialog.setHeaderText(report.getTitle()
                + (report.isSuccess() ? "" : " - no usable result (see notes)"));

        TextArea area = new TextArea(report.renderFullText());
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'monospace';");
        VBox.setVgrow(area, Priority.ALWAYS);
        VBox content = new VBox(area);
        content.setPadding(new Insets(8.0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(REPORT_WIDTH, REPORT_HEIGHT);
        dialog.setResizable(true);

        ButtonType close = new ButtonType("Close", ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        ButtonType saveReport = new ButtonType("Save report ...", ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().add(saveReport);
        ButtonType exportCsv = null;
        ButtonType saveInput = null;
        if (report.hasCsv()) {
            exportCsv = new ButtonType("Export CSV ...", ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().add(exportCsv);
        }
        if (report.getGeneratedInput() != null && !report.getGeneratedInput().isEmpty()) {
            saveInput = new ButtonType("Save input file ...", ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().add(saveInput);
        }

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        if (exportCsv != null && answer.get() == exportCsv) {
            saveText("Export analysis CSV", "analysis.csv",
                    report.getCsvLines().stream().collect(Collectors.joining("\n")) + "\n");
        } else if (saveInput != null && answer.get() == saveInput) {
            saveText("Save generated pp.x input", "pp.in", report.getGeneratedInput());
        } else if (answer.get() == saveReport) {
            saveText("Save analysis report", "report.txt", report.renderFullText());
        }
    }

    private void saveText(String title, String proposedName, String content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(proposedName);
        if (this.project.getDirectory() != null && this.project.getDirectory().isDirectory()) {
            chooser.setInitialDirectory(this.project.getDirectory());
        }
        File target = chooser.showSaveDialog(this.stage);
        if (target == null) {
            return;
        }
        try {
            AtomicFileWriter.writeUtf8(target.toPath(), content);
            showMessage(title, "Wrote " + target.getAbsolutePath(), AlertType.INFORMATION);
        } catch (IOException ex) {
            showMessage(title, "Could not write " + target.getName() + ": " + ex.getMessage(),
                    AlertType.ERROR);
        }
    }

    private void showMessage(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(700.0);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
