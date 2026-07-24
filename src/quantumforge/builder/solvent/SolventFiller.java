/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.solvent;

import java.util.ArrayList;
import java.util.List;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.builder.molecule.MoleculeBuilder;
import quantumforge.com.math.Matrix3D;

/**
 * Non-destructive Packmol-style solvent molecule filler for molecular dynamics boxes.
 * Generates coordinate translations, executes rigid molecule copy, and enforces strict
 * solute margin and inter-solvent overlap/collision checks (Roadmap #88).
 */
public class SolventFiller {

    public static final int SOLVENT_WATER = 0;
    public static final int SOLVENT_METHANOL = 1;
    public static final int SOLVENT_ETHANOL = 2;
    public static final int SOLVENT_ACETONE = 3;
    public static final int SOLVENT_EC = 4;   // Ethylene carbonate
    public static final int SOLVENT_DMC = 5;  // Dimethyl carbonate
    public static final int SOLVENT_ACETONITRILE = 6;

    private Cell soluteCell;
    private int solventType;
    private double density;  // g/cm³
    private double margin;    // minimum distance between solute and solvent
    
    private int moleculesPlacedCount = 0;
    private double achievedDensity = 0.0;
    private final List<String> diagnostics = new ArrayList<>();

    public SolventFiller(Cell soluteCell) {
        this.soluteCell = soluteCell;
        this.solventType = SOLVENT_WATER;
        this.density = 1.0;
        this.margin = 2.0; // Angstrom
    }

    public void setSolventType(int type) { this.solventType = type; }
    public void setDensity(double density) { this.density = Math.max(0.0, density); }
    public void setMargin(double margin) { this.margin = Math.max(1.0, margin); }

