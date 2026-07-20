/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #93/#100 (guarded submit-loop slice): the submit SEQUENCE for an
 * array sweep as a strictly-comment review draft. Every token comes from the
 * typed {@link SchedulerAdapter} single owners (submitCommand, and batch-141
 * {@link SchedulerAdapter#arraySubmitSpec()}); NOTHING executes from this
 * plan - it names exactly what a reviewed submission WOULD run, which of the
 * two honest shapes applies, and the checklist that must pass first:
 *
 * <ul>
 *   <li>SINGLE ARRAY SUBMISSION - when the adapter OWNS array knowledge
 *       (slurm, sge, pjm): one submission of the array script with the
 *       documented flag tokens; the array job id is parsed ONLY by the
 *       same adapter's own parseJobId from that one command's stdout;</li>
 *   <li>PER-TASK SUBMIT LOOP - when the adapter deliberately refuses array
 *       knowledge (PBS: Pro -J/PBS_ARRAY_INDEX vs Torque -t/PBS_ARRAYID
 *       divergence, stated verbatim): the same documented submitCommand
 *       once per task, each parsed separately - slower, and honest;</li>
 *   <li>the script path is a PLACEHOLDER token
 *       ({@value #SCRIPT_ANCHOR}) - the staged path is only knowable where
 *       the script actually lands, so the plan names the anchor instead of
 *       inventing a path;</li>
 *   <li>a degenerate range or a spec inconsistency refuses loudly
 *       (SUBMIT_PLAN_*), never silently degrades.</li>
 * </ul>
 */
public final class ArraySubmitPlan {

    /** Placeholder naming where the staged script path belongs. */
    public static final String SCRIPT_ANCHOR = "<staged-script-path>";
    /** Review display cap: long loops show head+tail and say so. */
    public static final int MAX_SHOWN_LOOP_LINES = 6;

    private ArraySubmitPlan() {
        // Utility
    }

    /** Which of the two honest submission shapes applies. */
    public enum Shape { SINGLE_ARRAY, PER_TASK_LOOP }

    /** One guarded submit plan (draft; nothing executes). */
    public static final class SubmitPlan {
        private final Shape shape;
        private final String adapterName;
        private final int taskCount;
        private final String envVar;         // null in PER_TASK_LOOP
        private final List<String> commands; // rendered review command lines
        private final boolean loopCapped;
        private final String reason;         // loop shape carries the adapter reason

        SubmitPlan(Shape shape, String adapterName, int taskCount, String envVar,
                List<String> commands, boolean loopCapped, String reason) {
            this.shape = shape;
            this.adapterName = adapterName;
            this.taskCount = taskCount;
            this.envVar = envVar;
            this.commands = List.copyOf(commands);
            this.loopCapped = loopCapped;
            this.reason = reason == null ? "" : reason;
        }

        public Shape getShape() { return this.shape; }
        public String getAdapterName() { return this.adapterName; }
        public int getTaskCount() { return this.taskCount; }
        public String getEnvVar() { return this.envVar; }
        public List<String> getCommands() { return this.commands; }
        public boolean isLoopCapped() { return this.loopCapped; }
        public String getReason() { return this.reason; }

        /** The strictly-comment review block (every line prefixed '#'). */
        public String reviewBlock() {
            StringBuilder block = new StringBuilder();
            block.append("# Array submission review (DRAFT - NOTHING runs from this plan)\n");
            if (this.shape == Shape.SINGLE_ARRAY) {
                block.append("# shape: SINGLE ARRAY SUBMISSION (scheduler '")
                        .append(this.adapterName).append("' OWNS array semantics)\n");
            } else {
                block.append("# shape: PER-TASK SUBMIT LOOP - the '")
                        .append(this.adapterName)
                        .append("' adapter deliberately owns no array form:\n#   ")
                        .append(this.reason).append("\n");
            }
            block.append("# script anchor: ").append(SCRIPT_ANCHOR)
                    .append(" - the real path exists only where the script is staged\n");
            for (String command : this.commands) {
                block.append("#   ").append(command).append('\n');
            }
            if (this.loopCapped) {
                block.append("#   ... (").append(this.taskCount - MAX_SHOWN_LOOP_LINES)
                        .append(" more line(s) elided in review; every task still gets "
                                + "its own submit - the cap is display-only)\n");
            }
            if (this.shape == Shape.SINGLE_ARRAY) {
                block.append("# parse lane: the array job id comes from the '")
                        .append(this.adapterName)
                        .append("' adapter's own parseJobId on THAT command's stdout -"
                                + " never from pattern-guessing elsewhere\n");
                block.append("# per-task mapping inside the script: $").append(this.envVar)
                        .append(" selects tasks.jsonl line N (1-based, exact)\n");
            } else {
                block.append("# parse lane: EACH submit's stdout feeds the '")
                        .append(this.adapterName)
                        .append("' adapter's own parseJobId - ")
                        .append(this.taskCount).append(" ids, one per task directory\n");
            }
            block.append("# REQUIRED checklist before any real submission:\n");
            block.append("#   [1] the staged script still carries its exit-2 guard - it "
                    + "comes out only after your review\n");
            block.append("#   [2] the flag shape matches YOUR site's version (check the "
                    + "manual named on each adapter's spec)\n");
            block.append("#   [3] the env var name is the one your site documents (probe "
                    + "it in a trivial test job first)\n");
            return block.toString();
        }
    }

    /**
     * The ONE place array-submit tokens are assembled: the adapter's
     * documented submit binary, its owned array flags for range
     * {@code 1..count}, then the remaining submit tokens (script path at
     * the tail). The guarded draft AND the batch-142 executor both call
     * exactly this - draft and wire can never diverge in flag order.
     */
    public static String[] composeArrayTokens(SchedulerAdapter adapter, int count,
            String scriptPath) {
        if (adapter == null) {
            throw new NullPointerException("adapter is required - submit tokens are "
                    + "owned by the adapter, never hand-assembled");
        }
        if (count < 1) {
            throw new IllegalArgumentException("array needs at least one task (got "
                    + count + ")");
        }
        ArraySubmitSpec spec = adapter.arraySubmitSpec();
        if (!spec.isSupported()) {
            throw new IllegalStateException("the '" + adapter.name()
                    + "' adapter owns no array form - " + spec.getUnsupportedReason()
                    + "; the per-task loop executor is the remaining #93 depth");
        }
        String[] flags = spec.renderFlagTokens(1, count);
        String[] submit = adapter.submitCommand(scriptPath);
        String[] tokens = new String[submit.length + flags.length];
        tokens[0] = submit[0];
        System.arraycopy(flags, 0, tokens, 1, flags.length);
        System.arraycopy(submit, 1, tokens, 1 + flags.length, submit.length - 1);
        return tokens;
    }

    /**
     * Build the guarded submit plan for a validated sweep. Code:
     * SUBMIT_DRAFT_OK.
     */
    public static OperationResult<SubmitPlan> plan(ArraySweepPlanner.SweepPlan sweep,
            SchedulerAdapter adapter) {
        if (sweep == null) {
            throw new NullPointerException("sweep is required - the plan names one "
                    + "sweep's submission sequence");
        }
        if (adapter == null) {
            throw new NullPointerException("adapter is required - submit tokens are "
                    + "owned by the adapter, never hand-assembled");
        }
        ArraySubmitSpec spec = adapter.arraySubmitSpec();
        int count = sweep.getValues().size();
        List<String> commands = new ArrayList<>();
        if (spec.isSupported()) {
            commands.add(String.join(" ",
                    composeArrayTokens(adapter, count, SCRIPT_ANCHOR)));
            return OperationResult.success("SUBMIT_DRAFT_OK",
                    "Single-array submission drafted for " + count + " task(s) via the '"
                            + adapter.name() + "' adapter's owned array form.",
                    new SubmitPlan(Shape.SINGLE_ARRAY, adapter.name(), count,
                            spec.getEnvVar(), commands, false, null));
        }
        for (int i = 1; i <= count; i++) {
            if (commands.size() < MAX_SHOWN_LOOP_LINES) {
                String[] submit = adapter.submitCommand(
                        SCRIPT_ANCHOR + "/" + sweep.taskDirectory(i) + "/job.sh");
                commands.add(String.join(" ", submit));
            }
        }
        boolean capped = count > MAX_SHOWN_LOOP_LINES;
        return OperationResult.success("SUBMIT_DRAFT_OK",
                "Per-task submit loop drafted for " + count + " task(s): the '"
                        + adapter.name() + "' adapter owns no array form (stated on the "
                        + "draft) - every task submits the same documented command.",
                new SubmitPlan(Shape.PER_TASK_LOOP, adapter.name(), count, null,
                        commands, capped, spec.getUnsupportedReason()));
    }
}
