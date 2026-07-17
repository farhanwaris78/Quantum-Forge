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
    private double fermiEnergyEv = 0.0;

    public QEBandsDataParser(ProjectProperty property) {
        super(property);
    }

    public List<Band> getBands() { return List.copyOf(this.bands); }
    public double getFermiEnergyEv() { return this.fermiEnergyEv; }

    @Override
    public void parse(File file) throws IOException {
        this.parseWithFermi(file, 0.0);
    }

    /**
     * Parses the bands file and shifts all energies relative to the Fermi level (E_F).
     */
    public void parseWithFermi(File file, double fermiEnergy) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.bands.clear();
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
                        double kDist = Double.parseDouble(tokens[0]);
                        double energyRaw = Double.parseDouble(tokens[1]);
                        // Shift relative to the Fermi energy
                        double shiftedEnergy = energyRaw - fermiEnergy;
                        currentBand.addPoint(kDist, shiftedEnergy);
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
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
