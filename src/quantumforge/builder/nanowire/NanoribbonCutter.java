/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.builder.nanowire;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Edge-State Modeler: Cuts 2D sheets into nanoribbons.
 */
public class NanoribbonCutter {

    public static Cell createNanoribbon(Cell sheet, int width, String edgeType) throws ZeroVolumCellException {
        if (sheet == null) return null;
        
        // Assume armchair or zigzag based on orientation
        double[][] lattice = sheet.copyLattice();
        double[][] ribbonLattice = new double[3][3];
        
        // Periodic along X, vacuum in Y and Z
        ribbonLattice[0] = lattice[0].clone();
        ribbonLattice[1][1] = 20.0 + (width * lattice[1][1]);
        ribbonLattice[2][2] = 20.0;

        Cell ribbon = new Cell(ribbonLattice);
        
        for (int i = 0; i < width; i++) {
            for (Atom atom : sheet.listAtoms(true)) {
                ribbon.addAtom(new Atom(atom.getName(), atom.getX(), atom.getY() + i * lattice[1][1], atom.getZ()));
            }
        }
        
        return ribbon;
    }
}
