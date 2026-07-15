/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

/**
 * Photocatalytic Analysis Tool.
 */
public class PhotocatalyticTool extends VBox {

    public PhotocatalyticTool() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Photocatalytic Band Alignment"));
        this.getChildren().add(new Button("Plot vs. HER/OER Potentials"));
        this.getChildren().add(new Button("Calculate Solar-to-Hydrogen (STH) %"));
    }
}
