/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.builder.nanowire;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Nanowire Wrapper: Transforms 2D sheets into 1D tubes/wires.
 */
public class NanowireWrapper {

    public static Cell wrapToTube(Cell sheet, double radius) throws ZeroVolumCellException {
        if (sheet == null || radius <= 0) return null;

        double[][] lattice = sheet.copyLattice();
        double Lx = Matrix3D.norm(lattice[0]);
        
        // The circumference must match Lx
        double targetRadius = Lx / (2 * Math.PI);
        
        double[][] wireLattice = new double[3][3];
        wireLattice[0][0] = radius * 4; // Vacuum in X
        wireLattice[1][1] = radius * 4; // Vacuum in Y
        wireLattice[2] = lattice[1].clone(); // 1D periodicity along Z

        Cell wireCell = new Cell(wireLattice);

        for (Atom atom : sheet.listAtoms(true)) {
            double phi = (atom.getX() / Lx) * 2 * Math.PI;
            double r = targetRadius + atom.getZ();
            
            double x = r * Math.cos(phi);
            double y = r * Math.sin(phi);
            double z = atom.getY(); // Sheet Y becomes Wire Z
            
            wireCell.addAtom(new Atom(atom.getName(), x, y, z));
        }

        return wireCell;
    }
}
