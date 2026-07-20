/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;
import quantumforge.run.WorkflowAudit.SyncVerdict;

class WorkflowAuditTest {

    @TempDir
    Path tempDir;

    private Path writeExport() throws IOException {
        QECommandDag dag = QECommandDag.build(RunningType.PHONON, "espresso.in", 1);
        Path script = this.tempDir.resolve(".quantumforge.workflow.sh");
        OperationResult<Path> exported = WorkflowExporter.export(
                script, dag, WorkflowExporter.Format.BASH, "espresso", 1, 1, null);
        assertTrue(exported.isSuccess(), exported.toString());
        return script;
    }

    @Test
    void realExportAuditsClean() throws IOException {
        Path script = writeExport();
        OperationResult<WorkflowAudit.Audit> result = WorkflowAudit.audit(script);
        assertTrue(result.isSuccess(), result.toString());
        WorkflowAudit.Audit audit = result.getValue().orElseThrow();
        assertTrue(audit.hasShebang(), "exported artifact keeps its shebang");
        assertTrue(audit.hasSetOptions(), "set -euo pipefail is a hard safety marker");
        assertFalse(audit.hasSlurmBlock(), "BASH format carries no SLURM directives");
        assertTrue(audit.getGeneratorLine().contains("QuantumForge"),
                audit.getGeneratorLine());
        assertEquals("PHONON", audit.getWorkflowType());
        assertEquals(List.of("scf", "ph", "q2r", "matdyn"), audit.stageIds(),
                "the PHONON DAG exports exactly this ordered stage set");
        assertTrue(audit.abortStageIds().isEmpty(),
                "a well-configured export aborts nowhere");
        for (WorkflowAudit.StageVerdict stage : audit.getStages()) {
            assertTrue(stage.hasCommand(), stage.getId());
            assertFalse(stage.isOptional(), "PHONON stages are all required");
        }
    }

    @Test
    void unconfiguredStageAndOptionalSentinelsAreNamed() throws IOException {
        Path script = this.tempDir.resolve("wf.sh");
        Files.writeString(script,
                "#!/usr/bin/env bash\n"
                        + "set -euo pipefail\n"
                        + "# Stage 0: SCF\n"
                        + "echo \"==> scf\"\n"
                        + "echo \"No command recorded for stage scf; configure QE paths before running.\" >&2\n"
                        + "exit 2\n\n"
                        + "# Stage 1: Projected DOS (projwfc.x) (optional)\n"
                        + "echo \"==> projwfc\"\n"
                        + "echo \"No command recorded for stage projwfc; configure QE paths before running.\" >&2\n");
        WorkflowAudit.Audit audit = WorkflowAudit.audit(script).getValue().orElseThrow();
        assertEquals(List.of("scf", "projwfc"), audit.stageIds());
        WorkflowAudit.StageVerdict scf = audit.getStages().get(0);
        assertFalse(scf.hasCommand(), "the No-command sentinel maps to hasCommand=false");
        assertTrue(scf.isAbortsWhenEmpty(),
                "a REQUIRED stage without command exits 2 at runtime - named, not hidden");
        WorkflowAudit.StageVerdict projwfc = audit.getStages().get(1);
        assertTrue(projwfc.isOptional());
        assertFalse(projwfc.hasCommand());
        assertFalse(projwfc.isAbortsWhenEmpty(),
                "an OPTIONAL stage without command never aborts the run");
        assertEquals(List.of("scf"), audit.abortStageIds());
        assertTrue(audit.hasSetOptions(), "strict mode marker survives");
    }

    @Test
    void syncVerdictsAreOrderSensitiveArithmetic() {
        List<String> artifact = List.of("scf", "ph", "q2r");
        List<String> current = List.of("scf", "ph", "q2r", "matdyn");
        assertEquals(SyncVerdict.BEHIND_CONFIG, WorkflowAudit.sync(artifact, current),
                "config added matdyn after the export - artifact is behind");
        assertEquals(List.of("matdyn"),
                WorkflowAudit.missingFromArtifact(artifact, current));
        assertEquals(SyncVerdict.AHEAD_OF_CONFIG, WorkflowAudit.sync(current, artifact));
        assertEquals(List.of("matdyn"), WorkflowAudit.extraInArtifact(current, artifact));
        assertEquals(SyncVerdict.IN_SYNC, WorkflowAudit.sync(current, current));
        assertEquals(SyncVerdict.DIVERGED,
                WorkflowAudit.sync(List.of("ph", "scf"), List.of("scf", "ph")),
                "a reordered artifact is DIVERGED, never IN_SYNC");
        assertEquals(SyncVerdict.NOT_COMPARABLE, WorkflowAudit.sync(artifact, null),
                "an unrebuildable current DAG refuses comparison honestly");
        assertEquals(List.of(), WorkflowAudit.missingFromArtifact(artifact, null));
        assertEquals(artifact, WorkflowAudit.extraInArtifact(artifact, null),
                "with no expected list, everything is honestly 'extra'");
    }

    @Test
    void strangerFilesRefuseClosed() throws IOException {
        Path script = this.tempDir.resolve("random.sh");
        Files.writeString(script, "echo hello\nls -la\n");
        OperationResult<WorkflowAudit.Audit> refused = WorkflowAudit.audit(script);
        assertFalse(refused.isSuccess());
        assertEquals("WORKFLOW_SHAPE", refused.getCode(),
                "auditing a stranger file would fabricate trust - refused");
        assertEquals("WORKFLOW_FILE",
                WorkflowAudit.audit(this.tempDir.resolve("nope.sh")).getCode());
        assertEquals("WORKFLOW_FILE", WorkflowAudit.audit(null).getCode());
    }
}
