/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.input.scf;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.input.QEFXInputController;
import quantumforge.app.project.editor.input.items.QEFXComboString;
import quantumforge.app.project.editor.input.items.QEFXToggleBoolean;
import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;

public class QEFXIsolatedController extends QEFXInputController {

    @FXML
    private ComboBox<String> isolatedCombo;
    @FXML
    private Button isolatedButton;

    @FXML
    private ComboBox<String> esmCombo;
    @FXML
    private Button esmButton;

    @FXML
    private ToggleButton dipoleToggle;
    @FXML
    private Button dipoleButton;

    @FXML
    private CheckBox mode2DCheck;

    public QEFXIsolatedController(QEFXMainController mainController, QEInput input) {
        super(mainController, input);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        QENamelist nmlSystem = this.input.getNamelist(QEInput.NAMELIST_SYSTEM);
        QENamelist nmlControl = this.input.getNamelist(QEInput.NAMELIST_CONTROL);

        if (nmlSystem != null) {
            setupIsolatedItem(nmlSystem);
            setupESMItem(nmlSystem);
            setupDipoleItem(nmlControl);
        }
        
        if (mode2DCheck != null) {
            mode2DCheck.setOnAction(event -> {
                if (mode2DCheck.isSelected()) {
                    // Logic for 2D Padding (increase Z box)
                    System.out.println("2D Material Mode Enabled: Z-Padding requested.");
                }
            });
        }
    }

    private void setupIsolatedItem(QENamelist nmlSystem) {
        QEFXComboString item = new QEFXComboString(nmlSystem.getValueBuffer("assume_isolated"), isolatedCombo);
        item.addItems("none", "makov-payne", "martyna-tuckerman", "esm");
        if (isolatedButton != null) item.setDefault("none", isolatedButton);
    }

    private void setupESMItem(QENamelist nmlSystem) {
        QEFXComboString item = new QEFXComboString(nmlSystem.getValueBuffer("esm_bc"), esmCombo);
        item.addItems("pbc", "bc1", "bc2", "bc3");
        if (esmButton != null) item.setDefault("pbc", esmButton);
    }

    private void setupDipoleItem(QENamelist nmlControl) {
        QEFXToggleBoolean item = new QEFXToggleBoolean(nmlControl.getValueBuffer("tefield"), dipoleToggle, false);
        if (dipoleButton != null) item.setDefault(false, dipoleButton);
    }
}
