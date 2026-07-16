/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import quantumforge.com.units.PhysicalQuantity;
import quantumforge.com.units.Unit;
import quantumforge.operation.OperationResult;

/**
 * Phonon-DOS thermodynamic integration (harmonic approximation).
 *
 * <p>Integrates a validated nonuniform frequency grid. Frequencies are treated as
 * cm^-1 unless the caller converts first. This replaces fabricated closed-form
 * placeholders with explicit trapezoidal integrals and unit-aware outputs.</p>
 */
public final class PhononDosThermodynamics {

    /** k_B in eV/K */
    public static final double K_BOLTZMANN_EV = 8.617333262145e-5;
    /** hc in eV·cm (so E(eV) = freq(cm^-1) * HC_EV_CM) */
    public static final double HC_EV_CM = 1.2398419843320026e-4;

    public static final class Result {
        private final double temperatureK;
        private final double zeroPointEnergyEv;
        private final double helmholtzFreeEnergyEv;
        private final double internalEnergyEv;
        private final double entropyEvPerK;
        private final double heatCapacityEvPerK;
        private final double integratedDos;
        private final String notes;

        public Result(double temperatureK, double zeroPointEnergyEv, double helmholtzFreeEnergyEv,
                      double internalEnergyEv, double entropyEvPerK, double heatCapacityEvPerK,
                      double integratedDos, String notes) {
            this.temperatureK = temperatureK;
            this.zeroPointEnergyEv = zeroPointEnergyEv;
            this.helmholtzFreeEnergyEv = helmholtzFreeEnergyEv;
            this.internalEnergyEv = internalEnergyEv;
            this.entropyEvPerK = entropyEvPerK;
            this.heatCapacityEvPerK = heatCapacityEvPerK;
            this.integratedDos = integratedDos;
            this.notes = notes == null ? "" : notes;
        }

        public double getTemperatureK() { return this.temperatureK; }
        public double getZeroPointEnergyEv() { return this.zeroPointEnergyEv; }
        public double getHelmholtzFreeEnergyEv() { return this.helmholtzFreeEnergyEv; }
        public double getInternalEnergyEv() { return this.internalEnergyEv; }
        public double getEntropyEvPerK() { return this.entropyEvPerK; }
        public double getHeatCapacityEvPerK() { return this.heatCapacityEvPerK; }
        public double getIntegratedDos() { return this.integratedDos; }
        public String getNotes() { return this.notes; }

        public PhysicalQuantity zeroPointEnergy() {
            return PhysicalQuantity.of(this.zeroPointEnergyEv, Unit.ELECTRONVOLT);
        }
    }

    private PhononDosThermodynamics() {
        // Utility.
    }

