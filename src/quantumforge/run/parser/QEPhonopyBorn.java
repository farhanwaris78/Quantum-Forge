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
import java.util.Optional;

import quantumforge.operation.OperationResult;

/**
 * Reader and writer for phonopy's {@code BORN} file - the artifact that
 * switches on the non-analytical term correction (NAC) for LO-TO splitting.
 * The grammar is pinned line-by-line against the upstream reader/writer
 * {@code get_born_parameters / get_BORN_lines} and the
 * {@code phonopy-qe-born} command's own print block
 * (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <pre>
 * [factor [G_cutoff [Lambda]]]   &lt;- line 1, OPTIONAL; a non-float first
 *                                     token (e.g. the '# epsilon and Z* ...'
 *                                     comment or the doc's 'default value'
 *                                     line) means: use the calculator
 *                                     default (upstream float() fails the
 *                                     same way and leaves factor=None)
 * e11 e12 e13 e21 e22 e23 e31 e32 e33   &lt;- line 2, EXACTLY 9 floats:
 *                                     dielectric constant, row-major
 * z11 ... z33                      &lt;- one line per SYMMETRY-INDEPENDENT
 * ...                                   atom of the primitive cell,
 *                                     EXACTLY 9 floats each
 * </pre>
 *
 * <p>Interpretation boundary, stated not hidden: how many charge lines
 * phonopy EXPECTS is decided by phonopy's own {@code Symmetry} construction
 * ({@code independent_atoms}); this reader cannot run symmetry, so it
 * reports the charge-line count VERBATIM and quotes upstream's own trailing
 * check ('Too many atoms in the BORN file (it should only contain
 * symmetry-independent atoms)') as the failure phonopy raises when the two
 * disagree. For the common all-atoms-independent case (e.g. the upstream
 * NaCl-QE example, example/NaCl-QE/BORN) the count equals the primitive
 * atom count.</p>
 *
 * <p>Live doctrine: a file being written is read only up to the last
 * COMPLETE line; a truncated trailing charge line (1-8 floats at EOF) is
 * held back and counted via {@link BornFile#getPartialLinesHeld()}, never
 * guessed. A malformed MID-file line is {@code PHONOPY_BORN_SHAPE}.</p>
 */
public final class QEPhonopyBorn {

    private QEPhonopyBorn() {
        // Utility
    }

    /** Fail-closed size bound (BORN files are tens of lines; this is generous). */
    public static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;

    /** Safety bound on charge lines (each is one atom; upstream NaCl has 2). */
    public static final int MAX_CHARGE_LINES = 100000;

    /**
     * Verbatim {@code %13.8f } grammar of the upstream writer
     * ({@code get_BORN_lines} / {@code phonopy_qe_born_script.run}).
     */
    private static final String ROW_FORMAT = "%13.8f ";

    /** A parsed BORN file. */
    public static final class BornFile {
        private final String sourceName;
        private final String firstLine;      // verbatim, may be comment/factor text
        private final Double factor;         // null when line 1 is not numeric
        private final Double gCutoff;        // null when absent
        private final Double lambda;         // null when absent
        private final double[] dielectric;   // 9, row-major
        private final List<double[]> charges; // each 9, row-major
        private final int partialLinesHeld;
        private final List<String> notes;

        BornFile(String sourceName, String firstLine, Double factor, Double gCutoff,
                Double lambda, double[] dielectric, List<double[]> charges,
                int partialLinesHeld, List<String> notes) {
            this.sourceName = sourceName;
            this.firstLine = firstLine;
            this.factor = factor;
            this.gCutoff = gCutoff;
            this.lambda = lambda;
            this.dielectric = dielectric;
            this.charges = charges;
            this.partialLinesHeld = partialLinesHeld;
            this.notes = notes;
        }

        public String getSourceName() { return this.sourceName; }

        /** Line 1 VERBATIM (comment / 'default value' / factor text). */
        public String getFirstLine() { return this.firstLine; }

        /** Conversion factor from line 1; empty when phonopy will use the default. */
        public Optional<Double> getFactor() { return Optional.ofNullable(this.factor); }

        /** Optional Grun... no - upstream's G_cutoff token from line 1, when present. */
        public Optional<Double> getGCutoff() { return Optional.ofNullable(this.gCutoff); }

