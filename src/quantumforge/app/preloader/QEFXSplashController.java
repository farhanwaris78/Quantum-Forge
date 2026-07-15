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

import java.net.URL;
import java.util.ResourceBundle;

import quantumforge.app.QEFXAppController;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class QEFXSplashController extends QEFXAppController {

    @FXML
    private Label commentLabel;

    @FXML
    private ProgressBar progressBar;

    public QEFXSplashController() {
        super();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.setupCommentLabel();
        this.setupProgressBar();
    }

    private void setupCommentLabel() {
        // TODO
    }

    private void setupProgressBar() {
        // TODO
    }
}
