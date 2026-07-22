/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Reader for the yiwang62 "BoltzTraP2Y" fork's dope-side DOS dumps, pinned
 * verbatim from {@code parse_dope} + the dosremesh epilogue in
 * {@code BoltzTraP2/interface.py} (branch 20210126):
 *
 * <ul>
 *   <li>{@code *.dope.dos}: two columns, NO header -
 *       {@code fp.write('{} {}\n'.format((e-fermi)/Volt, dos[i]*Volt))}, i.e.
 *       energy relative to the refined Fermi level expressed through the
 *       Volt unit (eV) and the DOS scaled by Volt;</li>
 *   <li>{@code *.dope.vvdos}: four columns, NO header -
 *       {@code (e-fermi)/Volt} then {@code vvdos[0,0]*Volt,
 *       vvdos[1,1]*Volt, vvdos[2,2]*Volt} (the velocity-weighted DOS
 *       tensor diagonals);</li>
 *   <li>the {@code _raw} suffixes carry the same shapes: when a DOSCAR
 *       remesh runs, the pre-remesh tables are renamed to
 *       {@code .dope.dos_raw} and {@code .dope.vvdos_raw} by
 *       {@code os.rename} BEFORE the refined pair is rewritten under the
 *       original names (provenance stated, never merged).</li>
 * </ul>
 *
 * <p>Both writers produce plain numeric whitespace rows and never a header.
 * Family by COLUMN COUNT with an optional name cross-check: a file named
 * {@code *.dope.vvdos} whose rows carry 2 columns (or vice-versa) is a
 * name/shape mismatch and refused, never re-labeled. Units are reported as
 * the writer expressions - no physics conversion is applied here (the Volt
 * scaling is already baked in upstream). Live doctrine: only the LAST row
 * may be a partial append (held back + counted); a ragged MID-file row is
 * BOLTZTRAP2_DOPEDOS_SHAPE, never skipped.</p>
 */
public final class BoltzTrap2DopeDosParser {

    /** Safety bound: rows beyond this are a dump, not a table. */
    private static final int MAX_ROWS = 2_000_000;
    /** 32 MiB - far beyond any sane dope.dos (NEDOS default 20000). */
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    private BoltzTrap2DopeDosParser() {
        // Utility
    }

    /** Which fork dump this file is (column-count certified). */
    public enum DopeDosKind {
        /** 2 columns: (E-Ef), dos*Volt. */
        DOS,
        /** 4 columns: (E-Ef), vvdos_11/22/33 *Volt. */
        VVDOS
    }

    /** The parsed table (verbatim cells; no rescaled physics). */
    public static final class DopeDosTable {
        private final DopeDosKind kind;
        private final double[] energy;
        private final List<double[]> channels;
        private final int rows;
        private final int partialTailHeld;
        private final List<String> notes;

        DopeDosTable(DopeDosKind kind, double[] energy, List<double[]> channels,
                int rows, int partialTailHeld, List<String> notes) {
            this.kind = kind;
            this.energy = energy;
            this.channels = channels;
            this.rows = rows;
            this.partialTailHeld = partialTailHeld;
            this.notes = notes;
        }

        public DopeDosKind getKind() { return this.kind; }
        /** Energy relative to the refined Fermi level (the writer's (e-fermi)/Volt). */
        public double[] getEnergy() { return this.energy.clone(); }
        /** One channel (dos*Volt) or three (vvdos diagonals *Volt). */
        public List<double[]> getChannels() {
            List<double[]> out = new ArrayList<>();
            for (double[] channel : this.channels) {
                out.add(channel.clone());
            }
            return out;
        }
        public int getRows() { return this.rows; }
        public int getPartialTailHeld() { return this.partialTailHeld; }
        public List<String> getNotes() { return this.notes; }
    }

