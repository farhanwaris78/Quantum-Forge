/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.QEFXResultEditor;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;
import quantumforge.project.Project;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import quantumforge.export.MLPTrainingExporter;
import quantumforge.project.property.ProjectGeometryList;

public class QEFXMLPExportButton extends QEFXResultButton<QEFXResultViewer<?>, QEFXResultEditor<?>> {

    public static QEFXResultButtonWrapper<QEFXMLPExportButton> getWrapper(QEFXProjectController projectController, Project project) {
        return () -> new QEFXMLPExportButton(projectController, project);
    }

    private Project project;

    public QEFXMLPExportButton(QEFXProjectController projectController, Project project) {
        super(projectController, "MLP Export", "DeepMD/ExtXYZ");
        this.project = project;
        this.setIconStyle("-fx-background-color: derive(darkgreen, -20%)");
    }

    @Override
    protected QEFXResultViewer<?> createResultViewer() throws IOException {
        return null;
    }

    @Override
    protected QEFXResultEditor<?> createResultEditor(QEFXResultViewer<?> viewer) throws IOException {
        return null;
    }

    @Override
    protected void onIconClicked() {
        exportData();
    }

    private void exportData() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export MLP Training Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Extended XYZ (*.xyz)", "*.xyz"));
        File file = fileChooser.showSaveDialog(projectController.getStage());
        if (file != null) {
            try {
                ProjectGeometryList list = project.getProperty().getMdList();
                if (list.numGeometries() == 0) {
                    list = project.getProperty().getOptList();
                }
                MLPTrainingExporter.exportToExtXYZ(list, file.getPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
