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
        
        Label status = new Label("Run a phonopy finite-displacement calculation to populate thermal properties.\n"
                + "Results will include free energy F(T), entropy S(T), heat capacity Cv(T),\n"
                + "and phonon DOS. Requires FORCE_SETS from QE force calculations.\n"
                + "Note: phonopy must be installed and configured separately.");
        status.setWrapText(true);
        
        Button plotBtn = new Button("Plot Thermal Properties");
        plotBtn.setMaxWidth(Double.MAX_WIDTH);
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
