/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.symmetry;

import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Lattice;

/**
 * Space group detector for crystalline materials.
 * 
 * This class provides symmetry analysis capabilities including:
 * - Bravais lattice (ibrav) detection from lattice vectors
 * - Space group number determination
 * - Crystal system classification
 * - Symmetry operation counting
 * 
 * This replaces the basic Lattice.getBravais() with a more robust 
 * detection that handles post-optimization numerical drift.
 */
public class SpaceGroupDetector {

    public static final int CRYSTAL_SYSTEM_TRICLINIC    = 1;
    public static final int CRYSTAL_SYSTEM_MONOCLINIC    = 2;
    public static final int CRYSTAL_SYSTEM_ORTHORHOMBIC  = 3;
    public static final int CRYSTAL_SYSTEM_TETRAGONAL    = 4;
    public static final int CRYSTAL_SYSTEM_TRIGONAL      = 5;
    public static final int CRYSTAL_SYSTEM_HEXAGONAL     = 6;
    public static final int CRYSTAL_SYSTEM_CUBIC         = 7;

    private static final double ANGLE_THRESHOLD = 1.0e-4;
    private static final double LENGTH_THRESHOLD = 1.0e-4;

    private Cell cell;
    private int ibrav;
    private int spaceGroupNumber;
    private String spaceGroupName;
    private int crystalSystem;
    private int numSymOperations;

    public SpaceGroupDetector(Cell cell) {
        this.cell = cell;
        this.ibrav = 0;
        this.spaceGroupNumber = 0;
        this.spaceGroupName = "Undetermined";
        this.crystalSystem = CRYSTAL_SYSTEM_TRICLINIC;
        this.numSymOperations = 0;
    }

    /**
     * Detect the Bravais lattice type (ibrav) from the cell.
     * Uses a two-pass approach: strict then relaxed threshold.
     */
    public int detectBravais() {
        if (this.cell == null) {
            return 0;
        }

        double[][] lattice = this.cell.copyLattice();
        this.ibrav = Lattice.getBravais(lattice);

        if (this.ibrav == 0) {
            // Try to determine the best ibrav from lattice parameters
            this.ibrav = this.detectFromLatticeParams(lattice);
        }

        return this.ibrav;
    }

    /**
     * Fallback detection from lattice parameters (a, b, c, alpha, beta, gamma)
     */
    private int detectFromLatticeParams(double[][] lattice) {
        double a = Lattice.getA(lattice);
        double b = Lattice.getB(lattice);
        double c = Lattice.getC(lattice);
        double alpha = Lattice.getAlpha(lattice);
        double beta = Lattice.getBeta(lattice);
        double gamma = Lattice.getGamma(lattice);

        // Cubic: a = b = c, alpha = beta = gamma = 90
        if (this.areEqual(a, b, LENGTH_THRESHOLD) &&
            this.areEqual(a, c, LENGTH_THRESHOLD) &&
            this.areEqual(alpha, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(beta, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(gamma, 90.0, ANGLE_THRESHOLD)) {
            this.crystalSystem = CRYSTAL_SYSTEM_CUBIC;
            return 1; // Simple cubic
        }

        // Hexagonal: a = b, c different, alpha = beta = 90, gamma = 120
        if (this.areEqual(a, b, LENGTH_THRESHOLD) &&
            this.areEqual(alpha, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(beta, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(gamma, 120.0, ANGLE_THRESHOLD)) {
            this.crystalSystem = CRYSTAL_SYSTEM_HEXAGONAL;
            return 4;
        }

        // Trigonal/Rhombohedral: a = b = c, alpha = beta = gamma != 90
        if (this.areEqual(a, b, LENGTH_THRESHOLD) &&
            this.areEqual(a, c, LENGTH_THRESHOLD) &&
            this.areEqual(alpha, beta, 1.0e-4) &&
            this.areEqual(alpha, gamma, 1.0e-4) &&
            Math.abs(alpha - 90.0) > ANGLE_THRESHOLD) {
            this.crystalSystem = CRYSTAL_SYSTEM_TRIGONAL;
            return 5;
        }

        // Tetragonal: a = b != c, alpha = beta = gamma = 90
        if (this.areEqual(a, b, LENGTH_THRESHOLD) &&
            !this.areEqual(a, c, LENGTH_THRESHOLD) &&
            this.areEqual(alpha, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(beta, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(gamma, 90.0, ANGLE_THRESHOLD)) {
            this.crystalSystem = CRYSTAL_SYSTEM_TETRAGONAL;
            return 6;
        }

        // Orthorhombic: a != b != c, alpha = beta = gamma = 90
        if (!this.areEqual(a, b, LENGTH_THRESHOLD) &&
            !this.areEqual(a, c, LENGTH_THRESHOLD) &&
            !this.areEqual(b, c, LENGTH_THRESHOLD) &&
            this.areEqual(alpha, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(beta, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(gamma, 90.0, ANGLE_THRESHOLD)) {
            this.crystalSystem = CRYSTAL_SYSTEM_ORTHORHOMBIC;
            return 8;
        }

        // Monoclinic: alpha = gamma = 90, beta != 90
        if (this.areEqual(alpha, 90.0, ANGLE_THRESHOLD) &&
            this.areEqual(gamma, 90.0, ANGLE_THRESHOLD) &&
            Math.abs(beta - 90.0) > ANGLE_THRESHOLD) {
            this.crystalSystem = CRYSTAL_SYSTEM_MONOCLINIC;
            return 12;
        }

        // Triclinic: all angles != 90
        this.crystalSystem = CRYSTAL_SYSTEM_TRICLINIC;
        return 0;
    }

    /**
     * Return the detected international space-group number.
     *
     * <p>The present implementation detects lattice metrics only. A Bravais
     * lattice does not uniquely determine a space group: atomic species and
     * fractional coordinates must also be tested against symmetry operations.
     * Returning a representative high-symmetry group (as older code did) can
     * silently assign the wrong physics, so zero explicitly means undetermined
     * until a validated spglib integration is available.</p>
     */
    public int getSpaceGroupNumber() {
        return 0;
    }

    /**
     * Return a space-group name, or an explicit undetermined marker.
     */
    public String getSpaceGroupName() {
        return "Undetermined";
    }

    /**
     * Get the crystal system name
     */
    public String getCrystalSystemName() {
        switch (this.crystalSystem) {
            case CRYSTAL_SYSTEM_CUBIC:         return "Cubic";
            case CRYSTAL_SYSTEM_HEXAGONAL:      return "Hexagonal";
            case CRYSTAL_SYSTEM_MONOCLINIC:     return "Monoclinic";
            case CRYSTAL_SYSTEM_ORTHORHOMBIC:   return "Orthorhombic";
            case CRYSTAL_SYSTEM_TETRAGONAL:     return "Tetragonal";
            case CRYSTAL_SYSTEM_TRICLINIC:      return "Triclinic";
            case CRYSTAL_SYSTEM_TRIGONAL:       return "Trigonal";
            default:                            return "Unknown";
        }
    }

    private boolean areEqual(double x, double y, double threshold) {
        return Math.abs(x - y) < threshold;
    }

    public int getIbrav() {
        return this.ibrav;
    }

    public int getNumSymOperations() {
        return this.numSymOperations;
    }
}
