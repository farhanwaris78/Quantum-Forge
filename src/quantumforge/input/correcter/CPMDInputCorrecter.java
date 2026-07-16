/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.input.correcter;

import quantumforge.input.QEInput;

/**
 * Car-Parrinello Molecular Dynamics (CPMD) input corrector.
 * 
 * NanoLabo supports CPMD calculations through QE's CP code.
 * This enables:
 * - First-principles MD with CP dynamics
 * - NVE and NVT ensembles
 * - Nuclear and electronic temperature control
 */
public class CPMDInputCorrecter extends QEInputCorrecter {

    private boolean cpmdEnabled;
    private int numSteps;
    private double timeStep;
    private double electronMass;

    public CPMDInputCorrecter(QEInput input) {
        super(input);
        this.cpmdEnabled = false;
        this.numSteps = 1000;
        this.timeStep = 5.0; // a.u.
        this.electronMass = 400.0; // a.u.
    }

    public void setCPMDEnabled(boolean enabled) { this.cpmdEnabled = enabled; }
    public void setNumSteps(int steps) { this.numSteps = Math.max(10, steps); }
    public void setTimeStep(double dt) { this.timeStep = Math.max(1.0, dt); }
    public void setElectronMass(double mass) { this.electronMass = Math.max(100.0, mass); }

    @Override
    public void correctInput() {
        if (!this.cpmdEnabled) return;

        // Car-Parrinello calculations run through cp.x and use the CP input
        // schema; "calculation='cp'" is not a valid pw.x calculation mode.
        // Failing explicitly is safer than writing an input that QE rejects or,
        // worse, that appears to represent dynamics it does not configure.
        throw new UnsupportedOperationException(
                "CPMD requires a dedicated cp.x input model and is not implemented in this release.");
    }
}
