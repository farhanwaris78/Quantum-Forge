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
 * Reader for a {@code q2r.x} force-constants output ({@code NaCl.fc} style)
 * - the QE-native route into phonopy force constants, marked "Experimental"
 * upstream. The grammar is pinned line-by-line against phonopy's own
 * experimental parser {@code phonopy/interface/qe.py} (class PH_Q2R,
 * methods _parse_parameters / _parse_born / _parse_fc,
 * github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e) plus phonopy's own REAL example
 * file {@code example/NaCl-QE-q2r/NaCl.fc} (decompressed from NaCl.fc.xz,
 * 18,489 lines = 21 header lines + 36 blocks x (1 header + 512 data),
 * 8x8x8 grid). Upstream's format provenance is its docstring's mailing list
 * references (pw_forum 2005-April/002408, 2008-September/010099,
 * 2009-August/013613, msg24388) - quoted, since the format itself has no
 * official QE documentation.
 *
 * <pre>
 * ntype natom ibrav [alat ...]        &lt;- line 1 (upstream reads [:3] only)
 * [3 cell lines]                      &lt;- only when ibrav == 0 (alat units)
 * ntype species/mass lines (verbatim)
 * natom atom lines (verbatim: idx, species idx, crystal position)
 * [ T                                 &lt;- optional NAC block flag line
 *   3 epsilon rows x 3 floats
 *   natom x (atom header line + 3 born rows x 3 floats) ]
 * dim1 dim2 dim3                      &lt;- the ldisp q grid == supercell dims
 * 3x3 x natom x natom blocks, in (k,ll,i,j) np.ndindex order
 * (k=xyz2 outer, ll=xzy1, i=p2, j=p1 inner):
 *   header line 'k ll i j' (4 ints, verbatim captured)
 *   ndim=prod(dim) data lines 'i1 i2 i3 value' with i1 fastest and the
 *   element at token[3] (upstream: float(line.split()[3]); Ry/au^2)
 * </pre>
 *
 * <p>Two verbatim doctrine facts ride in every report: (1) UNIT - the
 * parser's own docstring states "Physical unit of force constants in the
 * file is Ry/au^2" (stated, never converted); (2) NAC - when the epsilon/
 * born block IS present, the example README's own sentence applies: "The
 * force constants in this NaCl.fc is not usable for phonopy if dielectric
 * constants and Born effective charges are contained. This is because this
 * force constants are partially corrected by QE's implemented NAC method."
 * - phonopy needs UNCORRECTED force constants; the data-lins token order
 * census (i1-fastest) is checked as a computed integrity fact, reported
 * verbatim, values always taken from token[3] exactly as upstream does.</p>
 */
public final class QEPhonopyQ2rFc {

    private QEPhonopyQ2rFc() {
        // Utility
    }

    /** Fail-closed bound. */
    public static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;

    /** Element-count bound (36 blocks x 512 for the pinned example = 18,432). */
    public static final int MAX_ELEMENTS = 4_000_000;

    /** Verbatim unit note from the upstream parser docstring. */
    public static final String UNIT_NOTE =
            "Physical unit of force constants in the file is Ry/au^2.";

    /** Verbatim NAC doctrine from example/NaCl-QE-q2r/README. */
    public static final String NAC_DOCTRINE =
            "The force constants in this NaCl.fc is not usable for phonopy if"
            + " dielectric constants and Born effective charges are contained."
            + " This is because this force constants are partially corrected by"
            + " QE's implemented NAC method.";

    /** One fc block (k,ll,i,j) with its verbatim header and 4th-token values. */
    public static final class FcBlock {
        private final int k;          // xyz2, 0-based here
        private final int ll;         // xzy1
        private final int i;          // p2
        private final int j;          // p1
        private final String headerLine;
        private final double[] values; // ndim entries, i1-fastest order
        private final int orderChecked; // data lines matching the i1-fastest census

        FcBlock(int k, int ll, int i, int j, String headerLine, double[] values,
                int orderChecked) {
            this.k = k;
            this.ll = ll;
            this.i = i;
            this.j = j;
            this.headerLine = headerLine;
            this.values = values;
            this.orderChecked = orderChecked;
        }

        /** 'xyz2' index (k), 0-based. */
        public int getK() { return this.k; }

        /** 'xzy1' index (ll), 0-based. */
        public int getLl() { return this.ll; }

