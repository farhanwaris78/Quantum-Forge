/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.elastic;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;
import quantumforge.project.Project;

public class QEFXElasticButton extends QEFXResultButton<QEFXElasticViewer, QEFXElasticViewerController> {

    public static QEFXResultButtonWrapper<QEFXElasticButton> getWrapper(QEFXProjectController projectController, Project project) {
        return new QEFXResultButtonWrapper<QEFXElasticButton>() {
            @Override
            public QEFXElasticButton getInstance() {
                return new QEFXElasticButton(projectController, project);
            }
        };
    }

    public QEFXElasticButton(QEFXProjectController projectController, Project project) {
        super(projectController, project);
    }

    @Override
    protected String createText() {
        return "Elasticity";
    }

    @Override
    protected SVGData createSVGData() {
        return SVGData.GEAR;
    }

    @Override
    protected QEFXElasticViewer createViewer() {
        return new QEFXElasticViewer(this.projectController, this.project);
    }
}
