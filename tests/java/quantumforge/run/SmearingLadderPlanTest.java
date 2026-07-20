/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.SmearingLadderPlan.Ladder;
import quantumforge.run.SmearingLadderPlan.Rung;

class SmearingLadderPlanTest {

    @Test
    void downLadderPinsBothUnitsAndReductionRatios() {
        OperationResult<Ladder> result = SmearingLadderPlan.validate(
                "gaussian", "0.02; 0.01; 0.005");
        assertTrue(result.isSuccess(), result.toString());
        Ladder ladder = result.getValue().orElseThrow();
        assertEquals("gaussian", ladder.getScheme());
        assertEquals(3, ladder.getRungs().size());

        Rung first = ladder.getRungs().get(0);
        assertEquals(0.02, first.getDegaussRy(), 1e-15);
        assertEquals(0.272114, first.getDegaussEv(), 1e-6,
                "0.02 Ry x QEUnits.EV_PER_RY - the shared constant, not a retype");
        assertTrue(first.getReductionVsPrev() == null, "rung 1 has no previous ratio");

        Rung second = ladder.getRungs().get(1);
        assertEquals(0.136057, second.getDegaussEv(), 1e-6);
        assertEquals(2.0, second.getReductionVsPrev(), 1e-12);

        Rung third = ladder.getRungs().get(2);
        assertEquals(0.068028, third.getDegaussEv(), 1e-6);
        assertEquals(2.0, third.getReductionVsPrev(), 1e-12);
    }

    @Test
    void schemeAliasesNormalizeAndUnknownSchemesRefuse() {
        assertEquals("mv", SmearingLadderPlan.validate(
                "marzari-vanderbilt", "0.02; 0.01").getValue().orElseThrow().getScheme());
        assertEquals("fd", SmearingLadderPlan.validate(
                "Fermi-Dirac", "0.02; 0.01").getValue().orElseThrow().getScheme());
        assertEquals("mp", SmearingLadderPlan.validate(
                "methfessel-paxton", "0.02; 0.01").getValue().orElseThrow().getScheme());
        OperationResult<Ladder> free = SmearingLadderPlan.validate(
                "tetrahedron", "0.02; 0.01");
        assertFalse(free.isSuccess());
        assertEquals("SMEAR_SCHEME", free.getCode(),
                "free-form schemes refuse - tetrahedron is not a smearing scheme");
    }

    @Test
    void wideningLaddersRefuseRatherThanResort() {
        OperationResult<Ladder> widening = SmearingLadderPlan.validate(
                "gaussian", "0.01; 0.02");
        assertFalse(widening.isSuccess());
        assertEquals("SMEAR_LADDER", widening.getCode(),
                "a widening study inverts the physical question - no silent re-sort");
        OperationResult<Ladder> flat = SmearingLadderPlan.validate(
                "gaussian", "0.01; 0.01");
        assertEquals("SMEAR_LADDER", flat.getCode(), "unchanging ladders carry no information");
        assertEquals("SMEAR_LADDER", SmearingLadderPlan.validate(
                "gaussian", "0.01").getCode(), "a single value is not a study");
        assertEquals("SMEAR_LADDER", SmearingLadderPlan.validate(
                "gaussian", "").getCode());
    }

    @Test
    void degaussBoundsRefuse() {
        assertEquals("SMEAR_VALUE", SmearingLadderPlan.validate(
                "gaussian", "0.02; 0.0").getCode(),
                "degauss = 0 is a different calculation, not a rung");
        assertEquals("SMEAR_VALUE", SmearingLadderPlan.validate(
                "gaussian", "0.02; 2.0").getCode());
        assertEquals("SMEAR_VALUE", SmearingLadderPlan.validate(
                "gaussian", "0.02; 1e-5").getCode());
        assertEquals("SMEAR_VALUE", SmearingLadderPlan.validate(
                "gaussian", "0.02; oops").getCode());
        assertEquals("SMEAR_VALUE", SmearingLadderPlan.validate(
                "gaussian", "0.02; NaN").getCode());
        OperationResult<Ladder> equalAllowed = SmearingLadderPlan.validate(
                "gaussian", "0.02; 0.02; 0.01");
        assertTrue(equalAllowed.isSuccess(),
                "equal neighbors are allowed - the ladder must never WIDEN, and one "
                        + "strict decrease exists");
    }
}
