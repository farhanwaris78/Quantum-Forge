/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.builder.supercell;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Grain Boundary Stitcher.
 */
public class GrainBoundaryStitcher {

    public static Cell stitch(Cell crystal1, Cell crystal2, double distance) throws ZeroVolumCellException {
        if (crystal1 == null || crystal2 == null) return null;
        
        double[][] lat1 = crystal1.copyLattice();
        double[][] lat2 = crystal2.copyLattice();
        
        // Double the cell along Z for stitching
        double[][] newLat = new double[3][3];
        newLat[0] = lat1[0].clone();
        newLat[1] = lat1[1].clone();
        newLat[2][2] = lat1[2][2] + lat2[2][2] + distance;
        
        Cell combined = new Cell(newLat);
        // Copy atoms...
        return combined;
    }
}
