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

package burai.app.project.editor.result.bandgap;

/**
 * Band gap auto-detection from DOS and Band structure calculations.
 * 
 * NanoLabo v3.1+ automatically calculates and displays band gaps.
 * This class provides the same functionality, detecting:
 * - Direct vs indirect band gap
 * - Gap value in eV
 * - Valence band maximum (VBM)
 * - Conduction band minimum (CBM)
 * - Gap type classification (metal/semiconductor/insulator)
 */
public class BandGapDetector {

    private double bandGap;
    private double vbmEnergy;
    private double cbmEnergy;
    private double fermiEnergy;
    private boolean isDirect;
    private boolean isMetal;
    private int vbmKpointIndex;
    private int cbmKpointIndex;

    public BandGapDetector() {
        this.bandGap = -1.0;
        this.vbmEnergy = Double.NEGATIVE_INFINITY;
        this.cbmEnergy = Double.POSITIVE_INFINITY;
        this.fermiEnergy = 0.0;
        this.isDirect = false;
        this.isMetal = false;
        this.vbmKpointIndex = -1;
        this.cbmKpointIndex = -1;
    }

    /**
     * Detect band gap from band structure data.
     * 
     * @param energies 2D array: [nBands][nKpoints] of energies in eV
     * @param kpointWeights k-point weights or indices
     * @param nElectrons number of valence electrons
     * @param fermiEnergy Fermi level energy in eV
     */
    public void detectFromBands(double[][] energies, double[] kpointWeights,
                                 int nElectrons, double fermiEnergy) {
        if (energies == null || energies.length < 2) {
            this.isMetal = true;
            this.bandGap = 0.0;
            return;
        }

        this.fermiEnergy = fermiEnergy;
        int nBands = energies.length;
        int nKpoints = energies[0].length;

        // Find the highest occupied band (HOB) and lowest unoccupied band (LUB)
        int nOccBands = nElectrons / 2; // for non-spin-polarized

        this.vbmEnergy = Double.NEGATIVE_INFINITY;
        this.cbmEnergy = Double.POSITIVE_INFINITY;

        for (int ik = 0; ik < nKpoints; ik++) {
            // Valence band max (highest occupied)
            if (nOccBands > 0 && nOccBands <= nBands) {
                double eVal = energies[nOccBands - 1][ik];
                if (eVal > this.vbmEnergy) {
                    this.vbmEnergy = eVal;
                    this.vbmKpointIndex = ik;
                }
            }

            // Conduction band min (lowest unoccupied)
            if (nOccBands < nBands) {
                double eCon = energies[nOccBands][ik];
                if (eCon < this.cbmEnergy) {
                    this.cbmEnergy = eCon;
                    this.cbmKpointIndex = ik;
                }
            }
        }

        this.bandGap = this.cbmEnergy - this.vbmEnergy;
        this.isDirect = (this.vbmKpointIndex == this.cbmKpointIndex);
        this.isMetal = (this.bandGap <= 0.01);
    }

    /**
     * Detect band gap from DOS data.
     * 
     * @param energies energy grid in eV
     * @param dos density of states values
     * @param fermiEnergy Fermi level in eV
     * @param threshold DOS threshold for gap detection
     */
    public void detectFromDOS(double[] energies, double[] dos,
                               double fermiEnergy, double threshold) {
        if (energies == null || dos == null || energies.length != dos.length) {
            return;
        }

        this.fermiEnergy = fermiEnergy;

        // Find energy range around Fermi level
        int n = energies.length;

        // Find VBM (highest energy below Fermi with DOS below threshold)
        double vbm = Double.NEGATIVE_INFINITY;
        int vbmIdx = -1;
        for (int i = 0; i < n; i++) {
            if (energies[i] < fermiEnergy && dos[i] < threshold) {
                if (energies[i] > vbm) {
                    vbm = energies[i];
                    vbmIdx = i;
                }
            }
        }

        // Find CBM (lowest energy above Fermi with DOS below threshold)
        double cbm = Double.POSITIVE_INFINITY;
        int cbmIdx = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (energies[i] > fermiEnergy && dos[i] < threshold) {
                if (energies[i] < cbm) {
                    cbm = energies[i];
                    cbmIdx = i;
                }
            }
        }

        if (vbmIdx >= 0 && cbmIdx >= 0) {
            this.vbmEnergy = vbm;
            this.cbmEnergy = cbm;
            this.bandGap = cbm - vbm;
            this.isMetal = (this.bandGap <= 0.01);

            // Find k-point indices for VBM/CBM for direct/indirect detection
            this.isDirect = (vbmIdx == cbmIdx);
        }
    }

    // Getters
    public double getBandGap() { return this.bandGap; }
    public double getVBMLastEnergy() { return this.vbmEnergy; }
    public double getCBMFirstEnergy() { return this.cbmEnergy; }
    public double getFermiEnergy() { return this.fermiEnergy; }
    public boolean isDirect() { return this.isDirect; }
    public boolean isMetal() { return this.isMetal; }
    public int getVBMKpointIndex() { return this.vbmKpointIndex; }
    public int getCBMKpointIndex() { return this.cbmKpointIndex; }

    /**
     * Human-readable band gap description
     */
    public String getDescription() {
        if (this.isMetal) {
            return "Metallic (no band gap)";
        }

        String type = this.isDirect ? "Direct" : "Indirect";
        String gapStr = String.format("%.3f", this.bandGap);
        return type + " band gap: " + gapStr + " eV";
    }
}
