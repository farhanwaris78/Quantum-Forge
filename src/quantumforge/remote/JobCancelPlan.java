/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #97 (plan slice): fail-closed review plan for cancelling ONE
 * scheduler job. NOTHING is cancelled from this build - the plan pins exactly
 * what a future runtime step must honor, and its honesty rules exist to make
 * the classic cancel errors structurally impossible:
 *
 * <ul>
 *   <li>the scheduler is TYPED (slurm/pbs/pjm/sge) and the job-id grammar is
 *       OWNED PER SCHEDULER: SLURM accepts a numeric job id or an array task
 *       {@code id_index} (both numeric); PBS/PJM/SGE accept a numeric id
 *       (with an optional {@code .server} suffix stated and preserved for PBS
 *       style). A free-form string is refused - it can never become a shell
 *       fragment (CANCEL_JOBID);</li>
 *   <li>the CONFIRMATION must retype the job id EXACTLY - clicking a button
 *       is not confirmation that you meant THAT job (CANCEL_CONFIRM);</li>
 *   <li>the rendered command is a REVIEW line only: scancel (SLURM), qdel
 *       (PBS), pdel (PJM), qdel (SGE) - never a broad process-name kill and
 *       never a directory deletion (both are stated as FORBIDDEN lines);</li>
 *   <li>the plan declares the ONLY success signal: post-cancel verification
 *       via the scheduler query (squeue/qstat/pjobs) showing the job gone. A
 *       transport failure or an empty error message is NOT a successful
 *       cancellation and must never be displayed as one.</li>
 * </ul>
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

        CancelPlan(String scheduler, String jobId, String command) {
            this.scheduler = scheduler;
            this.jobId = jobId;
            this.command = command;
        }

        public String getScheduler() { return this.scheduler; }
        public String getJobId() { return this.jobId; }
        /** The review-only command line (never executed from this build). */
        public String getCommand() { return this.command; }

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
            text.append("confirmation     = job id retyped EXACTLY (a button click is "
                    + "not identity confirmation)\n");
            text.append("forbidden        = kill by process NAME; delete the job's\n");
            text.append("                   working/remote directory 'to clean up'\n");
            text.append("verify_after     = scheduler query (squeue/qstat/pjobs) MUST "
                    + "show the job gone;\n");
            text.append("                   that query is the ONLY success signal - a "
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
        String scheduler = schedulerText == null ? "" : schedulerText.trim()
                .toLowerCase(Locale.ROOT);
        String command;
        boolean arraysAllowed;
        switch (scheduler) {
        case "slurm":
            command = "scancel";
            arraysAllowed = true;
            break;
        case "pbs":
            command = "qdel";
            arraysAllowed = false;
            break;
        case "pjm":
            command = "pdel";
            arraysAllowed = false;
            break;
        case "sge":
            command = "qdel";
            arraysAllowed = false;
            break;
        default:
            return OperationResult.failed("CANCEL_SCHEDULER",
                    "scheduler is TYPED: slurm/pbs/pjm/sge (got '" + scheduler
                            + "') - free-form schedulers never reach a cancel command.",
                    null);
        }
        String id = jobId == null ? "" : jobId.trim();
        if (!id.matches(arraysAllowed ? "[0-9]{1,10}(_[0-9]{1,10})?"
                : "[0-9]{1,10}(\\.[A-Za-z0-9.-]{1,63})?")) {
            return OperationResult.failed("CANCEL_JOBID",
                    "job id '" + id + "' violates the owned " + scheduler + " grammar ("
                            + (arraysAllowed
                                    ? "numeric, optionally array 'id_index', both numeric"
                                    : "numeric, optional '.server' suffix")
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
                new CancelPlan(scheduler, id, command + " " + id));
    }
}
