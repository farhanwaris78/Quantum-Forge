/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.input.correcter;

import quantumforge.input.QEInput;
import quantumforge.input.namelist.QEValue;

/**
 * XAFS (X-ray Absorption Fine Structure) input corrector.
 * 
 * NanoLabo provides XAFS/EELS calculation support with:
 * - Core-hole pseudopotential selection
 * - Supercell generation for isolated excited atom
 * - GIPAW pseudopotential integration
 * - Automated XANES/EXAFS workflow
 */
public class XAFSInputCorrecter extends QEInputCorrecter {

    private boolean xafsEnabled;
    private int targetAtomIndex;
    private String coreHolePP;
    private String gipawPP;

    public XAFSInputCorrecter(QEInput input) {
        super(input);
        this.xafsEnabled = false;
        this.targetAtomIndex = -1;
        this.coreHolePP = null;
        this.gipawPP = null;
    }

    public void setXAFSEnabled(boolean enabled) { this.xafsEnabled = enabled; }
    public void setTargetAtomIndex(int index) { this.targetAtomIndex = index; }
    public void setCoreHolePP(String pp) { this.coreHolePP = pp; }
    public void setGipawPP(String pp) { this.gipawPP = pp; }

    @Override
    public void correctInput() {
        if (!this.xafsEnabled) return;

        if (this.nmlControl != null) {
            this.nmlControl.setValue("calculation = 'scf'");
        }

        if (this.nmlSystem != null) {
            // XANES calculation requires specific settings
            this.nmlSystem.setValue("xunit = 'angstrom'");

            // Set up empty states for absorption spectrum
            QEValue nbnd = this.nmlSystem.getValue("nbnd");
            if (nbnd == null) {
                this.nmlSystem.setValue("nbnd = 100");
            }

            // No smearing for core-level spectroscopy
            this.nmlSystem.setValue("occupations = 'fixed'");
        }
    }

    public int getTargetAtomIndex() { return this.targetAtomIndex; }
    public String getCoreHolePP() { return this.coreHolePP; }
    public String getGipawPP() { return this.gipawPP; }
}
