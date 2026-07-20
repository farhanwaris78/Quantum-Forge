/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #95 (guard slice): the LEGAL job-state transition table plus the
 * scheduler-signal mapping, as a pure tested truth function. No jobs exist in
 * this build - the guard is the contract the runtime state machine and the
 * batch-112 cancel plan must satisfy:
 *
 * <ul>
 *   <li>states (typed): STAGED -&gt; SUBMITTED -&gt; PENDING -&gt; RUNNING
 *       -&gt; terminal {COMPLETED, FAILED, CANCELLED, UNKNOWN};</li>
 *   <li>{@link #transition} fails closed on ILLEGAL edges (FAILED -&gt;
 *       RUNNING, COMPLETED -&gt; anything, etc.) with JOBSTATE_TRANSITION -
 *       terminals never leave their state, and a job never moves BACKWARD;
 *       the ONLY sideways edge is -&gt; UNKNOWN, permitted from any
 *       non-terminal state and ALWAYS labelled "reconciliation, not
 *       progress";</li>
 *   <li>{@link #mapSignal} maps scheduler codes (slurm two-letter, plus
 *       pbs/pjm/sge single letters) to states; an unrecognized signal is a
 *       SUCCESS result carrying UNKNOWN - unknown is a HONEST RESULT, while
 *       guessing a state would be the actual error (JOBSTATE_SCHEDULER
 *       refuses unrecognized schedulers);</li>
 *   <li>CANCELLED is reachable ONLY from SUBMITTED/PENDING/RUNNING and the
 *       batch-112 verification must already have shown the job absent -
 *       the message for a direct mapping of a cancel signal says so.</li>
 * </ul>
 */
public final class JobStateGuard {

    /** The typed states. */
    public enum State {
        STAGED, SUBMITTED, PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, UNKNOWN;

