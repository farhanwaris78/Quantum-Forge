/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */
package quantumforge.run.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import quantumforge.com.consts.Constants;
import quantumforge.com.file.AtomicFileWriter;

/**
 * Writes Phonopy's legacy finite-displacement {@code FORCE_SETS} format.
 *
 * <p>The format itself is unitless.  Phonopy's Quantum ESPRESSO interface
 * expects displacement vectors in Angstrom and forces in eV/Angstrom.  This
 * class therefore makes the unit boundary explicit: {@link #addRecord} takes
 * eV/Angstrom, while {@link #addRecordFromQeRyPerBohr} converts raw QE forces.
 * It never guesses a force unit from a log file.</p>
 */
public final class QEPhonopyForceSetsWriter {

    /** (Ry/bohr) to (eV/Angstrom), derived from the application's constants. */
    public static final double RY_PER_BOHR_TO_EV_PER_ANGSTROM =
            Constants.RYTOEV / Constants.BOHR_RADIUS_ANGS;

    public static final class DisplacementForceRecord {
        private final int atomIndex;
        private final double[] displacementVector;
        private final double[][] forces;

        public DisplacementForceRecord(int atomIndex, double[] displacementVector, double[][] forces) {
            if (atomIndex <= 0) {
                throw new IllegalArgumentException("atomIndex must be 1-based and positive");
            }
            this.atomIndex = atomIndex;
            this.displacementVector = copyVector(displacementVector, "displacementVector");
            this.forces = copyForces(forces);
        }

        public int getAtomIndex() { return this.atomIndex; }
        public double[] getDisplacementVector() { return this.displacementVector.clone(); }
        public double[][] getForces() { return copyForces(this.forces); }
    }

    private final List<DisplacementForceRecord> records = new ArrayList<>();

    /** Adds a record whose displacement is in Angstrom and forces are eV/Angstrom. */
    public void addRecord(int atomIndex, double[] displacementVectorAngstrom,
                          double[][] forcesEvPerAngstrom) {
        this.records.add(new DisplacementForceRecord(atomIndex, displacementVectorAngstrom,
                forcesEvPerAngstrom));
    }

    /**
     * Adds a record from QE XML/text forces expressed in Ry/bohr, converting
     * them exactly once at the interface to Phonopy's eV/Angstrom convention.
     */
    public void addRecordFromQeRyPerBohr(int atomIndex, double[] displacementVectorAngstrom,
                                         double[][] forcesRyPerBohr) {
        double[][] source = copyForces(forcesRyPerBohr);
        double[][] converted = new double[source.length][3];
        for (int atom = 0; atom < source.length; atom++) {
            for (int component = 0; component < 3; component++) {
                converted[atom][component] = source[atom][component] * RY_PER_BOHR_TO_EV_PER_ANGSTROM;
            }
        }
        addRecord(atomIndex, displacementVectorAngstrom, converted);
    }

    public List<DisplacementForceRecord> getRecords() { return List.copyOf(this.records); }
    public int size() { return this.records.size(); }

    /**
     * Generates a complete FORCE_SETS document. A partial record is rejected
     * rather than producing a file Phonopy may accept with shifted blocks.
     */
    public String generateForceSetsText(int numAtoms) {
        validateForAtomCount(numAtoms);
        StringBuilder sb = new StringBuilder();
        sb.append(numAtoms).append('\n');
        sb.append(this.records.size()).append('\n');
        for (DisplacementForceRecord record : this.records) {
            sb.append('\n');
            sb.append(record.getAtomIndex()).append('\n');
            double[] d = record.getDisplacementVector();
            sb.append(String.format(Locale.ROOT, "  %15.10f %15.10f %15.10f%n", d[0], d[1], d[2]));
            for (double[] force : record.getForces()) {
                sb.append(String.format(Locale.ROOT, "  %15.10f %15.10f %15.10f%n",
                        force[0], force[1], force[2]));
            }
        }
        return sb.toString();
    }

    /** Writes a UTF-8 FORCE_SETS file atomically, preserving a previous complete file on failure. */
    public void writeForceSetsFile(File file, int numAtoms) throws IOException {
        Objects.requireNonNull(file, "file");
        writeForceSetsFile(file.toPath(), numAtoms);
    }

    public void writeForceSetsFile(Path file, int numAtoms) throws IOException {
        Objects.requireNonNull(file, "file");
        AtomicFileWriter.writeUtf8(file, generateForceSetsText(numAtoms));
    }

    private void validateForAtomCount(int numAtoms) {
        if (numAtoms <= 0) {
            throw new IllegalArgumentException("numAtoms must be positive");
        }
        if (this.records.isEmpty()) {
            throw new IllegalStateException("FORCE_SETS requires at least one displacement-force record");
        }
        for (int i = 0; i < this.records.size(); i++) {
            DisplacementForceRecord record = this.records.get(i);
            if (record.getAtomIndex() > numAtoms) {
                throw new IllegalArgumentException("Record " + i + " displaces atom "
                        + record.getAtomIndex() + " but the supercell has " + numAtoms + " atoms");
            }
            if (record.getForces().length != numAtoms) {
                throw new IllegalArgumentException("Record " + i + " has " + record.getForces().length
                        + " force rows; expected " + numAtoms);
            }
        }
    }

    private static double[] copyVector(double[] vector, String name) {
        if (vector == null || vector.length != 3) {
            throw new IllegalArgumentException(name + " must contain exactly 3 components");
        }
        double[] copy = vector.clone();
        for (double value : copy) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " contains a non-finite value");
            }
        }
        return copy;
    }

    private static double[][] copyForces(double[][] forceRows) {
        if (forceRows == null || forceRows.length == 0) {
            throw new IllegalArgumentException("forces must contain at least one atom row");
        }
        double[][] copy = new double[forceRows.length][3];
        for (int atom = 0; atom < forceRows.length; atom++) {
            double[] row = forceRows[atom];
            if (row == null || row.length != 3) {
                throw new IllegalArgumentException("force row " + atom + " must contain exactly 3 components");
            }
            for (int component = 0; component < 3; component++) {
                if (!Double.isFinite(row[component])) {
                    throw new IllegalArgumentException("force row " + atom + " contains a non-finite value");
                }
                copy[atom][component] = row[component];
            }
        }
        return copy;
    }
}
