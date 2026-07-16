/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.phonon;

import java.util.ArrayList;
import java.util.List;

import quantumforge.capability.CapabilityRegistry;
import quantumforge.capability.ScientificFeatureUnavailableException;
import quantumforge.operation.OperationResult;
import quantumforge.run.parser.PhononDosThermodynamics;

/**
 * Phonon mode analysis, thermodynamics integration, and complex animation.
 * 
 * Provides structural definitions for dispersion, density of states (DOS),
 * and complex, phase-modulated eigenvector animations for supercell replication.
 */
public class PhononAnalyzer {

    public static final int MODE_ACOUSTIC = 0;
    public static final int MODE_OPTICAL = 1;

    private PhononBand[] bands;
    private double[] phononDOS;
    private double[] dosEnergies;
    private PhononMode[] modes;
    private double[][][] bornCharges;
    private double[][] dielectricTensor;

    public PhononAnalyzer() {
        this.bands = null;
        this.phononDOS = null;
        this.modes = null;
        this.bornCharges = null;
        this.dielectricTensor = new double[3][3];
    }

    public void setBands(PhononBand[] bands) { this.bands = bands; }
    public PhononBand[] getBands() { return this.bands; }

    public void setDOS(double[] energies, double[] dos) {
        this.dosEnergies = energies;
        this.phononDOS = dos;
    }
    public double[] getPhononDOS() { return this.phononDOS; }
    public double[] getDOSEnergies() { return this.dosEnergies; }

    public void setModes(PhononMode[] modes) { this.modes = modes; }
    public PhononMode[] getModes() { return this.modes; }

    /**
     * Phonon band data for dispersion plot
     */
    public static class PhononBand {
        private double[] frequencies; // in cm^-1
        private double[] qpoints;
        private String branchLabel;

        public PhononBand(double[] frequencies, double[] qpoints, String label) {
            this.frequencies = frequencies;
            this.qpoints = qpoints;
            this.branchLabel = label;
        }

        public double[] getFrequencies() { return this.frequencies; }
        public double[] getQPoints() { return this.qpoints; }
        public String getBranchLabel() { return this.branchLabel; }
    }

    /**
     * Single phonon mode at a given q-point with displacement vectors
     */
    public static class PhononMode {
        private int modeIndex;
        private double frequency; // cm^-1
        private int modeType;
        private double[][] displacementVectors; // [atomIndex][x,y,z] (Real component)
        private double[][] displacementVectorsImag; // [atomIndex][x,y,z] (Imaginary component)
        private double[] qVector = new double[3]; // [qx, qy, qz] wavevector
        private String[] atomLabels;
        private double[] atomPositions; // [3*atomIndex + 0/1/2] = x/y/z
        private double irIntensity;
        private double ramanActivity;
        private boolean infraredActive;
        private boolean ramanActive;

        public PhononMode(int index, double freq, int type) {
            this.modeIndex = index;
            this.frequency = freq;
            this.modeType = type;
            this.irIntensity = 0.0;
            this.ramanActivity = 0.0;
            this.infraredActive = false;
            this.ramanActive = false;
        }

        public int getModeIndex() { return this.modeIndex; }
        public double getFrequency() { return this.frequency; }
        public int getModeType() { return this.modeType; }

        public void setDisplacementVectors(double[][] vectors) {
            this.displacementVectors = vectors;
        }
        public double[][] getDisplacementVectors() { return this.displacementVectors; }

        public void setDisplacementVectorsImag(double[][] imag) {
            this.displacementVectorsImag = imag;
        }
        public double[][] getDisplacementVectorsImag() { return this.displacementVectorsImag; }

        public void setQVector(double[] q) {
            if (q != null && q.length == 3) {
                this.qVector = q.clone();
            }
        }
        public double[] getQVector() { return this.qVector.clone(); }

