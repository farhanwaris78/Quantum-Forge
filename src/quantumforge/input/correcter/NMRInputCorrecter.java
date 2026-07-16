/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.input.correcter;

import quantumforge.input.QEInput;

/**
 * NMR spectrum calculation input corrector.
 * 
 * NanoLabo provides NMR spectrum calculations using
 * the GIPAW (Gauge Including Projector Augmented Wave) method
 * implemented in Quantum ESPRESSO.
 */
public class NMRInputCorrecter extends QEInputCorrecter {

    private boolean nmrEnabled;
    private boolean computeShielding;
    private boolean computeEFG;

    public NMRInputCorrecter(QEInput input) {
        super(input);
        this.nmrEnabled = false;
        this.computeShielding = true;
        this.computeEFG = false;
    }

    public void setNMREnabled(boolean enabled) { this.nmrEnabled = enabled; }
    public void setComputeShielding(boolean shield) { this.computeShielding = shield; }
    public void setComputeEFG(boolean efg) { this.computeEFG = efg; }

    @Override
    public void correctInput() {
        if (!this.nmrEnabled) return;

        // NMR shielding/EFG is a multi-step GIPAW workflow with a separate
        // gipaw.x input and validated GIPAW pseudopotentials. Arbitrarily setting
        // nbnd=50 in pw.x neither configures nor runs that workflow.
        throw new UnsupportedOperationException(
                "NMR requires a dedicated GIPAW workflow and is not implemented in this release.");
    }
}
