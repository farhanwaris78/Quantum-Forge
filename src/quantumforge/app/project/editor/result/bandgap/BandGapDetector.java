/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.editor.result.bandgap;

import java.util.Locale;

/**
 * Unit-aware (eV) band-gap analysis with explicit limits.
 *
 * <p>A direct/indirect distinction is possible from k-resolved bands, but not
 * from a total DOS. Integer electron counting is valid only when the supplied
 * bands use a known spin degeneracy and have no partial occupations. For metals
 * or smearing-sensitive systems use {@link #detectFromBandsAndOccupations}.</p>
 */
public final class BandGapDetector {
    public enum Classification { UNKNOWN, METAL, GAPPED }

    private static final double DEFAULT_ENERGY_TOLERANCE_EV = 0.01;

    private double bandGap;
    private double vbmEnergy;
    private double cbmEnergy;
    private double fermiEnergy;
    private boolean direct;
    private boolean directKnown;
    private int vbmKpointIndex;
    private int cbmKpointIndex;
    private Classification classification;
    private String diagnostic;

    public BandGapDetector() {
        this.reset();
    }

    private void reset() {
        this.bandGap = Double.NaN;
        this.vbmEnergy = Double.NaN;
        this.cbmEnergy = Double.NaN;
        this.fermiEnergy = Double.NaN;
        this.direct = false;
        this.directKnown = false;
        this.vbmKpointIndex = -1;
        this.cbmKpointIndex = -1;
        this.classification = Classification.UNKNOWN;
        this.diagnostic = "No analysis has been performed.";
    }

    /**
     * Analyze non-spin-polarized integer-filled bands (spin degeneracy two).
     */
    public void detectFromBands(double[][] energies, double[] kpointWeights,
                                int nElectrons, double fermiEnergy) {
        this.detectFromBands(energies, kpointWeights, nElectrons, fermiEnergy,
                2, DEFAULT_ENERGY_TOLERANCE_EV);
    }

    /**
     * Analyze integer-filled bands with an explicit state degeneracy.
     *
     * @param energies energies[band][k-point] in eV
     * @param kpointWeights optional weights; when supplied its length is validated
     * @param nElectrons total electrons represented by these bands
     * @param fermiEnergy Fermi energy in eV
     * @param stateDegeneracy electrons per fully occupied state (normally 2 or 1)
     * @param energyToleranceEv gaps at or below this tolerance are classified metallic
     */
    public void detectFromBands(double[][] energies, double[] kpointWeights,
                                int nElectrons, double fermiEnergy,
                                int stateDegeneracy, double energyToleranceEv) {
        this.reset();
        int nKpoints = validateBandGrid(energies);
        validateFinite(fermiEnergy, "fermiEnergy");
        validatePositiveTolerance(energyToleranceEv);
        if (stateDegeneracy < 1) {
            throw new IllegalArgumentException("stateDegeneracy must be positive");
        }
        if (nElectrons <= 0 || nElectrons % stateDegeneracy != 0) {
            this.diagnostic = "Electron count is incompatible with integer occupations and the supplied degeneracy.";
            return;
        }
        if (kpointWeights != null && kpointWeights.length != nKpoints) {
            throw new IllegalArgumentException("kpointWeights length does not match the band grid");
        }

        int occupiedBands = nElectrons / stateDegeneracy;
        if (occupiedBands < 1 || occupiedBands >= energies.length) {
            this.diagnostic = "The band grid does not contain both occupied and unoccupied states.";
            return;
        }

        this.fermiEnergy = fermiEnergy;
        for (int k = 0; k < nKpoints; k++) {
            double valence = energies[occupiedBands - 1][k];
            double conduction = energies[occupiedBands][k];
            if (Double.isNaN(this.vbmEnergy) || valence > this.vbmEnergy) {
                this.vbmEnergy = valence;
                this.vbmKpointIndex = k;
            }
            if (Double.isNaN(this.cbmEnergy) || conduction < this.cbmEnergy) {
                this.cbmEnergy = conduction;
                this.cbmKpointIndex = k;
            }
        }
        finishBandAnalysis(energyToleranceEv,
                "Integer occupations were inferred from electron count and degeneracy; partial occupations were not supplied.");
    }

