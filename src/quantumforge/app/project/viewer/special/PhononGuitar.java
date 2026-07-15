/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.special;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

/**
 * Phonon Mode Guitar: Audio-visual vibration analysis.
 */
public class PhononGuitar extends VBox {

    public PhononGuitar() {
        this.setSpacing(10);
        this.getChildren().add(new Label("Phonon Mode Guitar (Audio-Visual)"));
        this.getChildren().add(new Button("Play Mode Frequency (Hz)"));
        this.getChildren().add(new Button("Export Vibration MIDI"));
    }
}
