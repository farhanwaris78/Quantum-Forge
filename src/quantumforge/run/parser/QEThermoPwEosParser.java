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
 * Parses Birch-Murnaghan Equation of State (EOS) volume-fitting parameters (V0, B0, B'0, E0)
 * from thermo_pw / pw.x volume-grid calculations (Roadmap #106).
 */
public final class QEThermoPwEosParser extends LogParser {

    public static final class EosResult {
        private final double equilibriumVolumeAng3; // V0
        private final double bulkModulusGpa;          // B0
        private final double bulkModulusDerivative;   // B'0
        private final double minimumEnergyRy;         // E0
        private final boolean success;

        public EosResult(double v0, double b0, double bPrime, double e0, boolean success) {
            this.equilibriumVolumeAng3 = v0;
            this.bulkModulusGpa = b0;
            this.bulkModulusDerivative = bPrime;
            this.minimumEnergyRy = e0;
            this.success = success;
        }

        public double getEquilibriumVolumeAng3() { return this.equilibriumVolumeAng3; }
        public double getBulkModulusGpa() { return this.bulkModulusGpa; }
        public double getBulkModulusDerivative() { return this.bulkModulusDerivative; }
        public double getMinimumEnergyRy() { return this.minimumEnergyRy; }
        public boolean isSuccess() { return this.success; }

        /**
         * Evaluates the Birch-Murnaghan Equation of State energy at a given volume V:
         * E(V) = E0 + 9*V0*B0 / 16 * { [ (V0/V)^(2/3) - 1 ]^3 * B'0 + [ (V0/V)^(2/3) - 1 ]^2 * [ 6 - 4 * (V0/V)^(2/3) ] }
         */
        public double evaluateEnergyRy(double V) {
            if (V <= 0.0 || !this.success) {
                return Double.NaN;
            }

            // GPa to Ry/Angstrom^3 conversion: 1 GPa = 0.000458739 Ry/Angstrom^3
            double GPA_TO_RY_ANG3 = 0.000458739;
            double b0_ry = this.bulkModulusGpa * GPA_TO_RY_ANG3;

            double ratio = Math.pow(this.equilibriumVolumeAng3 / V, 2.0 / 3.0);
            double f = ratio - 1.0;

            double term1 = f * f * f * this.bulkModulusDerivative;
            double term2 = f * f * (6.0 - 4.0 * ratio);

            return this.minimumEnergyRy + (9.0 * this.equilibriumVolumeAng3 * b0_ry / 16.0) * (term1 + term2);
        }
    }

    private double equilibriumVolume = 0.0;
    private double bulkModulus = 0.0;
    private double bulkModulusDerivative = 0.0;
    private double minimumEnergy = 0.0;
    private boolean eosParsed = false;

    public QEThermoPwEosParser(ProjectProperty property) {
        super(property);
    }

    public double getEquilibriumVolume() { return this.equilibriumVolume; }
    public double getBulkModulus() { return this.bulkModulus; }
    public double getBulkModulusDerivative() { return this.bulkModulusDerivative; }
    public double getMinimumEnergy() { return this.minimumEnergy; }
    public boolean isEosParsed() { return this.eosParsed; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.equilibriumVolume = 0.0;
        this.bulkModulus = 0.0;
        this.bulkModulusDerivative = 0.0;
        this.minimumEnergy = 0.0;
        this.eosParsed = false;

        // thermo_pw parses parameters:
        // Equation of state: Birch-Murnaghan
        // V0 =    143.2120 A^3,  B0 =    135.2100 GPa,  B'0 =      4.3210,  E0 =  -123.4560 Ry
        Pattern pEos = Pattern.compile("V0\\s*=\\s*([-\\d.]+)\\s*(?:A\\^3|bohr\\^3)\\s*,\\s*B0\\s*=\\s*([-\\d.]+)\\s*GPa\\s*,\\s*B'0\\s*=\\s*([-\\d.]+)\\s*,\\s*E0\\s*=\\s*([-\\d.]+)\\s*Ry", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher m = pEos.matcher(trim);
                if (m.find()) {
                    this.equilibriumVolume = Double.parseDouble(m.group(1));
                    this.bulkModulus = Double.parseDouble(m.group(2));
                    this.bulkModulusDerivative = Double.parseDouble(m.group(3));
                    this.minimumEnergy = Double.parseDouble(m.group(4));
                    this.eosParsed = true;
                }
            }
        }
    }

    public EosResult getResult() {
        return new EosResult(this.equilibriumVolume, this.bulkModulus, this.bulkModulusDerivative, this.minimumEnergy, this.eosParsed);
    }
}
