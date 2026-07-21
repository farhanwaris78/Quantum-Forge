/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.List;

import quantumforge.app.project.ProjectAction;
import quantumforge.app.project.ProjectActions;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.atoms.AtomsAction;
import quantumforge.app.project.viewer.designer.DesignerAction;
import quantumforge.app.project.viewer.inputfile.QEFXInputFile;
import quantumforge.app.project.viewer.modeler.ModelerAction;
import quantumforge.app.project.viewer.result.ResultAction;
import quantumforge.app.project.viewer.run.ArraySubmitAction;
import quantumforge.app.project.viewer.run.QEFXRunDialog;
import quantumforge.app.project.viewer.run.RunAction;
import quantumforge.app.project.viewer.run.RunEvent;
import quantumforge.app.project.viewer.recovery.RecoveryAction;
import quantumforge.app.project.viewer.save.SaveAction;
import quantumforge.app.project.viewer.screenshot.QEFXScreenshotDialog;
import quantumforge.export.AtomicExporter;
import quantumforge.operation.OperationResult;
import quantumforge.input.QEInput;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.input.validation.QEInputValidator;
import quantumforge.input.validation.QESchemaValidator;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.project.Project;
import quantumforge.run.parser.BandGapParser;
import quantumforge.run.parser.FinalGeometryUpdater;
import quantumforge.run.parser.QEErrorKnowledgeBase;
import quantumforge.run.parser.QETimingResourceParser;
import quantumforge.run.parser.QEPdosParser;
import quantumforge.run.parser.QEPhononFreqParser;
import quantumforge.run.parser.QERamanIRSpectraParser;
import quantumforge.run.parser.CubeGridReader;
import quantumforge.run.parser.QEGridDensityDifference;
import quantumforge.run.parser.ScfConvergenceAnalyzer;
import quantumforge.run.QECommandDag;
import quantumforge.run.RunningManager;
import quantumforge.run.RunningNode;
import quantumforge.run.RunningType;
import quantumforge.run.WorkflowExporter;
import quantumforge.tools.XCrySDenLauncher;
import quantumforge.app.project.viewer.analysis.AnalysisAction;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ViewerActions extends ProjectActions<Node> {

    private ViewerItemSet itemSet;

    private AtomsAction atomsAction;

    private ModelerAction modelerAction;

    private DesignerAction designerAction;

    private ResultAction resultAction;

    public ViewerActions(Project project, QEFXProjectController controller) {
        super(project, controller);

        this.itemSet = new ViewerItemSet();

        this.atomsAction = null;
        this.modelerAction = null;
        this.designerAction = null;
        this.resultAction = null;

        this.setupOnViewerSelected();
        this.setupActions();
    }

    @Override
    public void actionInitially() {
        if (this.controller == null) {
            return;
        }

        this.controller.addViewerMenuItems(this.itemSet.getItems());

        ProjectAction action = this.actions.get(this.itemSet.getAtomsViewerItem());
        if (action != null) {
            action.actionOnProject(this.controller);
        }
    }

    public boolean saveFile() {
        return this.actionSaveFile(this.controller);
    }

    public void screenShot() {
        this.screenShot(null);
    }

    public void screenShot(Node subject) {
        this.actionScreenShot(this.controller, subject);
    }

    private void setupOnViewerSelected() {
        if (this.controller == null) {
            return;
        }

        this.controller.setOnViewerSelected(graphic -> {
            if (graphic == null) {
                return;
            }

            ProjectAction action = null;
            if (this.actions != null) {
                action = this.actions.get(graphic);
            }

            if (action != null && this.controller != null) {
                action.actionOnProject(this.controller);
            }
        });
    }

    private void setupActions() {
        ViewerItem[] items = this.itemSet.getItems();
        for (ViewerItem item : items) {
            if (item == null) {
                continue;
            }

            if (item == this.itemSet.getAtomsViewerItem()) {
                this.actions.put(item, controller2 -> this.actionAtomsViewer(controller2));

            } else if (item == this.itemSet.getInputFileItem()) {
                this.actions.put(item, controller2 -> this.actionInputFile(controller2));

            } else if (item == this.itemSet.getModelerItem()) {
                this.actions.put(item, controller2 -> this.actionModeler(controller2));

            } else if (item == this.itemSet.getSaveFileItem()) {
                this.actions.put(item, controller2 -> this.actionSaveFile(controller2));

            } else if (item == this.itemSet.getSaveAsFileItem()) {
                this.actions.put(item, controller2 -> this.actionSaveAsFile(controller2));

            } else if (item == this.itemSet.getRecoverItem()) {
                this.actions.put(item, controller2 -> this.actionRecover(controller2));

            } else if (item == this.itemSet.getDesignerItem()) {
                this.actions.put(item, controller2 -> this.actionDesigner(controller2));

            } else if (item == this.itemSet.getScreenShotItem()) {
                this.actions.put(item, controller2 -> this.actionScreenShot(controller2, null));

            } else if (item == this.itemSet.getRunItem()) {
                this.actions.put(item, controller2 -> this.actionRun(controller2));

            } else if (item == this.itemSet.getArraySweepItem()) {
                this.actions.put(item, controller2 -> this.actionArraySweep(controller2));

            } else if (item == this.itemSet.getResultItem()) {
                this.actions.put(item, controller2 -> this.actionResult(controller2));

            } else if (item == this.itemSet.getExportItem()) {
                this.actions.put(item, controller2 -> this.actionExport(controller2));

            } else if (item == this.itemSet.getExportWorkflowItem()) {
                this.actions.put(item, controller2 -> this.actionExportWorkflow(controller2));

            } else if (item == this.itemSet.getValidateInputItem()) {
                this.actions.put(item, controller2 -> this.actionValidateInput(controller2));

            } else if (item == this.itemSet.getDiagnoseLogItem()) {
                this.actions.put(item, controller2 -> this.actionDiagnoseLog(controller2));

            } else if (item == this.itemSet.getBandGapItem()) {
                this.actions.put(item, controller2 -> this.actionAnalyzeBandGap(controller2));

            } else if (item == this.itemSet.getFinalGeometryItem()) {
                this.actions.put(item, controller2 -> this.actionPreviewFinalGeometry(controller2));

            } else if (item == this.itemSet.getPdosItem()) {
                this.actions.put(item, controller2 -> this.actionInspectPdos(controller2));

            } else if (item == this.itemSet.getPhononItem()) {
                this.actions.put(item, controller2 -> this.actionInspectPhonons(controller2));

            } else if (item == this.itemSet.getSpectraItem()) {
                this.actions.put(item, controller2 -> this.actionInspectSpectra(controller2));

            } else if (item == this.itemSet.getDensityDifferenceItem()) {
                this.actions.put(item, controller2 -> this.actionDensityDifference(controller2));

            } else if (item == this.itemSet.getAnalyzeResultsItem()) {
                this.actions.put(item, controller2 -> this.actionAnalyzeResults(controller2));

            } else if (item == this.itemSet.getXcrysdenItem()) {
                this.actions.put(item, controller2 -> this.actionXcrysden(controller2));
            }
        }
    }

    private void actionAtomsViewer(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        if (this.atomsAction == null || controller != this.atomsAction.getController()) {
            this.atomsAction = new AtomsAction(this.project, controller);
        }

        if (this.atomsAction != null) {
            this.atomsAction.showAtoms();
        }
    }

    private void actionInputFile(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        this.project.resolveQEInputs();

        try {
            QEFXInputFile inputFile = new QEFXInputFile(controller, this.project);
            controller.clearStackedsOnViewerPane();
            controller.stackOnViewerPane(inputFile.getNode());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void actionModeler(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        if (this.modelerAction == null || controller != this.modelerAction.getController()) {
            this.modelerAction = new ModelerAction(this.project, controller);
        }

        if (this.modelerAction != null) {
            this.modelerAction.showModeler();
        }
    }

    private boolean actionSaveFile(QEFXProjectController controller) {
        if (controller == null) {
            return false;
        }

        SaveAction saveAction = new SaveAction(this.project, controller);
        return saveAction.saveProject();
    }

    private void actionSaveAsFile(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        SaveAction saveAction = new SaveAction(this.project, controller);
        saveAction.saveProjectAsNew();
    }

    private void actionRecover(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }
        RecoveryAction recoveryAction = new RecoveryAction(this.project, controller);
        recoveryAction.recover();
    }

    private void actionDesigner(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        if (this.designerAction == null || controller != this.designerAction.getController()) {
            this.designerAction = new DesignerAction(this.project, controller);
        }

        if (this.designerAction != null) {
            this.designerAction.showDesigner();
        }
    }

    private void actionScreenShot(QEFXProjectController controller, Node subject) {
        if (controller == null) {
            return;
        }

        QEFXScreenshotDialog dialog = new QEFXScreenshotDialog(controller, this.project, subject);
        Optional<ButtonType> optButtonType = dialog.showAndWait();

        if (optButtonType != null && optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
            try {
                dialog.saveImage();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void actionRun(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        this.project.resolveQEInputs();

        QEFXRunDialog dialog = new QEFXRunDialog(this.project, this);
        Optional<RunEvent> optButtonType = dialog.showAndWait();

        if (optButtonType != null && optButtonType.isPresent()) {
            RunEvent runEvent = optButtonType.get();
            if (runEvent != null) {
                RunAction runAction = new RunAction(controller);
                runAction.runCalculation(runEvent);
                
                // Start live monitoring if possible
                this.startLiveMonitoring(runEvent);
            }
        }
    }

    /**
     * Batch-144 (#93 GUI slice): the array-sweep submission dialogue. Every
     * decision lives in the typed headless products (sweep planner, resolver,
     * guarded draft, executors); this menu entry only opens their shell.
     */
    private void actionArraySweep(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        ArraySubmitAction action = new ArraySubmitAction(this.project, controller);
        action.submitInteractively();
    }

    /** Polling cadence for the read-only live-run monitor, in milliseconds. */
    private static final long LIVE_MONITOR_PERIOD_MS = 2500L;

    /**
     * Read-only background monitor that observes an active run without owning its
     * lifecycle: the {@link RunningManager} remains the source of truth, the monitor
     * never touches project files, and it terminates by itself when the run node is
     * removed or finishes. GUI refreshes are performed on the JavaFX thread only.
     */
    private void startLiveMonitoring(RunEvent runEvent) {
        if (runEvent == null) {
            return;
        }
        RunningNode node = runEvent.getRunningNode();
        if (node == null) {
            return;
        }

        Thread liveThread = new Thread(() -> {
            try {
                // Wait for the process to actually start and create files.
                Thread.sleep(3000L);
                while (RunningManager.getInstance().getNode(this.project) != null) {
                    Platform.runLater(() -> {
                        // Refresh the results if we are currently looking at them.
                        if (this.controller != null && this.controller.isResultViewerMode()) {
                            // Result viewers poll their parsers through LogParser tailers.
                        }
                    });
                    Thread.sleep(LIVE_MONITOR_PERIOD_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        liveThread.setDaemon(true);
        liveThread.start();
    }

    private void actionResult(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        if (this.resultAction == null || controller != this.resultAction.getController()) {
            this.resultAction = new ResultAction(this.project, controller);
        }

        if (this.resultAction != null) {
            this.resultAction.showResult();
        }
    }

    private void actionExport(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export atomic configuration");

        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CIF (*.cif)", "*.cif"),
            new FileChooser.ExtensionFilter("XYZ (*.xyz)", "*.xyz"),
            new FileChooser.ExtensionFilter("VASP POSCAR", "*"),
            new FileChooser.ExtensionFilter("Quantum ESPRESSO (*.in)", "*.in")
        );

        Stage stage = controller.getStage();
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        int format = AtomicExporter.FORMAT_CIF;
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".cif")) {
            format = AtomicExporter.FORMAT_CIF;
        } else if (fileName.endsWith(".xyz")) {
            format = AtomicExporter.FORMAT_XYZ;
        } else if (fileName.endsWith(".in")) {
            format = AtomicExporter.FORMAT_QE_INPUT;
        } else {
            format = AtomicExporter.FORMAT_POSCAR;
        }

        boolean success = AtomicExporter.exportToFile(this.project.getCell(), file.getPath(), format);
        if (success) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Export");
            alert.setHeaderText("Export successful");
            alert.setContentText("Exported to " + file.getName());
            alert.showAndWait();
        } else {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Export");
            alert.setHeaderText("Export failed");
            alert.showAndWait();
        }
    }

    /** Show deterministic preflight findings without starting or modifying a run. */
    private void actionValidateInput(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }
        this.project.resolveQEInputs();
        QEInput input = this.project.getQEInputCurrent();
        List<ValidationIssue> issues = new java.util.ArrayList<>(
                new QEInputValidator().validate(input));
        // Batch 150: mined-schema audit layered on top of the structural checks,
        // against the newest mined QE grammar (window 7.2-7.6; a specific minor
        // version can be audited from the version-check analysis action).
        String schemaVersion = QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
        List<ValidationIssue> schemaIssues =
                new QESchemaValidator().validate(input, schemaVersion);
        issues.addAll(schemaIssues);
        boolean errors = QEInputValidator.hasErrors(issues);
        StringBuilder message = new StringBuilder();
        if (issues.isEmpty()) {
            message.append("No deterministic structural/preflight issues were found. ")
                    .append("This does not establish convergence or physical suitability.");
        } else {
            for (ValidationIssue issue : issues) {
                message.append(issue).append('\n');
                if (!issue.getDocumentationUrl().isEmpty()) {
                    message.append("  ").append(issue.getDocumentationUrl()).append('\n');
                }
            }
        }
        message.append("\n--\nStructural checks plus the mined-schema audit (QE ")
                .append(schemaVersion)
                .append(" grammar; namelist keywords machine-mined from QE tags qe-7.2 .. ")
                .append("qe-7.6 - cards and runtime-conditional rules stay out of scope); ")
                .append("the version-check analysis audits a specific minor version.");
        Alert alert = new Alert(errors ? AlertType.ERROR : AlertType.INFORMATION);
        alert.setTitle("Quantum ESPRESSO input validation");
        alert.setHeaderText(errors ? "Input has blocking preflight errors"
                : "Input preflight completed");
        alert.setContentText(message.toString());
        alert.getDialogPane().setPrefWidth(900.0);
        alert.setResizable(true);
        alert.showAndWait();
    }

    /** Parses an explicit QE gap summary; it never infers directness from a total DOS. */
    private void actionAnalyzeBandGap(QEFXProjectController controller) {
        if (controller == null || this.project.getDirectory() == null) {
            showBandGap("No project directory is available for band-gap analysis.", AlertType.WARNING);
            return;
        }
        Path log = this.project.getDirectory().toPath().resolve(this.project.getLogFileName());
        BandGapParser parser = new BandGapParser(log.toString());
        if (!parser.parse()) {
            String details = parser.getDiagnostics().isEmpty() ? "No supported gap summary was found."
                    : String.join("\n", parser.getDiagnostics());
            showBandGap(details, AlertType.WARNING);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(String.format(java.util.Locale.ROOT, "Gap: %.6f eV%n", parser.getBandGap()));
        if (parser.isDirectKnown()) {
            message.append(parser.isDirect() ? "Directness: explicitly reported direct." :
                    "Directness: explicitly reported indirect.");
        } else {
            message.append("Directness: unknown; this QE log summary is not k-resolved evidence.");
        }
        message.append("\nClassification: ").append(parser.isInsulator()
                ? "gapped above the 0.01 eV analysis tolerance." : "metallic/small-gap within tolerance.");
        showBandGap(message.toString(), AlertType.INFORMATION);
    }

    private void showBandGap(String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Quantum ESPRESSO band-gap analysis");
        alert.setHeaderText(type == AlertType.WARNING ? "Band gap undetermined" : "Band-gap summary");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Select one system CUBE followed by one or more component CUBEs; does not write output grids. */
    private void actionDensityDifference(QEFXProjectController controller) {
        if (controller == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select system CUBE first, then component CUBE files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CUBE files", "*.cube", "*.cub"));
        List<File> files = chooser.showOpenMultipleDialog(controller.getStage());
        if (files == null || files.size() < 2) {
            showDensityDifference("Select one system CUBE followed by at least one component CUBE.", AlertType.WARNING);
            return;
        }
        OperationResult<QEGridDensityDifference.Grid3D> systemResult = CubeGridReader.read(files.get(0).toPath());
        if (!systemResult.isSuccess()) {
            showDensityDifference(systemResult.getMessage(), AlertType.ERROR);
            return;
        }
        java.util.ArrayList<QEGridDensityDifference.Grid3D> components = new java.util.ArrayList<>();
        for (int i = 1; i < files.size(); i++) {
            OperationResult<QEGridDensityDifference.Grid3D> result = CubeGridReader.read(files.get(i).toPath());
            if (!result.isSuccess()) {
                showDensityDifference("Component " + files.get(i).getName() + ": " + result.getMessage(), AlertType.ERROR);
                return;
            }
            components.add(result.getValue().orElseThrow());
        }
        QEGridDensityDifference.DiffResult diff = QEGridDensityDifference.computeDifference(
                systemResult.getValue().orElseThrow(), components, 1.0e-8);
        showDensityDifference(diff.getDiagnosticMessage() + "\n\nNo output CUBE was written; inspect provenance and units before interpreting this value.",
                diff.isCompatible() ? AlertType.INFORMATION : AlertType.WARNING);
    }

    private void showDensityDifference(String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("CUBE density difference");
        alert.setHeaderText(type == AlertType.ERROR ? "Density difference failed" : "Grid compatibility and integral");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Displays parsed Raman/IR mode metadata; broadening parameters are not guessed in the GUI. */
    private void actionInspectSpectra(QEFXProjectController controller) {
        if (controller == null || this.project.getDirectory() == null) {
            showSpectra("No project directory is available for Raman/IR inspection.", AlertType.WARNING);
            return;
        }
        File log = new File(this.project.getDirectory(), this.project.getLogFileName());
        QERamanIRSpectraParser parser = new QERamanIRSpectraParser(this.project.getProperty());
        try {
            parser.parse(log);
        } catch (IOException ex) {
            showSpectra("Could not parse Raman/IR modes: " + ex.getMessage(), AlertType.ERROR);
            return;
        }
        List<QERamanIRSpectraParser.SpectroMode> modes = parser.getModes();
        if (modes.isEmpty()) {
            showSpectra("No supported Raman/IR mode table was found in " + log.getName()
                    + ". Raman/IR intensities require an engine route that emits those tensors.", AlertType.WARNING);
            return;
        }
        StringBuilder message = new StringBuilder("Parsed modes: ").append(modes.size()).append('\n');
        int limit = Math.min(modes.size(), 100);
        for (int i = 0; i < limit; i++) {
            QERamanIRSpectraParser.SpectroMode mode = modes.get(i);
            message.append(String.format(java.util.Locale.ROOT,
                    "mode %d: %.5f cm-1; IR=%g; Raman=%g%n", mode.getModeIndex(),
                    mode.getFrequencyCm1(), mode.getIrIntensity(), mode.getRamanActivity()));
        }
        message.append("\nRaw engine activities are shown. Powder broadening/orientation assumptions are not applied automatically.");
        showSpectra(message.toString(), AlertType.INFORMATION);
    }

    private void showSpectra(String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Raman / IR mode inspection");
        alert.setHeaderText(type == AlertType.WARNING ? "Raman/IR data unavailable" : "Parsed vibrational mode activities");
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(850.0);
        alert.setResizable(true);
        alert.showAndWait();
    }

    /** Inspects one explicit matdyn q-path frequency table without claiming full-BZ stability. */
    private void actionInspectPhonons(QEFXProjectController controller) {
        if (controller == null || this.project.getDirectory() == null) {
            showPhonons("No project directory is available for phonon-frequency inspection.", AlertType.WARNING);
            return;
        }
        File[] candidates = this.project.getDirectory().listFiles(file -> file.isFile()
                && (file.getName().equals("matdyn.freq") || file.getName().endsWith(".freq.gp")
                || file.getName().endsWith(".freq")));
        if (candidates == null || candidates.length == 0) {
            showPhonons("No matdyn frequency file was found. Expected matdyn.freq or *.freq.gp.", AlertType.WARNING);
            return;
        }
        java.util.Arrays.sort(candidates, java.util.Comparator.comparing(File::getName));
        QEPhononFreqParser parser = new QEPhononFreqParser(this.project.getProperty());
        try {
            parser.parse(candidates[0]);
        } catch (IOException ex) {
            showPhonons("Could not parse phonon frequencies: " + ex.getMessage(), AlertType.ERROR);
            return;
        }
        if (parser.getBranches().isEmpty()) {
            showPhonons(String.join("\n", parser.getDiagnostics()), AlertType.WARNING);
            return;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (QEPhononFreqParser.PhononBranch branch : parser.getBranches()) {
            for (double frequency : branch.getFrequencyCm1()) {
                min = Math.min(min, frequency);
                max = Math.max(max, frequency);
            }
        }
        StringBuilder message = new StringBuilder();
        message.append("File: ").append(candidates[0].getName()).append('\n');
        message.append("Branches: ").append(parser.getBranches().size()).append('\n');
        message.append(String.format(java.util.Locale.ROOT, "Sampled frequency range: %.4f to %.4f cm-1%n", min, max));
        message.append("Significant imaginary values: ").append(!parser.isLatticeStable()).append("\n\n");
        for (String diagnostic : parser.getDiagnostics()) {
            message.append(diagnostic).append('\n');
        }
        showPhonons(message.toString(), parser.isLatticeStable() ? AlertType.INFORMATION : AlertType.WARNING);
    }

    private void showPhonons(String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Phonon frequency inspection");
        alert.setHeaderText(type == AlertType.WARNING ? "Phonon stability needs review" : "Sampled phonon q-path");
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(850.0);
        alert.setResizable(true);
        alert.showAndWait();
    }

    /** Inspects parsed projwfc.x components without fabricating a total-DOS electron count. */
    private void actionInspectPdos(QEFXProjectController controller) {
        if (controller == null || this.project.getDirectory() == null) {
            showPdos("No project directory is available for projected-DOS inspection.", AlertType.WARNING);
            return;
        }
        QEPdosParser parser = new QEPdosParser(this.project.getProperty());
        parser.parseDirectory(this.project.getDirectory(), this.project.getPrefixName());
        List<QEPdosParser.PdosComponent> components = parser.getComponents();
        if (components.isEmpty()) {
            showPdos("No validated projwfc.x .pdos_atm# files were found. "
                    + "Files without a PDOS header are intentionally rejected.", AlertType.WARNING);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("Validated PDOS components: ").append(components.size()).append('\n');
        int limit = Math.min(components.size(), 100);
        for (int i = 0; i < limit; i++) {
            QEPdosParser.PdosComponent component = components.get(i);
            double[] energy = component.getEnergies();
            double[] density = component.getPdos();
            double area = QEPdosParser.integratePdos(energy, density);
            message.append(String.format(java.util.Locale.ROOT,
                    "atom %d %s wfc %d (%s), spin label %d: E=[%.5f, %.5f] eV, ∫PDOS dE=%.6g states%n",
                    component.getAtomIndex(), component.getElement(), component.getWfcIndex(),
                    component.getOrbitalL(), component.getSpinChannel(), energy[0], energy[energy.length - 1], area));
        }
        if (components.size() > limit) {
            message.append("Only the first ").append(limit).append(" components are shown.\n");
        }
        message.append("\nEnergy is exactly as emitted by projwfc.x. The integral is not an electron count "
                + "without an explicit Fermi/occupation convention.");
        showPdos(message.toString(), AlertType.INFORMATION);
    }

    private void showPdos(String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Projected DOS inspection");
        alert.setHeaderText(type == AlertType.WARNING ? "Projected DOS unavailable" : "Validated projwfc.x components");
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(900.0);
        alert.setResizable(true);
        alert.showAndWait();
    }

    /** Previews a converged final geometry and deliberately does not mutate input cards. */
    private void actionPreviewFinalGeometry(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }
        OperationResult<FinalGeometryUpdater.GeometryPreview> result = FinalGeometryUpdater.preview(this.project);
        Alert alert = new Alert(result.isSuccess() ? AlertType.INFORMATION : AlertType.WARNING);
        alert.setTitle("Final geometry preview");
        alert.setHeaderText(result.isSuccess() ? "Validated read-only geometry preview" : "Geometry cannot be applied safely");
        if (result.isSuccess()) {
            FinalGeometryUpdater.GeometryPreview preview = result.getValue().orElseThrow();
            StringBuilder message = new StringBuilder();
            message.append("Ionic step: ").append(preview.getStepIndex() + 1).append('\n');
            message.append(String.format(java.util.Locale.ROOT, "Energy: %.10f Ry%n", preview.getEnergyRy()));
            message.append(String.format(java.util.Locale.ROOT, "Total force: %.6g Ry/bohr%n", preview.getTotalForce()));
            message.append("Converged: ").append(preview.isConverged()).append('\n');
            message.append("Atoms: ").append(preview.getAtomCount()).append('\n');
            message.append("\nNo QE input cards were changed.\n");
            for (String note : preview.getNotes()) {
                message.append(note).append('\n');
            }
            alert.setContentText(message.toString());
        } else {
            alert.setContentText(result.getMessage());
        }
        alert.getDialogPane().setPrefWidth(800.0);
        alert.setResizable(true);
        alert.showAndWait();
    }

    /**
     * Diagnose the tail of the current QE log without executing a command or
     * loading an unbounded cluster output into the JavaFX process.
     */
    private void actionDiagnoseLog(QEFXProjectController controller) {
        if (controller == null || this.project.getDirectory() == null) {
            showLogDiagnosis("No project directory is available for log diagnosis.", AlertType.WARNING);
            return;
        }
        Path log = this.project.getDirectory().toPath().resolve(this.project.getLogFileName());
        if (!Files.isRegularFile(log)) {
            showLogDiagnosis("QE log file was not found: " + log, AlertType.WARNING);
            return;
        }
        try {
            String text = readLogTail(log, 2L * 1024L * 1024L);
            ScfConvergenceAnalyzer.Report scf = ScfConvergenceAnalyzer.analyze(text);
            List<QEErrorKnowledgeBase.Diagnosis> diagnoses = QEErrorKnowledgeBase.diagnose(text);
            QETimingResourceParser timing = new QETimingResourceParser(this.project.getProperty());
            timing.parse(log.toFile());

            StringBuilder message = new StringBuilder();
            message.append(ScfConvergenceAnalyzer.formatSummary(scf)).append('\n');
            if (Double.isFinite(timing.getWallTimeSeconds())) {
                message.append(String.format(java.util.Locale.ROOT,
                        "wall=%.2f s cpu=%.2f s ranks=%d memory=%.2f MB fft=%s%n",
                        timing.getWallTimeSeconds(), timing.getCpuTimeSeconds(), timing.getNumProcessors(),
                        timing.getEstimatedMaxMemoryMb(), timing.getFftGrid()));
            }
            if (diagnoses.isEmpty()) {
                message.append("No deterministic QE error signature matched this log tail.");
            } else {
                message.append("\nDiagnoses (review the raw log before changing input):\n");
                for (QEErrorKnowledgeBase.Diagnosis diagnosis : diagnoses) {
                    message.append(diagnosis).append('\n');
                    message.append("  ").append(diagnosis.getSignature().getDocumentationUrl()).append('\n');
                }
            }
            showLogDiagnosis(message.toString(), diagnoses.isEmpty() ? AlertType.INFORMATION : AlertType.WARNING);
        } catch (IOException | RuntimeException ex) {
            showLogDiagnosis("Could not diagnose QE log: " + ex.getMessage(), AlertType.ERROR);
        }
    }

    private void showLogDiagnosis(String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Quantum ESPRESSO log diagnosis");
        alert.setHeaderText(type == AlertType.ERROR ? "Log diagnosis failed" : "Deterministic log analysis");
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(900.0);
        alert.setResizable(true);
        alert.showAndWait();
    }

    private static String readLogTail(Path path, long maximumBytes) throws IOException {
        long size = Files.size(path);
        long start = Math.max(0L, size - maximumBytes);
        int count = (int) (size - start);
        ByteBuffer bytes = ByteBuffer.allocate(count);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(start);
            while (bytes.hasRemaining() && channel.read(bytes) >= 0) {
                // Read until EOF or requested bounded tail.
            }
        }
        String text = StandardCharsets.UTF_8.decode((ByteBuffer) bytes.flip()).toString();
        if (start > 0L) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline >= 0 ? text.substring(firstNewline + 1) : "";
            text = "[Earlier log content omitted; last " + maximumBytes + " bytes analyzed.]\n" + text;
        }
        return text;
    }

    private void actionExportWorkflow(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }
        RunningType type = RunningType.getRunningType(this.project);
        if (type == null) {
            type = RunningType.SCF;
        }
        try {
            QECommandDag dag = type.getCommandDag(this.project, 1);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export workflow script");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Bash script (*.sh)", "*.sh"),
                    new FileChooser.ExtensionFilter("SLURM script (*.slurm)", "*.slurm"));
            File file = chooser.showSaveDialog(controller.getStage());
            if (file == null) {
                return;
            }
            WorkflowExporter.Format format = file.getName().toLowerCase().endsWith(".slurm")
                    ? WorkflowExporter.Format.SLURM : WorkflowExporter.Format.BASH;
            OperationResult<java.nio.file.Path> result = WorkflowExporter.export(
                    file.toPath(), dag, format,
                    this.project.getDirectoryName() == null ? "quantumforge" : this.project.getDirectoryName(),
                    1, 1, null);
            Alert alert = new Alert(result.isSuccess() ? AlertType.INFORMATION : AlertType.ERROR);
            alert.setTitle("Workflow export");
            alert.setHeaderText(result.isSuccess() ? "Workflow exported" : "Export failed");
            alert.setContentText(result.getMessage());
            alert.showAndWait();
        } catch (RuntimeException ex) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Workflow export");
            alert.setHeaderText("Could not build workflow DAG");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Hosts every tested result-analysis backend (bands, magnetization, Born/dielectric,
     * Hubbard hp.x, TDDFT, XANES, NMR, PWcond, Wannier90, thermo_pw EOS, phono3py
     * kappa, Allen-Dynes Tc, and pp.x input previews) behind one chooser dialog. The
     * action is read-only: no command runs, and no file is written without an explicit
     * save confirmation from the user.
     */
    private void actionAnalyzeResults(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }
        AnalysisAction analysisAction = new AnalysisAction(this.project, controller.getStage());
        analysisAction.showAnalysisChooser();
    }

    private void actionXcrysden(QEFXProjectController controller) {
        if (controller == null) {
            return;
        }
        OperationResult<Process> result = XCrySDenLauncher.launch(this.project.getCell());
        Alert alert = new Alert(result.isSuccess() ? AlertType.INFORMATION : AlertType.WARNING);
        alert.setTitle("XCrySDen");
        alert.setHeaderText(result.isSuccess() ? "XCrySDen launched" : "Could not open XCrySDen");
        alert.setContentText(result.getMessage());
        alert.showAndWait();
    }
}
