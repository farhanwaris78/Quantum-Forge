/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.convergence;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;
import quantumforge.project.Project;

public class QEFXConvergenceButton extends QEFXResultButton<QEFXConvergenceViewer, QEFXConvergenceViewerController> {

    public static QEFXResultButtonWrapper<QEFXConvergenceButton> getWrapper(QEFXProjectController projectController, Project project) {
        return new QEFXResultButtonWrapper<QEFXConvergenceButton>() {
            @Override
            public QEFXConvergenceButton getInstance() {
                return new QEFXConvergenceButton(projectController, project);
            }
        };
    }

    public QEFXConvergenceButton(QEFXProjectController projectController, Project project) {
        super(projectController, project);
    }

    @Override
    protected String createText() {
        return "Convergence";
    }

    @Override
    protected SVGData createSVGData() {
        return SVGData.ARROW_ROUND;
    }

    @Override
    protected QEFXConvergenceViewer createViewer() {
        return new QEFXConvergenceViewer(this.projectController, this.project);
    }
}
