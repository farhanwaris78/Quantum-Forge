/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.GraphProperty;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewerController;

/**
 * Superconducting Tc Analyzer (McMillan formula).
 */
public class SuperconductingTcViewer extends QEFXGraphViewerController {

    public SuperconductingTcViewer(QEFXProjectController projectController) {
        super(projectController, Pos.TOP_RIGHT);
    }

    @Override
    protected int getCalculationID() {
        return 1;
    }

    @Override
    protected GraphProperty createProperty() {
        GraphProperty property = new GraphProperty();
        property.setTitle("Superconducting Tc");
        property.setXLabel("Coulomb Pseudopotential (mu*)");
        property.setYLabel("Tc / K");
        return property;
    }

    @Override
    protected void reloadData(LineChart<Number, Number> lineChart) {
        // McMillan formula: Tc = (theta_D / 1.45) * exp[-1.04(1+lambda) / (lambda - mu*(1+0.62lambda))]
    }
}
