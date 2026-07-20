/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;
import quantumforge.remote.SftpTransferPlan.TransferStep;

class SftpTransferPlanTest {

    @TempDir
    Path tempDir;

    private Path fixture() throws IOException {
        return Files.writeString(this.tempDir.resolve("deck.cube"), "cube payload bytes 123");
    }

    @Test
    void validPlanPinsHashSizeAndHonestRenderBlock() throws IOException {
        fixture();
        OperationResult<TransferStep> result = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "/home/farhan/qe/deck.cube", false);
        assertTrue(result.isSuccess(), result.toString());
        TransferStep step = result.getValue().orElseThrow();
        assertEquals("deck.cube", step.getLocalName());
        assertEquals(22L, step.getLocalBytes());
        assertEquals("4e866172d93737f62a50d277835dde013b32ea03401805d9896425fe78598152",
                step.getLocalSha256(),
                "SHA-256 of the fixture pinned at draft time - the integrity target");
        assertEquals("/home/farhan/qe/deck.cube", step.getRemotePath());
        assertFalse(step.isOverwriteAllowed());
        String render = step.render();
        assertTrue(render.contains("local_file      = deck.cube\n"), render);
        assertTrue(render.contains("local_bytes     = 22\n"), render);
        assertTrue(render.contains("local_sha256    = "
                + "4e866172d93737f62a50d277835dde013b32ea03401805d9896425fe78598152"), render);
        assertTrue(render.contains("remote_path     = /home/farhan/qe/deck.cube\n"), render);
        assertTrue(render.contains("overwrite       = REFUSE-IF-EXISTS\n"), render,
                "default posture is an explicit no-clobber statement");
        assertTrue(render.contains("verify_after    = sha256 MUST match local_sha256 (mandatory)\n"),
                render);
        assertTrue(render.contains("transfer_status = NOT STARTED - planning slice only\n"), render,
                "the plan can never masquerade as a completed transfer");
    }

    @Test
    void overwriteFlipsOnlyByExplicitChoice() throws IOException {
        fixture();
        OperationResult<TransferStep> result = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "/tmp/deck.cube", true);
        assertTrue(result.isSuccess(), result.toString());
        TransferStep step = result.getValue().orElseThrow();
        assertTrue(step.isOverwriteAllowed());
        assertTrue(step.render().contains("overwrite       = ALLOWED\n"), step.render());
    }

    @Test
    void localPathRefusalsAreFailClosed() throws IOException {
        fixture();
        OperationResult<TransferStep> climb = SftpTransferPlan.prepare(
                this.tempDir, "../escape.cube", "/tmp/x.cube", false);
        assertFalse(climb.isSuccess());
        assertEquals("SFTP_LOCAL_PATH", climb.getCode());

        OperationResult<TransferStep> absolute = SftpTransferPlan.prepare(
                this.tempDir, this.tempDir.resolve("deck.cube").toString(), "/tmp/x.cube", false);
        assertFalse(absolute.isSuccess());
        assertEquals("SFTP_LOCAL_PATH", absolute.getCode());

        OperationResult<TransferStep> unsafe = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube; rm -rf /", "/tmp/x.cube", false);
        assertFalse(unsafe.isSuccess());
        assertEquals("SFTP_LOCAL_PATH", unsafe.getCode(),
                "command-separator characters in a path are refused, never quoted around");

        OperationResult<TransferStep> missing = SftpTransferPlan.prepare(
                this.tempDir, "ghost.cube", "/tmp/x.cube", false);
        assertFalse(missing.isSuccess());
        assertEquals("SFTP_LOCAL_IO", missing.getCode(),
                "a nonexistent payload refuses - nothing is staged silently");
    }

    @Test
    void remotePathRefusalsAreFailClosed() throws IOException {
        fixture();
        OperationResult<TransferStep> trailing = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "/home/farhan/qe/", false);
        assertFalse(trailing.isSuccess());
        assertEquals("SFTP_REMOTE_PATH", trailing.getCode(),
                "a directory marker refuses - the remote FILE name is never guessed");

        OperationResult<TransferStep> climb = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "/home/../etc/deck.cube", false);
        assertFalse(climb.isSuccess());
        assertEquals("SFTP_REMOTE_PATH", climb.getCode());

        OperationResult<TransferStep> relative = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "home/farhan/deck.cube", false);
        assertFalse(relative.isSuccess());
        assertEquals("SFTP_REMOTE_PATH", relative.getCode());

        OperationResult<TransferStep> whitespace = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "/home/far han/deck.cube", false);
        assertFalse(whitespace.isSuccess());
        assertEquals("SFTP_REMOTE_PATH", whitespace.getCode());

        OperationResult<TransferStep> expansion = SftpTransferPlan.prepare(
                this.tempDir, "deck.cube", "/home/$USER/deck.cube", false);
        assertFalse(expansion.isSuccess());
        assertEquals("SFTP_REMOTE_PATH", expansion.getCode(),
                "expansion characters refuse - the plan is literal, not shell-interpreted");
    }

    @Test
    void draftPathCompilesToStagingRelativeByConfinement() throws IOException {
        Path payload = this.tempDir.resolve("espresso.log");
        Files.writeString(payload, "log\n");
        SftpTransferPlan.TransferStep step = SftpTransferPlan.prepare(this.tempDir,
                "espresso.log", "/scratch/jobs/a1/espresso.log", false)
                .getValue().orElseThrow();
        OperationResult<String> bridge = step.relativizeUnder("/scratch/");
        assertTrue(bridge.isSuccess(), bridge.toString());
        assertEquals("SFTP_BRIDGE_OK", bridge.getCode());
        assertEquals("jobs/a1/espresso.log", bridge.getValue().orElseThrow(),
                "the drafted absolute path compiles to the staging-relative form the"
                        + " runtime consumes");
    }

    @Test
    void confinementRefusesInsteadOfReRooting() throws IOException {
        Path payload = this.tempDir.resolve("pw.in");
        Files.writeString(payload, "&CONTROL\n/\n");
        SftpTransferPlan.TransferStep step = SftpTransferPlan.prepare(this.tempDir,
                "pw.in", "/home/farhan/qe/pw.in", false).getValue().orElseThrow();
        OperationResult<String> escaped = step.relativizeUnder("/scratch");
        assertFalse(escaped.isSuccess(),
                "the drafted path sits outside the staging root - silently prefixing"
                        + " it would write somewhere unreviewed");
        assertEquals("SFTP_ROOT_CONFINEMENT", escaped.getCode());
        assertTrue(escaped.getMessage().contains("never silently re-rooted")
                || escaped.getMessage().contains("never"
                        + " silently re-rooted"), escaped.getMessage());
        OperationResult<String> blankRoot = step.relativizeUnder("   ");
        assertFalse(blankRoot.isSuccess());
        assertEquals("SFTP_BRIDGE_ROOT", blankRoot.getCode());
        OperationResult<String> relativeRoot = step.relativizeUnder("scratch");
        assertFalse(relativeRoot.isSuccess());
        assertEquals("SFTP_BRIDGE_ROOT", relativeRoot.getCode(),
                "a non-absolute root is invalid on its own terms");
    }
}
