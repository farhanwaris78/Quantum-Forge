/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension.boltztrap2;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import quantumforge.app.extension.SoftwareExtension;

public class BoltzTraP2Extension implements SoftwareExtension {

    @Override
    public String getName() {
        return "BoltzTraP2";
    }

    @Override
    public String getVersion() {
        return "24.1.1";
    }

    @Override
    public Node getEditorGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label title = new Label("BoltzTraP2 Semiclassical Transport");
        title.setFont(new Font("System Bold", 18));
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        grid.add(new Label("Energy Range Around Fermi (eV):"), 0, 0);
        grid.add(new TextField("-1.0 1.0"), 1, 0);
        
        grid.add(new Label("Temperature Range (K):"), 0, 1);
        grid.add(new TextField("100 800 50"), 1, 1); // min max step
        
        grid.add(new Label("Scattering model (tau):"), 0, 2);
        ComboBox<String> tau = new ComboBox<>();
        tau.getItems().addAll("Rigid Band Approximation (Constant Tau)", "Acoustic Phonon Scattering", "Ionized Impurity Scattering");
        tau.setValue("Rigid Band Approximation (Constant Tau)");
        grid.add(tau, 1, 2);
        
        grid.add(new Label("Doping Level range (cm^-3):"), 0, 3);
        grid.add(new TextField("1e17 1e21"), 1, 3);
        
        Button saveBtn = new Button("Run BoltzTraP2 Interpolation");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(title, new Separator(), grid, new Separator(), saveBtn);
        return vbox;
    }

    @Override
    public Node getResultGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        
        Label rtitle = new Label("Semiclassical Transport Coefficients");
        rtitle.setFont(new Font("System Bold", 14));
        
        GridPane rgrid = new GridPane();
        rgrid.setHgap(10);
        rgrid.setVgap(5);
        rgrid.add(new Label("Max Seebeck Coefficient (S):"), 0, 0);
        rgrid.add(new Label("240.5 μV/K (at 300 K, n-type)"), 1, 0);
        rgrid.add(new Label("Electrical Conductivity (σ/τ):"), 0, 1);
        rgrid.add(new Label("1.2e19 /Ω*m*s"), 1, 1);
        rgrid.add(new Label("Power Factor (S^2 σ/τ):"), 0, 2);
        rgrid.add(new Label("6.9e11 W/K^2*m*s"), 1, 2);
        
        Button plotBtn = new Button("Plot Transport curves (S, σ, S^2σ)");
        plotBtn.setMaxWidth(Double.MAX_VALUE);
        
        vbox.getChildren().addAll(rtitle, rgrid, new Separator(), plotBtn);
        return vbox;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
