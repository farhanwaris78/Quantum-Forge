/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.app.project.editor.result.bandgap;

import java.util.ArrayList;
import java.util.List;

/**
 * PDOS Calculator for interactive orbital selection.
 * 
 * NanoLabo provides a PDOS Calculator that allows users to
 * interactively select which atomic orbitals contribute to
 * the density of states. This enables:
 * - Orbital-resolved DOS visualization
 * - Element-specific contributions
 * - l-orbital (s, p, d, f) decomposition
 * - Spin-up/down resolution
 * - Export as CSV/image
 */
public class PDOSCalculator {

    public static class PDOSData {
        public final double[] energies;
        public final double[] totalDOS;
        public final java.util.Map<String, double[]> orbitalDOS; // e.g. "Fe-d", "O-p"
        public final double[] integratedDOS;
        public final double fermiEnergy;

        public PDOSData(double[] energies, double[] total, double fermi) {
            this.energies = energies;
            this.totalDOS = total;
            this.orbitalDOS = new java.util.LinkedHashMap<>();
            this.integratedDOS = new double[energies.length];
            this.fermiEnergy = fermi;
            // Calculate integrated DOS
            double sum = 0;
            for (int i = 0; i < energies.length; i++) {
                sum += total[i];
                this.integratedDOS[i] = sum;
            }
        }

        public void addOrbital(String label, double[] dos) {
            this.orbitalDOS.put(label, dos);
        }

        /**
         * Get the band gap from PDOS
         */
        public double getBandGap() {
            BandGapDetector detector = new BandGapDetector();
            detector.detectFromDOS(this.energies, this.totalDOS, this.fermiEnergy, 0.01);
            return detector.getBandGap();
        }
    }

    private List<PDOSData> dataSeries;
    private String[] selectedOrbitals;

    public PDOSCalculator() {
        this.dataSeries = new ArrayList<>();
    }

    public void addData(PDOSData data) {
        if (data != null) this.dataSeries.add(data);
    }

    public PDOSData getData(int index) {
        if (index >= 0 && index < this.dataSeries.size()) {
            return this.dataSeries.get(index);
        }
        return null;
    }

    public int getNumData() { return this.dataSeries.size(); }

    public void setSelectedOrbitals(String[] orbitals) {
        this.selectedOrbitals = orbitals;
    }
}
