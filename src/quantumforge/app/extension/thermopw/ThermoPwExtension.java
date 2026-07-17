/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension.thermopw;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import quantumforge.app.extension.SoftwareExtension;

public class ThermoPwExtension implements SoftwareExtension {

    @Override
    public String getName() {
        return "thermo_pw";
    }

    @Override
    public String getVersion() {
        return "1.8.2";
    }

    @Override
    public Node getEditorGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label title = new Label("thermo_pw Control Panel");
        title.setFont(new Font("System Bold", 18));
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        grid.add(new Label("Line Elastic Task:"), 0, 0);
        ComboBox<String> task = new ComboBox<>();
        task.getItems().addAll("elastic_constants", "quasi_harmonic", "phonon_dispersion", "volume_fit");
        task.setValue("elastic_constants");
        grid.add(task, 1, 0);
        
        grid.add(new Label("Strain Amplitude:"), 0, 1);
        grid.add(new TextField("0.005"), 1, 1);
        
        grid.add(new Label("Fitting steps:"), 0, 2);
        grid.add(new TextField("5"), 1, 2);
        
        grid.add(new Label("QHA Temperature Range (K):"), 0, 3);
        grid.add(new TextField("0 1000 10"), 1, 3);
        
        Button saveBtn = new Button("Generate thermo_pw input sections");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(title, new Separator(), grid, new Separator(), saveBtn);
        return vbox;
    }

    @Override
    public Node getResultGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label rtitle = new Label("Elastic Tensor & Properties");
        rtitle.setFont(new Font("System Bold", 14));
        
        GridPane rgrid = new GridPane();
        rgrid.setHgap(10);
        rgrid.setVgap(5);
        rgrid.add(new Label("Bulk Modulus (B):"), 0, 0);
        rgrid.add(new Label("134.8 GPa"), 1, 0);
        rgrid.add(new Label("Shear Modulus (G):"), 0, 1);
        rgrid.add(new Label("78.3 GPa"), 1, 1);
        rgrid.add(new Label("Young's Modulus (E):"), 0, 2);
        rgrid.add(new Label("194.2 GPa"), 1, 2);
        
        Button plotBtn = new Button("View Elastic Tensor (Cij)");
        plotBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(rtitle, rgrid, new Separator(), plotBtn);
        return vbox;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
