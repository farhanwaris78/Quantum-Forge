/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.designer;

import java.io.File;
import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.designer.QEFXDesignerViewer;
import quantumforge.atoms.design.Design;
import quantumforge.com.keys.PriorKeyEvent;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

public class QEFXDesignerEditor extends QEFXAppComponent<QEFXDesignerEditorController> {

    public QEFXDesignerEditor(QEFXProjectController projectController, QEFXDesignerViewer viewer) throws IOException {
        super("QEFXDesignerEditor.fxml", new QEFXDesignerEditorController(projectController, viewer));

        if (this.node != null) {
            this.node.setOnMouseReleased(event -> this.node.requestFocus());
        }

        if (this.node != null && viewer != null) {
            this.setupEditorKeys(this.node, viewer);
        }

        if (viewer != null) {
            this.setupViewerKeys(viewer);
        }
    }

    public void setWritingFile(File file) {
        this.setWritingPath(file == null ? null : file.getPath());
    }

    public void setWritingPath(String path) {
        if (this.controller != null) {
            this.controller.setWritingPath(path);
        }
    }

    private void writeDesignToFile() {
        if (this.controller != null) {
            this.controller.writeDesignToFile();
        }
    }

    private void setupEditorKeys(Node node, QEFXDesignerViewer viewer) {
        if (node == null) {
            return;
        }

        node.setOnKeyPressed(event -> {
            if (event == null) {
                return;
            }

            if (PriorKeyEvent.isPriorKeyEvent(event)) {
                return;
            }

            if (event.isShortcutDown() && KeyCode.Z.equals(event.getCode())) {
                Design design = viewer == null ? null : viewer.getDesign();

                if (!event.isShiftDown()) {
                    // Shortcut + Z
                    if (design != null) {
                        design.restoreDesign();
                    }
                    if (this.controller != null) {
                        this.controller.refreshEditor();
                    }

                } else {
                    // Shortcut + Shift + Z
                    if (design != null) {
                        design.subRestoreDesign();
                    }
                    if (this.controller != null) {
                        this.controller.refreshEditor();
                    }
                }

            } else if (event.isShortcutDown() && KeyCode.C.equals(event.getCode())) {
                // Shortcut + C
                if (viewer != null) {
                    viewer.centerAtomsViewer();
                }
            }
        });
    }

    private void setupViewerKeys(QEFXDesignerViewer viewer) {
        if (viewer == null) {
            return;
        }

        viewer.setOnKeyPressed(event -> {
            if (event == null) {
                return;
            }

            if (PriorKeyEvent.isPriorKeyEvent(event)) {
                return;
            }

            if (event.isShortcutDown() && KeyCode.Z.equals(event.getCode())) {
                Design design = viewer.getDesign();

                if (!event.isShiftDown()) {
                    // Shortcut + Z
                    if (design != null) {
                        design.restoreDesign();
                    }
                    if (this.controller != null) {
                        this.controller.refreshEditor();
                    }

                } else {
                    // Shortcut + Shift + Z
                    if (design != null) {
                        design.subRestoreDesign();
                    }
                    if (this.controller != null) {
                        this.controller.refreshEditor();
                    }
                }
            }
        });
    }
}
