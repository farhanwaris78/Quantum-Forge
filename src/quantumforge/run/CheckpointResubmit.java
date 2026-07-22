/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.operation.OperationResult;

/**
 * Checkpoint-aware resubmit planner.
 *
 * <p>Detects walltime/preemption stops and available restart artifacts, then
 * clones a new job/manifest recommendation without destroying original history.</p>
 */
public final class CheckpointResubmit {

    public static final class Plan {
        private final boolean restartRecommended;
        private final String restartMode;
        private final String newJobId;
        private final String reason;
        private final List<String> diagnostics;
        private final Path planFile;

        public Plan(boolean restartRecommended, String restartMode, String newJobId,
                    String reason, List<String> diagnostics, Path planFile) {
            this.restartRecommended = restartRecommended;
            this.restartMode = restartMode;
            this.newJobId = newJobId;
            this.reason = reason == null ? "" : reason;
            this.diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
            this.planFile = planFile;
        }

        public boolean isRestartRecommended() { return this.restartRecommended; }
        public String getRestartMode() { return this.restartMode; }
        public String getNewJobId() { return this.newJobId; }
        public String getReason() { return this.reason; }
        public List<String> getDiagnostics() { return this.diagnostics; }
        public Path getPlanFile() { return this.planFile; }
    }

    private CheckpointResubmit() {
        // Utility.
    }

    public static OperationResult<Plan> plan(Path projectDirectory, String prefix,
                                             JobRecord previousJob) {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            return OperationResult.failed("PROJECT_DIR_MISSING",
                    "Project directory missing for checkpoint resubmit.", null);
        }
        String prefix2 = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();
        List<String> diagnostics = new ArrayList<>();
        String reason = detectStopReason(projectDirectory, prefix2, diagnostics);
        OperationResult<RestartManager.RestartAssessment> restart =
                RestartManager.assess(projectDirectory, prefix2);
        boolean restartSafe = restart.isSuccess()
                && restart.getValue().isPresent()
                && restart.getValue().get().isRestartSafe();
        String restartMode = restartSafe ? "restart" : "from_scratch";
        if (restart.getValue().isPresent()) {
            diagnostics.addAll(restart.getValue().get().getDiagnostics());
        }

        String newJobId = UUID.randomUUID().toString().substring(0, 8);
        if (previousJob != null) {
            previousJob.transition(JobState.FAILED,
                    "checkpoint-resubmit planned; original preserved as history");
            diagnostics.add("Previous job " + previousJob.getJobId()
                    + " kept; new job id " + newJobId);
        }