        public void setAtomPositions(double[] positions, String[] labels) {
            this.atomPositions = positions;
            this.atomLabels = labels;
        }
        public double[] getAtomPositions() { return this.atomPositions; }
        public String[] getAtomLabels() { return this.atomLabels; }

        /**
         * Get maximum displacement magnitude for scaling arrows
         */
        public double getMaxDisplacement() {
            if (this.displacementVectors == null) return 0.0;
            double max = 0.0;
            for (double[] vec : this.displacementVectors) {
                double mag = Math.sqrt(vec[0]*vec[0] + vec[1]*vec[1] + vec[2]*vec[2]);
                if (mag > max) max = mag;
            }
            return max;
        }

        /**
         * Simple temporal oscillation position calculator
         */
        public double[] getAnimatedPosition(int atomIdx, double time, double amplitude) {
            return getAnimatedPositionComplex(atomIdx, time, amplitude, new double[]{0, 0, 0});
        }

        /**
         * Mathematically rigorous temporal and spatial phase animation:
         * u_I(t, R) = Re [ e_I * e^(i(q.R - w*t)) ]
         *           = e_Real * cos(q.R - w*t) - e_Imag * sin(q.R - w*t)
         * where w*t is simulated by '2 * pi * time' and R is cellTranslation (Roadmap #52).
         */
        public double[] getAnimatedPositionComplex(int atomIdx, double time, double amplitude, double[] cellTranslation) {
            if (this.displacementVectors == null || atomIdx >= this.displacementVectors.length) {
                return new double[]{0, 0, 0};
            }
            double[] basePos = new double[]{
                this.atomPositions[3*atomIdx],
                this.atomPositions[3*atomIdx+1],
                this.atomPositions[3*atomIdx+2]
            };

            double qDotR = 0.0;
            if (this.qVector != null && cellTranslation != null && cellTranslation.length == 3) {
                qDotR = this.qVector[0] * cellTranslation[0] + this.qVector[1] * cellTranslation[1] + this.qVector[2] * cellTranslation[2];
            }

            double wt = 2.0 * Math.PI * time;
            double phase = qDotR - wt;

            double cosPhase = Math.cos(phase);
            double sinPhase = Math.sin(phase);

            double[] realVec = this.displacementVectors[atomIdx];
            double[] imagVec = (this.displacementVectorsImag != null && atomIdx < this.displacementVectorsImag.length) 
                ? this.displacementVectorsImag[atomIdx] : new double[]{0, 0, 0};

            // Re [ (e_Real + i * e_Imag) * (cos(phase) + i * sin(phase)) ]
            // = e_Real * cos(phase) - e_Imag * sin(phase)
            double ux = amplitude * (realVec[0] * cosPhase - imagVec[0] * sinPhase);
            double uy = amplitude * (realVec[1] * cosPhase - imagVec[1] * sinPhase);
            double uz = amplitude * (realVec[2] * cosPhase - imagVec[2] * sinPhase);

            double[] trans = cellTranslation != null && cellTranslation.length == 3 ? cellTranslation : new double[]{0,0,0};

            return new double[]{
                basePos[0] + trans[0] + ux,
                basePos[1] + trans[1] + uy,
                basePos[2] + trans[2] + uz
            };
        }

        public double getIrIntensity() { return this.irIntensity; }
        public void setIrIntensity(double intensity) {
            this.irIntensity = intensity;
            this.infraredActive = intensity > 0.01;
        }
        public boolean isInfraredActive() { return this.infraredActive; }

        public double getRamanActivity() { return this.ramanActivity; }
        public void setRamanActivity(double activity) {
            this.ramanActivity = activity;
            this.ramanActive = activity > 0.01;
        }
        public boolean isRamanActive() { return this.ramanActive; }
    }

