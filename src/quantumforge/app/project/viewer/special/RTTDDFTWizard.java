/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

/**
 * RT-TDDFT Wizard for laser-matter interaction.
 */
public class RTTDDFTWizard extends VBox {

    public RTTDDFTWizard() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Real-Time TDDFT Laser Pulse Setup"));
        
        this.getChildren().add(new Label("Pulse Intensity (W/cm2):"));
        this.getChildren().add(new TextField("1.0e12"));
        
        this.getChildren().add(new Label("Frequency (eV):"));
        this.getChildren().add(new TextField("1.55"));
        
        this.getChildren().add(new Button("Generate laser.in"));
    }
}
