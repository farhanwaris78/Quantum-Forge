/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #100 (per-task run-intent slice): the provenance seam between the
 * validated array sweep ({@link ArraySweepPlanner} + {@link ArrayDeckTemplate})
 * and the execution-time provenance record (RunManifest, #28). Each array
 * slot gets one PLAN-side INTENT line naming the task index, its exact swept
 * value, its directory and - the anchor - the sha256 of the rendered deck it
 * will run. A future site-side executor writes the real
 * {@code .quantumforge.run-manifest.jsonl} next to the run; DIFFing its
 * {@code inputSha256} against this plan's {@code input_sha256} proves which
 * task produced which output without trusting directory names alone.
 *
 * <p>Honesty boundaries (stated, fail-closed):</p>
 * <ul>
 *   <li>this is PLAN side only - nothing executes here, no directory is
 *       created and the field {@code stage} is pinned to
 *       {@code rendered-deck-only} so a consumer can never mistake an intent
 *       for a run record;</li>
 *   <li>field names deliberately MIRROR the execution provenance writer
 *       (the input-sha field name matches; the manifest writer stays the single
 *       owner of what is written at run time - this plan changes nothing
 *       about it);</li>
 *   <li>values render through {@link Double#toString(double)} quoted as
 *       JSON strings (exact, lossless - the batch-131 single-rounding truth
 *       survives every consumer);</li>
 *   <li>two tasks may never hash byte-identical decks (TASK_DECK_DUPLICATE):
 *       identical bytes would make two array slots ceremonial; for a
 *       validated sweep this is an unreachable tripwire, fired rather than
 *       silently shipping a fake sweep.</li>
 * </ul>
 */
public final class ArrayTaskIntent {

    private ArrayTaskIntent() {
        // Utility
    }

    /** One task's intent: index, directory, exact value, deck digest. */
    public static final class TaskIntent {
        private final int taskIndex;
        private final String directory;
        private final String valueExact;
        private final String inputSha256;

        TaskIntent(int taskIndex, String directory, String valueExact, String inputSha256) {
            this.taskIndex = taskIndex;
            this.directory = directory;
            this.valueExact = valueExact;
            this.inputSha256 = inputSha256;
        }

        public int getTaskIndex() { return this.taskIndex; }
        public String getDirectory() { return this.directory; }
        public String getValueExact() { return this.valueExact; }
        public String getInputSha256() { return this.inputSha256; }
    }

    /** The full intent list bound to one sweep's keyword. */
    public static final class TaskIntentPlan {
        private final String keyword;
        private final List<TaskIntent> tasks;

        TaskIntentPlan(String keyword, List<TaskIntent> tasks) {
            this.keyword = keyword;
            this.tasks = List.copyOf(tasks);
        }

        public String getKeyword() { return this.keyword; }
        public List<TaskIntent> getTasks() { return this.tasks; }

        /**
         * JSON Lines, one intent per task. Directory and keyword are
         * grammar-restricted by their owning products (job grammar /
         * keyword grammar), so plain quoting is sufficient - same rule as
         * the sweep manifest's own render.
         */
        public String toJsonLine(TaskIntent intent) {
            return String.format(Locale.ROOT,
                    "{\"task_index\":%d,\"keyword\":\"%s\",\"value_exact\":\"%s\","
                            + "\"directory\":\"%s\",\"input_sha256\":\"%s\","
                            + "\"stage\":\"rendered-deck-only\"}",
                    intent.getTaskIndex(), this.keyword, intent.getValueExact(),
                    intent.getDirectory(), intent.getInputSha256());
        }

        public String toJsonLines() {
            StringBuilder lines = new StringBuilder();
            for (TaskIntent intent : this.tasks) {
                lines.append(toJsonLine(intent)).append('\n');
            }
            return lines.toString();
        }
    }

    /**
     * Plan the intents. Codes: TASK_DECK_DUPLICATE (tripwire, should be
     * unreachable for a validated sweep).
     */
    public static OperationResult<TaskIntentPlan> plan(ArraySweepPlanner.SweepPlan sweep,
            ArrayDeckTemplate.DeckTemplate deck) {
        if (sweep == null) {
            throw new NullPointerException("sweep is required - intents bind to one "
                    + "sweep's exact values");
        }
        if (deck == null) {
            throw new NullPointerException("deck template is required - intents hash "
                    + "the rendered decks, never re-template them");
        }
        List<TaskIntent> tasks = new ArrayList<>();
        int count = deck.getTaskCount();
        for (int i = 1; i <= count; i++) {
            String rendered = deck.renderTaskDeck(i);
            String sha = sha256Hex(rendered);
            for (TaskIntent other : tasks) {
                if (other.getInputSha256().equals(sha)) {
                    return OperationResult.failed("TASK_DECK_DUPLICATE",
                            "task " + i + "'s rendered deck is byte-identical to task "
                                    + other.getTaskIndex() + "'s (sha256 " + sha
                                    + ") - two array slots would run the SAME input and "
                                    + "the sweep would be ceremonial. The single-rounding "
                                    + "arithmetic should make this unreachable; refusing "
                                    + "to ship a fake sweep.",
                            null);
                }
            }
            tasks.add(new TaskIntent(i, sweep.taskDirectory(i),
                    Double.toString(sweep.getValues().get(i - 1)), sha));
        }
        return OperationResult.success("TASK_INTENT_OK",
                "Planned " + count + " per-task run intents with distinct deck digests.",
                new TaskIntentPlan(sweep.getKeyword(), tasks));
    }

    /**
     * sha256 hex of the rendered deck bytes (UTF-8) - the same digest family
     * the execution-time manifest writer and the verified-transfer layer
     * already use; computed here because intents hash TEXT, not files.
     */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }
}
