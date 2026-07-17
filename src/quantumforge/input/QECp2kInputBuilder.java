/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input;

import java.util.Locale;
import java.util.Objects;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;

/**
 * Generates mathematically consistent and logically structured input files for the
 * CP2K molecular dynamics and electronic structure engine, mapping crystal cell lattices
 * and coordinates into nested QUICKSTEP subsys blocks (Roadmap #114).
 */
public final class QECp2kInputBuilder {

    private final Cell cell;
    private final String projectLabel;
    private final String xcFunctional; // e.g. PBE, LDA, PBEsol
    private final double cutoffRy;

    public QECp2kInputBuilder(Cell cell, String label, String xc, double cutoffRy) {
        this.cell = Objects.requireNonNull(cell, "cell");
        this.projectLabel = label == null ? "cp2k_calc" : label.trim();
        this.xcFunctional = xc == null ? "PBE" : xc.trim().toUpperCase(Locale.ROOT);
        this.cutoffRy = Math.max(10.0, cutoffRy);
    }

    public Cell getCell() { return this.cell; }
    public String getProjectLabel() { return this.projectLabel; }
    public String getXcFunctional() { return this.xcFunctional; }
    public double getCutoffRy() { return this.cutoffRy; }

    /**
     * Emits the complete, cleanly indented CP2K input file text.
     */
    public String generateInput() {
        StringBuilder sb = new StringBuilder();

        // 1. &GLOBAL section
        sb.append("&GLOBAL\n");
        sb.append("  PROJECT ").append(projectLabel).append("\n");
        sb.append("  RUN_TYPE ENERGY_FORCE\n");
        sb.append("  PRINT_LEVEL MEDIUM\n");
        sb.append("&END GLOBAL\n\n");

        // 2. &FORCE_EVAL section
        sb.append("&FORCE_EVAL\n");
        sb.append("  METHOD Quickstep\n");
        
        // &DFT subsection
        sb.append("  &DFT\n");
        sb.append("    BASIS_SET_FILE_NAME BASIS_MOLOPT\n");
        sb.append("    POTENTIAL_FILE_NAME GTH_POTENTIALS\n");
        sb.append("    &MGRID\n");
        sb.append(String.format(Locale.ROOT, "      CUTOFF %.1f\n", cutoffRy));
        sb.append("    &END MGRID\n");
        
        sb.append("    &XC\n");
        sb.append("      &XC_FUNCTIONAL ").append(xcFunctional).append("\n");
        sb.append("      &END XC_FUNCTIONAL\n");
        sb.append("    &END XC\n");
        sb.append("  &END DFT\n\n");

        // &SUBSYS subsection (holds crystal geometry)
        sb.append("  &SUBSYS\n");
        
        // &CELL block (lattice vectors in Angstroms)
        double[][] lattice = cell.copyLattice();
        sb.append("    &CELL\n");
        sb.append(String.format(Locale.ROOT, "      A  %12.6f %12.6f %12.6f\n", lattice[0][0], lattice[0][1], lattice[0][2]));
        sb.append(String.format(Locale.ROOT, "      B  %12.6f %12.6f %12.6f\n", lattice[1][0], lattice[1][1], lattice[1][2]));
        sb.append(String.format(Locale.ROOT, "      C  %12.6f %12.6f %12.6f\n", lattice[2][0], lattice[2][1], lattice[2][2]));
        sb.append("    &END CELL\n\n");

        // &COORD block (Cartesian site positions in Angstroms)
        sb.append("    &COORD\n");
        Atom[] atoms = cell.listAtoms(true);
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom == null || atom.isSlaveAtom()) continue;
                sb.append(String.format(Locale.ROOT, "      %-4s %12.6f %12.6f %12.6f\n", 
                    atom.getName(), atom.getX(), atom.getY(), atom.getZ()));
            }
        }
        sb.append("    &END COORD\n");
        sb.append("  &END SUBSYS\n");

        sb.append("&END FORCE_EVAL\n");

        return sb.toString();
    }
}
