/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed PDB (Protein Data Bank) structure reader for REVIEW purposes
 * (Roadmap #78): whitespace-token parsing of ATOM/HETATM records in the
 * classic 11/12-token shape (record serial name resName chain resSeq x y z
 * [occ temp] [element]) plus the CRYST1 cell record. The reader invents
 * nothing: a missing element column stays missing (no name-based guessing -
 * calcium vs C-alpha would be the classic failure), partial occupancies are
 * counted and reported as unresolved disorder, multi-model files are refused
 * outright rather than silently taking model 1, and REMARK/TER/END content is
 * counted, never interpreted.
 */
public final class PdbStructureReader {

    /** Parse bound identical to the other bounded text readers. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /** Atom-record bound keeps memory honest. */
    public static final int MAX_ATOMS = 2_000_000;

    /** One parsed ATOM/HETATM record (Angstrom, as printed). */
    public static final class PdbAtom {
        private final int serial;
        private final String record;   // ATOM or HETATM
        private final String name;
        private final String residue;
        private final String element;  // "" when the element column is absent
        private final double x;
        private final double y;
        private final double z;
        private final double occupancy; // NaN when the occupancy column is absent

        PdbAtom(int serial, String record, String name, String residue, String element,
                double x, double y, double z, double occupancy) {
            this.serial = serial;
            this.record = record;
            this.name = name;
            this.residue = residue;
            this.element = element;
            this.x = x;
            this.y = y;
            this.z = z;
            this.occupancy = occupancy;
        }

        public int getSerial() { return this.serial; }
        public String getRecord() { return this.record; }
        public String getName() { return this.name; }
        public String getResidue() { return this.residue; }
        public String getElement() { return this.element; }
        public double getX() { return this.x; }
        public double getY() { return this.y; }
        public double getZ() { return this.z; }
        public double getOccupancy() { return this.occupancy; }
    }

    /** Parsed review bundle. */
    public static final class PdbStructure {
        private final List<PdbAtom> atoms;
        private final double[] cryst; // null when no CRYST1: a,b,c,alpha,beta,gamma
        private final long ignoredLines;
        private final int partialOccupancy;
        private final int missingElement;

        PdbStructure(List<PdbAtom> atoms, double[] cryst, long ignoredLines,
                int partialOccupancy, int missingElement) {
            this.atoms = List.copyOf(atoms);
            this.cryst = cryst;
            this.ignoredLines = ignoredLines;
            this.partialOccupancy = partialOccupancy;
            this.missingElement = missingElement;
        }

        public List<PdbAtom> getAtoms() { return this.atoms; }
        public double[] getCryst() { return this.cryst == null ? null : this.cryst.clone(); }
        public boolean hasCryst() { return this.cryst != null; }
        public long getIgnoredLines() { return this.ignoredLines; }
        public int getPartialOccupancyCount() { return this.partialOccupancy; }
        public int getMissingElementCount() { return this.missingElement; }

        /** Composition from the element column only (no guessing). */
        public Map<String, Integer> elementCounts() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (PdbAtom atom : this.atoms) {
                if (atom.getElement().isEmpty()) {
                    continue;
                }
                counts.merge(atom.getElement(), 1, Integer::sum);
            }
            return counts;
        }

        /** CRYST1 cell volume in Angstrom^3 (only when the record exists). */
        public double cellVolume() {
            if (this.cryst == null) {
                return Double.NaN;
            }
            double aa = Math.toRadians(this.cryst[3]);
            double bb = Math.toRadians(this.cryst[4]);
            double cc = Math.toRadians(this.cryst[5]);
            double term = 1.0 - Math.cos(aa) * Math.cos(aa) - Math.cos(bb) * Math.cos(bb)
                    - Math.cos(cc) * Math.cos(cc)
                    + 2.0 * Math.cos(aa) * Math.cos(bb) * Math.cos(cc);
            if (term <= 0.0) {
                return Double.NaN;
            }
            return this.cryst[0] * this.cryst[1] * this.cryst[2] * Math.sqrt(term);
        }
    }

    private PdbStructureReader() { }

    /**
     * Parses a PDB file. Codes: PDB_IO, PDB_TOO_LARGE, PDB_EMPTY, PDB_SYNTAX,
     * PDB_VALUE, PDB_MODEL, PDB_CRYST.
     */
    public static OperationResult<PdbStructure> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PDB_IO", "The PDB file does not exist.", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("PDB_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("PDB_TOO_LARGE",
                    "The file exceeds the 64 MiB parse bound.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return OperationResult.failed("PDB_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        java.util.List<PdbAtom> atoms = new java.util.ArrayList<>();
        double[] cryst = null;
        long ignored = 0L;
        int partial = 0;
        int missingElement = 0;
        for (String line : lines) {
            String[] tokens = line.trim().isEmpty() ? new String[0]
                    : line.trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                continue;
            }
            String record = tokens[0];
            if ("MODEL".equals(record)) {
                return OperationResult.failed("PDB_MODEL",
                        "This file contains MODEL records (a multi-model trajectory/NMR "
                                + "bundle). Multi-model files are refused rather than "
                                + "silently using one model; split them first.",
                        null);
            }
            if ("CRYST1".equals(record)) {
                if (tokens.length != 7) {
                    return OperationResult.failed("PDB_CRYST",
                            "CRYST1 needs 6 values (a b c alpha beta gamma): '" + line
                                    .trim() + "'.",
                            null);
                }
                cryst = new double[6];
                for (int i = 0; i < 6; i++) {
                    cryst[i] = parseDouble(tokens[i + 1]);
                }
                if (!Double.isFinite(cryst[0]) || !Double.isFinite(cryst[1])
                        || !Double.isFinite(cryst[2]) || cryst[0] <= 0.0
                        || cryst[1] <= 0.0 || cryst[2] <= 0.0) {
                    return OperationResult.failed("PDB_CRYST",
                            "CRYST1 lengths must be positive finite numbers.", null);
                }
                for (int i = 3; i < 6; i++) {
                    if (!Double.isFinite(cryst[i]) || cryst[i] <= 0.0
                            || cryst[i] >= 180.0) {
                        return OperationResult.failed("PDB_CRYST",
                                "CRYST1 angles must lie in (0, 180) degrees: '"
                                        + tokens[i + 1] + "'.",
                                null);
                    }
                }
                continue;
            }
            if (!"ATOM".equals(record) && !"HETATM".equals(record)) {
                ignored += 1;
                continue;
            }
            if (atoms.size() >= MAX_ATOMS) {
                return OperationResult.failed("PDB_EMPTY",
                        "The atom count exceeds the " + MAX_ATOMS + " reader bound.",
                        null);
            }
            if (tokens.length != 11 && tokens.length != 12) {
                return OperationResult.failed("PDB_SYNTAX",
                        "ATOM/HETATM records need 11 or 12 whitespace tokens (record "
                                + "serial name resName chain resSeq x y z occ temp "
                                + "[element]); found " + tokens.length + " in: '"
                                + abbreviate(line) + "'.",
                        null);
            }
            int serial = parseInteger(tokens[1]);
            double x = parseDouble(tokens[6]);
            double y = parseDouble(tokens[7]);
            double z = parseDouble(tokens[8]);
            double occ = parseDouble(tokens[9]);
            double temp = parseDouble(tokens[10]);
            if (serial == Integer.MIN_VALUE || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || !Double.isFinite(occ)
                    || !Double.isFinite(temp)) {
                return OperationResult.failed("PDB_VALUE",
                        "Non-numeric serial/coordinate/occupancy/temp factor in: '"
                                + abbreviate(line) + "'.",
                        null);
            }
            if (occ < 0.0 || occ > 1.0) {
                return OperationResult.failed("PDB_VALUE",
                        "Occupancy outside [0, 1] in: '" + abbreviate(line) + "'.", null);
            }
            String element = tokens.length == 12 ? tokens[11] : "";
            if (occ > 0.0 && occ < 1.0 - 1.0e-12) {
                partial += 1;
            }
            if (element.isEmpty()) {
                missingElement += 1;
            }
            atoms.add(new PdbAtom(serial, record, tokens[2], tokens[3], element,
                    x, y, z, occ));
        }
        if (atoms.isEmpty()) {
            return OperationResult.failed("PDB_EMPTY",
                    "No ATOM/HETATM records were found; nothing can be reviewed.", null);
        }
        return OperationResult.success("PDB_OK", "Parsed " + atoms.size() + " atoms.",
                new PdbStructure(atoms, cryst, ignored, partial, missingElement));
    }

    private static int parseInteger(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    private static double parseDouble(String token) {
        try {
            return Double.parseDouble(token.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String abbreviate(String line) {
        String trimmed = line.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "...";
    }
}
