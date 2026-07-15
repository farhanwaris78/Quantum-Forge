/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.builder.neb;

import java.util.ArrayList;
import java.util.List;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Nudged Elastic Band (NEB) path creator.
 * Generates intermediate images between initial and final structures.
 */
public class NEBPathCreator {

    public static List<Cell> createInterpolatedPath(Cell initial, Cell finalCell, int nImages) throws ZeroVolumCellException {
        if (initial == null || finalCell == null || nImages < 3) {
            return null;
        }

        List<Cell> path = new ArrayList<>();
        path.add(initial);

        Atom[] atomsInitial = initial.listAtoms(true);
        Atom[] atomsFinal = finalCell.listAtoms(true);

        if (atomsInitial.length != atomsFinal.length) {
            throw new IllegalArgumentException("Initial and final structures must have the same number of atoms.");
        }

        double[][] latticeInitial = initial.copyLattice();
        double[][] latticeFinal = finalCell.copyLattice();

        for (int i = 1; i < nImages - 1; i++) {
            double fraction = (double) i / (nImages - 1);
            
            // Interpolate lattice
            double[][] interpLattice = new double[3][3];
            for (int j = 0; j < 3; j++) {
                for (int k = 0; j < 3; j++) {
                    interpLattice[j][k] = latticeInitial[j][k] + fraction * (latticeFinal[j][k] - latticeInitial[j][k]);
                }
            }

            Cell interpCell = new Cell(interpLattice);
            
            // Interpolate atom positions
            for (int j = 0; j < atomsInitial.length; j++) {
                double x = atomsInitial[j].getX() + fraction * (atomsFinal[j].getX() - atomsInitial[j].getX());
                double y = atomsInitial[j].getY() + fraction * (atomsFinal[j].getY() - atomsInitial[j].getY());
                double z = atomsInitial[j].getZ() + fraction * (atomsFinal[j].getZ() - atomsInitial[j].getZ());
                interpCell.addAtom(atomsInitial[j].getName(), x, y, z);
            }
            
            path.add(interpCell);
        }

        path.add(finalCell);
        return path;
    }
}
