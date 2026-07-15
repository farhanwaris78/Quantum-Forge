/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.modeler;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.atoms.model.property.AtomProperty;
import quantumforge.atoms.model.property.CellProperty;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.com.consts.ConstantAtoms;
import quantumforge.com.math.Matrix3D;

public abstract class ModelerBase {

    private static final int MAX_NUM_ATOMS = ConstantAtoms.MAX_NUM_ATOMS;

    public static int maxNumAtoms() {
        return MAX_NUM_ATOMS;
    }

    private static final double RMIN = 1.0e-4;
    private static final double RRMIN = RMIN * RMIN;

    protected Cell srcCell;
    protected Cell dstCell;

    protected AtomsViewer atomsViewer;

    protected ModelerBase(Cell srcCell) {
        if (srcCell == null) {
            throw new IllegalArgumentException("srcCell is null.");
        }

        this.srcCell = srcCell;
        this.dstCell = null;

        this.atomsViewer = null;

        this.copyCellForward();
    }

    public Cell getCell() {
        return this.dstCell;
    }

    public void setAtomsViewer(AtomsViewer atomsViewer) {
        this.atomsViewer = atomsViewer;
    }

    public void center() {
        if (this.atomsViewer != null) {
            this.atomsViewer.setCellToCenter();
        }
    }

    public void initialize() {
        this.copyCellForward();

        if (this.atomsViewer != null) {
            this.atomsViewer.setCellToCenter();
        }
    }

    public void reflect() {
        this.copyCellBackward();
    }

    public boolean isToReflect() {
        if (this.srcCell == null || this.dstCell == null) {
            return false;
        }

        // if srcCell is same as dstCell -> not to be reflected
        // if srcCell is different from dstCell -> to be reflected

        double[][] srcLattice = this.srcCell.copyLattice();
        double[][] dstLattice = this.srcCell.copyLattice();
        if (!Matrix3D.equals(srcLattice, dstLattice)) {
            return true;
        }

        int srcNAtom = this.srcCell.numAtoms(true);
        int dstNAtom = this.dstCell.numAtoms(true);
        if (srcNAtom != dstNAtom) {
            return true;
        }

        Atom[] srcAtoms = this.srcCell.listAtoms(true);
        Atom[] dstAtoms = this.dstCell.listAtoms(true);
        if (srcAtoms == null || dstAtoms == null) {
            return false;
        }

        int natom = srcAtoms.length;
        for (int i = 0; i < natom; i++) {
            Atom atom1 = srcAtoms[i];
            Atom atom2 = dstAtoms[i];
            if (atom1 == null || atom2 == null) {
                return false;
            }

            String name1 = atom1.getName();
            String name2 = atom2.getName();
            if (name1 == null || name2 == null) {
                return false;
            }
            if (!name1.equals(name2)) {
                return true;
            }

            double x1 = atom1.getX();
            double y1 = atom1.getY();
            double z1 = atom1.getZ();
            double x2 = atom2.getX();
            double y2 = atom2.getY();
            double z2 = atom2.getZ();
            double dx = x1 - x2;
            double dy = y1 - y2;
            double dz = z1 - z2;
            double rr = dx * dx + dy * dy + dz * dz;
            if (rr > RRMIN) {
                return true;
            }
        }

        return false;
    }

    private void copyCellForward() {
        if (this.dstCell != null) {
            this.dstCell.removeAllAtoms();
            this.dstCell.stopResolving();
        }

        // setup lattice
        double[][] lattice = this.srcCell.copyLattice();
        if (lattice == null || lattice.length < 3) {
            lattice = Matrix3D.unit();
        }

        try {
            if (this.dstCell == null) {
                this.dstCell = new Cell(lattice);
                this.dstCell.stopResolving();
            } else {
                this.dstCell.moveLattice(lattice);
            }

        } catch (ZeroVolumCellException e1) {
            e1.printStackTrace();

            try {
                if (this.dstCell == null) {
                    this.dstCell = new Cell(Matrix3D.unit());
                    this.dstCell.stopResolving();
                } else {
                    this.dstCell.moveLattice(Matrix3D.unit());
                }

            } catch (ZeroVolumCellException e2) {
                e2.printStackTrace();
            }
        }

        String axis = this.srcCell.stringProperty(CellProperty.AXIS);
        if (axis != null) {
            this.dstCell.setProperty(CellProperty.AXIS, axis);
        } else {
            this.dstCell.removeProperty(CellProperty.AXIS);
        }

        boolean molecule = this.srcCell.booleanProperty(CellProperty.MOLECULE);
        this.dstCell.setProperty(CellProperty.MOLECULE, molecule);

        // setup atoms
        Atom[] atoms = this.srcCell.listAtoms(true);
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom == null) {
                    continue;
                }

                String name = atom.getName();
                double x = atom.getX();
                double y = atom.getY();
                double z = atom.getZ();
                boolean xFixed = atom.booleanProperty(AtomProperty.FIXED_X);
                boolean yFixed = atom.booleanProperty(AtomProperty.FIXED_Y);
                boolean zFixed = atom.booleanProperty(AtomProperty.FIXED_Z);

                if (name != null && !name.isEmpty()) {
                    Atom atom_ = new Atom(name, x, y, z);
                    atom_.setProperty(AtomProperty.FIXED_X, xFixed);
                    atom_.setProperty(AtomProperty.FIXED_Y, yFixed);
                    atom_.setProperty(AtomProperty.FIXED_Z, zFixed);
                    this.dstCell.addAtom(atom_);
                }
            }
        }

        this.dstCell.restartResolving();
    }

    private void copyCellBackward() {
        if (this.dstCell == null) {
            return;
        }

        this.srcCell.removeAllAtoms();
        this.srcCell.stopResolving();

        // setup lattice
        double[][] lattice = this.dstCell.copyLattice();
        if (lattice == null || lattice.length < 3) {
            lattice = Matrix3D.unit();
        }

        try {
            this.srcCell.moveLattice(lattice);

        } catch (ZeroVolumCellException e) {
            e.printStackTrace();
            this.srcCell.restartResolving();
            return;
        }

        String axis = this.dstCell.stringProperty(CellProperty.AXIS);
        if (axis != null) {
            this.srcCell.setProperty(CellProperty.AXIS, axis);
        } else {
            this.srcCell.removeProperty(CellProperty.AXIS);
        }

        boolean molecule = this.dstCell.booleanProperty(CellProperty.MOLECULE);
        this.srcCell.setProperty(CellProperty.MOLECULE, molecule);

        // setup atoms
        Atom[] atoms = this.dstCell.listAtoms(true);
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom == null) {
                    continue;
                }

                String name = atom.getName();
                double x = atom.getX();
                double y = atom.getY();
                double z = atom.getZ();
                boolean xFixed = atom.booleanProperty(AtomProperty.FIXED_X);
                boolean yFixed = atom.booleanProperty(AtomProperty.FIXED_Y);
                boolean zFixed = atom.booleanProperty(AtomProperty.FIXED_Z);

                if (name != null && !name.isEmpty()) {
                    Atom atom_ = new Atom(name, x, y, z);
                    atom_.setProperty(AtomProperty.FIXED_X, xFixed);
                    atom_.setProperty(AtomProperty.FIXED_Y, yFixed);
                    atom_.setProperty(AtomProperty.FIXED_Z, zFixed);
                    this.srcCell.addAtom(atom_);
                }
            }
        }

        this.srcCell.restartResolving();
    }
}
