/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.util.Locale;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.operation.OperationResult;

/**
 * ASE-style extended-XYZ export of the live cell model (Roadmap #77 seam):
 * geometry only - `Lattice="a1x..a3z" Properties=species:S:1:pos:R:3 pbc="T T T"`.
 * Fail-closed on an empty cell, a near-singular or non-finite lattice and any
 * non-finite coordinate; every number is rendered losslessly (Double.toString)
 * so an independent reader reproduces the exact model values. Energy/force
 * labels are NOT fabricated here - dataset labelling belongs to the validated
 * training pipeline (#143/#144).
 */
public final class ExtXyzCellExporter {

    /** Below this |det(lattice)| in Angstrom^3 the cell is numerically degenerate. */
    public static final double MIN_LATTICE_DET = 1.0e-12;

    private ExtXyzCellExporter() { }

    /** Codes: XXYZ_EMPTY, XXYZ_LATTICE, XXYZ_VALUE. */
    public static OperationResult<String> export(Cell cell) {
        if (cell == null || cell.numAtoms(true) <= 0) {
            return OperationResult.failed("XXYZ_EMPTY",
                    "The project has no atoms to export.", null);
        }
        double[][] lattice = cell.copyLattice();
        if (lattice == null || lattice.length < 3) {
            return OperationResult.failed("XXYZ_LATTICE",
                    "The cell has no 3x3 lattice to export.", null);
        }
        for (int i = 0; i < 3; i++) {
            if (lattice[i] == null || lattice[i].length < 3) {
                return OperationResult.failed("XXYZ_LATTICE",
                        "The lattice is not a full 3x3 matrix.", null);
            }
            for (int j = 0; j < 3; j++) {
                if (!Double.isFinite(lattice[i][j])) {
                    return OperationResult.failed("XXYZ_LATTICE",
                            "The lattice contains a non-finite value at [" + (i + 1) + ","
                                    + (j + 1) + "].", null);
                }
            }
        }
        double det = lattice[0][0] * (lattice[1][1] * lattice[2][2]
                - lattice[1][2] * lattice[2][1])
                - lattice[0][1] * (lattice[1][0] * lattice[2][2]
                        - lattice[1][2] * lattice[2][0])
                + lattice[0][2] * (lattice[1][0] * lattice[2][1]
                        - lattice[1][1] * lattice[2][0]);
        if (Math.abs(det) < MIN_LATTICE_DET) {
            return OperationResult.failed("XXYZ_LATTICE",
                    "The lattice is numerically singular (|det| = " + Math.abs(det)
                            + " Angstrom^3); a degenerate cell must not be exported "
                            + "as periodic data.", null);
        }
        Atom[] atoms = cell.listAtoms(true);
        StringBuilder document = new StringBuilder();
        document.append(atoms.length).append('\n');
        StringBuilder comment = new StringBuilder("Lattice=\"");
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i > 0 || j > 0) {
                    comment.append(' ');
                }
                comment.append(Double.toString(lattice[i][j]));
            }
        }
        comment.append("\" Properties=species:S:1:pos:R:3 pbc=\"T T T\"");
        document.append(comment).append('\n');
        for (int i = 0; i < atoms.length; i++) {
            Atom atom = atoms[i];
            double x = atom.getX();
            double y = atom.getY();
            double z = atom.getZ();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return OperationResult.failed("XXYZ_VALUE",
                        "Atom " + (i + 1) + " has a non-finite coordinate.", null);
            }
            String name = atom.getName() == null || atom.getName().isBlank()
                    ? "X" : atom.getName().trim();
            document.append(String.format(Locale.ROOT, "%s %s %s %s%n", name,
                    Double.toString(x), Double.toString(y), Double.toString(z)));
        }
        return OperationResult.success("XXYZ_OK",
                "Exported " + atoms.length + " atom(s); cell volume " + Math.abs(det)
                        + " Angstrom^3.",
                document.toString());
    }
}
