/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SCF iteration energy / estimated accuracy from pw.x text logs.
 *
 * <p>Recognises both intermediate {@code total energy} lines and the final
 * {@code !    total energy} convergence marker. Values are in Rydberg as printed
 * by Quantum ESPRESSO.</p>
 */
public final class ScfConvergenceAnalyzer {

    private static final Pattern TOTAL_ENERGY = Pattern.compile(
            "^\\s*!\\s*total energy\\s*=\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][+-]?\\d+)?)\\s*Ry",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_ENERGY_PLAIN = Pattern.compile(
            "^\\s*total energy\\s*=\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][+-]?\\d+)?)\\s*Ry",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCURACY = Pattern.compile(
            "estimated scf accuracy\\s*<\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][+-]?\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_CONVERGED = Pattern.compile(
            "convergence NOT achieved", Pattern.CASE_INSENSITIVE);

    public enum Trend {
        UNKNOWN, DECREASING_ERROR, OSCILLATING, DIVERGING, FLAT
    }

    public static final class Report {
        private final List<ScfIterationRecord> iterations;
        private final boolean converged;
        private final boolean explicitlyNotConverged;
        private final Trend trend;
        private final Double finalEnergyRy;
        private final Double finalAccuracyRy;

        public Report(List<ScfIterationRecord> iterations, boolean converged,
                      boolean explicitlyNotConverged, Trend trend,
                      Double finalEnergyRy, Double finalAccuracyRy) {
            this.iterations = Collections.unmodifiableList(new ArrayList<>(iterations));
            this.converged = converged;
            this.explicitlyNotConverged = explicitlyNotConverged;
            this.trend = trend;
            this.finalEnergyRy = finalEnergyRy;
            this.finalAccuracyRy = finalAccuracyRy;
        }

        public List<ScfIterationRecord> getIterations() { return this.iterations; }
        public boolean isConverged() { return this.converged; }
        public boolean isExplicitlyNotConverged() { return this.explicitlyNotConverged; }
        public Trend getTrend() { return this.trend; }
        public Double getFinalEnergyRy() { return this.finalEnergyRy; }
        public Double getFinalAccuracyRy() { return this.finalAccuracyRy; }
    }

    private ScfConvergenceAnalyzer() {
        // Utility.
    }

    public static Report analyze(String logText) {
        List<String> lines = new ArrayList<>();
        if (logText != null) {
            for (String line : logText.split("\\R", -1)) {
                lines.add(line);
            }
        }
        return analyze(lines);
    }

    public static Report analyze(List<String> lines) {
        List<ScfIterationRecord> iterations = new ArrayList<>();
        boolean notConverged = false;
        Double pendingAccuracy = null;
        int index = 0;
        if (lines != null) {
            for (String raw : lines) {
                if (raw == null) {
                    continue;
                }
                String line = raw;
                if (NOT_CONVERGED.matcher(line).find()) {
                    notConverged = true;
                }
                Matcher acc = ACCURACY.matcher(line);
                if (acc.find()) {
                    pendingAccuracy = parseFortranDouble(acc.group(1));
                }
                Matcher bang = TOTAL_ENERGY.matcher(line);
                Matcher plain = TOTAL_ENERGY_PLAIN.matcher(line);
                Matcher energyMatch = bang.find() ? bang : (plain.find() ? plain : null);
                if (energyMatch != null) {
                    double energy = parseFortranDouble(energyMatch.group(1));
                    boolean convergedLine = line.trim().startsWith("!");
                    index++;
                    iterations.add(new ScfIterationRecord(index, energy, pendingAccuracy,
                            convergedLine, line.trim()));
                    pendingAccuracy = null;
                }
            }
        }

        boolean converged = false;
        Double finalEnergy = null;
        Double finalAccuracy = null;
        if (!iterations.isEmpty()) {
            ScfIterationRecord last = iterations.get(iterations.size() - 1);
            finalEnergy = last.getTotalEnergyRy();
            finalAccuracy = last.getEstimatedAccuracyRy();
            converged = last.isFinalConvergedLine() && !notConverged;
        }
        Trend trend = classifyTrend(iterations);
        return new Report(iterations, converged, notConverged, trend, finalEnergy, finalAccuracy);
    }

    static Trend classifyTrend(List<ScfIterationRecord> iterations) {
        List<Double> acc = new ArrayList<>();
        for (ScfIterationRecord record : iterations) {
            if (record.getEstimatedAccuracyRy() != null
                    && Double.isFinite(record.getEstimatedAccuracyRy())) {
                acc.add(record.getEstimatedAccuracyRy());
            }
        }
        if (acc.size() < 3) {
            return Trend.UNKNOWN;
        }
        int improving = 0;
        int worsening = 0;
        int signChanges = 0;
        double prevDelta = 0.0;
        for (int i = 1; i < acc.size(); i++) {
            double delta = acc.get(i) - acc.get(i - 1);
            if (delta < -1.0e-12) {
                improving++;
            } else if (delta > 1.0e-12) {
                worsening++;
            }
            if (i > 1 && Math.signum(delta) != 0 && Math.signum(prevDelta) != 0
                    && Math.signum(delta) != Math.signum(prevDelta)) {
                signChanges++;
            }
            prevDelta = delta;
        }
        if (signChanges >= Math.max(2, acc.size() / 3)) {
            return Trend.OSCILLATING;
        }
        if (worsening > improving * 2) {
            return Trend.DIVERGING;
        }
        if (improving >= worsening) {
            return Trend.DECREASING_ERROR;
        }
        double first = acc.get(0);
        double last = acc.get(acc.size() - 1);
        if (Math.abs(first - last) / Math.max(Math.abs(first), 1.0e-30) < 1.0e-3) {
            return Trend.FLAT;
        }
        return Trend.UNKNOWN;
    }

    static double parseFortranDouble(String token) {
        if (token == null) {
            throw new NumberFormatException("null");
        }
        String normalized = token.trim().replace('D', 'E').replace('d', 'e');
        return Double.parseDouble(normalized);
    }

    public static String formatSummary(Report report) {
        if (report == null) {
            return "No SCF report.";
        }
        StringBuilder out = new StringBuilder();
        out.append("SCF iterations=").append(report.getIterations().size());
        out.append(" converged=").append(report.isConverged());
        if (report.isExplicitlyNotConverged()) {
            out.append(" (explicit NOT achieved)");
        }
        out.append(" trend=").append(report.getTrend().name().toLowerCase(Locale.ROOT));
        if (report.getFinalEnergyRy() != null) {
            out.append(String.format(Locale.ROOT, " E=%.8f Ry", report.getFinalEnergyRy()));
        }
        if (report.getFinalAccuracyRy() != null) {
            out.append(String.format(Locale.ROOT, " acc=%.3e Ry", report.getFinalAccuracyRy()));
        }
        return out.toString();
    }
}
