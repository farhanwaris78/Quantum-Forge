/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Reader for phonopy's plain-text DOS tables - {@code total_dos.dat},
 * {@code partial_dos.dat} and {@code projected_dos.dat}. The grammar is
 * pinned against the upstream writers {@code write_total_dos /
 * write_projected_dos} (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <pre>
 * # Sigma = 0.063253            (optional comment lines, verbatim)
 * # Tetrahedron method          (the other upstream comment)
 * %20.10f%20.10f    (total/partial: frequency + one DOS column)
 * %20.10f%20.10f... (projected: frequency + one column per primitive-cell
 *                    atom; with XYZ_PROJECTION, three columns per atom in
 *                    x,y,z order - pinned from the output-files doc)
 * </pre>
 *
 * <p>Facts kept verbatim: comment lines (the Sigma/Debye/projection notes),
 * every frequency and DOS value, a UNIFORM column count enforced across all
 * rows. Units: the file carries NO unit text; the frequency column is THz
 * under phonopy's default setting and the DOS columns follow the same
 * frequency unit (phonopy doc: SIGMA's unit 'is same as that used for phonon
 * frequency') - {@link DosTable#getUnitNote()} says exactly that. The start
 * of the frequency axis may be negative: that is where phonopy places
 * imaginary modes (its own convention, e.g. the documented NaCl table starts
 * at -0.6695...), named in the note, never clamped.</p>
 *
 * <p>Live doctrine: ONLY the last line may be a partial append - it is held
 * back and counted ({@link DosTable#getPartialTailRows()}); a column-count
 * change or a non-number anywhere else is PHONOPY_DOS_SHAPE. Verdicts:
 * PHONOPY_DOS_INPUT, PHONOPY_DOS_EMPTY, PHONOPY_DOS_SHAPE,
 * PHONOPY_DOS_PARTIAL (no complete row yet), PHONOPY_DOS_OK. Bounded read:
 * {@link #MAX_FILE_BYTES}.</p>
 */
public final class QEPhonopyDos {

    /** Upper bound for one DOS table read. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    private QEPhonopyDos() {
        // Utility
    }

    /** A parsed DOS table. */
    public static final class DosTable {
        private final String sourceName;
        private final List<String> comments;
        private final int columnCount;
        private final double[] frequencies;
        private final double[][] series;
        private final double minFrequency;
        private final double maxFrequency;
        private final int negativeFrequencyRows;
        private final int partialTailRows;
        private final String unitNote;

        private DosTable(String sourceName, List<String> comments, int columnCount,
                         double[] frequencies, double[][] series,
                         double minFrequency, double maxFrequency,
                         int negativeFrequencyRows, int partialTailRows) {
            this.sourceName = sourceName;
            this.comments = comments;
            this.columnCount = columnCount;
            this.frequencies = frequencies;
            this.series = series;
            this.minFrequency = minFrequency;
            this.maxFrequency = maxFrequency;
            this.negativeFrequencyRows = negativeFrequencyRows;
            this.partialTailRows = partialTailRows;
            this.unitNote = "the .dat table carries no unit text: frequencies are THz"
                    + " under phonopy's default setting and DOS columns follow the"
                    + " same frequency unit (phonopy doc re SIGMA: its unit 'is same"
                    + " as that used for phonon frequency') - stated; a non-default"
                    + " conversion is NOT detectable here";
        }

        public String getSourceName() { return this.sourceName; }
        /** Comment lines verbatim (Sigma / Tetrahedron / projection notes). */
        public List<String> getComments() { return this.comments; }
        /** Total numeric columns incl. the leading frequency column. */
        public int getColumnCount() { return this.columnCount; }
        /** DOS series count (columnCount - 1). */
        public int getSeriesCount() { return this.columnCount - 1; }
        public double[] getFrequencies() { return this.frequencies.clone(); }
        /** Series values by [series][row]. */
        public double[][] getSeries() {
            double[][] out = new double[this.series.length][];
            for (int i = 0; i < out.length; i++) {
                out[i] = this.series[i].clone();
            }
            return out;
        }
        public double getMinFrequency() { return this.minFrequency; }
        public double getMaxFrequency() { return this.maxFrequency; }
        /** Rows below zero frequency - phonopy's imaginary-mode region. */
        public int getNegativeFrequencyRows() { return this.negativeFrequencyRows; }
        /** Trailing partial lines held back (a live append). */
        public int getPartialTailRows() { return this.partialTailRows; }
        /** The stated, never-guessed unit note. */
        public String getUnitNote() { return this.unitNote; }

        /** Peak DOS value and the frequency where it sits (chart helper). */
        public double[] peakSummary() {
            double best = Double.NEGATIVE_INFINITY;
            double bestFreq = Double.NaN;
            for (int r = 0; r < this.frequencies.length; r++) {
                for (double[] s : this.series) {
                    if (s[r] > best) {
                        best = s[r];
                        bestFreq = this.frequencies[r];
                    }
                }
            }
            return new double[] {best, bestFreq};
        }
    }

    /** Bounded-file entry point. */
    public static OperationResult<DosTable> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_DOS_INPUT",
                    "Not a regular file: " + file, null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("PHONOPY_DOS_INPUT",
                    "Size unreadable for " + file + ": " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("PHONOPY_DOS_INPUT",
                    file.getFileName() + " exceeds the " + MAX_FILE_BYTES
                            + "-byte DOS-table bound; refusing an unbounded read.",
                    null);
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("PHONOPY_DOS_INPUT",
                    "Could not read " + file + " as UTF-8 text: " + ex.getMessage(),
                    null);
        }
        return parseText(text, file.getFileName().toString());
    }

    /** Text entry point (tests, live-read content). */
    public static OperationResult<DosTable> parseText(String text, String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_DOS_INPUT",
                    "No DOS-table content supplied.", null);
        }
        if (text.length() > MAX_FILE_BYTES) {
            return OperationResult.failed("PHONOPY_DOS_INPUT",
                    "DOS-table text exceeds the " + MAX_FILE_BYTES + "-char bound.",
                    null);
        }
        String[] lines = text.split("\n", -1);
        List<String> comments = new ArrayList<>();
        List<double[]> rows = new ArrayList<>();
        int columnCount = -1;
        int partialTail = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (rows.isEmpty()) {
                    comments.add(trimmed);
                    continue;
                }
                return OperationResult.failed("PHONOPY_DOS_SHAPE",
                        sourceName + " line " + (i + 1) + ": a '#'-comment AFTER the"
                                + " numeric block started - upstream writes comments"
                                + " only at the top; a mid-table comment is a corrupt"
                                + " table, never skipped.", null);
            }
            String[] tokens = trimmed.split("\\s+");
            double[] row = new double[tokens.length];
            boolean ok = true;
            for (int t = 0; t < tokens.length; t++) {
                row[t] = QEThermoPwSeriesParser.parseFortranDouble(tokens[t]);
                if (!Double.isFinite(row[t])) {
                    ok = false;
                    break;
                }
            }
            boolean lastLine = i == lines.length - 1
                    || (i == lines.length - 2 && lines[lines.length - 1].trim().isEmpty());
            if (!ok) {
                if (lastLine && rows.isEmpty()) {
                    return OperationResult.failed("PHONOPY_DOS_EMPTY",
                            sourceName + ": the only numeric-looking line is not"
                                    + " parseable (" + abbreviate(trimmed)
                                    + ") - nothing measured.", null);
                }
                if (lastLine) {
                    partialTail++;
                    break; // a live partial append: held back
                }
                return OperationResult.failed("PHONOPY_DOS_SHAPE",
                        sourceName + " line " + (i + 1) + " is not a row of finite"
                                + " numbers ('" + abbreviate(trimmed)
                                + "') - never skipped mid-file.", null);
            }
            if (columnCount < 0) {
                columnCount = row.length;
                if (columnCount < 2) {
                    return OperationResult.failed("PHONOPY_DOS_SHAPE",
                            sourceName + " line " + (i + 1) + ": a phonopy DOS row is"
                                    + " frequency + at least one DOS column; found one"
                                    + " column.", null);
                }
            } else if (row.length != columnCount) {
                if (lastLine) {
                    partialTail++;
                    break; // mid-write column fragment: held back
                }
                return OperationResult.failed("PHONOPY_DOS_SHAPE",
                        sourceName + " line " + (i + 1) + " has " + row.length
                                + " columns, expected " + columnCount + " like the"
                                + " first row - a column-count change is a corrupt"
                                + " table, not a projected-DOS guess.", null);
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return OperationResult.failed(
                    partialTail > 0 ? "PHONOPY_DOS_PARTIAL" : "PHONOPY_DOS_EMPTY",
                    sourceName + " holds " + comments.size() + " comment line(s) and"
                            + (partialTail > 0
                                    ? " one still-partial numeric line - a live run is"
                                            + " writing; waiting."
                                    : " no numeric rows at all - neither a DOS table"
                                            + " nor a recognisable refusal case."),
                    null);
        }
        double[] frequencies = new double[rows.size()];
        double[][] series = new double[columnCount - 1][rows.size()];
        double minF = Double.POSITIVE_INFINITY;
        double maxF = Double.NEGATIVE_INFINITY;
        int negative = 0;
        for (int r = 0; r < rows.size(); r++) {
            double[] row = rows.get(r);
            frequencies[r] = row[0];
            minF = Math.min(minF, row[0]);
            maxF = Math.max(maxF, row[0]);
            if (row[0] < 0.0) {
                negative++;
            }
            for (int c = 1; c < columnCount; c++) {
                series[c - 1][r] = row[c];
            }
        }
        DosTable table = new DosTable(sourceName, List.copyOf(comments), columnCount,
                frequencies, series, minF, maxF, negative, partialTail);
        return OperationResult.success("PHONOPY_DOS_OK",
                rows.size() + " rows x " + columnCount + " columns ("
                        + (columnCount - 1) + " DOS series), frequency ["
                        + String.format(Locale.ROOT, "%.6f .. %.6f", minF, maxF)
                        + "]" + (partialTail > 0
                                ? ", " + partialTail + " trailing partial line(s)"
                                        + " held back" : "") + ".",
                table);
    }

    private static String abbreviate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }

    /** Chart helper: stack-safe legend labels for the DOS series. */
    public static List<String> seriesLabels(DosTable table) {
        List<String> labels = new ArrayList<>();
        int n = table.getSeriesCount();
        if (n == 1) {
            labels.add("total DOS (or partial sum - the file name says which)");
        } else if (n % 3 == 0 && n > 3 && n != 4) {
            // XYZ_PROJECTION shape candidate: x,y,z per atom; STATED as an inference
            int atoms = n / 3;
            for (int a = 0; a < atoms; a++) {
                labels.add("atom " + (a + 1) + " x");
                labels.add("atom " + (a + 1) + " y");
                labels.add("atom " + (a + 1) + " z");
            }
        } else {
            for (int a = 0; a < n; a++) {
                labels.add("atom " + (a + 1) + " (projected column " + (a + 1) + ")");
            }
        }
        return labels;
    }
}
