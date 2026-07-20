/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.result.special;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fermi contact isotropic hyperfine coupling (A_iso) helper with a registered,
 * explicitly bounded database of nuclear g-factors (gN) (Roadmap #166).
 *
 * <p>Fail-closed contract: an isotope that is not in the table yields NaN from
 * {@link #getNuclearGFactor(String)} and an IllegalArgumentException from
 * {@link #calculateAiso(double, String)}. Silent defaults (1.0) and silent zero
 * results were removed because they masqueraded as computed couplings. Any
 * non-finite input is rejected the same way.</p>
 */
public final class HyperfineMapper {

    /**
     * Compiled conversion constant in MHz * a0^3:
     * A_iso = HYPERFINE_FACTOR_MHZ * gN * |psi(0)|^2 with |psi(0)|^2 in a.u.^-3
     * (a0^-3). It embeds (mu0/4pi) * (8pi/3) * ge * muB * muN; treat the last
     * digits as subject to CODATA revision.
     */
    public static final double HYPERFINE_FACTOR_MHZ = 44.757237;

    // Database of standard nuclear g-factors (gN) for EPR-active isotopes.
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

    /** Sorted view of the isotope labels covered by the g-factor table. */
    public static List<String> coveredIsotopes() {
        List<String> labels = new ArrayList<>(ISOTOPE_GN_DATABASE.keySet());
        Collections.sort(labels);
        return List.copyOf(labels);
    }

    /**
     * Looks up the nuclear g-factor (gN) of the specified isotope (e.g. "13C").
     * Returns NaN when the isotope is not covered - callers must check and
     * refuse, never substitute a default.
     */
    public static double getNuclearGFactor(String isotope) {
        if (isotope == null) {
            return Double.NaN;
        }
        String key = isotope.trim().toUpperCase(Locale.ROOT);
        Double gn = ISOTOPE_GN_DATABASE.get(key);
        return gn == null ? Double.NaN : gn.doubleValue();
    }

    /**
     * A_iso = 44.757237 * gN * |psi(0)|^2  MHz (sign carries the product's sign;
     * a negative spin density or negative gN is reported, not clamped).
     *
     * @param psi0sq GIPAW nuclear spin density at the nucleus in a.u.^-3
     * @param gn nuclear g-factor of the target isotope
     * @throws IllegalArgumentException on any non-finite input
     */
    public static double calculateAiso(double psi0sq, double gn) {
        if (!Double.isFinite(psi0sq) || !Double.isFinite(gn)) {
            throw new IllegalArgumentException(
                    "Spin density and g-factor must be finite; a silent 0.0 MHz "
                            + "would masquerade as a computed coupling.");
        }
        return HYPERFINE_FACTOR_MHZ * gn * psi0sq;
    }

    /**
     * Calculates the coupling from the isotope label.
     *
     * @throws IllegalArgumentException when the isotope is not in the table
     */
    public static double calculateAiso(double psi0sq, String isotope) {
        double gn = getNuclearGFactor(isotope);
        if (!Double.isFinite(gn)) {
            throw new IllegalArgumentException("Isotope \"" + isotope
                    + "\" is not covered by the g-factor table " + coveredIsotopes()
                    + "; refusing to invent a g-factor.");
        }
        return calculateAiso(psi0sq, gn);
    }
}