        /** Terminal states never transition out. */
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED
                    || this == UNKNOWN;
        }
    }

    private JobStateGuard() {
    }

    /** A transition verdict. isReconciliation = the sideways -> UNKNOWN edge. */
    public static final class Verdict {
        private final State from;
        private final State to;
        private final boolean reconciliation;

        Verdict(State from, State to, boolean reconciliation) {
            this.from = from;
            this.to = to;
            this.reconciliation = reconciliation;
        }

        public State getFrom() { return this.from; }
        public State getTo() { return this.to; }
        /** True exactly for the sideways UNKNOWN edge - progress it is NOT. */
        public boolean isReconciliation() { return this.reconciliation; }
    }

    /** Legal edges per state. from == to is always refused (a no-op is a bug caller). */
    public static OperationResult<Verdict> transition(String fromText, String toText) {
        State from = parseState(fromText);
        State to = parseState(toText);
        if (from == null || to == null) {
            return OperationResult.failed("JOBSTATE_UNKNOWN_TEXT",
                    "state names must be typed: staged/submitted/pending/running/"
                            + "completed/failed/cancelled/unknown (got '" + fromText
                            + "' -> '" + toText + "').",
                    null);
        }
        if (from == to) {
            return OperationResult.failed("JOBSTATE_TRANSITION",
                    from + " -> " + to + " is a self-edge; a no-op transition is a "
                            + "caller bug, not reconciliation.",
                    null);
        }
        if (from.isTerminal()) {
            return OperationResult.failed("JOBSTATE_TRANSITION",
                    from + " is TERMINAL and never leaves its state (requested -> "
                            + to + "). A finished job changing meaning rewrites history.",
                    null);
        }
        if (to == State.UNKNOWN) {
            return OperationResult.success("JOBSTATE_OK",
                    from + " -> UNKNOWN permitted as RECONCILIATION (not progress): "
                            + "record the scheduler read as unknown and keep watching.",
                    new Verdict(from, to, true));
        }
        boolean legal = switch (from) {
        case STAGED -> to == State.SUBMITTED;
        case SUBMITTED -> to == State.PENDING || to == State.FAILED
                || to == State.CANCELLED;
        case PENDING -> to == State.RUNNING || to == State.CANCELLED
                || to == State.FAILED;
        case RUNNING -> to == State.COMPLETED || to == State.FAILED
                || to == State.CANCELLED;
        default -> false;
        };
        if (!legal) {
            if (to == State.CANCELLED) {
                return OperationResult.failed("JOBSTATE_TRANSITION",
                        from + " -> CANCELLED is illegal: CANCELLED requires the job to "
                                + "have been LIVE (submitted/pending/running) AND the "
                                + "batch-112 scheduler query to have shown it gone - "
                                + "a staged-only job was never live.",
                        null);
            }
            return OperationResult.failed("JOBSTATE_TRANSITION",
                    from + " -> " + to + " is not a legal edge (jobs move forward: "
                            + "staged->submitted->pending->running->terminal; backward "
                            + "edges rewrite history).",
                    null);
        }
        return OperationResult.success("JOBSTATE_OK", from + " -> " + to + " legal.",
                new Verdict(from, to, false));
    }

    /**
     * Maps a scheduler signal to a state. Unrecognized signals SUCCEED with
     * UNKNOWN (honest result) - an unknown scheduler refuses.
     * Code: JOBSTATE_SCHEDULER.
     */
    public static OperationResult<State> mapSignal(String schedulerText, String signal) {
        String scheduler = schedulerText == null ? "" : schedulerText.trim()
                .toLowerCase(Locale.ROOT);
        String sig = signal == null ? "" : signal.trim().toUpperCase(Locale.ROOT);
        State mapped = null;
        switch (scheduler) {
        case "slurm":
            if (sig.equals("PD")) {
                mapped = State.PENDING;
            } else if (sig.equals("R") || sig.equals("CF") || sig.equals("CG")) {
                mapped = State.RUNNING;   // configuring/completing: still LIVE
            } else if (sig.equals("CD")) {
                mapped = State.COMPLETED;
            } else if (sig.equals("F") || sig.equals("NF") || sig.equals("TO")
                    || sig.equals("BF") || sig.equals("DL") || sig.equals("OOM")) {
                mapped = State.FAILED;
            } else if (sig.equals("CA") || sig.equals("PR")) {
                mapped = State.CANCELLED; // cancel/preempt die by cancel semantics
            }
            break;
        case "pbs":
            if (sig.equals("Q") || sig.equals("H") || sig.equals("W") || sig.equals("S")
                    || sig.equals("T")) {
                mapped = State.PENDING;
            } else if (sig.equals("R") || sig.equals("E")) {
                mapped = State.RUNNING;   // E = exiting: still LIVE, stated as running
            } else if (sig.equals("F")) {
                mapped = State.COMPLETED; // PBS F = finished
            } else if (sig.equals("X")) {
                mapped = State.FAILED;    // PBS X = exited with error
            }
            break;
        case "pjm":
            if (sig.equals("ACC") || sig.equals("QUE") || sig.equals("HLD")) {
                mapped = State.PENDING;
            } else if (sig.equals("RUN")) {
                mapped = State.RUNNING;
            } else if (sig.equals("EXT")) {
                mapped = State.FAILED;    // PJM EXT = exited with error
            } else if (sig.equals("CCL")) {
                mapped = State.CANCELLED;
            }
            break;
        case "sge":
            if (sig.equals("QW") || sig.equals("HQW") || sig.equals("T")) {
                mapped = State.PENDING;
            } else if (sig.equals("R") || sig.equals("DR") || sig.equals("DT")) {
                mapped = State.RUNNING;   // deletion states: still LIVE until verified gone
            } else if (sig.equals("EQW")) {
                mapped = State.FAILED;
            }
            break;
        default:
            return OperationResult.failed("JOBSTATE_SCHEDULER",
                    "scheduler is TYPED: slurm/pbs/pjm/sge (got '" + scheduler
                            + "') - signals are never mapped for an unnamed scheduler.",
                    null);
        }
        if (mapped == null) {
            return OperationResult.success("JOBSTATE_UNKNOWN_SIGNAL",
                    "signal '" + sig + "' is NOT RECOGNIZED for " + scheduler
                            + " - the honest result is UNKNOWN, never a guess.",
                    State.UNKNOWN);
        }
        return OperationResult.success("JOBSTATE_OK",
                "signal '" + sig + "' maps to " + mapped + " for " + scheduler + ".",
                mapped);
    }

    private static State parseState(String text) {
        String value = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        try {
            return value.isEmpty() ? null : State.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
