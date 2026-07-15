/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.preloader;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.QEFXAppController;
import quantumforge.app.QEFXMain;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class QEFXSplash extends QEFXAppComponent<QEFXAppController> {

    private Stage splashStage;

    public QEFXSplash() throws IOException {
        super("QEFXSplash.fxml", new QEFXSplashController());
        this.splashStage = null;
    }

    public void showSplash() {
        if (this.splashStage == null) {
            this.splashStage = new Stage(StageStyle.TRANSPARENT);
            if (this.node != null && this.node instanceof Parent) {
                QEFXMain.initializeStyleSheets(((Parent) this.node).getStylesheets());
                this.splashStage.setScene(new Scene((Parent) this.node));
            }
        }

        this.splashStage.show();
    }

    public void hideSplash() {
        if (this.splashStage != null) {
            this.splashStage.close();
        }
    }
}