    public int getMoleculesPlacedCount() { return this.moleculesPlacedCount; }
    public double getAchievedDensity() { return this.achievedDensity; }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    /**
     * Fill the cell with solvent molecules non-destructively, preventing atomic overlaps.
     */
    public Cell fill() throws ZeroVolumCellException {
        if (this.soluteCell == null) {
            return null;
        }

        this.moleculesPlacedCount = 0;
        this.achievedDensity = 0.0;
        this.diagnostics.clear();

        Cell solventMol = createSolventMolecule();
        if (solventMol == null) {
            return copyCell(this.soluteCell);
        }

        double[][] lattice = this.soluteCell.copyLattice();
        double volume = Math.abs(Matrix3D.determinant(lattice));

        // Calculate target number of solvent molecules from density
        double molMass = getSolventMass(this.solventType);
        double nMol = (density * volume) / (molMass / 6022.14); // Avogadro relation
        nMol = Math.max(1, Math.round(nMol));

        int gridSize = (int) Math.ceil(Math.cbrt(nMol));

        // Clone solute cell non-destructively
        Cell outputCell = copyCell(this.soluteCell);

        Atom[] soluteAtoms = this.soluteCell.listAtoms(true);
        Atom[] solventAtoms = solventMol.listAtoms(true);
        if (solventAtoms == null || solventAtoms.length == 0) {
            return outputCell;
        }

        // Calculate center of mass of the solvent molecule
        double cx = 0, cy = 0, cz = 0;
        for (Atom a : solventAtoms) {
            cx += a.getX(); cy += a.getY(); cz += a.getZ();
        }
        cx /= solventAtoms.length;
        cy /= solventAtoms.length;
        cz /= solventAtoms.length;

        java.util.Random rand = new java.util.Random(42); // deterministic
        int placed = 0;

        // Pack molecules on a grid with randomized offsets (Packmol-style packing)
        for (int i = 0; i < gridSize && placed < nMol; i++) {
            for (int j = 0; j < gridSize && placed < nMol; j++) {
                for (int k = 0; k < gridSize && placed < nMol; k++) {
                    double fx = (i + 0.5 + rand.nextDouble() * 0.1) / gridSize;
                    double fy = (j + 0.5 + rand.nextDouble() * 0.1) / gridSize;
                    double fz = (k + 0.5 + rand.nextDouble() * 0.1) / gridSize;

                    double[] pos = Matrix3D.mult(new double[]{fx, fy, fz}, lattice);

                    // Perform collision checking (solute margin & inter-solvent contacts)
                    boolean collision = false;

                    // 1. Solute margin check
                    if (soluteAtoms != null) {
                        for (Atom solute : soluteAtoms) {
                            if (solute == null || solute.isSlaveAtom()) continue;
                            for (Atom solvent : solventAtoms) {
                                double sx = pos[0] + (solvent.getX() - cx);
                                double sy = pos[1] + (solvent.getY() - cy);
                                double sz = pos[2] + (solvent.getZ() - cz);

                                double dx = sx - solute.getX();
                                double dy = sy - solute.getY();
                                double dz = sz - solute.getZ();
                                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                                if (dist < this.margin) {
                                    collision = true;
                                    break;
                                }
                            }
                            if (collision) break;
                        }
                    }

                    // 2. Inter-solvent overlap check (minimum distance of 1.8 Angstrom between any atoms of different solvent molecules)
                    if (!collision) {
                        Atom[] placedAtoms = outputCell.listAtoms(true);
                        if (placedAtoms != null) {
                            for (Atom existing : placedAtoms) {
                                if (existing == null || existing.isSlaveAtom()) continue;
                                // Ignore original solute atoms (already checked)
                                if (isSoluteAtom(existing, soluteAtoms)) continue;

                                for (Atom solvent : solventAtoms) {
                                    double sx = pos[0] + (solvent.getX() - cx);
                                    double sy = pos[1] + (solvent.getY() - cy);
                                    double sz = pos[2] + (solvent.getZ() - cz);

                                    double dx = sx - existing.getX();
                                    double dy = sy - existing.getY();
                                    double dz = sz - existing.getZ();
                                    double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                                    if (dist < 1.8) {
                                        collision = true;
                                        break;
                                    }
                                }
                                if (collision) break;
                            }
                        }
                    }

                    // If safe, place the rigid solvent molecule
                    if (!collision) {
                        for (Atom solvent : solventAtoms) {
                            double sx = pos[0] + (solvent.getX() - cx);
                            double sy = pos[1] + (solvent.getY() - cy);
                            double sz = pos[2] + (solvent.getZ() - cz);
                            outputCell.addAtom(new Atom(solvent.getName(), sx, sy, sz));
                        }
                        placed++;
                    }
                }
            }
        }

        this.moleculesPlacedCount = placed;
        this.achievedDensity = (placed * (molMass / 6022.14)) / volume;

        this.diagnostics.add(String.format("Solvent filling completed. Target molecules: %d, Placed: %d.", (int)nMol, placed));
        this.diagnostics.add(String.format("Target density: %.3f g/cm3, Achieved density: %.3f g/cm3.", this.density, this.achievedDensity));

        return outputCell;
    }

    private boolean isSoluteAtom(Atom atom, Atom[] solute) {
        if (solute == null) return false;
        for (Atom s : solute) {
            if (s != null && s == atom) return true;
        }
        return false;
    }

    private Cell createSolventMolecule() {
        MoleculeBuilder builder = new MoleculeBuilder();
        switch (this.solventType) {
            case SOLVENT_WATER:       return builder.createWater();
            case SOLVENT_METHANOL:    return builder.createAlkaneChain(1);
            case SOLVENT_ETHANOL:     return builder.createAlkaneChain(2);
            default:                  return builder.createWater();
        }
    }

    private double getSolventMass(int type) {
        switch (type) {
            case SOLVENT_WATER:       return 18.015;
            case SOLVENT_METHANOL:    return 32.04;
            case SOLVENT_ETHANOL:     return 46.07;
            case SOLVENT_ACETONE:     return 58.08;
            case SOLVENT_EC:          return 88.06;
            case SOLVENT_DMC:         return 90.08;
            case SOLVENT_ACETONITRILE: return 41.05;
            default:                  return 18.015;
        }
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
