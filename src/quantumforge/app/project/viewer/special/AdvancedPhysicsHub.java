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
 * Advanced Physics & Topology Hub.
 * Features for high-impact publications.
 */
public class AdvancedPhysicsHub extends VBox {

    public AdvancedPhysicsHub() {
        this.setSpacing(10);
        this.setStyle("-fx-padding: 20;");
        
        Label title = new Label("Advanced Physics (Topological & Correlated)");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        
        this.getChildren().addAll(title, new Separator());

        // Axion Insulator
        Button axionBtn = new Button("Axion Insulator Tool");
        axionBtn.setTooltip(new javafx.scene.control.Tooltip("Calculate magnetoelectric coupling and Chern-Simons term."));
        
        // Majorana Tracker
        Button majoranaBtn = new Button("Majorana Zero-Mode Tracker");
        majoranaBtn.setTooltip(new javafx.scene.control.Tooltip("Identify topological superconductivity in SC-Semiconductor junctions."));
        
        // Flat-Band Engineering
        Button flatBandBtn = new Button("Flat-Band & Magic-Angle Designer");
        flatBandBtn.setTooltip(new javafx.scene.control.Tooltip("Analyze Moiré bands and suggest twist angles for flat-band states."));
        
        // Berry Curvature Dipole
        Button berryBtn = new Button("Berry Curvature Dipole Mapper");
        berryBtn.setTooltip(new javafx.scene.control.Tooltip("Calculate non-linear Hall effects for non-centrosymmetric systems."));

        this.getChildren().addAll(axionBtn, majoranaBtn, flatBandBtn, berryBtn);
    }
}
