/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;

/**
 * Green Energy & Catalysis Hub.
 */
public class EnergyCatalysisHub extends VBox {

    public EnergyCatalysisHub() {
        this.setSpacing(10);
        this.setStyle("-fx-padding: 20;");
        
        Label title = new Label("Green Energy & Catalysis");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        
        this.getChildren().addAll(title, new Separator());

        // SAC Hub
        Button sacBtn = new Button("Single-Atom Catalyst (SAC) Hub");
        sacBtn.setTooltip(new javafx.scene.control.Tooltip("Design 2D materials with atomic-scale catalytic centers."));
        
        // Solid-State Diffusivity
        Button diffBtn = new Button("Solid-State Diffusivity (MSD)");
        diffBtn.setTooltip(new javafx.scene.control.Tooltip("Calculate ion conductivity from Mean Squared Displacement of MD runs."));
        
        // Solar-to-Hydrogen
        Button sthBtn = new Button("Solar-to-Hydrogen (STH%) Estimator");
        sthBtn.setTooltip(new javafx.scene.control.Tooltip("Predict photocatalytic device efficiency from band edge alignment."));

        this.getChildren().addAll(sacBtn, diffBtn, sthBtn);
    }
}
