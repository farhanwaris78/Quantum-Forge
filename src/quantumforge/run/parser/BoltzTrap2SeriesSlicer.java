/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.run.parser.BoltzTrap2TraceParser.TransportRow;

/**
 * Temperature-series slicer for parsed BoltzTraP2 transport rows (Roadmap
 * #109 chart slice): turns the flat (mu, T) grid the writer emits into the
 * per-temperature curves a chart panel draws. Headless so every y-value on
 * screen is unit-verified against {@link BoltzTrap2TraceParser} itself.
 *
 * <p>Honesty rules:</p>
 * <ul>
 *   <li>chemical potentials are grouped by their EXACT parsed double (the
 *       writer prints the grid; identical text yields identical bits) - a
 *       mu that is not in the file yields an EMPTY series with the caller
 *       expected to say so, never an interpolated or nearest-row fake;</li>
 *   <li>series values are the parser's own numbers: isotropic S, sigma/tau
 *       and kappa/tau in header-verbatim units, the honestly labeled
 *       S^2.sigma power-factor approximation (never the tensor trace one),
 *       and the .condtens Seebeck diagonals - diagonal kinds on .trace rows
 *       REFUSE (that grammar never wrote them);</li>
 *   <li>sorting is by temperature with a stable file-order tie-break;
 *       duplicate-T rows are kept (surprises are displayed, not silently
 *       deduplicated).</li>
 * </ul>
 */
public final class BoltzTrap2SeriesSlicer {

    /** Series a chart can draw from a TransportRow. */
    public enum SeriesKind {
        /** Isotropic Seebeck coefficient, V/K. */
        SEEBECK,
        /** sigma/tau in the header's verbatim units. */
        SIGMA_OVER_TAU,
        /** kappa_e/tau in the header's verbatim units. */
        KAPPA_OVER_TAU,
        /** S^2.sigma power factor (isotropic-average approximation). */
        POWER_FACTOR,
        /** Seebeck tensor diagonal xx (.condtens rows only). */
        SEEBECK_XX,
        /** Seebeck tensor diagonal yy (.condtens rows only). */
        SEEBECK_YY,
        /** Seebeck tensor diagonal zz (.condtens rows only). */
        SEEBECK_ZZ
    }

    /** One sliced temperature curve (points sorted by T, file order stable). */
    public static final class TemperatureSeries {
        private final double muRy;
        private final SeriesKind kind;
        private final List<Double> temperatures;
        private final List<Double> values;

        TemperatureSeries(double muRy, SeriesKind kind, List<Double> temperatures,
                List<Double> values) {
            this.muRy = muRy;
            this.kind = kind;
            this.temperatures = List.copyOf(temperatures);
            this.values = List.copyOf(values);
        }

        /** The exact mu the slice was taken at (Ri, as written). */
        public double getMuRy() { return this.muRy; }

        /** which series this is. */
        public SeriesKind getKind() { return this.kind; }

        /** temperature points (K), ascending, duplicates kept. */
        public List<Double> getTemperatures() { return this.temperatures; }

        /** values paired with {@link #getTemperatures()}, same length. */
        public List<Double> getValues() { return this.values; }

        /** point count. */
        public int size() { return this.values.size(); }
    }

    private BoltzTrap2SeriesSlicer() {
        // Utility
    }

    /**
     * The distinct chemical potentials present in the rows, ascending. Exact
     * double grouping - the writer's own grid, never binned.
     */
    public static List<Double> distinctMuRy(List<TransportRow> rows) {
        List<Double> out = new ArrayList<>();
        for (TransportRow row : rows) {
            double mu = row.getMuRy();
            boolean seen = false;
            for (int i = 0; i < out.size(); i++) {
                if (Double.doubleToLongBits(out.get(i)) == Double.doubleToLongBits(mu)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                // insert sorted (grids are small; insertion order stays honest)
                int at = out.size();
                while (at > 0 && out.get(at - 1) > mu) {
                    at--;
                }
                out.add(at, mu);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Slices one temperature series at the exact requested mu. Diagonal
     * kinds REFUSE on rows that carry no diagonals (.trace: the writer
     * never wrote them - refusing beats fabricating isotropics as xx/yy/zz).
     * A mu absent from the rows yields an EMPTY series (size 0) - the panel
     * says "no curve", nothing is interpolated.
     */
    public static TemperatureSeries slice(List<TransportRow> rows, double muRy,
            SeriesKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("No series kind selected.");
        }
        if (needsDiagonal(kind)) {
            for (TransportRow row : rows) {
                if (row.getSeebeckDiag() == null) {
                    throw new IllegalArgumentException(
                            "Series " + kind + " needs the .condtens tensor diagonal, but at"
                                    + " least one row carries none (.trace grammar wrote"
                                    + " isotropic columns only) - refusing instead of"
                                    + " relabeling isotropic values as xx/yy/zz.");
                }
            }
        }
        List<double[]> points = new ArrayList<>();
        for (TransportRow row : rows) {
            if (Double.doubleToLongBits(row.getMuRy()) != Double.doubleToLongBits(muRy)) {
                continue;
            }
            points.add(new double[] {row.getTemperatureK(), valueOf(row, kind)});
        }
        points.sort((first, second) -> {
            int byT = Double.compare(first[0], second[0]);
            return byT != 0 ? byT : 0; // stable: file order breaks ties
        });
        List<Double> temperatures = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (double[] point : points) {
            temperatures.add(point[0]);
            values.add(point[1]);
        }
        return new TemperatureSeries(muRy, kind, temperatures, values);
    }

    private static boolean needsDiagonal(SeriesKind kind) {
        return kind == SeriesKind.SEEBECK_XX || kind == SeriesKind.SEEBECK_YY
                || kind == SeriesKind.SEEBECK_ZZ;
    }

    /** The parser's own number for this series kind (no rescaled physics). */
    static double valueOf(TransportRow row, SeriesKind kind) {
        return switch (kind) {
            case SEEBECK -> row.getSeebeckVK();
            case SIGMA_OVER_TAU -> row.getSigmaOverTau();
            case KAPPA_OVER_TAU -> row.getKappaOverTau();
            case POWER_FACTOR -> BoltzTrap2TraceParser.powerFactor(row);
            case SEEBECK_XX -> row.getSeebeckDiag()[0];
            case SEEBECK_YY -> row.getSeebeckDiag()[1];
            case SEEBECK_ZZ -> row.getSeebeckDiag()[2];
        };
    }

    /** Axis label for a series, with header-verbatim units where they exist. */
    public static String axisLabel(SeriesKind kind, String sigmaUnits, String kappaUnits) {
        return switch (kind) {
            case SEEBECK, SEEBECK_XX, SEEBECK_YY, SEEBECK_ZZ -> "Seebeck S [V/K]";
            case SIGMA_OVER_TAU -> sigmaUnits == null || sigmaUnits.isEmpty()
                    ? "sigma/tau [header units]" : "sigma/tau [" + sigmaUnits + "]";
            case KAPPA_OVER_TAU -> kappaUnits == null || kappaUnits.isEmpty()
                    ? "kappa/tau [header units]" : "kappa/tau [" + kappaUnits + "]";
            case POWER_FACTOR -> String.format(Locale.ROOT,
                    "S^2.sigma (isotropic-average approximation)%s",
                    sigmaUnits == null || sigmaUnits.isEmpty() ? ""
                            : "  [ (V/K)^2 x " + sigmaUnits + " ]");
        };
    }
}
