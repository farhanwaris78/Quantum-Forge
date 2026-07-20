/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Batch-140 (#100 per-task run-intent slice): distinct digest per task,
 * exact JSONL shape, the stage pin that keeps intents from masquerading as
 * run records, and the duplicate tripwire - fired directly through the
 * package-private plan objects it was built for.
 */
class ArrayTaskIntentTest {

    private static final String DECK = "&SYSTEM\n    ecutwfc = 30.0,  ! cutoff\n/\n";

    private static ArraySweepPlanner.SweepPlan sweep() {
        return ArraySweepPlanner.plan("ecutwfc", 30.0, 10.0, 3, "si-cut")
                .getValue().orElseThrow();
    }

    private static ArrayDeckTemplate.DeckTemplate deck(ArraySweepPlanner.SweepPlan sweep) {
        return ArrayDeckTemplate.validate(sweep, DECK).getValue().orElseThrow();
    }

    @Test
    void everyTaskGetsADistinctDigestAndExactValue() {
        ArraySweepPlanner.SweepPlan sweep = sweep();
        ArrayDeckTemplate.DeckTemplate deck = deck(sweep);
        OperationResult<ArrayTaskIntent.TaskIntentPlan> planned =
                ArrayTaskIntent.plan(sweep, deck);
        assertTrue(planned.isSuccess(), planned.getMessage());
        assertEquals("TASK_INTENT_OK", planned.getCode());
        ArrayTaskIntent.TaskIntentPlan intents = planned.getValue().orElseThrow();
        assertEquals(3, intents.getTasks().size());
        assertEquals("si-cut-001", intents.getTasks().get(0).getDirectory());
        assertEquals("30.0", intents.getTasks().get(0).getValueExact());
        assertEquals("50.0", intents.getTasks().get(2).getValueExact());
        // Distinct digest per distinct deck.
        assertTrue(intents.getTasks().get(0).getInputSha256()
                .matches("[0-9a-f]{64}"));
        assertFalse(intents.getTasks().get(0).getInputSha256()
                .equals(intents.getTasks().get(1).getInputSha256()));
        String jsonl = intents.toJsonLines();
        assertEquals(3, jsonl.trim().split("\n").length);
        String first = intents.toJsonLine(intents.getTasks().get(0));
        assertTrue(first.startsWith("{\"task_index\":1,\"keyword\":\"ecutwfc\","
                + "\"value_exact\":\"30.0\",\"directory\":\"si-cut-001\","), first);
        assertTrue(first.contains("\"input_sha256\":\""
                + intents.getTasks().get(0).getInputSha256() + "\","), first);
        assertTrue(first.endsWith("\"stage\":\"rendered-deck-only\"}"),
                "the stage pin keeps intents distinguishable from real run records: "
                        + first);
    }

    @Test
    void duplicateDeckDigestTripwireNamesBothOffenders() {
        // Sanity first: distinct validated values pass with distinct digs.
        ArraySweepPlanner.SweepPlan normal = ArraySweepPlanner.plan(
                "ecutwfc", 30.0, 20.0, 2, "dup").getValue().orElseThrow();
        ArrayTaskIntent.TaskIntentPlan okPlan = ArrayTaskIntent
                .plan(normal, deck(normal)).getValue().orElseThrow();
        assertEquals(2, okPlan.getTasks().size(), "sanity: distinct values pass");
        // The tripwire: a VALID sweep can never hold equal values, but the
        // guard must fire rather than ship two slots of identical deck bytes
        // - hence the same-package, hand-built plan carries them directly.
        ArraySweepPlanner.SweepPlan handSweep = new ArraySweepPlanner.SweepPlan(
                "ecutwfc", java.util.List.of(30.0, 30.0), "dup");
        ArrayDeckTemplate.DeckTemplate handDeck = ArrayDeckTemplate
                .validate(handSweep, DECK).getValue().orElseThrow();
        OperationResult<ArrayTaskIntent.TaskIntentPlan> refused =
                ArrayTaskIntent.plan(handSweep, handDeck);
        assertFalse(refused.isSuccess(), "identical deck bytes must not ship");
        assertEquals("TASK_DECK_DUPLICATE", refused.getCode());
        assertTrue(refused.getMessage().contains("task 2")
                        && refused.getMessage().contains("task 1"),
                "the refusal names both offenders: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("ceremonial"), refused.getMessage());
    }

    @Test
    void nullArgumentsFailLoudlyNotSilently() {
        ArraySweepPlanner.SweepPlan sweep = sweep();
        ArrayDeckTemplate.DeckTemplate deck = deck(sweep);
        NullPointerException noSweep = assertThrows(NullPointerException.class,
                () -> ArrayTaskIntent.plan(null, deck));
        assertTrue(noSweep.getMessage().contains("sweep is required"), noSweep.getMessage());
        NullPointerException noDeck = assertThrows(NullPointerException.class,
                () -> ArrayTaskIntent.plan(sweep, null));
        assertTrue(noDeck.getMessage().contains("never re-template"), noDeck.getMessage());
    }
}
