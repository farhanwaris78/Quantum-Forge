/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

/**
 * CatMAP Bridge: Exports adsorption energies for microkinetic modeling.
 */
public class CatMAPBridge extends VBox {

    public CatMAPBridge() {
        this.setSpacing(10);
        this.getChildren().add(new Label("CatMAP Kinetic Modeling Bridge"));
        this.getChildren().add(new Button("Export Adsorption Table (mkm)"));
        this.getChildren().add(new Button("Format Scaling Relations"));
    }
}
