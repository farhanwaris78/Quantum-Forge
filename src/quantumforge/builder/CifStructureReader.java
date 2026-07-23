/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * CIF 1.1 structure REVIEW reader (Roadmap #75 first slice) - a fail-closed,
 * line-based subset reader whose honesty rules are:
 *
 * <ul>
 *   <li>ONE data_ block per file (a second block refuses: silently reviewing
 *       the first of a multi-block file is the classic review failure);</li>
 *   <li>elements come from the loop's _atom_site_type_symbol column ONLY
 *       (it is the CIF-spec element carrier, stripped here of any oxidation
 *       suffix); label-only loops leave atoms ANONYMOUS - never guessed;</li>
 *   <li>uncertainties like 0.305(2) are stripped for parsing and COUNTED -
 *       never propagated (stated);</li>
 *   <li>partial occupancies are counted as unresolved disorder, not
 *       resolved; out-of-unit-cell fractional coordinates are counted, not
 *       wrapped;</li>
 *   <li>symmetry-operation loops are counted and the positions are flagged
 *       ASYMMETRIC-ONLY: NO symmetry expansion is applied;</li>
 *   <li>multi-line text frames (';' at column 1) are skipped honestly (their
 *       content is not reviewed); an unterminated frame is a syntax
 *       refusal.</li>
 * </ul>
 *
 * Full CIF 2.0 namespaces, dictionary validation and symmetry expansion are
 * the remaining #75 completeness work.
 */
public final class CifStructureReader {

    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_ATOMS = 500_000;

    private static final Pattern ELEMENT_TOKEN = Pattern.compile("^([A-Z][a-z]?)");
    private static final Pattern NUMBER_TOKEN =
            Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][+-]?\\d+)?");

    /** One reviewed atom (fractional coordinates as written). */
    public static final class CifAtom {
        private final String label;
        private final String element;   // null = anonymous
        private final double fx;
        private final double fy;
        private final double fz;
        private final double occupancy; // 1.0 when the column is absent
        private final boolean uncertaintyStripped;

        CifAtom(String label, String element, double fx, double fy, double fz,
                double occupancy, boolean uncertaintyStripped) {
            this.label = label;
            this.element = element;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.occupancy = occupancy;
            this.uncertaintyStripped = uncertaintyStripped;
        }

        public String getLabel() { return this.label; }
        public String getElement() { return this.element; }
        public double getFx() { return this.fx; }
        public double getFy() { return this.fy; }
        public double getFz() { return this.fz; }
        public double getOccupancy() { return this.occupancy; }
        public boolean isUncertaintyStripped() { return this.uncertaintyStripped; }
    }

    /** The reviewed subset of one data block. */
    public static final class CifStructure {
        private final String blockName;
        private final String chemicalFormula;  // verbatim or null
        private final String spaceGroupName;   // verbatim or null
        private final double[] cell;           // a,b,c,alpha,beta,gamma or null
        private final int symmetryOpRows;
        private final int skippedLoops;
        private final int uncertaintyStripCount;
        private final int partialOccupancyCount;
        private final int anonymousCount;
        private final int outOfCellCount;
        private final List<CifAtom> atoms;

        CifStructure(Builder builder) {
            this.blockName = builder.blockName;
            this.chemicalFormula = builder.chemicalFormula;
            this.spaceGroupName = builder.spaceGroupName;
            this.cell = builder.cell;
            this.symmetryOpRows = builder.symmetryOpRows;
            this.skippedLoops = builder.skippedLoops;
            this.uncertaintyStripCount = builder.uncertaintyStripCount;
            this.partialOccupancyCount = builder.partialOccupancyCount;
            this.anonymousCount = builder.anonymousCount;
            this.outOfCellCount = builder.outOfCellCount;
            this.atoms = builder.atoms;
        }

        public String getBlockName() { return this.blockName; }
        public String getChemicalFormula() { return this.chemicalFormula; }
        public String getSpaceGroupName() { return this.spaceGroupName; }
        public boolean hasCell() { return this.cell != null; }
        public double[] getCell() { return this.cell == null ? null : this.cell.clone(); }
        public int getSymmetryOpRows() { return this.symmetryOpRows; }
        public int getSkippedLoops() { return this.skippedLoops; }
        public List<CifAtom> getAtoms() { return List.copyOf(this.atoms); }
        public int getUncertaintyStripCount() { return this.uncertaintyStripCount; }
        public int getPartialOccupancyCount() { return this.partialOccupancyCount; }
        public int getAnonymousCount() { return this.anonymousCount; }
        public int getOutOfCellCount() { return this.outOfCellCount; }

        /** Triclinic cell volume from the general metric formula. */
        public double cellVolume() {
            double a = this.cell[0];
            double b = this.cell[1];
            double c = this.cell[2];
            double ca = Math.cos(Math.toRadians(this.cell[3]));
            double cb = Math.cos(Math.toRadians(this.cell[4]));
            double cg = Math.cos(Math.toRadians(this.cell[5]));
            return a * b * c * Math.sqrt(1.0 + 2.0 * ca * cb * cg - ca * ca - cb * cb
                    - cg * cg);
        }

        /** Element histogram from the type-symbol column only. */
        public Map<String, Integer> elementCounts() {
            Map<String, Integer> counts = new TreeMap<>();
            for (CifAtom atom : this.atoms) {
                if (atom.getElement() != null) {
                    counts.merge(atom.getElement(), 1, Integer::sum);
                }
            }
            return counts;
        }
    }

    private static final class Builder {
        String blockName;
        String chemicalFormula;
        String spaceGroupName;
        double[] cell;
        int symmetryOpRows;
        int skippedLoops;
        int uncertaintyStripCount;
        int partialOccupancyCount;
        int anonymousCount;
        int outOfCellCount;
        final List<CifAtom> atoms = new ArrayList<>();
    }

    private CifStructureReader() { }

    /**
     * Parses one CIF file. Codes: CIF_IO, CIF_TOO_LARGE, CIF_MULTIBLOCK,
     * CIF_SYNTAX, CIF_VALUE, CIF_CELL, CIF_EMPTY.
     */
    public static OperationResult<CifStructure> parse(Path path) {
        try {
            if (path == null || !Files.isRegularFile(path)) {
                return OperationResult.failed("CIF_IO", "No readable CIF file at: "
                        + path, null);
            }
            long size = Files.size(path);
            if (size > MAX_FILE_BYTES) {
                return OperationResult.failed("CIF_TOO_LARGE",
                        "The CIF file is " + size + " bytes, beyond the "
                                + MAX_FILE_BYTES + "-byte review bound.",
                        null);
            }
            return parseText(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return OperationResult.failed("CIF_IO", "Reading the CIF file failed: "
                    + ex.getMessage(), null);
        }
    }

    private static final class TokenizeException extends RuntimeException {
        private final int line;

        TokenizeException(int line, String what) {
            super(what);
            this.line = line;
        }
    }

    /** Visible for tests: parses from raw text with the same state machine. */
    static OperationResult<CifStructure> parseText(String text) {
        if (text == null || text.isBlank()) {
            return OperationResult.failed("CIF_EMPTY", "The CIF file is empty.", null);
        }
        Builder builder = new Builder();
        Map<String, String> scalars = new LinkedHashMap<>();
        String[] lines = text.split("\r\n|\n|\r");
        boolean inTextFrame = false;
        List<String> loopHeaders = null;
        List<String[]> loopRows = null;
        boolean coordLoop = false;
        boolean symopLoop = false;
        boolean rowMode = false;

        for (int raw = 0; raw < lines.length; raw++) {
            String line = lines[raw];
            int lineNo = raw + 1;
            if (line.startsWith(";")) {
                inTextFrame = !inTextFrame;
                continue;
            }
            if (inTextFrame || line.isBlank()) {
                continue;
            }
            List<String> tokens;
            try {
                tokens = tokenize(line, lineNo);
            } catch (TokenizeException ex) {
                return OperationResult.failed("CIF_SYNTAX",
                        "Line " + ex.line + ": " + ex.getMessage(), null);
            }
            if (tokens.isEmpty() || "#".equals(tokens.get(0))) {
                continue;
            }
            String first = tokens.get(0);
            String lowered = first.toLowerCase(Locale.ROOT);
            if (lowered.equals("loop_")) {
                String finished = finishLoop(builder, loopHeaders, loopRows, coordLoop,
                        symopLoop);
                if (finished != null) {
                    return OperationResult.failed("CIF_VALUE", finished
                            + " (loop ending before line " + lineNo + ")", null);
                }
                loopHeaders = new ArrayList<>();
                loopRows = new ArrayList<>();
                coordLoop = false;
                symopLoop = false;
                rowMode = false;
                continue;
            }
            if (lowered.startsWith("data_")) {
                String finished = finishLoop(builder, loopHeaders, loopRows, coordLoop,
                        symopLoop);
                if (finished != null) {
                    return OperationResult.failed("CIF_VALUE", finished
                            + " (before data_ at line " + lineNo + ")", null);
                }
                loopHeaders = null;
                loopRows = null;
                if (builder.blockName != null) {
                    return OperationResult.failed("CIF_MULTIBLOCK",
                            "A second data_ block '" + first + "' starts at line "
                                    + lineNo + "; split multi-block CIFs first.",
                            null);
                }
                builder.blockName = first.substring("data_".length());
                if (builder.blockName.isBlank()) {
                    return OperationResult.failed("CIF_SYNTAX",
                            "A data_ block with an empty name at line " + lineNo + ".",
                            null);
                }
                continue;
            }
            if (lowered.startsWith("save_") || lowered.startsWith("stop_")) {
                String finished = finishLoop(builder, loopHeaders, loopRows, coordLoop,
                        symopLoop);
                if (finished != null) {
                    return OperationResult.failed("CIF_VALUE", finished
                            + " (loop ending before line " + lineNo + ")", null);
                }
                loopHeaders = null;
                loopRows = null;
                continue;
            }
            if (first.startsWith("_")) {
                if (loopHeaders != null && !rowMode) {
                    loopHeaders.add(first);
                    if (first.equalsIgnoreCase("_atom_site_fract_x")) {
                        coordLoop = true;
                    }
                    if (lowered.startsWith("_symmetry_equiv_pos")
                            || lowered.startsWith("_space_group_symop")) {
                        symopLoop = true;
                    }
                    continue;
                }
                String finished = finishLoop(builder, loopHeaders, loopRows, coordLoop,
                        symopLoop);
                if (finished != null) {
                    return OperationResult.failed("CIF_VALUE", finished
                            + " (loop ending before line " + lineNo + ")", null);
                }
                loopHeaders = null;
                loopRows = null;
                rowMode = false;
                coordLoop = false;
                symopLoop = false;
                if (tokens.size() < 2) {
                    return OperationResult.failed("CIF_SYNTAX",
                            "Tag " + first + " at line " + lineNo + " has no value.",
                            null);
                }
                scalars.putIfAbsent(lowered,
                        String.join(" ", tokens.subList(1, tokens.size())));
                continue;
            }
            // Bare-token line: must be a loop row.
            if (loopHeaders == null || loopHeaders.isEmpty()) {
                return OperationResult.failed("CIF_SYNTAX",
                        "Stray content at line " + lineNo + " outside any loop/tag: '"
                                + abbreviate(line) + "'.",
                        null);
            }
            rowMode = true;
            if (tokens.size() != loopHeaders.size()) {
                return OperationResult.failed("CIF_SYNTAX",
                        "Row at line " + lineNo + " has " + tokens.size()
                                + " columns but the loop declares " + loopHeaders.size()
                                + " headers.",
                        null);
            }
            loopRows.add(tokens.toArray(new String[0]));
        }
        if (inTextFrame) {
            return OperationResult.failed("CIF_SYNTAX",
                    "Unterminated semicolon text frame at end of file.", null);
        }
        String finished = finishLoop(builder, loopHeaders, loopRows, coordLoop,
                symopLoop);
        if (finished != null) {
            return OperationResult.failed("CIF_VALUE", finished + " (final loop)",
                    null);
        }
        if (builder.blockName == null) {
            return OperationResult.failed("CIF_SYNTAX",
                    "No data_ block found - this is not a CIF structure file.", null);
        }
        String cellError = readCell(builder, scalars);
        if (cellError != null) {
            return OperationResult.failed("CIF_CELL", cellError, null);
        }
        builder.chemicalFormula = scalars.get("_chemical_formula_sum");
        builder.spaceGroupName = scalars.get("_symmetry_space_group_name_h-m");
        if (builder.atoms.isEmpty()) {
            return OperationResult.failed("CIF_EMPTY",
                    "No _atom_site coordinate loop was found; nothing to review.", null);
        }
        return OperationResult.success("CIF_OK", "CIF reviewed.",
                new CifStructure(builder));
    }

    /** Reads and validates the six cell tags (all six or none). */
    private static String readCell(Builder builder, Map<String, String> scalars) {
        String[] raw = {scalars.get("_cell_length_a"), scalars.get("_cell_length_b"),
                scalars.get("_cell_length_c"), scalars.get("_cell_angle_alpha"),
                scalars.get("_cell_angle_beta"), scalars.get("_cell_angle_gamma")};
        int present = 0;
        for (String value : raw) {
            if (value != null) {
                present += 1;
            }
        }
        if (present == 0) {
            return null;
        }
        if (present != 6) {
            return "The cell tag set is incomplete (" + present + " of 6); give all six "
                    + "or none.";
        }
        double[] cell = new double[6];
        for (int i = 0; i < 6; i++) {
            ParsedNumber number = parseNumber(raw[i]);
            if (number == null) {
                return "Unparsable cell tag value: '" + raw[i] + "'.";
            }
            cell[i] = number.value;
            builder.uncertaintyStripCount += number.stripped ? 1 : 0;
        }
        if (cell[0] <= 0.0 || cell[1] <= 0.0 || cell[2] <= 0.0) {
            return "Cell lengths must be positive.";
        }
        if (cell[3] <= 0.0 || cell[3] >= 180.0 || cell[4] <= 0.0 || cell[4] >= 180.0
                || cell[5] <= 0.0 || cell[5] >= 180.0) {
            return "Cell angles must lie in (0, 180) degrees.";
        }
        builder.cell = cell;
        return null;
    }

    /** Computes loop contributions; returns null on success, or the failure text. */
    private static String finishLoop(Builder builder, List<String> headers,
            List<String[]> rows, boolean coordLoop, boolean symopLoop) {
        if (headers == null) {
            return null;
        }
        if (symopLoop && rows != null) {
            builder.symmetryOpRows += rows.size();
        }
        if (!coordLoop) {
            if (rows != null && !rows.isEmpty() && !symopLoop) {
                builder.skippedLoops += 1;
            }
            return null;
        }
        int ix = indexOfIgnoreCase(headers, "_atom_site_fract_x");
        int iy = indexOfIgnoreCase(headers, "_atom_site_fract_y");
        int iz = indexOfIgnoreCase(headers, "_atom_site_fract_z");
        if (iy < 0 || iz < 0) {
            return "The _atom_site loop has fract_x but is missing fract_y/fract_z "
                    + "headers.";
        }
        int iLabel = indexOfIgnoreCase(headers, "_atom_site_label");
        int iType = indexOfIgnoreCase(headers, "_atom_site_type_symbol");
        int iOcc = indexOfIgnoreCase(headers, "_atom_site_occupancy");
        if (rows == null) {
            return null;
        }
        for (String[] row : rows) {
            if (builder.atoms.size() >= MAX_ATOMS) {
                return "The coordinate loop exceeds the " + MAX_ATOMS
                        + "-atom review bound.";
            }
            ParsedNumber x = parseNumber(row[ix]);
            ParsedNumber y = parseNumber(row[iy]);
            ParsedNumber z = parseNumber(row[iz]);
            if (x == null || y == null || z == null) {
                return "Unparsable fractional coordinate in row '"
                        + String.join(" ", row) + "'.";
            }
            double occupancy = 1.0;
            if (iOcc >= 0) {
                ParsedNumber occ = parseNumber(row[iOcc]);
                if (occ == null || occ.value <= 0.0 || occ.value > 1.0) {
                    return "Occupancy outside (0, 1] in row '" + String.join(" ", row)
                            + "'.";
                }
                occupancy = occ.value;
                if (occ.value < 1.0) {
                    builder.partialOccupancyCount += 1;
                }
            }
            String element = null;
            if (iType >= 0) {
                Matcher matcher = ELEMENT_TOKEN.matcher(row[iType]);
                if (matcher.find()) {
                    element = matcher.group(1);
                }
            }
            if (element == null) {
                builder.anonymousCount += 1;
            }
            if (Math.abs(x.value) > 1.0 || Math.abs(y.value) > 1.0
                    || Math.abs(z.value) > 1.0) {
                builder.outOfCellCount += 1;
            }
            boolean stripped = x.stripped || y.stripped || z.stripped;
            if (stripped) {
                builder.uncertaintyStripCount += 1;
            }
            builder.atoms.add(new CifAtom(iLabel >= 0 ? row[iLabel] : "?", element,
                    x.value, y.value, z.value, occupancy, stripped));
        }
        return null;
    }

    private static int indexOfIgnoreCase(List<String> headers, String name) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String abbreviate(String line) {
        String trimmed = line.trim();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "..." : trimmed;
    }

    /** Quote-aware whitespace tokenizer honouring CIF single/double quotes. */
    static List<String> tokenize(String line, int lineNo) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '#') {
                if (tokens.isEmpty()) {
                    tokens.add("#");
                }
                break; // comment start (only after whitespace or at line start)
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                int j = i + 1;
                StringBuilder value = new StringBuilder();
                while (j < line.length() && line.charAt(j) != quote) {
                    value.append(line.charAt(j));
                    j++;
                }
                if (j >= line.length()) {
                    throw new TokenizeException(lineNo, "unterminated quoted string");
                }
                tokens.add(value.toString());
                i = j + 1;
                continue;
            }
            int j = i;
            while (j < line.length() && !Character.isWhitespace(line.charAt(j))
                    && line.charAt(j) != '#') {
                j++;
            }
            tokens.add(line.substring(i, j));
            i = j;
        }
        return tokens;
    }

    /** A parsed number; an (n) uncertainty suffix is stripped and flagged. */
    static final class ParsedNumber {
        final double value;
        final boolean stripped;

        ParsedNumber(double value, boolean stripped) {
            this.value = value;
            this.stripped = stripped;
        }
    }

    static ParsedNumber parseNumber(String token) {
        if (token == null) {
            return null;
        }
        String work = token.trim();
        boolean stripped = false;
        int paren = work.indexOf('(');
        if (paren >= 0) {
            if (!work.endsWith(")") || work.indexOf('(', paren + 1) >= 0) {
                return null;
            }
            if (!work.substring(paren + 1, work.length() - 1).matches("\\d+")) {
                return null;
            }
            work = work.substring(0, paren);
            stripped = true;
        }
        if (!NUMBER_TOKEN.matcher(work).matches()) {
            return null;
        }
        try {
            return new ParsedNumber(
                    Double.parseDouble(work.replace('D', 'E').replace('d', 'E')),
                    stripped);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
