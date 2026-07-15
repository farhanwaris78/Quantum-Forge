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

import javafx.application.Preloader;
import javafx.stage.Stage;

public class QEFXPreloader extends Preloader {

    private QEFXSplash splash;

    @Override
    public void start(Stage primaryStage) {
        try {
            this.splash = new QEFXSplash();
            this.splash.showSplash();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification stateChangeNotification) {
        if (stateChangeNotification == null) {
            return;
        }

        if (stateChangeNotification.getType() == StateChangeNotification.Type.BEFORE_START) {
            if (this.splash != null) {
                this.splash.hideSplash();
            }
        }
    }
}
