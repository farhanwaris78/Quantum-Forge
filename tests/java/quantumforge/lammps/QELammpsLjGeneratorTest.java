package quantumforge.lammps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.lammps.QELammpsLjGenerator.LjParams;

class QELammpsLjGeneratorTest {

    @Test
    void testParamsLookupAndLorentzBerthelotMixing() {
        // Argon (Ar): epsilon = 0.01040 eV, sigma = 3.40 A
        LjParams ar = QELammpsLjGenerator.getParams("Ar");
        assertNotNull(ar);
        assertEquals(0.01040, ar.getEpsilonEv(), 1e-6);
        assertEquals(3.40, ar.getSigmaAng(), 1e-6);

        // Krypton (Kr): epsilon = 0.01470 eV, sigma = 3.65 A
        LjParams kr = QELammpsLjGenerator.getParams("Kr");
        assertNotNull(kr);

        // Mix Argon and Krypton:
        // Mixed sigma = (3.40 + 3.65) / 2 = 3.525 A
        // Mixed epsilon = sqrt(0.01040 * 0.01470) = 0.012364 eV
        LjParams mixed = QELammpsLjGenerator.mixLorentzBerthelot(ar, kr);
        assertNotNull(mixed);
        assertEquals(3.525, mixed.getSigmaAng(), 1e-6);
        assertEquals(Math.sqrt(0.01040 * 0.01470), mixed.getEpsilonEv(), 1e-6);
    }

    @Test
    void testGeneratePairCoefficientsFormat() {
        List<String> elements = new ArrayList<>();
        elements.add("Ar");
        elements.add("Kr");

        String script = QELammpsLjGenerator.generatePairCoefficients(elements, 12.0);
        assertNotNull(script);

        // Verify key LAMMPS pair commands
        assertTrue(script.contains("pair_style      lj/cut 12.00"));
        assertTrue(script.contains("pair_coeff      1 1   0.010400     3.4000")); // Ar-Ar
        assertTrue(script.contains("pair_coeff      2 2   0.014700     3.6500")); // Kr-Kr
        assertTrue(script.contains("pair_coeff      1 2")); // Ar-Kr mixed
    }
}
