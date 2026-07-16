package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.com.math.Matrix3D;
import quantumforge.run.parser.QEGridDensityDifference.DiffResult;
import quantumforge.run.parser.QEGridDensityDifference.Grid3D;

class QEGridDensityDifferenceTest {

    @Test
    void testComputeDifferenceValidatesCompatibilityAndIntegratesCorrectly() {
        double[][] lattice = Matrix3D.unit(4.0); // 4x4x4 cubic cell (volume = 64)
        int nx = 5, ny = 5, nz = 5;

        // Mock System Grid (System with charge)
        double[][][] sysVals = new double[nx][ny][nz];
        // Mock Component 1 (Substrate)
        double[][][] subVals = new double[nx][ny][nz];
        // Mock Component 2 (Adsorbate)
        double[][][] adsVals = new double[nx][ny][nz];

        // Populate with mock charge densities
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    sysVals[i][j][k] = 2.5;
                    subVals[i][j][k] = 1.5;
                    adsVals[i][j][k] = 0.9; // difference will be 2.5 - 1.5 - 0.9 = 0.1 at each grid point
                }
            }
        }

        Grid3D system = new Grid3D(lattice, nx, ny, nz, sysVals);
        Grid3D substrate = new Grid3D(lattice, nx, ny, nz, subVals);
        Grid3D adsorbate = new Grid3D(lattice, nx, ny, nz, adsVals);

        List<Grid3D> components = new ArrayList<>();
        components.add(substrate);
        components.add(adsorbate);

        DiffResult result = QEGridDensityDifference.computeDifference(system, components, 1e-4);
        assertNotNull(result);
        assertTrue(result.isCompatible(), "Grids must be compatible");

        Grid3D diff = result.getDifferenceGrid();
        assertNotNull(diff);
        assertEquals(0.1, diff.getValues()[0][0][0], 1e-6, "Delta_rho should be 0.1 at each point");

        // Spatial integral of delta_rho (constant 0.1 over volume 64)
        // Integral = (64 / (5*5*5)) * Sum(0.1) = 64 * 0.1 = 6.4 electrons
        assertEquals(6.4, result.getIntegratedChargeDifference(), 1e-4);
    }

    @Test
    void testIncompatibleGridFailsClosed() {
        double[][] lattice = Matrix3D.unit(4.0);
        double[][] latticeDifferent = Matrix3D.unit(5.0); // mismatched cell size

        double[][][] sysVals = new double[2][2][2];
        Grid3D system = new Grid3D(lattice, 2, 2, 2, sysVals);
        Grid3D component = new Grid3D(latticeDifferent, 2, 2, 2, sysVals);

        List<Grid3D> components = new ArrayList<>();
        components.add(component);

        DiffResult result = QEGridDensityDifference.computeDifference(system, components, 1e-4);
        assertNotNull(result);
        assertFalse(result.isCompatible(), "Mismatched lattices should be incompatible");
    }
}
