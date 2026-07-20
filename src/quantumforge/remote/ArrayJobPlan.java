/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #100 (plan slice): a typed scheduler ARRAY-JOB plan - a parameter
 * sweep with an explicit per-task mapping, so hundreds of convergence points
 * can be submitted and tracked consistently. Nothing is submitted or
 * generated per-task from this build; the plan pins the mapping arithmetic
 * and its honesty rules:
 *
 * <ul>
 *   <li>base name: {@code [A-Za-z][A-Za-z0-9._-]{0,63}} (ARRAY_NAME) - it
 *       seeds the per-task DIRECTORY names, so anything that could fragment
 *       a path refuses;</li>
 *   <li>sweep values: 1..1000 plain finite decimal tokens, echoed VERBATIM
 *       (validated as numbers but never re-typed/re-rounded - float mangling
 *       of a swept parameter would silently change what runs)
 *       (ARRAY_VALUES); duplicates refuse (ARRAY_DUPLICATE) - two tasks
 *       computing the same point is exactly the silent-redundancy an array
 *       manifest exists to prevent;</li>
 *   <li>the mapping is pinned 1-BASED, the way SLURM {@code --array=1-N}
 *       counts: task i of N sweeps value_i into directory
 *       {@code <base>/task_<i>} (ARRAY_MAP invariants are pinned by tests);</li>
 *   <li>the rendered {@code #SBATCH --array=1-N} line is a REVIEW line tied
 *       to the SLURM draft family - other schedulers' array syntax is stated
 *       as remaining depth, not guessed;</li>
 *   <li>per-task input templating (substituting value_i into decks) is NOT
 *       hallucinated: the plan states exactly where the value lands in the
 *       directory mapping and leaves deck templating to the #100 runtime
 *       slice;</li>
 *   <li>sister surface (do not mix): {@code hpc.ArraySweepPlanner} is the
 *       numeric-generated JSONL manifest under the same roadmap item. Its
 *       directory mapping is {@code <base>-NNN} (this plan uses
 *       {@code <base>/task_<i>}), its name grammar allows leading digits at
 *       32 chars (this plan requires a leading letter at 64), and its count
 *       bound is 2..50 (this plan: 1..1000). Pick ONE per study - the
 *       ARRAY_JOB_AUDIT kind renders both mappings side by side.</li>
 * </ul>
 */
public final class ArrayJobPlan {

    /** Task-count bound for one array. */
    public static final int MAX_TASKS = 1000;

    private ArrayJobPlan() {
    }

    /** One validated plan. */
    public static final class Plan {
        private final String baseName;
        private final List<String> values;   // verbatim tokens, 1-based mapping
        private final boolean slurmArrayLine;

        Plan(String baseName, List<String> values, boolean slurmArrayLine) {
            this.baseName = baseName;
            this.values = values;
            this.slurmArrayLine = slurmArrayLine;
        }

        public String getBaseName() { return this.baseName; }
        public List<String> getValues() { return this.values; }
        public int getTaskCount() { return this.values.size(); }
        public boolean hasSlurmArrayLine() { return this.slurmArrayLine; }

        /** 1-based directory for task i: <base>/task_<i> (i must be 1..N). */
        public String taskDirectory(int taskIndex) {
            if (taskIndex < 1 || taskIndex > this.values.size()) {
                throw new IllegalArgumentException("taskIndex " + taskIndex
                        + " outside 1.." + this.values.size()
                        + " - the array mapping is 1-BASED like --array=1-N");
            }
            return this.baseName + "/task_" + taskIndex;
        }

        /** The swept value for task i (verbatim token). */
        public String taskValue(int taskIndex) {
            if (taskIndex < 1 || taskIndex > this.values.size()) {
                throw new IllegalArgumentException("taskIndex " + taskIndex
                        + " outside 1.." + this.values.size());
            }
            return this.values.get(taskIndex - 1);
        }

        /** The pinned review block. */
        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# QuantumForge array-job REVIEW plan (Roadmap #100, plan slice)\n");
            text.append("# NOTHING is submitted or templated from this build.\n");
            text.append("base_name        = ").append(this.baseName).append('\n');
            text.append("task_count       = ").append(this.values.size()).append('\n');
            text.append("mapping          = 1-BASED  (task i -> value_i -> dir "
                    + this.baseName + "/task_<i>, counting like --array=1-N)\n");
            if (this.slurmArrayLine) {
                text.append("slurm_array_line = #SBATCH --array=1-")
                        .append(this.values.size())
                        .append("   # REVIEW line for the SLURM draft family\n");
            } else {
                text.append("slurm_array_line = (not incorporated - SLURM directive "
                        + "drafts live in the SLURM_SCRIPT_DRAFT)\n");
            }
            text.append("other_schedulers = pbs/pjm/sge array syntax is #100 runtime "
                    + "depth - NOT guessed here\n");
            text.append("templating       = value_i lands in the per-task DIRECTORY "
                    + "mapping above;\n");
            text.append("                   substituting it into decks is runtime depth, "
                    + "never silently composed\n");
            text.append("values:\n");
            for (int i = 0; i < this.values.size(); i++) {
                text.append(String.format(Locale.ROOT, "  task %d = %s   (dir %s)%n",
                        i + 1, this.values.get(i), this.baseName + "/task_" + (i + 1)));
            }
            return text.toString();
        }
    }

    /** Validates one plan. Codes: ARRAY_NAME/ARRAY_VALUES/ARRAY_DUPLICATE. */
    public static OperationResult<Plan> validate(String baseName, String valuesCsv,
            boolean slurmArrayLine) {
        String base = baseName == null ? "" : baseName.trim();
        if (!base.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            return OperationResult.failed("ARRAY_NAME",
                    "base name must start with a letter and use only [A-Za-z0-9._-], "
                            + "up to 64 chars (got '" + base + "') - it seeds per-task "
                            + "DIRECTORY names.",
                    null);
        }
        String text = valuesCsv == null ? "" : valuesCsv.trim();
        if (text.isEmpty()) {
            return OperationResult.failed("ARRAY_VALUES",
                    "At least one sweep value is required - an empty array maps zero "
                            + "tasks.",
                    null);
        }
        List<String> values = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<Double> seenNumeric = new LinkedHashSet<>();
        for (String token : text.split(",", -1)) {
            String value = token.trim();
            if (value.isEmpty() || value.length() > 40) {
                return OperationResult.failed("ARRAY_VALUES",
                        "sweep values must be non-blank tokens up to 40 chars (got '"
                                + value + "').",
                        null);
            }
            double parsed;
            try {
                parsed = Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                return OperationResult.failed("ARRAY_VALUES",
                        "sweep value '" + value + "' is not a plain number; non-numeric "
                                + "templating is #100 runtime depth, not silently "
                                + "accepted.",
                        null);
            }
            if (!Double.isFinite(parsed)) {
                return OperationResult.failed("ARRAY_VALUES",
                        "sweep value '" + value + "' is not finite.", null);
            }
            if (!seen.add(value)) {
                return OperationResult.failed("ARRAY_DUPLICATE",
                        "sweep value '" + value + "' appears twice - two tasks computing "
                                + "the same point is exactly the silent-redundancy this "
                                + "manifest exists to prevent.",
                        null);
            }
            if (!seenNumeric.add(Double.valueOf(parsed))) {
                return OperationResult.failed("ARRAY_DUPLICATE",
                        "sweep value '" + value + "' equals an earlier token NUMERICALLY "
                                + "(e.g. '30' vs '30.0') - verbatim echo stays, but "
                                + "duplicate-detection is numeric; two tasks computing "
                                + "the same point refuse.",
                        null);
            }
            values.add(value);  // VERBATIM - never re-typed or re-rounded
        }
        if (values.size() > MAX_TASKS) {
            return OperationResult.failed("ARRAY_VALUES",
                    "task count " + values.size() + " exceeds " + MAX_TASKS + " per "
                            + "array - campaign-scale sweeps need the workflow export "
                            + "slice, not one giant array.",
                    null);
        }
        return OperationResult.success("ARRAY_OK", "Array plan validated.",
                new Plan(base, List.copyOf(values), slurmArrayLine));
    }
}
