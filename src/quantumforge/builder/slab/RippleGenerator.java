/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.builder.slab;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

/**
 * Wrinkle & Ripple Generator for 2D sheets.
 * Studies strain-gradient and flexoelectric effects.
 */
public class RippleGenerator {

    public static void applyGaussianRipple(Cell sheet, double amplitude, double sigma) {
        if (sheet == null) return;
        
        Atom[] atoms = sheet.listAtoms(true);
        double[][] lattice = sheet.copyLattice();
        double cx = lattice[0][0] / 2.0;
        double cy = lattice[1][1] / 2.0;

        for (Atom atom : atoms) {
            double dx = atom.getX() - cx;
            double dy = atom.getY() - cy;
            double r2 = dx*dx + dy*dy;
            double dz = amplitude * Math.exp(-r2 / (2 * sigma * sigma));
            atom.moveTo(atom.getX(), atom.getY(), atom.getZ() + dz);
        }
    }
}
