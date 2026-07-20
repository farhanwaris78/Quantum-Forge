/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import quantumforge.operation.OperationResult;

/**
 * Small, HONEST unit library for quantum-chemistry quantities (Roadmap #26
 * first slice): a curated registry of the units this application's reports
 * actually print, with their conversion factors pinned and their provenance
 * stated. Rules that make it trustworthy:
 *
 * <ul>
 *   <li>SI-exact relations (2019 SI: e, N_A, h, c are exact definitions) are
 *       used for eV&lt;-&gt;kJ/mol and eV&lt;-&gt;cm^-1&lt;-&gt;THz;</li>
 *   <li>measured constants (Hartree and Bohr radii from CODATA-2018, carried
 *       at the same digits the rest of the codebase already pins - the cube
 *       reader uses 0.529177210903) are labelled as CODATA values with a
 *       stated precision, never presented as exact;</li>
 *   <li>cross-domain spectroscopic conversions (energy &lt;-&gt; cm^-1/THz)
 *       are allowed but explicitly flagged as the spectroscopic convention;</li>
 *   <li>incompatible domains (energy &lt;-&gt; length &lt;-&gt; pressure)
 *       and unknown tokens fail closed - no guessing, no silent defaults.</li>
 * </ul>
 *
 * Full unchecked unit coverage (temperature, dipole moments, etc.) is the
 * remaining #26 work.
 */
public final class QEUnits {

    /** Domain of a curated unit; only same-domain or the stated spectroscopic bridge converts. */
    public enum Domain {
        ENERGY,
        /** cm^-1 / THz - bridged to ENERGY via the exact SI spectroscopic convention. */
        SPECTROSCOPIC,
        LENGTH,
        PRESSURE
    }

    /** One curated unit: canonical factor to the domain base (eV / Angstrom / GPa). */
    public static final class Unit {
        private final String canonicalName;
        private final Domain domain;
        private final double factorToCanonical;

        Unit(String canonicalName, Domain domain, double factorToCanonical) {
            this.canonicalName = canonicalName;
            this.domain = domain;
            this.factorToCanonical = factorToCanonical;
        }

        public String getCanonicalName() { return this.canonicalName; }
        public Domain getDomain() { return this.domain; }
        public double getFactorToCanonical() { return this.factorToCanonical; }
    }

    /** A completed conversion. */
    public static final class Conversion {
        private final double valueFrom;
        private final String fromUnit;
        private final String toUnit;
        private final double valueTo;
        private final boolean spectroscopicBridge;

        Conversion(double valueFrom, String fromUnit, String toUnit, double valueTo,
                boolean spectroscopicBridge) {
            this.valueFrom = valueFrom;
            this.fromUnit = fromUnit;
            this.toUnit = toUnit;
            this.valueTo = valueTo;
            this.spectroscopicBridge = spectroscopicBridge;
        }

        public double getValueFrom() { return this.valueFrom; }
        public String getFromUnit() { return this.fromUnit; }
        public String getToUnit() { return this.toUnit; }
        public double getValueTo() { return this.valueTo; }

        /** True when the energy &lt;-&gt; cm^-1/THz bridge was crossed. */
        public boolean isSpectroscopicBridge() { return this.spectroscopicBridge; }
    }

    // Pinned factors (provenance stated in the class javadoc):
    /** eV per Rydberg, CODATA-2018 (measured; reported at 12 significant digits). */
    public static final double EV_PER_RY = 13.605693122994;
    /** eV per Hartree, CODATA-2018 (measured; reported at 12 significant digits). */
    public static final double EV_PER_HA = 27.211386245988;
    /** eV per kJ/mol reciprocal, SI-exact (e * N_A / 1000). */
    public static final double KJMOL_PER_EV = 96.48533212331002;
    /** eV per wavenumber (cm^-1), SI-exact (h * c / e in J -> eV). */
    public static final double EV_PER_WAVENUMBER = 1.2398419843320026e-4;
    /** eV per THz, SI-exact (h * 1e12 / e). */
    public static final double EV_PER_THZ = 4.135667696923859e-3;
    /** Angstrom per Bohr radius, CODATA-2018 (matches the cube reader's constant). */
    public static final double ANG_PER_BOHR = 0.529177210903;

    private static final Map<String, Unit> REGISTRY = buildRegistry();

    private QEUnits() { }

