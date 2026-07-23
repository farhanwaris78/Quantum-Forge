/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.builder.slab;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

/**
 * Janus Layer Builder: Creates asymmetric 2D materials (e.g. MoSSe).
 */
public class JanusLayerBuilder {

    public static Cell createJanus(Cell symmetricCell, String bottomElement, String topElement) {
        if (symmetricCell == null) return null;

        Cell janusCell = null;
        try {
            janusCell = new Cell(symmetricCell.copyLattice());
            Atom[] atoms = symmetricCell.listAtoms(true);
            
            // Assume 2D material with Z as normal
            double zCenter = 0.0;
            for(Atom a : atoms) zCenter += a.getZ();
            zCenter /= atoms.length;

            for (Atom atom : atoms) {
                String name = atom.getName();
                if (atom.getZ() > zCenter + 0.1) {
                    name = topElement;
                } else if (atom.getZ() < zCenter - 0.1) {
                    name = bottomElement;
                }
                janusCell.addAtom(new Atom(name, atom.getX(), atom.getY(), atom.getZ()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return janusCell;
    }
}
