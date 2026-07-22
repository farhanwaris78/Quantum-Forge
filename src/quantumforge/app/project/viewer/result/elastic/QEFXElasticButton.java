/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.elastic;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.elastic.QEFXElasticEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

public class QEFXElasticButton extends QEFXResultButton<QEFXElasticViewer, QEFXElasticEditor> {

    private static final String BUTTON_TITLE = "Elasticity";
    private static final String BUTTON_SUBTITLE = null;
    private static final String BUTTON_BACKGROUND = "-fx-background-color: derive(grey, -20.0%)";

    public static QEFXResultButtonWrapper<QEFXElasticButton> getWrapper(QEFXProjectController projectController, Project project) {
        if (projectController == null) {
            return null;
        }
        return () -> new QEFXElasticButton(projectController, project);
    }

    public QEFXElasticButton(QEFXProjectController projectController, Project project) {
        super(projectController, BUTTON_TITLE, BUTTON_SUBTITLE);
        this.setIconStyle(BUTTON_BACKGROUND);
    }

    @Override
    protected QEFXElasticViewer createResultViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }
        return new QEFXElasticViewer(this.projectController);
    }

    @Override
    protected QEFXElasticEditor createResultEditor(QEFXElasticViewer resultViewer) throws IOException {
        if (resultViewer == null) {
            return null;
        }
        if (this.projectController == null) {
            return null;
        }
        return new QEFXElasticEditor(this.projectController, resultViewer);
    }
}
