/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.model.event;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Bond;

public class CellEvent extends ModelEvent {

    private double[][] lattice;

    private Atom atom;

    private Bond bond;

    public CellEvent(Object source) {
        super(source);
        this.lattice = null;
        this.atom = null;
        this.bond = null;
    }

    public void setLattice(double[][] lattice) {
        this.lattice = lattice;
    }

    public double[][] getLattice() {
        return this.lattice;
    }

    public void setAtom(Atom atom) {
        this.atom = atom;
    }

    public Atom getAtom() {
        return this.atom;
    }

    public void setBond(Bond bond) {
        this.bond = bond;
    }

    public Bond getBond() {
        return this.bond;
    }
}
