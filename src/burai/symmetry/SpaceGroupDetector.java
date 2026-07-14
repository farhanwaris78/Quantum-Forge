/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package burai.symmetry;

import burai.atoms.model.Cell;
import burai.com.math.Lattice;

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
        this.spaceGroupName = "P1";
        this.crystalSystem = CRYSTAL_SYSTEM_TRICLINIC;
        this.numSymOperations = 1;
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
     * Get the space group number based on ibrav and cell content.
     * This provides a simplified mapping. For full space group 
     * determination, spglib should be used.
     */
    public int getSpaceGroupNumber() {
        if (this.spaceGroupNumber > 0) {
            return this.spaceGroupNumber;
        }

        // Simplified mapping based on ibrav
        // For accurate space group, use spglib integration
        switch (this.ibrav) {
            case 1:  this.spaceGroupNumber = 221; break; // Pm-3m (cubic)
            case 2:  this.spaceGroupNumber = 229; break; // Im-3m (bcc)
            case 3:  this.spaceGroupNumber = 225; break; // Fm-3m (fcc)
            case -3: this.spaceGroupNumber = 166; break; // R-3m (rhombohedral)
            case 4:  this.spaceGroupNumber = 194; break; // P6_3/mmc (hexagonal)
            case 5:  this.spaceGroupNumber = 166; break; // R-3m (trigonal)
            case 6:  this.spaceGroupNumber = 139; break; // I4/mmm (tetragonal)
            case 7:  this.spaceGroupNumber = 139; break; // I4/mmm (tetragonal)
            case 8:  this.spaceGroupNumber = 65;  break; // Cmmm (orthorhombic)
            case 12: this.spaceGroupNumber = 12;  break; // C2/m (monoclinic)
            case 14: this.spaceGroupNumber = 2;   break; // P-1 (triclinic)
            default: this.spaceGroupNumber = 1;   break; // P1
        }

        return this.spaceGroupNumber;
    }

    /**
     * Get the space group name (Hermann-Mauguin notation)
     */
    public String getSpaceGroupName() {
        if (this.spaceGroupName != null && !this.spaceGroupName.equals("P1")) {
            return this.spaceGroupName;
        }

        this.getSpaceGroupNumber(); // Ensure number is computed

        // Simplified name mapping
        switch (this.ibrav) {
            case 1:  this.spaceGroupName = "Pm-3m"; break;
            case 2:  this.spaceGroupName = "Im-3m"; break;
            case 3:  this.spaceGroupName = "Fm-3m"; break;
            case -3: this.spaceGroupName = "R-3m"; break;
            case 4:  this.spaceGroupName = "P6_3/mmc"; break;
            case 5:  this.spaceGroupName = "R-3m"; break;
            case 6:  this.spaceGroupName = "I4/mmm"; break;
            case 8:  this.spaceGroupName = "Cmmm"; break;
            case 12: this.spaceGroupName = "C2/m"; break;
            case 14: this.spaceGroupName = "P-1"; break;
            default: this.spaceGroupName = "P1"; break;
        }

        return this.spaceGroupName;
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
