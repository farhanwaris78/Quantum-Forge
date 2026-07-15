/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.qc;

import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.graph.GraphProperty;
import quantumforge.app.project.viewer.result.graph.QEFXGraphViewerController;
import quantumforge.app.project.viewer.result.graph.SeriesProperty;
import quantumforge.project.property.ProjectDos;
import quantumforge.project.property.ProjectProperty;
import quantumforge.project.property.DosData;

/**
 * Quantum Capacitance (QC) Viewer.
 * Cq = e^2 * DOS(E)
 */
public class QEFXQCViewerController extends QEFXGraphViewerController {

    private ProjectProperty projectProperty;
    private static final double ELECTRON_CHARGE = 1.602176634e-19;
    private static final double EV_TO_JOULE = 1.602176634e-19;

    public QEFXQCViewerController(QEFXProjectController projectController, ProjectProperty projectProperty) {
        super(projectController, Pos.BOTTOM_RIGHT);
        this.projectProperty = projectProperty;
    }

    @Override
    protected int getCalculationID() {
        return projectProperty.getStatus().getDosCount();
    }

    @Override
    protected GraphProperty createProperty() {
        GraphProperty property = new GraphProperty();
        property.setTitle("Quantum Capacitance");
        property.setXLabel("Voltage / V");
        property.setYLabel("Cq / (\u00b5F/cm\u00b2)");
        
        SeriesProperty series = new SeriesProperty();
        series.setName("Quantum Capacitance");
        series.setColor("green");
        series.setWithSymbol(false);
        property.addSeries(series);
        
        return property;
    }

    @Override
    protected void reloadData(LineChart<Number, Number> lineChart) {
        lineChart.getData().clear();
        
        ProjectDos projectDos = projectProperty.getDos();
        if (projectDos == null) return;
        
        DosData dosData = projectDos.getDosData();
        if (dosData == null) return;

        double fermi = 0.0;
        if (projectProperty.getFermiEnergies().numEnergies() > 0) {
            fermi = projectProperty.getFermiEnergies().getEnergy(projectProperty.getFermiEnergies().numEnergies() - 1);
        }

        Series<Number, Number> series = new Series<>();
        series.setName("Cq");

        int n = dosData.numPoints();
        // Area of the 2D cell (simplified)
        double area = 1.0e-16; // cm^2 placeholder

        for (int i = 0; i < n; i++) {
            double energy = dosData.getEnergy(i); // eV
            double dos = dosData.getDos(i);       // states/eV/cell
            
            double voltage = energy - fermi;
            // Cq = e^2 * DOS / Area
            // states/eV -> states/J (divide by e)
            // C = q/V -> states * e / (J/e) = states * e^2 / J
            double cq = (ELECTRON_CHARGE * ELECTRON_CHARGE * dos / EV_TO_JOULE) / area;
            cq *= 1e6; // to uF
            
            series.getData().add(new Data<>(voltage, cq));
        }
        
        lineChart.getData().add(series);
    }
}
