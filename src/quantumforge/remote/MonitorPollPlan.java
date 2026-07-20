/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #96 (plan slice): a bounded remote-monitoring POLL POLICY with its
 * arithmetic fully spelled out BEFORE any thread exists. No thread starts
 * from this build - the plan is data; the runtime loop is the #96 depth. The
 * honesty rules that keep monitoring from becoming a request storm:
 *
 * <ul>
 *   <li>bounded everywhere: initial interval 1..3600 s (MONITOR_INTERVAL),
 *       capped interval &gt;= initial and &lt;= 86400 s (MONITOR_MAX),
 *       backoff factor 1.0..4.0 finite - a factor of exactly 1.0 is an
 *       honest CONSTANT-poll declaration, never dressed up as backoff
 *       (MONITOR_FACTOR) - and poll count 1..10000 (MONITOR_POLLS);</li>
 *   <li>the schedule preview is REAL arithmetic: interval_k =
 *       min(max, initial * factor^(k-1)), with the cap visibly engaged on the
 *       pinned rows and the cumulative horizon summed exactly;</li>
 *   <li>single-flight: at most one poll in flight - an overlap is a plan
 *       violation, stated as a FORBIDDEN line;</li>
 *   <li>jitter is NOT implemented in this slice and the plan says so -
 *       claiming anti-thundering-herd jitter that does not exist would be a
 *       lie;</li>
 *   <li>OFFLINE vs JOB-STATE distinction is declared: repeated transport
 *       failure means the CHANNEL is unknown, never that the job finished -
 *       reconnect resumes the SAME plan, polling never silently restarts
 *       counters.</li>
 * </ul>
 */
public final class MonitorPollPlan {

    /** Rows of the schedule preview rendered before the "+N more" counter. */
    public static final int PREVIEW_ROWS = 12;

    private MonitorPollPlan() {
    }

    /** One validated poll policy. */
    public static final class PollPlan {
        private final double initialSec;
        private final double maxSec;
        private final double factor;
        private final int maxPolls;
        private final List<double[]> preview;   // each: k, intervalSec, cumulativeSec
        private final double horizonSec;        // exact full-schedule cumulative sum
        private final int cappedPolls;          // how many polls ride at the cap

        PollPlan(double initialSec, double maxSec, double factor, int maxPolls,
                List<double[]> preview, double horizonSec, int cappedPolls) {
            this.initialSec = initialSec;
            this.maxSec = maxSec;
            this.factor = factor;
            this.maxPolls = maxPolls;
            this.preview = preview;
            this.horizonSec = horizonSec;
            this.cappedPolls = cappedPolls;
        }

        public double getInitialSec() { return this.initialSec; }
        public double getMaxSec() { return this.maxSec; }
        public double getFactor() { return this.factor; }
        public int getMaxPolls() { return this.maxPolls; }
        public List<double[]> getPreview() { return this.preview; }
        public double getHorizonSec() { return this.horizonSec; }
        public int getCappedPolls() { return this.cappedPolls; }

        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# QuantumForge remote-monitoring POLL PLAN (Roadmap #96, plan "
                    + "slice)\n");
            text.append("# No thread starts from this build - this file is the policy a "
                    + "runtime loop MUST honor.\n");
            text.append(String.format(Locale.ROOT, "initial_interval_s = %.3f%n",
                    this.initialSec));
            text.append(String.format(Locale.ROOT, "max_interval_s     = %.3f%n",
                    this.maxSec));
            text.append(String.format(Locale.ROOT, "backoff_factor     = %s%n",
                    this.factor == 1.0 ? "1.000  (CONSTANT polling - declared plainly, "
                            + "not dressed up as backoff)"
                            : String.format(Locale.ROOT, "%.3f", this.factor)));
            text.append(String.format(Locale.ROOT, "max_polls          = %d%n",
                    this.maxPolls));
            text.append(String.format(Locale.ROOT, "capped_polls       = %d%n",
                    this.cappedPolls));
            text.append(String.format(Locale.ROOT, "horizon_s          = %.3f%n",
                    this.horizonSec));
            text.append("single_flight      = at most one poll in flight - overlap is a "
                    + "plan VIOLATION\n");
            text.append("jitter             = NOT IMPLEMENTED in this slice (stated "
                    + "honestly - no fake anti-thundering-herd claim)\n");
            text.append("offline_semantics  = repeated transport failure means CHANNEL "
                    + "unknown, never\n");
            text.append("                     'job finished'; reconnect RESUMES the same "
                    + "plan/counters\n");
            return text.toString();
        }
    }

    /** Validates one policy and computes its schedule. Codes: MONITOR_*. */
    public static OperationResult<PollPlan> validate(double initialSec, double maxSec,
            double factor, int maxPolls) {
        if (!Double.isFinite(initialSec) || initialSec < 1.0 || initialSec > 3600.0) {
            return OperationResult.failed("MONITOR_INTERVAL",
                    "initial interval must be finite 1..3600 s (got " + initialSec
                            + ") - sub-second remote polling is a request storm by "
                            + "definition.",
                    null);
        }
        if (!Double.isFinite(maxSec) || maxSec < 1.0 || maxSec > 86400.0) {
            return OperationResult.failed("MONITOR_MAX",
                    "max interval must be finite 1..86400 s (got " + maxSec + ").",
                    null);
        }
        if (maxSec < initialSec) {
            return OperationResult.failed("MONITOR_MAX",
                    "max interval (" + maxSec + " s) is below the initial interval ("
                            + initialSec + " s) - a cap under its own start is a "
                            + "specification error, not something to silently floor.",
                    null);
        }
        if (!Double.isFinite(factor) || factor < 1.0 || factor > 4.0) {
            return OperationResult.failed("MONITOR_FACTOR",
                    "backoff factor must be finite 1.0..4.0 (got " + factor + "); "
                            + "exactly 1.0 is permitted as an HONEST constant-poll "
                            + "declaration.",
                    null);
        }
        if (maxPolls < 1 || maxPolls > 10000) {
            return OperationResult.failed("MONITOR_POLLS",
                    "max polls must be 1..10000 (got " + maxPolls + ") - unbounded "
                            + "polling is refused by construction.",
                    null);
        }
        List<double[]> preview = new ArrayList<>();
        double cumulative = 0.0;
        double current = initialSec;
        int capped = 0;
        for (int k = 1; k <= maxPolls; k++) {
            cumulative += current;
            if (current >= maxSec) {
                capped += 1;
            }
            if (k <= PREVIEW_ROWS) {
                preview.add(new double[] {k, current, cumulative});
            }
            current = Math.min(maxSec, current * factor);
        }
        return OperationResult.success("MONITOR_OK", "Poll plan validated.",
                new PollPlan(initialSec, maxSec, factor, maxPolls, List.copyOf(preview),
                        cumulative, capped));
    }
}