    /**
     * Analyze k-resolved energies and occupations, including partially occupied metals.
     * Occupations must use the same [band][k-point] shape as energies.
     */
    public void detectFromBandsAndOccupations(double[][] energies, double[][] occupations,
                                              double fermiEnergy, double fullOccupation,
                                              double occupationTolerance,
                                              double energyToleranceEv) {
        this.reset();
        int nKpoints = validateBandGrid(energies);
        validateMatchingGrid(occupations, energies.length, nKpoints, "occupations");
        validateFinite(fermiEnergy, "fermiEnergy");
        validateFinite(fullOccupation, "fullOccupation");
        if (fullOccupation <= 0.0) {
            throw new IllegalArgumentException("fullOccupation must be positive");
        }
        if (!Double.isFinite(occupationTolerance) || occupationTolerance <= 0.0
                || occupationTolerance >= 0.5 * fullOccupation) {
            throw new IllegalArgumentException("occupationTolerance is outside the valid range");
        }
        validatePositiveTolerance(energyToleranceEv);
        this.fermiEnergy = fermiEnergy;

        boolean partiallyOccupied = false;
        for (int band = 0; band < energies.length; band++) {
            for (int k = 0; k < nKpoints; k++) {
                double occupation = occupations[band][k];
                if (!Double.isFinite(occupation) || occupation < -occupationTolerance
                        || occupation > fullOccupation + occupationTolerance) {
                    throw new IllegalArgumentException("Invalid occupation at band " + band + ", k-point " + k);
                }
                if (occupation > occupationTolerance
                        && occupation < fullOccupation - occupationTolerance) {
                    partiallyOccupied = true;
                }
                if (occupation > occupationTolerance) {
                    double energy = energies[band][k];
                    if (Double.isNaN(this.vbmEnergy) || energy > this.vbmEnergy) {
                        this.vbmEnergy = energy;
                        this.vbmKpointIndex = k;
                    }
                }
                if (occupation < fullOccupation - occupationTolerance) {
                    double energy = energies[band][k];
                    if (Double.isNaN(this.cbmEnergy) || energy < this.cbmEnergy) {
                        this.cbmEnergy = energy;
                        this.cbmKpointIndex = k;
                    }
                }
            }
        }

        if (partiallyOccupied) {
            markMetal("At least one state is partially occupied.");
            return;
        }
        if (Double.isNaN(this.vbmEnergy) || Double.isNaN(this.cbmEnergy)) {
            this.diagnostic = "Could not identify both occupied and unoccupied states.";
            return;
        }
        finishBandAnalysis(energyToleranceEv, "Occupations were supplied explicitly.");
    }

    /**
     * Estimate a DOS gap around the Fermi energy. Directness cannot be obtained
     * from a total DOS and remains explicitly unknown.
     */
    public void detectFromDOS(double[] energies, double[] dos,
                              double fermiEnergy, double threshold) {
        this.reset();
        if (energies == null || dos == null || energies.length != dos.length || energies.length < 3) {
            throw new IllegalArgumentException("energies and DOS must have equal length >= 3");
        }
        validateFinite(fermiEnergy, "fermiEnergy");
        if (!Double.isFinite(threshold) || threshold < 0.0) {
            throw new IllegalArgumentException("DOS threshold must be finite and non-negative");
        }
        for (int i = 0; i < energies.length; i++) {
            validateFinite(energies[i], "energies[" + i + "]");
            validateFinite(dos[i], "dos[" + i + "]");
            if (dos[i] < 0.0) {
                throw new IllegalArgumentException("DOS cannot be negative at index " + i);
            }
            if (i > 0 && energies[i] <= energies[i - 1]) {
                throw new IllegalArgumentException("Energy grid must be strictly increasing");
            }
        }
        this.fermiEnergy = fermiEnergy;

        int nearest = 0;
        for (int i = 1; i < energies.length; i++) {
            if (Math.abs(energies[i] - fermiEnergy) < Math.abs(energies[nearest] - fermiEnergy)) {
                nearest = i;
            }
        }
        if (dos[nearest] > threshold) {
            markMetal("DOS at the sampled Fermi energy exceeds the selected threshold.");
            return;
        }

        int valenceIndex = -1;
        int conductionIndex = -1;
        for (int i = 0; i < energies.length && energies[i] <= fermiEnergy; i++) {
            if (dos[i] > threshold) {
                valenceIndex = i;
            }
        }
        for (int i = 0; i < energies.length; i++) {
            if (energies[i] >= fermiEnergy && dos[i] > threshold) {
                conductionIndex = i;
                break;
            }
        }
        if (valenceIndex < 0 || conductionIndex < 0 || conductionIndex <= valenceIndex) {
            this.diagnostic = "The sampled DOS does not bracket a gap around the Fermi energy.";
            return;
        }

        this.vbmEnergy = interpolateThreshold(energies, dos, valenceIndex,
                Math.min(valenceIndex + 1, energies.length - 1), threshold);
        this.cbmEnergy = interpolateThreshold(energies, dos,
                Math.max(0, conductionIndex - 1), conductionIndex, threshold);
        this.bandGap = Math.max(0.0, this.cbmEnergy - this.vbmEnergy);
        this.classification = this.bandGap <= DEFAULT_ENERGY_TOLERANCE_EV
                ? Classification.METAL : Classification.GAPPED;
        if (this.classification == Classification.METAL) {
            this.bandGap = 0.0;
        }
        this.directKnown = false;
        this.diagnostic = "DOS-threshold gap estimate; direct/indirect character requires k-resolved bands."
                + " Result depends on energy resolution, broadening, and threshold.";
    }