        /** 'p2' index (i), 0-based. */
        public int getI() { return this.i; }

        /** 'p1' index (j), 0-based. */
        public int getJ() { return this.j; }

        /** The block's verbatim header line ('k ll i j', 1-based as printed). */
        public String getHeaderLine() { return this.headerLine; }

        /** ndim values, in the file's own i1-fastest translation order. */
        public double[] getValues() { return this.values.clone(); }

        /** Data lines whose (i1,i2,i3) tokens matched the i1-fastest enumeration. */
        public int getOrderChecked() { return this.orderChecked; }
    }

    /** A parsed q2r .fc document. */
    public static final class Q2rFc {
        private final String sourceName;
        private final int ntype;
        private final int natom;
        private final int ibrav;
        private final String firstLine;
        private final List<String> cellLines;
        private final List<String> speciesLines;
        private final List<String> atomLines;
        private final double[] epsilon;       // 9 row-major, or null
        private final List<double[]> borns;   // natom x 9 row-major
        private final List<String> bornHeaders;
        private final int[] dim;              // 3
        private final List<FcBlock> blocks;
        private final int partialBlocksHeld;
        private final List<String> notes;

        Q2rFc(String sourceName, int ntype, int natom, int ibrav, String firstLine,
                List<String> cellLines, List<String> speciesLines,
                List<String> atomLines, double[] epsilon, List<double[]> borns,
                List<String> bornHeaders, int[] dim, List<FcBlock> blocks,
                int partialBlocksHeld, List<String> notes) {
            this.sourceName = sourceName;
            this.ntype = ntype;
            this.natom = natom;
            this.ibrav = ibrav;
            this.firstLine = firstLine;
            this.cellLines = cellLines;
            this.speciesLines = speciesLines;
            this.atomLines = atomLines;
            this.epsilon = epsilon;
            this.borns = borns;
            this.bornHeaders = bornHeaders;
            this.dim = dim;
            this.blocks = blocks;
            this.partialBlocksHeld = partialBlocksHeld;
            this.notes = notes;
        }

        public String getSourceName() { return this.sourceName; }

        public int getNtype() { return this.ntype; }

        public int getNatom() { return this.natom; }

        public int getIbrav() { return this.ibrav; }

        /** Line 1 verbatim (may carry alat + extra tokens; upstream reads [:3]). */
        public String getFirstLine() { return this.firstLine; }

        /** 3 cell lines (alat units) verbatim, present when ibrav == 0. */
        public List<String> getCellLines() { return this.cellLines; }

        /** ntype species/mass lines verbatim. */
        public List<String> getSpeciesLines() { return this.speciesLines; }

        /** natom atom lines verbatim (idx, species idx, crystal position). */
        public List<String> getAtomLines() { return this.atomLines; }

        /** Dielectric tensor 9 row-major, EMPTY when no NAC block was written. */
        public Optional<double[]> getEpsilon() {
            return Optional.ofNullable(this.epsilon == null ? null : this.epsilon.clone());
        }

        /** Born effective charges, one 9-value row-major array per atom (raw). */
        public List<double[]> getBorns() { return this.borns; }

        /** Per-atom born header lines verbatim ('    1', '    2', ...). */
        public List<String> getBornHeaders() { return this.bornHeaders; }

        /** The ldisp q grid == supercell dimensions (3 ints). */
        public int[] getDim() { return this.dim.clone(); }

        public List<FcBlock> getBlocks() { return this.blocks; }

        /** Expected block count 3*3*natom*natom. */
        public int getExpectedBlockCount() { return 9 * this.natom * this.natom; }

        public int getPartialBlocksHeld() { return this.partialBlocksHeld; }

        public List<String> getNotes() { return this.notes; }

        /** True when the file carries the NAC block (epsilon + borns). */
        public boolean hasNac() { return this.epsilon != null; }

        /** Largest |element| over every parsed block (computed fact). */
        public double getMaxAbsElement() {
            double max = 0.0;
            for (FcBlock block : this.blocks) {
                for (double v : block.values) {
                    max = Math.max(max, Math.abs(v));
                }
            }
            return max;
        }

