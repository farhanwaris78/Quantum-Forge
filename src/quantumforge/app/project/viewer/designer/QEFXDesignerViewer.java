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

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.atoms.design.Design;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.viewer.NodeWrapper;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;

public class QEFXDesignerViewer extends QEFXAppComponent<QEFXDesignerViewerController> {

    public QEFXDesignerViewer(QEFXProjectController projectController, Cell cell) throws IOException {
        super("QEFXDesignerViewer.fxml", new QEFXDesignerViewerController(projectController, cell));
    }

    public void centerAtomsViewer() {
        if (this.controller != null) {
            this.controller.centerAtomsViewer();
        }
    }

    public void addExclusiveNode(Node node) {
        if (this.controller != null) {
            this.controller.addExclusiveNode(node);
        }
    }

    public void addExclusiveNode(NodeWrapper nodeWrapper) {
        if (this.controller != null) {
            this.controller.addExclusiveNode(nodeWrapper);
        }
    }

    public void setOnKeyPressed(EventHandler<? super KeyEvent> value) {
        if (this.controller != null) {
            this.controller.setOnKeyPressed(value);
        }
    }

    public Design getDesign() {
        return this.controller == null ? null : this.controller.getDesign();
    }

    public void setDesign(Design design, boolean prim, boolean dual) {
        if (this.controller != null) {
            this.controller.setDesign(design, prim, dual);
        }
    }

}
