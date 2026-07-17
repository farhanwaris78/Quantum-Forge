/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension.castep;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import quantumforge.app.extension.SoftwareExtension;

public class CASTEPExtension implements SoftwareExtension {

    @Override
    public String getName() {
        return "CASTEP";
    }

    @Override
    public String getVersion() {
        return "24.1";
    }

    @Override
    public Node getEditorGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label title = new Label("CASTEP Cell & Param Editor");
        title.setFont(new Font("System Bold", 18));
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        grid.add(new Label("Task:"), 0, 0);
        ComboBox<String> task = new ComboBox<>();
        task.getItems().addAll("GeometryOptimization", "SinglePoint", "Phonon", "BandStructure");
        task.setValue("GeometryOptimization");
        grid.add(task, 1, 0);
        
        grid.add(new Label("Cut-off Energy (eV):"), 0, 1);
        grid.add(new TextField("350.0"), 1, 1);
        
        grid.add(new Label("XC Functional:"), 0, 2);
        ComboBox<String> xc = new ComboBox<>();
        xc.getItems().addAll("PBE", "LDA", "PBEsol", "PW91");
        xc.setValue("PBE");
        grid.add(xc, 1, 2);
        
        grid.add(new Label("MP Grid Size:"), 0, 3);
        grid.add(new TextField("4 4 4"), 1, 3);

        grid.add(new Label("Energy Tolerance (eV/atom):"), 0, 4);
        grid.add(new TextField("1E-5"), 1, 4);
        
        Button saveBtn = new Button("Generate CASTEP Input Files");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(title, new Separator(), grid, new Separator(), saveBtn);
        return vbox;
    }

    @Override
    public Node getResultGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getChildren().add(new Label("CASTEP Results Parser"));
        vbox.getChildren().add(new Button("Parse .castep Output"));
        return vbox;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
