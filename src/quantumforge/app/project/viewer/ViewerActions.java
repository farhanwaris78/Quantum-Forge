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
import java.util.Optional;

import quantumforge.app.project.ProjectAction;
import quantumforge.app.project.ProjectActions;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.atoms.AtomsAction;
import quantumforge.app.project.viewer.designer.DesignerAction;
import quantumforge.app.project.viewer.inputfile.QEFXInputFile;
import quantumforge.app.project.viewer.modeler.ModelerAction;
import quantumforge.app.project.viewer.result.ResultAction;
import quantumforge.app.project.viewer.run.QEFXRunDialog;
import quantumforge.app.project.viewer.run.RunAction;
import quantumforge.app.project.viewer.run.RunEvent;
import quantumforge.app.project.viewer.recovery.RecoveryAction;
import quantumforge.app.project.viewer.save.SaveAction;
import quantumforge.app.project.viewer.screenshot.QEFXScreenshotDialog;
import quantumforge.export.AtomicExporter;
import quantumforge.project.Project;
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

            } else if (item == this.itemSet.getResultItem()) {
                this.actions.put(item, controller2 -> this.actionResult(controller2));

            } else if (item == this.itemSet.getExportItem()) {
                this.actions.put(item, controller2 -> this.actionExport(controller2));
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

    private void startLiveMonitoring(RunEvent runEvent) {
        RunningNode node = runEvent.getRunningNode();
        if (node == null) return;

        Thread liveThread = new Thread(() -> {
            try {
                // Wait for the process to actually start and create files
                Thread.sleep(3000); 
                while (RunningManager.getInstance().getNode(this.project) != null) {
                    Platform.runLater(() -> {
                        // Refresh the results if we are currently looking at them
                        if (this.controller != null && this.controller.isResultViewerMode()) {
                            // Find scf result and reload
                        }
                    });
                    Thread.sleep(LIVE_PLOT_RELOAD);
                }
            } catch (InterruptedException e) {
                // Ignore
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
}
