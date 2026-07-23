/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.MonitorPollPlan.PollPlan;

class MonitorPollPlanTest {

    @Test
    void backoffScheduleArithmeticIsPinned() {
        OperationResult<PollPlan> result = MonitorPollPlan.validate(10.0, 300.0, 2.0, 6);
        assertTrue(result.isSuccess(), result.toString());
        PollPlan plan = result.getValue().orElseThrow();
        List<double[]> rows = plan.getPreview();
        double[][] expected = {
                {1, 10.0, 10.0}, {2, 20.0, 30.0}, {3, 40.0, 70.0},
                {4, 80.0, 150.0}, {5, 160.0, 310.0}, {6, 300.0, 610.0}
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][1], rows.get(i)[1], 1e-9,
                    "interval row " + (i + 1) + " (cap visibly engaged on the last row)");
            assertEquals(expected[i][2], rows.get(i)[2], 1e-9, "cumulative row " + (i + 1));
        }
        assertEquals(610.0, plan.getHorizonSec(), 1e-9,
                "the horizon is the exact cumulative sum, not an estimate");
        assertEquals(1, plan.getCappedPolls(), "one poll rides at the 300 s cap");
    }

    @Test
    void constantPollingIsDeclaredPlainlyAndPreviewCapsAtTwelve() {
        OperationResult<PollPlan> result = MonitorPollPlan.validate(30.0, 30.0, 1.0, 20);
        assertTrue(result.isSuccess(), result.toString());
        PollPlan plan = result.getValue().orElseThrow();
        assertEquals(12, plan.getPreview().size(), "preview caps at 12 rows");
        assertEquals(600.0, plan.getHorizonSec(), 1e-9, "20 x 30 s constant");
        assertEquals(20, plan.getCappedPolls(), "every poll rides at the cap = constant");
        assertTrue(plan.render().contains("CONSTANT polling - declared plainly"),
                "factor 1.0 is an honest declaration, never dressed up as backoff");
    }

    @Test
    void boundsRefuseLikeAPlanShould() {
        assertEquals("MONITOR_INTERVAL", MonitorPollPlan.validate(
                0.5, 300.0, 2.0, 10).getCode(),
                "sub-second remote polling is a request storm by definition");
        assertEquals("MONITOR_INTERVAL", MonitorPollPlan.validate(
                3601.0, 86400.0, 2.0, 10).getCode());
        assertEquals("MONITOR_MAX", MonitorPollPlan.validate(
                10.0, 5.0, 2.0, 10).getCode(),
                "a cap under its own start is a specification error, not floored");
        assertEquals("MONITOR_MAX", MonitorPollPlan.validate(
                10.0, 90000.0, 2.0, 10).getCode());
        assertEquals("MONITOR_FACTOR", MonitorPollPlan.validate(
                10.0, 300.0, 0.5, 10).getCode(),
                "shrinking intervals are not backoff");
        assertEquals("MONITOR_FACTOR", MonitorPollPlan.validate(
                10.0, 300.0, 5.0, 10).getCode());
        assertEquals("MONITOR_POLLS", MonitorPollPlan.validate(
                10.0, 300.0, 2.0, 0).getCode());
        assertEquals("MONITOR_POLLS", MonitorPollPlan.validate(
                10.0, 300.0, 2.0, 10001).getCode(),
                "unbounded polling is refused by construction");
    }

    @Test
    void rebellionStatementsRender() {
        PollPlan plan = MonitorPollPlan.validate(10.0, 300.0, 2.0, 6)
                .getValue().orElseThrow();
        String text = plan.render();
        assertTrue(text.contains("single_flight"), text);
        assertTrue(text.contains("NOT IMPLEMENTED in this slice"), text + " | " + "jitter absence is stated - no fake claim");
        assertTrue(text.contains("never\n                     'job finished'"), text);
        assertTrue(text.contains("RESUMES the same plan/counters"), text);
    }

    @Test
    void renderStatesItsRelationshipToTheRuntimeLoop() {
        OperationResult<MonitorPollPlan.PollPlan> result =
                MonitorPollPlan.validate(30.0, 300.0, 2.0, 60);
        assertTrue(result.isSuccess(), result.toString());
        String text = result.getValue().orElseThrow().render();
        assertTrue(text.contains("runtime_relationship"), text);
        assertTrue(text.contains("USER POLICY preview"), text);
        assertTrue(text.contains("NOT the runtime loop"), text + " | " + "the draft arithmetic can never be mistaken for the runtime's"
                        + " owned growth shape");
        assertTrue(text.contains("JOB_MONITOR_AUDIT"), text);
    }
}
