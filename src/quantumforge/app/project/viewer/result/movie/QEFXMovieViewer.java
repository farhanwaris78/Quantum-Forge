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
import quantumforge.app.project.viewer.atoms.AtomsAction;
import quantumforge.app.project.viewer.result.QEFXResultViewer;
import quantumforge.atoms.design.Design;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.AtomsViewerInterface;
import quantumforge.project.property.ProjectProperty;
import javafx.scene.layout.BorderPane;

public class QEFXMovieViewer extends QEFXResultViewer<QEFXMovieViewerController> {

    private Design design;

    public QEFXMovieViewer(QEFXProjectController projectController, ProjectProperty projectProperty, Cell cell,
            boolean mdMode) {

        super(cell == null ? null : new AtomsViewer(cell, AtomsAction.getAtomsViewerSize(), true),
                new QEFXMovieViewerController(projectController, projectProperty, cell, mdMode));

        if (this.node != null && (this.node instanceof AtomsViewerInterface)) {
            this.setupAtomsViewer((AtomsViewerInterface) this.node, projectController);
        }

        this.design = null;
    }

    private void setupAtomsViewer(AtomsViewerInterface atomsViewer, QEFXProjectController projectController) {
        if (atomsViewer == null) {
            return;
        }

        final BorderPane projectPane;
        if (projectController != null) {
            projectPane = projectController.getProjectPane();
        } else {
            projectPane = null;
        }

        if (projectPane != null) {
            atomsViewer.addExclusiveNode(() -> {
                return projectPane.getRight();
            });
            atomsViewer.addExclusiveNode(() -> {
                return projectPane.getBottom();
            });
        }
    }

    @Override
    public void reload() {
        this.setDesign(this.design);
        super.reload();
    }

    public void setDesign(Design design) {
        if (design == null) {
            return;
        }

        this.design = design;

        if (this.node != null && (this.node instanceof AtomsViewer)) {
            ((AtomsViewer) this.node).setDesign(this.design);
        }
    }
}
