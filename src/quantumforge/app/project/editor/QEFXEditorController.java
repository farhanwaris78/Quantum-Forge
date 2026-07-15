/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import quantumforge.app.QEFXAppController;
import quantumforge.app.QEFXMainController;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.TitledPane;

public abstract class QEFXEditorController extends QEFXAppController {

    @FXML
    protected Accordion accordion;

    public QEFXEditorController(QEFXMainController mainController) {
        super(mainController);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.setupConfigAccordion();
    }

    private void setupConfigAccordion() {
        if (this.accordion == null) {
            return;
        }

        List<TitledPane> panes = this.accordion.getPanes();
        if (panes != null && !panes.isEmpty()) {
            TitledPane pane = panes.get(0);
            if (pane != null) {
                this.accordion.setExpandedPane(pane);
            }
        }
    }
}
