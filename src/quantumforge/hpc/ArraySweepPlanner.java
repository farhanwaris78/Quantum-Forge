/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Scheduler-array sweep manifest (Roadmap #100 data layer): turns one keyword
 * sweep (start/step/count) into a task list (JSON Lines) plus a SLURM array
 * script PREVIEW. The preview is deliberately not runnable as delivered - a
 * REQUIRED-EDIT guard (`exit 2`) forces the user to review and remove it, so a
 * generated script can never be submitted blind. Values are produced by exact
 * repeated addition and consecutive-duplicate detection, and every validation
 * failure carries a code.
 */
public final class ArraySweepPlanner {

    public static final int MIN_TASKS = 2;
    public static final int MAX_TASKS = 50;
    private static final Pattern KEYWORD = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9_]*(\\([0-9]+\\))?$");
    private static final Pattern JOB_BASE = Pattern.compile("^[A-Za-z0-9._-]{1,32}$");

    /** One planned sweep: keyword, exact task values, and the job base name. */
    public static final class SweepPlan {
        private final String keyword;
        private final List<Double> values;
        private final String jobBaseName;

        SweepPlan(String keyword, List<Double> values, String jobBaseName) {
            this.keyword = keyword;
            this.values = List.copyOf(values);
            this.jobBaseName = jobBaseName;
        }

        public String getKeyword() { return this.keyword; }
        public List<Double> getValues() { return this.values; }
        public String getJobBaseName() { return this.jobBaseName; }

        /** Task directory name for a 1-based task index: {@code base-001} etc. */
        public String taskDirectory(int taskIndex) {
            return String.format(Locale.ROOT, "%s-%03d", this.jobBaseName, taskIndex);
        }

        /**
         * JSON Lines manifest: one task per line with the task index, the exact
         * value and its directory. Keyword is regex-constrained so no escaping
         * beyond the standard quote is needed.
         */
        public String toJsonLines() {
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < this.values.size(); i++) {
                // Double.toString is exact and lossless; no precision model is invented.
                json.append(String.format(Locale.ROOT,
                        "{\"task_index\":%d,\"keyword\":\"%s\",\"value\":%s,"
                                + "\"directory\":\"%s\"}",
                        i + 1, this.keyword, Double.toString(this.values.get(i)),
                        taskDirectory(i + 1)));
                json.append('\n');
            }
            return json.toString();
        }

        /**
         * SLURM array script PREVIEW - never runnable as delivered because of
         * the REQUIRED-EDIT `exit 2` guard; every edit point is a comment.
         */
        public String sbatchPreview() {
            StringBuilder script = new StringBuilder();
            script.append("#!/bin/bash\n");
            script.append("# REQUIRED-EDIT: review this generated preview; it refuses to\n");
            script.append("# REQUIRED-EDIT: run until every REQUIRED-EDIT line is removed.\n");
            script.append("exit 2  # REQUIRED-EDIT guard: preview must not run as-is\n");
            script.append(String.format(Locale.ROOT, "#SBATCH --array=1-%d%n",
                    this.values.size()));
            script.append("#SBATCH --job-name=").append(this.jobBaseName).append('\n');
            script.append("# REQUIRED-EDIT: partition, account, walltime and module loads\n");
            script.append("# REQUIRED-EDIT: belong to your site profile (Roadmap #94); set them here.\n");
            script.append("set -euo pipefail\n");
            script.append("TASK=$(sed -n \"${SLURM_ARRAY_TASK_ID}p\" tasks.jsonl)\n");
            script.append("DIR=$(printf '%s' \"$TASK\" | sed -n 's/.*\"directory\":\"\\([^\"]*\\)\".*/\\1/p')\n");
            script.append("VALUE=$(printf '%s' \"$TASK\" | sed -n 's/.*\"value\":\\([0-9.eE+-]*\\).*/\\1/p')\n");
            script.append("mkdir -p \"$DIR\" && cd \"$DIR\"\n");
            script.append(String.format(Locale.ROOT,
                    "# REQUIRED-EDIT: render your input with %s = $VALUE here (QuantumForge\n",
                    this.keyword));
            script.append("# REQUIRED-EDIT: rewrites only this keyword; every other field is yours)\n");
            script.append("# sbatch of the actual pw.x call belongs here after review.\n");
            return script.toString();
        }
    }

    private ArraySweepPlanner() { }

    /**
     * Validates the sweep and builds the plan. Codes: SWEEP_KEYWORD,
     * SWEEP_VALUE, SWEEP_COUNT, SWEEP_NAME.
     */
    public static OperationResult<SweepPlan> plan(String keyword, double start,
            double step, int count, String jobBaseName) {
        String key = keyword == null ? "" : keyword.trim();
        if (!KEYWORD.matcher(key).matches()) {
            return OperationResult.failed("SWEEP_KEYWORD",
                    "The sweep keyword must look like a QE keyword (letters, digits, "
                            + "underscore, optional (n) index); got \"" + key + "\".", null);
        }
        String base = jobBaseName == null ? "" : jobBaseName.trim();
        if (!JOB_BASE.matcher(base).matches()) {
            return OperationResult.failed("SWEEP_NAME",
                    "The job base name must be 1-32 characters of [A-Za-z0-9._-]; got \""
                            + base + "\".", null);
        }
        if (!Double.isFinite(start) || !Double.isFinite(step) || step == 0.0) {
            return OperationResult.failed("SWEEP_VALUE",
                    "The start value must be finite and the step finite and non-zero.",
                    null);
        }
        if (count < MIN_TASKS || count > MAX_TASKS) {
            return OperationResult.failed("SWEEP_COUNT",
                    "A scheduler array needs " + MIN_TASKS + ".." + MAX_TASKS
                            + " tasks here; got " + count + ".", null);
        }
        List<Double> values = new ArrayList<>();
        double value = start;
        for (int i = 0; i < count; i++) {
            if (!Double.isFinite(value)) {
                return OperationResult.failed("SWEEP_VALUE",
                        "Task " + (i + 1) + " evaluated to a non-finite value; the sweep "
                                + "overflows.", null);
            }
            if (i > 0 && value == values.get(values.size() - 1)) {
                return OperationResult.failed("SWEEP_VALUE",
                        "The step is too small to change the value between tasks " + i
                                + " and " + (i + 1) + " (both are " + value
                                + "); increase the step - duplicate tasks would waste "
                                + "allocation.", null);
            }
            values.add(value);
            value = start + (i + 1) * step;
        }
        return OperationResult.success("SWEEP_OK",
                "Planned " + count + " sweep task(s) for " + key + ".",
                new SweepPlan(key, values, base));
    }
}
