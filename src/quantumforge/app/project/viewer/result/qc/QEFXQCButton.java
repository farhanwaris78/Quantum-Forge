/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.qc;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;
import quantumforge.project.Project;

public class QEFXQCButton extends QEFXResultButton<QEFXQCViewer, QEFXQCViewerController> {

    public static QEFXResultButtonWrapper<QEFXQCButton> getWrapper(QEFXProjectController projectController, Project project) {
        return new QEFXResultButtonWrapper<QEFXQCButton>() {
            @Override
            public QEFXQCButton getInstance() {
                return new QEFXQCButton(projectController, project);
            }
        };
    }

    public QEFXQCButton(QEFXProjectController projectController, Project project) {
        super(projectController, project);
    }

    @Override
    protected String createText() {
        return "Q-Capacitance";
    }

    @Override
    protected SVGData createSVGData() {
        return SVGData.COLORS;
    }

    @Override
    protected QEFXQCViewer createViewer() {
        return new QEFXQCViewer(this.projectController, this.project);
    }
}
