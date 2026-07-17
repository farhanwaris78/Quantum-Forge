/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats and writes the exact, mathematically correct Phonopy FORCE_SETS files
 * from finite-displacement calculation forces (Roadmap #107).
 */
public final class QEPhonopyForceSetsWriter {

    public static final class DisplacementForceRecord {
        private final int atomIndex; // 1-based index of the displaced atom
        private final double[] displacementVector; // 3D displacement in Angstroms
        private final double[][] forces; // [numAtoms][3] forces in eV/Angstrom

        public DisplacementForceRecord(int atomIndex, double[] displacementVector, double[][] forces) {
            if (atomIndex <= 0) {
                throw new IllegalArgumentException("atomIndex must be 1-based and positive");
            }
            this.atomIndex = atomIndex;
            this.displacementVector = Objects.requireNonNull(displacementVector, "displacementVector").clone();
            
            // Clone 2D forces array
            this.forces = new double[forces.length][3];
            for (int i = 0; i < forces.length; i++) {
                System.arraycopy(forces[i], 0, this.forces[i], 0, 3);
            }
        }

        public int getAtomIndex() { return this.atomIndex; }
        public double[] getDisplacementVector() { return this.displacementVector.clone(); }
        public double[][] getForces() {
            double[][] out = new double[forces.length][3];
            for (int i = 0; i < forces.length; i++) {
                System.arraycopy(this.forces[i], 0, out[i], 0, 3);
            }
            return out;
        }
    }

    private final List<DisplacementForceRecord> records = new ArrayList<>();

    public QEPhonopyForceSetsWriter() {
        // Constructor
    }

    public void addRecord(int atomIndex, double[] displacementVector, double[][] forces) {
        this.records.add(new DisplacementForceRecord(atomIndex, displacementVector, forces));
    }

    public List<DisplacementForceRecord> getRecords() { return List.copyOf(this.records); }
    public int size() { return this.records.size(); }

    /**
     * Synthesizes the exact, structured FORCE_SETS file conforming to Phonopy specification.
     */
    public String generateForceSetsText(int numAtoms) {
        if (this.records.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        // 1. Line 1: total atoms in the supercell
        sb.append(String.format(Locale.ROOT, "%d\n", numAtoms));
        // 2. Line 2: total displacement steps
        sb.append(String.format(Locale.ROOT, "%d\n", this.records.size()));

        // 3. Output displacement and force blocks sequentially
        for (int step = 0; step < this.records.size(); step++) {
            DisplacementForceRecord record = this.records.get(step);
            sb.append("\n");
            // Index of displaced atom (1-based)
            sb.append(String.format(Locale.ROOT, "%d\n", record.getAtomIndex()));
            // 3D displacement vector
            double[] d = record.getDisplacementVector();
            sb.append(String.format(Locale.ROOT, "  %15.10f %15.10f %15.10f\n", d[0], d[1], d[2]));

            // Atomic forces for all atoms in the supercell
            double[][] f = record.getForces();
            for (int i = 0; i < numAtoms; i++) {
                sb.append(String.format(Locale.ROOT, "  %15.10f %15.10f %15.10f\n", f[i][0], f[i][1], f[i][2]));
            }
        }

        return sb.toString();
    }

    /**
     * Writes the FORCE_SETS file to disk securely.
     */
    public void writeForceSetsFile(File file, int numAtoms) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        String content = generateForceSetsText(numAtoms);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }
}
