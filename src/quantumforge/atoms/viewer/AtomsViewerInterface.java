/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer;

import quantumforge.atoms.model.Cell;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

public abstract class AtomsViewerInterface extends Group {

    protected AtomsViewerInterface() {
        super();
    }

    public abstract Cell getCell();

    public abstract double getSceneWidth();

    public abstract double getSceneHeight();

    public abstract void setSceneStyle(String style);

    public abstract void addExclusiveNode(Node node);

    public abstract void addExclusiveNode(NodeWrapper nodeWrapper);

    public abstract void startExclusiveMode();

    public abstract void stopExclusiveMode();

    public abstract void bindSceneTo(Pane pane);

    public abstract void unbindScene();

}
