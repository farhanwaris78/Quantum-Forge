/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.elastic;

import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.GraphProperty;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewerController;

/**
 * Elastic Analysis Tool (ELATE interface).
 */
public class QEFXElasticViewerController extends QEFXGraphViewerController {

    public QEFXElasticViewerController(QEFXProjectController projectController) {
        super(projectController, Pos.TOP_RIGHT);
    }

    @Override
    protected int getCalculationID() {
        return 1;
    }

    @Override
    protected GraphProperty createProperty() {
        GraphProperty property = new GraphProperty();
        property.setTitle("Elastic Moduli (ELATE)");
        property.setXLabel("Angle / deg");
        property.setYLabel("Young's Modulus / GPa");
        return property;
    }

    @Override
    protected void reloadData(LineChart<Number, Number> lineChart) {
        // Read thermo_pw output and plot directional elastic constants
    }
}
