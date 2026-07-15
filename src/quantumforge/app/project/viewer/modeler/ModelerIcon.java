/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.modeler;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import quantumforge.com.graphic.svg.SVGLibrary;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;

public class ModelerIcon extends Group {

    private static final double INSETS_SIZE = 6.0;
    private static final double GRAPHIC_SIZE = 72.0;
    private static final String GRAPHIC_CLASS = "icon-modeler";

    public ModelerIcon(String text) {
        super();

        String text2 = "";
        if (text != null) {
            text2 = text;
        }

        Node figure = SVGLibrary.getGraphic(SVGData.TOOL, GRAPHIC_SIZE, null, GRAPHIC_CLASS);
        StackPane.setMargin(figure, new Insets(INSETS_SIZE));

        Label label = new Label(text2);
        label.setWrapText(true);
        label.getStyleClass().add(GRAPHIC_CLASS);

        StackPane pane = new StackPane();
        pane.getChildren().add(figure);
        pane.getChildren().add(label);

        this.getChildren().add(pane);
        StackPane.setAlignment(this, Pos.BOTTOM_LEFT);
    }
}
