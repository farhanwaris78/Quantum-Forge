/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.web;

import java.io.IOException;

import javafx.scene.web.WebEngine;
import quantumforge.app.QEFXAppComponent;
import quantumforge.app.QEFXMainController;

public class QEFXWeb extends QEFXAppComponent<QEFXWebController> {

    public QEFXWeb(QEFXMainController mainController, String url) throws IOException {
        super("QEFXWeb.fxml", new QEFXWebController(mainController, url));

        this.setupOnTabClosed();
    }

    private void setupOnTabClosed() {
        WebEngine engine = this.getEngine();
        if (engine == null) {
            return;
        }

        engine.setOnVisibilityChanged(event -> {
            if (event != null && (!event.getData())) {
                QEFXMainController mainController = null;
                if (this.controller != null) {
                    mainController = this.controller.getMainController();
                }

                if (mainController != null) {
                    mainController.hideWebPage(engine);
                }
            }
        });
    }

    public WebEngine getEngine() {
        if (this.controller == null) {
            return null;
        }

        return this.controller.getEngine();
    }
}