    /** File-bound read (bounded), then the same positional grammar. */
    public static OperationResult<DopeDosTable> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_INPUT",
                    "no such file - pick a *.dope.dos or *.dope.vvdos table"
                            + " written by btp2 dope", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_INPUT",
                    file + ": cannot stat (" + ex.getMessage() + ")", null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_INPUT",
                    file.getFileName() + ": " + size + " bytes exceeds the 32 MiB"
                            + " sane bound for a dope table (NEDOS default 20000)"
                            + " - refusing rather than reading an unbounded dump", null);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_INPUT",
                    file + ": " + ex.getMessage(), null);
        }
        String name = file.getFileName() == null ? null : file.getFileName().toString();
        return parseText(text, name);
    }

    /** In-memory read (bounded by the caller); name may be null (cross-check skipped). */
    public static OperationResult<DopeDosTable> parseText(String text, String sourceName) {
        if (text == null) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_INPUT", "null text", null);
        }
        if (text.isBlank()) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_EMPTY",
                    (sourceName == null ? "table" : sourceName)
                            + ": empty - btp2 dope always writes rows immediately"
                            + " (the open(...,'w') empties the file first)", null);
        }
        String[] lines = text.split("\n", -1);
        List<Double> energy = new ArrayList<>();
        List<double[]> rows = new ArrayList<>();
        int width = -1;
        int held = 0;
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length != 2 && tokens.length != 4) {
                if (i == lastNonBlank(lines) && held == 0) {
                    held++; // only the LAST line may be a partial append
                    continue;
                }
                return OperationResult.failed("BOLTZTRAP2_DOPEDOS_SHAPE",
                        (sourceName == null ? "table" : sourceName) + ": line "
                                + (i + 1) + " carries " + tokens.length
                                + " cells - the writers emit exactly 2 (.dope.dos)"
                                + " or 4 (.dope.vvdos); a ragged mid-file row is"
                                + " corrupt, never skipped", null);
            }
            if (width < 0) {
                width = tokens.length;
                notes.add(width == 2
                        ? "family by column count: 2 columns = .dope.dos"
                                + " ((e-fermi)/Volt, dos*Volt)"
                        : "family by column count: 4 columns = .dope.vvdos"
                                + " ((e-fermi)/Volt, vvdos[0,0], vvdos[1,1],"
                                + " vvdos[2,2] *Volt)");
            } else if (tokens.length != width) {
                return OperationResult.failed("BOLTZTRAP2_DOPEDOS_SHAPE",
                        (sourceName == null ? "table" : sourceName) + ": line "
                                + (i + 1) + " has " + tokens.length + " cells but the"
                                + " family locked at " + width + " - uniform-width"
                                + " enforced (the writer never mixes shapes)", null);
            }
            double[] cells = new double[tokens.length];
            try {
                for (int c = 0; c < tokens.length; c++) {
                    cells[c] = Double.parseDouble(tokens[c]);
                }
            } catch (NumberFormatException ex) {
                return OperationResult.failed("BOLTZTRAP2_DOPEDOS_SHAPE",
                        (sourceName == null ? "table" : sourceName) + ": line "
                                + (i + 1) + " is not plain numeric text ('"
                                + trimmed + "')", null);
            }
            energy.add(cells[0]);
            double[] channel = new double[tokens.length - 1];
            System.arraycopy(cells, 1, channel, 0, channel.length);
            rows.add(channel);
            if (rows.size() > MAX_ROWS) {
                return OperationResult.failed("BOLTZTRAP2_DOPEDOS_SHAPE",
                        (sourceName == null ? "table" : sourceName) + ": beyond the"
                                + " sane row bound - refusing", null);
            }
        }
        if (rows.isEmpty()) {
            return OperationResult.failed("BOLTZTRAP2_DOPEDOS_EMPTY",
                    (sourceName == null ? "table" : sourceName)
                            + ": no numeric rows parsed", null);
        }
        DopeDosKind kind = width == 2 ? DopeDosKind.DOS : DopeDosKind.VVDOS;
        // name cross-check (only when a real name is supplied): refuse mismatch
        if (sourceName != null) {
            String base = sourceName.endsWith("_raw")
                    ? sourceName.substring(0, sourceName.length() - 4) : sourceName;
            if (base.endsWith(".dope.dos") && kind != DopeDosKind.DOS) {
                return OperationResult.failed("BOLTZTRAP2_DOPEDOS_SHAPE",
                        sourceName + ": named .dope.dos but rows carry " + width
                                + " columns - name/shape mismatch, never re-labeled", null);
            }
            if (base.endsWith(".dope.vvdos") && kind != DopeDosKind.VVDOS) {
                return OperationResult.failed("BOLTZTRAP2_DOPEDOS_SHAPE",
                        sourceName + ": named .dope.vvdos but rows carry " + width
                                + " columns - name/shape mismatch, never re-labeled", null);
            }
            if (sourceName.endsWith("_raw")) {
                notes.add("_raw suffix: the pre-remesh table that parse_dope renamed"
                        + " before dosremesh rewrote the refined one under the original"
                        + " name (os.rename, source-verbatim) - never merged with it");
            }
        }
        notes.add("energy column: (e - fermi)/Volt as written by"
                + " fp.write('{} {}\\n'.format((e-fermi)/Volt, ...)) - energy"
                + " relative to the refined Fermi level through the Volt unit (eV)");
        notes.add("channel column(s): *Volt-scaled exactly as written"
                + " (dos[i]*Volt / vvdos[ii,ii,i]*Volt) - no physics conversion"
                + " applied by this reader");
        if (held > 0) {
            notes.add("live write: " + held + " trailing partial row held back"
                    + " (btp2 dope rewrites this table at its dosremesh stage)");
        }
        double[] e = new double[energy.size()];
        for (int i = 0; i < e.length; i++) {
            e[i] = energy.get(i);
        }
        List<double[]> channels = new ArrayList<>();
        int channelCount = width - 1;
        for (int c = 0; c < channelCount; c++) {
            double[] channel = new double[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                channel[r] = rows.get(r)[c];
            }
            channels.add(channel);
        }
        return OperationResult.success("BOLTZTRAP2_DOPEDOS_OK",
                rows.size() + " rows (" + (width == 2 ? ".dope.dos" : ".dope.vvdos")
                        + (held > 0 ? "; " + held + " trailing partial held" : "") + ")",
                new DopeDosTable(kind, e, channels, rows.size(), held,
                        List.copyOf(notes)));
    }

    private static int lastNonBlank(String[] lines) {
        int last = lines.length - 1;
        while (last >= 0 && lines[last].trim().isEmpty()) {
            last--;
        }
        return last;
    }
}
