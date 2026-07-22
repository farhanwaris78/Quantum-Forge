/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import quantumforge.operation.OperationResult;

/**
 * Extracts the dielectric tensor and the Born effective charges (BEC) from a
 * Quantum ESPRESSO {@code ph.x} output - the raw half of upstream's
 * {@code phonopy-qe-born} command
 * (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e,
 * {@code phonopy/cui/phonopy_qe_born_script.py parse_ph_out}). The hooks and
 * the block layouts are pinned VERBATIM against upstream's own QE test
 * fixture {@code test/interface/qe/NaCl-ph/NaCl.ph.out}:
 *
 * <pre>
 * number of atoms/cell      =            2      &lt;- split()[4], sanity check
 *          Dielectric constant in cartesian axis   &lt;- hook (LAST block wins)
 *                                                &lt;- one line skipped
 *          (       2.474410241       0.000000000      -0.000000000 )
 *          (      -0.000000000       2.474410241      -0.000000000 )
 *          (       0.000000000      -0.000000000       2.474410241 )
 *          Effective charges (d Force / dE) in cartesian axis without acoustic ...
 *                                                &lt;- hook (LAST block wins)
 *                                                &lt;- one line skipped
 *           atom    1  Na    Mean Z*:        1.09885   &lt;- per-atom header skipped
 *      Ex  (        1.09885        0.00000       -0.00000 )  &lt;- tokens[2:5]
 *      Ey  (        0.00000        1.09885       -0.00000 )
 *      Ez  (       -0.00000       -0.00000        1.09885 )
 * </pre>
 *
 * <p>LAST-BLOCK-WINS is upstream's own behavior: {@code parse_ph_out} loops
 * over the whole file and re-assigns the tensors each time a hook matches
 * (the pinned NaCl fixture carries the dielectric/BEC pair TWICE, lines
 * 208/214 and 307/313) - the later, complete block replaces the earlier
 * one, never blended. The sibling block {@code Effective charges ... with
 * asr applied:} and the {@code Effective charges Sum: Mean:} lines do NOT
 * match the hook and are counted as surrounding context, never parsed.</p>
 *
 * <p>Interpretation boundary: upstream's command then runs
 * {@code elaborate_borns_and_epsilon} (symmetrize_tensors=True default) so
 * its PRINTED BORN values are symmetrized (the upstream test pins
 * eps=2.47441024-eye and Z*=+-1.10075500-eye AFTER symmetrization, while
 * the raw parsed BEC values are 1.09885 / -1.10266). This class mirrors the
 * RAW PARSE ONLY (the upstream {@code parse_ph_out} function), never the
 * symmetrization - {@link QeBornExtract#toBornText()} therefore emits raw
 * per-atom tensors and SAYS SO, and the raw values differ from upstream's
 * symmetrized print BY DESIGN. When atoms are symmetry-equivalent under the
 * primitive cell, upstream's own {@code phonopy-qe-born} must be used: it
 * also merges to symmetry-independent lines; ours emits one line per atom,
 * which is exactly phonopy's expected BORN shape only when all atoms are
 * symmetry-independent (the common molecular / distinct-species case).</p>
 */
public final class QEPhonopyQeBorn {

    private QEPhonopyQeBorn() {
        // Utility
    }

    /** Fail-closed bound (ph.x stdout, generous). */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    /** Verbatim upstream hooks (parse_ph_out). */
    public static final String HOOK_EPS = "Dielectric constant in cartesian axis";
    public static final String HOOK_BEC =
            "Effective charges (d Force / dE) in cartesian axis without acoustic";
    public static final String HOOK_NATOM = "number of atoms/cell      =";
    /** Context-only neighbor that must NOT match the BEC hook. */
    public static final String HOOK_ASR_APPLIED =
            "Effective charges (d Force / dE) in cartesian axis with asr applied:";

