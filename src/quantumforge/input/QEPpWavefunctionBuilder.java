/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input;

import java.util.Objects;

/**
 * Generates mathematically consistent and logically validated input files for the
 * Quantum ESPRESSO post-processing engine (pp.x) to extract and visualize molecular 
 * orbitals and band wavefunctions (Psi) with phase sign preservation (Roadmap #58).
 */
public final class QEPpWavefunctionBuilder {

    private final String prefix;
    private final String outdir;
    private final int kpointIndex;
    private final int bandIndex;
    private final int spinComponent; // 0 for unpolarized, 1 or 2 for spin-up/down
    private final boolean lsign;     // True to preserve wave-function phase sign (+/-)
    private final String outputFilename;

    public QEPpWavefunctionBuilder(String prefix, String outdir, int kpoint, int band,
                                   int spin, boolean lsign, String outputFilename) {
        if (kpoint <= 0) {
            throw new IllegalArgumentException("kpoint index must be positive (1-based)");
        }
        if (band <= 0) {
            throw new IllegalArgumentException("band index must be positive (1-based)");
        }

        this.prefix = prefix == null ? "espresso" : prefix.trim();
        this.outdir = outdir == null ? "./tmp" : outdir.trim();
        this.kpointIndex = kpoint;
        this.bandIndex = band;
        this.spinComponent = spin;
        this.lsign = lsign;
        this.outputFilename = outputFilename == null ? "psi_orbital.cube" : outputFilename.trim();
    }

    public String getPrefix() { return this.prefix; }
    public String getOutdir() { return this.outdir; }
    public int getKpointIndex() { return this.kpointIndex; }
    public int getBandIndex() { return this.bandIndex; }
    public int getSpinComponent() { return this.spinComponent; }
    public boolean isLsign() { return this.lsign; }
    public String getOutputFilename() { return this.outputFilename; }

    /**
     * Emits the complete, cleanly formatted pp.x input text block.
     * Uses plot_num = 7 for wave-functions.
     */
    public String generateInput() {
        StringBuilder sb = new StringBuilder();

        // &inputpp namelist
        sb.append("&inputpp\n");
        sb.append("    prefix = '").append(prefix).append("'\n");
        sb.append("    outdir = '").append(outdir).append("'\n");
        sb.append("    filplot = '").append(prefix).append("-psi.tmp'\n");
        sb.append("    plot_num = 7\n"); // 7 = wavefunction psi
        sb.append("    kpoint = ").append(kpointIndex).append("\n");
        sb.append("    kband = ").append(bandIndex).append("\n");
        if (spinComponent > 0) {
            sb.append("    spin_component = ").append(spinComponent).append("\n");
        }
        sb.append("    lsign = ").append(lsign ? ".true." : ".false.").append("\n");
        sb.append("/\n\n");

        // &plot namelist
        sb.append("&plot\n");
        sb.append("    nfile = 1\n");
        sb.append("    filepp(1) = '").append(prefix).append("-psi.tmp'\n");
        sb.append("    weight(1) = 1.0\n");
        sb.append("    iflag = 3\n");            // 3D grid plot
        sb.append("    output_format = 6\n");    // 6 = Gaussian Cube format
        sb.append("    fileout = '").append(outputFilename).append("'\n");
        sb.append("/\n");

        return sb.toString();
    }
}
