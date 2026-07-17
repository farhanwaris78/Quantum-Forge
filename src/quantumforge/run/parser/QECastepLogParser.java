/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectProperty;

/**
 * Parses CASTEP (.castep) output log files, extracting converged electronic free energy,
 * Fermi levels, and geometry optimization completion status (Roadmap #112).
 */
public final class QECastepLogParser extends LogParser {

    private double finalEnergyEv = 0.0;
    private double fermiEnergyEv = 0.0;
    private boolean geometryConverged = false;
    private final List<String> diagnostics = new ArrayList<>();

    public QECastepLogParser(ProjectProperty property) {
        super(property);
    }

    public double getFinalEnergyEv() { return this.finalEnergyEv; }
    public double getFermiEnergyEv() { return this.fermiEnergyEv; }
    public boolean isGeometryConverged() { return this.geometryConverged; }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.finalEnergyEv = 0.0;
        this.fermiEnergyEv = 0.0;
        this.geometryConverged = false;
        this.diagnostics.clear();

        // CASTEP log formats:
        // Final energy, E             =  -1234.567890 eV
        // Final free energy (E-TS)    =  -1234.567890 eV
        // Fermi energy                =   4.321000 eV
        Pattern pEnergy = Pattern.compile("Final\\s+(?:free\\s+)?energy(?:\\s*\\(E-TS\\))?,\\s*E\\s*=\\s*([-\\d.]+)\\s*eV", Pattern.CASE_INSENSITIVE);
        Pattern pFermi = Pattern.compile("Fermi\\s+energy\\s*=\\s*([-\\d.]+)\\s*eV", Pattern.CASE_INSENSITIVE);
        Pattern pGeom = Pattern.compile("Geometry\\s+optimization\\s+completed\\s+successfully", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mEnergy = pEnergy.matcher(trim);
                if (mEnergy.find()) {
                    this.finalEnergyEv = Double.parseDouble(mEnergy.group(1));
                }

                Matcher mFermi = pFermi.matcher(trim);
                if (mFermi.find()) {
                    this.fermiEnergyEv = Double.parseDouble(mFermi.group(1));
                }

                if (pGeom.matcher(trim).find()) {
                    this.geometryConverged = true;
                }
            }
        }

        performValidation();
    }

    private void performValidation() {
        if (this.finalEnergyEv != 0.0) {
            this.diagnostics.add(String.format("CASTEP calculation parsed successfully. Final energy: %.6f eV.", this.finalEnergyEv));
        } else {
            this.diagnostics.add("Warning: Could not parse final total energy from CASTEP log.");
        }

        if (this.fermiEnergyEv != 0.0) {
            this.diagnostics.add(String.format("Fermi energy level: %.4f eV.", this.fermiEnergyEv));
        }

        if (this.geometryConverged) {
            this.diagnostics.add("Geometry optimization completed successfully (mechanical forces converged).");
        }
    }
}
