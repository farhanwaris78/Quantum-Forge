/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.geometry;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

/**
 * Geometric information measurement tool.
 * 
 * Measures bond lengths, bond angles, and dihedral angles
 * between selected atoms, supporting standard Minimum Image Convention
 * for periodic triclinic boundary cells (Roadmap #79).
 */
public class GeometryMeasurer {

    private Atom atomA;
    private Atom atomB;
    private Atom atomC;
    private Atom atomD;
    private Cell cell;

    private double bondLengthAB;
    private double bondLengthBC;
    private double bondLengthCD;
    private double bondAngleABC;
    private double bondAngleBCD;
    private double dihedralAngle;

    public GeometryMeasurer() {
        this.atomA = null;
        this.atomB = null;
        this.atomC = null;
        this.atomD = null;
        this.cell = null;
    }

    public void setAtomA(Atom atom) { this.atomA = atom; }
    public void setAtomB(Atom atom) { this.atomB = atom; }
    public void setAtomC(Atom atom) { this.atomC = atom; }
    public void setAtomD(Atom atom) { this.atomD = atom; }
    public void setCell(Cell cell) { this.cell = cell; }

    /**
     * Calculate all geometric properties between atoms A-B-C-D
     */
    public boolean calculate() {
        if (this.atomA == null || this.atomB == null) {
            return false;
        }

        // Bond length A-B
        double[] vAB = getVector(this.atomB, this.atomA); // B -> A
        this.bondLengthAB = Math.sqrt(vAB[0] * vAB[0] + vAB[1] * vAB[1] + vAB[2] * vAB[2]);

        if (this.atomC != null) {
            // Bond length B-C
            double[] vBC = getVector(this.atomC, this.atomB); // C -> B
            this.bondLengthBC = Math.sqrt(vBC[0] * vBC[0] + vBC[1] * vBC[1] + vBC[2] * vBC[2]);

            // Bond angle A-B-C
            double[] vBA = getVector(this.atomB, this.atomA); // B -> A
            double[] vBC_angle = getVector(this.atomB, this.atomC); // B -> C
            this.bondAngleABC = computeAngle(vBA, vBC_angle);

            if (this.atomD != null) {
                // Bond length C-D
                double[] vCD = getVector(this.atomD, this.atomC); // D -> C
                this.bondLengthCD = Math.sqrt(vCD[0] * vCD[0] + vCD[1] * vCD[1] + vCD[2] * vCD[2]);

                // Bond angle B-C-D
                double[] vCB = getVector(this.atomC, this.atomB); // C -> B
                double[] vCD_angle = getVector(this.atomC, this.atomD); // C -> D
                this.bondAngleBCD = computeAngle(vCB, vCD_angle);

                // Dihedral angle between planes ABC and BCD
                this.dihedralAngle = computeDihedral(this.atomA, this.atomB, this.atomC, this.atomD);
            }
        }

        return true;
    }

    private double[] getVector(Atom from, Atom to) {
        if (this.cell != null) {
            return getMinimumImageVector(to, from, this.cell);
        }
        return new double[]{to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ()};
    }

    public static double[] getMinimumImageVector(Atom to, Atom from, Cell cell) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double[] diff = {dx, dy, dz};

        double[][] lattice = cell.copyLattice();
        if (lattice == null) {
            return diff;
        }

        double[][] inv = Matrix3D.inverse(lattice);
        if (inv == null) {
            return diff;
        }

        // Convert Cartesian -> Fractional: s = diff * inv
        double s0 = diff[0] * inv[0][0] + diff[1] * inv[1][0] + diff[2] * inv[2][0];
        double s1 = diff[0] * inv[0][1] + diff[1] * inv[1][1] + diff[2] * inv[2][1];
        double s2 = diff[0] * inv[0][2] + diff[1] * inv[1][2] + diff[2] * inv[2][2];

        // Minimum image convention
        s0 = s0 - Math.round(s0);
        s1 = s1 - Math.round(s1);
        s2 = s2 - Math.round(s2);

        // Convert back to Cartesian
        double rx = s0 * lattice[0][0] + s1 * lattice[1][0] + s2 * lattice[2][0];
        double ry = s0 * lattice[0][1] + s1 * lattice[1][1] + s2 * lattice[2][1];
        double rz = s0 * lattice[0][2] + s1 * lattice[1][2] + s2 * lattice[2][2];

        return new double[]{rx, ry, rz};
    }

    private double computeAngle(double[] v1, double[] v2) {
        double dot = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
        double n1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]);
        double n2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2]);

        if (n1 == 0.0 || n2 == 0.0) return 0.0;

        double cosAngle = dot / (n1 * n2);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

        return Math.acos(cosAngle) * 180.0 / Math.PI;
    }

    private double computeDihedral(Atom a, Atom b, Atom c, Atom d) {
        double[] v1 = getVector(b, a); // B -> A
        double[] v2 = getVector(b, c); // B -> C
        double[] v3 = getVector(c, b); // C -> B
        double[] v4 = getVector(c, d); // C -> D

        // Normal vectors of planes ABC and BCD
        double[] n1 = cross(v1, v2);
        double[] n2 = cross(v3, v4);

        double dot = n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2];
        double n1Norm = Math.sqrt(n1[0] * n1[0] + n1[1] * n1[1] + n1[2] * n1[2]);
        double n2Norm = Math.sqrt(n2[0] * n2[0] + n2[1] * n2[1] + n2[2] * n2[2]);

        if (n1Norm == 0.0 || n2Norm == 0.0) return 0.0;

        double cosAngle = dot / (n1Norm * n2Norm);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

        return Math.acos(cosAngle) * 180.0 / Math.PI;
    }

    private double[] cross(double[] v1, double[] v2) {
        return new double[]{
            v1[1] * v2[2] - v1[2] * v2[1],
            v1[2] * v2[0] - v1[0] * v2[2],
            v1[0] * v2[1] - v1[1] * v2[0]
        };
    }

    // Getters
    public double getBondLengthAB() { return this.bondLengthAB; }
    public double getBondLengthBC() { return this.bondLengthBC; }
    public double getBondLengthCD() { return this.bondLengthCD; }
    public double getBondAngleABC() { return this.bondAngleABC; }
    public double getBondAngleBCD() { return this.bondAngleBCD; }
    public double getDihedralAngle() { return this.dihedralAngle; }
}
