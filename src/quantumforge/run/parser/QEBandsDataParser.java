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

import quantumforge.project.property.ProjectProperty;

/**
 * Parses gnuplot-formatted electronic band structures (<prefix>.bands.dat.gnu) 
 * output by bands.x, grouping coordinates into discrete bands and shifting energies 
 * relative to the Fermi level reference (Roadmap #46).
 */
public final class QEBandsDataParser extends LogParser {

    public static final class Band {
        private final List<Double> kDistance = new ArrayList<>();
        private final List<Double> energyEv = new ArrayList<>();

        public void addPoint(double k, double energy) {
            this.kDistance.add(k);
            this.energyEv.add(energy);
        }

        public double[] getKDistance() {
            return kDistance.stream().mapToDouble(Double::doubleValue).toArray();
        }

        public double[] getEnergyEv() {
            return energyEv.stream().mapToDouble(Double::doubleValue).toArray();
        }

        public int size() {
            return kDistance.size();
        }
    }

    private final List<Band> bands = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private double fermiEnergyEv = Double.NaN;

    public QEBandsDataParser(ProjectProperty property) {
        super(property);
    }

    public List<Band> getBands() { return List.copyOf(this.bands); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
    public double getFermiEnergyEv() { return this.fermiEnergyEv; }

    @Override
    public void parse(File file) throws IOException {
        this.parseWithFermi(file, 0.0);
    }

    /**
     * Parses the bands file and shifts all energies relative to the Fermi level (E_F).
     */
    public void parseWithFermi(File file, double fermiEnergy) throws IOException {
        this.bands.clear();
        this.diagnostics.clear();
        this.fermiEnergyEv = Double.NaN;
        if (file == null || !file.isFile()) {
            this.diagnostics.add("Bands data file is missing or not a regular file.");
            return;
        }
        if (!Double.isFinite(fermiEnergy)) {
            this.diagnostics.add("Fermi energy is not finite; energies cannot be referenced safely.");
            return;
        }
        this.fermiEnergyEv = fermiEnergy;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Band currentBand = new Band();
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                // Blank line represents the boundary separating different bands
                if (trim.isEmpty()) {
                    if (currentBand.size() > 0) {
                        this.bands.add(currentBand);
                        currentBand = new Band();
                    }
                    continue;
                }

                String[] tokens = trim.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        double kDist = ScfConvergenceAnalyzer.parseFortranDouble(tokens[0]);
                        double energyRaw = ScfConvergenceAnalyzer.parseFortranDouble(tokens[1]);
                        if (!Double.isFinite(kDist) || !Double.isFinite(energyRaw)
                                || (currentBand.size() > 0
                                && kDist < currentBand.getKDistance()[currentBand.size() - 1])) {
                            this.diagnostics.add("Skipped non-finite or non-monotonic band point: " + trim);
                            continue;
                        }
                        // Shift relative to the explicit Fermi reference.
                        currentBand.addPoint(kDist, energyRaw - fermiEnergy);
                    } catch (NumberFormatException e) {
                        this.diagnostics.add("Skipped malformed band-data row: " + trim);
                    }
                }
            }

            // Catch the last band if file didn't end with a blank line
            if (currentBand.size() > 0) {
                this.bands.add(currentBand);
            }
        }
    }
}
