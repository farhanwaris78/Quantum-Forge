/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.input.correcter;

import quantumforge.atoms.element.ElementUtil;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.pseudo.PseudoPotential;

public class BandCorrector {

    private QEInput input;

    private QENamelist nmlSystem;

    private QEAtomicSpecies cardSpecies;

    private QEAtomicPositions cardPositions;

    public BandCorrector(QEInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        this.input = input;

        this.nmlSystem = this.input.getNamelist(QEInput.NAMELIST_SYSTEM);

        this.cardSpecies = this.input.getCard(QEAtomicSpecies.class);

        this.cardPositions = this.input.getCard(QEAtomicPositions.class);
    }

    public boolean isAvailable() {
        if (this.cardSpecies == null || this.cardSpecies.numSpecies() < 1) {
            return false;
        }

        if (this.cardPositions == null || this.cardPositions.numPositions() < 1) {
            return false;
        }

        return true;
    }

    public int getNumBands() {
        if (this.cardSpecies == null || this.cardPositions == null) {
            return 0;
        }

        int numElems = this.cardSpecies.numSpecies();
        int[] nbandList = new int[numElems];

        for (int i = 0; i < numElems; i++) {
            String name = this.cardSpecies.getLabel(i);
            if (name == null || name.trim().isEmpty()) {
                nbandList[i] = 0;
                continue;
            }

            int valence = Math.max(0, ElementUtil.getValence(name));

            int numOrb = 0;
            if (ElementUtil.isLanthanoid(name) || ElementUtil.isActinoid(name)) {
                numOrb = 1 + 3 + 5 + 7;
            } else if (ElementUtil.isTransitionMetal(name)) {
                numOrb = 1 + 3 + 5;
            } else {
                numOrb = 1 + 3;
            }

            PseudoPotential pseudoPot = this.cardSpecies.getPseudoPotential(i);
            if (pseudoPot == null) {
                nbandList[i] = numOrb;
                continue;
            }

            double zValence = pseudoPot.getData().getZValence();
            double dValence = zValence - (double) valence;
            double occCore = 0.5 * dValence;
            int numCore = Math.max(0, (int) (occCore + 1.0 - 1.0e-6));
            nbandList[i] = numOrb + numCore;
        }

        int nbands = 0;
        int numAtoms = this.cardPositions.numPositions();
        for (int i = 0; i < numAtoms; i++) {
            String name = this.cardPositions.getLabel(i);
            int index = this.cardSpecies.indexOfSpecies(name);
            nbands += nbandList[index];
        }

        if (this.nmlSystem != null) {
            QEValue value = this.nmlSystem.getValue("noncolin");
            if (value != null && value.getLogicalValue()) {
                nbands *= 2;
            }
        }

        return nbands;
    }
}
