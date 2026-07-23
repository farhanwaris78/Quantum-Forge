/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.SlurmScriptBuilder.SlurmDraft;

class SlurmScriptBuilderTest {

    @Test
    void fullDraftRendersOwnedDirectivesAndVerbatimPayload() {
        OperationResult<SlurmDraft> result = SlurmScriptBuilder.validate(
                "qe-scf", "main", 2, 64, "1:30:00", "qe/7.3,mpi/openmpi",
                "srun pw.x -in scf.in > scf.out");
        assertTrue(result.isSuccess(), result.toString());
        SlurmDraft draft = result.getValue().orElseThrow();
        String script = draft.render();
        assertTrue(script.startsWith("#!/bin/bash\n"), script);
        assertTrue(script.contains("#SBATCH --job-name=qe-scf\n"), script);
        assertTrue(script.contains("#SBATCH --nodes=2\n"), script);
        assertTrue(script.contains("#SBATCH --ntasks=64\n"), script);
        assertTrue(script.contains("#SBATCH --time=01:30:00\n"), script + " | " + "loose '1:30:00' normalizes to strict zero-padded HH:MM:SS");
        assertTrue(script.contains("#SBATCH --partition=main\n"), script);
        assertTrue(script.contains("module load qe/7.3\n"), script);
        assertTrue(script.contains("module load mpi/openmpi\n"), script);
        assertTrue(script.contains("\nsrun pw.x -in scf.in > scf.out\n"), script + " | " + "the payload is exactly the analyst-reviewed line - verbatim");
    }

    @Test
    void omittedPartitionAndModulesRenderHonestCommentsNotAssumptions() {
        OperationResult<SlurmDraft> result = SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "00:20:00", "", "srun pw.x -in scf.in > scf.out");
        assertTrue(result.isSuccess(), result.toString());
        String script = result.getValue().orElseThrow().render();
        assertTrue(script.contains("# --partition intentionally omitted"), script);
        assertTrue(script.contains("# no modules declared"), script);
        assertFalse(script.contains("#SBATCH --partition"), script + " | " + "an omitted partition must not materialize as a directive");
        assertFalse(script.contains("module load"), script);
    }

    @Test
    void directiveRefusalsAreFailClosed() {
        assertEquals("SLURM_NAME", SlurmScriptBuilder.validate(
                "1bad", "", 1, 1, "00:20:00", "", "x").getCode(),
                "job-name must start with a letter");
        assertEquals("SLURM_NAME", SlurmScriptBuilder.validate(
                "bad name", "", 1, 1, "00:20:00", "", "x").getCode());
        assertEquals("SLURM_PARTITION", SlurmScriptBuilder.validate(
                "qe-scf", "main; rm -rf /", 1, 1, "00:20:00", "", "x").getCode(),
                "partition separators refuse, not quote");
        assertEquals("SLURM_NODES", SlurmScriptBuilder.validate(
                "qe-scf", "", 0, 1, "00:20:00", "", "x").getCode());
        assertEquals("SLURM_NODES", SlurmScriptBuilder.validate(
                "qe-scf", "", 5000, 1, "00:20:00", "", "x").getCode());
        assertEquals("SLURM_NTASKS", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 0, "00:20:00", "", "x").getCode());
        assertEquals("SLURM_NTASKS", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 70000, "00:20:00", "", "x").getCode());
    }

    @Test
    void walltimeGrammarIsStrict() {
        assertEquals("SLURM_TIME", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "90:00", "", "x").getCode(),
                "two-field times refuse");
        assertEquals("SLURM_TIME", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "10:90:00", "", "x").getCode(),
                "minutes above 59 refuse");
        assertEquals("SLURM_TIME", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "200:00:00", "", "x").getCode(),
                "the 7-day per-job cap refuses");
        assertEquals("SLURM_TIME", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "00:00:00", "", "x").getCode(),
                "zero describes no job at all");
        assertEquals("SLURM_TIME", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "2-00:00:00", "", "x").getCode(),
                "SLURM days syntax refuses until site profiles declare it");
        assertEquals("SLURM_TIME", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "ab:00:00", "", "x").getCode());
    }

    @Test
    void moduleAndPayloadGuards() {
        assertEquals("SLURM_MODULE", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "00:20:00", "qe 7.3", "x").getCode(),
                "whitespace never reaches a module line");
        assertEquals("SLURM_COMMAND", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "00:20:00", "", "   ").getCode(),
                "empty jobs are never drafted");
        assertEquals("SLURM_COMMAND", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "00:20:00", "", "ls\npwd").getCode(),
                "multi-line payloads are stated depth, not silently joined");
        assertEquals("SLURM_COMMAND", SlurmScriptBuilder.validate(
                "qe-scf", "", 1, 1, "00:20:00", "", "#SBATCH --x").getCode(),
                "directive smuggling inside the payload refuses");
    }
}
