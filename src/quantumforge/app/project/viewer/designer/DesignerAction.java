/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.designer;

import java.io.File;
import java.io.IOException;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.editor.designer.QEFXDesignerEditor;
import quantumforge.app.project.viewer.atoms.AtomsAction;
import quantumforge.atoms.design.Design;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.AtomsViewerInterface;
import quantumforge.project.Project;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public class DesignerAction {

    private Project project;

    private QEFXProjectController controller;

    private QEFXDesignerViewer designerViewer;

    public DesignerAction(Project project, QEFXProjectController controller) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }

        this.project = project;
        this.controller = controller;

        this.designerViewer = null;
    }

    public QEFXProjectController getController() {
        return this.controller;
    }

    public void showDesigner() {
        if (this.designerViewer == null) {
            this.initializeDesigner();
            return;
        }

        this.controller.setDesignerMode();
    }

    private void initializeDesigner() {
        final AtomsViewer atomsViewer;
        AtomsViewerInterface atomsViewerInterface = this.controller.getAtomsViewer();
        if (atomsViewerInterface != null && atomsViewerInterface instanceof AtomsViewer) {
            atomsViewer = (AtomsViewer) atomsViewerInterface;
        } else {
            atomsViewer = null;
        }

        if (this.designerViewer == null) {
            try {
                this.designerViewer = this.createDesignerViewer(atomsViewer);
            } catch (IOException e) {
                this.designerViewer = null;
                e.printStackTrace();
            }
        }

        QEFXDesignerEditor designerEditor = null;
        if (this.designerViewer != null) {
            try {
                designerEditor = this.createDesignerEditor(this.designerViewer);
            } catch (IOException e) {
                designerEditor = null;
                e.printStackTrace();
            }
        }

        if (designerEditor != null && this.designerViewer != null) {
            this.controller.setDesignerMode(controller2 -> {
                Design srcDesign = atomsViewer == null ? null : atomsViewer.getDesign();
                if (srcDesign != null) {
                    this.designerViewer.setDesign(srcDesign, false, true);
                }

                this.designerViewer.centerAtomsViewer();
            });

            if (atomsViewer != null) {
                this.controller.setOnModeBacked(controller2 -> {
                    Design dstDesign = this.designerViewer.getDesign();
                    if (dstDesign != null) {
                        atomsViewer.setDesign(dstDesign);
                    }
                    return true;
                });
            }

            this.controller.clearStackedsOnViewerPane();

            Node viewerNode = this.designerViewer.getNode();
            if (viewerNode != null) {
                this.controller.setViewerPane(viewerNode);
            }

            Node editorNode = designerEditor.getNode();
            if (editorNode != null) {
                this.controller.setEditorPane(editorNode);
            }
        }
    }

    private QEFXDesignerViewer createDesignerViewer(AtomsViewer atomsViewer) throws IOException {
        Cell cell = this.project.getCell();
        if (cell == null) {
            return null;
        }

        QEFXDesignerViewer designerViewer = new QEFXDesignerViewer(this.controller, cell);

        if (atomsViewer != null) {
            Design srcDesign = atomsViewer.getDesign();
            if (srcDesign != null) {
                designerViewer.setDesign(srcDesign, true, true);
            }
        }

        final BorderPane projectPane;
        if (this.controller != null) {
            projectPane = this.controller.getProjectPane();
        } else {
            projectPane = null;
        }

        if (projectPane != null) {
            designerViewer.addExclusiveNode(() -> {
                return projectPane.getRight();
            });
            designerViewer.addExclusiveNode(() -> {
                return projectPane.getBottom();
            });
        }

        return designerViewer;
    }

    private QEFXDesignerEditor createDesignerEditor(QEFXDesignerViewer designerViewer) throws IOException {
        if (designerViewer == null) {
            return null;
        }

        QEFXDesignerEditor designerEditor = new QEFXDesignerEditor(this.controller, designerViewer);

        File designFile = AtomsAction.getAtomsDesignFile(this.project);
        if (designFile != null) {
            designerEditor.setWritingFile(designFile);
        }

        this.project.addOnFilePathChanged(path -> {
            File designFile_ = AtomsAction.getAtomsDesignFile(this.project);
            if (designFile_ != null) {
                designerEditor.setWritingFile(designFile_);
            }
        });

        return designerEditor;
    }
}
