/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.builder.slab;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Electrified Double Layer (EDL) Builder.
 * Adds explicit solvent and counter-ions at the surface.
 */
public class EDLBuilder {

    public static void addElectrolyte(Cell slab, String solvent, String counterIon, int count) throws ZeroVolumCellException {
        if (slab == null) return;
        
        double[][] lattice = slab.copyLattice();
        // Place ions in the vacuum region (assuming Z is normal)
        double zStart = 15.0; // Angstrom
        
        for (int i = 0; i < count; i++) {
            double x = Math.random() * lattice[0][0];
            double y = Math.random() * lattice[1][1];
            double z = zStart + Math.random() * 5.0;
            slab.addAtom(counterIon, x, y, z);
        }
    }
}
