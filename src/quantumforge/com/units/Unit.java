/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.units;

import quantumforge.com.consts.Constants;

/**
 * Explicit scientific units with SI conversion factors.
 *
 * <p>Conversion factors follow Quantum ESPRESSO / NIST-style constants already
 * present in {@link Constants}. Every conversion is reversible within floating
 * point noise.</p>
 */
public enum Unit {

    // Energy
    RYDBERG(Dimension.ENERGY, "Ry", 1.0, Constants.RYDBERG_SI),
    HARTREE(Dimension.ENERGY, "Ha", 1.0, Constants.HARTREE_SI),
    ELECTRONVOLT(Dimension.ENERGY, "eV", 1.0, Constants.ELECTRONVOLT_SI),
    JOULE(Dimension.ENERGY, "J", 1.0, 1.0),

    // Length
    BOHR(Dimension.LENGTH, "bohr", 1.0, Constants.BOHR_RADIUS_SI),
    ANGSTROM(Dimension.LENGTH, "Ang", 1.0, 1.0e-10),
    METER(Dimension.LENGTH, "m", 1.0, 1.0),

    // Pressure
    RY_PER_BOHR3(Dimension.PRESSURE, "Ry/bohr^3", 1.0, Constants.RYDBERG_SI
            / (Constants.BOHR_RADIUS_SI * Constants.BOHR_RADIUS_SI * Constants.BOHR_RADIUS_SI)),
    KBAR(Dimension.PRESSURE, "kbar", 1.0, 1.0e8),
    GPA(Dimension.PRESSURE, "GPa", 1.0, 1.0e9),
    PASCAL(Dimension.PRESSURE, "Pa", 1.0, 1.0),

    // Frequency / wavenumber-style spectroscopic units used in phonon work
    INVERSE_CM(Dimension.FREQUENCY, "cm^-1", 1.0,
            Constants.C_SI * 100.0), // Hz equivalent of 1 cm^-1 (c in cm/s)
    THZ(Dimension.FREQUENCY, "THz", 1.0, 1.0e12),
    HERTZ(Dimension.FREQUENCY, "Hz", 1.0, 1.0),

    // Temperature
    KELVIN(Dimension.TEMPERATURE, "K", 1.0, 1.0);

    public enum Dimension {
        ENERGY, LENGTH, PRESSURE, FREQUENCY, TEMPERATURE
    }

    private final Dimension dimension;
    private final String symbol;
    /** Multiplier from this unit to SI (value_si = value * toSiFactor). */
    private final double toSiFactor;

    Unit(Dimension dimension, String symbol, double unusedCompatibility, double toSiFactor) {
        this.dimension = dimension;
        this.symbol = symbol;
        this.toSiFactor = toSiFactor;
    }

    public Dimension getDimension() {
        return this.dimension;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public double toSi(double value) {
        return value * this.toSiFactor;
    }

    public double fromSi(double siValue) {
        return siValue / this.toSiFactor;
    }

    /**
     * Convert a raw value between two units of the same dimension.
     */
    public static double convert(double value, Unit from, Unit to) {
        return PhysicalQuantity.of(value, from).valueIn(to);
    }
}
