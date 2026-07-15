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

public class QEFXMdEnergyButton extends QEFXGraphButton<QEFXEnergyViewer> {

    private static final String FILE_NAME = ".quantumforge.graph.md";

    private static final String BUTTON_TITLE = "MD";
    private static final String BUTTON_FONT_COLOR = "-fx-text-fill: limegreen";
    private static final String BUTTON_BACKGROUND = "-fx-background-color: snow";

    public static QEFXResultButtonWrapper<QEFXMdEnergyButton> getWrapper(
            QEFXProjectController projectController, Project project, EnergyType energyType) {

        if (projectController == null) {
            return null;
        }

        ProjectProperty projectProperty = project == null ? null : project.getProperty();
        if (projectProperty == null) {
            return null;
        }

        if (energyType == null) {
            return null;
        }

        ProjectGeometryList projectGeometryList = projectProperty.getMdList();
        if (projectGeometryList == null || projectGeometryList.numGeometries() < 1) {
            return null;
        }

        if (!projectGeometryList.hasAnyConvergedGeometries()) {
            return null;
        }

        return () -> {
            QEFXMdEnergyButton button = new QEFXMdEnergyButton(projectController, projectProperty, energyType);

            String propPath = project == null ? null : project.getDirectoryPath();
            File propFile = propPath == null ? null : new File(propPath, FILE_NAME + "." + energyType.getSymbol());
            if (propFile != null) {
                button.setPropertyFile(propFile);
            }

            return button;
        };
    }

    private EnergyType energyType;

    private ProjectProperty projectProperty;

    private QEFXMdEnergyButton(
            QEFXProjectController projectController, ProjectProperty projectProperty, EnergyType energyType) {

        super(projectController, BUTTON_TITLE, "." + (energyType == null ? "" : energyType.getSymbol()));

        if (projectProperty == null) {
            throw new IllegalArgumentException("projectProperty is null.");
        }

        if (energyType == null) {
            throw new IllegalArgumentException("energyType is null.");
        }

        this.projectProperty = projectProperty;
        this.energyType = energyType;

        this.setIconStyle(BUTTON_BACKGROUND);
        this.setLabelStyle(BUTTON_FONT_COLOR);
    }

    @Override
    protected QEFXEnergyViewer createGraphViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }

        return new QEFXEnergyViewer(this.projectController, this.projectProperty, this.energyType, true);
    }
}
