/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

/**
 * Topological Analysis Hub.
 * Link to WannierBerri/Z2Pack.
 */
public class TopologyHub extends VBox {

    public TopologyHub() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Topological Invariants & Berry Curvature"));
        
        this.getChildren().add(new Button("Detect Z2 Invariant"));
        this.getChildren().add(new Button("Calculate Chern Number"));
        this.getChildren().add(new Button("Plot Anomalous Hall Conductivity"));
    }
}
