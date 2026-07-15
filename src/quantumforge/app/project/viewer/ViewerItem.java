/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer;

import quantumforge.com.graphic.svg.SVGLibrary;
import quantumforge.com.graphic.svg.SVGLibrary.SVGData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

public class ViewerItem extends BorderPane {

    private static final double GRAPHIC_SIZE = 28.0;
    private static final String GRAPHIC_CLASS = "teeth-menu-icon";

    private static final double CAPTION_MARGIN = 16.0;

    private static final double SHADOW_RADIUS = 2.0;
    private static final double SHADOW_LAYOUT_X = 0.0;
    private static final double SHADOW_LAYOUT_Y = 3.0;

    public ViewerItem(SVGData svgData, String message) {
        super();

        if (svgData == null) {
            throw new IllegalArgumentException("svgData is null");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("message is empty.");
        }

        Node icon = SVGLibrary.getGraphic(svgData, GRAPHIC_SIZE, null, GRAPHIC_CLASS);
        BorderPane.setAlignment(icon, Pos.CENTER);

        Label caption = new Label(message);
        caption.getStyleClass().add(GRAPHIC_CLASS);
        BorderPane.setAlignment(caption, Pos.CENTER_LEFT);
        BorderPane.setMargin(caption, new Insets(0.0, 0.0, 0.0, CAPTION_MARGIN));

        this.setCenter(icon);
        this.setRight(caption);
        this.setLayoutX(0.0);
        this.setLayoutY(-0.5 * GRAPHIC_SIZE);
        this.setEffect(new DropShadow(SHADOW_RADIUS, SHADOW_LAYOUT_X, SHADOW_LAYOUT_Y, Color.BLACK));
    }
}
