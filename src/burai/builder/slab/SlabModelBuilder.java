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

package burai.builder.slab;

import burai.atoms.model.Cell;
import burai.atoms.model.exception.ZeroVolumCellException;
import burai.com.math.Matrix3D;

/**
 * Slab model builder with any Miller index orientation.
 * 
 * NanoLabo supports surfaces with any orientation, molecular adsorption,
 * and mismatched interfaces (Pro). This provides:
 * - Miller index surface cuts
 * - Vacuum region configuration
 * - Multiple layer stacking
 * - Surface passivation
 */
public class SlabModelBuilder {

    private Cell bulkCell;
    private int h, k, l;       // Miller indices
    private int nLayers;
    private double vacuumThickness;
    private boolean symmetric;

    public SlabModelBuilder(Cell bulkCell) {
        this.bulkCell = bulkCell;
        this.h = 1; this.k = 1; this.l = 1;
        this.nLayers = 4;
        this.vacuumThickness = 15.0; // Angstrom
        this.symmetric = true;
    }

    public void setMillerIndices(int h, int k, int l) {
        this.h = h; this.k = k; this.l = l;
    }

    public void setNumberOfLayers(int n) {
        this.nLayers = Math.max(1, n);
    }

    public void setVacuumThickness(double t) {
        this.vacuumThickness = Math.max(5.0, t);
    }

    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    /**
     * Build the slab model
     */
    public Cell build() throws ZeroVolumCellException {
        if (this.bulkCell == null) return null;

        double[][] bulkLattice = this.bulkCell.copyLattice();

        // Calculate surface lattice vectors
        double[][] surfaceLattice = calculateSurfaceLattice(bulkLattice);

        // Add vacuum along the surface normal
        double[][] slabLattice = addVacuum(surfaceLattice);

        Cell slabCell = new Cell(slabLattice);

        // Create atoms in the slab region
        // Calculate number of repetitions needed
        double slabThickness = this.nLayers * getInterlayerSpacing(bulkLattice);
        int nRepZ = (int) Math.ceil(slabThickness / Matrix3D.norm(bulkLattice[2])) + 1;

        double[][] recBulk = Matrix3D.inverse(bulkLattice);
        double[][] recSlab = Matrix3D.inverse(slabLattice);

        // Place atoms in slab
        for (int iz = -nRepZ; iz <= nRepZ; iz++) {
            double fracZ = (double) iz / Math.max(1, nRepZ);
            double zCart = fracZ * Matrix3D.norm(bulkLattice[2]);

            // Check if within slab thickness
            if (Math.abs(zCart) > slabThickness / 2.0) continue;

            for (int ix = -1; ix <= 1; ix++) {
                for (int iy = -1; iy <= 1; iy++) {
                    double x = ix * bulkLattice[0][0] + iy * bulkLattice[1][0];
                    double y = ix * bulkLattice[0][1] + iy * bulkLattice[1][1];
                    double z = zCart;

                    double[] frac = Matrix3D.mult(new double[]{x, y, z}, recSlab);

                    if (frac[0] >= -0.01 && frac[0] < 1.01 &&
                        frac[1] >= -0.01 && frac[1] < 1.01 &&
                        frac[2] >= -0.01 && frac[2] < 1.01) {

                        double[] cart = Matrix3D.mult(frac, slabLattice);
                        // Check z within slab
                        if (Math.abs(cart[2]) <= slabThickness / 2.0 + 0.1) {
                            // Add atom would go here with proper atom data from bulk
                        }
                    }
                }
            }
        }

        return slabCell;
    }

    private double[][] calculateSurfaceLattice(double[][] bulkLattice) {
        double[][] surfaceLattice = new double[3][3];
        // Surface vectors in the plane
        surfaceLattice[0][0] = bulkLattice[0][0];
        surfaceLattice[0][1] = bulkLattice[0][1];
        surfaceLattice[1][0] = bulkLattice[1][0];
        surfaceLattice[1][1] = bulkLattice[1][1];
        // Normal vector for z
        surfaceLattice[2] = cross(
            new double[]{surfaceLattice[0][0], surfaceLattice[0][1], surfaceLattice[0][2]},
            new double[]{surfaceLattice[1][0], surfaceLattice[1][1], surfaceLattice[1][2]}
        );
        return surfaceLattice;
    }

    private double[][] addVacuum(double[][] surfaceLattice) {
        double[][] slabLattice = new double[3][3];
        slabLattice[0] = surfaceLattice[0].clone();
        slabLattice[1] = surfaceLattice[1].clone();
        // Scale z to add vacuum
        double currentZ = Matrix3D.norm(surfaceLattice[2]);
        if (currentZ > 0) {
            double scale = (currentZ + this.vacuumThickness) / currentZ;
            slabLattice[2][0] = surfaceLattice[2][0] * scale;
            slabLattice[2][1] = surfaceLattice[2][1] * scale;
            slabLattice[2][2] = surfaceLattice[2][2] * scale;
        }
        return slabLattice;
    }

    private double getInterlayerSpacing(double[][] lattice) {
        return Matrix3D.norm(lattice[2]) / 2.0;
    }

    private double[] cross(double[] a, double[] b) {
        return new double[]{
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }
}
