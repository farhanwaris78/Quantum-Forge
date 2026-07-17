package quantumforge.app.project.viewer.result.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectiveMassTensorTest {

    @Test
    void testEffectiveMassTensorFitsIsotropicParaboloidExactly() {
        // Construct a grid of 3D k-points around Gamma: 27 points in a 3x3x3 grid from -0.1 to 0.1 bohr^-1
        double[] grid = {-0.1, 0.0, 0.1};
        int M = 27;
        double[][] kpoints = new double[M][3];
        double[] energies = new double[M];

        int idx = 0;
        for (double kx : grid) {
            for (double ky : grid) {
                for (double kz : grid) {
                    kpoints[idx][0] = kx;
                    kpoints[idx][1] = ky;
                    kpoints[idx][2] = kz;

                    // Let E(k) = 2.0 * kx^2 + 2.0 * ky^2 + 2.0 * kz^2 (isotropic parabolic band!)
                    energies[idx] = 2.0 * kx * kx + 2.0 * ky * ky + 2.0 * kz * kz;
                    idx++;
                }
            }
        }

        double[][] mStar = EffectiveMassTensor.calculateEffectiveMassTensor(kpoints, energies);
        assertNotNull(mStar);

        // Analytical Hessian H_ii = d^2E/dk_i^2 = 4.0
        // Effective Mass m*_ii = 1 / H_ii = 0.25!
        assertEquals(0.25, mStar[0][0], 1e-6);
        assertEquals(0.25, mStar[1][1], 1e-6);
        assertEquals(0.25, mStar[2][2], 1e-6);

        // Off-diagonal anisotropic shear masses must be exactly 0
        assertEquals(0.0, mStar[0][1], 1e-6);
        assertEquals(0.0, mStar[0][2], 1e-6);
        assertEquals(0.0, mStar[1][2], 1e-6);
    }
}
