/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.adsorption;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

/**
 * Molecular adsorption on surfaces.
 * 
 * NanoLabo provides molecule adsorption with:
 * - Multiple adsorption sites (Top, Bridge, FCC, HCP)
 * - User-defined height
 * - Automatic vacuum adjustment
 * - Multiple molecule types
 */
public class MoleculeAdsorber {

    public static final int SITE_TOP = 0;
    public static final int SITE_BRIDGE = 1;
    public static final int SITE_FCC_HOLLOW = 2;
    public static final int SITE_HCP_HOLLOW = 3;

    private Cell slabCell;
    private int adsorptionSite;
    private double height;
    private Cell molecule;
    private double xPos, yPos; // fractional position on surface

    public MoleculeAdsorber(Cell slabCell) {
        this.slabCell = slabCell;
        this.adsorptionSite = SITE_TOP;
        this.height = 2.0; // Angstrom
        this.molecule = null;
    }

    public void setMolecule(Cell molecule) { this.molecule = molecule; }
    public void setAdsorptionSite(int site) { this.adsorptionSite = site; }
    public void setHeight(double height) { this.height = Math.max(1.0, height); }
    public void setPosition(double x, double y) { this.xPos = x; this.yPos = y; }

    /**
     * Build pre-defined molecules for adsorption
     */
    public static Cell createMolecule(String type) {
        Cell mol = Cell.getEmptyCell();
        if (mol == null) return null;

        switch (type.toUpperCase()) {
            case "CO":
                mol.addAtom("C", 0.0, 0.0, 0.0);
                mol.addAtom("O", 0.0, 0.0, 1.128);
                break;
            case "H2O":
                double oh = 0.96;
                double angle = 104.5 * Math.PI / 180.0;
                mol.addAtom("O", 0.0, 0.0, 0.0);
                mol.addAtom("H", oh, 0.0, 0.0);
                mol.addAtom("H", oh * Math.cos(angle), oh * Math.sin(angle), 0.0);
                break;
            case "NH3":
                double nh = 1.01;
                double tetra = 109.5 * Math.PI / 180.0;
                mol.addAtom("N", 0.0, 0.0, 0.0);
                mol.addAtom("H", nh, 0.0, 0.0);
                mol.addAtom("H", nh * Math.cos(tetra), nh * Math.sin(tetra), 0.0);
                mol.addAtom("H", nh * Math.cos(tetra), -nh * Math.sin(tetra), 0.0);
                break;
            case "OH":
                mol.addAtom("O", 0.0, 0.0, 0.0);
                mol.addAtom("H", 0.0, 0.0, 0.97);
                break;
            case "NO":
                mol.addAtom("N", 0.0, 0.0, 0.0);
                mol.addAtom("O", 0.0, 0.0, 1.15);
                break;
        }
        return mol;
    }

    /**
     * Adsorb the molecule on the slab surface
     */
    public Cell adsorb() {
        if (this.slabCell == null || this.molecule == null) return this.slabCell;

        double[][] lattice = this.slabCell.copyLattice();

        // Find surface z-position
        double[][] recLattice = Matrix3D.inverse(lattice);
        double surfZ = getSurfaceZ();
        double molCenterZ = surfZ + this.height;

        // Place molecule atoms on surface
        Atom[] molAtoms = this.molecule.listAtoms(false);

        // Calculate molecular center offset
        double cx = 0, cy = 0, cz = 0;
        int count = 0;
        if (molAtoms != null) {
            for (Atom atom : molAtoms) {
                if (atom != null) { cx += atom.getX(); cy += atom.getY(); cz += atom.getZ(); count++; }
            }
            if (count > 0) { cx /= count; cy /= count; cz /= count; }

            for (Atom atom : molAtoms) {
                if (atom != null) {
                    // Position relative to molecular center, placed at adsorption site
                    double x = this.xPos * lattice[0][0] + this.yPos * lattice[1][0] + (atom.getX() - cx);
                    double y = this.xPos * lattice[0][1] + this.yPos * lattice[1][1] + (atom.getY() - cy);
                    double z = molCenterZ + (atom.getZ() - cz);
                    this.slabCell.addAtom(atom.getName(), x, y, z);
                }
            }
        }

        return this.slabCell;
    }

    private double getSurfaceZ() {
        double zMax = Double.NEGATIVE_INFINITY;
        Atom[] atoms = this.slabCell.listAtoms(true);
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom != null && !atom.isSlaveAtom()) {
                    if (atom.getZ() > zMax) zMax = atom.getZ();
                }
            }
        }
        return zMax;
    }
}
