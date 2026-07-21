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
 * Reads the plot-data files thermo_pw writes next to its PostScript figures
 * (Roadmap: thermo_pw doc integration). The grammar is pinned against the
 * upstream reference outputs of examples 04/05/09 in
 * {@code dalcorso/thermo_pw} at commit b73edd6d75b92df80f3a322279c6b12b301b9947
 * (same rows this class's tests pin), and the workflow layout against the
 * project's own fetched tutorial/user-guide pages:
 *
 * <ul>
 *   <li>{@code energy_files/output_ev.dat} - header-less two-column
 *       volume/energy rows; the volume unit is cross-pinned from the run's own
 *       stdout print ("unit-cell volume ... (a.u.)^3") and the sibling fit
 *       file's header, never guessed;</li>
 *   <li>{@code energy_files/output_ev.dat_mur} - the EOS-fit curve whose one
 *       {@code #} header names omega (a.u.)**3 / energy (Ry) /
 *       enthalpy(p) (Ry) / pressure (kbar);</li>
 *   <li>{@code therm_files/output_therm.dat.gN} - per-geometry harmonic
 *       thermodynamics; the {@code #} block carries the zero-point-energy
 *       note and the units: energy and free energy in Ry/cell, entropy and
 *       heat capacity Cv in Ry/cell/K. The {@code .gN_ph} variants share the
 *       grammar and are flagged as ph variants by the scanner;</li>
 *   <li>{@code anhar_files/output_anhar.dat[.therm|.bulk_mod|.heat|.gamma]} -
 *       the mur_lc_t quasi-harmonic series (volume thermal expansion beta in
 *       "10^(-6) K^(-1)", isothermal/isoentropic bulk moduli in kbar,
 *       electronic and C_P-C_V heat terms in Ry/cell/K, average Grüneisen
 *       gamma dimensionless) and the QHA E/F/S/Cv table in the same grammar
 *       as the harmonic files;</li>
 *   <li>{@code anhar_files/output_anhar.dat[(_ph)|.therm_ph|.bulk_mod_ph|
 *       .heat_ph|.gamma_ph|.dbulk_mod_ph]} - the ph-route counterparts of
 *       the anhar families, which share the base files' grammar verbatim
 *       (ground truth: the upstream example09 reference rows), plus
 *       {@code .dbulk_mod[(_ph)]} (the 2-column T / dB/dp(T) table whose
 *       header carries no unit - the derivative ratio is dimensionless by
 *       header silence, stated not assumed), {@code .aux_grun} (the
 *       Grüneisen-route auxiliaries: beta(T)x10^6, (C_p - C_v)(T),
 *       (B_S - B_T)(T) in kbar) and {@code .gamma_grun} (the Grüneisen-route
 *       average gamma, same 4-column grammar as .gamma);</li>
 *   <li>{@code anhar_files/output_pgrun.dat[.I[.J]]} and
 *       {@code output_pgrun.dat_freq.[I[.J]]} - the 2-column header-less
 *       plot rows (flgrun/flpgrun family of user-guide §3.19): the mode
 *       Grüneisen parameter gamma (dimensionless per the upstream plot
 *       script's own ylabel 'Mode-Grüneisen parameters gamma_nu(q)') and
 *       the mode frequency in cm^{-1} (pinned verbatim from the upstream
 *       dispersion script ylabel 'Frequency (cm^{-1})'). The k-path x
 *       coordinate carries NO unit by design - the script's own k-axis
 *       label line is commented out, so none is invented here either. The
 *       dotted [.I[.J]] index tail is preserved verbatim by the scanner;
 *       whether an index is a band or a path segment is deliberately NOT
 *       asserted (the upstream example09 reference set contains a
 *       '.7.1' index next to a 6-band &plot declaration - asserting the
 *       semantics would be guessing).</li>
 * </ul>
 *
 * <p>Deliberate grammar boundary: {@code output_grun.dat} /
 * {@code output_grun.dat_freq} (the {@code &plot nbnd=.., nks=.. /}
 * k/band row-matrix written by flpgrun) are ENUMERATED by the scanner but
 * never parsed by this class - their content is plotted by thermo_pw's own
 * scripts from the pgrun per-segment siblings, which this parser reads in
 * full, so no physics is hidden, only the duplicate layout is refused.</p>
 *
 * <p>Units are taken VERBATIM from each file's own header (or the two
 * cross-pinned sources for the header-less ev table); nothing is converted or
 * re-derived. Files are read fully but bounded ({@link #MAX_FILE_BYTES},
 * {@link #MAX_ROWS}); a file whose expected header signature is absent is
 * refused as THERMOPW_HEADER (wrong selection, never guessed). Live doctrine
 * for running calculations: thermo_pw (like gnuplot) appends rows, so ONLY
 * the final line may legitimately be a partial write - a short/unparseable
 * LAST line is dropped and counted in {@link Series#getPartialTailRows()},
 * while the same defect anywhere earlier makes the whole file THERMOPW_CORRUPT
 * with the offending line number, because silently dropping measured rows
 * would be worse than refusing.</p>
 */
public final class QEThermoPwSeriesParser {

    /** Upper bound on one series file (reference outputs are kilobytes). */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /** Upper bound on parsed comment lines per file. */
    public static final int MAX_COMMENT_LINES = 200;
    /** Upper bound on data rows kept per file. */
    public static final int MAX_ROWS = 400_000;

    private QEThermoPwSeriesParser() {
        // Utility
    }

    /** The supported thermo_pw plot-data families. */
    public enum SeriesKind {
        /** energy_files/output_ev.dat: volume (a.u.)^3, energy (Ry). */
        EV_CURVE("E(V) points", 2, null, null),
        /** energy_files/output_ev.dat_mur: EOS-fit curve incl. pressure (kbar). */
        MUR_FIT("EOS fit curve (Murnaghan/Birch-Murnaghan)", 4,
                "omega (a.u.)**3", "pressure (kbar)"),
        /** therm_files/output_therm.dat.gN: harmonic E/F/S/Cv vs T. */
        THERMO_HARMONIC("harmonic thermodynamics vs T", 5,
                "Temperature T in K", null),
        /** anhar_files/output_anhar.dat.therm: QHA E/F/S/Cv vs T. */
        ANHARM_THERM("quasi-harmonic thermodynamics vs T", 5,
                "Temperature T in K", null),
        /** anhar_files/output_anhar.dat: T, V(T), F(T), beta. */
        ANHARM_MAIN("quasi-harmonic volume and thermal expansion vs T", 4,
                "beta is the volume thermal expansion", null),
        /** anhar_files/output_anhar.dat.bulk_mod: B_T, B_S, difference (kbar). */
        ANHARM_BULK("bulk moduli vs T", 4, "B_T(T)", null),
        /** anhar_files/output_anhar.dat.heat: electronic + C_P-C_V terms. */
        ANHARM_HEAT("heat-capacity terms vs T", 4, "C_e(T)", null),
        /** anhar_files/output_anhar.dat.gamma: average Grüneisen + helpers. */
        ANHARM_GAMMA("average Grüneisen parameter vs T", 4, "gamma(T)", null),
        /** anhar_files/output_anhar.dat.dbulk_mod[(_ph)]: T, dB/dp(T). */
        ANHARM_DBULK("pressure derivative of the bulk modulus vs T", 2,
                "dB/dp (T)", null),
        /** anhar_files/output_anhar.dat.aux_grun: Grüneisen-route auxiliaries. */
        ANHARM_AUX_GRUN("thermal-expansion auxiliaries (Grüneisen route) vs T", 4,
                "gamma is the average gruneisen parameter", "(B_S - B_T)"),
        /** anhar_files/output_anhar.dat.gamma_grun: gamma from the Grüneisen route. */
        ANHARM_GAMMA_GRUN("average Grüneisen parameter vs T (Grüneisen route)", 4,
                "gamma(T)", "beta B_T (kbar/K)"),
        /** anhar_files/output_pgrun.dat[.I[.J]]: header-less gamma plot rows. */
        PGRUN_GAMMA("mode Grüneisen parameter along the k-path (plot rows)", 2,
                null, null),
        /** anhar_files/output_pgrun.dat_freq.[I[.J]]: header-less frequency rows. */
        PGRUN_FREQ("mode frequency along the k-path (plot rows)", 2, null, null);

        private final String label;
        private final int columnCount;
        private final String headerNeedleFirst;
        private final String headerNeedleSecond;

        SeriesKind(String label, int columnCount, String headerNeedleFirst,
                   String headerNeedleSecond) {
            this.label = label;
            this.columnCount = columnCount;
            this.headerNeedleFirst = headerNeedleFirst;
            this.headerNeedleSecond = headerNeedleSecond;
        }

        public String getLabel() { return this.label; }
        public int getColumnCount() { return this.columnCount; }
    }

    /** One named column with its verbatim unit hint. */
    public static final class Column {
        private final String name;
        private final String unit;

        private Column(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        public String getName() { return this.name; }
        /** Verbatim unit text; empty means the quantity is dimensionless. */
        public String getUnit() { return this.unit; }
        /** Display label: name plus unit when one exists. */
        public String getLabel() {
            return this.unit.isEmpty() ? this.name : this.name + " (" + this.unit + ")";
        }
    }

    /** One parsed series: columns (with verbatim units), rows, provenance. */
    public static final class Series {
        private final SeriesKind kind;
        private final String sourceName;
        private final List<Column> columns;
        private final List<double[]> rows;
        private final List<String> commentLines;
        private final int partialTailRows;
        private final String unitProvenance;

        private Series(SeriesKind kind, String sourceName, List<Column> columns,
                       List<double[]> rows, List<String> commentLines, int partialTailRows,
                       String unitProvenance) {
            this.kind = kind;
            this.sourceName = sourceName;
            this.columns = columns;
            this.rows = rows;
            this.commentLines = commentLines;
            this.partialTailRows = partialTailRows;
            this.unitProvenance = unitProvenance;
        }

        public SeriesKind getKind() { return this.kind; }
        public String getSourceName() { return this.sourceName; }
        public List<Column> getColumns() { return this.columns; }
        public List<double[]> getRows() { return this.rows; }
        public int getRowCount() { return this.rows.size(); }
        public List<String> getCommentLines() { return this.commentLines; }
        /** Trailing partial-write rows dropped while reading a live file. */
        public int getPartialTailRows() { return this.partialTailRows; }
        /** Where the unit labels came from (header verbatim or the ev cross-pin). */
        public String getUnitProvenance() { return this.unitProvenance; }
        public double getX(int row) { return this.rows.get(row)[0]; }
        public double getY(int row, int column) { return this.rows.get(row)[column]; }

        public String getXLabel() { return this.columns.get(0).getLabel(); }
        public String getYLabel(int column) { return this.columns.get(column).getLabel(); }
    }

    /** Candidate kinds for a file NAME (empty when unsupported). */
    public static List<SeriesKind> candidateKinds(String fileName) {
        List<SeriesKind> kinds = new ArrayList<>();
        if (fileName == null) {
            return kinds;
        }
        switch (fileName) {
            case "output_ev.dat" -> kinds.add(SeriesKind.EV_CURVE);
            case "output_ev.dat_mur" -> kinds.add(SeriesKind.MUR_FIT);
            case "output_anhar.dat" -> kinds.add(SeriesKind.ANHARM_MAIN);
            case "output_anhar.dat_ph" -> kinds.add(SeriesKind.ANHARM_MAIN);
            case "output_anhar.dat.therm", "output_anhar.dat.therm_ph" ->
                    kinds.add(SeriesKind.ANHARM_THERM);
            case "output_anhar.dat.bulk_mod", "output_anhar.dat.bulk_mod_ph" ->
                    kinds.add(SeriesKind.ANHARM_BULK);
            case "output_anhar.dat.heat", "output_anhar.dat.heat_ph" ->
                    kinds.add(SeriesKind.ANHARM_HEAT);
            case "output_anhar.dat.gamma", "output_anhar.dat.gamma_ph" ->
                    kinds.add(SeriesKind.ANHARM_GAMMA);
            case "output_anhar.dat.dbulk_mod", "output_anhar.dat.dbulk_mod_ph" ->
                    kinds.add(SeriesKind.ANHARM_DBULK);
            case "output_anhar.dat.aux_grun" -> kinds.add(SeriesKind.ANHARM_AUX_GRUN);
            case "output_anhar.dat.gamma_grun" -> kinds.add(SeriesKind.ANHARM_GAMMA_GRUN);
            default -> {
                if (fileName.matches("output_therm\\.dat\\.g[0-9]+(_ph)?")) {
                    kinds.add(SeriesKind.THERMO_HARMONIC);
                } else if (fileName.matches("output_pgrun\\.dat\\.[0-9]+(\\.[0-9]+)?")) {
                    kinds.add(SeriesKind.PGRUN_GAMMA);
                } else if (fileName.matches("output_pgrun\\.dat_freq\\.[0-9]+(\\.[0-9]+)?")) {
                    kinds.add(SeriesKind.PGRUN_FREQ);
                }
                // output_grun.dat / output_grun.dat_freq stay unmapped on purpose:
                // the &plot k/band row-matrix is enumerated, never parsed - the
                // pgrun per-segment siblings carry the plotted physics.
            }
        }
        return kinds;
    }

    /**
     * Parses one series file with a caller-chosen kind. The verdicts are
     * typed: THERMOPW_INPUT (null/unreadable/oversized), THERMOPW_HEADER
     * (the kind's signature comment is missing), THERMOPW_CORRUPT (a data row
     * before the last is short, unparseable, or non-finite, with the line
     * number), THERMOPW_EMPTY (no usable rows), THERMOPW_OK.
     */
    public static OperationResult<Series> parse(Path file, SeriesKind kind) {
        if (file == null || kind == null) {
            return OperationResult.failed("THERMOPW_INPUT",
                    "A series file and its series kind are both required.", null);
        }
        if (!Files.isRegularFile(file)) {
            return OperationResult.failed("THERMOPW_INPUT",
                    "Not a regular file: " + file, null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("THERMOPW_INPUT",
                    "Size unreadable for " + file + ": " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("THERMOPW_INPUT",
                    file.getFileName() + " exceeds the " + MAX_FILE_BYTES
                            + "-byte series bound; refusing an unbounded read.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("THERMOPW_INPUT",
                    "Could not read " + file + " as UTF-8 text: " + ex.getMessage(), null);
        }

        List<String> comments = new ArrayList<>();
        List<double[]> rows = new ArrayList<>();
        int expected = kind.getColumnCount();
        String fileName = file.getFileName().toString();
        int dataSeen = 0;
        int partialTail = 0;
        boolean headerChecked = false;

        StringBuilder headerBlock = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (comments.size() < MAX_COMMENT_LINES) {
                    comments.add(trimmed);
                }
                headerBlock.append(trimmed).append('\n');
                continue;
            }
            if (!headerChecked) {
                headerChecked = true;
                OperationResult<Series> headerRefusal = checkHeader(kind, fileName, headerBlock);
                if (headerRefusal != null) {
                    return headerRefusal;
                }
            }
            String[] tokens = trimmed.split("\\s+");
            boolean lastLine = i == lines.size() - 1;
            if (tokens.length != expected || firstTokenNotFinite(tokens[0])) {
                if (lastLine && dataSeen > 0) {
                    partialTail++;
                    continue; // running write in progress; the row completes later
                }
                return OperationResult.failed("THERMOPW_CORRUPT",
                        fileName + " line " + (i + 1) + ": expected " + expected
                                + " columns of numbers, found " + tokens.length
                                + " ('" + abbreviate(trimmed) + "'). A row before the last is"
                                + " never silently dropped - the file is refused instead.", null);
            }
            double[] row = new double[expected];
            boolean bad = false;
            for (int c = 0; c < expected; c++) {
                row[c] = parseFortranDouble(tokens[c]);
                if (Double.isNaN(row[c])) {
                    bad = true;
                    break;
                }
            }
            if (bad) {
                if (lastLine && dataSeen > 0) {
                    partialTail++;
                    continue;
                }
                return OperationResult.failed("THERMOPW_CORRUPT",
                        fileName + " line " + (i + 1)
                                + ": non-numeric or non-finite value in a data row ('"
                                + abbreviate(trimmed) + "').", null);
            }
            rows.add(row);
            dataSeen++;
            if (rows.size() > MAX_ROWS) {
                return OperationResult.failed("THERMOPW_INPUT",
                        fileName + " exceeds the " + MAX_ROWS + "-row series bound.", null);
            }
        }
        if (!headerChecked) {
            OperationResult<Series> headerRefusal = checkHeader(kind, fileName, headerBlock);
            if (headerRefusal != null) {
                return headerRefusal;
            }
        }
        if (rows.isEmpty()) {
            return OperationResult.failed("THERMOPW_EMPTY",
                    fileName + " contains no complete " + expected + "-column rows yet"
                            + (partialTail > 0 ? " (only a partial first row - the run has"
                                    + " not finished writing it)" : "")
                            + ".", null);
        }
        return OperationResult.success("THERMOPW_OK",
                rows.size() + " row(s)" + (partialTail > 0
                        ? "; " + partialTail + " trailing partial row(s) held back until the"
                                + " write completes" : ""),
                new Series(kind, fileName, columnsFor(kind), List.copyOf(rows),
                        List.copyOf(comments), partialTail, unitProvenance(kind)));
    }

    /** Header signature verdict; null means the signature matched. */
    private static OperationResult<Series> checkHeader(SeriesKind kind, String fileName,
                                                       CharSequence headerBlock) {
        String needle1 = kind.headerNeedleFirst;
        String needle2 = kind.headerNeedleSecond;
        if (needle1 == null) {
            return null; // header-less kind (the ev table); nothing to verify
        }
        String block = headerBlock.toString();
        if (block.contains(needle1) && (needle2 == null || block.contains(needle2))) {
            return null;
        }
        return OperationResult.failed("THERMOPW_HEADER",
                fileName + " does not carry the expected thermo_pw header ('" + needle1
                        + (needle2 == null ? "" : "' + '" + needle2) + "'). Wrong file "
                        + "selection or a format this build has not pinned - never guessed.",
                null);
    }

    private static boolean firstTokenNotFinite(String token) {
        return Double.isNaN(parseFortranDouble(token));
    }

    /** Fortran D/d exponents tolerated; NaN marks an unparseable token. */
    static double parseFortranDouble(String token) {
        try {
            return Double.parseDouble(token.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }

    private static String unitProvenance(SeriesKind kind) {
        return switch (kind) {
            case EV_CURVE -> "header-less table: units cross-pinned from the run stdout"
                    + " 'unit-cell volume (a.u.)^3' print and the output_ev.dat_mur header"
                    + " (upstream example05 reference rows)";
            case MUR_FIT -> "single '#' header line, verbatim";
            case THERMO_HARMONIC, ANHARM_THERM ->
                    "'#' unit block: 'Energy and free energy in Ry/cell',"
                    + " 'Entropy ... in Ry/cell/K', 'Heat capacity Cv in Ry/cell/K'";
            case ANHARM_MAIN -> "'#' header: 'T (K)  V(T) (a.u.)^3  F (T) (Ry)"
                    + "  beta (10^(-6) K^(-1))'";
            case ANHARM_BULK -> "'#' header: 'B_T(T) (kbar)  B_S(T) (kbar)"
                    + "  B_S(T)-B_T(T) (kbar)'";
            case ANHARM_HEAT -> "'#' header: 'C_e(T) (Ry/cell/K)"
                    + "  (C_P-C_V)(T) (Ry/cell/K)  C_e+C_P-C_V(T) (Ry/cell/K)'";
            case ANHARM_GAMMA -> "'#' header: 'gamma(T)  C_V(T) (Ry/cell/K)"
                    + "  beta B_T (kbar/K)'";
            case ANHARM_DBULK -> "'#' header: 'T (K)  dB/dp (T)' - the header carries"
                    + " no unit for dB/dp (a pressure-derivative ratio); none is invented";
            case ANHARM_AUX_GRUN -> "'#' header: 'T (K)  beta(T)x10^6  (C_p - C_v)(T)"
                    + "  (B_S - B_T) (T) (kbar)' verbatim - only (kbar) is header-stated;"
                    + " the blank units elsewhere are header silence, stated not guessed"
                    + " (the x10^6 on beta is part of the column name verbatim)";
            case ANHARM_GAMMA_GRUN -> "'#' header: 'gamma(T)  C_V(T) (Ry/cell/K)"
                    + "  beta B_T (kbar/K)' (Grüneisen-route counterpart of .gamma)";
            case PGRUN_GAMMA -> "header-less 2-column plot rows; gamma is dimensionless"
                    + " per the upstream plot script's own ylabel 'Mode-Grüneisen"
                    + " parameters gamma_nu(q)' (gnuplot_files/gnuplot.tmp_grun,"
                    + " commit b73edd6d); the k-axis carries no unit by design - the"
                    + " script's k-label line is commented out";
            case PGRUN_FREQ -> "header-less 2-column plot rows; the frequency unit"
                    + " 'cm^{-1}' is pinned verbatim from the upstream dispersion plot"
                    + " script ylabel 'Frequency (cm^{-1})'"
                    + " (gnuplot_files/gnuplot.tmp.g1_disp, commit b73edd6d)";
        };
    }

    private static List<Column> columnsFor(SeriesKind kind) {
        List<Column> columns = new ArrayList<>();
        switch (kind) {
            case EV_CURVE -> {
                columns.add(new Column("V", "(a.u.)^3"));
                columns.add(new Column("E", "Ry"));
            }
            case MUR_FIT -> {
                columns.add(new Column("omega", "(a.u.)**3"));
                columns.add(new Column("energy", "Ry"));
                columns.add(new Column("enthalpy(p)", "Ry"));
                columns.add(new Column("pressure", "kbar"));
            }
            case THERMO_HARMONIC, ANHARM_THERM -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("E", "Ry/cell"));
                columns.add(new Column("F", "Ry/cell"));
                columns.add(new Column("S", "Ry/cell/K"));
                columns.add(new Column("Cv", "Ry/cell/K"));
            }
            case ANHARM_MAIN -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("V(T)", "(a.u.)^3"));
                columns.add(new Column("F(T)", "Ry"));
                columns.add(new Column("beta", "10^(-6) K^(-1)"));
            }
            case ANHARM_BULK -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("B_T(T)", "kbar"));
                columns.add(new Column("B_S(T)", "kbar"));
                columns.add(new Column("B_S(T)-B_T(T)", "kbar"));
            }
            case ANHARM_HEAT -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("C_e(T)", "Ry/cell/K"));
                columns.add(new Column("(C_P-C_V)(T)", "Ry/cell/K"));
                columns.add(new Column("C_e+C_P-C_V(T)", "Ry/cell/K"));
            }
            case ANHARM_GAMMA, ANHARM_GAMMA_GRUN -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("gamma(T)", ""));
                columns.add(new Column("C_V(T)", "Ry/cell/K"));
                columns.add(new Column("beta B_T", "kbar/K"));
            }
            case ANHARM_DBULK -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("dB/dp(T)", ""));
            }
            case ANHARM_AUX_GRUN -> {
                columns.add(new Column("T", "K"));
                columns.add(new Column("beta(T)x10^6", ""));
                columns.add(new Column("(C_p - C_v)(T)", ""));
                columns.add(new Column("(B_S - B_T)(T)", "kbar"));
            }
            case PGRUN_GAMMA -> {
                columns.add(new Column("k-path coordinate", ""));
                columns.add(new Column("mode Grüneisen gamma", ""));
            }
            case PGRUN_FREQ -> {
                columns.add(new Column("k-path coordinate", ""));
                columns.add(new Column("mode frequency", "cm^{-1}"));
            }
            default -> throw new IllegalStateException("unmapped kind " + kind);
        }
        if (columns.size() != kind.getColumnCount()) {
            throw new IllegalStateException("column map drift for " + kind);
        }
        return List.copyOf(columns);
    }

    /** Formats one data value for status lines (no rounding beyond %.6g). */
    public static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.6g", value);
    }
}
