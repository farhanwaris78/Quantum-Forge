/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import quantumforge.operation.OperationResult;

/**
 * Reader for phonopy's {@code FORCE_CONSTANTS} text file - the artifact
 * written by {@code phonopy --writefc} and consumed instead of FORCE_SETS by
 * {@code phonopy --readfc} / {@code phonopy-load}. The grammar is pinned
 * line-by-line against the upstream writer/reader
 * {@code get_FORCE_CONSTANTS_lines / parse_FORCE_CONSTANTS}
 * (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <pre>
 * %4d %4d                         &lt;- dims (n_i, n_j); a SINGLE int is also
 *                                    accepted by upstream and read as square
 * i j                             &lt;- block header, 1-based; upstream reads
 * ( %22.15f x3 ) x3 rows              ONLY the first token (s_i) for its
 * ...                                 index-consistency census
 * </pre>
 *
 * <p>Blocks: {@code n_i x n_j} of them; each tensor is 3 rows of exactly 3
 * doubles row-major. The full shape is {@code (n_satom, n_satom)}; the
 * compact shape {@code (n_patom, n_satom)} pairs with a p2s_map that lives
 * in phonopy.yaml/hdf5, NOT in this file - so this reader reports the dims
 * verbatim and never invents a p2s mapping. Upstream's
 * {@code check_force_constants_indices} censuses the DISTINCT first tokens
 * of the block headers; we do the same and REPORT the census without the
 * symmetry-side check phonopy performs.</p>
 *
 * <p>Strictness mirrored from upstream's {@code float(x)}: plain decimal
 * text only (Fortran D exponents are corrupt here, never converted). A
 * truncated trailing block (live write) is held back and counted; a
 * malformed MID-file row is {@code PHONOPY_FC_SHAPE}, not skipped.</p>
 */
public final class QEPhonopyForceConstants {

    private QEPhonopyForceConstants() {
        // Utility
    }

    /** Fail-closed size bound (a 216-atom full tensor file is ~10 MB). */
    public static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;

    /** Safety bound on n_i x n_j cells (216x216 = 46656 real-world example). */
    public static final int MAX_CELLS = 250000;

    /** A parsed FORCE_CONSTANTS file (values kept as written, double). */
    public static final class ForceConstants {
        private final String sourceName;
        private final int dimI;          // first dims token
        private final int dimJ;          // second dims token (== dimI for 1-token)
        private final boolean singleIntHeader;
        private final List<double[]> cells; // row-major 9-value tensors, block order
        private final List<int[]> headers;  // verbatim [i, j] (j may be -1 if absent)
        private final int distinctFirstIndices;
        private final int partialBlocksHeld;
        private final List<String> notes;

        ForceConstants(String sourceName, int dimI, int dimJ, boolean singleIntHeader,
                List<double[]> cells, List<int[]> headers, int distinctFirstIndices,
                int partialBlocksHeld, List<String> notes) {
            this.sourceName = sourceName;
            this.dimI = dimI;
            this.dimJ = dimJ;
            this.singleIntHeader = singleIntHeader;
            this.cells = cells;
            this.headers = headers;
            this.distinctFirstIndices = distinctFirstIndices;
            this.partialBlocksHeld = partialBlocksHeld;
            this.notes = notes;
        }

        public String getSourceName() { return this.sourceName; }

        /** First dims token (atom count on the i axis). */
        public int getDimI() { return this.dimI; }

        /** Second dims token (atom count on the j axis). */
        public int getDimJ() { return this.dimJ; }

        /** True when the header line carried ONE int (upstream: read as square). */
        public boolean isSingleIntHeader() { return this.singleIntHeader; }

        /** Fully parsed n_i x n_j blocks, each a 9-value row-major tensor. */
        public List<double[]> getCells() { return this.cells; }

        /** Verbatim block headers [i, j] (1-based; j = -1 when not printed). */
        public List<int[]> getHeaders() { return this.headers; }

        /** Distinct first-header-token census (upstream idx1 mirror). */
        public int getDistinctFirstIndices() { return this.distinctFirstIndices; }

        /** Trailing partial block held back during a live read (0 or 1). */
        public int getPartialBlocksHeld() { return this.partialBlocksHeld; }

        public List<String> getNotes() { return this.notes; }

