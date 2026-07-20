/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #99 (advice slice): checkpoint-aware resubmission ADVICE that stays
 * strictly read-only. The explicit-write siblings ({@link CheckpointResubmit#plan}
 * and {@code exportScript}) remain available to the caller that WANTS files; this
 * advisor never writes a plan file, never writes a script, and never mutates a
 * job record - it only composes two already-tested truths:
 *
 * <ul>
 *   <li>{@link RestartManager#assess} - are the restart artifacts complete enough
 *       that restarting is even SAFE (never invented: missing data-file or
 *       charge-density means unsafe);</li>
 *   <li>{@link CheckpointResubmit#detectStopReason} - WHY the run appears to have
 *       stopped, from a bounded log-signature scan (walltime / preemption /
 *       SCF non-convergence / checkpoint-present / unknown), with the honest
 *       caveat that an unscanned or oversized log yields "unknown".</li>
 * </ul>
 *
 * <p>The recommendation is a TYPED truth table, not vibes:</p>
 * <ul>
 *   <li>RESTART_AND_CONTINUE - artifacts safe AND the stop signature is a
 *       resource/preemption/checkpoint class where continuing is the design;</li>
 *   <li>REVIEW_BEFORE_RESUBMIT - either the stop was electronic
 *       (scf_not_converged: a blind resubmission re-runs the same failure;
 *       mixing/smearing/electron_maxstep must change first) or the reason is
 *       UNKNOWN (never resubmit on a guess);</li>
 *   <li>FROM_SCRATCH - restart artifacts are unsafe (RestartManager said so);
 *       no signature changes that;</li>
 *   <li>INSUFFICIENT_EVIDENCE - artifacts unsafe AND no stop signature found.
 *       Nothing about this run is established - do not move yet.</li>
 * </ul>
 *
 * <p>What this slice deliberately does NOT do (stated, not hidden): it does not
 * parse slurm/step accounting, it does not resubmit anything, and it does not
 * vouch that a "restart" succeeds - only that artifacts are present and the
 * stop class matches. Codes: RESUBMIT_DIR (missing project dir) / RESUBMIT_OK.</p>
 */
public final class ResubmitAdvice {

    /** Typed resubmission recommendation - the caller still decides. */
    public enum Recommendation {
        RESTART_AND_CONTINUE, REVIEW_BEFORE_RESUBMIT, FROM_SCRATCH, INSUFFICIENT_EVIDENCE
    }

    /** Immutable composed advice. */
    public static final class Advice {
        private final String stopReason;          // typed signature string from CheckpointResubmit
        private final boolean restartSafe;
        private final String restartMode;          // from RestartManager (restart|from_scratch)
        private final Path saveDirectory;
        private final Recommendation recommendation;
        private final String rationale;            // why this recommendation, in one line
        private final List<String> diagnostics;

        Advice(String stopReason, boolean restartSafe, String restartMode,
               Path saveDirectory, Recommendation recommendation, String rationale,
               List<String> diagnostics) {
            this.stopReason = stopReason;
            this.restartSafe = restartSafe;
            this.restartMode = restartMode;
            this.saveDirectory = saveDirectory;
            this.recommendation = recommendation;
            this.rationale = rationale;
            this.diagnostics = List.copyOf(diagnostics);
        }

        public String getStopReason() { return this.stopReason; }
        public boolean isRestartSafe() { return this.restartSafe; }
        public String getRestartMode() { return this.restartMode; }
        public Path getSaveDirectory() { return this.saveDirectory; }
        public Recommendation getRecommendation() { return this.recommendation; }
        public String getRationale() { return this.rationale; }
        public List<String> getDiagnostics() { return this.diagnostics; }
        /** CONTROL card line for a resubmission input - review before use. */
        public String controlSnippet() {
            return "restart_mode = '" + this.restartMode + "'";
        }
    }

    private ResubmitAdvice() {
        // Utility.
    }

    /**
     * Compose advice for {@code projectDirectory} and {@code prefix}. Read-only:
     * nothing is created, moved, or overwritten.
     */
    public static OperationResult<Advice> advise(Path projectDirectory, String prefix) {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            return OperationResult.failed("RESUBMIT_DIR",
                    "Project directory missing - there is nothing to advise about.", null);
        }
        String prefix2 = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();

        OperationResult<RestartManager.RestartAssessment> restart =
                RestartManager.assess(projectDirectory, prefix2);
        if (!restart.isSuccess() || restart.getValue().isEmpty()) {
            return OperationResult.failed("RESUBMIT_DIR",
                    "Restart artifact assessment failed: " + restart.getMessage(),
                    null);
        }
        RestartManager.RestartAssessment assessment = restart.getValue().get();

        List<String> diagnostics = new ArrayList<>(assessment.getDiagnostics());
        String stopReason = CheckpointResubmit.detectStopReason(
                projectDirectory, prefix2, diagnostics);

        boolean restartSafe = assessment.isRestartSafe();
        Recommendation recommendation;
        String rationale;
        if (!restartSafe) {
            if ("scf_not_converged".equals(stopReason)) {
                recommendation = Recommendation.REVIEW_BEFORE_RESUBMIT;
                rationale = "The stop was ELECTRONIC (convergence not achieved), and the "
                        + "restart artifacts are incomplete - a blind resubmission re-runs "
                        + "the same failure. Change the SCF setup (mixing_beta, "
                        + "electron_maxstep, smearing) before any resubmission.";
            } else if ("unknown".equals(stopReason)) {
                recommendation = Recommendation.INSUFFICIENT_EVIDENCE;
                rationale = "No stop signature was found AND the restart artifacts are "
                        + "incomplete - nothing about this run is established. Inspect "
                        + "the logs manually before deciding anything.";
            } else {
                recommendation = Recommendation.FROM_SCRATCH;
                rationale = "RestartManager judged the artifacts INCOMPLETE (missing "
                        + "data-file and/or charge-density) - only from_scratch is "
                        + "honest; no stop signature overrides broken artifacts.";
            }
        } else {
            switch (stopReason) {
            case "walltime":
            case "preempted":
            case "checkpoint_present":
                recommendation = Recommendation.RESTART_AND_CONTINUE;
                rationale = "Artifacts are complete and the stop signature ("
                        + stopReason + ") is exactly the class checkpointing is designed "
                        + "for - continuing from restart is the intended path. The "
                        + "resubmission itself is ALWAYS explicit.";
                break;
            case "scf_not_converged":
                recommendation = Recommendation.REVIEW_BEFORE_RESUBMIT;
                rationale = "Artifacts are complete BUT the stop was electronic "
                        + "(convergence not achieved) - resubmitting unchanged input "
                        + "re-runs the same non-convergence. Review mixing_beta / "
                        + "electron_maxstep / smearing first.";
                break;
            default: // "unknown" or any future signature is an honest review gate
                recommendation = Recommendation.REVIEW_BEFORE_RESUBMIT;
                rationale = "Artifacts are complete but the stop reason is UNKNOWN "
                        + "(no signature in the bounded scan) - a resubmission decided "
                        + "on a guess is not a plan. Read the tail of the run first.";
                break;
            }
        }
        return OperationResult.success("RESUBMIT_OK",
                "Advice composed for recommendation " + recommendation + ".",
                new Advice(stopReason, restartSafe, assessment.getRecommendedRestartMode(),
                        assessment.getSaveDirectory(), recommendation, rationale,
                        diagnostics));
    }
}
