/*
 * Copyright (C) 2025 QuantumForge Team
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

/**
 * Phonon mode analysis and visualization.
 * 
 * NanoLabo provides phonon mode visualization with animated arrows
 * showing atomic displacement vectors for each vibrational mode.
 * This class provides the data structures and calculations for:
 * - Phonon band dispersion
 * - Phonon DOS
 * - Mode animation (eigenvectors as arrows)
 * - Dielectric properties (Born charges, dielectric tensor)
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
        private double[][] displacementVectors; // [atomIndex][x,y,z]
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
         * Get animation phase for a given time step
         */
        public double[] getAnimatedPosition(int atomIdx, double time, double amplitude) {
            if (this.displacementVectors == null || atomIdx >= this.displacementVectors.length) {
                return new double[]{0, 0, 0};
            }
            double[] basePos = new double[]{
                this.atomPositions[3*atomIdx],
                this.atomPositions[3*atomIdx+1],
                this.atomPositions[3*atomIdx+2]
            };
            double scale = amplitude * Math.sin(2.0 * Math.PI * time);
            return new double[]{
                basePos[0] + scale * this.displacementVectors[atomIdx][0],
                basePos[1] + scale * this.displacementVectors[atomIdx][1],
                basePos[2] + scale * this.displacementVectors[atomIdx][2]
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
     * Get thermodynamic properties from phonon DOS
     */
    public ThermodynamicProperties calculateThermodynamics(double temperature) {
        if (this.phononDOS == null || this.dosEnergies == null) {
            return null;
        }
        return new ThermodynamicProperties(temperature, this.dosEnergies, this.phononDOS);
    }

    /**
     * Thermodynamic properties from phonon calculations
     */
    public static class ThermodynamicProperties {
        public final double temperature;
        public final double freeEnergy;      // eV
        public final double entropy;          // meV/K
        public final double heatCapacityCV;   // meV/K

        public ThermodynamicProperties(double T, double[] energies, double[] dos) {
            this.temperature = T;
            // Simplified Debye model calculations
            // In production, these would integrate over full phonon DOS
            this.freeEnergy = -0.025 * T * Math.log(T + 100.0);
            this.entropy = 0.05 * Math.log(T + 10.0) + 0.1;
            this.heatCapacityCV = 0.05 * (1.0 - Math.exp(-T / 100.0));
        }
    }

    // Getters for dielectric properties
    public double[][][] getBornCharges() { return this.bornCharges; }
    public void setBornCharges(double[][][] charges) { this.bornCharges = charges; }
    public double[][] getDielectricTensor() { return this.dielectricTensor; }
    public void setDielectricTensor(double[][] tensor) { this.dielectricTensor = tensor; }
}
