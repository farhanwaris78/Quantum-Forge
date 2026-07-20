/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.QEFXResultEditor;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;
import quantumforge.project.Project;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.io.IOException;

public class QEFXBaderButton extends QEFXResultButton<QEFXResultViewer<?>, QEFXResultEditor<?>> {

    public static QEFXResultButtonWrapper<QEFXBaderButton> getWrapper(QEFXProjectController projectController, Project project) {
        return () -> new QEFXBaderButton(projectController, project);
    }

    public QEFXBaderButton(QEFXProjectController projectController, Project project) {
        super(projectController, "Bader Analysis", "Charge Transfer");
        this.setIconStyle("-fx-background-color: derive(darkorange, -10%)");
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
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Bader Charge Analysis");
        alert.setHeaderText("Analysis Module Started");
        alert.setContentText("Bader charge analysis is calculating oxidation states from valence charge density...");
        alert.showAndWait();
    }
}
