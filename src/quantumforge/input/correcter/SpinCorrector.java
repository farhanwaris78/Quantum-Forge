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
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.pseudo.PseudoData;
import quantumforge.pseudo.PseudoPotential;

public class SpinCorrector {

    public enum SpinType {
        NON_POLARIZED,
        COLINEAR,
        NON_COLINEAR;
    }

    private QEInput input;

    private QEAtomicSpecies cardSpecies;

    public SpinCorrector(QEInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input is null.");
        }

        this.input = input;

        this.cardSpecies = this.input.getCard(QEAtomicSpecies.class);
    }

    public boolean isAvailable() {
        if (this.cardSpecies == null || this.cardSpecies.numSpecies() < 1) {
            return false;
        }

        return true;
    }

    public SpinType getSpinType() {
        if (this.cardSpecies == null) {
            return SpinType.NON_POLARIZED;
        }

        int numSpecies = this.cardSpecies.numSpecies();

        boolean hasRelative = false;
        boolean hasTransMetal = false;

        for (int i = 0; i < numSpecies; i++) {
            PseudoPotential pseudoPot = this.cardSpecies.getPseudoPotential(i);
            if (pseudoPot != null) {
                int relative = pseudoPot.getData().getRelativistic();
                if (relative == PseudoData.RELATIVISTIC_FULL) {
                    hasRelative = true;
                }
            }

            String label = this.cardSpecies.getLabel(i);
            label = label == null ? null : label.trim();
            if (label != null && !label.isEmpty()) {
                if (ElementUtil.isTransitionMetal(label)) {
                    hasTransMetal = true;
                }
            }
        }

        if (hasRelative) {
            return SpinType.NON_COLINEAR;

        } else if (hasTransMetal) {
            return SpinType.COLINEAR;

        } else {
            return SpinType.NON_POLARIZED;
        }
    }
}
