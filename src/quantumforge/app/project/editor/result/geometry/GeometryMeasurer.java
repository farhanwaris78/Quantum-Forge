/*
 * Copyright (C) 2025 QuantumForge Team
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
 * between selected atoms, matching NanoLabo's Geometric Information
 * feature in the atomic structure viewer.
 */
public class GeometryMeasurer {

    private Atom atomA;
    private Atom atomB;
    private Atom atomC;
    private Atom atomD;

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
    }

    public void setAtomA(Atom atom) { this.atomA = atom; }
    public void setAtomB(Atom atom) { this.atomB = atom; }
    public void setAtomC(Atom atom) { this.atomC = atom; }
    public void setAtomD(Atom atom) { this.atomD = atom; }

    /**
     * Calculate all geometric properties between atoms A-B-C-D
     */
    public boolean calculate() {
        if (this.atomA == null || this.atomB == null) {
            return false;
        }

        // Bond length A-B
        this.bondLengthAB = distance(this.atomA, this.atomB);

        if (this.atomC != null) {
            // Bond length B-C
            this.bondLengthBC = distance(this.atomB, this.atomC);

            // Bond angle A-B-C
            this.bondAngleABC = angle(this.atomA, this.atomB, this.atomC);

            if (this.atomD != null) {
                // Bond length C-D
                this.bondLengthCD = distance(this.atomC, this.atomD);

                // Bond angle B-C-D
                this.bondAngleBCD = angle(this.atomB, this.atomC, this.atomD);

                // Dihedral angle between planes ABC and BCD
                this.dihedralAngle = dihedral(this.atomA, this.atomB, this.atomC, this.atomD);
            }
        }

        return true;
    }

    private double distance(Atom a, Atom b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double angle(Atom a, Atom b, Atom c) {
        double[] v1 = {a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ()};
        double[] v2 = {c.getX() - b.getX(), c.getY() - b.getY(), c.getZ() - b.getZ()};

        double dot = v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
        double n1 = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]);
        double n2 = Math.sqrt(v2[0] * v2[0] + v2[1] * v2[1] + v2[2] * v2[2]);

        if (n1 == 0.0 || n2 == 0.0) return 0.0;

        double cosAngle = dot / (n1 * n2);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

        return Math.acos(cosAngle) * 180.0 / Math.PI;
    }

    private double dihedral(Atom a, Atom b, Atom c, Atom d) {
        double[] v1 = {a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ()};
        double[] v2 = {c.getX() - b.getX(), c.getY() - b.getY(), c.getZ() - b.getZ()};
        double[] v3 = {b.getX() - c.getX(), b.getY() - c.getY(), b.getZ() - c.getZ()};
        double[] v4 = {d.getX() - c.getX(), d.getY() - c.getY(), d.getZ() - c.getZ()};

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
