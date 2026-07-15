/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.app.project.viewer.special;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

/**
 * Logic for designing Single-Atom Catalysts.
 */
public class SACDesigner {

    public static void placeCatalystAtom(Cell sheet, int vacancyIndex, String catalystElement) {
        if (sheet == null) return;
        
        Atom[] atoms = sheet.listAtoms(true);
        if (vacancyIndex >= 0 && vacancyIndex < atoms.length) {
            Atom vacancy = atoms[vacancyIndex];
            double x = vacancy.getX();
            double y = vacancy.getY();
            double z = vacancy.getZ();
            
            sheet.removeAtom(vacancy);
            sheet.addAtom(catalystElement, x, y, z);
        }
    }
}