        /** Optional Lambda token from line 1, when present. */
        public Optional<Double> getLambda() { return Optional.ofNullable(this.lambda); }

        /** Dielectric tensor, 9 values row-major, as printed. */
        public double[] getDielectric() { return this.dielectric.clone(); }

        /** Born effective charge tensors, one 9-value row-major array per line. */
        public List<double[]> getCharges() { return this.charges; }

        /** Charge-line count (upstream expectation: symmetry-independent atoms). */
        public int getChargeCount() { return this.charges.size(); }

        /** Trailing partial line(s) held back during a live read. */
        public int getPartialLinesHeld() { return this.partialLinesHeld; }

        public List<String> getNotes() { return this.notes; }
    }

    /** Reads a BORN file from disk (bounded). */
    public static OperationResult<BornFile> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_BORN_INPUT",
                    "No such BORN file: " + file, null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return OperationResult.failed("PHONOPY_BORN_INPUT",
                        "BORN file exceeds the " + MAX_FILE_BYTES
                                + "-byte safety bound (" + size + " bytes): refusing"
                                + " rather than reading an unbounded artifact.", null);
            }
            String text = Files.readString(file);
            return parseText(text, file.getFileName().toString());
        } catch (IOException e) {
            return OperationResult.failed("PHONOPY_BORN_INPUT",
                    "Could not read BORN file " + file + ": " + e.getMessage(), null);
        }
    }

    /**
     * Parses BORN text. Exact upstream mirror:
     * line 1 optional factor tokens, line 2 exactly 9 floats, then one
     * 9-float line per (symmetry-independent) charge; a trailing partial
     * line at EOF is held back (live), a malformed mid-file line is SHAPE.
     */
    public static OperationResult<BornFile> parseText(String text, String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_BORN_INPUT", "null text", null);
        }
        String[] raw = text.split("\n", -1);
        List<String> lines = new ArrayList<>(raw.length);
        for (String s : raw) {
            String t = s.strip();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        if (lines.isEmpty()) {
            return OperationResult.failed("PHONOPY_BORN_EMPTY",
                    sourceName + ": no content lines - a BORN file needs at least the"
                            + " factor/comment line and the 9-float dielectric line.",
                    null);
        }
        if (lines.size() < 2) {
            return OperationResult.failed("PHONOPY_BORN_HEADER",
                    sourceName + ": only one content line; upstream"
                            + " get_born_parameters requires line 2 to carry EXACTLY"
                            + " 9 floats (the dielectric constant, row-major) - its"
                            + " own message: 'BORN file format of line 2 is incorrect'.",
                    null);
        }

        // ---- line 1: optional factor / G_cutoff / Lambda (float-parse tolerant) ----
        String first = lines.get(0);
        String[] firstTokens = first.split("\\s+");
        Double factor = parseOrNull(firstTokens, 0);
        Double gCutoff = parseOrNull(firstTokens, 1);
        Double lambda = parseOrNull(firstTokens, 2);
        List<String> notes = new ArrayList<>();
        if (factor == null) {
            notes.add("line 1 ('" + first + "') is not a number: phonopy reads this"
                    + " as 'use the calculator default NAC factor' (upstream float()"
                    + " fails the same way and leaves factor=None) - the doc/qe.md"
                    + " example literally writes 'default value' here, and"
                    + " example/NaCl-QE/BORN carries exactly that line");
        } else {
            notes.add("line 1 factor = " + format(firstTokens[0])
                    + (gCutoff != null ? ", G_cutoff = " + format(firstTokens[1]) : "")
                    + (lambda != null ? ", Lambda = " + format(firstTokens[2]) : "")
                    + " (verbatim tokens; phonopy multiplies the NAC term by this"
                    + " factor)");
        }

        // ---- line 2: dielectric, EXACTLY 9 floats ----
        double[] dielectric = parseNine(lines.get(1));
        if (dielectric == null) {
            return OperationResult.failed("PHONOPY_BORN_SHAPE",
                    sourceName + ": line 2 must carry EXACTLY 9 floats (dielectric"
                            + " constant, row-major); upstream message: 'BORN file"
                            + " format of line 2 is incorrect'. Found: '"
                            + lines.get(1) + "'", null);
        }

        // ---- charge lines: 9 floats each; trailing partial held (live) ----
        List<double[]> charges = new ArrayList<>();
        int held = 0;
        int lineCount = lines.size();
        for (int i = 2; i < lineCount; i++) {
            double[] row = parseNine(lines.get(i));
            if (row == null) {
                boolean lastLine = (i == lineCount - 1);
                if (lastLine) {
                    held++; // live writer mid-line: hold back, keep the complete lines
                } else {
                    return OperationResult.failed("PHONOPY_BORN_SHAPE",
                            sourceName + ": content line " + (i + 1) + " must carry"
                                    + " EXACTLY 9 floats (one Born effective charge"
                                    + " tensor, row-major); upstream message shape:"
                                    + " 'BORN file format of line " + (i + 1)
                                    + " is incorrect'. Found: '" + lines.get(i) + "'",
                            null);
                }
            } else {
                charges.add(row);
                if (charges.size() > MAX_CHARGE_LINES) {
                    return OperationResult.failed("PHONOPY_BORN_SHAPE",
                            sourceName + ": more than " + MAX_CHARGE_LINES
                                    + " charge lines: beyond any physical atom count,"
                                    + " refusing.", null);
                }
            }
        }
        if (charges.isEmpty()) {
            return OperationResult.failed("PHONOPY_BORN_HEADER",
                    sourceName + ": dielectric present but ZERO complete Born"
                            + " effective charge lines - upstream expects one 9-float"
                            + " line per symmetry-independent atom of the primitive"
                            + " cell.", null);
        }
        notes.add(charges.size() + " Born effective charge tensor(s) verbatim;"
                + " phonopy checks this count against ITS symmetry construction"
                + " ('Too many atoms in the BORN file (it should only contain"
                + " symmetry-independent atoms)' is upstream's own mismatch"
                + " message) - QuantumForge reports the count, phonopy decides");
        if (held > 0) {
            notes.add(held + " trailing partial line(s) held back (live write in"
                    + " progress) - re-read completes the row");
        }
        return OperationResult.success("PHONOPY_BORN_OK",
                sourceName + ": dielectric + " + charges.size() + " charge tensor(s)"
                        + (factor != null ? ", factor " + format(firstTokens[0]) : "")
                        + (held > 0 ? "; " + held + " trailing partial held" : ""),
                new BornFile(sourceName, first, factor, gCutoff, lambda, dielectric,
                        charges, held, List.copyOf(notes)));
    }

    /**
     * Emits BORN text in the upstream writer's EXACT shape
     * ({@code phonopy-qe-born} print block / {@code get_BORN_lines}):
     * comment header naming the 1-based atom indices, then the dielectric,
     * then one row per charge, each row {@code %13.8f } x9 (trailing space).
     */
    public static String bornText(List<double[]> charges, double[] dielectric) {
        if (dielectric == null || dielectric.length != 9) {
            throw new IllegalArgumentException("dielectric must carry 9 values");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# epsilon and Z* of atoms");
        for (int i = 0; i < charges.size(); i++) {
            sb.append(' ').append(i + 1);
        }
        sb.append('\n');
        appendRow(sb, dielectric);
        for (double[] charge : charges) {
            if (charge == null || charge.length != 9) {
                throw new IllegalArgumentException("each charge must carry 9 values");
            }
            appendRow(sb, charge);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, double[] row) {
        for (double v : row) {
            sb.append(String.format(Locale.ROOT, ROW_FORMAT, v));
        }
        sb.append('\n');
    }

    private static Double parseOrNull(String[] tokens, int index) {
        if (index >= tokens.length) {
            return null;
        }
        try {
            return Double.valueOf(tokens[index]);
        } catch (NumberFormatException e) {
            return null; // mirrors upstream's try/except around float(line_arr[i])
        }
    }

    private static double[] parseNine(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 9) {
            return null;
        }
        double[] row = new double[9];
        try {
            for (int i = 0; i < 9; i++) {
                row[i] = Double.parseDouble(tokens[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return row;
    }

    private static String format(String token) {
        return token;
    }
}
