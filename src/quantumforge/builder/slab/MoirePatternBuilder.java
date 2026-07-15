/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.builder.slab;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Moiré Pattern Builder for twisted 2D bilayers.
 * Supports Graphene, MoS2, and other 2D materials.
 */
public class MoirePatternBuilder {

    private Cell layer1;
    private Cell layer2;
    private double twistAngle; // in degrees
    private double interlayerDistance; // in Angstrom

    public MoirePatternBuilder(Cell layer1, Cell layer2) {
        this.layer1 = layer1;
        this.layer2 = layer2;
        this.twistAngle = 0.0;
        this.interlayerDistance = 3.4; // Default for graphene
    }

    public void setTwistAngle(double angle) {
        this.twistAngle = angle;
    }

    public void setInterlayerDistance(double distance) {
        this.interlayerDistance = distance;
    }

    /**
     * Build the twisted bilayer Moiré supercell.
     * This uses a simplified commensurate supercell approximation.
     */
    public Cell build() throws ZeroVolumCellException {
        if (layer1 == null || layer2 == null) return null;

        // In a full implementation, we calculate integers (n, m) such that
        // cos(theta) = (n^2 + 4nm + m^2) / (2(n^2 + nm + m^2))
        // For simplicity, we create a reasonably sized supercell and twist.
        
        double[][] lat1 = layer1.copyLattice();
        double angleRad = twistAngle * Math.PI / 180.0;
        
        // Rotation matrix around Z
        double[][] rotZ = {
            {Math.cos(angleRad), -Math.sin(angleRad), 0},
            {Math.sin(angleRad),  Math.cos(angleRad), 0},
            {0, 0, 1}
        };
        
        double[][] lat2Twisted = Matrix3D.mult(rotZ, layer2.copyLattice());
        
        // Create a large enough supercell to minimize strain
        // This is a placeholder for the actual commensurate algorithm
        Cell moireCell = new Cell(lat1); 
        
        // Add atoms from layer 1
        for (Atom atom : layer1.listAtoms(true)) {
            moireCell.addAtom(atom.getName(), atom.getX(), atom.getY(), 0.0);
        }
        
        // Add atoms from layer 2 (twisted and shifted in Z)
        for (Atom atom : layer2.listAtoms(true)) {
            double[] pos = {atom.getX(), atom.getY(), atom.getZ()};
            double[] twistedPos = Matrix3D.mult(rotZ, pos);
            moireCell.addAtom(atom.getName(), twistedPos[0], twistedPos[1], twistedPos[2] + interlayerDistance);
        }

        return moireCell;
    }
}
