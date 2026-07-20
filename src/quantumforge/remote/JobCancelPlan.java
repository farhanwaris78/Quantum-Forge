/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.Optional;

import quantumforge.hpc.SchedulerAdapter;
import quantumforge.hpc.SchedulerAdapters;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #97 (plan slice): fail-closed review plan for cancelling ONE
 * scheduler job. NOTHING is cancelled from this build - the plan pins exactly
 * what a future runtime step must honor, and its honesty rules exist to make
 * the classic cancel errors structurally impossible:
 *
 * <ul>
 *   <li>the scheduler is TYPED and resolved ONLY through the
 *       {@code SchedulerAdapters} registry (slurm/pbs/pjm/sge); a free-form
 *       scheduler name never reaches a cancel command (CANCEL_SCHEDULER)
 *       and there is deliberately no default adapter;</li>
 *   <li>the job-id grammar is ADAPTER-OWNED: this plan holds NO regex of its
 *       own - it asks the resolved adapter for its cancel tokens and honors
 *       the adapter's refusal verbatim (CANCEL_JOBID). That single-owner rule
 *       is what keeps this review channel and the runtime channel from
 *       drifting apart (e.g. SLURM's array {@code id_index} is SLURM-only
 *       grammar, exactly as the SLURM adapter enforces);</li>
 *   <li>the CONFIRMATION must retype the job id EXACTLY - clicking a button
 *       is not confirmation that you meant THAT job (CANCEL_CONFIRM);</li>
 *   <li>the rendered command is a REVIEW line only, produced by the adapter
 *       itself: scancel (SLURM), qdel (PBS), pjdel (PJM), qdel (SGE) - never
 *       a broad process-name kill and never a directory deletion (both are
 *       stated as FORBIDDEN lines);</li>
 *   <li>the plan declares the ONLY success signal: post-cancel verification
 *       via the adapter's own status query (squeue -j / qstat -f / pjstat -S
 *       / qstat -j) showing the job gone. A transport failure or an empty
 *       error message is NOT a successful cancellation and must never be
 *       displayed as one.</li>
 * </ul>
 *
 * <p>Batch-126 correction (provenance, kept deliberately): this plan's PJM
 * command was previously rendered as {@code pdel} and the verification note
 * named {@code pjobs}; Fujitsu's PJM grammar is {@code pjdel} for cancel and
 * {@code pjstat} for status (Fujitsu Technical Computing Suite manual and
 * e.g. the Kyushu University Genkai job-usage documentation). Delegating the
 * command text to the now-complete PJM adapter removes the textual copy that
 * let that mistake exist.</p>
 *
 * <p>Runtime depth (stated by the kind): the actual SSH channel, the
 * scheduler query parse-back, and the #95 state-machine transition that a
 * CANCELLED verdict must produce.</p>
 */
public final class JobCancelPlan {

    private JobCancelPlan() {
    }

    /** One validated cancellation review plan. */
    public static final class CancelPlan {
        private final String scheduler;
        private final String jobId;
        private final String command;
        private final String statusCommand;

        CancelPlan(String scheduler, String jobId, String command, String statusCommand) {
            this.scheduler = scheduler;
            this.jobId = jobId;
            this.command = command;
            this.statusCommand = statusCommand;
        }

        public String getScheduler() { return this.scheduler; }
        public String getJobId() { return this.jobId; }
        /** The review-only cancel command line (never executed from this build). */
        public String getCommand() { return this.command; }
        /** The adapter's own status query - the ONLY accepted success signal. */
        public String getStatusCommand() { return this.statusCommand; }

        /** The review block spelling out every guarantee and the ONLY success signal. */
        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# QuantumForge job-cancellation REVIEW plan (Roadmap #97, "
                    + "plan slice)\n");
            text.append("# NOTHING has been cancelled - execution requires the runtime "
                    + "SSH channel and your re-review.\n");
            text.append("scheduler        = ").append(this.scheduler).append('\n');
            text.append("job_id           = ").append(this.jobId).append('\n');
            text.append("cancel_command   = ").append(this.command)
                    .append("   # review line only - not executed\n");
            text.append("status_command   = ").append(this.statusCommand)
                    .append("   # the ONLY accepted success signal\n");
            text.append("grammar_owner    = the hpc SchedulerAdapter itself - this plan "
                    + "holds no\n");
            text.append("                   regex copy, so review and runtime cannot drift\n");
            text.append("confirmation     = job id retyped EXACTLY (a button click is "
                    + "not identity confirmation)\n");
            text.append("forbidden        = kill by process NAME; delete the job's\n");
            text.append("                   working/remote directory 'to clean up'\n");
            text.append("verify_after     = the status_command above MUST show the job "
                    + "gone; that\n");
            text.append("                   query is the ONLY success signal - a "
                    + "transport failure or an\n");
            text.append("                   empty stderr is NEVER a successful "
                    + "cancellation\n");
            text.append("state_machine    = on verified absence, the #95 machine MUST "
                    + "record CANCELLED -\n");
            text.append("                   never carpet-FAILED - with the verifying "
                    + "query cited\n");
            return text.toString();
        }
    }

    /**
     * Validates one plan. The confirmation must equal the job id EXACTLY.
     * Codes: CANCEL_SCHEDULER / CANCEL_JOBID / CANCEL_CONFIRM.
     */
    public static OperationResult<CancelPlan> validate(String schedulerText, String jobId,
            String confirmation) {
        Optional<SchedulerAdapter> resolved = SchedulerAdapters.forName(schedulerText);
        if (resolved.isEmpty()) {
            String got = schedulerText == null ? "" : schedulerText.trim();
            return OperationResult.failed("CANCEL_SCHEDULER",
                    "scheduler is TYPED: " + SchedulerAdapters.supportedNames()
                            + " (got '" + got + "') - free-form schedulers never reach a"
                            + " cancel command, and there is no default adapter.",
                    null);
        }
        SchedulerAdapter adapter = resolved.get();
        String id = jobId == null ? "" : jobId.trim();
        String[] cancelTokens;
        String[] statusTokens;
        try {
            cancelTokens = adapter.cancelCommand(id);
            statusTokens = adapter.statusCommand(id);
        } catch (IllegalArgumentException refusal) {
            return OperationResult.failed("CANCEL_JOBID",
                    "job id '" + id + "' violates the ADAPTER-OWNED " + adapter.name()
                            + " grammar (" + refusal.getMessage()
                            + ") - a free-form id can never become a command fragment.",
                    null);
        }
        String confirm = confirmation == null ? "" : confirmation;
        // The confirmation is compared WITHOUT trimming on purpose: the rule is
        // "retype the job id EXACTLY", and a silent trim would quietly forgive
        // sloppy clipboard pastes into a destructive action.
        if (!confirm.equals(id)) {
            return OperationResult.failed("CANCEL_CONFIRM",
                    "confirmation must retype the job id EXACTLY ('" + id + "'; got '"
                            + confirm + "') - identity is proven by typing, not clicking.",
                    null);
        }
        return OperationResult.success("CANCEL_OK", "Cancellation plan validated.",
                new CancelPlan(adapter.name(), id, String.join(" ", cancelTokens),
                        String.join(" ", statusTokens)));
    }
}
