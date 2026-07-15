/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.band;

import java.io.File;
import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.result.band.QEFXBandEditor;
import quantumforge.app.project.viewer.result.QEFXResultButton;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;
import quantumforge.project.property.BandData;
import quantumforge.project.property.ProjectBand;
import quantumforge.project.property.ProjectBandPaths;
import quantumforge.project.property.ProjectEnergies;
import quantumforge.project.property.ProjectProperty;
import quantumforge.project.property.ProjectStatus;

public class QEFXBandButton extends QEFXResultButton<QEFXBandViewer, QEFXBandEditor> {

    private static final String FILE_NAME = ".quantumforge.graph.band";

    private static final String BUTTON_TITLE = "BAND";
    private static final String BUTTON_FONT_COLOR = "-fx-text-fill: ivory";
    private static final String BUTTON_BACKGROUND = "-fx-background-color: derive(lightslategrey, -55.0%)";

    public static QEFXResultButtonWrapper<QEFXBandButton> getWrapper(QEFXProjectController projectController, Project project) {
        if (projectController == null) {
            return null;
        }

        ProjectProperty projectProperty = project == null ? null : project.getProperty();
        if (projectProperty == null) {
            return null;
        }

        ProjectStatus projectStatus = projectProperty.getStatus();
        if (projectStatus == null || (!projectStatus.isBandDone())) {
            return null;
        }

        ProjectEnergies projectEnergies = projectProperty.getFermiEnergies();
        if (projectEnergies == null || projectEnergies.numEnergies() < 1) {
            return null;
        }

        ProjectBand projectBand = projectProperty.getBand();
        if (projectBand == null) {
            return null;
        }

        ProjectBandPaths projectBandPaths = projectProperty.getBandPaths();
        if (projectBandPaths == null || projectBandPaths.numPoints() < 1) {
            return null;
        }

        BandData bandData = projectBand.getBandData();
        if (bandData == null) {
            return null;
        }

        String dirPath = project == null ? null : project.getDirectoryPath();
        String fileName = project == null ? null : (project.getPrefixName() + ".band1.gnu");

        File file = null;
        if (dirPath != null && fileName != null) {
            file = new File(dirPath, fileName);
        }

        try {
            if (file == null || (!file.isFile()) || (file.length() <= 0L)) {
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return () -> {
            QEFXBandButton button = new QEFXBandButton(projectController, projectProperty);

            String propPath = project == null ? null : project.getDirectoryPath();
            File propFile = propPath == null ? null : new File(propPath, FILE_NAME);
            if (propFile != null) {
                button.propertyFile = propFile;
            }

            return button;
        };
    }

    private File propertyFile;

    private ProjectProperty projectProperty;

    private QEFXBandButton(QEFXProjectController projectController, ProjectProperty projectProperty) {
        super(projectController, BUTTON_TITLE, null);

        if (projectProperty == null) {
            throw new IllegalArgumentException("projectProperty is null.");
        }

        this.propertyFile = null;
        this.projectProperty = projectProperty;

        this.setIconStyle(BUTTON_BACKGROUND);
        this.setLabelStyle(BUTTON_FONT_COLOR);
    }

    @Override
    protected final QEFXBandViewer createResultViewer() throws IOException {
        if (this.projectController == null) {
            return null;
        }

        QEFXBandViewer viewer = new QEFXBandViewer(this.projectController, this.projectProperty);

        if (viewer != null) {
            QEFXBandViewerController controller = viewer.getController();
            if (controller != null) {
                controller.setPropertyFile(this.propertyFile);
            }
        }

        return viewer;
    }

    @Override
    protected final QEFXBandEditor createResultEditor(QEFXBandViewer resultViewer) throws IOException {
        if (resultViewer == null) {
            return null;
        }

        if (this.projectController == null) {
            return null;
        }

        return new QEFXBandEditor(this.projectController, resultViewer);
    }
}
