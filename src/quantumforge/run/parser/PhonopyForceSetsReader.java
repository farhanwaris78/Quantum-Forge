/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Fail-closed reader for phonopy's legacy finite-displacement {@code FORCE_SETS}
 * file - the exact mirror of {@link QEPhonopyForceSetsWriter} (Roadmap #107).
 *
 * <p>Layout (blank lines between blocks tolerated):</p>
 * <pre>
 *   num_atoms
 *   num_displacement_sets
 *
 *   atom_index                 (1-based)
 *   dx dy dz                   (displacement of that atom)
 *   fx fy fz                   (force row for atom 1)
 *   ... num_atoms force rows
 *   (repeat per set)
 * </pre>
 *
 * <p>Like the writer, this reader treats the format as unitless and never
 * guesses a force unit; the phonopy/QE convention (Angstrom, eV/Angstrom) is
 * stated by the calling report. Trailing lines after the declared sets are
 * counted, never silently consumed.</p>
 */
public final class PhonopyForceSetsReader {

    /** Absurd-allocation guards for the two header integers. */
    public static final int MAX_ATOMS = 1_000_000;
    public static final int MAX_SETS = 100_000;

    /** One validated displacement-force block. */
    public static final class DisplacementSet {
        private final int atomIndex;      // 1-based, exactly as written in the file
        private final double[] displacement;
        private final double[][] forces;

        public DisplacementSet(int atomIndex, double[] displacement, double[][] forces) {
            this.atomIndex = atomIndex;
            this.displacement = displacement.clone();
            this.forces = copyRows(forces);
        }

        /** 1-based displaced-atom index exactly as written in the file. */
        public int getAtomIndex() { return this.atomIndex; }
        public double[] getDisplacement() { return this.displacement.clone(); }
        public double[][] getForces() { return copyRows(this.forces); }

        /** Euclidean norm of the displacement vector. */
        public double displacementNorm() {
            return Math.sqrt(this.displacement[0] * this.displacement[0]
                    + this.displacement[1] * this.displacement[1]
                    + this.displacement[2] * this.displacement[2]);
        }

        /** Largest force-row Euclidean norm in this set. */
        public double maxForceNorm() {
            double max = 0.0;
            for (double[] row : this.forces) {
                max = Math.max(max, norm(row));
            }
            return max;
        }

        /** Mean force-row Euclidean norm over all rows of this set. */
        public double meanForceNorm() {
            double sum = 0.0;
            for (double[] row : this.forces) {
                sum += norm(row);
            }
            return this.forces.length == 0 ? 0.0 : sum / this.forces.length;
        }

        private static double norm(double[] row) {
            return Math.sqrt(row[0] * row[0] + row[1] * row[1] + row[2] * row[2]);
        }

        private static double[][] copyRows(double[][] rows) {
            double[][] copy = new double[rows.length][3];
            for (int i = 0; i < rows.length; i++) {
                copy[i] = rows[i].clone();
            }
            return copy;
        }
    }

    /** The complete validated document. */
    public static final class ForceSets {
        private final int numAtoms;
        private final List<DisplacementSet> sets;
        private final int trailingLines;

        private ForceSets(int numAtoms, List<DisplacementSet> sets, int trailingLines) {
            this.numAtoms = numAtoms;
            this.sets = List.copyOf(sets);
            this.trailingLines = trailingLines;
        }

        public int getNumAtoms() { return this.numAtoms; }
        public List<DisplacementSet> getSets() { return this.sets; }
        /** Non-empty lines after the last declared set (ignored by the reader). */
        public int getTrailingLines() { return this.trailingLines; }

        /** Number of distinct displaced atoms across all sets. */
        public int countDistinctDisplacedAtoms() {
            java.util.Set<Integer> atoms = new java.util.HashSet<>();
            for (DisplacementSet set : this.sets) {
                atoms.add(set.getAtomIndex());
            }
            return atoms.size();
        }
    }

    private PhonopyForceSetsReader() {
        // Utility
    }

    /**
     * Parses a FORCE_SETS document. Any structural, range, truncation, or
     * non-finite-value problem is a blocking failure with the offending line
     * number in the message; a partially read document is never returned.
     */
    public static OperationResult<ForceSets> parse(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("FORCE_SETS_IO",
                    "Could not read " + file.getFileName() + ": " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            return OperationResult.failed("FORCE_SETS_IO",
                    "Could not read " + file.getFileName() + " (not UTF-8 text?): "
                            + ex.getMessage(), ex);
        }
        // Collapse: meaningful non-blank lines with their original line numbers.
        List<int[]> numbers = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty()) {
                numbers.add(new int[] {i + 1});
                texts.add(trimmed);
            }
        }
        Cursor cursor = new Cursor();
        int numAtoms = readPositiveInt(texts, numbers, cursor,
                "atom count", MAX_ATOMS);
        if (numAtoms < 0) {
            return fail(cursor.code, cursor.message);
        }
        int numSets = readPositiveInt(texts, numbers, cursor,
                "displacement-set count", MAX_SETS);
        if (numSets < 0) {
            return fail(cursor.code, cursor.message);
        }
        List<DisplacementSet> sets = new ArrayList<>();
        for (int set = 0; set < numSets; set++) {
            int atomIndex = readPositiveInt(texts, numbers, cursor,
                    "displaced-atom index of set " + (set + 1), numAtoms);
            if (atomIndex < 0) {
                return fail(cursor.code, cursor.message);
            }
            double[] displacement = readVector(texts, numbers, cursor,
                    "displacement of set " + (set + 1));
            if (displacement == null) {
                return fail(cursor.code, cursor.message);
            }
            double[][] forces = new double[numAtoms][3];
            for (int atom = 0; atom < numAtoms; atom++) {
                double[] row = readVector(texts, numbers, cursor,
                        "force row " + (atom + 1) + " of set " + (set + 1));
                if (row == null) {
                    return fail(cursor.code, cursor.message);
                }
                forces[atom] = row;
            }
            sets.add(new DisplacementSet(atomIndex, displacement, forces));
        }
        int trailing = texts.size() - cursor.position;
        return OperationResult.success("FORCE_SETS_OK",
                "FORCE_SETS parsed: " + numAtoms + " atoms, " + numSets + " set(s)"
                        + (trailing > 0 ? "; " + trailing + " trailing line(s) ignored" : ""),
                new ForceSets(numAtoms, sets, trailing));
    }

    private static OperationResult<ForceSets> fail(String code, String message) {
        return OperationResult.failed(code, message, null);
    }

    /** Mutable parse cursor over the non-blank lines. */
    private static final class Cursor {
        private int position = 0;
        private String code = "";
        private String message = "";
    }

    private static int readPositiveInt(List<String> texts, List<int[]> numbers,
            Cursor cursor, String what, int maxValue) {
        if (cursor.position >= texts.size()) {
            cursor.code = "FORCE_SETS_TRUNCATED";
            cursor.message = "Expected the " + what + " but reached the end of the file.";
            return -1;
        }
        String text = texts.get(cursor.position);
        int line = numbers.get(cursor.position)[0];
        cursor.position++;
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            cursor.code = "FORCE_SETS_SYNTAX";
            cursor.message = "Line " + line + " must be a single integer (the " + what
                    + ") but is: \"" + text + "\"";
            return -1;
        }
        if (value <= 0 || value > maxValue) {
            cursor.code = "FORCE_SETS_RANGE";
            cursor.message = "Line " + line + ": the " + what + " must be within 1.."
                    + maxValue + " (got " + value + ").";
            return -1;
        }
        return value;
    }

    private static double[] readVector(List<String> texts, List<int[]> numbers,
            Cursor cursor, String what) {
        if (cursor.position >= texts.size()) {
            cursor.code = "FORCE_SETS_TRUNCATED";
            cursor.message = "Expected the " + what + " but reached the end of the file.";
            return null;
        }
        String text = texts.get(cursor.position);
        int line = numbers.get(cursor.position)[0];
        cursor.position++;
        String[] tokens = text.split("\\s+");
        if (tokens.length != 3) {
            cursor.code = "FORCE_SETS_SYNTAX";
            cursor.message = "Line " + line + " must have exactly 3 numbers (the " + what
                    + ") but has " + tokens.length + ": \"" + text + "\"";
            return null;
        }
        double[] vector = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                vector[i] = Double.parseDouble(tokens[i].replace('D', 'E').replace('d', 'e'));
            } catch (NumberFormatException ex) {
                cursor.code = "FORCE_SETS_SYNTAX";
                cursor.message = "Line " + line + " has a non-numeric value \"" + tokens[i]
                        + "\" (the " + what + ").";
                return null;
            }
            if (!Double.isFinite(vector[i])) {
                cursor.code = "FORCE_SETS_SYNTAX";
                cursor.message = "Line " + line + " has a non-finite value \"" + tokens[i]
                        + "\" (the " + what + ").";
                return null;
            }
        }
        return vector;
    }
}
