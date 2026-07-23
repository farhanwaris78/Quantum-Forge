/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.convergence;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.convergence.QEFXConvergenceEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

public class QEFXConvergenceButton extends QEFXResultButton<QEFXConvergenceViewer, QEFXConvergenceEditor> {

    private static final String BUTTON_TITLE = "Convergence";
    private static final String BUTTON_SUBTITLE = null;
    private static final String BUTTON_BACKGROUND = "-fx-background-color: derive(lightslategrey, -55.0%)";

    public static QEFXResultButtonWrapper<QEFXConvergenceButton> getWrapper(QEFXProjectController projectController, Project project) {
        if (projectController == null) {
            return null;
        }
        return () -> new QEFXConvergenceButton(projectController, project);
    }

    public QEFXConvergenceButton(QEFXProjectController projectController, Project project) {
        super(projectController, BUTTON_TITLE, BUTTON_SUBTITLE);
        this.setIconStyle(BUTTON_BACKGROUND);
    }

    @Override
    protected QEFXConvergenceViewer createResultViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }
        return new QEFXConvergenceViewer(this.projectController);
    }

    @Override
    protected QEFXConvergenceEditor createResultEditor(QEFXConvergenceViewer resultViewer) throws IOException {
        if (resultViewer == null) {
            return null;
        }
        if (this.projectController == null) {
            return null;
        }
        return new QEFXConvergenceEditor(this.projectController, resultViewer);
    }
}
