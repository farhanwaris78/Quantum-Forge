/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class WorkspaceLightIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void cataloguesInputsLogsAndSkipsOthers() throws IOException {
        Files.writeString(this.tempDir.resolve("si.in"),
                "&CONTROL\n   calculation = 'relax'\n/\n"
                + "&SYSTEM\n   ibrav = 2, nat = 2, ntyp = 1, ecutwfc = 30.0\n/\n"
                + "&ELECTRONS\n/\n"
                + "ATOMIC_SPECIES\n Si 28.0855 Si.pz-vbc.UPF\n"
                + "ATOMIC_POSITIONS crystal\n Si 0.0 0.0 0.0\n Si 0.25 0.25 0.25\n"
                + "K_POINTS automatic\n 4 4 4 0 0 0\n");
        Files.writeString(this.tempDir.resolve("si.log"),
                "tidy pw output\nJOB DONE.\n");
        Files.writeString(this.tempDir.resolve("crash.log"),
                "Error in routine electrons (1): bad idea\n");
        Files.writeString(this.tempDir.resolve("running.out"),
                "scf continues... no marker yet\n");
        Files.writeString(this.tempDir.resolve("readme.txt"), "not an artifact\n");

        OperationResult<WorkspaceLightIndex.WorkspaceScan> result =
                WorkspaceLightIndex.scan(this.tempDir);
        assertTrue(result.isSuccess(), result.getMessage());
        WorkspaceLightIndex.WorkspaceScan scan = result.getValue().orElseThrow();
        assertEquals(4, scan.getEntries().size());
        assertEquals(1, scan.getOtherFiles(), "readme.txt counted, untouched");

        WorkspaceLightIndex.WorkspaceEntry input = scan.getEntries().stream()
                .filter(e -> e.getFileName().equals("si.in")).findFirst().orElseThrow();
        assertEquals("INPUT", input.getKind());
        assertEquals("Si", input.getComposition());
        assertEquals(2, input.getAtomCount());
        assertTrue(input.getCalculation().contains("relax"), input.getCalculation());

        assertTrue(scan.getEntries().stream()
                .anyMatch(e -> e.getFileName().equals("si.log")
                        && e.getStatus().startsWith("completed")),
                "JOB DONE. must be reported as completed");
        assertTrue(scan.getEntries().stream()
                .anyMatch(e -> e.getFileName().equals("crash.log")
                        && e.getStatus().startsWith("error")));
        assertTrue(scan.getEntries().stream()
                .anyMatch(e -> e.getFileName().equals("running.out")
                        && e.getStatus().startsWith("unknown")));
    }

    @Test
    void failsClosedOnMissingOrEmptyDirectories() throws IOException {
        assertEquals("WS_IO",
                WorkspaceLightIndex.scan(this.tempDir.resolve("missing")).getCode());
        Path empty = Files.createDirectories(this.tempDir.resolve("empty"));
        assertEquals("WS_EMPTY", WorkspaceLightIndex.scan(empty).getCode());
        Path onlyOther = Files.createDirectories(this.tempDir.resolve("other"));
        Files.writeString(onlyOther.resolve("data.bin"), "00 11");
        assertEquals("WS_EMPTY", WorkspaceLightIndex.scan(onlyOther).getCode(),
                "Directories with no QE artifacts fail honestly");
    }
}
