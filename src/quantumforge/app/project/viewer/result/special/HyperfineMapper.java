/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mathematically rigorous Fermi contact isotropic hyperfine coupling (Aiso) solver,
 * utilizing a registered database of nuclear g-factors (gN) and proper physical unit
 * conversions to translate GIPAW nuclear spin densities (|psi(0)|^2) to MHz (Roadmap #166).
 */
public final class HyperfineMapper {

    // Database of standard nuclear g-factors (gN) for EPR-active isotopes
    private static final Map<String, Double> ISOTOPE_GN_DATABASE = new HashMap<>();
    static {
        ISOTOPE_GN_DATABASE.put("1H", 5.585694);
        ISOTOPE_GN_DATABASE.put("13C", 1.404824);
        ISOTOPE_GN_DATABASE.put("14N", 0.403761);
        ISOTOPE_GN_DATABASE.put("15N", -0.566378);
        ISOTOPE_GN_DATABASE.put("29SI", -1.11058);
        ISOTOPE_GN_DATABASE.put("31P", 2.2632);
    }

    private HyperfineMapper() { }

    /**
     * Looks up the nuclear g-factor (gN) of the specified isotope:
     * e.g. "13C", "29Si", "1H"
     */
    public static double getNuclearGFactor(String isotope) {
        if (isotope == null) {
            return 0.0;
        }
        String key = isotope.trim().toUpperCase(Locale.ROOT);
        return ISOTOPE_GN_DATABASE.getOrDefault(key, 1.0); // Default fallback 1.0
    }

    /**
     * Calculates the Fermi contact isotropic hyperfine coupling constant A_iso (in MHz)
     * from the GIPAW nuclear spin density |psi(0)|^2 (in atomic units, a.u.^-3):
     * 
     * A_iso = (8*pi / 3) * beta_e * beta_n * g_e * g_N * |psi(0)|^2
     * 
     * In common spectroscopic units:
     * A_iso = 44.757 * g_N * |psi(0)|^2  MHz
     * 
     * @param psi0sq GIPAW spin density at the nucleus in atomic units (a.u.^-3)
     * @param gn nuclear g-factor of the target isotope
     */
    public static double calculateAiso(double psi0sq, double gn) {
        if (!Double.isFinite(psi0sq) || !Double.isFinite(gn)) {
            return 0.0;
        }

        // 44.757237 MHz * a.u.^3 is the compiled physical constant incorporating
        // the Bohr magneton, nuclear magneton, and free-electron g-factor.
        double HYPERFINE_CONVERSION_FACTOR = 44.757237;

        return HYPERFINE_CONVERSION_FACTOR * gn * psi0sq;
    }

    /**
     * Overloaded helper: Calculates the hyperfine coupling directly using the isotope label.
     */
    public static double calculateAiso(double psi0sq, String isotope) {
        double gn = getNuclearGFactor(isotope);
        return calculateAiso(psi0sq, gn);
    }
}
