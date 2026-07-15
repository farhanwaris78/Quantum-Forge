/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.explorer;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.QEFXMainController;

public class QEFXExplorer extends QEFXAppComponent<QEFXExplorerController> {

    public QEFXExplorer(QEFXMainController mainController) throws IOException {
        super("QEFXExplorer.fxml", new QEFXExplorerController(mainController));
    }

    public QEFXExplorerFacade getFacade() {
        if(this.controller == null) {
            return null;
        }

        return new QEFXExplorerFacade(this.controller);
    }

}
