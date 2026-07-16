/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension.phonopy;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import quantumforge.app.extension.SoftwareExtension;

public class PhonopyExtension implements SoftwareExtension {

    @Override
    public String getName() {
        return "Phonopy";
    }

    @Override
    public String getVersion() {
        return "2.21";
    }

    @Override
    public Node getEditorGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label title = new Label("Phonopy Finite Displacements Builder");
        title.setFont(new Font("System Bold", 18));
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        grid.add(new Label("Supercell Matrix:"), 0, 0);
        grid.add(new TextField("2 2 2"), 1, 0);
        
        grid.add(new Label("Displacement Distance (Å):"), 0, 1);
        grid.add(new TextField("0.01"), 1, 1);
        
        grid.add(new Label("ASR (Acoustic Sum Rule):"), 0, 2);
        ComboBox<String> asr = new ComboBox<>();
        asr.getItems().addAll("none", "pick", "crystal", "symmetric");
        asr.setValue("crystal");
        grid.add(asr, 1, 2);
        
        grid.add(new Label("Calculate Thermal Properties:"), 0, 3);
        CheckBox thermal = new CheckBox();
        thermal.setSelected(true);
        grid.add(thermal, 1, 3);
        
        Button saveBtn = new Button("Generate Displaced Supercells");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(title, new Separator(), grid, new Separator(), saveBtn);
        return vbox;
    }

    @Override
    public Node getResultGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label rtitle = new Label("Phonopy Results Analysis");
        rtitle.setFont(new Font("System Bold", 14));
        
        GridPane rgrid = new GridPane();
        rgrid.setHgap(10);
        rgrid.setVgap(5);
        rgrid.add(new Label("Free Energy (F):"), 0, 0);
        rgrid.add(new Label("-2.54 kJ/mol (at 300 K)"), 1, 0);
        rgrid.add(new Label("Entropy (S):"), 0, 1);
        rgrid.add(new Label("41.2 J/K*mol"), 1, 1);
        rgrid.add(new Label("Heat Capacity (Cv):"), 0, 2);
        rgrid.add(new Label("22.8 J/K*mol"), 1, 2);
        
        Button plotBtn = new Button("Plot Thermal Properties");
        plotBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(rtitle, rgrid, new Separator(), plotBtn);
        return vbox;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
