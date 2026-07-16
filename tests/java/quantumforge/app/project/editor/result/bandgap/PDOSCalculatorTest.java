package quantumforge.app.project.editor.result.bandgap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PDOSCalculatorTest {
    @Test
    void integratesNonuniformEnergyGridWithTrapezoidsAndDefensiveCopies() {
        double[] energy = {0.0, 1.0, 3.0};
        double[] dos = {0.0, 2.0, 2.0};
        PDOSCalculator.PDOSData data = new PDOSCalculator.PDOSData(energy, dos, 0.5);
        assertArrayEquals(new double[] {0.0, 1.0, 5.0}, data.integratedDOS, 1.0e-12);
        assertNotSame(energy, data.energies);
        assertNotSame(dos, data.totalDOS);

        data.addOrbital("Si-p", new double[] {0.0, 1.0, 1.0});
        assertEquals(1, data.orbitalDOS.size());
    }

    @Test
    void rejectsMismatchedAndUnsortedData() {
        assertThrows(IllegalArgumentException.class,
                () -> new PDOSCalculator.PDOSData(new double[] {0, 1}, new double[] {1}, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PDOSCalculator.PDOSData(new double[] {1, 0}, new double[] {1, 1}, 0));
    }
}
