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
 * Parses LAMMPS thermodynamic logs, extracting cycle steps, temperature, pressure,
 * potential energy, kinetic energy, and total energy to calculate statistical
 * fluctuations and ensemble stability (Roadmap #113).
 */
public final class QELammpsThermoParser extends LogParser {

    public static final class ThermoStep {
        private final int step;
        private final double temperatureK;
        private final double pressureBar;
        private final double potentialEnergyEv;
        private final double kineticEnergyEv;
        private final double totalEnergyEv;

        public ThermoStep(int step, double temp, double press, double pe, double ke, double total) {
            this.step = step;
            this.temperatureK = temp;
            this.pressureBar = press;
            this.potentialEnergyEv = pe;
            this.kineticEnergyEv = ke;
            this.totalEnergyEv = total;
        }

        public int getStep() { return this.step; }
        public double getTemperatureK() { return this.temperatureK; }
        public double getPressureBar() { return this.pressureBar; }
        public double getPotentialEnergyEv() { return this.potentialEnergyEv; }
        public double getKineticEnergyEv() { return this.kineticEnergyEv; }
        public double getTotalEnergyEv() { return this.totalEnergyEv; }
    }

    private final List<ThermoStep> steps = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();

    public QELammpsThermoParser(ProjectProperty property) {
        super(property);
    }

    public List<ThermoStep> getSteps() { return List.copyOf(this.steps); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.steps.clear();
        this.diagnostics.clear();

        // LAMMPS thermo table starts after "Memory usage per processor" or headers like:
        // "Step Temp Press PotEng KinEng TotEng" or similar.
        // It ends with a line starting with "Loop time of".
        boolean inThermo = false;
        Pattern pHeader = Pattern.compile("^\\s*Step\\s+Temp\\s+Press", Pattern.CASE_INSENSITIVE);
        Pattern pCols = Pattern.compile("^\\s*(\\d+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                if (pHeader.matcher(trim).find()) {
                    inThermo = true;
                    continue;
                }

                if (inThermo && (trim.startsWith("Loop time") || trim.contains("WARNING"))) {
                    inThermo = false;
                    continue;
                }

                if (inThermo) {
                    Matcher m = pCols.matcher(trim);
                    if (m.find()) {
                        try {
                            int step = Integer.parseInt(m.group(1));
                            double temp = Double.parseDouble(m.group(2));
                            double press = Double.parseDouble(m.group(3));
                            double pe = Double.parseDouble(m.group(4));
                            double ke = Double.parseDouble(m.group(5));
                            double total = Double.parseDouble(m.group(6));
                            this.steps.add(new ThermoStep(step, temp, press, pe, ke, total));
                        } catch (NumberFormatException e) {
                            // Skip malformed lines
                        }
                    }
                }
            }
        }

        performStatisticalAnalysis();
    }

    /**
     * Computes averages and standard deviations of thermodynamic variables.
     */
    private void performStatisticalAnalysis() {
        if (this.steps.isEmpty()) {
            this.diagnostics.add("No thermodynamic data steps parsed from LAMMPS log.");
            return;
        }

        int N = this.steps.size();
        double sumT = 0, sumP = 0, sumPE = 0, sumKE = 0, sumE = 0;
        for (ThermoStep s : this.steps) {
            sumT += s.temperatureK;
            sumP += s.pressureBar;
            sumPE += s.potentialEnergyEv;
            sumKE += s.kineticEnergyEv;
            sumE += s.totalEnergyEv;
        }

        double avgT = sumT / N;
        double avgP = sumP / N;
        double avgE = sumE / N;

        // Compute fluctuations / standard deviations
        double varT = 0, varE = 0;
        for (ThermoStep s : this.steps) {
            varT += (s.temperatureK - avgT) * (s.temperatureK - avgT);
            varE += (s.totalEnergyEv - avgE) * (s.totalEnergyEv - avgE);
        }
        double stdT = Math.sqrt(varT / N);
        double stdE = Math.sqrt(varE / N);

        this.diagnostics.add(String.format("Statistical analysis completed over %d MD steps.", N));
        this.diagnostics.add(String.format("Averages: Temp = %.2f K, Press = %.2f bar, Total Energy = %.4f eV.", avgT, avgP, avgE));
        this.diagnostics.add(String.format("Fluctuations (StdDev): Temp = %.2f K, Total Energy = %.6f eV.", stdT, stdE));
    }
}
