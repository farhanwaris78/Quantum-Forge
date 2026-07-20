/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #93 (draft slice): typed, fail-closed SLURM submit-script drafting.
 * The CORE DIRECTIVES are OWNED - every one is validated against an explicit
 * grammar/range before it can appear in the script; nothing about the
 * directives is free-form concatenation. The single PAYLOAD LINE is the only
 * verbatim analyst content, clearly commented as such in the render, and even
 * it is guarded: it may not smuggle directives (lines starting with
 * {@code #SBATCH} refuse) and it may not be multi-line (one reviewed line per
 * draft; multi-line payload blocks are stated as remaining depth).
 *
 * <p>Owned directive grammars:</p>
 * <ul>
 *   <li>{@code --job-name}: required, {@code [A-Za-z][A-Za-z0-9._-]{0,63}}
 *       (SLURM_NAME);</li>
 *   <li>{@code --partition}: OPTIONAL - blank omits the directive AND prints
 *       an honest comment; when given per-account clusters often reject
 *       unknown partitions, so its grammar is
 *       {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}} (SLURM_PARTITION);</li>
 *   <li>{@code --nodes} 1..1024 (SLURM_NODES), {@code --ntasks} 1..65536
 *       (SLURM_NTASKS);</li>
 *   <li>{@code --time} strict HH:MM:SS with MM,SS in [0,59] and a 7-day cap,
 *       rendered zero-padded (SLURM_TIME) - "1:30:00" drafts as "01:30:00";</li>
 *   <li>modules: comma-separated, each token
 *       {@code [A-Za-z0-9._/+:-]{1,128}} - a "module load" line per token;
 *       a blank list prints the honest "no modules declared" comment rather
 *       than assuming a site environment (SLURM_MODULE).</li>
 * </ul>
 *
 * <p>Nothing is submitted from this build - the script renders through the
 * draft channel and saves only via the explicit save action. Site profiles
 * (#94), job-ID parse-back and the state machine (#95) remain.</p>
 */
public final class SlurmScriptBuilder {

    /** 7 days in hours - the owned walltime cap for one drafted job. */
    public static final int MAX_HOURS = 168;

    private SlurmScriptBuilder() {
    }

    /** One validated draft. */
    public static final class SlurmDraft {
        private final String jobName;
        private final String partition;      // "" when omitted on purpose
        private final int nodes;
        private final int ntasks;
        private final String walltime;       // normalized HH:MM:SS
        private final List<String> modules;  // possibly empty - rendered honestly
        private final String command;

        SlurmDraft(String jobName, String partition, int nodes, int ntasks,
                String walltime, List<String> modules, String command) {
            this.jobName = jobName;
            this.partition = partition;
            this.nodes = nodes;
            this.ntasks = ntasks;
            this.walltime = walltime;
            this.modules = modules;
            this.command = command;
        }

        public String getJobName() { return this.jobName; }
        public String getPartition() { return this.partition; }
        public int getNodes() { return this.nodes; }
        public int getNtasks() { return this.ntasks; }
        public String getWalltime() { return this.walltime; }
        public List<String> getModules() { return this.modules; }
        public String getCommand() { return this.command; }

        /** The full script text - directives owned, payload commented. */
        public String render() {
            StringBuilder script = new StringBuilder();
            script.append("#!/bin/bash\n");
            script.append("#\n# QuantumForge SLURM draft (Roadmap #93, draft slice) - REVIEW before\n");
            script.append("# submission; nothing was or will be submitted by QuantumForge itself.\n");
            script.append("#SBATCH --job-name=").append(this.jobName).append('\n');
            script.append("#SBATCH --nodes=").append(this.nodes).append('\n');
            script.append("#SBATCH --ntasks=").append(this.ntasks).append('\n');
            script.append("#SBATCH --time=").append(this.walltime).append('\n');
            if (this.partition.isEmpty()) {
                script.append("# --partition intentionally omitted (cluster default applies;\n");
                script.append("#  set one explicitly at the prompt to pin it).\n");
            } else {
                script.append("#SBATCH --partition=").append(this.partition).append('\n');
            }
            if (this.modules.isEmpty()) {
                script.append("\n# no modules declared - the site module environment is NOT\n");
                script.append("# assumed; add modules at the prompt if the cluster needs them.\n");
            } else {
                script.append('\n');
                for (String module : this.modules) {
                    script.append("module load ").append(module).append('\n');
                }
            }
            script.append("\n# payload: exactly the analyst-reviewed line below - verbatim, not\n");
            script.append("# constructed by the builder; the builder guards but does not run it.\n");
            script.append(this.command).append('\n');
            return script.toString();
        }
    }

    /** Validates one draft. Codes: SLURM_NAME/PARTITION/NODES/NTASKS/TIME/MODULE/COMMAND. */
    public static OperationResult<SlurmDraft> validate(String jobName, String partition,
            int nodes, int ntasks, String walltime, String modulesCsv, String command) {
        String name = jobName == null ? "" : jobName.trim();
        if (!name.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) {
            return OperationResult.failed("SLURM_NAME",
                    "--job-name must start with a letter and use only "
                            + "[A-Za-z0-9._-], up to 64 chars (got '" + name + "').",
                    null);
        }
        String part = partition == null ? "" : partition.trim();
        if (!part.isEmpty() && !part.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return OperationResult.failed("SLURM_PARTITION",
                    "--partition must use only [A-Za-z0-9._-], up to 64 chars (got '"
                            + part + "'); blank omits the directive honestly.",
                    null);
        }
        if (nodes < 1 || nodes > 1024) {
            return OperationResult.failed("SLURM_NODES",
                    "--nodes must be 1..1024 per drafted job (got " + nodes
                            + "); larger campaigns wait for the site-profile (#94) limits.",
                    null);
        }
        if (ntasks < 1 || ntasks > 65536) {
            return OperationResult.failed("SLURM_NTASKS",
                    "--ntasks must be 1..65536 (got " + ntasks + ").", null);
        }
        OperationResult<String> time = normalizeWalltime(walltime);
        if (!time.isSuccess()) {
            return OperationResult.failed(time.getCode(), time.getMessage(), null);
        }
        List<String> modules = new ArrayList<>();
        String csv = modulesCsv == null ? "" : modulesCsv.trim();
        if (!csv.isEmpty()) {
            for (String token : csv.split(",", -1)) {
                String module = token.trim();
                if (!module.matches("[A-Za-z0-9][A-Za-z0-9._/+:-]{0,127}")) {
                    return OperationResult.failed("SLURM_MODULE",
                            "module token '" + module + "' is outside the owned grammar "
                                    + "[A-Za-z0-9._/+:-] (1..128 chars) - no whitespace or "
                                    + "shell characters ever reach a module line.",
                            null);
                }
                modules.add(module);
            }
        }
        String payload = command == null ? "" : command.trim();
        if (payload.isEmpty()) {
            return OperationResult.failed("SLURM_COMMAND",
                    "A payload command line is required - drafting an empty job is "
                            + "never useful.",
                    null);
        }
        if (payload.contains("\n") || payload.contains("\r")) {
            return OperationResult.failed("SLURM_COMMAND",
                    "The payload is ONE reviewed line per draft; multi-line blocks are "
                            + "stated as remaining depth, not silently joined.",
                    null);
        }
        if (payload.startsWith("#SBATCH")) {
            return OperationResult.failed("SLURM_COMMAND",
                    "The payload must not smuggle directives (a line starting with "
                            + "#SBATCH) - directives are owned above.",
                    null);
        }
        return OperationResult.success("SLURM_OK", "Script draft validated.",
                new SlurmDraft(name, part, nodes, ntasks, time.getValue().orElseThrow(),
                        List.copyOf(modules), payload));
    }

    /** Strict HH:MM:SS normalization; zero-pads; refuses junk. */
    private static OperationResult<String> normalizeWalltime(String walltime) {
        String raw = walltime == null ? "" : walltime.trim();
        String[] fields = raw.split(":", -1);
        if (fields.length != 3) {
            return OperationResult.failed("SLURM_TIME",
                    "--time must be strict HH:MM:SS (got '" + raw + "'); days like "
                            + "'2-00:00:00' are refused until site profiles declare them.",
                    null);
        }
        int hours;
        int minutes;
        int seconds;
        try {
            hours = Integer.parseInt(fields[0]);
            minutes = Integer.parseInt(fields[1]);
            seconds = Integer.parseInt(fields[2]);
        } catch (NumberFormatException ex) {
            return OperationResult.failed("SLURM_TIME",
                    "--time fields must be digits only (got '" + raw + "').", null);
        }
        if (hours < 0 || hours > MAX_HOURS) {
            return OperationResult.failed("SLURM_TIME",
                    "--time hours must be 0.." + MAX_HOURS + " (7-day cap per drafted job; "
                            + "got " + hours + ").",
                    null);
        }
        if (minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
            return OperationResult.failed("SLURM_TIME",
                    "--time minutes/seconds must be 00..59 (got '" + raw + "').", null);
        }
        if (hours == 0 && minutes == 0 && seconds == 0) {
            return OperationResult.failed("SLURM_TIME",
                    "--time of 00:00:00 describes no job at all - refused.", null);
        }
        return OperationResult.success("SLURM_OK", "walltime",
                String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds));
    }
}