    /** All curated units (for honest "what I know" listings). */
    public static List<String> listUnitTokens() {
        return List.copyOf(REGISTRY.keySet());
    }

    /** Looks a unit up by token or alias; unknown tokens are an honest empty. */
    public static Optional<Unit> findUnit(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String key = token.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("å", "ang").replace("angstrom", "ang")
                .replace("wavenumber", "cm-1").replace("cm^-1", "cm-1")
                .replace("rydberg", "ry").replace("hartree", "ha")
                .replace("kj/mol", "kjmol");
        return Optional.ofNullable(REGISTRY.get(key));
    }

    /**
     * Converts a finite value between curated units. Codes: UNIT_SYNTAX
     * (NaN/infinite value or blank token), UNIT_UNKNOWN (token outside the
     * curated registry), UNIT_DOMAIN (incompatible domains).
     */
    public static OperationResult<Conversion> convert(double value, String fromToken,
            String toToken) {
        if (!Double.isFinite(value)) {
            return OperationResult.failed("UNIT_SYNTAX",
                    "The value to convert must be finite, got: " + value, null);
        }
        Optional<Unit> from = findUnit(fromToken);
        if (from.isEmpty()) {
            return OperationResult.failed("UNIT_UNKNOWN",
                    "Unknown unit \"" + fromToken + "\"; curated tokens: "
                            + listUnitTokens(),
                    null);
        }
        Optional<Unit> to = findUnit(toToken);
        if (to.isEmpty()) {
            return OperationResult.failed("UNIT_UNKNOWN",
                    "Unknown unit \"" + toToken + "\"; curated tokens: "
                            + listUnitTokens(),
                    null);
        }
        Unit source = from.get();
        Unit target = to.get();
        boolean bridge = source.getDomain() != target.getDomain();
        if (bridge && !bridgesOnlySpectroscopic(source.getDomain(), target.getDomain())) {
            return OperationResult.failed("UNIT_DOMAIN",
                    "Cannot convert " + source.getCanonicalName() + " (" + source.getDomain()
                            + ") to " + target.getCanonicalName() + " (" + target.getDomain()
                            + "): the domains are physically incompatible.",
                    null);
        }
        double converted = value * source.getFactorToCanonical()
                / target.getFactorToCanonical();
        if (!Double.isFinite(converted)) {
            return OperationResult.failed("UNIT_SYNTAX",
                    "The conversion overflowed double arithmetic - refused.", null);
        }
        return OperationResult.success("UNIT_OK", "Converted.",
                new Conversion(value, source.getCanonicalName(), target.getCanonicalName(),
                        converted, bridge));
    }

    private static boolean bridgesOnlySpectroscopic(Domain a, Domain b) {
        return (a == Domain.ENERGY && b == Domain.SPECTROSCOPIC)
                || (a == Domain.SPECTROSCOPIC && b == Domain.ENERGY);
    }

    private static Map<String, Unit> buildRegistry() {
        Map<String, Unit> registry = new LinkedHashMap<>();
        registry.put("ry", new Unit("Ry", Domain.ENERGY, EV_PER_RY));
        registry.put("ev", new Unit("eV", Domain.ENERGY, 1.0));
        registry.put("mev", new Unit("meV", Domain.ENERGY, 1.0e-3));
        registry.put("ha", new Unit("Ha", Domain.ENERGY, EV_PER_HA));
        registry.put("kjmol", new Unit("kJ/mol", Domain.ENERGY, 1.0 / KJMOL_PER_EV));
        registry.put("cm-1", new Unit("cm^-1", Domain.SPECTROSCOPIC, EV_PER_WAVENUMBER));
        registry.put("thz", new Unit("THz", Domain.SPECTROSCOPIC, EV_PER_THZ));
        registry.put("bohr", new Unit("bohr", Domain.LENGTH, ANG_PER_BOHR));
        registry.put("ang", new Unit("Angstrom", Domain.LENGTH, 1.0));
        registry.put("nm", new Unit("nm", Domain.LENGTH, 10.0));
        registry.put("pm", new Unit("pm", Domain.LENGTH, 0.01));
        registry.put("kbar", new Unit("kbar", Domain.PRESSURE, 0.1));
        registry.put("gpa", new Unit("GPa", Domain.PRESSURE, 1.0));
        return Map.copyOf(registry);
    }
}