    /**
     * @param frequencyCmInverse frequency grid in cm^-1 (strictly increasing, non-negative)
     * @param dos density of states on the same grid (non-negative, finite)
     * @param temperatureK temperature in kelvin (&gt; 0)
     */
    public static OperationResult<Result> integrate(double[] frequencyCmInverse, double[] dos,
                                                    double temperatureK) {
        if (frequencyCmInverse == null || dos == null) {
            return OperationResult.failed("PHONON_DOS_NULL", "Frequency/DOS arrays are null.", null);
        }
        if (frequencyCmInverse.length != dos.length || frequencyCmInverse.length < 2) {
            return OperationResult.failed("PHONON_DOS_LENGTH",
                    "Frequency and DOS must have equal length >= 2.", null);
        }
        if (!(temperatureK > 0.0) || !Double.isFinite(temperatureK)) {
            return OperationResult.failed("PHONON_T", "Temperature must be positive and finite.", null);
        }
        for (int i = 0; i < frequencyCmInverse.length; i++) {
            if (!Double.isFinite(frequencyCmInverse[i]) || frequencyCmInverse[i] < 0.0) {
                return OperationResult.failed("PHONON_FREQ",
                        "Frequencies must be finite and non-negative (cm^-1).", null);
            }
            if (!Double.isFinite(dos[i]) || dos[i] < 0.0) {
                return OperationResult.failed("PHONON_DOS",
                        "DOS values must be finite and non-negative.", null);
            }
            if (i > 0 && !(frequencyCmInverse[i] > frequencyCmInverse[i - 1])) {
                return OperationResult.failed("PHONON_GRID",
                        "Frequency grid must be strictly increasing.", null);
            }
        }

        double kT = K_BOLTZMANN_EV * temperatureK;
        double zpe = 0.0;
        double u = 0.0;
        double f = 0.0;
        double s = 0.0;
        double cv = 0.0;
        double nModes = 0.0;

        for (int i = 0; i < frequencyCmInverse.length - 1; i++) {
            double w1 = frequencyCmInverse[i];
            double w2 = frequencyCmInverse[i + 1];
            double d1 = dos[i];
            double d2 = dos[i + 1];
            double dw = w2 - w1;
            // Trapezoid weight
            nModes += 0.5 * (d1 + d2) * dw;

            double e1 = phononEnergyEv(w1);
            double e2 = phononEnergyEv(w2);
            zpe += 0.5 * 0.5 * (e1 * d1 + e2 * d2) * dw;

            double f1 = freeEnergyDensity(e1, kT);
            double f2 = freeEnergyDensity(e2, kT);
            f += 0.5 * (f1 * d1 + f2 * d2) * dw;

            double u1 = internalEnergyDensity(e1, kT);
            double u2 = internalEnergyDensity(e2, kT);
            u += 0.5 * (u1 * d1 + u2 * d2) * dw;

            double s1 = entropyDensity(e1, kT);
            double s2 = entropyDensity(e2, kT);
            s += 0.5 * (s1 * d1 + s2 * d2) * dw;

            double c1 = heatCapacityDensity(e1, kT);
            double c2 = heatCapacityDensity(e2, kT);
            cv += 0.5 * (c1 * d1 + c2 * d2) * dw;
        }

        // F = ZPE + kT ∫ g(w) ln(1-e^{-βħw}) ... handled via freeEnergyDensity including ZPE term carefully.
        // Our freeEnergyDensity includes ħw/2 + kT ln(1-exp(-x)).
        String notes = String.format(Locale.ROOT,
                "Harmonic phonon DOS integration; ∫g(ω)dω=%.6g modes; frequencies in cm^-1; energies in eV.",
                nModes);
        Result result = new Result(temperatureK, zpe, f, u, s, cv, nModes, notes);
        return OperationResult.success("PHONON_THERMO_OK", notes, result);
    }

    static double phononEnergyEv(double frequencyCmInverse) {
        return frequencyCmInverse * HC_EV_CM;
    }

    static double freeEnergyDensity(double energyEv, double kT) {
        if (energyEv <= 0.0) {
            return 0.0;
        }
        double x = energyEv / kT;
        if (x > 100.0) {
            return 0.5 * energyEv;
        }
        // F/mode = ħω/2 + kT ln(1 - e^{-x})
        return 0.5 * energyEv + kT * Math.log1p(-Math.exp(-x));
    }

    static double internalEnergyDensity(double energyEv, double kT) {
        if (energyEv <= 0.0) {
            return 0.0;
        }
        double x = energyEv / kT;
        if (x > 100.0) {
            return 0.5 * energyEv;
        }
        // U/mode = ħω/2 + ħω/(e^x - 1)
        double n = 1.0 / Math.expm1(x);
        return 0.5 * energyEv + energyEv * n;
    }

    static double entropyDensity(double energyEv, double kT) {
        if (energyEv <= 0.0 || kT <= 0.0) {
            return 0.0;
        }
        double x = energyEv / kT;
        if (x > 100.0) {
            return 0.0;
        }
        // S/k = x/(e^x-1) - ln(1-e^{-x})
        double n = 1.0 / Math.expm1(x);
        return K_BOLTZMANN_EV * (x * n - Math.log1p(-Math.exp(-x)));
    }

    static double heatCapacityDensity(double energyEv, double kT) {
        if (energyEv <= 0.0 || kT <= 0.0) {
            return 0.0;
        }
        double x = energyEv / kT;
        if (x > 100.0 || x < 1.0e-12) {
            return 0.0;
        }
        // Cv/k = x^2 e^x / (e^x - 1)^2
        double ex = Math.exp(x);
        double denom = Math.expm1(x);
        double value = (x * x * ex) / (denom * denom);
        return K_BOLTZMANN_EV * value;
    }
}
