package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.QEExxPlanner.ExxGuidance;
import quantumforge.input.validation.ValidationSeverity;

/** Batch-44 coverage for the EXX k/q grid guidance (Roadmap #70). */
class QEExxPlannerTest {

    private static boolean hasCode(ExxGuidance guidance, String code) {
        return guidance.getIssues().stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    @Test
    void testDivisibleGridIsUsableWithExactPairCount() {
        ExxGuidance guidance = QEExxPlanner.plan(8, 8, 8, 2, 2, 2);
        assertTrue(guidance.isUsable(), guidance.getIssues().toString());
        assertEquals(0L, guidance.errors().size());
        // Explicit recompute: nk_total = 512, nq_total = 8, pairs = 512*8 = 4096 exactly.
        assertEquals(4096L, guidance.getKqPairCount(),
                "8^3 k x 2^3 q pair count is nk_total*nq_total exactly");
        assertTrue(hasCode(guidance, "EXX_COST_COUNT_ONLY"),
                "The pre-symmetry count caveat must be present");
        assertTrue(hasCode(guidance, "EXX_SETTINGS_MANUAL"),
                "The physics-settings honesty note must be present");
    }

    @Test
    void testQunitGridIsAlwaysCompatible() {
        ExxGuidance guidance = QEExxPlanner.plan(6, 4, 2, 1, 1, 1);
        assertTrue(guidance.isUsable());
        // nk_total = 6*4*2 = 48, nq_total = 1 -> 48 pairs.
        assertEquals(48L, guidance.getKqPairCount());
    }

    @Test
    void testNonDivisorIsBlocking() {
        ExxGuidance guidance = QEExxPlanner.plan(6, 4, 4, 2, 3, 2);
        assertFalse(guidance.isUsable());
        assertTrue(hasCode(guidance, "EXX_NQ_NOT_DIVISOR"), guidance.getIssues().toString());
        assertEquals(0L, guidance.getKqPairCount(),
                "No pair count may be quoted for an invalid q grid");
        assertEquals(1L, guidance.errors().stream()
                .filter(i -> i.getCode().equals("EXX_NQ_NOT_DIVISOR")).count());
    }

    @Test
    void testDenserQGridIsBlocking() {
        ExxGuidance guidance = QEExxPlanner.plan(2, 2, 2, 4, 1, 1);
        assertFalse(guidance.isUsable());
        assertTrue(hasCode(guidance, "EXX_NQ_DENSER_THAN_K"), guidance.getIssues().toString());
        assertEquals(0L, guidance.getKqPairCount());
    }

    @Test
    void testInvalidGridsFailClosed() {
        ExxGuidance zeroNq = QEExxPlanner.plan(4, 4, 4, 0, 1, 1);
        assertFalse(zeroNq.isUsable());
        assertTrue(hasCode(zeroNq, "EXX_NQ_INVALID"));
        assertTrue(zeroNq.errors().stream()
                .allMatch(i -> i.getSeverity() == ValidationSeverity.ERROR));

        ExxGuidance zeroK = QEExxPlanner.plan(0, 4, 4, 1, 1, 1);
        assertFalse(zeroK.isUsable());
        assertTrue(hasCode(zeroK, "EXX_KGRID_INVALID"));
        assertEquals(0L, zeroK.getKqPairCount());
    }

    @Test
    void testGridsAreClonedAndImmutable() {
        ExxGuidance guidance = QEExxPlanner.plan(4, 4, 4, 2, 2, 2);
        int[] k = guidance.getKGrid();
        k[0] = 999;
        assertEquals(4, guidance.getKGrid()[0], "Returned grids are defensive copies");
        assertTrue(guidance.isUsable());
    }
}
