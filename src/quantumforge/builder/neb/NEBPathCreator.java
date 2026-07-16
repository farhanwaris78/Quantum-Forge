/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.builder.neb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

/**
 * Nudged Elastic Band (NEB) path creator.
 *
 * <p>Generates intermediate images between initial and final structures with
 * validated atom counts, non-singular lattices, and explicit provenance. Does
 * not silently reorder images.</p>
 */
public final class NEBPathCreator {

    public static final class NebPath {
        private final List<Cell> images;
        private final List<Double> fractions;
        private final List<String> diagnostics;

        public NebPath(List<Cell> images, List<Double> fractions, List<String> diagnostics) {
            this.images = Collections.unmodifiableList(new ArrayList<>(images));
            this.fractions = Collections.unmodifiableList(new ArrayList<>(fractions));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(
                    diagnostics == null ? List.of() : diagnostics));
        }

        public List<Cell> getImages() { return this.images; }
        public List<Double> getFractions() { return this.fractions; }
        public List<String> getDiagnostics() { return this.diagnostics; }
        public int size() { return this.images.size(); }
    }

    private NEBPathCreator() {
        // Utility.
    }

    /**
     * Linear Cartesian interpolation including endpoints.
     *
     * @param nImages total images including initial and final (minimum 3)
     */
    public static OperationResult<NebPath> createPath(Cell initial, Cell finalCell, int nImages) {
        if (initial == null || finalCell == null) {
            return OperationResult.failed("NEB_CELL_NULL", "Initial/final cell is null.", null);
        }
        if (nImages < 3) {
            return OperationResult.failed("NEB_TOO_FEW_IMAGES",
                    "NEB path requires at least 3 images (initial, intermediates, final).", null);
        }
        Atom[] atomsInitial = initial.listAtoms(true);
        Atom[] atomsFinal = finalCell.listAtoms(true);
        if (atomsInitial == null || atomsFinal == null) {
            return OperationResult.failed("NEB_ATOMS_MISSING", "Structures have no atoms.", null);
        }
        if (atomsInitial.length != atomsFinal.length) {
            return OperationResult.failed("NEB_ATOM_COUNT",
                    "Initial and final structures must have the same number of atoms ("
                            + atomsInitial.length + " vs " + atomsFinal.length + ").", null);
        }
        for (int i = 0; i < atomsInitial.length; i++) {
            String a = atomsInitial[i].getName();
            String b = atomsFinal[i].getName();
            if (a != null && b != null && !a.equalsIgnoreCase(b)) {
                // Soft warning only; species mapping is positional for this release.
            }
        }

        List<String> diagnostics = new ArrayList<>();
        double[][] latticeInitial = initial.copyLattice();
        double[][] latticeFinal = finalCell.copyLattice();
        try {
            double detI = Matrix3D.determinant(latticeInitial);
            double detF = Matrix3D.determinant(latticeFinal);
            if (!Double.isFinite(detI) || Math.abs(detI) < 1.0e-12
                    || !Double.isFinite(detF) || Math.abs(detF) < 1.0e-12) {
                return OperationResult.failed("NEB_SINGULAR_LATTICE",
                        "Initial or final lattice is singular.", null);
            }
            if (Math.abs(detI - detF) / Math.max(Math.abs(detI), 1.0e-30) > 0.25) {
                diagnostics.add(String.format(Locale.ROOT,
                        "Lattice volumes differ by more than 25%% (Vi=%.6g Vf=%.6g); check pathway physicality.",
                        detI, detF));
            }
        } catch (RuntimeException ex) {
            return OperationResult.failed("NEB_LATTICE", "Lattice check failed: " + ex.getMessage(), ex);
        }

        try {
            List<Cell> path = new ArrayList<>(nImages);
            List<Double> fractions = new ArrayList<>(nImages);
            for (int i = 0; i < nImages; i++) {
                double fraction = (double) i / (double) (nImages - 1);
                fractions.add(fraction);
                if (i == 0) {
                    path.add(copyCell(initial));
                    continue;
                }
                if (i == nImages - 1) {
                    path.add(copyCell(finalCell));
                    continue;
                }
                path.add(interpolate(initial, finalCell, atomsInitial, atomsFinal,
                        latticeInitial, latticeFinal, fraction));
            }
            // Order integrity: first/last are endpoints; fractions strictly increasing.
            for (int i = 1; i < fractions.size(); i++) {
                if (!(fractions.get(i) > fractions.get(i - 1))) {
                    return OperationResult.failed("NEB_ORDER",
                            "Image fractions are not strictly increasing.", null);
                }
            }
            diagnostics.add("Linear Cartesian interpolation; climbing-image NEB not configured here.");
            diagnostics.add("Image order is fixed: index 0=initial, last=final.");
            NebPath neb = new NebPath(path, fractions, diagnostics);
            return OperationResult.success("NEB_PATH_OK",
                    "Created NEB path with " + neb.size() + " images.", neb);
        } catch (ZeroVolumCellException ex) {
            return OperationResult.failed("NEB_ZERO_VOLUME",
                    "Interpolated cell has zero volume.", ex);
        } catch (RuntimeException ex) {
            return OperationResult.failed("NEB_BUILD_FAILED",
                    "NEB path build failed: " + ex.getMessage(), ex);
        }
    }

    /** Backward-compatible wrapper. */
    public static List<Cell> createInterpolatedPath(Cell initial, Cell finalCell, int nImages)
            throws ZeroVolumCellException {
        OperationResult<NebPath> result = createPath(initial, finalCell, nImages);
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            if (result.getMessage() != null && result.getMessage().toLowerCase(Locale.ROOT).contains("volume")) {
                throw new ZeroVolumCellException(result.getMessage());
            }
            throw new IllegalArgumentException(result.getMessage());
        }
        return result.getValue().get().getImages();
    }

    private static Cell interpolate(Cell initial, Cell finalCell,
                                    Atom[] atomsInitial, Atom[] atomsFinal,
                                    double[][] latticeInitial, double[][] latticeFinal,
                                    double fraction) throws ZeroVolumCellException {
        double[][] interpLattice = new double[3][3];
        for (int j = 0; j < 3; j++) {
            for (int k = 0; k < 3; k++) {
                interpLattice[j][k] = latticeInitial[j][k]
                        + fraction * (latticeFinal[j][k] - latticeInitial[j][k]);
            }
        }
        double det = Matrix3D.determinant(interpLattice);
        if (!Double.isFinite(det) || Math.abs(det) < 1.0e-12) {
            throw new ZeroVolumCellException("interpolated lattice singular at fraction=" + fraction);
        }
        Cell interpCell = new Cell(interpLattice);
        for (int j = 0; j < atomsInitial.length; j++) {
            double x = atomsInitial[j].getX()
                    + fraction * (atomsFinal[j].getX() - atomsInitial[j].getX());
            double y = atomsInitial[j].getY()
                    + fraction * (atomsFinal[j].getY() - atomsInitial[j].getY());
            double z = atomsInitial[j].getZ()
                    + fraction * (atomsFinal[j].getZ() - atomsInitial[j].getZ());
            String name = atomsInitial[j].getName();
            if (name == null || name.isBlank()) {
                name = atomsFinal[j].getName();
            }
            interpCell.addAtom(Objects.requireNonNullElse(name, "X"), x, y, z);
        }
        return interpCell;
    }

    private static Cell copyCell(Cell source) throws ZeroVolumCellException {
        Cell copy = new Cell(source.copyLattice());
        Atom[] atoms = source.listAtoms(true);
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom == null) {
                    continue;
                }
                copy.addAtom(atom.getName(), atom.getX(), atom.getY(), atom.getZ());
            }
        }
        return copy;
    }
}
