/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package burai.input.correcter;

import burai.input.QEInput;
import burai.input.namelist.QENamelist;
import burai.input.namelist.QEValue;

/**
 * Corrector for ESM (Effective Screening Medium) method.
 * 
 * ESM is used for work function calculations of surfaces and 2D materials.
 * This is a QE 7.5 feature that NanoLabo supports.
 * 
 * Enables:
 * - Work function calculation
 * - Surface dipole correction
 * - Metal/vacuum interfaces
 */
public class ESMInputCorrecter extends QEInputCorrecter {

    public static final String ESM_BC_NONE = "none";
    public static final String ESM_BC_PBC = "pbc";
    public static final String ESM_BC_BC1 = "bc1";
    public static final String ESM_BC_BC2 = "bc2";
    public static final String ESM_BC_BC3 = "bc3";

    private boolean esmEnabled;
    private String esmBC;
    private double esmCharge;
    private boolean adjustPositions;

    public ESMInputCorrecter(QEInput input) {
        super(input);
        this.esmEnabled = false;
        this.esmBC = ESM_BC_BC2;
        this.esmCharge = 0.0;
        this.adjustPositions = true;
    }

    public void setESMEnabled(boolean enabled) {
        this.esmEnabled = enabled;
    }

    public void setESMBoundaryCondition(String bc) {
        if (bc != null) {
            this.esmBC = bc;
        }
    }

    public void setESMCharge(double charge) {
        this.esmCharge = charge;
    }

    public void setAdjustPositions(boolean adjust) {
        this.adjustPositions = adjust;
    }

    @Override
    public void correctInput() {
        if (!this.esmEnabled) {
            return;
        }

        if (this.nmlSystem == null) {
            return;
        }

        // Set assume_isolated to 'esm' for QE 7.5
        this.nmlSystem.setValue("assume_isolated = 'esm'");

        // Set ESM boundary condition
        if (this.esmBC != null) {
            this.nmlSystem.setValue("esm_bc = '" + this.esmBC + "'");
        }

        // Set ESM charge
        this.nmlSystem.setValue("esm_charge = " + this.esmCharge);

        // Set ESM effective screening medium parameters
        this.nmlSystem.setValue("esm_efield = 0.0");

        if (this.adjustPositions) {
            this.nmlSystem.setValue("esm_w = 0.0");
        }

        // Ensure surface is in correct orientation
        // ESM requires the surface normal to be along z-axis
        if (this.nmlControl != null) {
            this.nmlControl.setValue("calculation = 'scf'");
        }
    }

    /**
     * Calculate work function from electrostatic potential
     */
    public static double calculateWorkFunction(double vacuumLevel, double fermiEnergy) {
        return vacuumLevel - fermiEnergy;
    }

    /**
     * Extract vacuum level from planar-averaged potential
     */
    public static double extractVacuumLevel(double[] planarPotential, double dz) {
        if (planarPotential == null || planarPotential.length < 10) {
            return 0.0;
        }

        // Vacuum level is the average of the last 20% of the potential
        int nVacuum = planarPotential.length / 5;
        double sum = 0.0;
        for (int i = planarPotential.length - nVacuum; i < planarPotential.length; i++) {
            sum += planarPotential[i];
        }
        return sum / nVacuum;
    }
}
