/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.vlight;

import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;
import quantumforge.atoms.viewer.operation.ViewerEventKernel;

public class MouseLightHandler implements EventHandler<MouseEvent> {

    private double[][] mousePosition;

    private AtomsVLight atomsVLight;

    public MouseLightHandler(AtomsVLight atomsVLight) {
        if (atomsVLight == null) {
            throw new IllegalArgumentException("atomsVLight is null.");
        }

        this.atomsVLight = atomsVLight;

        this.mousePosition = new double[3][2];
        for (int i = 0; i < this.mousePosition.length; i++) {
            for (int j = 0; j < this.mousePosition[i].length; j++) {
                this.mousePosition[i][j] = 0.0;
            }
        }
    }

    @Override
    public void handle(MouseEvent event) {
        if (event == null) {
            return;
        }

        EventType<? extends MouseEvent> eventType = event.getEventType();
        if (eventType == null) {
            return;
        }

        if (eventType == MouseEvent.MOUSE_PRESSED) {
            double sceneX = event.getSceneX();
            double sceneY = event.getSceneY();
            Point2D point = this.atomsVLight.sceneToLocal(sceneX, sceneY);
            double x = point.getX();
            double y = point.getY();
            this.mousePosition[0][0] = x;
            this.mousePosition[0][1] = y;
            this.mousePosition[1][0] = x;
            this.mousePosition[1][1] = y;
            this.mousePosition[2][0] = x;
            this.mousePosition[2][1] = y;

        } else if (eventType == MouseEvent.MOUSE_DRAGGED) {
            double sceneX = event.getSceneX();
            double sceneY = event.getSceneY();
            Point2D point = this.atomsVLight.sceneToLocal(sceneX, sceneY);
            double x1 = this.mousePosition[2][0];
            double y1 = this.mousePosition[2][1];
            double x2 = point.getX();
            double y2 = point.getY();
            this.mousePosition[1][0] = x1;
            this.mousePosition[1][1] = y1;
            this.mousePosition[2][0] = x2;
            this.mousePosition[2][1] = y2;
            this.rotateCell();

        } else if (eventType == MouseEvent.MOUSE_RELEASED) {
            // NOP
        }
    }

    private void rotateCell() {
        double x1 = this.mousePosition[1][0];
        double y1 = this.mousePosition[1][1];
        double x2 = this.mousePosition[2][0];
        double y2 = this.mousePosition[2][1];
        double dx = x2 - x1;
        double dy = y2 - y1;

        double rr = dx * dx + dy * dy;
        if (rr > 0.0) {
            double rho = ViewerEventKernel.MOUSE_ROTATE_SPEED * Math.sqrt(rr);
            this.atomsVLight.appendRotation(rho, dy, -dx, 0.0);
        }
    }
}
