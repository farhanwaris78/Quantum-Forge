/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.input.convergence;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.input.QEFXInputController;
import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

public class QEFXConvergenceController extends QEFXInputController {

    @FXML
    private ComboBox<String> parameterCombo;

    @FXML
    private TextField startField;

    @FXML
    private TextField endField;

    @FXML
    private TextField stepField;

    @FXML
    private Button runConvergenceButton;

    public QEFXConvergenceController(QEFXMainController mainController, QEInput input) {
        super(mainController, input);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (parameterCombo != null) {
            parameterCombo.getItems().addAll("ecutwfc", "ecutrho", "k-points", "degauss");
            parameterCombo.setValue("ecutwfc");
        }
        
        if (runConvergenceButton != null) {
            runConvergenceButton.setOnAction(event -> runConvergenceTest());
        }
    }

    private void runConvergenceTest() {
        String parameter = parameterCombo.getValue();
        double start = Double.parseDouble(startField.getText());
        double end = Double.parseDouble(endField.getText());
        double step = Double.parseDouble(stepField.getText());

        System.out.println("Running convergence test for " + parameter + " from " + start + " to " + end + " step " + step);
        
        // In a real implementation, this would loop and create multiple projects or tasks
        // and plot the results (Total Energy vs Parameter).
    }
}