    /** One atom's raw BEC block with the verbatim header aids upstream discards. */
    public static final class AtomCharge {
        private final int atomIndex;      // 1-based, from the 'atom    N' header
        private final String speciesLabel; // verbatim e.g. Na / Cl (aid, not used upstream)
        private final String meanZText;    // verbatim 'Mean Z*' token (aid)
        private final double[][] tensor;   // [3][3] from the Ex/Ey/Ez rows tokens[2:5]

        AtomCharge(int atomIndex, String speciesLabel, String meanZText,
                double[][] tensor) {
            this.atomIndex = atomIndex;
            this.speciesLabel = speciesLabel;
            this.meanZText = meanZText;
            this.tensor = tensor;
        }

        public int getAtomIndex() { return this.atomIndex; }

        /** Species label captured verbatim from the atom header (upstream ignores it). */
        public String getSpeciesLabel() { return this.speciesLabel; }

        /** Verbatim 'Mean Z*' printed value from the atom header (upstream ignores it). */
        public String getMeanZText() { return this.meanZText; }

        /** Raw 3x3 Born effective charge tensor (Ex/Ey/Ez rows, tokens 2..4). */
        public double[][] getTensor() { return this.tensor; }
    }

    /** The merged result: LAST complete dielectric + BEC blocks. */
    public static final class QeBornExtract {
        private final String sourceName;
        private final Integer fileNatom;         // from the natom hook line, if present
        private final int dielectricHookCount;   // how many eps blocks were seen
        private final int becHookCount;          // how many BEC blocks were seen
        private final int asrAppliedBlockCount;  // 'with asr applied' blocks (counted only)
        private final double[] dielectric;       // 9 row-major, LAST complete block
        private final List<AtomCharge> charges;  // natom entries, LAST complete block
        private final int partialBlocksHeld;
        private final List<String> notes;

        QeBornExtract(String sourceName, Integer fileNatom, int dielectricHookCount,
                int becHookCount, int asrAppliedBlockCount, double[] dielectric,
                List<AtomCharge> charges, int partialBlocksHeld, List<String> notes) {
            this.sourceName = sourceName;
            this.fileNatom = fileNatom;
            this.dielectricHookCount = dielectricHookCount;
            this.becHookCount = becHookCount;
            this.asrAppliedBlockCount = asrAppliedBlockCount;
            this.dielectric = dielectric;
            this.charges = charges;
            this.partialBlocksHeld = partialBlocksHeld;
            this.notes = notes;
        }

        public String getSourceName() { return this.sourceName; }

        /** nat atoms declared by the file itself ('number of atoms/cell ='), if printed. */
        public Optional<Integer> getFileNatom() { return Optional.ofNullable(this.fileNatom); }

        public int getDielectricHookCount() { return this.dielectricHookCount; }

        public int getBecHookCount() { return this.becHookCount; }

        /** '... with asr applied:' sibling blocks seen (context, never parsed). */
        public int getAsrAppliedBlockCount() { return this.asrAppliedBlockCount; }

        /** Dielectric tensor, 9 values row-major, LAST complete block. */
        public double[] getDielectric() { return this.dielectric.clone(); }

        /** Raw per-atom BEC tensors, LAST complete block. */
        public List<AtomCharge> getCharges() { return this.charges; }

        /** Trailing partial block(s) held back during a live read. */
        public int getPartialBlocksHeld() { return this.partialBlocksHeld; }

        public List<String> getNotes() { return this.notes; }

