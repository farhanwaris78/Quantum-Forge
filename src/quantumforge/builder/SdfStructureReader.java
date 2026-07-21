/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #78 (partial): fail-closed MDL MOL/SDF V2000 structure REVIEW.
 *
 * <p>This reader owns a deliberately small, honest subset of the CTfile
 * family:</p>
 * <ul>
 *   <li>exactly ONE record per file - an SDF data bundle with a {@code $$$$}
 *       record separator is REFUSED (SDF_MULTIRECORD) rather than silently
 *       reviewing only the first molecule and hiding the rest;</li>
 *   <li>V2000 counts/atom/bond blocks parsed FIXED-WIDTH (columns 1-3 atoms,
 *       4-6 bonds; atom line x/y/z in 10-wide 4-decimal fields, element
 *       symbol in columns 32-34); line 3+ content beyond the owned columns is
 *       NEVER reinterpreted;</li>
 *   <li>V3000 files are REFUSED (SDF_V3000) - the extended syntax is a
 *       different grammar, not a parse error here;</li>
 *   <li>the element symbol column IS the element carrier: query/pseudo atoms
 *       (A, Q, L, LP, *, R, R#) are counted and kept element-null - they are
 *       NEVER guessed into elements;</li>
 *   <li>bond types are echoed and counted (1/2/3/4 per spec) but NO
 *       aromaticity, stereochemistry, valence or charge chemistry is
 *       perceived; the stereo flag column is NOT interpreted;</li>
 *   <li>{@code M  CHG} property entries are counted and their integer sum is
 *       reported as the declared formal-charge sum; other property lines are
 *       counted, not interpreted.</li>
 * </ul>
 *
 * <p>Refusal codes: SDF_IO, SDF_TOO_LARGE, SDF_MULTIRECORD, SDF_V3000,
 * SDF_SYNTAX, SDF_COUNTS, SDF_VALUE, SDF_BONDS, SDF_EMPTY.</p>
 */
public final class SdfStructureReader {

    /** Maximum file size the review will read. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    /** Owned pseudo-atom labels that must never become elements. */
    private static final List<String> PSEUDO_LABELS =
            List.of("A", "Q", "L", "LP", "*", "R", "R#");

    /** One parsed atom row from the V2000 atom block. */
    public static final class SdfAtom {
        private final int index;
        private final String element;
        private final boolean pseudoAtom;
        private final double x;
        private final double y;
        private final double z;

        SdfAtom(int index, String element, boolean pseudoAtom,
                double x, double y, double z) {
            this.index = index;
            this.element = element;
            this.pseudoAtom = pseudoAtom;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getIndex() { return this.index; }
        /** Element symbol, or {@code null} for pseudo/query atoms. */
        public String getElement() { return this.element; }
        public boolean isPseudoAtom() { return this.pseudoAtom; }
        public double getX() { return this.x; }
        public double getY() { return this.y; }
        public double getZ() { return this.z; }
    }

    /** One parsed bond row: 1-based atom indices plus the verbatim type. */
    public static final class SdfBond {
        private final int first;
        private final int second;
        private final int type;

        SdfBond(int first, int second, int type) {
            this.first = first;
            this.second = second;
            this.type = type;
        }

        public int getFirst() { return this.first; }
        public int getSecond() { return this.second; }
        /** Bond type code from the file (1,2,3,4...); NOT chemistry-checked. */
        public int getType() { return this.type; }
    }

    /** Reviewed single-record molecule. */
    public static final class SdfStructure {
        private final String title;
        private final boolean versionMarkerPresent;
        private final List<SdfAtom> atoms;
        private final List<SdfBond> bonds;
        private final int chargedAtoms;
        private final int chargeSum;
        private final int propertyLines;
        private final int dataFieldCount;

        SdfStructure(String title, boolean versionMarkerPresent, List<SdfAtom> atoms,
                List<SdfBond> bonds, int chargedAtoms, int chargeSum,
                int propertyLines, int dataFieldCount) {
            this.title = title;
            this.versionMarkerPresent = versionMarkerPresent;
            this.atoms = new ArrayList<>(atoms);
            this.bonds = new ArrayList<>(bonds);
            this.chargedAtoms = chargedAtoms;
            this.chargeSum = chargeSum;
            this.propertyLines = propertyLines;
            this.dataFieldCount = dataFieldCount;
        }

        /** Line 1 of the record, verbatim (may be empty). */
        public String getTitle() { return this.title; }
        /** True when a "V2000" marker was present on the counts line. */
        public boolean hasVersionMarker() { return this.versionMarkerPresent; }
        public List<SdfAtom> getAtoms() { return List.copyOf(this.atoms); }
        public List<SdfBond> getBonds() { return List.copyOf(this.bonds); }
        /** Atoms with a {@code M  CHG} entry. */
        public int getChargedAtoms() { return this.chargedAtoms; }
        /** Sum of the declared {@code M  CHG} formal charges. */
        public int getChargeSum() { return this.chargeSum; }
        /** Property-block lines seen (including {@code M  END}). */
        public int getPropertyLines() { return this.propertyLines; }
        /** SDF {@code > <FIELD>} data headers after {@code M  END}. */
        public int getDataFieldCount() { return this.dataFieldCount; }

        /** Pseudo/query atoms whose element must never be guessed. */
        public int getPseudoAtomCount() {
            int count = 0;
            for (SdfAtom atom : this.atoms) {
                if (atom.isPseudoAtom()) {
                    count += 1;
                }
            }
            return count;
        }

        /** Composition from the element column ONLY; pseudo atoms excluded. */
        public Map<String, Integer> elementCounts() {
            Map<String, Integer> counts = new TreeMap<>();
            for (SdfAtom atom : this.atoms) {
                if (atom.getElement() != null) {
                    counts.merge(atom.getElement(), 1, Integer::sum);
                }
            }
            return counts;
        }

        /** Bond-type histogram keyed by the verbatim type code. */
        public Map<Integer, Integer> bondTypeCounts() {
            Map<Integer, Integer> counts = new TreeMap<>();
            for (SdfBond bond : this.bonds) {
                counts.merge(bond.getType(), 1, Integer::sum);
            }
            return counts;
        }
    }

    private SdfStructureReader() {
    }

    /**
     * Reads two integers: first from the fixed V2000 columns 1-3 and 4-6 of
     * the RAW line (leading spaces carry field alignment), falling back to
     * two leading whitespace-separated tokens for non-padded writers.
     */
    private static int[] readIntPair(String raw) {
        if (raw.length() >= 6 && raw.charAt(0) == ' ') {
            try {
                return new int[]{Integer.parseInt(raw.substring(0, 3).trim()),
                        Integer.parseInt(raw.substring(3, 6).trim())};
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Reads three integers: first from the fixed V2000 columns 1-3, 4-6 and
     * 7-9 of the RAW line, falling back to three leading whitespace-separated
     * tokens for non-padded writers.
     */
    private static int[] readIntTriple(String raw) {
        if (raw.length() >= 9 && raw.charAt(0) == ' ') {
            try {
                return new int[]{Integer.parseInt(raw.substring(0, 3).trim()),
                        Integer.parseInt(raw.substring(3, 6).trim()),
                        Integer.parseInt(raw.substring(6, 9).trim())};
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length < 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]),
                    Integer.parseInt(tokens[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Parses a MOL/SDF file. Codes: SDF_IO, SDF_TOO_LARGE, SDF_MULTIRECORD,
     * SDF_V3000, SDF_SYNTAX, SDF_COUNTS, SDF_VALUE, SDF_BONDS, SDF_EMPTY.
     */
    public static OperationResult<SdfStructure> parse(Path file) {
        if (file == null || !Files.exists(file)) {
            return OperationResult.failed("SDF_IO", "The MOL/SDF file does not exist.", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("SDF_IO",
                    "Could not stat the MOL/SDF file: " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("SDF_TOO_LARGE",
                    "The MOL/SDF file is " + size + " bytes; the review cap is "
                            + MAX_FILE_BYTES + " bytes. Nothing was parsed.",
                    null);
        }
        try {
            return parseText(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return OperationResult.failed("SDF_IO",
                    "Reading the MOL/SDF file failed: " + ex.getMessage(), null);
        }
    }

    /** Package-visible parser for tests; same codes as {@link #parse(Path)}. */
    static OperationResult<SdfStructure> parseText(String text) {
        if (text == null || text.isBlank()) {
            return OperationResult.failed("SDF_EMPTY", "The MOL/SDF file is empty.", null);
        }
        String[] lines = text.split("\n", -1);
        int separators = 0;
        for (String line : lines) {
            if (line.strip().equals("$$$$")) {
                separators += 1;
            }
        }
        if (separators > 0) {
            return OperationResult.failed("SDF_MULTIRECORD",
                    "The file contains " + separators + " '$$$$' record separator(s): "
                            + "it is an SDF data bundle, not a single molecule. The "
                            + "review REFUSES to pick a record silently - split the "
                            + "bundle with a cheminformatics toolkit and review one "
                            + "record at a time.",
                    null);
        }
        // A MOL record has exactly 4 header fields (title / program / comment / counts)
        // and the comment line is LEGITIMATELY blank, so the gate counts PHYSICAL lines:
        // a blank-comment V3000 record must reach the V3000 refusal below, not be
        // mis-rejected as generic syntax. Terminal split artefacts (the empty strings a
        // trailing newline produces) are not record lines.
        int physical = lines.length;
        while (physical > 0 && lines[physical - 1].isEmpty()) {
            physical--;
        }
        if (physical < 4) {
            return OperationResult.failed("SDF_SYNTAX",
                    "A MOL record needs 3 header lines plus a counts line; only "
                            + physical + " line(s) were found.",
                    null);
        }
        // Header lines 1-3 are verbatim-only (title / program / comment).
        int headerEnd = 0;
        int seen = 0;
        while (headerEnd < lines.length && seen < 3) {
            seen += 1;
            headerEnd += 1;
        }
        String title = lines[0].strip();
        String countsRaw = lines[headerEnd];
        if (countsRaw.contains("V3000")) {
            return OperationResult.failed("SDF_V3000",
                    "This record declares V3000 syntax. The review owns the V2000 "
                            + "fixed-width subset only; convert/export as V2000 with a "
                            + "cheminformatics toolkit before reviewing.",
                    null);
        }
        boolean markerPresent = countsRaw.contains("V2000");
        int[] counts = readIntPair(countsRaw);
        if (counts == null) {
            return OperationResult.failed("SDF_COUNTS",
                    "The counts line '" + countsRaw + "' does not carry integer "
                            + "atom/bond counts (fixed-width V2000 columns 1-6 or "
                            + "leading whitespace-separated tokens).",
                    null);
        }
        int atomCount = counts[0];
        int bondCount = counts[1];
        if (atomCount <= 0) {
            return OperationResult.failed("SDF_EMPTY",
                    "The counts line declares " + atomCount + " atoms; there is no "
                            + "molecule to review.",
                    null);
        }
        if (bondCount < 0) {
            return OperationResult.failed("SDF_COUNTS",
                    "The counts line declares a negative bond count " + bondCount + ".",
                    null);
        }
        int cursor = headerEnd + 1;
        List<SdfAtom> atoms = new ArrayList<>();
        for (int idx = 1; idx <= atomCount; idx += 1) {
            if (cursor >= lines.length) {
                return OperationResult.failed("SDF_SYNTAX",
                        "The counts line declares " + atomCount + " atoms but the "
                                + "atom block ends after " + atoms.size() + " line(s).",
                        null);
            }
            String atomLine = lines[cursor];
            cursor += 1;
            double x;
            double y;
            double z;
            String symbol;
            if (atomLine.length() >= 34 && atomLine.charAt(0) == ' ') {
                // canonical 10.4f fixed-width layout; alignment is load-bearing
                try {
                    x = Double.parseDouble(atomLine.substring(0, 10).trim());
                    y = Double.parseDouble(atomLine.substring(10, 20).trim());
                    z = Double.parseDouble(atomLine.substring(20, 30).trim());
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("SDF_VALUE",
                            "Atom line " + idx + " carries a non-numeric coordinate: '"
                                    + atomLine + "'.",
                            null);
                }
                symbol = atomLine.substring(31, 34).trim();
            } else {
                // non-padded writer: x y z symbol as leading tokens
                String[] tokens = atomLine.trim().split("\\s+");
                if (tokens.length < 4) {
                    return OperationResult.failed("SDF_SYNTAX",
                            "Atom line " + idx + " is neither the fixed 34-column "
                                    + "V2000 atom-row layout nor 'x y z symbol' tokens: '"
                                    + atomLine + "'.",
                            null);
                }
                try {
                    x = Double.parseDouble(tokens[0]);
                    y = Double.parseDouble(tokens[1]);
                    z = Double.parseDouble(tokens[2]);
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("SDF_VALUE",
                            "Atom line " + idx + " carries a non-numeric coordinate: '"
                                    + atomLine + "'.",
                            null);
                }
                symbol = tokens[3];
            }
            boolean pseudo = PSEUDO_LABELS.contains(symbol);
            String element = null;
            if (!pseudo) {
                if (!symbol.matches("[A-Z][a-z]{0,2}")) {
                    return OperationResult.failed("SDF_VALUE",
                            "Atom line " + idx + " carries the element symbol '"
                                    + symbol + "' which is neither an element-style "
                                    + "symbol nor an owned pseudo-atom label (A, Q, L, "
                                    + "LP, *, R, R#). It is NOT guessed.",
                            null);
                }
                element = symbol;
            }
            atoms.add(new SdfAtom(idx, element, pseudo, x, y, z));
        }
        List<SdfBond> bonds = new ArrayList<>();
        for (int idx = 1; idx <= bondCount; idx += 1) {
            if (cursor >= lines.length) {
                return OperationResult.failed("SDF_SYNTAX",
                        "The counts line declares " + bondCount + " bonds but the "
                                + "bond block ends after " + bonds.size() + " line(s).",
                        null);
            }
            String bondLine = lines[cursor];
            cursor += 1;
            int[] fields = readIntTriple(bondLine);
            if (fields == null) {
                return OperationResult.failed("SDF_BONDS",
                        "Bond line " + idx + " does not carry integer atom indices "
                                + "and bond type (fixed-width V2000 columns 1-9 or "
                                + "leading whitespace-separated tokens): '" + bondLine
                                + "'.",
                        null);
            }
            int first = fields[0];
            int second = fields[1];
            int type = fields[2];
            if (first < 1 || first > atomCount || second < 1 || second > atomCount) {
                return OperationResult.failed("SDF_BONDS",
                        "Bond line " + idx + " references atoms " + first + " and "
                                + second + " outside 1.." + atomCount + ".",
                        null);
            }
            bonds.add(new SdfBond(first, second, type));
        }
        boolean endSeen = false;
        int chargedAtoms = 0;
        int chargeSum = 0;
        int propertyLines = 0;
        while (cursor < lines.length) {
            // the property block has no fixed-width columns, so trimming is safe
            String property = lines[cursor].trim();
            cursor += 1;
            if (property.isEmpty()) {
                continue;
            }
            propertyLines += 1;
            if (property.startsWith("M  END")) {
                endSeen = true;
                break;
            }
            if (property.startsWith("M  CHG")) {
                String[] tokens = property.trim().split("\\s+");
                if (tokens.length < 4) {
                    return OperationResult.failed("SDF_VALUE",
                            "The M  CHG line '" + property + "' declares no pairs.",
                            null);
                }
                int pairs;
                try {
                    pairs = Integer.parseInt(tokens[2]);
                } catch (NumberFormatException ex) {
                    return OperationResult.failed("SDF_VALUE",
                            "The M  CHG line '" + property + "' has a non-integer "
                                    + "pair count.",
                            null);
                }
                if (tokens.length != 3 + pairs * 2) {
                    return OperationResult.failed("SDF_VALUE",
                            "The M  CHG line declares " + pairs + " pair(s) but "
                                    + "carries " + (tokens.length - 3) + " fields.",
                            null);
                }
                for (int pair = 0; pair < pairs; pair += 1) {
                    int atomIndex;
                    int charge;
                    try {
                        atomIndex = Integer.parseInt(tokens[3 + pair * 2]);
                        charge = Integer.parseInt(tokens[4 + pair * 2]);
                    } catch (NumberFormatException ex) {
                        return OperationResult.failed("SDF_VALUE",
                                "The M  CHG line '" + property + "' carries a "
                                        + "non-integer atom index or charge.",
                                null);
                    }
                    if (atomIndex < 1 || atomIndex > atomCount) {
                        return OperationResult.failed("SDF_BONDS",
                                "The M  CHG line references atom " + atomIndex
                                        + " outside 1.." + atomCount + ".",
                                null);
                    }
                    chargedAtoms += 1;
                    chargeSum += charge;
                }
            }
            // every other property line is counted, never interpreted
        }
        if (!endSeen) {
            return OperationResult.failed("SDF_SYNTAX",
                    "The record never reaches its 'M  END' terminator; the "
                            + "property block is not truncated silently.",
                    null);
        }
        int dataFields = 0;
        for (int idx = cursor; idx < lines.length; idx += 1) {
            String rest = lines[idx].trim();
            if (rest.startsWith(">")) {
                dataFields += 1;
            }
        }
        return OperationResult.success("SDF_OK", "Reviewed " + atoms.size() + " atoms.",
                new SdfStructure(title, markerPresent, atoms, bonds, chargedAtoms,
                        chargeSum, propertyLines, dataFields));
    }
}
