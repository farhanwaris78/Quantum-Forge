/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.adsorption;

import java.util.ArrayList;
import java.util.List;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

/**
 * Non-destructive molecular adsorption builder on surface slabs,
 * incorporating geometric collision checks and contact distance diagnostics (Roadmap #83).
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
    
    private double minimumContactDistance = Double.MAX_VALUE;
    private boolean collisionDetected = false;
    private final List<String> diagnostics = new ArrayList<>();

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

    public double getMinimumContactDistance() { return this.minimumContactDistance; }
    public boolean isCollisionDetected() { return this.collisionDetected; }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    /**
     * Build pre-defined molecules for adsorption
     */
    public static Cell createMolecule(String type) {
        Cell mol = createMolecularCell();
        if (mol == null) return null;

        switch (type.toUpperCase()) {
            case "CO":
                mol.addAtom(new Atom("C", 0.0, 0.0, 0.0));
                mol.addAtom(new Atom("O", 0.0, 0.0, 1.128));
                break;
            case "H2O":
                double oh = 0.96;
                double angle = 104.5 * Math.PI / 180.0;
                mol.addAtom(new Atom("O", 0.0, 0.0, 0.0));
                mol.addAtom(new Atom("H", oh, 0.0, 0.0));
                mol.addAtom(new Atom("H", oh * Math.cos(angle), oh * Math.sin(angle), 0.0));
                break;
            case "NH3":
                double nh = 1.01;
                double tetra = 109.5 * Math.PI / 180.0;
                mol.addAtom(new Atom("N", 0.0, 0.0, 0.0));
                mol.addAtom(new Atom("H", nh, 0.0, 0.0));
                mol.addAtom(new Atom("H", nh * Math.cos(tetra), nh * Math.sin(tetra), 0.0));
                mol.addAtom(new Atom("H", nh * Math.cos(tetra), -nh * Math.sin(tetra), 0.0));
                break;
            case "OH":
                mol.addAtom(new Atom("O", 0.0, 0.0, 0.0));
                mol.addAtom(new Atom("H", 0.0, 0.0, 0.97));
                break;
            case "NO":
                mol.addAtom(new Atom("N", 0.0, 0.0, 0.0));
                mol.addAtom(new Atom("O", 0.0, 0.0, 1.15));
                break;
        }
        return mol;
    }

    private static Cell createMolecularCell() {
        try {
            return new Cell(Matrix3D.unit(20.0));
        } catch (Exception ex) {
            return Cell.getEmptyCell();
        }
    }

    /**
     * Adsorb the molecule on the slab surface non-destructively
     */
    public Cell adsorb() {
        if (this.slabCell == null) return null;
        if (this.molecule == null) return copyCell(this.slabCell);

        this.minimumContactDistance = Double.MAX_VALUE;
        this.collisionDetected = false;
        this.diagnostics.clear();

        double[][] lattice = this.slabCell.copyLattice();

        // Find surface z-position
        double surfZ = getSurfaceZ();
        double molCenterZ = surfZ + this.height;

        // Clone slab cell non-destructively
        Cell outputCell = copyCell(this.slabCell);

        // Place molecule atoms on surface
        Atom[] molAtoms = this.molecule.listAtoms(false);
        Atom[] slabAtoms = this.slabCell.listAtoms(true);

        // Calculate molecular center offset
        double cx = 0, cy = 0, cz = 0;
        int count = 0;
        if (molAtoms != null) {
            for (Atom atom : molAtoms) {
                if (atom != null) { cx += atom.getX(); cy += atom.getY(); cz += atom.getZ(); count++; }
            }
            if (count > 0) { cx /= count; cy /= count; cz /= count; }

            // Perform predictive collision checking before adding atoms (Roadmap #83)
            for (Atom matom : molAtoms) {
                if (matom == null) continue;
                double mx = this.xPos * lattice[0][0] + this.yPos * lattice[1][0] + (matom.getX() - cx);
                double my = this.xPos * lattice[0][1] + this.yPos * lattice[1][1] + (matom.getY() - cy);
                double mz = molCenterZ + (matom.getZ() - cz);

                // Compute distance to all existing slab atoms
                if (slabAtoms != null) {
                    for (Atom satom : slabAtoms) {
                        if (satom == null || satom.isSlaveAtom()) continue;
                        double dx = mx - satom.getX();
                        double dy = my - satom.getY();
                        double dz = mz - satom.getZ();
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (dist < this.minimumContactDistance) {
                            this.minimumContactDistance = dist;
                        }
                    }
                }
            }

            // Warning conditions for unphysical molecular overlap/collisions
            double collisionThresholdAngstrom = 1.2;
            if (this.minimumContactDistance < collisionThresholdAngstrom) {
                this.collisionDetected = true;
                this.diagnostics.add(String.format("Warning: Adsorption collision detected! Minimum contact distance is %.3f A, below the 1.2 A limit.", this.minimumContactDistance));
                this.diagnostics.add("Increase the adsorption height or adjust the fractional surface coordinates to prevent unphysical atomic overlap.");
            } else {
                this.collisionDetected = false;
                this.diagnostics.add(String.format("Molecular adsorption optimized. Minimum contact distance: %.3f A.", this.minimumContactDistance));
            }

            // Populate coordinates
            for (Atom atom : molAtoms) {
                if (atom != null) {
                    double x = this.xPos * lattice[0][0] + this.yPos * lattice[1][0] + (atom.getX() - cx);
                    double y = this.xPos * lattice[0][1] + this.yPos * lattice[1][1] + (atom.getY() - cy);
                    double z = molCenterZ + (atom.getZ() - cz);
                    outputCell.addAtom(new Atom(atom.getName(), x, y, z));
                }
            }
        }

        return outputCell;
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
        return zMax == Double.NEGATIVE_INFINITY ? 0.0 : zMax;
    }

    private static Cell copyCell(Cell source) {
        double[][] lattice = source.copyLattice();
        Cell copy = null;
        try {
            copy = new Cell(lattice);
            Atom[] atoms = source.listAtoms(true);
            if (atoms != null) {
                for (Atom atom : atoms) {
                    if (atom != null && !atom.isSlaveAtom()) {
                        copy.addAtom(new Atom(atom.getName(), atom.getX(), atom.getY(), atom.getZ()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return copy;
    }
}
