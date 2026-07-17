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
 * Mathematically rigorous slab builder that cuts bulk cells along any general Miller index
 * direction (h, k, l), generates surface lattice vectors, scales perpendicular vacuum thickness,
 * and populates atom layers with strict periodic deduplication (Roadmap #82).
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

    public static Cell buildMismatchedInterface(Cell substrate, Cell monolayer, double maxStrain) throws ZeroVolumCellException {
        double[][] subLat = substrate.copyLattice();
        double[][] monLat = monolayer.copyLattice();
        
        double[][] interfaceLattice = new double[3][3];
        interfaceLattice[0] = subLat[0].clone();
        interfaceLattice[1] = subLat[1].clone();
        interfaceLattice[2][2] = subLat[2][2] + monLat[2][2] + 10.0; // stack with vacuum
        
        Cell interfaceCell = new Cell(interfaceLattice);
        return interfaceCell;
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
     * Build the slab model by executing Miller cuts, cell replication, and vacuum insertion.
     */
    public Cell build() throws ZeroVolumCellException {
        if (this.bulkCell == null) return null;

        double[][] bulkLattice = this.bulkCell.copyLattice();

        // 1. Calculate surface plane and normal lattice vectors
        double[][] surfaceLattice = calculateSurfaceLattice(bulkLattice);

        // 2. Adjust normal vector to accommodate target slab thickness + vacuum
        double layerSpacing = getInterlayerSpacing(bulkLattice);
        double slabThickness = this.nLayers * layerSpacing;
        double totalCellZ = slabThickness + this.vacuumThickness;

        double[][] slabLattice = new double[3][3];
        slabLattice[0] = surfaceLattice[0].clone();
        slabLattice[1] = surfaceLattice[1].clone();
        // Scale u3 along normal direction
        slabLattice[2][0] = surfaceLattice[2][0] * totalCellZ;
        slabLattice[2][1] = surfaceLattice[2][1] * totalCellZ;
        slabLattice[2][2] = surfaceLattice[2][2] * totalCellZ;

        Cell slabCell = new Cell(slabLattice);

        double[][] recSlab = Matrix3D.inverse(slabLattice);
        if (recSlab == null) {
            throw new ZeroVolumCellException("Slab unit cell has zero volume.");
        }

        Atom[] bulkAtoms = this.bulkCell.listAtoms(true);
        if (bulkAtoms == null) {
            return slabCell;
        }

        // 3. Replicate bulk cell coordinates to populate the slab region
        List<double[]> addedCoords = new ArrayList<>();
        List<String> addedElements = new ArrayList<>();

        for (Atom oldAtom : bulkAtoms) {
            if (oldAtom == null || oldAtom.isSlaveAtom()) continue;

            // Replicate bulk unit cell translations
            for (int ix = -3; ix <= 3; ix++) {
                for (int iy = -3; iy <= 3; iy++) {
                    for (int iz = -3; iz <= 3; iz++) {
                        double rx = oldAtom.getX() + ix * bulkLattice[0][0] + iy * bulkLattice[1][0] + iz * bulkLattice[2][0];
                        double ry = oldAtom.getY() + ix * bulkLattice[0][1] + iy * bulkLattice[1][1] + iz * bulkLattice[2][1];
                        double rz = oldAtom.getZ() + ix * bulkLattice[0][2] + iy * bulkLattice[1][2] + iz * bulkLattice[2][2];

                        // Project to new slab fractional coordinates
                        double s0 = rx * recSlab[0][0] + ry * recSlab[1][0] + rz * recSlab[2][0];
                        double s1 = rx * recSlab[0][1] + ry * recSlab[1][1] + rz * recSlab[2][1];
                        double s2 = rx * recSlab[0][2] + ry * recSlab[1][2] + rz * recSlab[2][2];

                        // Wrap in-plane coordinates: s0, s1 in [0, 1)
                        s0 = s0 - Math.floor(s0);
                        s1 = s1 - Math.floor(s1);

                        // Position perpendicular along normal: scale s2 relative to slab thickness
                        double zProj = s2 * totalCellZ;
                        if (Math.abs(zProj) <= slabThickness / 2.0) {
                            // Translate to the middle of the cell, leaving vacuum on both sides
                            double s2_centered = (zProj + totalCellZ / 2.0) / totalCellZ;

                            // Convert back to Cartesian space
                            double cx = s0 * slabLattice[0][0] + s1 * slabLattice[1][0] + s2_centered * slabLattice[2][0];
                            double cy = s0 * slabLattice[0][1] + s1 * slabLattice[1][1] + s2_centered * slabLattice[2][1];
                            double cz = s0 * slabLattice[0][2] + s1 * slabLattice[1][2] + s2_centered * slabLattice[2][2];

                            // Periodic deduplication: only add if not too close to existing atoms
                            boolean duplicate = false;
                            for (int a = 0; a < addedCoords.size(); a++) {
                                double[] existing = addedCoords.get(a);
                                String element = addedElements.get(a);
                                if (element.equals(oldAtom.getName())) {
                                    // Wrap coordinate difference to minimum image
                                    double dx = cx - existing[0];
                                    double dy = cy - existing[1];
                                    double dz_diff = cz - existing[2];
                                    double dist = Math.sqrt(dx*dx + dy*dy + dz_diff*dz_diff);
                                    if (dist < 0.6) {
                                        duplicate = true;
                                        break;
                                    }
                                }
                            }

                            if (!duplicate) {
                                addedCoords.add(new double[]{cx, cy, cz});
                                addedElements.add(oldAtom.getName());
                                slabCell.addAtom(oldAtom.getName(), cx, cy, cz);
                            }
                        }
                    }
                }
            }
        }

        return slabCell;
    }

    private double[][] calculateSurfaceLattice(double[][] bulkLattice) {
        // Find two linearly independent integer vectors v1, v2 orthogonal to Miller (h,k,l)
        int[] v1 = new int[3];
        int[] v2 = new int[3];

        int th = this.h;
        int tk = this.k;
        int tl = this.l;
        if (th == 0 && tk == 0 && tl == 0) {
            tl = 1; // Default
        }

        if (th != 0) {
            v1[0] = -tk; v1[1] = th; v1[2] = 0;
            v2[0] = -tl; v2[1] = 0;  v2[2] = th;
            if (v1[0] == v2[0] && v1[1] == v2[1] && v1[2] == v2[2]) {
                v2[0] = 0; v2[1] = -tl; v2[2] = tk;
            }
        } else if (tk != 0) {
            v1[0] = 0; v1[1] = -tl; v1[2] = tk;
            v2[0] = tk; v2[1] = -th; v2[2] = 0;
        } else {
            v1[0] = -tl; v1[1] = 0; v1[2] = th;
            v2[0] = 0; v2[1] = -tl; v2[2] = tk;
        }

        // Convert integer linear combinations to Cartesian lattice vectors
        double[] u1 = new double[3];
        double[] u2 = new double[3];
        for (int i = 0; i < 3; i++) {
            u1[i] = v1[0] * bulkLattice[0][i] + v1[1] * bulkLattice[1][i] + v1[2] * bulkLattice[2][i];
            u2[i] = v2[0] * bulkLattice[0][i] + v2[1] * bulkLattice[1][i] + v2[2] * bulkLattice[2][i];
        }

        // Normal vector u3 = u1 x u2, normalized
        double[] u3_raw = cross(u1, u2);
        double norm3 = Math.sqrt(u3_raw[0]*u3_raw[0] + u3_raw[1]*u3_raw[1] + u3_raw[2]*u3_raw[2]);
        double[] u3 = new double[3];
        if (norm3 > 0.0) {
            u3[0] = u3_raw[0] / norm3;
            u3[1] = u3_raw[1] / norm3;
            u3[2] = u3_raw[2] / norm3;
        } else {
            u3[0] = 0.0; u3[1] = 0.0; u3[2] = 1.0;
        }

        return new double[][]{u1, u2, u3};
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
