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

        if (this.nmlControl != null) {
            this.nmlControl.setValue("calculation = 'scf'");
        }

        if (this.nmlSystem != null) {
            // NMR requires more empty bands
            // GIPAW pseudopotentials are needed
            this.nmlSystem.setValue("nbnd = 50");
        }
    }
}
