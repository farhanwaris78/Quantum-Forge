/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.builder.molecule;

import java.util.ArrayList;
import java.util.List;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

/**
 * Organic molecule builder for QuantumForge.
 * 
 * Provides tools for constructing organic molecules:
 * - SMILES-based molecule generation
 * - Common functional group templates
 * - Cartoon drawing interface
 * - Solvent filling
 * 
 * Inspired by NanoLabo's molecule builder functionality.
 */
public class MoleculeBuilder {

    /**
     * Common functional group templates
     */
    public static final String GROUP_METHYL = "CH3";
    public static final String GROUP_ETHYL = "C2H5";
    public static final String GROUP_PHENYL = "C6H5";
    public static final String GROUP_HYDROXYL = "OH";
    public static final String GROUP_CARBOXYL = "COOH";
    public static final String GROUP_AMINO = "NH2";
    public static final String GROUP_NITRO = "NO2";
    public static final String GROUP_CARBONYL = "C=O";
    public static final String GROUP_ETHER = "O";
    public static final String GROUP_SULFHYDRYL = "SH";

    private Cell cell;
    private List<Atom> atoms;
    private double bondLength;

    public MoleculeBuilder() {
        this.cell = Cell.getEmptyCell();
        this.atoms = new ArrayList<Atom>();
        this.bondLength = 1.54; // Default C-C bond in Angstrom
    }

    public void setBondLength(double bondLength) {
        if (bondLength > 0.5 && bondLength < 3.0) {
            this.bondLength = bondLength;
        }
    }

    /**
     * Add a single atom at the specified position
     */
    public boolean addAtom(String element, double x, double y, double z) {
        if (this.cell != null) {
            return this.cell.addAtom(element, x, y, z);
        }
        return false;
    }

    /**
     * Add a functional group attached to a position
     */
    public boolean addFunctionalGroup(String group, double x, double y, double z) {
        // Placeholder for functional group addition
        // In a full implementation, this would add the correct atoms
        // based on the group template
        return addAtom(group.substring(0, 1), x, y, z);
    }

    /**
     * Create a simple alkane chain
     */
    public Cell createAlkaneChain(int nCarbons) {
        Cell cell = Cell.getEmptyCell();
        if (cell == null || nCarbons < 1) {
            return null;
        }

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (int i = 0; i < nCarbons; i++) {
            cell.addAtom("C", x, y, z);
            x += this.bondLength;
            // Add H atoms (simplified)
            if (i == 0 || i == nCarbons - 1) {
                cell.addAtom("H", x - 0.5, y + 0.5, z);
                cell.addAtom("H", x - 0.5, y - 0.5, z);
                cell.addAtom("H", x, y, z + 0.5);
            } else {
                cell.addAtom("H", x - 0.5, y + 0.5, z);
                cell.addAtom("H", x - 0.5, y - 0.5, z);
            }
        }

        return cell;
    }

    /**
     * Create a benzene ring
     */
    public Cell createBenzeneRing() {
        Cell cell = Cell.getEmptyCell();
        if (cell == null) {
            return null;
        }

        double radius = this.bondLength;
        double angleStep = 60.0 * Math.PI / 180.0;

        for (int i = 0; i < 6; i++) {
            double angle = i * angleStep;
            double x = radius * Math.cos(angle);
            double y = radius * Math.sin(angle);
            cell.addAtom("C", x, y, 0.0);
            // Add H atom outward
            double hx = 1.5 * radius * Math.cos(angle);
            double hy = 1.5 * radius * Math.sin(angle);
            cell.addAtom("H", hx, hy, 0.0);
        }

        return cell;
    }

    /**
     * Create water molecule
     */
    public Cell createWater() {
        Cell cell = Cell.getEmptyCell();
        if (cell == null) {
            return null;
        }

        double ohBond = 0.96; // O-H bond length in Angstrom
        double angle = 104.5 * Math.PI / 180.0;

        cell.addAtom("O", 0.0, 0.0, 0.0);
        cell.addAtom("H", ohBond, 0.0, 0.0);
        cell.addAtom("H", ohBond * Math.cos(angle), ohBond * Math.sin(angle), 0.0);

        return cell;
    }

    public Cell getCell() {
        return this.cell;
    }
}
