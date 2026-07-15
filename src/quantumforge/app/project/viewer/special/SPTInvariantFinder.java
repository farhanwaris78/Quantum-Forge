/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

/**
 * Symmetry-Protected Topological (SPT) Invariant Finder.
 */
public class SPTInvariantFinder extends VBox {

    public SPTInvariantFinder() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Symmetry-Protected Topological (SPT) Analysis"));
        this.getChildren().add(new Button("Check Inversion Symmetry Parity"));
        this.getChildren().add(new Button("Calculate C2/C3/C4 Invariants"));
        this.getChildren().add(new Button("Identify 2D Topological Insulator Class"));
    }
}
