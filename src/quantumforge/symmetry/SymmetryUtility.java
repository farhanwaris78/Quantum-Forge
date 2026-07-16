/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.symmetry;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

/**
 * Crystal lattice conversion helpers backed by the spglib sidecar when available.
 *
 * <p>Never returns the original cell silently as if conversion succeeded.</p>
 */
public final class SymmetryUtility {

    private SymmetryUtility() {
        // Utility.
    }

    public static Cell convertToPrimitive(Cell cell) throws ZeroVolumCellException {
        return convert(cell, true);
    }

    public static Cell convertToConventional(Cell cell) throws ZeroVolumCellException {
        return convert(cell, false);
    }

    public static OperationResult<SeekPathResult> seekPath(Cell cell, double tolerance) {
        return SpglibService.detectDefault().seekPath(cell, tolerance);
    }

    private static Cell convert(Cell cell, boolean primitive) throws ZeroVolumCellException {
        if (cell == null) {
            return null;
        }
        OperationResult<StandardizedCell> result =
                SpglibService.detectDefault().standardize(cell, 1.0e-5, primitive);
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            throw new UnsupportedOperationException(
                    (primitive ? "Primitive" : "Conventional")
                            + "-cell conversion unavailable: " + result.getMessage());
        }
        return toCell(result.getValue().get());
    }

    static Cell toCell(StandardizedCell standardized) throws ZeroVolumCellException {
        double[][] lattice = standardized.getLattice();
        // Ensure non-singular.
        double det = Matrix3D.determinant(lattice);
        if (!Double.isFinite(det) || Math.abs(det) < 1.0e-12) {
            throw new ZeroVolumCellException();
        }
        Cell cell = new Cell(lattice);
        for (StandardizedCell.AtomSite site : standardized.getSites()) {
            double x = site.getX();
            double y = site.getY();
            double z = site.getZ();
            if (site.isFractional()) {
                // x_cart = frac · lattice (row-vector convention used elsewhere).
                double cx = x * lattice[0][0] + y * lattice[1][0] + z * lattice[2][0];
                double cy = x * lattice[0][1] + y * lattice[1][1] + z * lattice[2][1];
                double cz = x * lattice[0][2] + y * lattice[1][2] + z * lattice[2][2];
                x = cx;
                y = cy;
                z = cz;
            }
            String name = elementSymbol(site.getAtomicNumber());
            cell.addAtom(new Atom(name, x, y, z));
        }
        return cell;
    }

    private static String elementSymbol(int z) {
        // Minimal map for common DFT elements; unknown Z uses "X{z}".
        String[] table = {
                "X", "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne",
                "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca",
                "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
                "Ga", "Ge", "As", "Se", "Br", "Kr"
        };
        if (z > 0 && z < table.length) {
            return table[z];
        }
        return "X" + z;
    }
}
