/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Builds and validates crystalline point defects (vacancies, substitutions, interstitials),
 * tracking charge-state metadata and calculating periodic image-separation diagnostics
 * to prevent non-physical defect-defect interactions (Roadmap #84).
 */
public final class QEPointDefectBuilder {

    public enum DefectType {
        VACANCY,
        SUBSTITUTION,
        INTERSTITIAL
    }

    public static final class DefectRecord {
        private final DefectType type;
        private final int targetAtomIndex; // -1 for interstitials
        private final String originalElement;
        private final String newElement;   // Empty for vacancies
        private final double[] coordinates;
        private final int chargeState;     // Net charge (e.g. -1, 0, +1, +2)

        public DefectRecord(DefectType type, int targetAtomIdx, String original, String next, double[] coords, int charge) {
            this.type = Objects.requireNonNull(type);
            this.targetAtomIndex = targetAtomIdx;
            this.originalElement = original == null ? "" : original;
            this.newElement = next == null ? "" : next;
            this.coordinates = coords != null ? coords.clone() : new double[3];
            this.chargeState = charge;
        }

        public DefectType getType() { return this.type; }
        public int getTargetAtomIndex() { return this.targetAtomIndex; }
        public String getOriginalElement() { return this.originalElement; }
        public String getNewElement() { return this.newElement; }
        public double[] getCoordinates() { return this.coordinates.clone(); }
        public int getChargeState() { return this.chargeState; }

        @Override
        public String toString() {
            return String.format("Defect [%s] atom#%d (%s -> %s) charge=%+d",
                type, targetAtomIndex + 1, originalElement, newElement.isEmpty() ? "VAC" : newElement, chargeState);
        }
    }

    private final Cell baseCell;
    private final List<DefectRecord> defects = new ArrayList<>();

    public QEPointDefectBuilder(Cell cell) {
        this.baseCell = Objects.requireNonNull(cell, "baseCell");
    }

    public List<DefectRecord> getDefects() { return List.copyOf(this.defects); }

    /**
     * Replaces an atom with a vacancy.
     */
    public void addVacancy(int atomIdx, int chargeState) {
        Atom[] atoms = baseCell.listAtoms(true);
        if (atomIdx < 0 || atomIdx >= atoms.length) {
            throw new IndexOutOfBoundsException("Atom index is out of bounds");
        }
        Atom target = atoms[atomIdx];
        double[] coords = {target.getX(), target.getY(), target.getZ()};
        this.defects.add(new DefectRecord(DefectType.VACANCY, atomIdx, target.getName(), "", coords, chargeState));
    }

    /**
     * Replaces an atom with a different chemical species (substitution).
     */
    public void addSubstitution(int atomIdx, String newElement, int chargeState) {
        Atom[] atoms = baseCell.listAtoms(true);
        if (atomIdx < 0 || atomIdx >= atoms.length) {
            throw new IndexOutOfBoundsException("Atom index is out of bounds");
        }
        if (newElement == null || newElement.trim().isEmpty()) {
            throw new IllegalArgumentException("New element cannot be empty");
        }
        Atom target = atoms[atomIdx];
        double[] coords = {target.getX(), target.getY(), target.getZ()};
        this.defects.add(new DefectRecord(DefectType.SUBSTITUTION, atomIdx, target.getName(), newElement.trim(), coords, chargeState));
    }

    /**
     * Places a new atom at an interstitial position.
     */
    public void addInterstitial(double x, double y, double z, String element, int chargeState) {
        if (element == null || element.trim().isEmpty()) {
            throw new IllegalArgumentException("Element cannot be empty");
        }
        this.defects.add(new DefectRecord(DefectType.INTERSTITIAL, -1, "", element.trim(), new double[]{x, y, z}, chargeState));
    }

    /**
     * Builds the defective cell by applying all added defects sequentially.
     */
    public Cell build() throws ZeroVolumCellException {
        double[][] lattice = baseCell.copyLattice();
        Cell defectCell = new Cell(lattice);

        Atom[] atoms = baseCell.listAtoms(true);
        boolean[] deleted = new boolean[atoms.length];
        String[] species = new String[atoms.length];
        for (int i = 0; i < atoms.length; i++) {
            species[i] = atoms[i].getName();
        }

        // Apply vacancies and substitutions
        for (DefectRecord record : this.defects) {
            if (record.type == DefectType.VACANCY) {
                deleted[record.targetAtomIndex] = true;
            } else if (record.type == DefectType.SUBSTITUTION) {
                species[record.targetAtomIndex] = record.newElement;
            }
        }

        // Populate preserved base atoms
        for (int i = 0; i < atoms.length; i++) {
            if (!deleted[i] && !atoms[i].isSlaveAtom()) {
                defectCell.addAtom(species[i], atoms[i].getX(), atoms[i].getY(), atoms[i].getZ());
            }
        }

        // Populate interstitials
        for (DefectRecord record : this.defects) {
            if (record.type == DefectType.INTERSTITIAL) {
                defectCell.addAtom(record.newElement, record.coordinates[0], record.coordinates[1], record.coordinates[2]);
            }
        }

        return defectCell;
    }

    /**
     * Calculates the minimum periodic image separation distance (in Angstroms) of the defect.
     * In PBC, this is the shortest translation vector of the lattice:
     * min(|n1*a1 + n2*a2 + n3*a3|) where n != 0
     */
    public double calculateImageSeparation() {
        double[][] lattice = baseCell.copyLattice();
        if (lattice == null) {
            return 0.0;
        }

        double[] a1 = lattice[0];
        double[] a2 = lattice[1];
        double[] a3 = lattice[2];

        double minDistance = Double.MAX_VALUE;

        // Check translations from -1 to 1 (except 0,0,0)
        for (int n1 = -1; n1 <= 1; n1++) {
            for (int n2 = -1; n2 <= 1; n2++) {
                for (int n3 = -1; n3 <= 1; n3++) {
                    if (n1 == 0 && n2 == 0 && n3 == 0) {
                        continue;
                    }

                    double rx = n1 * a1[0] + n2 * a2[0] + n3 * a3[0];
                    double ry = n1 * a1[1] + n2 * a2[1] + n3 * a3[1];
                    double rz = n1 * a1[2] + n2 * a2[2] + n3 * a3[2];
                    double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
                    if (dist < minDistance) {
                        minDistance = dist;
                    }
                }
            }
        }

        return minDistance;
    }

    /**
     * Returns true if the image-separation distance is safe (typically >= 10 Angstroms)
     * to prevent non-physical artificial band hybridization.
     */
    public boolean isImageSeparationSafe(double thresholdAngstrom) {
        return calculateImageSeparation() >= thresholdAngstrom;
    }
}