        /** Largest |element| over every parsed tensor (computed fact). */
        public double getMaxAbsElement() {
            double max = 0.0;
            for (double[] cell : this.cells) {
                for (double v : cell) {
                    max = Math.max(max, Math.abs(v));
                }
            }
            return max;
        }

        /** The (i-1, j-1) zero-based tensor, or null when out of range. */
        public double[] cellAt(int i, int j) {
            long index = (long) i * this.dimJ + j;
            if (i < 0 || j < 0 || index >= this.cells.size()) {
                return null;
            }
            return this.cells.get((int) index);
        }
    }

    /** Reads a FORCE_CONSTANTS file from disk (bounded). */
    public static OperationResult<ForceConstants> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_FC_INPUT",
                    "No such FORCE_CONSTANTS file: " + file, null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return OperationResult.failed("PHONOPY_FC_INPUT",
                        "FORCE_CONSTANTS exceeds the " + MAX_FILE_BYTES
                                + "-byte safety bound (" + size + " bytes): refusing"
                                + " rather than reading an unbounded artifact.", null);
            }
            return parseText(Files.readString(file), file.getFileName().toString());
        } catch (IOException e) {
            return OperationResult.failed("PHONOPY_FC_INPUT",
                    "Could not read FORCE_CONSTANTS " + file + ": " + e.getMessage(),
                    null);
        }
    }

    /** Parses FORCE_CONSTANTS text (live-tail aware). */
    public static OperationResult<ForceConstants> parseText(String text,
            String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_FC_INPUT", "null text", null);
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
            return OperationResult.failed("PHONOPY_FC_EMPTY",
                    sourceName + ": no content - FORCE_CONSTANTS starts with the"
                            + " '%4d %4d' dims line.", null);
        }

        // ---- dims line: 1 int (square) or 2 ints ----
        String[] dimTokens = lines.get(0).split("\\s+");
        int dimI;
        int dimJ;
        boolean single = dimTokens.length == 1;
        try {
            if (dimTokens.length == 1) {
                dimI = Integer.parseInt(dimTokens[0]);
                dimJ = dimI;
            } else if (dimTokens.length == 2) {
                dimI = Integer.parseInt(dimTokens[0]);
                dimJ = Integer.parseInt(dimTokens[1]);
            } else {
                return OperationResult.failed("PHONOPY_FC_SHAPE",
                        sourceName + ": dims line must carry 1 or 2 ints (upstream"
                                + " accepts both; writer prints '%4d %4d'). Found: '"
                                + lines.get(0) + "'", null);
            }
        } catch (NumberFormatException e) {
            return OperationResult.failed("PHONOPY_FC_SHAPE",
                    sourceName + ": dims line is not integer text: '" + lines.get(0)
                            + "'", null);
        }
        if (dimI < 1 || dimJ < 1) {
            return OperationResult.failed("PHONOPY_FC_SHAPE",
                    sourceName + ": dims must be >= 1 (found " + dimI + " " + dimJ
                            + ")", null);
        }
        long cellTotal = (long) dimI * dimJ;
        if (cellTotal > MAX_CELLS) {
            return OperationResult.failed("PHONOPY_FC_SHAPE",
                    sourceName + ": dims imply " + cellTotal + " tensor blocks, beyond"
                            + " the " + MAX_CELLS + "-cell safety bound (a 216-atom"
                            + " full tensor is 46656 blocks / ~10 MB) - refusing"
                            + " rather than allocating an unbounded structure.", null);
        }
        // ---- blocks (live-tail aware: only the FINAL block may be mid-write) ----
        List<double[]> cells = new ArrayList<>((int) cellTotal);
        List<int[]> headers = new ArrayList<>((int) cellTotal);
        Set<Integer> distinctFirst = new LinkedHashSet<>();
        int held = 0;
        int cursor = 1;
        for (long b = 0; b < cellTotal; b++) {
            if (cursor + 4 > lines.size()) {
                held = 1; // live: the LAST block is mid-write
                break;
            }
            String[] headTokens = lines.get(cursor).split("\\s+");
            int hi;
            int hj = -1;
            try {
                hi = Integer.parseInt(headTokens[0]);
                if (headTokens.length >= 2) {
                    hj = Integer.parseInt(headTokens[1]);
                }
            } catch (NumberFormatException e) {
                return OperationResult.failed("PHONOPY_FC_SHAPE",
                        sourceName + ": block " + (b + 1) + " header is not integer"
                                + " text (upstream reads the first token, writer"
                                + " prints '%d %d'): '" + lines.get(cursor) + "'",
                        null);
            }
            double[] tensor = new double[9];
            int k = 0;
            boolean rowBad = false;
            for (int r = 0; r < 3 && !rowBad; r++) {
                String[] tokens = lines.get(cursor + 1 + r).split("\\s+");
                if (tokens.length != 3) {
                    rowBad = true;
                    break;
                }
                try {
                    for (int c = 0; c < 3; c++) {
                        tensor[k++] = Double.parseDouble(tokens[c]);
                    }
                } catch (NumberFormatException e) {
                    rowBad = true;
                }
            }
            if (rowBad) {
                return OperationResult.failed("PHONOPY_FC_SHAPE",
                        sourceName + ": block " + (b + 1) + " (header '"
                                + lines.get(cursor) + "') needs 3 rows of EXACTLY 3"
                                + " plain doubles each (writer '%22.15f'; upstream"
                                + " float() rejects D exponents, so we do too).", null);
            }
            distinctFirst.add(hi);
            headers.add(new int[] {hi, hj});
            cells.add(tensor);
            cursor += 4;
        }

        List<String> notes = new ArrayList<>();
        notes.add("dims " + dimI + " x " + dimJ
                + (single ? " (single-int header, read as square - upstream's own"
                        + " acceptance)" : " (two-int header, writer '%4d %4d')")
                + "; shape (n_satom, n_satom) when full or (n_patom, n_satom) when"
                + " compact - the p2s_map that distinguishes the two lives in"
                + " phonopy.yaml/hdf5, NOT in this file, so the choice is reported,"
                + " never guessed");
        notes.add(distinctFirst.size() + " distinct first header token(s) across "
                + cells.size() + " parsed block(s) - upstream census"
                + " (check_force_constants_indices) compares THIS set against its"
                + " p2s_map expectation; phonopy makes that symmetry call, we report"
                + " the census verbatim");
        if (held > 0) {
            notes.add("final block mid-write held back (live read): " + cells.size()
                    + " of " + cellTotal + " blocks parsed; the held block re-reads"
                    + " complete once the writer finishes");
        } else if (cells.size() != cellTotal) {
            // unreachable by construction (loop only breaks via held), defensive
            notes.add("parsed " + cells.size() + " of " + cellTotal + " blocks");
        }
        if (cells.isEmpty()) {
            return OperationResult.failed("PHONOPY_FC_PARTIAL",
                    sourceName + ": dims declare " + cellTotal + " blocks but not even"
                            + " ONE complete block is present (file just opened or"
                            + " still being written?) - refusing rather than reading"
                            + " an empty tensor set as real.", null);
        }
        if (held == 0 && cursor < lines.size()) {
            notes.add((lines.size() - cursor) + " trailing content line(s) after the"
                    + " declared " + cellTotal + " blocks: phonopy's own reader stops"
                    + " at the declared count too; reported, not parsed");
        }
        return OperationResult.success("PHONOPY_FC_OK",
                sourceName + ": " + cells.size() + "/" + cellTotal
                        + " tensor block(s) (" + dimI + " x " + dimJ + ")"
                        + (held > 0 ? "; final block held (live)" : ""),
                new ForceConstants(sourceName, dimI, dimJ, single, cells, headers,
                        distinctFirst.size(), held, List.copyOf(notes)));
    }

    /** Debug/describe helper: one-line summary used by the service + viewer. */
    public static String describe(ForceConstants fc) {
        StringBuilder sb = new StringBuilder();
        sb.append("dims: ").append(fc.getDimI()).append(" x ").append(fc.getDimJ())
                .append(fc.isSingleIntHeader() ? " (single-int header)" : "")
                .append('\n');
        sb.append("blocks parsed: ").append(fc.getCells().size()).append('\n');
        sb.append("distinct first header tokens: ").append(fc.getDistinctFirstIndices())
                .append('\n');
        if (fc.getPartialBlocksHeld() > 0) {
            sb.append("final block HELD (live write)\n");
        }
        sb.append(String.format(Locale.ROOT, "max |element|: %.10g%n",
                fc.getMaxAbsElement()));
        for (String note : fc.getNotes()) {
            sb.append("- ").append(note).append('\n');
        }
        return sb.toString().trim();
    }
}
