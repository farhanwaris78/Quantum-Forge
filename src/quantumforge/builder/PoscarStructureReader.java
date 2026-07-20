/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed VASP POSCAR/CONTCAR reader for REVIEW purposes (Roadmap #76):
 * handles the VASP 4 and VASP 5 layouts, a negative (volume-scaled) or
 * positive scale factor, the optional Selective dynamics line, and Direct or
 * Cartesian coordinate frames with optional T/F flags. The reader invents
 * nothing: VASP 4 files carry no species names (the POTCAR order applies and
 * is NOT knowable from the file), velocities/predictor-corrector trailing
 * blocks are reported as unparsed, and every deviation from this grammar is a
 * coded failure rather than a guess.
 */
public final class PoscarStructureReader {

    /** Parse bound identical to the other bounded text readers. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /** Atom bound keeps positional bookkeeping honest and memory bounded. */
    public static final int MAX_ATOMS = 1_000_000;
    /** Below this determinant the lattice is degenerate. */
    public static final double MIN_LATTICE_DET = 1.0e-12;

    /** Parsed structure: lattice (scaled, Angstrom), species/counts, positions. */
    public static final class PoscarStructure {
        private final String comment;
        private final double scaleWritten;
        private final double scaleApplied;
        private final boolean volumeScaled;
        private final double[][] lattice;
        private final List<String> species; // empty for VASP 4 (POTCAR order applies)
        private final int[] counts;
        private final boolean selectiveDynamics;
        private final boolean directFrame;
        private final double[][] positionsAng; // Cartesian Angstrom, scale applied
        private final boolean[] fullyFixed;    // per atom: all three flags F (sel. dyn.)
        private final int outOfCellCount;      // direct coords outside [0,1)
        private final String trailingNote;     // null when nothing trailed the coords

        PoscarStructure(String comment, double scaleWritten, double scaleApplied,
                boolean volumeScaled, double[][] lattice, List<String> species, int[] counts,
                boolean selectiveDynamics, boolean directFrame, double[][] positionsAng,
                boolean[] fullyFixed, int outOfCellCount, String trailingNote) {
            this.comment = comment;
            this.scaleWritten = scaleWritten;
            this.scaleApplied = scaleApplied;
            this.volumeScaled = volumeScaled;
            this.lattice = lattice;
            this.species = List.copyOf(species);
            this.counts = counts.clone();
            this.selectiveDynamics = selectiveDynamics;
            this.directFrame = directFrame;
            this.positionsAng = positionsAng;
            this.fullyFixed = fullyFixed;
            this.outOfCellCount = outOfCellCount;
            this.trailingNote = trailingNote;
        }

        public String getComment() { return this.comment; }
        public double getScaleWritten() { return this.scaleWritten; }
        public double getScaleApplied() { return this.scaleApplied; }
        public boolean isVolumeScaled() { return this.volumeScaled; }

        public double[][] getLattice() {
            double[][] copy = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(this.lattice[i], 0, copy[i], 0, 3);
            }
            return copy;
        }

        public List<String> getSpecies() { return this.species; }
        public int[] getCounts() { return this.counts.clone(); }

        public int getTotalAtoms() {
            int total = 0;
            for (int count : this.counts) {
                total += count;
            }
            return total;
        }

        public boolean isSelectiveDynamics() { return this.selectiveDynamics; }
        public boolean isDirectFrame() { return this.directFrame; }

        public double[][] getPositionsAng() {
            double[][] copy = new double[this.positionsAng.length][3];
            for (int i = 0; i < copy.length; i++) {
                System.arraycopy(this.positionsAng[i], 0, copy[i], 0, 3);
            }
            return copy;
        }

        public boolean[] getFullyFixed() { return this.fullyFixed.clone(); }
        public int getOutOfCellCount() { return this.outOfCellCount; }
        public String getTrailingNote() { return this.trailingNote; }

        /** Composition label in count order ("Si=2,O=4"); empty names stay anonymous. */
        public String composition() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < this.counts.length; i++) {
                if (i > 0) {
                    text.append(',');
                }
                String name = i < this.species.size() ? this.species.get(i) : ("#" + (i + 1));
                text.append(name).append('=').append(this.counts[i]);
            }
            return text.toString();
        }
    }

    private PoscarStructureReader() { }

    /**
     * Parses a POSCAR/CONTCAR file. Codes: POSCAR_IO, POSCAR_TOO_LARGE,
     * POSCAR_SYNTAX, POSCAR_SCALE, POSCAR_COUNT, POSCAR_SHAPE, POSCAR_VALUE,
     * POSCAR_FRAME.
     */
    public static OperationResult<PoscarStructure> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("POSCAR_IO", "The POSCAR file does not exist.",
                    null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("POSCAR_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("POSCAR_TOO_LARGE",
                    "The file exceeds the 64 MiB parse bound.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return OperationResult.failed("POSCAR_IO",
                    "Reading the file failed: " + ex.getMessage(), ex);
        }
        if (lines.size() < 8) {
            return OperationResult.failed("POSCAR_SYNTAX",
                    "A POSCAR needs at least 8 lines (comment, scale, 3 lattice, "
                            + "counts, frame, >=1 coordinate); found " + lines.size() + ".",
                    null);
        }
        String comment = lines.get(0).trim();

        double scale = parseScalar(lines.get(1));
        if (Double.isNaN(scale) || !Double.isFinite(scale) || scale == 0.0) {
            return OperationResult.failed("POSCAR_SCALE",
                    "Line 2 must hold one finite non-zero scale factor, found: '"
                            + lines.get(1).trim() + "'.",
                    null);
        }

        double[][] raw = new double[3][3];
        for (int i = 0; i < 3; i++) {
            double[] row = parseVector(lines.get(2 + i), 3);
            if (row == null) {
                return OperationResult.failed("POSCAR_SYNTAX",
                        "Lattice row " + (i + 1) + " needs exactly 3 finite values: '"
                                + lines.get(2 + i).trim() + "'.",
                        null);
            }
            raw[i] = row;
        }
        double rawDet = Math.abs(det3(raw));
        if (rawDet < MIN_LATTICE_DET) {
            return OperationResult.failed("POSCAR_SHAPE",
                    "The lattice determinant is " + String.format(Locale.ROOT, "%.3e", rawDet)
                            + " (< 1e-12); a degenerate cell cannot locate atoms.", null);
        }
        boolean volumeScaled = scale < 0.0;
        double applied = scale;
        if (volumeScaled) {
            applied = Math.cbrt(Math.abs(scale) / rawDet);
            if (!Double.isFinite(applied) || applied <= 0.0) {
                return OperationResult.failed("POSCAR_SCALE",
                        "The negative (volume) scale cannot be resolved against this lattice.",
                        null);
            }
        }
        double[][] lattice = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                lattice[i][j] = raw[i][j] * applied;
            }
        }

        int cursor = 5;
        List<String> species = new ArrayList<>();
        String[] sixth = tokens(lines.get(cursor));
        if (sixth.length == 0) {
            return OperationResult.failed("POSCAR_SYNTAX",
                    "Line 6 is empty; it must hold species names (VASP 5) or counts (VASP 4).",
                    null);
        }
        int[] counts = tryCounts(sixth);
        if (counts != null) {
            cursor += 1; // VASP 4: counts on line 6, species follow the POTCAR order
        } else {
            for (String token : sixth) {
                if (!token.matches("[A-Za-z][A-Za-z0-9_]*")) {
                    return OperationResult.failed("POSCAR_SYNTAX",
                            "Line 6 mixes species-like and count-like tokens: '"
                                    + lines.get(cursor).trim() + "'.",
                            null);
                }
                species.add(token);
            }
            cursor += 1;
            if (cursor >= lines.size()) {
                return OperationResult.failed("POSCAR_SYNTAX",
                        "The species line is not followed by a counts line.", null);
            }
            counts = tryCounts(tokens(lines.get(cursor)));
            if (counts == null) {
                return OperationResult.failed("POSCAR_COUNT",
                        "The counts line must hold only positive integers, found: '"
                                + lines.get(cursor).trim() + "'.",
                        null);
            }
            cursor += 1;
        }
        if (!species.isEmpty() && species.size() != counts.length) {
            return OperationResult.failed("POSCAR_COUNT",
                    "Species/count mismatch: " + species.size() + " names but "
                            + counts.length + " counts.",
                    null);
        }
        long total = 0;
        for (int count : counts) {
            total += count;
        }
        if (total <= 0) {
            return OperationResult.failed("POSCAR_COUNT",
                    "Zero atoms: every species count must be a positive integer.", null);
        }
        if (total > MAX_ATOMS) {
            return OperationResult.failed("POSCAR_COUNT",
                    "The atom count " + total + " exceeds the " + MAX_ATOMS
                            + " reader bound.",
                    null);
        }

        boolean selective = false;
        if (cursor < lines.size()) {
            String first = firstWord(lines.get(cursor));
            if (first != null && (first.charAt(0) == 's' || first.charAt(0) == 'S')) {
                selective = true;
                cursor += 1;
            }
        }
        if (cursor >= lines.size()) {
            return OperationResult.failed("POSCAR_SYNTAX",
                    "The coordinate frame line (Direct/Cartesian) is missing.", null);
        }
        String frameWord = firstWord(lines.get(cursor));
        if (frameWord == null || frameWord.isEmpty()) {
            return OperationResult.failed("POSCAR_FRAME",
                    "The coordinate frame line is empty.", null);
        }
        char fc = Character.toLowerCase(frameWord.charAt(0));
        boolean direct;
        if (fc == 'd') {
            direct = true;
        } else if (fc == 'c' || fc == 'k') {
            direct = false;
        } else {
            return OperationResult.failed("POSCAR_FRAME",
                    "The frame line must start with D (Direct) or C/K (Cartesian), found: '"
                            + frameWord + "'.",
                    null);
        }
        cursor += 1;

        int natoms = (int) total;
        if (cursor + natoms > lines.size()) {
            return OperationResult.failed("POSCAR_SYNTAX",
                    "Only " + (lines.size() - cursor) + " coordinate rows for " + natoms
                            + " atoms.",
                    null);
        }
        double[][] positions = new double[natoms][3];
        boolean[] fixed = new boolean[natoms];
        int outOfCell = 0;
        for (int a = 0; a < natoms; a++) {
            String[] row = tokens(lines.get(cursor + a));
            if (row.length < 3) {
                return OperationResult.failed("POSCAR_SYNTAX",
                        "Atom " + (a + 1) + " needs at least 3 coordinates: '"
                                + lines.get(cursor + a).trim() + "'.",
                        null);
            }
            double[] xyz = new double[3];
            for (int j = 0; j < 3; j++) {
                xyz[j] = parseDouble(row[j]);
                if (!Double.isFinite(xyz[j])) {
                    return OperationResult.failed("POSCAR_VALUE",
                            "Atom " + (a + 1) + " coordinate " + (j + 1)
                                    + " is not a finite number: '" + row[j] + "'.",
                            null);
                }
            }
            if (selective && row.length >= 6) {
                boolean allFalse = true;
                for (int j = 3; j < 6; j++) {
                    String flag = row[j];
                    if (!flag.equalsIgnoreCase("t") && !flag.equalsIgnoreCase("f")) {
                        return OperationResult.failed("POSCAR_SYNTAX",
                                "Atom " + (a + 1) + " selective flag " + (j - 2)
                                        + " must be T or F, found: '" + flag + "'.",
                                null);
                    }
                    if (flag.equalsIgnoreCase("t")) {
                        allFalse = false;
                    }
                }
                fixed[a] = allFalse;
            } else if (selective && row.length > 3) {
                return OperationResult.failed("POSCAR_SYNTAX",
                        "Atom " + (a + 1) + " has a partial selective-flag row: '"
                                + lines.get(cursor + a).trim() + "'.",
                        null);
            }
            if (direct) {
                for (int j = 0; j < 3; j++) {
                    if (xyz[j] < 0.0 || xyz[j] >= 1.0) {
                        outOfCell += 1;
                        break;
                    }
                }
            }
            // Cartesian Angstrom: Direct = f . lattice; Cartesian = scale * xyz.
            for (int axis = 0; axis < 3; axis++) {
                double value;
                if (direct) {
                    value = xyz[0] * lattice[0][axis] + xyz[1] * lattice[1][axis]
                            + xyz[2] * lattice[2][axis];
                } else {
                    value = applied * xyz[axis];
                }
                positions[a][axis] = value;
            }
        }

        String trailingNote = null;
        for (int i = cursor + natoms; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                trailingNote = "Trailing non-blank content after the coordinate block "
                        + "(lines " + (cursor + natoms + 1) + "-" + lines.size()
                        + ") was NOT parsed: velocity and predictor-corrector blocks are "
                        + "outside this reader's grammar.";
                break;
            }
        }

        return OperationResult.success("POSCAR_OK", "Parsed " + natoms + " atoms.",
                new PoscarStructure(comment, scale, applied, volumeScaled, lattice, species,
                        counts, selective, direct, positions, fixed, outOfCell, trailingNote));
    }

    /** Whitespace tokens of one line. */
    private static String[] tokens(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static String firstWord(String line) {
        String[] parts = tokens(line);
        return parts.length == 0 ? null : parts[0];
    }

    /** Every token is a positive integer; null otherwise. */
    private static int[] tryCounts(String[] row) {
        if (row.length == 0) {
            return null;
        }
        int[] counts = new int[row.length];
        for (int i = 0; i < row.length; i++) {
            try {
                counts[i] = Integer.parseInt(row[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (counts[i] <= 0) {
                return null;
            }
        }
        return counts;
    }

    /** Exactly three finite values or null; Fortran D exponents tolerated. */
    private static double[] parseVector(String line, int expected) {
        String[] row = tokens(line);
        if (row.length != expected) {
            return null;
        }
        double[] values = new double[expected];
        for (int i = 0; i < expected; i++) {
            values[i] = parseDouble(row[i]);
            if (!Double.isFinite(values[i])) {
                return null;
            }
        }
        return values;
    }

    /** One finite scalar or 0.0 on failure (callers reject zero scales). */
    private static double parseScalar(String line) {
        String[] row = tokens(line);
        if (row.length != 1) {
            return Double.NaN;
        }
        return parseDouble(row[0]);
    }

    private static double parseDouble(String token) {
        try {
            return Double.parseDouble(token.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /** 3x3 determinant in row-major vector form. */
    private static double det3(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }
}
