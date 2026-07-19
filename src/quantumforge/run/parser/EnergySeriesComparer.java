/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Same-grid comparison of two energy series (Roadmap #124 data layer). The
 * input CSV shares one parameter column between the two energy columns, so
 * grid agreement holds by construction. No reference alignment (Fermi, VBM,
 * vacuum) is ever applied here: aligned comparisons are an interactive choice,
 * not a hidden shift.
 */
public final class EnergySeriesComparer {

    public static final int MAX_ROWS_PER_COMPARISON = 200_000;

    /** The completed comparison. */
    public static final class SeriesComparison {
        private final int rowCount;
        private final int rejectedRows;
        private final int outOfOrderRows;
        private final String parameterLabel;
        private final String firstSeriesLabel;
        private final String secondSeriesLabel;
        private final double rmsDeltaEv;
        private final double meanSignedDeltaEv;
        private final double maxAbsDeltaEv;
        private final double maxAbsDeltaAtParameter;
        private final double firstDeltaEv;
        private final double lastDeltaEv;
        private final int signCrossings;
        private final List<double[]> rows;

        private SeriesComparison(Builder builder) {
            this.rowCount = builder.rows.size();
            this.rejectedRows = builder.rejectedRows;
            this.outOfOrderRows = builder.outOfOrderRows;
            this.parameterLabel = builder.parameterLabel;
            this.firstSeriesLabel = builder.firstSeriesLabel;
            this.secondSeriesLabel = builder.secondSeriesLabel;
            this.rows = List.copyOf(builder.rows);
            double sumSq = 0.0;
            double sum = 0.0;
            double maxAbs = -1.0;
            double maxAt = Double.NaN;
            int crossings = 0;
            double prevDelta = Double.NaN;
            for (double[] row : this.rows) {
                double delta = row[2] - row[1];
                sumSq += delta * delta;
                sum += delta;
                if (Math.abs(delta) > maxAbs) {
                    maxAbs = Math.abs(delta);
                    maxAt = row[0];
                }
                if (!Double.isNaN(prevDelta) && Math.signum(delta) != 0.0
                        && Math.signum(prevDelta) != 0.0
                        && Math.signum(delta) != Math.signum(prevDelta)) {
                    crossings++;
                }
                prevDelta = delta;
            }
            this.rmsDeltaEv = this.rows.isEmpty() ? Double.NaN
                    : Math.sqrt(sumSq / this.rows.size());
            this.meanSignedDeltaEv = this.rows.isEmpty() ? Double.NaN
                    : sum / this.rows.size();
            this.maxAbsDeltaEv = maxAbs;
            this.maxAbsDeltaAtParameter = maxAt;
            this.firstDeltaEv = this.rows.isEmpty() ? Double.NaN : this.rows.get(0)[2]
                    - this.rows.get(0)[1];
            this.lastDeltaEv = this.rows.isEmpty() ? Double.NaN
                    : this.rows.get(this.rows.size() - 1)[2]
                            - this.rows.get(this.rows.size() - 1)[1];
            this.signCrossings = crossings;
        }

        public int getRowCount() { return this.rowCount; }
        public int getRejectedRows() { return this.rejectedRows; }
        /** Rows whose parameter is smaller than the previous row's (order note only). */
        public int getOutOfOrderRows() { return this.outOfOrderRows; }
        public String getParameterLabel() { return this.parameterLabel; }
        public String getFirstSeriesLabel() { return this.firstSeriesLabel; }
        public String getSecondSeriesLabel() { return this.secondSeriesLabel; }
        /** sqrt(mean((E2 - E1)^2)) over all valid rows. */
        public double getRmsDeltaEv() { return this.rmsDeltaEv; }
        public double getMeanSignedDeltaEv() { return this.meanSignedDeltaEv; }
        public double getMaxAbsDeltaEv() { return this.maxAbsDeltaEv; }
        public double getMaxAbsDeltaAtParameter() { return this.maxAbsDeltaAtParameter; }
        public double getFirstDeltaEv() { return this.firstDeltaEv; }
        public double getLastDeltaEv() { return this.lastDeltaEv; }
        /** Strict sign flips of (E2 - E1) along the series. */
        public int getSignCrossings() { return this.signCrossings; }
        /** Valid rows as (parameter, e1, e2). */
        public List<double[]> getRows() { return this.rows; }
    }

    private static final class Builder {
        private final List<double[]> rows = new ArrayList<>();
        private int rejectedRows = 0;
        private int outOfOrderRows = 0;
        private String parameterLabel = "(row order index)";
        private String firstSeriesLabel = "series_1";
        private String secondSeriesLabel = "series_2";
    }

    private EnergySeriesComparer() {
        // Utility
    }

    /**
     * Compares two same-grid energy columns. Needs at least two valid rows;
     * numeric rows below the first three columns are ignored with a warning
     * note upstream, non-numeric required cells are counted as rejected.
     */
    public static OperationResult<SeriesComparison> compare(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("SERIES_IO",
                    "Could not read " + file.getFileName() + " as UTF-8: " + ex.getMessage(),
                    ex instanceof IOException ? (IOException) ex : null);
        }
        Builder builder = new Builder();
        boolean headerSeen = false;
        boolean firstLineConsumed = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] cells = trimmed.split("[,\\s]+");
            if (cells.length < 3) {
                builder.rejectedRows++;
                continue;
            }
            double parameter;
            double e1;
            double e2;
            try {
                parameter = Double.parseDouble(normalize(cells[0]));
                e1 = Double.parseDouble(normalize(cells[1]));
                e2 = Double.parseDouble(normalize(cells[2]));
            } catch (NumberFormatException ex) {
                if (!headerSeen && !firstLineConsumed) {
                    headerSeen = true;
                    firstLineConsumed = true;
                    builder.parameterLabel = cells[0];
                    builder.firstSeriesLabel = cells[1];
                    builder.secondSeriesLabel = cells[2];
                    continue;
                }
                builder.rejectedRows++;
                continue;
            }
            headerSeen = true;
            firstLineConsumed = true;
            if (!Double.isFinite(parameter) || !Double.isFinite(e1)
                    || !Double.isFinite(e2)) {
                builder.rejectedRows++;
                continue;
            }
            if (!builder.rows.isEmpty()
                    && parameter < builder.rows.get(builder.rows.size() - 1)[0]) {
                builder.outOfOrderRows++;
            }
            if (builder.rows.size() >= MAX_ROWS_PER_COMPARISON) {
                builder.rejectedRows++;
                continue;
            }
            builder.rows.add(new double[] {parameter, e1, e2});
        }
        if (builder.rows.size() < 2) {
            return OperationResult.failed("SERIES_TOO_SHORT",
                    "Only " + builder.rows.size() + " comparable (parameter, E1, E2) rows "
                            + "were parsed (rejected: " + builder.rejectedRows
                            + "); two rows are the minimum for a comparison.", null);
        }
        SeriesComparison comparison = new SeriesComparison(builder);
        return OperationResult.success("SERIES_OK",
                "Compared " + comparison.getRowCount() + " row(s); RMS delta "
                        + comparison.getRmsDeltaEv() + ".", comparison);
    }

    private static String normalize(String token) {
        return token.replace('D', 'E').replace('d', 'e');
    }
}
