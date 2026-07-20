/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed LAMMPS data-file reader for REVIEW purposes (Roadmap #113
 * layer): header counts, box bounds (with optional tilt), and the Masses /
 * Atoms sections. The load-bearing honesty rule: a LAMMPS data file does NOT
 * record its atom_style - the style lives in the input script - so this
 * reader NEVER column-guesses. The style is an explicit caller parameter with
 * documented token shapes, and everything else in the file (Velocities,
 * Bonds, Angles, Pair Coeffs, ...) is skipped by name. Element inference from
 * masses is NOT done: types stay numbers and masses print verbatim.
 */
public final class LammpsDataReader {

    /** Parse bound identical to the other bounded text readers. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /** Atom bound keeps memory honest. */
    public static final int MAX_ATOMS = 2_000_000;

    /** Supported atom styles with their column shapes. */
    public enum AtomStyle {
        /** id type x y z (+ optional 3 image flags). */
        ATOMIC,
        /** id type q x y z (+ optional 3 image flags). */
        CHARGE,
        /** id molecule type x y z (+ optional 3 image flags). */
        MOLECULAR,
        /** id molecule type q x y z (+ optional 3 image flags). */
        FULL
    }

    /** One atom row; q/molecule are NaN/-1 when the style lacks them. */
    public static final class LammpsAtom {
        private final int id;
        private final int molecule;
        private final int type;
        private final double charge;
        private final double x;
        private final double y;
        private final double z;

        LammpsAtom(int id, int molecule, int type, double charge,
                double x, double y, double z) {
            this.id = id;
            this.molecule = molecule;
            this.type = type;
            this.charge = charge;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getId() { return this.id; }
        public int getMolecule() { return this.molecule; }
        public int getType() { return this.type; }
        public double getCharge() { return this.charge; }
        public double getX() { return this.x; }
        public double getY() { return this.y; }
        public double getZ() { return this.z; }
    }

    /** Parsed review bundle. */
    public static final class LammpsData {
        private final String title;
        private final int atomCount;
        private final int typeCount;
        private final double[] boxLengths; // lx, ly, lz
        private final boolean tilted;
        private final Double[] masses;     // per type, null array when Masses absent
        private final List<LammpsAtom> atoms;
        private final AtomStyle style;
        private final long outsideBox;
        private final List<String> skippedSections;

        LammpsData(int atomCount, int typeCount, double[] boxLengths, boolean tilted,
                Double[] masses, List<LammpsAtom> atoms, AtomStyle style, long outsideBox,
                List<String> skippedSections) {
            this.title = "";
            this.atomCount = atomCount;
            this.typeCount = typeCount;
            this.boxLengths = boxLengths;
            this.tilted = tilted;
            this.masses = masses;
            this.atoms = List.copyOf(atoms);
            this.style = style;
            this.outsideBox = outsideBox;
            this.skippedSections = List.copyOf(skippedSections);
        }

        public String getTitle() { return this.title; }
        public int getAtomCount() { return this.atomCount; }
        public int getTypeCount() { return this.typeCount; }
        public double[] getBoxLengths() { return this.boxLengths.clone(); }
        public boolean isTilted() { return this.tilted; }
        public Double[] getMasses() { return this.masses == null ? null : this.masses.clone(); }
        public List<LammpsAtom> getAtoms() { return this.atoms; }
        public AtomStyle getStyle() { return this.style; }
        public long getOutsideBoxCount() { return this.outsideBox; }
        public List<String> getSkippedSections() { return this.skippedSections; }
    }

    private LammpsDataReader() { }

    private static String[] tokens(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        int comment = trimmed.indexOf('#');
        if (comment >= 0) {
            trimmed = trimmed.substring(0, comment).trim();
            if (trimmed.isEmpty()) {
                return new String[0];
            }
        }
        return trimmed.split("\\s+");
    }

