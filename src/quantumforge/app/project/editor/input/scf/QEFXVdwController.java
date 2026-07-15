/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.input.scf;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.input.QEFXInputController;
import quantumforge.app.project.editor.input.items.QEFXComboString;
import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;

public class QEFXVdwController extends QEFXInputController {

    @FXML
    private Label vdwLabel;

    @FXML
    private ComboBox<String> vdwCombo;

    @FXML
    private Button vdwButton;

    public QEFXVdwController(QEFXMainController mainController, QEInput input) {
        super(mainController, input);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        QENamelist nmlSystem = this.input.getNamelist(QEInput.NAMELIST_SYSTEM);

        if (nmlSystem != null) {
            this.setupVdwItem(nmlSystem);
        }
    }

    private void setupVdwItem(QENamelist nmlSystem) {
        if (this.vdwCombo == null) {
            return;
        }

        QEFXComboString item = new QEFXComboString(nmlSystem.getValueBuffer("vdw_corr"), this.vdwCombo);

        if (this.vdwLabel != null) {
            item.setLabel(this.vdwLabel);
        }

        if (this.vdwButton != null) {
            item.setDefault("none", this.vdwButton);
        }

        item.addItems("none", "grimme-d2", "grimme-d3", "ts", "tkatchenko-scheffler", "mbd", "xdm");

        item.pullAllTriggers();
    }
}
