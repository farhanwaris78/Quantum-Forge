/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.movie;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;

public class QEFXOptMovieButton extends QEFXMovieButton {

    private static final String BUTTON_TITLE = "OPT";
    private static final String BUTTON_SUBTITLE = ".movie";
    private static final String BUTTON_FONT_COLOR = "-fx-text-fill: snow";
    private static final String BUTTON_BACKGROUND = "-fx-background-color: mediumorchid";

    public static QEFXResultButtonWrapper<QEFXOptMovieButton> getWrapper(QEFXProjectController projectController, Project project) {
        if (projectController == null) {
            return null;
        }

        if (project == null) {
            return null;
        }

        ProjectProperty projectProperty = project.getProperty();
        if (projectProperty == null) {
            return null;
        }

        ProjectGeometryList projectGeometryList = projectProperty.getOptList();
        if (projectGeometryList == null || projectGeometryList.numGeometries() < 1) {
            return null;
        }

        if (!projectGeometryList.hasAnyConvergedGeometries()) {
            return null;
        }

        return () -> new QEFXOptMovieButton(projectController, project, projectProperty);
    }

    private QEFXOptMovieButton(QEFXProjectController projectController, Project project, ProjectProperty projectProperty) {
        super(projectController, project, projectProperty, BUTTON_TITLE, BUTTON_SUBTITLE, false);

        this.setIconStyle(BUTTON_BACKGROUND);
        this.setLabelStyle(BUTTON_FONT_COLOR);
    }
}
