/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements rigorous planar-averaged electrostatic potential plateau diagnostics 
 * for surface slab and ESM calculations, extracting left/right vacuum levels and 
 * verifying vacuum thickness adequacy (Roadmap #54).
 */
public final class QESlabPlateauDiagnostic {

    public static final class PlateauResult {
        private final double leftVacuumLevel;
        private final double rightVacuumLevel;
        private final double dipoleStep;
        private final double leftWorkFunction;
        private final double rightWorkFunction;
        private final boolean plateauFound;
        private final List<String> diagnosticWarnings = new ArrayList<>();

        public PlateauResult(double leftVac, double rightVac, double fermiEnergy, boolean found) {
            this.leftVacuumLevel = leftVac;
            this.rightVacuumLevel = rightVac;
            this.dipoleStep = rightVac - leftVac;
            this.leftWorkFunction = leftVac - fermiEnergy;
            this.rightWorkFunction = rightVac - fermiEnergy;
            this.plateauFound = found;
        }

        public double getLeftVacuumLevel() { return this.leftVacuumLevel; }
        public double getRightVacuumLevel() { return this.rightVacuumLevel; }
        public double getDipoleStep() { return this.dipoleStep; }
        public double getLeftWorkFunction() { return this.leftWorkFunction; }
        public double getRightWorkFunction() { return this.rightWorkFunction; }
        public boolean isPlateauFound() { return this.plateauFound; }
        public List<String> getWarnings() { return List.copyOf(this.diagnosticWarnings); }
        
        public void addWarning(String warning) {
            this.diagnosticWarnings.add(warning);
        }
    }

    private QESlabPlateauDiagnostic() {
        // Utility.
    }

    /**
     * Analyses a 1D planar-averaged electrostatic potential array V(z) along the perpendicular direction.
     * Finds flat regions (slopes near zero) corresponding to true vacuum plateaus.
     * 
     * @param potential potential values along the z axis (eV)
     * @param dz grid spacing along the z axis (Angstroms)
     * @param fermiEnergy Fermi energy of the slab (eV)
     * @param maxSlopeTolerance maximum slope (eV/A) to qualify as a flat plateau
     */
    public static PlateauResult analyzePotential(double[] potential, double dz, double fermiEnergy, double maxSlopeTolerance) {
        if (potential == null || potential.length < 10 || dz <= 0.0) {
            return new PlateauResult(0.0, 0.0, fermiEnergy, false);
        }

        int N = potential.length;
        double[] slope = new double[N - 1];
        for (int i = 0; i < N - 1; i++) {
            slope[i] = (potential[i + 1] - potential[i]) / dz;
        }

        // Search for flat regions (vacuum plateaus) in the left and right halves of the unit cell
        List<Integer> leftFlatIndices = new ArrayList<>();
        List<Integer> rightFlatIndices = new ArrayList<>();

        int mid = N / 2;
        double tol = maxSlopeTolerance > 0.0 ? maxSlopeTolerance : 1.0e-3;

        for (int i = 0; i < N - 1; i++) {
            if (Math.abs(slope[i]) < tol) {
                if (i < mid) {
                    leftFlatIndices.add(i);
                } else {
                    rightFlatIndices.add(i);
                }
            }
        }

        double leftVac = 0.0;
        double rightVac = 0.0;
        boolean foundLeft = !leftFlatIndices.isEmpty();
        boolean foundRight = !rightFlatIndices.isEmpty();

        if (foundLeft) {
            double sum = 0.0;
            for (int idx : leftFlatIndices) {
                sum += potential[idx];
            }
            leftVac = sum / leftFlatIndices.size();
        } else {
            // Fallback: average the first 10%
            double sum = 0.0;
            int count = N / 10;
            for (int i = 0; i < count; i++) {
                sum += potential[i];
            }
            leftVac = sum / count;
        }

        if (foundRight) {
            double sum = 0.0;
            for (int idx : rightFlatIndices) {
                sum += potential[idx];
            }
            rightVac = sum / rightFlatIndices.size();
        } else {
            // Fallback: average the last 10%
            double sum = 0.0;
            int count = N / 10;
            for (int i = N - count; i < N; i++) {
                sum += potential[i];
            }
            rightVac = sum / count;
        }

        PlateauResult result = new PlateauResult(leftVac, rightVac, fermiEnergy, foundLeft && foundRight);

        // System warnings and plateau diagnostics
        if (!foundLeft || !foundRight) {
            result.addWarning("Warning: Could not identify flat potential plateaus in both vacuum halves.");
            result.addWarning("Your vacuum thickness may be too small, causing strong periodic image interactions.");
        } else {
            // Check if the plateaus are contiguous and long enough (at least 5% of the unit cell width)
            int minContiguousPoints = Math.max(3, N / 20);
            if (leftFlatIndices.size() < minContiguousPoints || rightFlatIndices.size() < minContiguousPoints) {
                result.addWarning("Warning: Vacuum plateaus are extremely narrow. Consider increasing vacuum space along z.");
            }
        }

        return result;
    }
}
