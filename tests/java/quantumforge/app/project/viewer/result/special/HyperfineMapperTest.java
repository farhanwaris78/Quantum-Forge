package quantumforge.app.project.viewer.result.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HyperfineMapperTest {

    @Test
    void testHyperfineMapperLooksUpIsotopeAndComputesFermiContactCoupling() {
        // Isotope: Carbon-13 (13C), gN = 1.404824
        double gnFe = HyperfineMapper.getNuclearGFactor("13C");
        assertEquals(1.404824, gnFe, 1e-6);

        // Spin density at the nucleus |psi(0)|^2 = 2.5 a.u.^-3 (typical for deep defects)
        double psi0sq = 2.5;

        // A_iso = 44.757237 * gN * |psi(0)|^2
        // A_iso = 44.757237 * 1.404824 * 2.5 = 157.20914 MHz
        double aiso = HyperfineMapper.calculateAiso(psi0sq, "13C");
        assertEquals(157.20914, aiso, 1e-3, "Fermi contact isotropic hyperfine coupling must calculate with absolute physical precision");

        // Isotope: Silicon-29 (29Si), gN = -1.11058
        double aisoSi = HyperfineMapper.calculateAiso(1.2, "29Si");
        // A_iso = 44.757237 * (-1.11058) * 1.2 = -59.6469 MHz
        assertEquals(-59.6469, aisoSi, 1e-3);
    }
}