        try {
            Path planFile = projectDirectory.resolve(".quantumforge.resubmit-" + newJobId + ".txt");
            StringBuilder body = new StringBuilder();
            body.append("QuantumForge checkpoint resubmit plan\n");
            body.append("created=").append(Instant.now()).append('\n');
            body.append("new_job_id=").append(newJobId).append('\n');
            body.append("restart_mode=").append(restartMode).append('\n');
            body.append("reason=").append(reason).append('\n');
            body.append("prefix=").append(prefix2).append('\n');
            if (previousJob != null) {
                body.append("previous_job_id=").append(previousJob.getJobId()).append('\n');
                body.append("previous_scheduler_job_id=")
                        .append(previousJob.getSchedulerJobId() == null ? ""
                                : previousJob.getSchedulerJobId()).append('\n');
            }
            body.append("CONTROL snippet: ").append(
                    RestartManager.namelistSnippet(
                            restart.getValue().orElse(
                                    new RestartManager.RestartAssessment(
                                            false, "from_scratch",
                                            projectDirectory.resolve(prefix2 + ".save"),
                                            List.of())))).append('\n');
            body.append("diagnostics:\n");
            for (String d : diagnostics) {
                body.append(" - ").append(d).append('\n');
            }
            body.append("NOTE: Resubmission is explicit; original job history is preserved.\n");
            AtomicFileWriter.writeUtf8(planFile, body.toString());

            Plan plan = new Plan(restartSafe, restartMode, newJobId, reason, diagnostics, planFile);
            return OperationResult.success("RESUBMIT_PLAN_OK",
                    "Prepared resubmit plan " + newJobId + " (" + restartMode + ")", plan);
        } catch (IOException ex) {
            return OperationResult.failed("RESUBMIT_PLAN_IO",
                    "Could not write resubmit plan: " + ex.getMessage(), ex);
        }
    }


    /**
     * Write an executable local bash resubmit script that applies the planned restart_mode.
     */
    public static OperationResult<Path> exportScript(Path projectDirectory, Plan plan, String[] command) {
        if (projectDirectory == null || plan == null) {
            return OperationResult.failed("RESUBMIT_ARGS", "projectDirectory/plan required.", null);
        }
        try {
            Path script = projectDirectory.resolve("resubmit-" + plan.getNewJobId() + ".sh");
            StringBuilder body = new StringBuilder();
            body.append("#!/usr/bin/env bash\n");
            body.append("set -euo pipefail\n");
            body.append("# Auto-generated by QuantumForge CheckpointResubmit\n");
            body.append("cd \"$(dirname \"$0\")\"\n");
            body.append("echo \"Resubmit plan: ").append(plan.getNewJobId()).append("\"\n");
            body.append("echo \"restart_mode=").append(plan.getRestartMode()).append("\"\n");
            body.append("echo \"reason=").append(plan.getReason().replace("\"", "'")).append("\"\n");
            if (command != null && command.length > 0) {
                body.append("set --");
                for (String token : command) {
                    if (token == null) continue;
                    body.append(' ').append(quantumforge.ssh.ShellQuotes.single(token));
                }
                body.append("\n\"$@\"\n");
            } else {
                body.append("echo \"No command provided; edit this script to launch QE with restart_mode='");
                body.append(plan.getRestartMode()).append("'\"\n");
                body.append("exit 2\n");
            }
            AtomicFileWriter.writeUtf8(script, body.toString());
            try {
                script.toFile().setExecutable(true);
            } catch (SecurityException ignored) {
            }
            return OperationResult.success("RESUBMIT_SCRIPT_OK",
                    "Wrote resubmit script " + script.getFileName(), script);
        } catch (Exception ex) {
            return OperationResult.failed("RESUBMIT_SCRIPT_IO",
                    "Could not write resubmit script: " + ex.getMessage(), ex);
        }
    }

    static String detectStopReason(Path projectDirectory, String prefix, List<String> diagnostics) {
        // Log attribution must be scoped: only files belonging to THIS prefix (prefix.log,
        // prefix.log.*, prefix.err*) plus scheduler outputs (slurm-*.out, the only place a
        // walltime/preemption signature can live) may testify about this job's stop reason.
        // Scanning every *.{log,err,out} in the directory would fold a NEIGHBOUR job's
        // outcome (e.g. another deck's "convergence not achieved") into this job's restart
        // decision - an unknown stop reason is honest, a misattributed one is not.
        StringBuilder combined = new StringBuilder();
        try (var stream = Files.list(projectDirectory)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
                boolean prefixOwned = name.startsWith(lowerPrefix + ".log")
                        || name.startsWith(lowerPrefix + ".err");
                boolean schedulerOwned = name.startsWith("slurm-") && name.endsWith(".out");
                if (!(prefixOwned || schedulerOwned)) {
                    continue;
                }
                if (Files.size(file) > 2_000_000L) {
                    diagnostics.add("Skipped oversized log (>2 MB): " + name + " - its stop "
                            + "signature is unscanned (bounded memory).");
                    continue;
                }
                combined.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
                diagnostics.add("Scanned stop-signature source: " + name);
            }
        } catch (IOException ex) {
            diagnostics.add("Could not scan logs: " + ex.getMessage());
        }
        String lower = combined.toString().toLowerCase(Locale.ROOT);
        if (lower.contains("walltime") || lower.contains("time limit")
                || lower.contains("cancelled due to time")
                || lower.contains("dne limit")
                || lower.contains("job exceeded time limit")) {
            diagnostics.add("Detected walltime/time-limit stop signature in logs.");
            return "walltime";
        }
        if (lower.contains("preempt") || lower.contains("requeued")
                || lower.contains("node_fail") || lower.contains("epilog")) {
            diagnostics.add("Detected preemption/node-fail signature in logs.");
            return "preempted";
        }
        if (lower.contains("convergence not achieved")) {
            diagnostics.add("Detected electronic non-convergence.");
            return "scf_not_converged";
        }
        if (Files.isDirectory(projectDirectory.resolve(prefix + ".save"))) {
            diagnostics.add("Found .save directory without explicit walltime marker.");
            return "checkpoint_present";
        }
        diagnostics.add("No explicit stop signature found.");
        return "unknown";
    }
}
