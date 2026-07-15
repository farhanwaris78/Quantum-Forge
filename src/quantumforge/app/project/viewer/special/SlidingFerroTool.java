/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;

/**
 * Sliding Ferroelectricity Tool for 2D interfaces.
 */
public class SlidingFerroTool extends VBox {

    public SlidingFerroTool() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Interlayer Sliding Ferroelectricity"));
        
        Label offsetLabel = new Label("Translation Offset (fractional): 0.0");
        Slider slider = new Slider(0, 1, 0);
        slider.valueProperty().addListener((obs, oldV, newV) -> {
            offsetLabel.setText(String.format("Translation Offset: %.3f", newV.doubleValue()));
        });
        
        this.getChildren().addAll(offsetLabel, slider);
    }
}
