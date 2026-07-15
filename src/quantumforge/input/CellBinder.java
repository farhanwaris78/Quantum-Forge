/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.input;

import java.util.List;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.atoms.model.property.AtomProperty;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QECard;
import quantumforge.input.card.QECardEvent;
import quantumforge.input.card.QECellParameters;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

public class CellBinder {

    private QEGeometryInput input;

    public CellBinder(QEGeometryInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        this.input = input;
    }

    public void bindCell(Cell cell) {
        if (cell == null) {
            return;
        }

        this.bindBySystem(cell);
        this.bindCellParameters(cell);
        this.bindAtomicPositions(cell);
    }

    private void bindBySystem(Cell cell) {
        QENamelist nmlSystem = this.input.getNamelist(QEInput.NAMELIST_SYSTEM);
        if (nmlSystem == null) {
            return;
        }

        nmlSystem.addListener("ibrav", value -> this.updateLattice(cell));
        nmlSystem.addListener("a", value -> this.updateLattice(cell));
        nmlSystem.addListener("b", value -> this.updateLattice(cell));
        nmlSystem.addListener("c", value -> this.updateLattice(cell));
        nmlSystem.addListener("cosbc", value -> this.updateLattice(cell));
        nmlSystem.addListener("cosac", value -> this.updateLattice(cell));
        nmlSystem.addListener("cosab", value -> this.updateLattice(cell));
        nmlSystem.addListener("celldm(1)", value -> this.updateLattice(cell));
        nmlSystem.addListener("celldm(2)", value -> this.updateLattice(cell));
        nmlSystem.addListener("celldm(3)", value -> this.updateLattice(cell));
        nmlSystem.addListener("celldm(4)", value -> this.updateLattice(cell));
        nmlSystem.addListener("celldm(5)", value -> this.updateLattice(cell));
        nmlSystem.addListener("celldm(6)", value -> this.updateLattice(cell));
    }

    private void bindCellParameters(Cell cell) {
        QECard card = this.input.getCard(QECellParameters.CARD_NAME);
        if (card == null || !(card instanceof QECellParameters)) {
            return;
        }

        QECellParameters cellParameters = (QECellParameters) card;

        cellParameters.addListener(event -> {
            QENamelist nmlSystem = this.input.getNamelist(QEInput.NAMELIST_SYSTEM);
            if (nmlSystem == null) {
                return;
            }

            QEValue value = nmlSystem.getValue("ibrav");
            if (value != null && value.getIntegerValue() == 0) {
                this.updateLattice(cell);
            }
        });
    }

    private void updateLattice(Cell cell) {
        if (cell.booleanProperty(QEGeometryInput.MODEL_BUSY)) {
            return;
        }

        QECard card = this.input.getCard(QEAtomicPositions.CARD_NAME);
        if (card == null || !(card instanceof QEAtomicPositions)) {
            return;
        }

        QEAtomicPositions atomicPositions = (QEAtomicPositions) card;

        double[][] lattice = this.input.getLattice();
        if (lattice == null) {
            return;
        }

        this.input.setBusyWithActions(true);

        try {
            if (atomicPositions.isCrystal()) {
                if (!cell.equalsLattice(lattice)) {
                    cell.moveLattice(lattice, Cell.ATOMS_POSITION_WITH_LATTICE);
                }

            } else if (atomicPositions.isAlat()) {
                if (!cell.equalsLattice(lattice)) {
                    List<Atom> atoms = this.input.getAtoms();
                    cell.moveLattice(lattice, Cell.ATOMS_POSITION_SCALED, atoms);
                    //this.actionForAllAtoms(cell);
                }

            } else {
                if (!cell.equalsLattice(lattice)) {
                    List<Atom> atoms = this.input.getAtoms();
                    cell.moveLattice(lattice, Cell.ATOMS_POSITION_LEFT, atoms);
                    //this.actionForAllAtoms(cell);
                }
            }

        } catch (ZeroVolumCellException e) {
            //e.printStackTrace();
        }

        this.input.setBusyWithActions(false);
    }