    private static double parseDouble(String token) {
        try {
            return Double.parseDouble(token.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static long parseLong(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ex) {
            return Long.MIN_VALUE;
        }
    }

    /** True when the comment-stripped head is 1-2 words starting with a letter. */
    private static boolean isSectionHeader(String[] parts) {
        return parts.length >= 1 && parts.length <= 2
                && Character.isLetter(parts[0].charAt(0));
    }

    /**
     * Parses a LAMMPS data file with the EXPLICIT atom style (the file itself
     * never records it). Codes: LAMMPS_IO, LAMMPS_TOO_LARGE, LAMMPS_SYNTAX,
     * LAMMPS_VALUE, LAMMPS_COUNT, LAMMPS_STYLE, LAMMPS_SECTION, LAMMPS_EMPTY.
     */
    public static OperationResult<LammpsData> parse(Path file, AtomStyle style) {
        if (style == null) {
            return OperationResult.failed("LAMMPS_STYLE",
                    "The atom_style is mandatory - a data file does not record it and "
                            + "column-guessing would silently shift coordinates.",
                    null);
        }
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("LAMMPS_IO",
                    "The data file does not exist.", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("LAMMPS_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("LAMMPS_TOO_LARGE",
                    "The file exceeds the 64 MiB parse bound.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return OperationResult.failed("LAMMPS_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }

        long atomCount = -1L;
        long typeCount = -1L;
        double[] boxLo = new double[3];
        double[] boxHi = new double[3];
        boolean[] haveBox = new boolean[3];
        boolean tilted = false;
        Double[] masses = null;
        boolean massesSeen = false;
        List<LammpsAtom> atoms = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String section = "";
        boolean titleConsumed = false;
        Set<Long> seenIds = new HashSet<>();

        for (String line : lines) {
            String[] parts = tokens(line);
            if (parts.length == 0) {
                continue;
            }
            // LAMMPS mandates a comment/title first line; consume exactly one
            // letter-starting preamble line as the title so it is not mistaken
            // for a section header.
            if (!titleConsumed && section.isEmpty() && atomCount < 0 && typeCount < 0
                    && Character.isLetter(parts[0].charAt(0))) {
                titleConsumed = true;
                continue;
            }
            if (isSectionHeader(parts)) {
                String name = parts.length == 2 ? parts[0] + " " + parts[1] : parts[0];
                if ("Masses".equalsIgnoreCase(parts[0])) {
                    section = "Masses";
                    massesSeen = true;
                    if (masses == null) {
                        return OperationResult.failed("LAMMPS_SECTION",
                                "The Masses section arrived before the 'atom types' "
                                        + "count was known.",
                                null);
                    }
                } else if ("Atoms".equalsIgnoreCase(parts[0])) {
                    section = "Atoms";
                } else {
                    section = "__SKIP__";
                    if (!skipped.contains(name)) {
                        skipped.add(name);
                    }
                }
                continue;
            }
            if (section.isEmpty()) {
                if (parts.length == 2 && parts[1].equalsIgnoreCase("atoms")) {
                    atomCount = parseLong(parts[0]);
                    if (atomCount <= 0 || atomCount > MAX_ATOMS) {
                        return OperationResult.failed("LAMMPS_COUNT",
                                "The atom count '" + line.trim() + "' is not in 1.."
                                        + MAX_ATOMS + ".",
                                null);
                    }
                } else if (parts.length == 3 && parts[1].equalsIgnoreCase("atom")
                        && parts[2].equalsIgnoreCase("types")) {
                    typeCount = parseLong(parts[0]);
                    if (typeCount <= 0 || typeCount > 1_000_000L) {
                        return OperationResult.failed("LAMMPS_COUNT",
                                "The atom-type count '" + line.trim() + "' is malformed.",
                                null);
                    }
                    masses = new Double[(int) typeCount];
                } else if (parts.length == 4 && parts[3].equalsIgnoreCase("xhi")) {
                    if (!bounds(parts, boxLo, boxHi, 0)) {
                        return OperationResult.failed("LAMMPS_VALUE",
                                "Malformed xlo xhi line: '" + line.trim() + "'.", null);
                    }
                    haveBox[0] = true;
                } else if (parts.length == 4 && parts[3].equalsIgnoreCase("yhi")) {
                    if (!bounds(parts, boxLo, boxHi, 1)) {
                        return OperationResult.failed("LAMMPS_VALUE",
                                "Malformed ylo yhi line: '" + line.trim() + "'.", null);
                    }
                    haveBox[1] = true;
                } else if (parts.length == 4 && parts[3].equalsIgnoreCase("zhi")) {
                    if (!bounds(parts, boxLo, boxHi, 2)) {
                        return OperationResult.failed("LAMMPS_VALUE",
                                "Malformed zlo zhi line: '" + line.trim() + "'.", null);
                    }
                    haveBox[2] = true;
                } else if (parts.length == 6 && parts[3].equalsIgnoreCase("xy")) {
                    tilted = true;
                } else if (parts.length >= 2 && Character.isDigit(parts[0].charAt(0))) {
                    // Other count lines (bonds, angles, type counts): counted, unused.
                    if (parts[parts.length - 1].equalsIgnoreCase("types")) {
                        continue;
                    }
                    continue;
                }
                continue;
            }
            if ("Masses".equals(section)) {
                if (parts.length != 2) {
                    return OperationResult.failed("LAMMPS_SYNTAX",
                            "Masses rows hold exactly 'type mass': '" + abbreviate(line)
                                    + "'.",
                            null);
                }
                long type = parseLong(parts[0]);
                double mass = parseDouble(parts[1]);
                if (type < 1 || type > masses.length || !Double.isFinite(mass)
                        || mass <= 0.0) {
                    return OperationResult.failed("LAMMPS_VALUE",
                            "Malformed Masses row: '" + line.trim() + "'.", null);
                }
                masses[(int) type - 1] = mass;
                continue;
            }
            if ("Atoms".equals(section)) {
                int expected = switch (style) {
                case ATOMIC -> 5;
                case CHARGE, MOLECULAR -> 6;
                case FULL -> 7;
                };
                if (parts.length != expected && parts.length != expected + 3) {
                    return OperationResult.failed("LAMMPS_STYLE",
                            "atom_style '" + style + "' expects " + expected
                                    + " columns (+3 with image flags); found "
                                    + parts.length + " in: '" + abbreviate(line)
                                    + "'. If the column count looks like another style, "
                                    + "the STYLE parameter is wrong - the file does not "
                                    + "record it.",
                            null);
                }
                int offset = 0;
                long id = parseLong(parts[offset++]);
                long molecule = 1L;
                if (style == AtomStyle.MOLECULAR || style == AtomStyle.FULL) {
                    molecule = parseLong(parts[offset++]);
                }
                long type = parseLong(parts[offset++]);
                double charge = Double.NaN;
                if (style == AtomStyle.CHARGE || style == AtomStyle.FULL) {
                    charge = parseDouble(parts[offset++]);
                }
                double x = parseDouble(parts[offset++]);
                double y = parseDouble(parts[offset++]);
                double z = parseDouble(parts[offset]);
                if (id <= 0 || !seenIds.add(id) || type <= 0
                        || (typeCount > 0 && type > typeCount)
                        || molecule <= 0
                        || !Double.isFinite(x) || !Double.isFinite(y)
                        || !Double.isFinite(z)
                        || ((style == AtomStyle.CHARGE || style == AtomStyle.FULL)
                                && !Double.isFinite(charge))) {
                    return OperationResult.failed("LAMMPS_VALUE",
                            "Malformed/duplicate/out-of-range atom row: '"
                                    + abbreviate(line) + "'.",
                            null);
                }
                atoms.add(new LammpsAtom((int) id,
                        (style == AtomStyle.MOLECULAR || style == AtomStyle.FULL)
                                ? (int) molecule : -1,
                        (int) type, charge, x, y, z));
            }
            // Skipped-section bodies: counted by name, never interpreted.
        }

        if (atomCount <= 0) {
            return OperationResult.failed("LAMMPS_EMPTY",
                    "No 'N atoms' header line was found; this is not a LAMMPS data file "
                            + "in the supported grammar.",
                    null);
        }
        if (typeCount <= 0) {
            return OperationResult.failed("LAMMPS_EMPTY",
                    "No 'N atom types' header line was found.", null);
        }
        if (!haveBox[0] || !haveBox[1] || !haveBox[2]) {
            return OperationResult.failed("LAMMPS_VALUE",
                    "The box needs xlo/xhi, ylo/yhi and zlo/zhi lines; at least one is "
                            + "missing.",
                    null);
        }
        for (int axis = 0; axis < 3; axis++) {
            if (!(boxHi[axis] > boxLo[axis])) {
                return OperationResult.failed("LAMMPS_VALUE",
                        "The box high bound must exceed the low bound on every axis.",
                        null);
            }
        }
        if (atoms.isEmpty()) {
            return OperationResult.failed("LAMMPS_EMPTY",
                    "The Atoms section is missing or empty.", null);
        }
        if (atoms.size() != atomCount) {
            return OperationResult.failed("LAMMPS_COUNT",
                    "The header declares " + atomCount + " atoms but the Atoms section "
                            + "holds " + atoms.size() + " rows - refusing to merge the "
                            + "two.",
                    null);
        }
        if (massesSeen) {
            for (int i = 0; i < masses.length; i++) {
                if (masses[i] == null) {
                    return OperationResult.failed("LAMMPS_COUNT",
                            "The Masses section does not cover type " + (i + 1) + " of "
                                    + typeCount + " declared types.",
                            null);
                }
            }
        }
        long outside = 0L;
        for (LammpsAtom atom : atoms) {
            if (atom.getX() < boxLo[0] || atom.getX() >= boxHi[0]
                    || atom.getY() < boxLo[1] || atom.getY() >= boxHi[1]
                    || atom.getZ() < boxLo[2] || atom.getZ() >= boxHi[2]) {
                outside += 1;
            }
        }
        double[] lengths = {boxHi[0] - boxLo[0], boxHi[1] - boxLo[1], boxHi[2] - boxLo[2]};
        return OperationResult.success("LAMMPS_OK", "Parsed " + atoms.size() + " atoms.",
                new LammpsData((int) atomCount, (int) typeCount, lengths, tilted,
                        massesSeen ? masses : null, atoms, style, outside, skipped));
    }

    private static boolean bounds(String[] parts, double[] lo, double[] hi, int axis) {
        double a = parseDouble(parts[0]);
        double b = parseDouble(parts[1]);
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        lo[axis] = a;
        hi[axis] = b;
        return true;
    }

    private static String abbreviate(String line) {
        String trimmed = line.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "...";
    }
}
