/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.CutoffLadderPlan.Ladder;
import quantumforge.run.CutoffLadderPlan.Rung;

class CutoffLadderPlanTest {

    @Test
    void ladderPinsUnitsImpliedRhoAndHeuristicCost() {
        OperationResult<Ladder> result = CutoffLadderPlan.validate("30; 40; 60", 8.0);
        assertTrue(result.isSuccess(), result.toString());
        Ladder ladder = result.getValue().orElseThrow();
        assertEquals(8.0, ladder.getRhoRatio(), 1e-15);
        assertEquals(3, ladder.getRungs().size());

        Rung first = ladder.getRungs().get(0);
        assertEquals(30.0, first.getWfcRy(), 1e-15);
        assertEquals(408.170794, first.getWfcEv(), 1e-6,
                "30 Ry x QEUnits.EV_PER_RY - shared constant, not a retype");
        assertEquals(240.0, first.getImpliedRhoRy(), 1e-9,
                "ecutrho is IMPLIED from the deck ratio - the plan and deck cannot drift");
        assertTrue(first.getCostFactorVsPrev() == null);

        Rung second = ladder.getRungs().get(1);
        assertEquals(544.227725, second.getWfcEv(), 1e-6);
        assertEquals(320.0, second.getImpliedRhoRy(), 1e-9);
        assertEquals(1.539601, second.getCostFactorVsPrev(), 1e-6,
                "(40/30)^1.5 - exact arithmetic of a LABELED rule-of-thumb");

        Rung third = ladder.getRungs().get(2);
        assertEquals(816.341587, third.getWfcEv(), 1e-6);
        assertEquals(480.0, third.getImpliedRhoRy(), 1e-9);
        assertEquals(1.837117, third.getCostFactorVsPrev(), 1e-6);
    }

    @Test
    void ratioUnderOneRefusesWithTheReasonStated() {
        OperationResult<Ladder> result = CutoffLadderPlan.validate("30; 40", 0.5);
        assertFalse(result.isSuccess());
        assertEquals("CUTOFF_RATIO", result.getCode());
        assertTrue(result.getMessage().contains("COARSER"));
        assertEquals("CUTOFF_RATIO", CutoffLadderPlan.validate(
                "30; 40", Double.NaN).getCode(), "no invented default for the ratio");
        assertEquals(1.0, CutoffLadderPlan.validate(
                "30; 40", 1.0).getValue().orElseThrow().getRhoRatio(), 1e-15,
                "ratio exactly 1 is legal (NC pseudos commonly run it)");
    }

    @Test
    void descendingAndFlatRefuse() {
        OperationResult<Ladder> descending = CutoffLadderPlan.validate("40; 30", 8.0);
        assertFalse(descending.isSuccess());
        assertEquals("CUTOFF_LADDER", descending.getCode(), "coarsening refuses - never re-sorted");
        assertEquals("CUTOFF_LADDER", CutoffLadderPlan.validate("40; 40", 8.0).getCode());
        assertEquals("CUTOFF_LADDER", CutoffLadderPlan.validate("40", 8.0).getCode());
        assertEquals("CUTOFF_LADDER", CutoffLadderPlan.validate("", 8.0).getCode());
    }

    @Test
    void valueBandRefuses() {
        assertEquals("CUTOFF_VALUE", CutoffLadderPlan.validate("4; 30", 8.0).getCode(),
                "below the owned band refuses - the band is ours, stated as such");
        assertEquals("CUTOFF_VALUE", CutoffLadderPlan.validate("30; 501", 8.0).getCode());
        assertEquals("CUTOFF_VALUE", CutoffLadderPlan.validate("30; forty", 8.0).getCode());
        assertEquals("CUTOFF_VALUE", CutoffLadderPlan.validate("30; Infinity", 8.0).getCode());
        OperationResult<Ladder> equalRungs = CutoffLadderPlan.validate("30; 30; 60", 8.0);
        assertTrue(equalAllowed(equalRungs),
                "equal neighbors pass when the ladder never descends and rises once");
    }

    private static boolean equalAllowed(OperationResult<Ladder> result) {
        return result.isSuccess();
    }
}
