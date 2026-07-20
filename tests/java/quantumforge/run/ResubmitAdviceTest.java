/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;
import quantumforge.run.ResubmitAdvice.Recommendation;

class ResubmitAdviceTest {

    @TempDir
    Path tempDir;

    private void completeSave() throws IOException {
        Path save = this.tempDir.resolve("espresso.save");
        Files.createDirectories(save);
        Files.writeString(save.resolve("data-file-schema.xml"), "<root/>");
        Files.writeString(save.resolve("charge-density.dat"), "density");
    }

    @Test
    void walltimeWithCompleteSaveRecommendsRestart() throws IOException {
        completeSave();
        Files.writeString(this.tempDir.resolve("espresso.log"),
                "output written\ncancelled due to time limit reached\n");
        OperationResult<ResubmitAdvice.Advice> result =
                ResubmitAdvice.advise(this.tempDir, "espresso");
        assertTrue(result.isSuccess(), result.toString());
        ResubmitAdvice.Advice advice = result.getValue().orElseThrow();
        assertEquals("walltime", advice.getStopReason());
        assertTrue(advice.isRestartSafe());
        assertEquals(Recommendation.RESTART_AND_CONTINUE, advice.getRecommendation(),
                "resource-class stop + complete artifacts is the design case");
        assertEquals("restart_mode = 'restart'", advice.controlSnippet());
        assertTrue(advice.getRationale().contains("ALWAYS explicit"),
                "the resubmission itself is never implied");
    }

    @Test
    void scfStopIsAReviewGateEvenWithCompleteArtifacts() throws IOException {
        completeSave();
        Files.writeString(this.tempDir.resolve("espresso.log"),
                "convergence not achieved after 100 iterations\n");
        OperationResult<ResubmitAdvice.Advice> result =
                ResubmitAdvice.advise(this.tempDir, "espresso");
        ResubmitAdvice.Advice advice = result.getValue().orElseThrow();
        assertEquals("scf_not_converged", advice.getStopReason());
        assertEquals(Recommendation.REVIEW_BEFORE_RESUBMIT, advice.getRecommendation(),
                "an electronic stop re-fails if resubmitted unchanged - never continue");
        assertTrue(advice.getRationale().contains("mixing_beta"),
                "the review gate names the knobs to change");
    }

    @Test
    void unsafeArtifactsDominateEverySignature() throws IOException {
        // espresso NOT converged, and no .save directory at all.
        Files.writeString(this.tempDir.resolve("espresso.log"),
                "convergence not achieved\n");
        ResubmitAdvice.Advice advice =
                ResubmitAdvice.advise(this.tempDir, "espresso").getValue().orElseThrow();
        assertFalse(advice.isRestartSafe());
        assertEquals(Recommendation.REVIEW_BEFORE_RESUBMIT, advice.getRecommendation());
        assertEquals("restart_mode = 'from_scratch'", advice.controlSnippet());

        // unknown stop + missing artifacts = insufficient evidence, not a restart.
        ResubmitAdvice.Advice blind =
                ResubmitAdvice.advise(this.tempDir, "nothing-ran-here").getValue()
                        .orElseThrow();
        assertEquals("unknown", blind.getStopReason());
        assertEquals(Recommendation.INSUFFICIENT_EVIDENCE, blind.getRecommendation(),
                "never advise movement on no evidence");

        // walltime signature + broken artifacts = from_scratch, never restart.
        Files.writeString(this.tempDir.resolve("broke.log"), "walltime exceeded\n");
        Path save = this.tempDir.resolve("broke.save");
        Files.createDirectories(save); // exists but EMPTY - no data-file, no charge
        ResubmitAdvice.Advice broken =
                ResubmitAdvice.advise(this.tempDir, "broke").getValue().orElseThrow();
        assertEquals(Recommendation.FROM_SCRATCH, broken.getRecommendation(),
                "no stop signature overrides INCOMPLETE artifacts");
        assertTrue(broken.getRationale().contains("INCOMPLETE"));
    }

    @Test
    void missingDirectoryRefusesClosed() {
        OperationResult<ResubmitAdvice.Advice> result = ResubmitAdvice.advise(
                this.tempDir.resolve("nope"), "espresso");
        assertFalse(result.isSuccess());
        assertEquals("RESUBMIT_DIR", result.getCode());
        OperationResult<ResubmitAdvice.Advice> nullDir = ResubmitAdvice.advise(null, "x");
        assertFalse(nullDir.isSuccess());
        assertEquals("RESUBMIT_DIR", nullDir.getCode());
    }
}
