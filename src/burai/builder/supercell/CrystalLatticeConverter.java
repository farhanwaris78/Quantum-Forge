/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.builder.supercell;

import burai.atoms.model.Atom;
import burai.atoms.model.Cell;
import burai.atoms.model.exception.ZeroVolumCellException;
import burai.com.math.Lattice;
import burai.com.math.Matrix3D;

/**
 * Crystal lattice conversion utilities.
 * 
 * NanoLabo provides conversion between:
 * - Primitive cell ↔ Conventional cell
 * - Standard cell representation
 * - Niggli-reduced cell
 * 
 * These are essential for matching experimental data
 * and ensuring correct symmetry analysis.
 */
public class CrystalLatticeConverter {

    private Cell inputCell;

    public CrystalLatticeConverter(Cell cell) {
        this.inputCell = cell;
    }

    /**
     * Find and return the primitive cell
     */
    public Cell findPrimitiveCell() throws ZeroVolumCellException {
        if (this.inputCell == null) return null;

        double[][] lattice = this.inputCell.copyLattice();
        double[][] recLattice = Matrix3D.inverse(lattice);

        // Get atoms in fractional coordinates
        Atom[] atoms = this.inputCell.listAtoms(true);
        if (atoms == null) return null;

        // Use the Bravais lattice information to find primitive cell
        int ibrav = Lattice.getBravais(lattice);

        double[][] primLattice;
        switch (ibrav) {
            case 2: // BCC -> primitive
                primLattice = new double[][]{
                    {-lattice[0][0]/2 + lattice[1][1]/2 + lattice[2][2]/2,
                     lattice[0][0]/2 - lattice[1][1]/2 + lattice[2][2]/2,
                     lattice[0][0]/2 + lattice[1][1]/2 - lattice[2][2]/2},
                    {lattice[0][0]/2 - lattice[1][1]/2 + lattice[2][2]/2,
                     -lattice[0][0]/2 + lattice[1][1]/2 + lattice[2][2]/2,
                     lattice[0][0]/2 + lattice[1][1]/2 - lattice[2][2]/2},
                    {lattice[0][0]/2 + lattice[1][1]/2 - lattice[2][2]/2,
                     lattice[0][0]/2 + lattice[1][1]/2 - lattice[2][2]/2,
                     -lattice[0][0]/2 + lattice[1][1]/2 + lattice[2][2]/2}
                };
                break;

            case 3: // FCC -> primitive
            case -3:
                primLattice = new double[][]{
                    {0, lattice[0][0]/2, lattice[0][0]/2},
                    {lattice[0][0]/2, 0, lattice[0][0]/2},
                    {lattice[0][0]/2, lattice[0][0]/2, 0}
                };
                break;

            default: // Already primitive or ibrav=0
                primLattice = lattice;
                break;
        }

        Cell primCell = new Cell(primLattice);
        double[][] recPrim = Matrix3D.inverse(primLattice);

        // Convert atom positions to primitive cell
        for (Atom atom : atoms) {
            if (atom == null) continue;
            double[] frac = Matrix3D.mult(
                new double[]{atom.getX(), atom.getY(), atom.getZ()}, recLattice);
            double[] fracPrim = Matrix3D.mult(new double[]{frac[0], frac[1], frac[2]},
                Matrix3D.mult(lattice, recPrim));

            // Fold into primitive cell
            for (int i = 0; i < 3; i++) {
                fracPrim[i] = fracPrim[i] - Math.floor(fracPrim[i]);
            }

            double[] cart = Matrix3D.mult(fracPrim, primLattice);
            primCell.addAtom(atom.getName(), cart[0], cart[1], cart[2]);
        }

        return primCell;
    }

    /**
     * Find and return the conventional (standard) cell
     */
    public Cell findStandardCell() throws ZeroVolumCellException {
        if (this.inputCell == null) return null;

        double[][] lattice = this.inputCell.copyLattice();
        int ibrav = Lattice.getBravais(lattice);

        double[] celldm = Lattice.getCellDm(ibrav, lattice);
        double[][] stdCell = Lattice.getCell(ibrav, celldm);

        if (stdCell == null) {
            return null;
        }

        Cell cell = new Cell(stdCell);
        return cell;
    }
}
