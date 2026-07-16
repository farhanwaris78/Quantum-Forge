package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class WorkflowExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsBashScriptWithStages() throws Exception {
        QECommandDag dag = QECommandDag.build(RunningType.SCF, "espresso.in", 1);
        Path out = tempDir.resolve("run-scf.sh");
        OperationResult<Path> result = WorkflowExporter.export(out, dag,
                WorkflowExporter.Format.BASH, "scf", 1, 1, null);
        assertTrue(result.isSuccess(), result.toString());
        String script = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(script.startsWith("#!/usr/bin/env bash"));
        assertTrue(script.contains("set -euo pipefail"));
        assertTrue(script.contains("Stage 0"));
        assertFalse(script.contains("#SBATCH"));
    }

    @Test
    void exportsSlurmDirectives() throws Exception {
        QECommandDag dag = QECommandDag.build(RunningType.DOS, "espresso.in", 4);
        Path out = tempDir.resolve("run-dos.slurm");
        OperationResult<Path> result = WorkflowExporter.export(out, dag,
                WorkflowExporter.Format.SLURM, "dos-job", 2, 32, "compute");
        assertTrue(result.isSuccess());
        String script = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(script.contains("#SBATCH --job-name='dos-job'"));
        assertTrue(script.contains("#SBATCH --nodes=2"));
        assertTrue(script.contains("#SBATCH --ntasks=32"));
        assertTrue(script.contains("#SBATCH --partition='compute'"));
        assertTrue(script.contains("Stage 2"));
    }

    @Test
    void shellQuoteEscapesEmbeddedQuotes() {
        assertTrue(WorkflowExporter.shellSingleQuote("a'b").contains("\"'\""));
    }
}