    private static int validateBandGrid(double[][] values) {
        if (values == null || values.length < 2 || values[0] == null || values[0].length < 1) {
            throw new IllegalArgumentException("Band grid must contain at least two bands and one k-point");
        }
        int nKpoints = values[0].length;
        validateMatchingGrid(values, values.length, nKpoints, "energies");
        return nKpoints;
    }

    private static void validateMatchingGrid(double[][] values, int nBands, int nKpoints, String name) {
        if (values == null || values.length != nBands) {
            throw new IllegalArgumentException(name + " has the wrong number of bands");
        }
        for (int band = 0; band < nBands; band++) {
            if (values[band] == null || values[band].length != nKpoints) {
                throw new IllegalArgumentException(name + " is not rectangular at band " + band);
            }
            for (int k = 0; k < nKpoints; k++) {
                validateFinite(values[band][k], name + "[" + band + "][" + k + "]");
            }
        }
    }

    private static void validateFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void validatePositiveTolerance(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("energy tolerance must be finite and non-negative");
        }
    }

    private static double interpolateThreshold(double[] energy, double[] dos,
                                               int first, int second, double threshold) {
        if (first == second || dos[first] == dos[second]) {
            return energy[first];
        }
        double fraction = (threshold - dos[first]) / (dos[second] - dos[first]);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return energy[first] + fraction * (energy[second] - energy[first]);
    }

    private void finishBandAnalysis(double tolerance, String method) {
        this.bandGap = this.cbmEnergy - this.vbmEnergy;
        if (this.bandGap <= tolerance) {
            markMetal("Global CBM-VBM separation is at or below the selected tolerance. " + method);
            return;
        }
        this.classification = Classification.GAPPED;
        this.directKnown = true;
        this.direct = this.vbmKpointIndex == this.cbmKpointIndex;
        this.diagnostic = method;
    }

    private void markMetal(String reason) {
        this.classification = Classification.METAL;
        this.bandGap = 0.0;
        this.direct = false;
        this.directKnown = false;
        this.diagnostic = reason;
    }

    public double getBandGap() { return this.bandGap; }
    public double getVBMLastEnergy() { return this.vbmEnergy; }
    public double getCBMFirstEnergy() { return this.cbmEnergy; }
    public double getFermiEnergy() { return this.fermiEnergy; }
    public boolean isDirect() { return this.directKnown && this.direct; }
    public boolean isDirectKnown() { return this.directKnown; }
    public boolean isMetal() { return this.classification == Classification.METAL; }
    public int getVBMKpointIndex() { return this.vbmKpointIndex; }
    public int getCBMKpointIndex() { return this.cbmKpointIndex; }
    public Classification getClassification() { return this.classification; }
    public String getDiagnostic() { return this.diagnostic; }

    public String getDescription() {
        if (this.classification == Classification.UNKNOWN) {
            return "Band gap undetermined: " + this.diagnostic;
        }
        if (this.classification == Classification.METAL) {
            return "Metallic within analysis tolerance: " + this.diagnostic;
        }
        String type = this.directKnown ? (this.direct ? "Direct" : "Indirect") : "DOS-estimated";
        return String.format(Locale.ROOT, "%s band gap: %.3f eV", type, this.bandGap);
    }
}
