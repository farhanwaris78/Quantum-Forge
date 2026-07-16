/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.editor.result.bandgap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data model for total/projected DOS selection and numerically correct integration. */
public final class PDOSCalculator {

    public static final class PDOSData {
        public final double[] energies;
        public final double[] totalDOS;
        public final Map<String, double[]> orbitalDOS;
        public final double[] integratedDOS;
        public final double fermiEnergy;

        public PDOSData(double[] energies, double[] total, double fermi) {
            validateGrid(energies, total);
            if (!Double.isFinite(fermi)) {
                throw new IllegalArgumentException("Fermi energy must be finite");
            }
            this.energies = energies.clone();
            this.totalDOS = total.clone();
            this.orbitalDOS = new LinkedHashMap<>();
            this.integratedDOS = integrateTrapezoidal(this.energies, this.totalDOS);
            this.fermiEnergy = fermi;
        }

        public void addOrbital(String label, double[] dos) {
            if (label == null || label.trim().isEmpty()) {
                throw new IllegalArgumentException("Orbital label is empty");
            }
            if (dos == null || dos.length != this.energies.length) {
                throw new IllegalArgumentException("Orbital DOS does not match the energy grid");
            }
            double[] copy = dos.clone();
            for (int i = 0; i < copy.length; i++) {
                if (!Double.isFinite(copy[i])) {
                    throw new IllegalArgumentException("Non-finite orbital DOS at index " + i);
                }
            }
            this.orbitalDOS.put(label.trim(), copy);
        }

        public double getBandGap() {
            BandGapDetector detector = new BandGapDetector();
            detector.detectFromDOS(this.energies, this.totalDOS, this.fermiEnergy, 0.01);
            return detector.getBandGap();
        }

        private static void validateGrid(double[] energy, double[] values) {
            if (energy == null || values == null || energy.length != values.length || energy.length < 2) {
                throw new IllegalArgumentException("Energy and DOS arrays must have equal length >= 2");
            }
            for (int i = 0; i < energy.length; i++) {
                if (!Double.isFinite(energy[i]) || !Double.isFinite(values[i])) {
                    throw new IllegalArgumentException("Non-finite DOS data at index " + i);
                }
                if (i > 0 && energy[i] <= energy[i - 1]) {
                    throw new IllegalArgumentException("Energy grid must be strictly increasing");
                }
            }
        }

        private static double[] integrateTrapezoidal(double[] energy, double[] values) {
            double[] integral = new double[energy.length];
            integral[0] = 0.0;
            for (int i = 1; i < energy.length; i++) {
                double width = energy[i] - energy[i - 1];
                integral[i] = integral[i - 1] + 0.5 * width * (values[i - 1] + values[i]);
            }
            return integral;
        }
    }

    private final List<PDOSData> dataSeries = new ArrayList<>();
    private String[] selectedOrbitals = new String[0];

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
        this.selectedOrbitals = orbitals == null ? new String[0] : orbitals.clone();
    }

    public String[] getSelectedOrbitals() {
        return this.selectedOrbitals.clone();
    }

    @Override
    public String toString() {
        return "PDOSCalculator{series=" + this.dataSeries.size()
                + ", selected=" + Arrays.toString(this.selectedOrbitals) + "}";
    }
}
