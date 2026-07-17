/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input;

import java.util.Objects;

/**
 * Generates mathematically consistent and logically validated input files for the
 * Quantum ESPRESSO post-processing engine (pp.x) to extract and format 3D volumetric
 * charge densities or electrostatic potentials (Roadmap #55).
 */
public final class QEPpChargePotentialBuilder {

    private final String prefix;
    private final String outdir;
    private final boolean isPotential; // True for electrostatic potential (plot_num=11), False for charge density (plot_num=0)
    private final String outputFilename;

    public QEPpChargePotentialBuilder(String prefix, String outdir, boolean isPotential, String outputFilename) {
        this.prefix = prefix == null ? "espresso" : prefix.trim();
        this.outdir = outdir == null ? "./tmp" : outdir.trim();
        this.isPotential = isPotential;
        this.outputFilename = outputFilename == null ? (isPotential ? "potential.cube" : "charge_density.cube") : outputFilename.trim();
    }

    public String getPrefix() { return this.prefix; }
    public String getOutdir() { return this.outdir; }
    public boolean isPotential() { return this.isPotential; }
    public String getOutputFilename() { return this.outputFilename; }

    /**
     * Emits the complete, cleanly formatted pp.x input text block.
     * Uses plot_num = 0 for charge density, and plot_num = 11 for electrostatic potentials.
     */
    public String generateInput() {
        StringBuilder sb = new StringBuilder();

        // &inputpp namelist
        sb.append("&inputpp\n");
        sb.append("    prefix = '").append(prefix).append("'\n");
        sb.append("    outdir = '").append(outdir).append("'\n");
        sb.append("    filplot = '").append(prefix).append("-plot.tmp'\n");
        sb.append("    plot_num = ").append(isPotential ? 11 : 0).append("\n"); // 0 = charge density, 11 = electrostatic potential
        sb.append("/\n\n");

        // &plot namelist
        sb.append("&plot\n");
        sb.append("    nfile = 1\n");
        sb.append("    filepp(1) = '").append(prefix).append("-plot.tmp'\n");
        sb.append("    weight(1) = 1.0\n");
        sb.append("    iflag = 3\n");            // 3D grid plot
        sb.append("    output_format = 6\n");    // 6 = Gaussian Cube format
        sb.append("    fileout = '").append(outputFilename).append("'\n");
        sb.append("/\n");

        return sb.toString();
    }
}
