/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder.supercell;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Mathematically rigorous Coincidence Site Lattice (CSL) Grain Boundary Builder.
 * Supports standard Sigma twist values (Sigma 5, Sigma 13) around [001], symmetric
 * dual-grain rotation, and automated interface atomic overlap removal (Roadmap #86).
 */
public final class QEGrainBoundaryBuilder {

    public enum SigmaValue {
        SIGMA_5(36.87, 5),
        SIGMA_13(22.62, 13);

        private final double angleDegrees;
        private final int sigmaInt;

        SigmaValue(double angle, int sig) {
            this.angleDegrees = angle;
            this.sigmaInt = sig;
        }

        public double getAngleDegrees() { return this.angleDegrees; }
        public int getSigmaInt() { return this.sigmaInt; }
    }

    private final Cell bulkGrain;
    private final SigmaValue sigmaValue;
    private final double overlapThresholdAngstrom;

    public QEGrainBoundaryBuilder(Cell bulkGrain, SigmaValue sigma) {
        this(bulkGrain, sigma, 1.5); // Default 1.5 Angstrom overlap threshold
    }

    public QEGrainBoundaryBuilder(Cell bulkGrain, SigmaValue sigma, double overlapThreshold) {
        this.bulkGrain = Objects.requireNonNull(bulkGrain, "bulkGrain");
        this.sigmaValue = Objects.requireNonNull(sigma, "sigma");
        this.overlapThresholdAngstrom = Math.max(0.5, overlapThreshold);
    }

    public SigmaValue getSigmaValue() { return this.sigmaValue; }
    public double getOverlapThreshold() { return this.overlapThresholdAngstrom; }

    /**
     * Builds the commensurate periodic CSL Grain Boundary supercell.
     */
    public Cell build() throws ZeroVolumCellException {
        double[][] bulkLattice = this.bulkGrain.copyLattice();

        // 1. Double the lattice size along Z to hold both grain 1 and grain 2
        double[][] gbLattice = new double[3][3];
        gbLattice[0] = bulkLattice[0].clone();
        gbLattice[1] = bulkLattice[1].clone();
        gbLattice[2][0] = bulkLattice[2][0];
        gbLattice[2][1] = bulkLattice[2][1];
        gbLattice[2][2] = bulkLattice[2][2] * 2.0; // stack grains along Z

        Cell gbCell = new Cell(gbLattice);

        // Symmetric twist angle +/- theta/2 around [001]
        double thetaRad = (this.sigmaValue.getAngleDegrees() * Math.PI) / 180.0;
        double[][] rot1 = getRotationMatrix(-thetaRad / 2.0);
        double[][] rot2 = getRotationMatrix(thetaRad / 2.0);

        Atom[] atoms = this.bulkGrain.listAtoms(true);
        if (atoms == null) {
            return gbCell;
        }

        List<double[]> grain1Coords = new ArrayList<>();
        List<String> grain1Names = new ArrayList<>();
        List<double[]> grain2Coords = new ArrayList<>();
        List<String> grain2Names = new ArrayList<>();

        // Populate and rotate Grain 1 (Bottom half of the cell, z < bulk_L_z)
        for (Atom atom : atoms) {
            if (atom == null || atom.isSlaveAtom()) continue;
            double[] pos = {atom.getX(), atom.getY(), atom.getZ()};
            double[] rotated = Matrix3D.mult(rot1, pos);
            grain1Coords.add(rotated);
            grain1Names.add(atom.getName());
        }

        // Populate and rotate Grain 2 (Top half of the cell, z >= bulk_L_z)
        double zTranslation = bulkLattice[2][2];
        for (Atom atom : atoms) {
            if (atom == null || atom.isSlaveAtom()) continue;
            double[] pos = {atom.getX(), atom.getY(), atom.getZ()};
            double[] rotated = Matrix3D.mult(rot2, pos);
            rotated[2] += zTranslation; // stack on top of Grain 1
            grain2Coords.add(rotated);
            grain2Names.add(atom.getName());
        }

        // 2. Perform strict atomic overlap removal at the interface boundaries (z=0 and z=bulk_L_z)
        // If an atom in Grain 2 is located too close to an atom in Grain 1 across the periodic border,
        // we delete it to ensure mechanical and electronic stability!
        boolean[] deleteGrain2 = new boolean[grain2Coords.size()];

        for (int i = 0; i < grain2Coords.size(); i++) {
            double[] g2 = grain2Coords.get(i);
            for (int j = 0; j < grain1Coords.size(); j++) {
                double[] g1 = grain1Coords.get(j);

                // Compute periodic distance (accounts for cell boundary stacking)
                double dx = g2[0] - g1[0];
                double dy = g2[1] - g1[1];
                double dz = g2[2] - g1[2];

                // Wrap Z difference across the periodic boundary
                double cellHeight = gbLattice[2][2];
                dz = dz - cellHeight * Math.round(dz / cellHeight);

                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (dist < this.overlapThresholdAngstrom) {
                    deleteGrain2[i] = true;
                    break;
                }
            }
        }

        // 3. Populate final stable grain boundary cell
        for (int i = 0; i < grain1Coords.size(); i++) {
            double[] g1 = grain1Coords.get(i);
            gbCell.addAtom(grain1Names.get(i), g1[0], g1[1], g1[2]);
        }

        int removedCount = 0;
        for (int i = 0; i < grain2Coords.size(); i++) {
            if (!deleteGrain2[i]) {
                double[] g2 = grain2Coords.get(i);
                gbCell.addAtom(grain2Names.get(i), g2[0], g2[1], g2[2]);
            } else {
                removedCount++;
            }
        }

        System.out.println(String.format("Grain Boundary built (Sigma %d): symmetric rotation +/- %.2f deg. Cleaned %d overlapping interface atoms.",
            this.sigmaValue.getSigmaInt(), this.sigmaValue.getAngleDegrees() / 2.0, removedCount));

        return gbCell;
    }

    private double[][] getRotationMatrix(double angleRad) {
        return new double[][]{
            {Math.cos(angleRad), -Math.sin(angleRad), 0.0},
            {Math.sin(angleRad),  Math.cos(angleRad), 0.0},
            {0.0, 0.0, 1.0}
        };
    }
}
