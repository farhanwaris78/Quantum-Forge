/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.qc;

import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.qc.QEFXQCEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

public class QEFXQCButton extends QEFXResultButton<QEFXQCViewer, QEFXQCEditor> {

    private static final String BUTTON_TITLE = "Q-Capacitance";
    private static final String BUTTON_SUBTITLE = null;
    private static final String BUTTON_BACKGROUND = "-fx-background-color: derive(midnightblue, -10.0%)";

    private Project project;

    public static QEFXResultButtonWrapper<QEFXQCButton> getWrapper(QEFXProjectController projectController, Project project) {
        if (projectController == null) {
            return null;
        }
        return () -> new QEFXQCButton(projectController, project);
    }

    public QEFXQCButton(QEFXProjectController projectController, Project project) {
        super(projectController, BUTTON_TITLE, BUTTON_SUBTITLE);
        this.project = project;
        this.setIconStyle(BUTTON_BACKGROUND);
    }

    @Override
    protected QEFXQCViewer createResultViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }
        if (this.project == null) {
            return null;
        }
        return new QEFXQCViewer(this.projectController, this.project);
    }

    @Override
    protected QEFXQCEditor createResultEditor(QEFXQCViewer resultViewer) throws IOException {
        if (resultViewer == null) {
            return null;
        }
        if (this.projectController == null) {
            return null;
        }
        return new QEFXQCEditor(this.projectController, resultViewer);
    }
}
