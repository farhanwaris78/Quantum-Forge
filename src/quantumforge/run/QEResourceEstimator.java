/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run;

import java.util.Objects;
import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

/**
 * Predicts computational resource requirements (RAM, CPU core-hours, storage) 
 * for electronic-structure runs based on physical scaling laws (Roadmap #101).
 */
public final class QEResourceEstimator {

    public static final class Estimation {
        private final double estimatedMemoryGb;
        private final double estimatedCoreHours;
        private final double confidenceLowerCoreHours;
        private final double confidenceUpperCoreHours;

        public Estimation(double mem, double coreHours, double lower, double upper) {
            this.estimatedMemoryGb = Math.max(0.1, mem);
            this.estimatedCoreHours = Math.max(0.1, coreHours);
            this.confidenceLowerCoreHours = Math.max(0.05, lower);
            this.confidenceUpperCoreHours = Math.max(0.15, upper);
        }

        public double getEstimatedMemoryGb() { return this.estimatedMemoryGb; }
        public double getEstimatedCoreHours() { return this.estimatedCoreHours; }
        public double getConfidenceLowerCoreHours() { return this.confidenceLowerCoreHours; }
        public double getConfidenceUpperCoreHours() { return this.confidenceUpperCoreHours; }

        public String getReport() {
            return String.format(java.util.Locale.ROOT,
                "Resource Estimation Report:\n" +
                " - Predicted RAM requirement: %.2f GB\n" +
                " - Predicted compute cost: %.2f core-hours (95%% confidence: %.2f to %.2f core-hours)\n",
                estimatedMemoryGb, estimatedCoreHours, confidenceLowerCoreHours, confidenceUpperCoreHours);
        }
    }

    private QEResourceEstimator() {
        // Utility
    }

    /**
     * Estimates resource requirements based on O(N^3) scaling for DFT.
     */
    public static Estimation estimate(Cell cell, QEInput input) {
        if (cell == null || input == null) {
            return new Estimation(0.1, 0.1, 0.05, 0.15);
        }

        int natoms = cell.listAtoms(true).length;
        if (natoms <= 0) {
            natoms = 1;
        }

        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        double ecutwfc = 30.0; // Default
        if (system != null) {
            QEValue val = system.getValue("ecutwfc");
            if (val != null) ecutwfc = val.getRealValue();
        }

        int nkpoints = 1;
        quantumforge.input.card.QEKPoints kPoints = input.getCard(quantumforge.input.card.QEKPoints.class);
        if (kPoints != null) {
            if (kPoints.isAutomatic()) {
                int[] grid = kPoints.getKGrid();
                nkpoints = Math.max(1, grid[0] * grid[1] * grid[2] / 2);
            } else {
                nkpoints = Math.max(1, kPoints.numKPoints());
            }
        }

        // 1. Memory estimate: scales as O(Natoms * Npw)
        // Let's use a robust baseline scaling:
        double basisScale = Math.pow(ecutwfc, 1.5) / 1000.0;
        double memGb = 0.05 + (0.005 * natoms * basisScale * nkpoints);

        // 2. CPU Core-hours estimate: scales as O(Natoms^3 * N_kpoints) for standard SCF DFT
        double cpuBaselineMultiplier = 1.2e-4; // Core-hours baseline scale factor
        double complexityFactor = Math.pow(natoms, 3) * basisScale * nkpoints;
        double coreHours = complexityFactor * cpuBaselineMultiplier;

        // Confidence bounds (95% range: typically 0.5x to 2.0x due to convergence variations)
        double lower = coreHours * 0.5;
        double upper = coreHours * 2.0;

        return new Estimation(memGb, coreHours, lower, upper);
    }
}
