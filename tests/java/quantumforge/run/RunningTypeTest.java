package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunningTypeTest {
    @Test
    void convergenceModeRefusesUnconfiguredMockSweep() {
        assertThrows(UnsupportedOperationException.class,
                () -> RunningType.CONVERGE.getCommandList("calculation.in", 1));
    }
}
