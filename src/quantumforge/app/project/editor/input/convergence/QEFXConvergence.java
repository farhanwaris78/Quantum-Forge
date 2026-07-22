/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.editor.input.convergence;

import java.io.IOException;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorComponent;
import quantumforge.input.QEInput;

public class QEFXConvergence extends QEFXEditorComponent<QEFXConvergenceController> {

    public QEFXConvergence(QEFXMainController mainController, QEInput input) throws IOException {
        super("QEFXConvergence.fxml", new QEFXConvergenceController(mainController, input));
    }

    @Override
    public void notifyEditorOpened() {
        // The convergence form has no deferred refresh action.
    }
}
