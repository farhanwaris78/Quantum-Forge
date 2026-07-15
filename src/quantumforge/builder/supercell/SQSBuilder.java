/*
 * Copyright (C) 2025 QuantumForge Team
 * Proprietary and Confidential
 */

package quantumforge.builder.supercell;

import quantumforge.atoms.model.Cell;
import java.util.Random;

/**
 * Special Quasi-random Structures (SQS) Builder.
 */
public class SQSBuilder {

    public static void generateSQS(Cell cell, String[] elements, double[] concentrations) {
        if (cell == null || elements == null) return;
        
        Random rand = new Random();
        quantumforge.atoms.model.Atom[] atoms = cell.listAtoms(true);
        
        for (quantumforge.atoms.model.Atom atom : atoms) {
            double r = rand.nextDouble();
            double cum = 0.0;
            for (int i = 0; i < elements.length; i++) {
                cum += concentrations[i];
                if (r < cum) {
                    atom.setName(elements[i]);
                    break;
                }
            }
        }
    }
}