        /**
         * Emits BORN-file text in upstream {@code phonopy-qe-born}'s PRINT shape
         * ({@code %13.8f } x9 rows) with the RAW values. NO symmetrization is
         * applied - upstream's printed numbers differ (its default
         * symmetrize_tensors=True) and this text says so on the comment line.
         * Line 1 carries the upstream comment header with 1-based atom indices;
         * phonopy reads such a non-numeric first line as 'use the default NAC
         * factor'.
         */
        public String toBornText() {
            List<double[]> rows = new ArrayList<>(this.charges.size());
            for (AtomCharge charge : this.charges) {
                double[] flat = new double[9];
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        flat[i * 3 + j] = charge.tensor[i][j];
                    }
                }
                rows.add(flat);
            }
            return QEPhonopyBorn.bornText(rows, this.dielectric);
        }
    }

    /** Reads a ph.x output from disk (bounded). expectedNatom comes from the pw input. */
    public static OperationResult<QeBornExtract> parse(Path file, int expectedNatom) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_QEBORN_INPUT",
                    "No such ph.x output: " + file, null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return OperationResult.failed("PHONOPY_QEBORN_INPUT",
                        "ph.x output exceeds the " + MAX_FILE_BYTES
                                + "-byte safety bound (" + size + " bytes): refusing"
                                + " rather than reading an unbounded artifact.", null);
            }
            return parseText(Files.readString(file), file.getFileName().toString(),
                    expectedNatom);
        } catch (IOException e) {
            return OperationResult.failed("PHONOPY_QEBORN_INPUT",
                    "Could not read ph.x output " + file + ": " + e.getMessage(), null);
        }
    }

    /**
     * Parses ph.x output text. LAST complete dielectric block + LAST complete
     * BEC block win (upstream's own re-assign loop); a trailing block cut
     * mid-rows is held back (live), a MALFORMED row inside a block is SHAPE.
     */
    public static OperationResult<QeBornExtract> parseText(String text,
            String sourceName, int expectedNatom) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_QEBORN_INPUT", "null text", null);
        }
        if (expectedNatom < 1) {
            return OperationResult.failed("PHONOPY_QEBORN_INPUT",
                    "expectedNatom must be >= 1 (it comes from the pw input's nat"
                            + " card; upstream's parse_ph_out takes it as a required"
                            + " argument). Given: " + expectedNatom, null);
        }
        String[] lines = text.split("\n", -1);
        Integer fileNatom = null;
        int epsHooks = 0;
        int becHooks = 0;
        int asrBlocks = 0;
        int held = 0;
        double[] dielectric = null;
        List<AtomCharge> charges = null;
        String shapeIssue = null;

        int i = 0;
        while (i < lines.length && shapeIssue == null) {
            String line = lines[i];
            if (line.contains(HOOK_NATOM)) {
                String[] tokens = line.trim().split("\\s+");
                // upstream: int(line.split()[4]); assert natoms == _natoms fires
                // IMMEDIATELY at this line (it precedes every block hook), so the
                // mismatch refusal is early too, never deferred behind block parses
                if (tokens.length > 4) {
                    try {
                        fileNatom = Integer.valueOf(tokens[4]);
                    } catch (NumberFormatException e) {
                        shapeIssue = "the 'number of atoms/cell =' line's 5th token"
                                + " is not an int: '" + line.trim() + "'";
                        break;
                    }
                    if (fileNatom.intValue() != expectedNatom) {
                        return OperationResult.failed("PHONOPY_QEBORN_NATOM",
                                sourceName + ": the pw side declares nat="
                                        + expectedNatom + " but the ph.x output"
                                        + " itself prints 'number of atoms/cell = "
                                        + fileNatom + "' - upstream raises"
                                        + " AssertionError at exactly this line;"
                                        + " refusing rather than pairing a cell with"
                                        + " the wrong output.", null);
                    }
                } else {
                    shapeIssue = "the 'number of atoms/cell =' line has fewer than 5"
                            + " tokens: '" + line.trim() + "'";
                    break;
                }
                i++;
                continue;
            }
            if (line.contains(HOOK_EPS)) {
                epsHooks++;
                // skip 1 line, then 3 rows of parens-stripped floats
                double[] block = tryDielectric(lines, i);
                if (block != null) {
                    dielectric = block; // LAST complete block replaces (upstream mirror)
                    i += 5;             // hook + skip + 3 rows
                } else {
                    BlockProbe probe = probeDielectric(lines, i);
                    if (probe.truncatedAtEof) {
                        held++; // live: file cut inside this LAST block - keep previous
                        break;
                    }
                    shapeIssue = probe.issue;
                }
                continue;
            }
            if (line.contains(HOOK_BEC)) {
                becHooks++;
                BlockProbe probe = probeBec(lines, i, expectedNatom);
                if (probe.charges != null) {
                    charges = probe.charges; // LAST complete block replaces
                    i = probe.nextIndex;
                } else if (probe.truncatedAtEof) {
                    held++;
                    break;
                } else {
                    shapeIssue = probe.issue;
                }
                continue;
            }
            if (line.contains(HOOK_ASR_APPLIED)) {
                asrBlocks++;
            }
            i++;
        }

        if (shapeIssue != null) {
            return OperationResult.failed("PHONOPY_QEBORN_SHAPE",
                    sourceName + ": " + shapeIssue
                            + " (grammar pinned to upstream parse_ph_out + the QE test"
                            + " fixture NaCl.ph.out)", null);
        }
        if (dielectric == null) {
            // upstream RuntimeError message, verbatim
            return OperationResult.failed("PHONOPY_QEBORN_HEADER",
                    sourceName + ": Could not find dielectric tensor in ph.x output."
                            + " (verbatim upstream message; the run needs 'epsil ="
                            + " .true.' under &inputph)", null);
        }
        if (charges == null) {
            // upstream RuntimeError message, verbatim
            return OperationResult.failed("PHONOPY_QEBORN_HEADER",
                    sourceName + ": Could not find Born effective charges in ph.x"
                            + " output. (verbatim upstream message)", null);
        }
        List<String> notes = new ArrayList<>();
        notes.add("LAST of " + epsHooks + " dielectric block(s) + LAST of " + becHooks
                + " 'without acoustic' BEC block(s) used - LAST-BLOCK-WINS mirrors"
                + " upstream's re-assign loop (the pinned NaCl.ph.out fixture carries"
                + " the pair TWICE)");
        if (asrBlocks > 0) {
            notes.add(asrBlocks + " 'Effective charges ... with asr applied:' block(s)"
                    + " seen and COUNTED only - they do not match the upstream hook"
                    + " and are never parsed (phonopy ignores them identically)");
        }
        notes.add("RAW values (upstream parse_ph_out), NOT symmetrized: upstream's"
                + " phonopy-qe-born then applies elaborate_borns_and_epsilon"
                + " (symmetrize_tensors=True), so its printed BORN differs - e.g."
                + " the pinned fixture parses to 1.09885 / -1.10266 raw but prints"
                + " +-1.10075500 symmetrized");
        notes.add("each atom's species label and 'Mean Z*' header token are captured"
                + " VERBATIM as display aids; upstream's parser discards them");
        if (fileNatom == null) {
            notes.add("no 'number of atoms/cell =' line in this output: the caller's"
                    + " nat=" + expectedNatom + " is used unchecked (upstream only"
                    + " asserts when the line EXISTS)");
        }
        if (held > 0) {
            notes.add(held + " trailing block(s) cut mid-rows held back (live write"
                    + " in progress); the LAST COMPLETE block set is reported");
        }
        return OperationResult.success("PHONOPY_QEBORN_OK",
                sourceName + ": dielectric + " + charges.size() + " raw BEC tensor(s)"
                        + (held > 0 ? "; " + held + " trailing partial block held" : ""),
                new QeBornExtract(sourceName, fileNatom, epsHooks, becHooks, asrBlocks,
                        dielectric, charges, held, List.copyOf(notes)));
    }

    // ---------- block probes ----------

    private static final class BlockProbe {
        double[] epsRows;
        List<AtomCharge> charges;
        int nextIndex;
        boolean truncatedAtEof;
        String issue;
    }

    /** Reads the 3 dielectric rows after hook+skip; null when not cleanly parseable. */
    private static double[] tryDielectric(String[] lines, int hookIndex) {
        if (hookIndex + 5 > lines.length) {
            return null; // truncated at EOF; probeDielectric classifies
        }
        double[] flat = new double[9];
        int k = 0;
        for (int r = 0; r < 3; r++) {
            double[] row = stripFloats(lines[hookIndex + 2 + r]);
            if (row == null || row.length != 3) {
                return null; // exact-3 grammar; extras are corruption, never skipped
            }
            flat[k++] = row[0];
            flat[k++] = row[1];
            flat[k++] = row[2];
        }
        return flat;
    }

    private static BlockProbe probeDielectric(String[] lines, int hookIndex) {
        BlockProbe probe = new BlockProbe();
        if (hookIndex + 5 > lines.length) {
            probe.truncatedAtEof = true;
            return probe;
        }
        for (int r = 0; r < 3; r++) {
            String row = lines[hookIndex + 2 + r];
            double[] values = stripFloats(row);
            if (values == null || values.length != 3) {
                probe.issue = "dielectric row " + (r + 1) + " after the hook is not"
                        + " three parens-wrapped floats: '" + row.trim() + "'";
                return probe;
            }
        }
        probe.issue = "dielectric block failed to parse";
        return probe;
    }

    private static BlockProbe probeBec(String[] lines, int hookIndex, int natom) {
        BlockProbe probe = new BlockProbe();
        // hook + skip 1 + per atom: 1 header + 3 rows
        int need = hookIndex + 2 + natom * 4;
        if (need > lines.length) {
            probe.truncatedAtEof = true;
            return probe;
        }
        List<AtomCharge> charges = new ArrayList<>(natom);
        int cursor = hookIndex + 2;
        for (int atom = 0; atom < natom; atom++) {
            String header = lines[cursor].trim();
            // verbatim aids: 'atom    1  Na    Mean Z*:        1.09885'
            int atomIndex = atom + 1;
            String species = "";
            String meanZ = "";
            String[] ht = header.split("\\s+");
            if (ht.length >= 2 && ht[0].equals("atom")) {
                try {
                    atomIndex = Integer.parseInt(ht[1]);
                } catch (NumberFormatException e) {
                    atomIndex = atom + 1;
                }
                if (ht.length >= 3) {
                    species = ht[2];
                }
                if (ht.length >= 1) {
                    meanZ = ht[ht.length - 1];
                }
            }
            double[][] tensor = new double[3][3];
            for (int r = 0; r < 3; r++) {
                String row = lines[cursor + 1 + r];
                String[] tokens = row.trim().split("\\s+");
                // upstream: line.split()[2:5]
                if (tokens.length < 5) {
                    probe.issue = "BEC row " + (r + 1) + " of atom " + (atom + 1)
                            + " has fewer than 5 tokens (upstream reads tokens"
                            + " [2:5]): '" + row.trim() + "'";
                    return probe;
                }
                for (int c = 0; c < 3; c++) {
                    try {
                        tensor[r][c] = Double.parseDouble(tokens[2 + c]);
                    } catch (NumberFormatException e) {
                        probe.issue = "BEC row " + (r + 1) + " of atom " + (atom + 1)
                                + " token " + (3 + c) + " is not a float: '"
                                + tokens[2 + c] + "'";
                        return probe;
                    }
                }
            }
            charges.add(new AtomCharge(atomIndex, species, meanZ, tensor));
            cursor += 4;
        }
        probe.charges = charges;
        probe.nextIndex = cursor;
        return probe;
    }

    /**
     * Strips parens and parses doubles (upstream _strip + fromstring with
     * float64 - double here so the 12-decimal dielectric digits survive).
     */
    private static double[] stripFloats(String line) {
        String stripped = line.trim();
        while (stripped.startsWith("(")) {
            stripped = stripped.substring(1).trim();
        }
        while (stripped.endsWith(")")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        if (stripped.isEmpty()) {
            return new double[0];
        }
        String[] tokens = stripped.split("\\s+");
        double[] values = new double[tokens.length];
        try {
            for (int i = 0; i < tokens.length; i++) {
                values[i] = Double.parseDouble(tokens[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return values;
    }
}
