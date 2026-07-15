/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.input.phonon;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorController;
import quantumforge.input.QEInput;

public class QEFXPhononController extends QEFXEditorController {

    @FXML
    private TextField nq1Field, nq2Field, nq3Field;
    @FXML
    private TextField tr2phField;
    @FXML
    private CheckBox epsilCheck;
    @FXML
    private ComboBox<String> asrCombo;

    public QEFXPhononController(QEFXMainController mainController) {
        super(mainController);
    }

    @Override
    protected void setupFXComponents() {
        if (asrCombo != null) {
            asrCombo.getItems().addAll("no", "simple", "crystal", "one-dim", "poly-eth");
            asrCombo.setValue("crystal");
        }
    }
}
