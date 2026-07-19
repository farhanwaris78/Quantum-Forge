/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension.ml;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import quantumforge.app.extension.SoftwareExtension;

public class MLPotentialExtension implements SoftwareExtension {

    @Override
    public String getName() {
        return "ML-Potentials";
    }

    @Override
    public String getVersion() {
        return "3.0";
    }

    @Override
    public Node getEditorGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label title = new Label("Deep Learning ML Potential Pre-Relaxer");
        title.setFont(new Font("System Bold", 18));
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        grid.add(new Label("GNN Model Selection:"), 0, 0);
        ComboBox<String> model = new ComboBox<>();
        model.getItems().addAll("M3GNet (MatGL)", "CHGNet", "MACE (MP-0b)", "SevenNet", "ORB-v2", "MatterSim");
        model.setValue("MACE (MP-0b)");
        grid.add(model, 1, 0);
        
        grid.add(new Label("Computation Device:"), 0, 1);
        ComboBox<String> dev = new ComboBox<>();
        dev.getItems().addAll("CPU", "CUDA GPU (Automatic precision)");
        dev.setValue("CPU");
        grid.add(dev, 1, 1);
        
        grid.add(new Label("Enforce Domain Check:"), 0, 2);
        CheckBox gate = new CheckBox();
        gate.setSelected(true);
        grid.add(gate, 1, 2);
        
        grid.add(new Label("Uncertainty threshold:"), 0, 3);
        grid.add(new TextField("0.05 eV/atom"), 1, 3);
        
        Button saveBtn = new Button("Run Pre-Relaxation via Python Sidecar");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(title, new Separator(), grid, new Separator(), saveBtn);
        return vbox;
    }

    @Override
    public Node getResultGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label rtitle = new Label("GNN Optimization & Uncertainty Diagnostics");
        rtitle.setFont(new Font("System Bold", 14));
        
        Label status = new Label("Run an ML pre-relaxation to see optimization diagnostics.\n"
                + "Results will include predicted energy/atom, max force, and ensemble\n"
                + "disagreement (uncertainty). Coordinates must be validated with DFT\n"
                + "before use in production calculations.\n"
                + "WARNING: ML energies are not interchangeable with DFT energies.");
        status.setWrapText(true);
        
        Button plotBtn = new Button("Transfer Optimized Coordinates to QE");
        plotBtn.setMaxWidth(Double.MAX_VALUE);
        plotBtn.setDisable(true);
        
        vbox.getChildren().addAll(rtitle, new Separator(), status, new Separator(), plotBtn);
        return vbox;
    }

    @Override
    public boolean isAvailable() {
        // These forms are retained only as experimental source material.  They
        // cannot create, execute, parse, and validate a complete workflow.
        // Never expose a prototype as an installable scientific capability.
        return false;
    }
}
