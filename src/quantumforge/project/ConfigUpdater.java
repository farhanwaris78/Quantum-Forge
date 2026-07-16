/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.project;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QECellParameters;
import quantumforge.run.parser.GeometryParser;

/**
 * Update atomic configuration after optimization.
 * 
 * NanoLabo provides an "Update atom config" button after
 * structure optimization and MD calculations that:
 * - Reads the optimized geometry from output files
 * - Updates the project's atomic structure
 * - Refreshes the 3D viewer
 * - Preserves symmetry information
 * 
 * This is critical for workflow continuity.
 */
public class ConfigUpdater {

    private Project project;

    public ConfigUpdater(Project project) {
        this.project = project;
    }

    /**
     * Update atomic configuration from QE output files
     */
    public boolean updateFromOutput() {
        if (this.project == null) return false;
        // The legacy body below clears position cards but never inserts parsed
        // coordinates. Keep it unreachable until a transactional final-geometry
        // parser is implemented; failing is safer than corrupting every mode.
        if (!isTransactionalUpdateImplemented()) return false;

        String dirPath = this.project.getDirectoryPath();
        if (dirPath == null) return false;

        try {
            // Read optimized geometry from output
            GeometryParser parser = new GeometryParser(
                this.project.getQEInputCurrent());

            // Get the optimized cell
            Cell optimizedCell = this.project.getCell();
            if (optimizedCell == null) return false;

            // Get optimized lattice from final output structure
            String logFile = dirPath + "/" + this.project.getLogFileName();
            double[][] optimizedLattice = null;

            // In production, parse the final lattice from output
            // For now, use the existing lattice as fallback
            optimizedLattice = optimizedCell.copyLattice();

            if (optimizedLattice != null) {
                // Update cell with optimized lattice
                // This preserves the atomic positions from optimization
                boolean moved = optimizedCell.moveLattice(optimizedLattice,
                    Cell.ATOMS_POSITION_WITH_LATTICE);

                if (moved) {
                    // Update the project's QE inputs with new geometry
                    updateInputsWithNewGeometry(optimizedCell);
                    return true;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private static boolean isTransactionalUpdateImplemented() {
        return false;
    }

    private void updateInputsWithNewGeometry(Cell cell) {
        double[][] lattice = cell.copyLattice();

        // Update all calculation modes
        int[] modes = {
            Project.INPUT_MODE_GEOMETRY,
            Project.INPUT_MODE_SCF,
            Project.INPUT_MODE_OPTIMIZ,
            Project.INPUT_MODE_MD
        };

        for (int mode : modes) {
            QEInput input = this.getInputForMode(mode);
            if (input == null) continue;

            // Update cell parameters
            QECellParameters cellParams = input.getCard(QECellParameters.class);
            if (cellParams != null) {
                cellParams.setAngstrom();
                cellParams.setVector(1, lattice[0]);
                cellParams.setVector(2, lattice[1]);
                cellParams.setVector(3, lattice[2]);
            }

            // Update atomic positions
            QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
            if (positions != null) {
                positions.clear();
                Atom[] atoms = cell.listAtoms(true);
                double[][] recLattice = Matrix3D.inverse(lattice);

                if (atoms != null) {
                    for (Atom atom : atoms) {
                        if (atom == null) continue;
                        double[] frac = Matrix3D.mult(
                            new double[]{atom.getX(), atom.getY(), atom.getZ()},
                            recLattice);
                        // In production, add to positions card
                    }
                }
            }
        }
    }

    private QEInput getInputForMode(int mode) {
        int currentMode = this.project.getInputMode();
        this.project.setInputMode(mode);
        QEInput input = this.project.getQEInputCurrent();
        this.project.setInputMode(currentMode);
        return input;
    }
}
