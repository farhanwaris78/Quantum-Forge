/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */
package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.com.consts.Constants;
import quantumforge.project.property.ProjectProperty;

/**
 * Parses a reported Birch-Murnaghan EOS summary from thermo_pw/QE output.
 *
 * <p>The returned equilibrium volume is always Angstrom^3.  A unit label is
 * mandatory: accepting a naked number, or treating bohr^3 as Angstrom^3,
 * would silently corrupt an EOS curve.</p>
 */
public final class QEThermoPwEosParser extends LogParser {

    private static final double BOHR3_TO_ANGSTROM3 = Math.pow(Constants.BOHR_RADIUS_ANGS, 3);
    private static final double GPA_TO_RY_PER_ANGSTROM3 = 1.0e9 * 1.0e-30 / Constants.RYDBERG_SI;
    private static final Pattern EOS = Pattern.compile(
            "V0\\s*=\\s*([-+0-9.DdEe]+)\\s*(A(?:ng)?\\^?3|bohr\\^?3)\\s*,\\s*"
            + "B0\\s*=\\s*([-+0-9.DdEe]+)\\s*GPa\\s*,\\s*B'0\\s*=\\s*([-+0-9.DdEe]+)\\s*,\\s*"
            + "E0\\s*=\\s*([-+0-9.DdEe]+)\\s*Ry",
            Pattern.CASE_INSENSITIVE);

    public static final class EosResult {
        private final double equilibriumVolumeAng3;
        private final double bulkModulusGpa;
        private final double bulkModulusDerivative;
        private final double minimumEnergyRy;
        private final boolean success;

        public EosResult(double volumeAng3, double bulkGpa, double derivative, double energyRy,
                         boolean success) {
            this.success = success && finitePositive(volumeAng3) && finitePositive(bulkGpa)
                    && Double.isFinite(derivative) && Double.isFinite(energyRy);
            this.equilibriumVolumeAng3 = volumeAng3;
            this.bulkModulusGpa = bulkGpa;
            this.bulkModulusDerivative = derivative;
            this.minimumEnergyRy = energyRy;
        }

        public double getEquilibriumVolumeAng3() { return this.equilibriumVolumeAng3; }
        public double getBulkModulusGpa() { return this.bulkModulusGpa; }
        public double getBulkModulusDerivative() { return this.bulkModulusDerivative; }
        public double getMinimumEnergyRy() { return this.minimumEnergyRy; }
        public boolean isSuccess() { return this.success; }

        /** Evaluates third-order Birch-Murnaghan E(V), with V in Angstrom^3 and E in Ry. */
        public double evaluateEnergyRy(double volumeAng3) {
            if (!this.success || !finitePositive(volumeAng3)) {
                return Double.NaN;
            }
            double eta2 = Math.pow(this.equilibriumVolumeAng3 / volumeAng3, 2.0 / 3.0);
            double strain = eta2 - 1.0;
            return this.minimumEnergyRy + 9.0 * this.equilibriumVolumeAng3
                    * this.bulkModulusGpa * GPA_TO_RY_PER_ANGSTROM3 / 16.0
                    * (this.bulkModulusDerivative * strain * strain * strain
                    + (6.0 - 4.0 * eta2) * strain * strain);
        }

        private static boolean finitePositive(double value) {
            return Double.isFinite(value) && value > 0.0;
        }
    }

    private double equilibriumVolumeAng3;
    private double bulkModulusGpa;
    private double bulkModulusDerivative;
    private double minimumEnergyRy;
    private boolean eosParsed;

    public QEThermoPwEosParser(ProjectProperty property) { super(property); }

    public double getEquilibriumVolume() { return this.equilibriumVolumeAng3; }
    public double getBulkModulus() { return this.bulkModulusGpa; }
    public double getBulkModulusDerivative() { return this.bulkModulusDerivative; }
    public double getMinimumEnergy() { return this.minimumEnergyRy; }
    public boolean isEosParsed() { return this.eosParsed; }

    @Override
    public void parse(File file) throws IOException {
        reset();
        if (file == null || !file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher match = EOS.matcher(line);
                if (!match.find()) {
                    continue;
                }
                double volume = ScfConvergenceAnalyzer.parseFortranDouble(match.group(1));
                String unit = match.group(2).toLowerCase(Locale.ROOT);
                if (unit.startsWith("bohr")) {
                    volume *= BOHR3_TO_ANGSTROM3;
                }
                double bulk = ScfConvergenceAnalyzer.parseFortranDouble(match.group(3));
                double derivative = ScfConvergenceAnalyzer.parseFortranDouble(match.group(4));
                double energy = ScfConvergenceAnalyzer.parseFortranDouble(match.group(5));
                if (volume > 0.0 && bulk > 0.0 && Double.isFinite(derivative)
                        && Double.isFinite(energy)) {
                    this.equilibriumVolumeAng3 = volume;
                    this.bulkModulusGpa = bulk;
                    this.bulkModulusDerivative = derivative;
                    this.minimumEnergyRy = energy;
                    this.eosParsed = true;
                }
            }
        }
    }

    public EosResult getResult() {
        return new EosResult(this.equilibriumVolumeAng3, this.bulkModulusGpa,
                this.bulkModulusDerivative, this.minimumEnergyRy, this.eosParsed);
    }

    private void reset() {
        this.equilibriumVolumeAng3 = Double.NaN;
        this.bulkModulusGpa = Double.NaN;
        this.bulkModulusDerivative = Double.NaN;
        this.minimumEnergyRy = Double.NaN;
        this.eosParsed = false;
    }
}
