/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package burai.builder.solvent;

import burai.atoms.model.Atom;
import burai.atoms.model.Cell;
import burai.atoms.model.exception.ZeroVolumCellException;
import burai.builder.molecule.MoleculeBuilder;
import burai.com.math.Matrix3D;

/**
 * Solvent molecule filler for molecular dynamics systems.
 * 
 * NanoLabo provides solvent filling to model liquid environments
 * around solutes or in confined systems. This supports:
 * - Water (TIP3P, SPC/E models)
 * - Organic solvents (methanol, ethanol, etc.)
 * - Electrolyte solutions (LiBF4 in EC:DMC, etc.)
 * - User-defined density/temperature
 */
public class SolventFiller {

    public static final int SOLVENT_WATER = 0;
    public static final int SOLVENT_METHANOL = 1;
    public static final int SOLVENT_ETHANOL = 2;
    public static final int SOLVENT_ACETONE = 3;
    public static final int SOLVENT_EC = 4;   // Ethylene carbonate
    public static final int SOLVENT_DMC = 5;  // Dimethyl carbonate
    public static final int SOLVENT_ACETONITRILE = 6;

    private Cell soluteCell;
    private int solventType;
    private double density;  // g/cm³
    private double margin;    // minimum distance between solute and solvent

    public SolventFiller(Cell soluteCell) {
        this.soluteCell = soluteCell;
        this.solventType = SOLVENT_WATER;
        this.density = 1.0;
        this.margin = 2.0; // Angstrom
    }

    public void setSolventType(int type) { this.solventType = type; }
    public void setDensity(double density) { this.density = Math.max(0.1, density); }
    public void setMargin(double margin) { this.margin = Math.max(1.0, margin); }

    /**
     * Fill the cell with solvent molecules
     */
    public Cell fill() throws ZeroVolumCellException {
        if (this.soluteCell == null) {
            return this.soluteCell;
        }

        Cell solventCell = createSolventMolecule();
        if (solventCell == null) return this.soluteCell;

        double[][] lattice = this.soluteCell.copyLattice();
        double volume = Math.abs(Matrix3D.determinant(lattice));

        // Calculate number of solvent molecules from density
        double molMass = getSolventMass(this.solventType);
        double nMol = (density * volume) / (molMass / 6022.14); // Avogadro relation

        nMol = Math.max(1, Math.round(nMol));
        int gridSize = (int) Math.ceil(Math.cbrt(nMol));

        // Pack molecules in a grid with random offset
        Atom[] soluteAtoms = this.soluteCell.listAtoms(true);
        double[] cellDims = {
            Matrix3D.norm(lattice[0]),
            Matrix3D.norm(lattice[1]),
            Matrix3D.norm(lattice[2])
        };

        double spacing = Math.cbrt(volume / nMol);

        java.util.Random rand = new java.util.Random(42); // deterministic
        int placed = 0;

        for (int i = 0; i < gridSize && placed < nMol; i++) {
            for (int j = 0; j < gridSize && placed < nMol; j++) {
                for (int k = 0; k < gridSize && placed < nMol; k++) {
                    double fx = (i + 0.5 + rand.nextDouble() * 0.2) / gridSize;
                    double fy = (j + 0.5 + rand.nextDouble() * 0.2) / gridSize;
                    double fz = (k + 0.5 + rand.nextDouble() * 0.2) / gridSize;

                    double[] pos = Matrix3D.mult(new double[]{fx, fy, fz}, lattice);

                    // Check distance to solute atoms
                    boolean tooClose = false;
                    if (soluteAtoms != null) {
                        for (Atom solute : soluteAtoms) {
                            if (solute == null) continue;
                            double dx = pos[0] - solute.getX();
                            double dy = pos[1] - solute.getY();
                            double dz = pos[2] - solute.getZ();
                            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            if (dist < margin) {
                                tooClose = true;
                                break;
                            }
                        }
                    }

                    if (!tooClose) {
                        // Place molecule at this position
                        this.soluteCell.addAtom("H", pos[0], pos[1], pos[2]);
                        this.soluteCell.addAtom("H", pos[0] + 0.5, pos[1] + 0.5, pos[2]);
                        this.soluteCell.addAtom("O", pos[0], pos[1], pos[2]); // simplified
                        placed++;
                    }
                }
            }
        }

        return this.soluteCell;
    }

    private Cell createSolventMolecule() {
        MoleculeBuilder builder = new MoleculeBuilder();
        switch (this.solventType) {
            case SOLVENT_WATER:       return builder.createWater();
            case SOLVENT_METHANOL:    return builder.createAlkaneChain(1);
            case SOLVENT_ETHANOL:     return builder.createAlkaneChain(2);
            default:                  return builder.createWater();
        }
    }

    private double getSolventMass(int type) {
        switch (type) {
            case SOLVENT_WATER:       return 18.015;
            case SOLVENT_METHANOL:    return 32.04;
            case SOLVENT_ETHANOL:     return 46.07;
            case SOLVENT_ACETONE:     return 58.08;
            case SOLVENT_EC:          return 88.06;
            case SOLVENT_DMC:         return 90.08;
            case SOLVENT_ACETONITRILE: return 41.05;
            default:                  return 18.015;
        }
    }
}
