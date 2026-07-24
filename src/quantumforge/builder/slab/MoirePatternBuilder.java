/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.slab;

import java.util.ArrayList;
import java.util.List;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Mathematically rigorous Commensurate Moiré Supercell Builder for twisted 2D bilayers
 * (Graphene, h-BN, MoS2), solving the integer lattice-matching conditions to prevent
 * periodic boundary mismatches and unphysical atom collisions (Roadmap #85).
 */
public class MoirePatternBuilder {

    private Cell layer1;
    private Cell layer2;
    private double twistAngle; // in degrees
    private double interlayerDistance; // in Angstrom
    private int nInt = 2; // Commensurate integer n
    private int mInt = 1; // Commensurate integer m
    private boolean useIntegers = true;

    public MoirePatternBuilder(Cell layer1, Cell layer2) {
        this.layer1 = layer1;
        this.layer2 = layer2;
        this.twistAngle = 21.787; // Commensurate angle for (2,1)
        this.interlayerDistance = 3.4; // Default for graphene
    }

    public void setTwistAngle(double angle) {
        this.twistAngle = angle;
        this.useIntegers = false;
    }

    public void setInterlayerDistance(double distance) {
        this.interlayerDistance = distance;
    }

    public void setCommensurateIntegers(int n, int m) {
        if (n <= 0 || m < 0) {
            throw new IllegalArgumentException("Invalid commensurate integers: n must be > 0 and m must be >= 0");
        }
        this.nInt = n;
        this.mInt = m;
        this.useIntegers = true;
        // Calculate commensurate angle theta:
        // cos(theta) = (n^2 + 4nm + m^2) / (2*(n^2 + nm + m^2))
        double n2 = n * n;
        double m2 = m * m;
        double nm = n * m;
        double cosTheta = (n2 + 4.0 * nm + m2) / (2.0 * (n2 + nm + m2));
        cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta));
        this.twistAngle = Math.acos(cosTheta) * 180.0 / Math.PI;
    }

    public int getNInt() { return this.nInt; }
    public int getMInt() { return this.mInt; }
    public double getTwistAngle() { return this.twistAngle; }

    /**
     * Builds the commensurate twisted bilayer Moiré supercell.
     */
    public Cell build() throws ZeroVolumCellException {
        if (this.layer1 == null || this.layer2 == null) return null;

        // If user set twistAngle manually, find the closest commensurate (n, m) pair
        if (!this.useIntegers) {
            findClosestCommensurate(this.twistAngle);
        }

        double[][] lat1 = this.layer1.copyLattice();

        // 1. Compute commensurate supercell lattice vectors L1, L2
        // L1 = n*a1 + m*a2
        // L2 = -m*a1 + (n+m)*a2
        double[] L1 = new double[3];
        double[] L2 = new double[3];
        for (int i = 0; i < 3; i++) {
            L1[i] = this.nInt * lat1[0][i] + this.mInt * lat1[1][i];
            L2[i] = -this.mInt * lat1[0][i] + (this.nInt + this.mInt) * lat1[1][i];
        }

        // L3 includes thickness + interlayer space + vacuum
        double[][] supercellLattice = new double[3][3];
        supercellLattice[0] = L1;
        supercellLattice[1] = L2;
        supercellLattice[2][0] = 0.0;
        supercellLattice[2][1] = 0.0;
        supercellLattice[2][2] = lat1[2][2] + this.interlayerDistance + 15.0; // add safe vacuum

        Cell moireCell = new Cell(supercellLattice);

        // 2. Populate Layer 1 (bottom layer, rotated by -theta/2) and Layer 2 (top layer, rotated by +theta/2)
        // to preserve symmetry (or we can just rotate Layer 2 by theta relative to un-rotated Layer 1)
        double thetaRad = this.twistAngle * Math.PI / 180.0;
        double[][] rot1 = getRotationMatrix(-thetaRad / 2.0);
        double[][] rot2 = getRotationMatrix(thetaRad / 2.0);

        int multiplicity = this.nInt * this.nInt + this.nInt * this.mInt + this.mInt * this.mInt;
        
        // Replicate original primitive coordinates inside the supercell
        // Bounding box of translations n1, n2 to fill the supercell:
        int range = Math.max(this.nInt, this.mInt) + 2;

        double[][] recSupercell = Matrix3D.inverse(supercellLattice);
        if (recSupercell == null) {
            throw new ZeroVolumCellException("Moiré supercell volume is zero.");
        }

        // Add Layer 1 atoms
        addLayerAtoms(this.layer1, outputCoords(this.layer1), rot1, 0.0, range, lat1, supercellLattice, recSupercell, moireCell);

        // Add Layer 2 atoms
        addLayerAtoms(this.layer2, outputCoords(this.layer2), rot2, this.interlayerDistance, range, lat1, supercellLattice, recSupercell, moireCell);

        return moireCell;
    }

    private void addLayerAtoms(Cell layer, List<double[]> originalCoords, double[][] rot, double zOffset,
                               int range, double[][] primitiveLattice, double[][] supercellLattice,
                               double[][] recSupercell, Cell output) {
        Atom[] atoms = layer.listAtoms(true);
        if (atoms == null) return;

        double eps = 1.0e-5;

        for (int aIdx = 0; aIdx < atoms.length; aIdx++) {
            Atom atom = atoms[aIdx];
            if (atom == null || atom.isSlaveAtom()) continue;

            for (int i = -range; i <= range; i++) {
                for (int j = -range; j <= range; j++) {
                    // Translated primitive coordinate
                    double rx = atom.getX() + i * primitiveLattice[0][0] + j * primitiveLattice[1][0];
                    double ry = atom.getY() + i * primitiveLattice[0][1] + j * primitiveLattice[1][1];
                    double rz = atom.getZ() + i * primitiveLattice[0][2] + j * primitiveLattice[1][2];

                    // Rotate the coordinate around Z axis
                    double[] rotated = Matrix3D.mult(rot, new double[]{rx, ry, rz});
                    rotated[2] += zOffset; // apply interlayer offset

                    // Project to supercell fractional coordinates
                    double s0 = rotated[0] * recSupercell[0][0] + rotated[1] * recSupercell[1][0] + rotated[2] * recSupercell[2][0];
                    double s1 = rotated[0] * recSupercell[0][1] + rotated[1] * recSupercell[1][1] + rotated[2] * recSupercell[2][1];
                    double s2 = rotated[0] * recSupercell[0][2] + rotated[1] * recSupercell[1][2] + rotated[2] * recSupercell[2][2];

                    // Check if inside supercell boundary: s0, s1 in [0, 1) and s2 in [0, 1)
                    s0 = s0 - Math.floor(s0);
                    s1 = s1 - Math.floor(s1);

                    double cx = s0 * supercellLattice[0][0] + s1 * supercellLattice[1][0] + s2 * supercellLattice[2][0];
                    double cy = s0 * supercellLattice[0][1] + s1 * supercellLattice[1][1] + s2 * supercellLattice[2][1];
                    double cz = s0 * supercellLattice[0][2] + s1 * supercellLattice[1][2] + s2 * supercellLattice[2][2];

                    // Deduplicate
                    boolean duplicate = false;
                    Atom[] existing = output.listAtoms(true);
                    if (existing != null) {
                        for (Atom ext : existing) {
                            if (ext != null && ext.getName().equals(atom.getName())) {
                                double dx = cx - ext.getX();
                                double dy = cy - ext.getY();
                                double dz = cz - ext.getZ();
                                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                                if (dist < 0.6) {
                                    duplicate = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!duplicate) {
                        output.addAtom(new Atom(atom.getName(), cx, cy, cz));
                    }
                }
            }
        }
    }

    private List<double[]> outputCoords(Cell cell) {
        List<double[]> list = new ArrayList<>();
        Atom[] atoms = cell.listAtoms(true);
        if (atoms != null) {
            for (Atom a : atoms) {
                if (a != null && !a.isSlaveAtom()) {
                    list.add(new double[]{a.getX(), a.getY(), a.getZ()});
                }
            }
        }
        return list;
    }

    private double[][] getRotationMatrix(double angleRad) {
        return new double[][]{
            {Math.cos(angleRad), -Math.sin(angleRad), 0.0},
            {Math.sin(angleRad),  Math.cos(angleRad), 0.0},
            {0.0, 0.0, 1.0}
        };
    }

    private void findClosestCommensurate(double targetAngle) {
        // Scans commensurate pairs up to N=10 to find the closest twist angle
        double minDiff = Double.MAX_VALUE;
        int bestN = 2;
        int bestM = 1;

        for (int n = 1; n <= 10; n++) {
            for (int m = 0; m <= n; m++) {
                if (n == 0 && m == 0) continue;
                double n2 = n * n;
                double m2 = m * m;
                double nm = n * m;
                double cosTheta = (n2 + 4.0 * nm + m2) / (2.0 * (n2 + nm + m2));
                cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta));
                double angle = Math.acos(cosTheta) * 180.0 / Math.PI;

                double diff = Math.abs(angle - targetAngle);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestN = n;
                    bestM = m;
                }
            }
        }

        this.nInt = bestN;
        this.mInt = bestM;
        // recalculate exact twist angle to match integers
        double n2 = bestN * bestN;
        double m2 = bestM * bestM;
        double nm = bestN * bestM;
        double cosTheta = (n2 + 4.0 * nm + m2) / (2.0 * (n2 + nm + m2));
        this.twistAngle = Math.acos(Math.max(-1.0, Math.min(1.0, cosTheta))) * 180.0 / Math.PI;
    }
}