    private void bindAtomicPositions(Cell cell) {
        QECard card = this.input.getCard(QEAtomicPositions.CARD_NAME);
        if (card == null || !(card instanceof QEAtomicPositions)) {
            return;
        }

        QEAtomicPositions atomicPositions = (QEAtomicPositions) card;

        atomicPositions.addListener(event -> {
            if (cell.booleanProperty(QEGeometryInput.MODEL_BUSY)) {
                return;
            }

            this.input.setBusyWithActions(true);

            int eventType = event.getEventType();
            int atomIndex = event.getAtomIndex();

            if (eventType == QECardEvent.EVENT_TYPE_ATOM_CHANGED) {
                this.actionOnAtomChanged(cell, atomIndex);

            } else if (eventType == QECardEvent.EVENT_TYPE_ATOM_MOVED) {
                this.actionOnAtomChanged(cell, atomIndex);

            } else if (eventType == QECardEvent.EVENT_TYPE_ATOM_ADDED) {
                this.actionOnAtomAdded(cell, atomIndex);

            } else if (eventType == QECardEvent.EVENT_TYPE_ATOM_REMOVED) {
                this.actionOnAtomRemoved(cell, atomIndex);

            } else if (eventType == QECardEvent.EVENT_TYPE_ATOM_CLEARED) {
                this.actionOnAtomsCleared(cell);

            } else {
                this.actionForAllAtoms(cell);
            }

            this.input.setBusyWithActions(false);
        });
    }

    protected static Atom pickOutAtom(Cell cell, int index) {
        Atom[] atoms = cell.listAtoms(true);
        if (atoms == null) {
            return null;
        }

        for (Atom atom : atoms) {
            if (atom == null) {
                continue;
            }
            if (index == atom.intProperty(AtomProperty.INPUT_INDEX)) {
                return atom;
            }
        }

        return null;
    }

    private void actionOnAtomChanged(Cell cell, int index) {
        Atom inpAtom = this.input.getAtom(index);
        if (inpAtom == null) {
            return;
        }

        Atom atom = pickOutAtom(cell, index);
        if (atom != null && !atom.booleanProperty(QEGeometryInput.MODEL_BUSY)) {
            atom.setName(inpAtom.getName());
            atom.moveTo(inpAtom.getX(), inpAtom.getY(), inpAtom.getZ());
            atom.setProperty(AtomProperty.FIXED_X, inpAtom.booleanProperty(AtomProperty.FIXED_X));
            atom.setProperty(AtomProperty.FIXED_Y, inpAtom.booleanProperty(AtomProperty.FIXED_Y));
            atom.setProperty(AtomProperty.FIXED_Z, inpAtom.booleanProperty(AtomProperty.FIXED_Z));
        }
    }

    private void actionOnAtomAdded(Cell cell, int index) {
        Atom atom = this.input.getAtom(index);
        if (atom == null) {
            return;
        }

        cell.addAtom(atom);
    }

    private void actionOnAtomRemoved(Cell cell, int index) {
        Atom atom = pickOutAtom(cell, index);
        if (atom != null) {
            cell.removeAtom(atom);
        }

        Atom[] atoms = cell.listAtoms(true);
        for (int i = 0; i < atoms.length; i++) {
            if (atoms[i] == null) {
                continue;
            }
            int myIndex = atoms[i].intProperty(AtomProperty.INPUT_INDEX);
            if (myIndex >= index) {
                atoms[i].setProperty(AtomProperty.INPUT_INDEX, myIndex - 1);
            }
        }
    }

    private void actionOnAtomsCleared(Cell cell) {
        cell.removeAllAtoms();
    }

    private void actionForAllAtoms(Cell cell) {
        cell.removeAllAtoms();

        cell.stopResolving();

        List<Atom> atoms = this.input.getAtoms();
        for (Atom atom : atoms) {
            if (atom != null) {
                cell.addAtom(atom);
            }
        }

        cell.restartResolving();
    }
}
