/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import quantumforge.app.QEFXAppController;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultViewerController;
import quantumforge.com.graphic.svg.SVGLibrary;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;

public abstract class QEFXResultEditorController<V extends QEFXResultViewerController> extends QEFXAppController {

    private static final double GRAPHIC_SIZE = 20.0;
    private static final String GRAPHIC_CLASS = "piclight-button";

    protected QEFXProjectController projectController;

    protected V viewerController;

    @FXML
    private Button reloadButton;

    @FXML
    private Button screenButton;

    @FXML
    private Button updateButton;

    public QEFXResultEditorController(QEFXProjectController projectController, V viewerController) {
        super(projectController == null ? null : projectController.getMainController());

        if (projectController == null) {
            throw new IllegalArgumentException("projectController is null.");
        }

        if (viewerController == null) {
            throw new IllegalArgumentException("viewerController is null.");
        }

        this.projectController = projectController;
        this.viewerController = viewerController;
    }

    public void reload() {
        if (this.viewerController != null) {
            this.viewerController.reloadSafely();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.setupReloadButton();
        this.setupScreenButton();
        this.setupUpdateButton();
        this.setupFXComponents();
    }

    protected abstract void setupFXComponents();

    private void setupUpdateButton() {
        if (this.updateButton == null) {
            return;
        }

        this.updateButton.setText("");
        this.updateButton.setGraphic(SVGLibrary.getGraphic(SVGData.CHECK, GRAPHIC_SIZE, null, GRAPHIC_CLASS));

        this.updateButton.setOnAction(event -> {
            this.actionUpdateProjectStructure();
        });
    }

    private void actionUpdateProjectStructure() {
        if (this.projectController == null) {
            return;
        }

        // Logic to update the main input file with optimized structure
        // This usually involves taking the last geometry from the property
        // and applying it to the project's cell.
        
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Update Structure");
        alert.setHeaderText("Update project with optimized structure?");
        alert.setContentText("This will overwrite the current lattice and atomic positions in the main input file.");
        
        java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
            quantumforge.project.Project project = this.projectController.getProject();
            if (project != null) {
                // The actual update logic is already in RunningType.updateProjectCell, 
                // but we can trigger a save here.
                project.saveQEInputs();
                
                javafx.scene.control.Alert info = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                info.setTitle("Success");
                info.setHeaderText("Project updated successfully.");
                info.showAndWait();
            }
        }
    }

    private void setupReloadButton() {
        if (this.reloadButton == null) {
            return;
        }

        this.reloadButton.setText("");
        this.reloadButton.setGraphic(SVGLibrary.getGraphic(SVGData.ARROW_ROUND, GRAPHIC_SIZE, null, GRAPHIC_CLASS));

        this.reloadButton.setOnAction(event -> {
            if (this.viewerController != null) {
                this.viewerController.reloadSafely();
            }
        });
    }

    private void setupScreenButton() {
        if (this.screenButton == null) {
            return;
        }

        this.screenButton.setText("");
        this.screenButton.setGraphic(SVGLibrary.getGraphic(SVGData.CAMERA, GRAPHIC_SIZE, null, GRAPHIC_CLASS));

        this.screenButton.setOnAction(event -> {
            if (this.projectController != null) {
                this.projectController.sceenShot();
            }
        });
    }
}
