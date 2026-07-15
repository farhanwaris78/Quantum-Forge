/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.app.project.viewer.result.special;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import java.util.List;

/**
 * Reaction Energy Graph generator.
 */
public class ReactionEnergyGraph {

    public static void plotPathway(LineChart<Number, Number> chart, List<Double> energies, List<String> stepNames) {
        if (energies == null || energies.isEmpty()) return;
        
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Reaction Pathway");
        
        for (int i = 0; i < energies.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, energies.get(i)));
        }
        
        chart.getData().add(series);
    }
}
