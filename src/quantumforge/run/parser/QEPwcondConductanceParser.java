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
 * Parses electronic transmission coefficients (T) from quantum ballistic transport
 * (pwcond.x) calculations, converting raw transmission to ballistic conductance 
 * in units of conductance quantum (G0) and SI Siemens (Roadmap #68).
 */
public final class QEPwcondConductanceParser extends LogParser {

    private static final double CONDUCTANCE_QUANTUM_G0_SI = 7.74809172e-5; // Siemens (2e^2/h)

    public static final class ConductancePoint {
        private final double energyEv;
        private final double transmission; // T(E)

        public ConductancePoint(double energy, double transmission) {
            this.energyEv = energy;
            this.transmission = transmission;
        }

        public double getEnergyEv() { return this.energyEv; }
        public double getTransmission() { return this.transmission; }

        /**
         * Returns ballistic conductance in units of the conductance quantum G0:
         * G = G0 * T(E)
         */
        public double getConductanceInG0() {
            return this.transmission; // G / G0 = T(E)
        }

        /**
         * Returns ballistic conductance in SI units of Siemens (S):
         * G = G0 * T(E)
         */
        public double getConductanceSI() {
            return CONDUCTANCE_QUANTUM_G0_SI * this.transmission;
        }
    }

    private final List<ConductancePoint> conductancePoints = new ArrayList<>();

    public QEPwcondConductanceParser(ProjectProperty property) {
        super(property);
    }

    public List<ConductancePoint> getConductancePoints() { return List.copyOf(this.conductancePoints); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.conductancePoints.clear();

        // Regex to parse pwcond.x transmission rows:
        // e.g. "E =    -1.2400 eV,   Transmission =      1.5420"
        // or typical columns: "     -1.2400      1.5420"
        Pattern pCond = Pattern.compile("E\\s*=\\s*([-\\d.]+)\\s*eV\\s*,\\s*(?:Transmission|T)\\s*=\\s*([-\\d.]+)", Pattern.CASE_INSENSITIVE);
        Pattern pCols = Pattern.compile("^\\s*([-\\d.]+)\\s+([-\\d.]+)$");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inDataSection = false;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                if (trim.contains("Energy (eV)") && (trim.contains("Transmission") || trim.contains("T(E)"))) {
                    inDataSection = true;
                    continue;
                }

                if (inDataSection && trim.isEmpty()) {
                    inDataSection = false;
                    continue;
                }

                Matcher mCond = pCond.matcher(trim);
                if (mCond.find()) {
                    try {
                        double energy = Double.parseDouble(mCond.group(1));
                        double trans = Double.parseDouble(mCond.group(2));
                        this.conductancePoints.add(new ConductancePoint(energy, trans));
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                } else if (inDataSection) {
                    Matcher mCols = pCols.matcher(trim);
                    if (mCols.find()) {
                        try {
                            double energy = Double.parseDouble(mCols.group(1));
                            double trans = Double.parseDouble(mCols.group(2));
                            this.conductancePoints.add(new ConductancePoint(energy, trans));
                        } catch (NumberFormatException e) {
                            // Skip malformed lines
                        }
                    }
                }
            }
        }
    }
}
