/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import quantumforge.operation.OperationResult;

/**
 * Explicit-term point-defect formation and adsorption energy arithmetic
 * (Roadmap #152/#153, formulation layer).
 *
 * <p>These helpers perform the two textbook sums with every term supplied
 * explicitly by the caller and every term validated finite. No correction is
 * ever guessed: charged-defect finite-size corrections (FNV, LK), potential
 * alignment, vibrational/ZPE contributions, and gas-phase reference quality all
 * belong to the caller's input, and their absence is reported by the analysis
 * layer, not compensated numerically here.</p>
 *
 * <pre>
 * E_form  = E_defect - E_host - sum_i(n_i * mu_i) + q * (E_VBM + dE_F) + E_corr
 * dG_ads  = E_total - E_slab - E_molecule + (dZPE - T*dS)_corr
 * </pre>
 * All energies are in eV.
 */
public final class QEThermochemistryMath {

    private QEThermochemistryMath() {
        // Utility
    }

    /**
     * Defect formation energy at the stated Fermi level. {@code defectCharge} q
     * multiplies {@code vbmEv + fermiShiftEv}; {@code chemPotSumEv} is the
     * caller-precomputed sum Sigma n_i*mu_i over added/removed atoms with a
     * consistent reference set.
     */
    public static OperationResult<Double> defectFormationEnergy(double defectEnergyEv,
            double hostEnergyEv, double chemPotSumEv, int defectCharge, double vbmEv,
            double fermiShiftEv, double correctionEv) {
        OperationResult<Void> finite = requireFinite(
                new double[] {defectEnergyEv, hostEnergyEv, chemPotSumEv, vbmEv,
                        fermiShiftEv, correctionEv},
                "defect/host/chemical-potential/VBM/Fermi-shift/correction energies");
        if (finite != null) {
            return OperationResult.failed(finite.getCode(), finite.getMessage(), null);
        }
        double value = defectEnergyEv - hostEnergyEv - chemPotSumEv
                + defectCharge * (vbmEv + fermiShiftEv) + correctionEv;
        return OperationResult.success("THERMO_OK", "Formation energy computed.", value);
    }

    /**
     * Adsorption energy (optionally free-energy-style when the caller supplies
     * a combined dZPE - T*dS correction term in {@code correctionEv}).
     */
    public static OperationResult<Double> adsorptionEnergy(double totalEnergyEv,
            double slabEnergyEv, double moleculeEnergyEv, double correctionEv) {
        OperationResult<Void> finite = requireFinite(
                new double[] {totalEnergyEv, slabEnergyEv, moleculeEnergyEv, correctionEv},
                "total/slab/molecule/correction energies");
        if (finite != null) {
            return OperationResult.failed(finite.getCode(), finite.getMessage(), null);
        }
        return OperationResult.success("THERMO_OK", "Adsorption energy computed.",
                totalEnergyEv - slabEnergyEv - moleculeEnergyEv + correctionEv);
    }

    private static OperationResult<Void> requireFinite(double[] values, String fields) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return OperationResult.failed("ENERGY_NONFINITE",
                        "A required energy term is missing or non-finite (" + fields
                                + "); every term must be supplied explicitly in eV.", null);
            }
        }
        return null;
    }
}
