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
 * Parses optical absorption and complex polarizability tensors output by the TDDFPT 
 * spectrum engine (turbo_spectrum.x), calculating isotropic cross-sections (Roadmap #64).
 */
public final class QETurboSpectrumParser extends LogParser {

    public static final class SpectrumPoint {
        private final double energyEv;
        private final double reAlphaXx;
        private final double imAlphaXx;
        private final double reAlphaYy;
        private final double imAlphaYy;
        private final double reAlphaZz;
        private final double imAlphaZz;

        public SpectrumPoint(double energy, double reXx, double imXx, double reYy, double imYy, double reZz, double imZz) {
            this.energyEv = energy;
            this.reAlphaXx = reXx;
            this.imAlphaXx = imXx;
            this.reAlphaYy = reYy;
            this.imAlphaYy = imYy;
            this.reAlphaZz = reZz;
            this.imAlphaZz = imZz;
        }

        public double getEnergyEv() { return this.energyEv; }
        public double getReAlphaXx() { return this.reAlphaXx; }
        public double getImAlphaXx() { return this.imAlphaXx; }
        public double getReAlphaYy() { return this.reAlphaYy; }
        public double getImAlphaYy() { return this.imAlphaYy; }
        public double getReAlphaZz() { return this.reAlphaZz; }
        public double getImAlphaZz() { return this.imAlphaZz; }

        /**
         * Computes the isotropic absorption cross-section (photo-absorption intensity),
         * which is proportional to energy * Im(alpha_avg):
         * Sigma = (energy) * Im(alpha_xx + alpha_yy + alpha_zz) / 3
         */
        public double getIsotropicAbsorption() {
            double imAvg = (this.imAlphaXx + this.imAlphaYy + this.imAlphaZz) / 3.0;
            return this.energyEv * imAvg;
        }
    }

    private final List<SpectrumPoint> spectrumPoints = new ArrayList<>();

    public QETurboSpectrumParser(ProjectProperty property) {
        super(property);
    }

    public List<SpectrumPoint> getSpectrumPoints() { return List.copyOf(this.spectrumPoints); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.spectrumPoints.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();
                if (trim.isEmpty() || trim.startsWith("#") || trim.startsWith("@")) {
                    continue; // Skip comments/headers
                }

                String[] tokens = trim.split("\\s+");
                // Expected columns: Energy (Ry or eV), Re(xx), Im(xx), Re(yy), Im(yy), Re(zz), Im(zz)...
                if (tokens.length >= 7) {
                    try {
                        double energy = Double.parseDouble(tokens[0]);
                        double reXx = Double.parseDouble(tokens[1]);
                        double imXx = Double.parseDouble(tokens[2]);
                        double reYy = Double.parseDouble(tokens[3]);
                        double imYy = Double.parseDouble(tokens[4]);
                        double reZz = Double.parseDouble(tokens[5]);
                        double imZz = Double.parseDouble(tokens[6]);

                        if (Double.isFinite(energy) && Double.isFinite(imXx)) {
                            this.spectrumPoints.add(new SpectrumPoint(energy, reXx, imXx, reYy, imYy, reZz, imZz));
                        }
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                }
            }
        }
    }
}
