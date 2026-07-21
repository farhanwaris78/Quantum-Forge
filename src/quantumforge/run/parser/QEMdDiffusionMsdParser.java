/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import quantumforge.com.math.Matrix3D;
import quantumforge.project.property.ProjectProperty;

/**
 * Calculates Mean Squared Displacement (MSD) from Molecular Dynamics (MD) trajectories,
 * unwrapping periodic boundary coordinates and fitting diffusion coefficients (D)
 * in standard SI units (cm^2/s) (Roadmap #156).
 */
public final class QEMdDiffusionMsdParser extends LogParser {

    private final double[][] lattice; // 3x3 box in Angstroms
    private final List<double[][]> wrappedTrajectory = new ArrayList<>(); // List of [numAtoms][3]
    private final List<double[][]> unwrappedTrajectory = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();

    public QEMdDiffusionMsdParser(ProjectProperty property, double[][] lattice) {
        super(property);
        this.lattice = Matrix3D.copy(Objects.requireNonNull(lattice, "lattice"));
    }

    public void addFrame(double[][] coords) {
        if (coords == null || coords.length == 0) return;
        double[][] copy = new double[coords.length][3];
        for (int i = 0; i < coords.length; i++) {
            System.arraycopy(coords[i], 0, copy[i], 0, 3);
        }
        this.wrappedTrajectory.add(copy);
    }

    public List<double[][]> getWrappedTrajectory() { return List.copyOf(this.wrappedTrajectory); }
    public List<double[][]> getUnwrappedTrajectory() { return List.copyOf(this.unwrappedTrajectory); }
    public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }

    @Override
    public void parse(File file) throws IOException {
        // MD files parsing is managed externally or via frames feeding
    }

    /**
     * Unwraps periodic boundary crossings:
     * r_unwrapped(t) = r_wrapped(t) + N(t) * L
     * If the distance change between adjacent steps exceeds half the box size,
     * it registers a boundary crossing.
     */
    public void unwrapTrajectory() {
        if (this.wrappedTrajectory.isEmpty()) {
            return;
        }

        this.unwrappedTrajectory.clear();
        int numAtoms = this.wrappedTrajectory.get(0).length;
        int numFrames = this.wrappedTrajectory.size();

        // Accumulate integer boundary crossings for each atom: [numAtoms][3]
        double[][] offsets = new double[numAtoms][3];

        // Frame 0 is the reference
        double[][] firstFrame = this.wrappedTrajectory.get(0);
        double[][] unwrapped0 = new double[numAtoms][3];
        for (int a = 0; a < numAtoms; a++) {
            System.arraycopy(firstFrame[a], 0, unwrapped0[a], 0, 3);
        }
        this.unwrappedTrajectory.add(unwrapped0);

        double boxX = this.lattice[0][0];
        double boxY = this.lattice[1][1];
        double boxZ = this.lattice[2][2];

        for (int f = 1; f < numFrames; f++) {
            double[][] prev = this.wrappedTrajectory.get(f - 1);
            double[][] cur = this.wrappedTrajectory.get(f);
            double[][] unwrapped = new double[numAtoms][3];

            for (int a = 0; a < numAtoms; a++) {
                double dx = cur[a][0] - prev[a][0];
                double dy = cur[a][1] - prev[a][1];
                double dz = cur[a][2] - prev[a][2];

                // Check and correct periodic boundary crossings
                if (Math.abs(dx) > boxX / 2.0) {
                    offsets[a][0] -= Math.signum(dx) * boxX;
                }
                if (Math.abs(dy) > boxY / 2.0) {
                    offsets[a][1] -= Math.signum(dy) * boxY;
                }
                if (Math.abs(dz) > boxZ / 2.0) {
                    offsets[a][2] -= Math.signum(dz) * boxZ;
                }

                unwrapped[a][0] = cur[a][0] + offsets[a][0];
                unwrapped[a][1] = cur[a][1] + offsets[a][1];
                unwrapped[a][2] = cur[a][2] + offsets[a][2];
            }
            this.unwrappedTrajectory.add(unwrapped);
        }
    }

    /**
     * Computes the Mean Squared Displacement (MSD) for each step of the trajectory:
     * MSD(t) = (1 / Natoms) * Sum_i |r_unwrapped(t) - r_unwrapped(0)|^2
     */
    public double[] computeMsd() {
        if (this.unwrappedTrajectory.isEmpty()) {
            return new double[0];
        }

        int numFrames = this.unwrappedTrajectory.size();
        int numAtoms = this.unwrappedTrajectory.get(0).length;
        double[] msd = new double[numFrames];

        double[][] r0 = this.unwrappedTrajectory.get(0);

        for (int f = 0; f < numFrames; f++) {
            double[][] rf = this.unwrappedTrajectory.get(f);
            double sumSq = 0.0;

            for (int a = 0; a < numAtoms; a++) {
                double dx = rf[a][0] - r0[a][0];
                double dy = rf[a][1] - r0[a][1];
                double dz = rf[a][2] - r0[a][2];
                sumSq += (dx*dx + dy*dy + dz*dz);
            }

            msd[f] = sumSq / numAtoms;
        }

        return msd;
    }

    /**
     * Fits the MSD slope in the linear region (long-time limit) to compute
     * the self-diffusion coefficient D (in standard units of cm^2/s):
     * D = (Slope / 6) * 1.0e-4 cm^2/s   (since 1 A^2/ps = 1.0e-4 cm^2/s)
     * 
     * @param timeStepPs MD time interval between stored frames (in picoseconds)
     */
    public double calculateDiffusionCoefficientSI(double timeStepPs) {
        double[] msd = computeMsd();
        if (msd.length < 5 || timeStepPs <= 0.0) {
            this.diagnostics.add("Diffusion analysis skipped: Trajectory is too short.");
            return 0.0;
        }

        int N = msd.length;
        // Fit the last 50% of the trajectory (linear diffusive region)
        int startIdx = N / 2;
        int fitLength = N - startIdx;

        double sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        for (int i = startIdx; i < N; i++) {
            double t = i * timeStepPs;
            double val = msd[i];
            sumX += t;
            sumY += val;
            sumXX += t * t;
            sumXY += t * val;
        }

        // Linear regression: y = m*x + c
        double denom = (fitLength * sumXX - sumX * sumX);
        if (Math.abs(denom) < 1.0e-12) {
            return 0.0;
        }

        double slope = (fitLength * sumXY - sumX * sumY) / denom; // in A^2/ps
        if (slope < 0.0) {
            slope = 0.0; // clamp unphysical negative drifts
        }

        // D = slope / 6 (in A^2/ps)
        double d_ang2_ps = slope / 6.0;

        // Convert A^2/ps -> cm^2/s (1 A^2/ps = 1.0e-4 cm^2/s)
        double d_cm2_s = d_ang2_ps * 1.0e-4;

        this.diagnostics.clear();
        this.diagnostics.add(String.format("MD Diffusion analysis completed over %d frames.", N));
        this.diagnostics.add(String.format("Linear fit region: from step %d to %d.", startIdx, N - 1));
        this.diagnostics.add(String.format("Calculated self-diffusion coefficient D: %.4e cm2/s.", d_cm2_s));

        return d_cm2_s;
    }
}