    /**
     * Find the mode with the highest IR intensity
     */
    public PhononMode getStrongestIRMode() {
        if (this.modes == null) return null;
        PhononMode strongest = null;
        double maxIntensity = 0.0;
        for (PhononMode mode : this.modes) {
            if (mode.getIrIntensity() > maxIntensity) {
                maxIntensity = mode.getIrIntensity();
                strongest = mode;
            }
        }
        return strongest;
    }

    /**
     * Get thermodynamic properties from phonon DOS (harmonic approximation).
     */
    public ThermodynamicProperties calculateThermodynamics(double temperature) {
        if (this.dosEnergies == null || this.phononDOS == null) {
            throw new ScientificFeatureUnavailableException(CapabilityRegistry.PHONOPY,
                    "Phonon DOS is not set; load a real phonon DOS before thermodynamics.");
        }
        OperationResult<PhononDosThermodynamics.Result> result =
                PhononDosThermodynamics.integrate(this.dosEnergies, this.phononDOS, temperature);
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            throw new IllegalArgumentException("Phonon thermodynamics failed: " + result.getMessage());
        }
        PhononDosThermodynamics.Result r = result.getValue().get();
        return new ThermodynamicProperties(
                r.getTemperatureK(),
                r.getHelmholtzFreeEnergyEv(),
                r.getEntropyEvPerK() * 1000.0,      // eV/K -> meV/K
                r.getHeatCapacityEvPerK() * 1000.0, // eV/K -> meV/K
                r.getZeroPointEnergyEv(),
                r.getInternalEnergyEv(),
                r.getIntegratedDos(),
                r.getNotes());
    }

    /**
     * Thermodynamic properties from phonon calculations
     */
    public static class ThermodynamicProperties {
        public final double temperature;
        public final double freeEnergy;      // eV
        public final double entropy;          // meV/K
        public final double heatCapacityCV;   // meV/K
        public final double zeroPointEnergyEv;
        public final double internalEnergyEv;
        public final double integratedDos;
        public final String notes;

        public ThermodynamicProperties(double temperature, double freeEnergyEv,
                                       double entropyMevPerK, double heatCapacityMevPerK,
                                       double zeroPointEnergyEv, double internalEnergyEv,
                                       double integratedDos, String notes) {
            this.temperature = temperature;
            this.freeEnergy = freeEnergyEv;
            this.entropy = entropyMevPerK;
            this.heatCapacityCV = heatCapacityMevPerK;
            this.zeroPointEnergyEv = zeroPointEnergyEv;
            this.internalEnergyEv = internalEnergyEv;
            this.integratedDos = integratedDos;
            this.notes = notes == null ? "" : notes;
        }

        /** @deprecated incomplete constructor retained only for compile compatibility */
        @Deprecated
        public ThermodynamicProperties(double T, double[] energies, double[] dos) {
            OperationResult<PhononDosThermodynamics.Result> result =
                    PhononDosThermodynamics.integrate(energies, dos, T);
            if (!result.isSuccess() || result.getValue().isEmpty()) {
                throw new IllegalArgumentException(result.getMessage());
            }
            PhononDosThermodynamics.Result r = result.getValue().get();
            this.temperature = r.getTemperatureK();
            this.freeEnergy = r.getHelmholtzFreeEnergyEv();
            this.entropy = r.getEntropyEvPerK() * 1000.0;
            this.heatCapacityCV = r.getHeatCapacityEvPerK() * 1000.0;
            this.zeroPointEnergyEv = r.getZeroPointEnergyEv();
            this.internalEnergyEv = r.getInternalEnergyEv();
            this.integratedDos = r.getIntegratedDos();
            this.notes = r.getNotes();
        }
    }

    // Getters for dielectric properties
    public double[][][] getBornCharges() { return this.bornCharges; }
    public void setBornCharges(double[][][] charges) { this.bornCharges = charges; }
    public double[][] getDielectricTensor() { return this.dielectricTensor; }
    public void setDielectricTensor(double[][] tensor) { this.dielectricTensor = tensor; }
}
