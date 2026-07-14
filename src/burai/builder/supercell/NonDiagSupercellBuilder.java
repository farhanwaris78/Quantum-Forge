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

package burai.builder.supercell;

import burai.atoms.model.Atom;
import burai.atoms.model.Cell;
import burai.atoms.model.exception.ZeroVolumCellException;
import burai.com.math.Matrix3D;

/**
 * Non-diagonal supercell expansion builder.
 * 
 * NanoLabo provides two methods:
 * - As Miller Index: specify transformation matrix via Miller indices
 * - As Dual Vector: auto-calculate from reciprocal ratios
 * 
 * This enables general supercell construction beyond simple repetitions
 * along the crystal axes, e.g. for matching surfaces or creating
 * commensurate supercells.
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
     * Each row: new_{a,b,c} = matrix[i][0]*old_a + matrix[i][1]*old_b + matrix[i][2]*old_c
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
     * Calculates the smallest integer ratios from reciprocal lattice vectors.
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
     * Build the supercell with the defined transformation matrix
     */
    public Cell build() throws ZeroVolumCellException {
        if (this.originalCell == null) return null;

        double[][] oldLattice = this.originalCell.copyLattice();
        double[][] newLattice = multiplyMatrix(oldLattice, this.transformationMatrix);

        Cell newCell = new Cell(newLattice);

        // Place atoms in supercell
        int n1 = Math.abs(this.transformationMatrix[0][0])
               + Math.abs(this.transformationMatrix[0][1])
               + Math.abs(this.transformationMatrix[0][2]);
        int n2 = Math.abs(this.transformationMatrix[1][0])
               + Math.abs(this.transformationMatrix[1][1])
               + Math.abs(this.transformationMatrix[1][2]);
        int n3 = Math.abs(this.transformationMatrix[2][0])
               + Math.abs(this.transformationMatrix[2][1])
               + Math.abs(this.transformationMatrix[2][2]);

        // Ensure we capture enough repetitions
        n1 = Math.max(1, n1 + 1);
        n2 = Math.max(1, n2 + 1);
        n3 = Math.max(1, n3 + 1);

        double[][] recOld = Matrix3D.inverse(oldLattice);
        double[][] recNew = Matrix3D.inverse(newLattice);

        Atom[] oldAtoms = this.originalCell.listAtoms(true);
        if (oldAtoms == null) return newCell;

        for (Atom oldAtom : oldAtoms) {
            if (oldAtom == null || oldAtom.isSlaveAtom()) continue;

            double[] fracOld = Matrix3D.mult(
                new double[]{oldAtom.getX(), oldAtom.getY(), oldAtom.getZ()}, recOld);

            for (int i = 0; i < n1; i++) {
                for (int j = 0; j < n2; j++) {
                    for (int k = 0; k < n3; k++) {
                        double[] fracNew = {
                            (fracOld[0] + i) / n1,
                            (fracOld[1] + j) / n2,
                            (fracOld[2] + k) / n3
                        };

                        // Check if within new cell bounds
                        if (fracNew[0] >= 0 && fracNew[0] < 1.0 &&
                            fracNew[1] >= 0 && fracNew[1] < 1.0 &&
                            fracNew[2] >= 0 && fracNew[2] < 1.0) {

                            double[] cart = Matrix3D.mult(fracNew, newLattice);
                            newCell.addAtom(oldAtom.getName(), cart[0], cart[1], cart[2]);
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
        // Find smallest integer ratios that approximate the given values
        double min = Math.min(Math.min(da, db), dc);
        double[] ratios = {da / min, db / min, dc / min};

        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = Math.max(1, (int) Math.round(ratios[i]));
        }
        return result;
    }
}