        /**
         * BORN text in the make_born_q2r.py print shape (doc script + the
         * README's own result): first line 'default', then '%13.8f' x9 rows
         * WITHOUT trailing space (the q2r script uses '%13.8f'*9, unlike
         * phonopy-qe-born's trailing-space variant). RAW values, NOT
         * symmetrized (upstream runs elaborate_borns_and_epsilon; stated).
         * Null when the file carries no NAC block.
         */
        public String toBornText() {
            if (this.epsilon == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder("default\n");
            appendRow(sb, this.epsilon);
            for (double[] born : this.borns) {
                appendRow(sb, born);
            }
            return sb.toString();
        }

        private static void appendRow(StringBuilder sb, double[] row) {
            for (int c = 0; c < 9; c++) {
                if (c > 0) {
                    sb.append(' ');
                }
                sb.append(String.format(Locale.ROOT, "%13.8f", row[c]));
            }
            sb.append('\n');
        }
    }

    /** Reads a q2r .fc file from disk (bounded). */
    public static OperationResult<Q2rFc> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_Q2R_INPUT",
                    "No such q2r file: " + file, null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return OperationResult.failed("PHONOPY_Q2R_INPUT",
                        "q2r file exceeds the " + MAX_FILE_BYTES
                                + "-byte safety bound (" + size + " bytes): refusing"
                                + " rather than reading an unbounded artifact.", null);
            }
            return parseText(Files.readString(file), file.getFileName().toString());
        } catch (IOException e) {
            return OperationResult.failed("PHONOPY_Q2R_INPUT",
                    "Could not read q2r file " + file + ": " + e.getMessage(), null);
        }
    }

    /** Parses q2r .fc text (live-tail aware). */
    public static OperationResult<Q2rFc> parseText(String text, String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_Q2R_INPUT", "null text", null);
        }
        // NOTE: blank lines are NOT dropped here - the grammar is positional
        // (upstream readline()s sequentially). Trailing empties handled at loop end.
        if (text.isBlank()) {
            return OperationResult.failed("PHONOPY_Q2R_EMPTY",
                    sourceName + ": empty - a q2r .fc starts with the"
                            + " 'ntype natom ibrav' line.", null);
        }
        String[] lines = text.split("\n", -1);
        int cursor = 0;
        // guaranteed non-null: non-blank text splits into >= 1 line
        String first = lines[cursor];
        cursor++;
        String[] firstTokens = first.trim().split("\\s+");
        if (firstTokens.length < 3) {
            return OperationResult.failed("PHONOPY_Q2R_HEADER",
                    sourceName + ": line 1 must carry at least 'ntype natom ibrav'"
                            + " (upstream reads the first 3 tokens): '" + first.trim()
                            + "'", null);
        }
        int ntype;
        int natom;
        int ibrav;
        try {
            ntype = Integer.parseInt(firstTokens[0]);
            natom = Integer.parseInt(firstTokens[1]);
            ibrav = Integer.parseInt(firstTokens[2]);
        } catch (NumberFormatException e) {
            return OperationResult.failed("PHONOPY_Q2R_HEADER",
                    sourceName + ": line 1's first three tokens are not ints: '"
                            + first.trim() + "'", null);
        }
        if (ntype < 1 || natom < 1) {
            return OperationResult.failed("PHONOPY_Q2R_HEADER",
                    sourceName + ": ntype=" + ntype + " natom=" + natom
                            + " must both be >= 1", null);
        }
        if ((long) 9 * natom * natom > MAX_ELEMENTS) {
            return OperationResult.failed("PHONOPY_Q2R_HEADER",
                    sourceName + ": natom=" + natom + " implies "
                            + 9L * natom * natom + " fc blocks, beyond the element"
                            + " safety bound - refusing rather than allocating an"
                            + " unbounded structure.", null);
        }

        // ---- ibrav == 0: three cell lines (alat units) ----
        List<String> cellLines = new ArrayList<>();
        if (ibrav == 0) {
            for (int c = 0; c < 3; c++) {
                String cell = nextNonEof(lines, cursor);
                if (cell == null) {
                    return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                            sourceName + ": ibrav=0 declares 3 cell lines but the"
                                    + " file ends after " + c + " (still being"
                                    + " written by q2r.x?)", null);
                }
                cursor++;
                cellLines.add(cell.trim());
            }
        }

        // ---- ntype species lines + natom atom lines (verbatim) ----
        List<String> speciesLines = new ArrayList<>();
        for (int s = 0; s < ntype; s++) {
            String line = nextNonEof(lines, cursor);
            if (line == null) {
                return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                        sourceName + ": file ends inside the species block (line "
                                + (s + 1) + " of " + ntype + ")", null);
            }
            cursor++;
            speciesLines.add(line.trim());
        }
        List<String> atomLines = new ArrayList<>();
        for (int a = 0; a < natom; a++) {
            String line = nextNonEof(lines, cursor);
            if (line == null) {
                return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                        sourceName + ": file ends inside the atom block (atom "
                                + (a + 1) + " of " + natom + ")", null);
            }
            cursor++;
            atomLines.add(line.trim());
        }

        // ---- optional NAC block ('T' flag) ----
        String flagLine = nextNonEof(lines, cursor);
        if (flagLine == null) {
            return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                    sourceName + ": file ends before the NAC-flag / dims line", null);
        }
        cursor++;
        double[] epsilon = null;
        List<double[]> borns = new ArrayList<>();
        List<String> bornHeaders = new ArrayList<>();
        if (flagLine.strip().startsWith("T")) {
            epsilon = new double[9];
            for (int r = 0; r < 3; r++) {
                String row = nextNonEof(lines, cursor);
                if (row == null) {
                    return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                            sourceName + ": file ends inside the dielectric block"
                                    + " (row " + (r + 1) + " of 3)", null);
                }
                cursor++;
                if (!readRow3(row, epsilon, r * 3)) {
                    return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                            sourceName + ": dielectric row " + (r + 1)
                                    + " must carry 3 plain floats: '" + row.trim()
                                    + "'", null);
                }
            }
            for (int a = 0; a < natom; a++) {
                String header = nextNonEof(lines, cursor);
                if (header == null) {
                    return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                            sourceName + ": file ends before born header of atom "
                                    + (a + 1), null);
                }
                cursor++;
                bornHeaders.add(header.trim());
                double[] born = new double[9];
                for (int r = 0; r < 3; r++) {
                    String row = nextNonEof(lines, cursor);
                    if (row == null) {
                        return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                                sourceName + ": file ends inside the born block of"
                                        + " atom " + (a + 1) + " (row " + (r + 1)
                                        + " of 3)", null);
                    }
                    cursor++;
                    if (!readRow3(row, born, r * 3)) {
                        return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                                sourceName + ": born row " + (r + 1) + " of atom "
                                        + (a + 1) + " must carry 3 plain floats: '"
                                        + row.trim() + "'", null);
                    }
                }
                borns.add(born);
            }
            // after a NAC block the dims line follows
            flagLine = nextNonEof(lines, cursor);
            if (flagLine == null) {
                return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                        sourceName + ": NAC block complete but the dims line is"
                                + " missing", null);
            }
            cursor++;
        }

        // ---- dims line ----
        String[] dimTokens = flagLine.trim().split("\\s+");
        if (dimTokens.length != 3) {
            return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                    sourceName + ": the dims line must carry exactly 3 ints"
                            + " (nq1 nq2 nq3 of the ldisp grid): '" + flagLine.trim()
                            + "'", null);
        }
        int[] dim = new int[3];
        try {
            for (int d = 0; d < 3; d++) {
                dim[d] = Integer.parseInt(dimTokens[d]);
                if (dim[d] < 1) {
                    return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                            sourceName + ": dim entry " + (d + 1) + " is < 1: '"
                                    + flagLine.trim() + "'", null);
                }
            }
        } catch (NumberFormatException e) {
            return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                    sourceName + ": dims line is not integer text: '"
                            + flagLine.trim() + "'", null);
        }
        long ndim = (long) dim[0] * dim[1] * dim[2];
        if (ndim <= 0 || ndim > MAX_ELEMENTS) {
            return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                    sourceName + ": ndim = " + ndim + " exceeds the element safety"
                            + " bound", null);
        }
        long totalElements = 9L * natom * natom * ndim;
        if (totalElements > MAX_ELEMENTS) {
            return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                    sourceName + ": " + totalElements + " fc elements exceed the "
                            + MAX_ELEMENTS + " element safety bound (the pinned"
                            + " NaCl-QE-q2r example holds 18,432)", null);
        }

        // ---- fc blocks: (k,ll,i,j) ndindex order ----
        List<FcBlock> blocks = new ArrayList<>();
        int held = 0;
        int orderMismatchTotal = 0;
        String shapeIssue = null;
        for (int k = 0; k < 3 && shapeIssue == null && held == 0; k++) {
            for (int ll = 0; ll < 3 && shapeIssue == null && held == 0; ll++) {
                for (int i = 0; i < natom && shapeIssue == null && held == 0; i++) {
                    for (int j = 0; j < natom && shapeIssue == null && held == 0; j++) {
                        String header = nextNonEof(lines, cursor);
                        while (header != null && header.trim().isEmpty()) {
                            cursor++; // tolerate blank separators between blocks
                            header = nextNonEof(lines, cursor);
                        }
                        if (header == null) {
                            held++; // live: remaining blocks not written yet
                            break;
                        }
                        cursor++;
                        double[] values = new double[(int) ndim];
                        int orderChecked = 0;
                        for (long t = 0; t < ndim; t++) {
                            String row = nextNonEof(lines, cursor);
                            if (row == null || row.trim().isEmpty()) {
                                held++; // live: trailing block mid-write / cut
                                break;
                            }
                            cursor++;
                            String[] tokens = row.trim().split("\\s+");
                            if (tokens.length < 4) {
                                shapeIssue = "fc data line " + (t + 1) + " of block ("
                                        + (k + 1) + "," + (ll + 1) + "," + (i + 1) + ","
                                        + (j + 1) + ") has fewer than 4 tokens"
                                        + " (upstream reads token[3]): '" + row.trim()
                                        + "'";
                                break;
                            }
                            try {
                                values[(int) t] = Double.parseDouble(tokens[3]);
                            } catch (NumberFormatException e) {
                                shapeIssue = "fc data line " + (t + 1) + " of block ("
                                        + (k + 1) + "," + (ll + 1) + "," + (i + 1) + ","
                                        + (j + 1) + ") token 4 is not a plain double:"
                                        + " '" + tokens[3] + "' (Fortran E accepted,"
                                        + " D corrupt here)";
                                break;
                            }
                            // i1-fastest census (computed integrity fact)
                            try {
                                int i1 = Integer.parseInt(tokens[0]);
                                int i2 = Integer.parseInt(tokens[1]);
                                int i3 = Integer.parseInt(tokens[2]);
                                long expect = translationIndex(dim, i1, i2, i3);
                                if (expect == t) {
                                    orderChecked++;
                                }
                            } catch (NumberFormatException e) {
                                orderMismatchTotal++;
                            }
                        }
                        if (held > 0 || shapeIssue != null) {
                            break;
                        }
                        blocks.add(new FcBlock(k, ll, i, j, header.trim(), values,
                                orderChecked));
                    }
                }
            }
        }
        if (shapeIssue != null) {
            return OperationResult.failed("PHONOPY_Q2R_SHAPE",
                    sourceName + ": " + shapeIssue
                            + " (grammar pinned to phonopy/interface/qe.py PH_Q2R"
                            + " @ 3a3e0f09 + the real example/NaCl-QE-q2r/NaCl.fc)",
                    null);
        }
        if (blocks.isEmpty()) {
            return OperationResult.failed("PHONOPY_Q2R_PARTIAL",
                    sourceName + ": headers parsed but not even ONE complete fc"
                            + " block (still writing? the pinned example's first"
                            + " data line lands only after " + ndim
                            + " lines of block 1) - refusing rather than reading an"
                            + " empty tensor set as real.", null);
        }

        List<String> notes = new ArrayList<>();
        notes.add(UNIT_NOTE + " (verbatim upstream parser docstring; never"
                + " converted)");
        notes.add("ntype=" + ntype + " natom=" + natom + " ibrav=" + ibrav
                + (ibrav == 0 ? " (3 cell lines in alat units, verbatim)"
                        : " (no cell lines follow - upstream skips them only when"
                                + " ibrav=0)")
                + ", dims " + dim[0] + "x" + dim[1] + "x" + dim[2] + " -> ndim "
                + ndim + " translations per block");
        notes.add("blocks in 'xyz2 xzy1 p2 p1' (k,ll,i,j) ndindex order with"
                + " element = token[3] of each 'i1 i2 i3 value' line, i1 fastest:"
                + " " + blocks.size() + "/" + (9 * natom * natom) + " complete"
                + (held > 0 ? " (remainder held - live write in progress)" : ""));
        long totalChecked = blocks.stream().mapToLong(FcBlock::getOrderChecked).sum();
        notes.add("order census: " + totalChecked + "/" + ((long) blocks.size() * ndim)
                + " data lines carry the expected i1-fastest (i1,i2,i3) tokens"
                + (orderMismatchTotal > 0 ? " (" + orderMismatchTotal
                        + " non-integer token triples - values still read from"
                        + " token[3] exactly as upstream does)" : ""));
        if (epsilon != null) {
            notes.add("NAC BLOCK PRESENT (dielectric + " + natom + " born tensors,"
                    + " raw): " + NAC_DOCTRINE + " (verbatim"
                    + " example/NaCl-QE-q2r/README) - for phonopy consumption the"
                    + " README route replaces the Gamma dyn file with an"
                    + " epsil=.false. run; the dielectric/borns remain usable to"
                    + " BUILD the phonopy BORN file (toBornText, make_born_q2r.py"
                    + " shape, NOT symmetrized)");
        } else {
            notes.add("no NAC block: force constants UNcorrected - the shape"
                    + " phonopy consumes directly (README doctrine)");
        }
        notes.add("format provenance: upstream's docstring cites pw_forum"
                + " 2005-April/002408, 2008-September/010099, 2009-August/013613,"
                + " msg24388 - the format is community-documented, quoted, never"
                + " reinvented");
        if (held > 0) {
            notes.add("trailing blocks held back: " + blocks.size() + " complete of "
                    + (9 * natom * natom) + " declared by ndindex");
        }

        return OperationResult.success("PHONOPY_Q2R_OK",
                sourceName + ": natom=" + natom + ", grid " + dim[0] + "x" + dim[1]
                        + "x" + dim[2] + ", " + blocks.size() + "/" + (9 * natom * natom)
                        + " fc blocks" + (epsilon != null ? ", NAC block present"
                                : ", no NAC block")
                        + (held > 0 ? ", remainder HELD (live)" : ""),
                new Q2rFc(sourceName, ntype, natom, ibrav, first.trim(), cellLines,
                        speciesLines, atomLines, epsilon, borns, bornHeaders, dim,
                        blocks, held, List.copyOf(notes)));
    }

    /** i1-fastest translation enumeration index of (i1,i2,i3) in a dim grid. */
    private static long translationIndex(int[] dim, int i1, int i2, int i3) {
        if (i1 < 1 || i1 > dim[0] || i2 < 1 || i2 > dim[1] || i3 < 1 || i3 > dim[2]) {
            return -1;
        }
        return (long) (i1 - 1) + (long) dim[0] * ((long) (i2 - 1)
                + (long) dim[1] * (long) (i3 - 1));
    }

    private static String nextNonEof(String[] lines, int cursor) {
        return cursor >= lines.length ? null : lines[cursor];
    }

    private static boolean readRow3(String line, double[] target, int offset) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length != 3) {
            return false;
        }
        try {
            for (int c = 0; c < 3; c++) {
                target[offset + c] = Double.parseDouble(tokens[c]);
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /** One-line census report for the service + viewer. */
    public static String describe(Q2rFc fc) {
        StringBuilder sb = new StringBuilder();
        sb.append("atoms: ").append(fc.getNatom()).append(", species: ")
                .append(fc.getNtype()).append(", ibrav: ").append(fc.getIbrav())
                .append('\n');
        sb.append("q2r grid (== supercell dims): ").append(fc.getDim()[0])
                .append(" x ").append(fc.getDim()[1]).append(" x ")
                .append(fc.getDim()[2]).append('\n');
        sb.append("fc blocks: ").append(fc.getBlocks().size()).append(" / ")
                .append(fc.getExpectedBlockCount()).append('\n');
        sb.append(String.format(Locale.ROOT, "max |fc element|: %.10g (unit note:"
                + " %s)%n", fc.getMaxAbsElement(), UNIT_NOTE));
        sb.append("NAC block: ").append(fc.hasNac() ? "PRESENT (raw dielectric + "
                + fc.getBorns().size() + " born tensors)" : "absent").append('\n');
        if (fc.getPartialBlocksHeld() > 0) {
            sb.append("live: trailing blocks held\n");
        }
        for (String note : fc.getNotes()) {
            sb.append("- ").append(note).append('\n');
        }
        return sb.toString().trim();
    }
}
