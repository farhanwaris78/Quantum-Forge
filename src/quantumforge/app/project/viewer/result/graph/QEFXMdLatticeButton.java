/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.graph;

import java.io.File;
import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;

public class QEFXMdLatticeButton extends QEFXGraphButton<QEFXLatticeViewer> {

    private static final String FILE_NAME = ".quantumforge.graph.md.latt";

    private static final String BUTTON_TITLE = "MD";
    private static final String BUTTON_SUBTITLE = ".latt";
    private static final String BUTTON_FONT_COLOR = "-fx-text-fill: derive(limegreen, -20.0%)";
    private static final String BUTTON_BACKGROUND = "-fx-background-color: snow";

    public static QEFXResultButtonWrapper<QEFXMdLatticeButton> getWrapper(
            QEFXProjectController projectController, Project project, LatticeViewerType lattVType) {

        if (projectController == null) {
            return null;
        }

        ProjectProperty projectProperty = project == null ? null : project.getProperty();
        if (projectProperty == null) {
            return null;
        }

        if (lattVType == null) {
            return null;
        }

        ProjectGeometryList projectGeometryList = projectProperty.getMdList();
        if (projectGeometryList == null || projectGeometryList.numGeometries() < 2) {
            return null;
        }

        if (!(new GeometryChecker(projectGeometryList).isAvailableLattice(lattVType))) {
            return null;
        }

        return () -> {
            QEFXMdLatticeButton button = new QEFXMdLatticeButton(projectController, projectProperty, lattVType);

            String propPath = project == null ? null : project.getDirectoryPath();
            File propFile = propPath == null ? null : new File(propPath, FILE_NAME + "." + lattVType.toString());
            if (propFile != null) {
                button.setPropertyFile(propFile);
            }

            return button;
        };
    }

    private LatticeViewerType lattVType;

    private ProjectProperty projectProperty;

    private QEFXMdLatticeButton(QEFXProjectController projectController,
            ProjectProperty projectProperty, LatticeViewerType lattVType) {

        super(projectController,
                BUTTON_TITLE, BUTTON_SUBTITLE + "." + (lattVType == null ? "" : lattVType.name()));

        if (projectProperty == null) {
            throw new IllegalArgumentException("projectProperty is null.");
        }

        if (lattVType == null) {
            throw new IllegalArgumentException("lattVType is null.");
        }

        this.projectProperty = projectProperty;
        this.lattVType = lattVType;

        this.setIconStyle(BUTTON_BACKGROUND);
        this.setLabelStyle(BUTTON_FONT_COLOR);
    }

    @Override
    protected QEFXLatticeViewer createGraphViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }

        return new QEFXLatticeViewer(this.projectController, this.projectProperty, this.lattVType, true);
    }
}
