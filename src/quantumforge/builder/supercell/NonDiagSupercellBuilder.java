/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.supercell;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Mathematically rigorous non-diagonal supercell expansion builder.
 * 
 * Implements general 3x3 integer matrix transformations, computing optimal 
 * bounding box translations and fractional coordinate wrapping. Ensures exactly
 * |det(T)| * N_atoms are populated with absolute periodic consistency (Roadmap #81).
 */
public class NonDiagSupercellBuilder {

    private Cell originalCell;
    private int[][] transformationMatrix; // 3x3 integer matrix

    public NonDiagSupercellBuilder(Cell cell) {
        this.originalCell = cell;
        this.transformationMatrix = new int[][]{
            {1, 0, 0},
            {0, 1, 0},
            {0, 0, 1}
        };
    }

    /**
     * Set transformation using Miller indices for new lattice vectors.
     */
    public void setFromMillerIndices(int[][] matrix) {
        if (matrix == null || matrix.length != 3) return;
        for (int i = 0; i < 3; i++) {
            if (matrix[i] == null || matrix[i].length != 3) return;
        }
        this.transformationMatrix = matrix;
    }

    /**
     * Set transformation via dual vector method.
     */
    public void setFromDualVectors(double da, double db, double dc) {
        if (da <= 0 || db <= 0 || dc <= 0) return;
        int[] ratios = findIntegerRatios(da, db, dc);
        this.transformationMatrix = new int[][]{
            {ratios[0], 0, 0},
            {0, ratios[1], 0},
            {0, 0, ratios[2]}
        };
    }

    /**
     * Build the supercell using rigorous minimum image and bounding-box mapping.
     */
    public Cell build() throws ZeroVolumCellException {
        if (this.originalCell == null) return null;

        double[][] oldLattice = this.originalCell.copyLattice();
        double[][] newLattice = multiplyMatrix(oldLattice, this.transformationMatrix);

        // Determinant of transformation matrix
        double det = this.transformationMatrix[0][0] * (this.transformationMatrix[1][1] * this.transformationMatrix[2][2] - this.transformationMatrix[1][2] * this.transformationMatrix[2][1])
                   - this.transformationMatrix[0][1] * (this.transformationMatrix[1][0] * this.transformationMatrix[2][2] - this.transformationMatrix[1][2] * this.transformationMatrix[2][0])
                   + this.transformationMatrix[0][2] * (this.transformationMatrix[1][0] * this.transformationMatrix[2][1] - this.transformationMatrix[1][1] * this.transformationMatrix[2][0]);
        int multiplicity = (int) Math.round(Math.abs(det));
        if (multiplicity == 0) {
            throw new ZeroVolumCellException("Supercell transformation matrix has zero volume.");
        }

        Cell newCell = new Cell(newLattice);

        // Bounding box of original cell translations:
        // Finds min/max coordinates of the 8 vertices of the supercell in original fractional space.
        int[][] vertices = new int[8][3];
        for (int i = 0; i < 8; i++) {
            int a1 = (i & 1);
            int a2 = ((i >> 1) & 1);
            int a3 = ((i >> 2) & 1);
            vertices[i][0] = a1 * this.transformationMatrix[0][0] + a2 * this.transformationMatrix[1][0] + a3 * this.transformationMatrix[2][0];
            vertices[i][1] = a1 * this.transformationMatrix[0][1] + a2 * this.transformationMatrix[1][1] + a3 * this.transformationMatrix[2][1];
            vertices[i][2] = a1 * this.transformationMatrix[0][2] + a2 * this.transformationMatrix[1][2] + a3 * this.transformationMatrix[2][2];
        }

        int min0 = Integer.MAX_VALUE, max0 = Integer.MIN_VALUE;
        int min1 = Integer.MAX_VALUE, max1 = Integer.MIN_VALUE;
        int min2 = Integer.MAX_VALUE, max2 = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            min0 = Math.min(min0, vertices[i][0]); max0 = Math.max(max0, vertices[i][0]);
            min1 = Math.min(min1, vertices[i][1]); max1 = Math.max(max1, vertices[i][1]);
            min2 = Math.min(min2, vertices[i][2]); max2 = Math.max(max2, vertices[i][2]);
        }

        // Broaden range by 1 unit to handle edge tolerance safely
        min0--; max0++;
        min1--; max1++;
        min2--; max2++;

        // Invert transformation matrix
        double[][] T_double = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                T_double[i][j] = this.transformationMatrix[i][j];
            }
        }
        double[][] T_inv = Matrix3D.inverse(T_double);
        if (T_inv == null) {
            throw new ZeroVolumCellException("Supercell transformation matrix is not invertible.");
        }

        double[][] recOld = Matrix3D.inverse(oldLattice);
        if (recOld == null) {
            throw new ZeroVolumCellException("Original cell lattice is not invertible.");
        }

        Atom[] oldAtoms = this.originalCell.listAtoms(true);
        if (oldAtoms == null) return newCell;

        double eps = 1.0e-5;

        for (Atom oldAtom : oldAtoms) {
            if (oldAtom == null || oldAtom.isSlaveAtom()) continue;

            double[] fracOld = Matrix3D.mult(
                new double[]{oldAtom.getX(), oldAtom.getY(), oldAtom.getZ()}, recOld);

            for (int i = min0; i <= max0; i++) {
                for (int j = min1; j <= max1; j++) {
                    for (int k = min2; k <= max2; k++) {
                        double[] translatedFrac = {fracOld[0] + i, fracOld[1] + j, fracOld[2] + k};

                        // Convert to new cell's fractional space: s_new = (s_old + n) * T_inv
                        double s0 = translatedFrac[0] * T_inv[0][0] + translatedFrac[1] * T_inv[1][0] + translatedFrac[2] * T_inv[2][0];
                        double s1 = translatedFrac[0] * T_inv[0][1] + translatedFrac[1] * T_inv[1][1] + translatedFrac[2] * T_inv[2][1];
                        double s2 = translatedFrac[0] * T_inv[0][2] + translatedFrac[1] * T_inv[1][2] + translatedFrac[2] * T_inv[2][2];

                        // Inside new cell boundary checks: [0, 1)
                        if (s0 >= -eps && s0 < 1.0 - eps &&
                            s1 >= -eps && s1 < 1.0 - eps &&
                            s2 >= -eps && s2 < 1.0 - eps) {

                            double[] cart = Matrix3D.mult(new double[]{s0, s1, s2}, newLattice);
                            newCell.addAtom(new Atom(oldAtom.getName(), cart[0], cart[1], cart[2]));
                        }
                    }
                }
            }
        }

        return newCell;
    }

    private double[][] multiplyMatrix(double[][] lattice, int[][] transform) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                result[i][j] = 0.0;
                for (int k = 0; k < 3; k++) {
                    result[i][j] += transform[i][k] * lattice[k][j];
                }
            }
        }
        return result;
    }

    private int[] findIntegerRatios(double da, double db, double dc) {
        double min = Math.min(Math.min(da, db), dc);
        double[] ratios = {da / min, db / min, dc / min};

        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = Math.max(1, (int) Math.round(ratios[i]));
        }
        return result;
    }
}
