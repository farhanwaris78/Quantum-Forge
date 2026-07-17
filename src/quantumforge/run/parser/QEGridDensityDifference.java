/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import quantumforge.com.math.Matrix3D;

/**
 * Parses and computes 3D charge density differences (rho_system - Sum rho_components)
 * on spatial grids, verifying grid/cell compatibility and reporting the spatial 
 * charge integral to validate conservation laws (Roadmap #57).
 */
public final class QEGridDensityDifference {

    public static final class Grid3D {
        private final double[][] lattice; // 3x3 lattice in Angstroms
        private final int nx, ny, nz;     // Grid dimensions
        private final double[][][] values; // 3D grid values

        public Grid3D(double[][] lattice, int nx, int ny, int nz, double[][][] values) {
            this.lattice = Matrix3D.copy(Objects.requireNonNull(lattice, "lattice"));
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.values = new double[nx][ny][nz];
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    System.arraycopy(values[i][j], 0, this.values[i][j], 0, nz);
                }
            }
        }

        public double[][] getLattice() { return Matrix3D.copy(this.lattice); }
        public int getNx() { return this.nx; }
        public int getNy() { return this.ny; }
        public int getNz() { return this.nz; }
        public double[][][] getValues() {
            double[][][] out = new double[nx][ny][nz];
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    System.arraycopy(this.values[i][j], 0, out[i][j], 0, nz);
                }
            }
            return out;
        }

        /**
         * Calculates the unit cell volume Omega in Angstrom^3.
         */
        public double getVolume() {
            return Math.abs(Matrix3D.determinant(this.lattice));
        }

        /**
         * Integrates the grid values over the unit cell volume:
         * Integral = (Omega / (Nx * Ny * Nz)) * Sum(rho_ijk)
         */
        public double integrate() {
            double sum = 0.0;
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    for (int k = 0; k < nz; k++) {
                        sum += this.values[i][j][k];
                    }
                }
            }
            double dV = this.getVolume() / (nx * ny * nz);
            return sum * dV;
        }
    }

    public static final class DiffResult {
        private final Grid3D differenceGrid;
        private final double integratedChargeDifference;
        private final boolean compatible;
        private final String diagnosticMessage;

        public DiffResult(Grid3D differenceGrid, double integratedCharge, boolean compatible, String diagnosticMessage) {
            this.differenceGrid = differenceGrid;
            this.integratedChargeDifference = integratedCharge;
            this.compatible = compatible;
            this.diagnosticMessage = Objects.requireNonNull(diagnosticMessage);
        }

        public Grid3D getDifferenceGrid() { return this.differenceGrid; }
        public double getIntegratedChargeDifference() { return this.integratedChargeDifference; }
        public boolean isCompatible() { return this.compatible; }
        public String getDiagnosticMessage() { return this.diagnosticMessage; }
    }

    private QEGridDensityDifference() {
        // Utility
    }

    /**
     * Verifies if two 3D grids have compatible dimensions and identical lattices within tolerances.
     */
    public static boolean isCompatible(Grid3D g1, Grid3D g2, double latticeTolerance) {
        if (g1 == null || g2 == null) {
            return false;
        }

        if (g1.getNx() != g2.getNx() || g1.getNy() != g2.getNy() || g1.getNz() != g2.getNz()) {
            return false;
        }

        return Matrix3D.equals(g1.getLattice(), g2.getLattice(), latticeTolerance);
    }

    /**
     * Subtracts the sum of constituent component grids from the main system grid:
     * Delta_rho = rho_system - Sum_i(rho_component_i)
     */
    public static DiffResult computeDifference(Grid3D system, List<Grid3D> components, double latticeTolerance) {
        if (system == null) {
            return new DiffResult(null, 0.0, false, "System grid is null.");
        }
        if (components == null || components.isEmpty()) {
            return new DiffResult(null, 0.0, false, "No component grids supplied for difference subtraction.");
        }

        // Verify grid compatibility
        for (int i = 0; i < components.size(); i++) {
            Grid3D comp = components.get(i);
            if (!isCompatible(system, comp, latticeTolerance)) {
                return new DiffResult(null, 0.0, false, String.format(
                    "Incompatible grids: Component %d has different dimensions (%d x %d x %d vs system %d x %d x %d) or mismatched lattice vectors.",
                    i + 1, comp.getNx(), comp.getNy(), comp.getNz(), system.getNx(), system.getNy(), system.getNz()
                ));
            }
        }

        int nx = system.getNx();
        int ny = system.getNy();
        int nz = system.getNz();

        double[][][] diffVals = new double[nx][ny][nz];
        double[][][] sysVals = system.getValues();

        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    double val = sysVals[i][j][k];
                    for (Grid3D comp : components) {
                        val -= comp.getValues()[i][j][k];
                    }
                    diffVals[i][j][k] = val;
                }
            }
        }

        Grid3D diffGrid = new Grid3D(system.getLattice(), nx, ny, nz, diffVals);
        double integral = diffGrid.integrate();

        String msg = String.format("Density difference computed successfully. Net integrated charge difference: %.6f electrons (Charge conservation validation).", integral);
        return new DiffResult(diffGrid, integral, true, msg);
    }
}
