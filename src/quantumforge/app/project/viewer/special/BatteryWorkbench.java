/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

/**
 * Battery Research Workbench.
 */
public class BatteryWorkbench extends VBox {

    public BatteryWorkbench() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Battery & Ion Transport Analysis"));
        
        this.getChildren().add(new Button("Generate Diffusion NEB Path"));
        this.getChildren().add(new Button("Plot Voltage Profile (OCV)"));
        this.getChildren().add(new Button("Calculate Ion Activation Map"));
    }
}
