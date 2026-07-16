package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class PhononDosThermodynamicsTest {

    @Test
    void integratesSimpleDos() {
        double[] freq = {0.0, 100.0, 200.0, 300.0};
        double[] dos = {0.0, 1.0, 1.0, 0.0};
        OperationResult<PhononDosThermodynamics.Result> result =
                PhononDosThermodynamics.integrate(freq, dos, 300.0);
        assertTrue(result.isSuccess(), result.toString());
        PhononDosThermodynamics.Result r = result.getValue().orElseThrow();
        assertTrue(r.getZeroPointEnergyEv() > 0.0);
        assertTrue(r.getIntegratedDos() > 0.0);
        assertTrue(Double.isFinite(r.getHelmholtzFreeEnergyEv()));
        assertTrue(Double.isFinite(r.getHeatCapacityEvPerK()));
    }

    @Test
    void rejectsBadGrid() {
        assertFalse(PhononDosThermodynamics.integrate(new double[] {1, 0}, new double[] {1, 1}, 300)
                .isSuccess());
        assertFalse(PhononDosThermodynamics.integrate(new double[] {0, 1}, new double[] {1, -1}, 300)
                .isSuccess());
        assertFalse(PhononDosThermodynamics.integrate(new double[] {0, 1}, new double[] {1, 1}, 0)
                .isSuccess());
    }
}
