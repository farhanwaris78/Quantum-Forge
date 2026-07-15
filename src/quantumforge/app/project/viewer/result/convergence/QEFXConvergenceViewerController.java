/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.convergence;

import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.GraphProperty;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewerController;
import quantumforge.app.project.viewer.result.graph.SeriesProperty;

public class QEFXConvergenceViewerController extends QEFXGraphViewerController {

    public QEFXConvergenceViewerController(QEFXProjectController projectController) {
        super(projectController, Pos.BOTTOM_RIGHT);
    }

    @Override
    protected int getCalculationID() {
        return 1; // Stub
    }

    @Override
    protected GraphProperty createProperty() {
        GraphProperty property = new GraphProperty();
        property.setTitle("Convergence Test");
        property.setXLabel("Parameter Value");
        property.setYLabel("Total Energy / Ry");
        
        SeriesProperty series = new SeriesProperty();
        series.setName("Total Energy");
        series.setColor("blue");
        series.setWithSymbol(true);
        property.addSeries(series);
        
        return property;
    }

    @Override
    protected void reloadData(LineChart<Number, Number> lineChart) {
        // Stub: In a real implementation, read convergence results from a file
        lineChart.getData().clear();
    }
}
