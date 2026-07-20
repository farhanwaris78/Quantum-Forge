/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.builder.PdbStructureReader.PdbStructure;
import quantumforge.operation.OperationResult;

class PdbStructureReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void parsesCryst1AtomsAndFlagsPartialOccupancy() throws IOException {
        Path file = write("ala.pdb",
                "HEADER    small alanine example\n"
                + "CRYST1   30.000   40.000   50.000  90.00  90.00  90.00\n"
                + "ATOM      1  N   ALA A   1      11.104  13.207  10.325  1.00 20.00           N\n"
                + "ATOM      2  CA  ALA A   1      12.104  13.500  10.500  0.50 20.00           C\n"
                + "HETATM  101  O   HOH A 101      20.000  20.000  20.000  1.00 30.00\n"
                + "END\n");
        OperationResult<PdbStructure> result = PdbStructureReader.parse(file);
        assertTrue(result.isSuccess(), result.toString());
        PdbStructure structure = result.getValue().orElseThrow();
        assertEquals(3, structure.getAtoms().size());
        assertTrue(structure.hasCryst());
        assertEquals(60000.0, structure.cellVolume(), 1e-9);
        assertEquals(1, structure.getPartialOccupancyCount(), "occ 0.50 is disorder");
        assertEquals(1, structure.getMissingElementCount(),
                "the HETATM line has no element column - reported, not guessed");
        assertEquals(2, structure.getIgnoredLines(), "HEADER + END only");
        Map<String, Integer> counts = structure.elementCounts();
        assertEquals(Integer.valueOf(1), counts.get("N"));
        assertEquals(Integer.valueOf(1), counts.get("C"));
        assertEquals(null, counts.get("O"), "no element column, no invented O");
        assertEquals(12.104, structure.getAtoms().get(1).getX(), 0.0);
        assertEquals("C", structure.getAtoms().get(1).getElement());
    }

    @Test
    void malformedRecordsFailClosedWithCodes() throws IOException {
        assertEquals("PDB_MODEL", PdbStructureReader.parse(write("model.pdb",
                "MODEL        1\nATOM      1  N   ALA A   1       1.0 1.0 1.0  1.00 20.00           N\n"))
                .getCode(), "multi-model files are refused, never silently split");
        assertEquals("PDB_SYNTAX", PdbStructureReader.parse(write("short.pdb",
                "ATOM      1  N   ALA A   1       1.0 1.0 1.0  1.00\n")).getCode());
        assertEquals("PDB_VALUE", PdbStructureReader.parse(write("nan.pdb",
                "ATOM      1  N   ALA A   1       xyz 1.0 1.0  1.00 20.00           N\n"))
                .getCode());
        assertEquals("PDB_VALUE", PdbStructureReader.parse(write("occ.pdb",
                "ATOM      1  N   ALA A   1       1.0 1.0 1.0  1.50 20.00           N\n"))
                .getCode(), "occupancy > 1 is a malformed record");
        assertEquals("PDB_CRYST", PdbStructureReader.parse(write("angle.pdb",
                "CRYST1   30.000   40.000   50.000  190.00  90.00  90.00\n"
                        + "ATOM      1  N   ALA A   1       1.0 1.0 1.0  1.00 20.00           N\n"))
                .getCode());
        assertEquals("PDB_EMPTY", PdbStructureReader.parse(write("empty.pdb",
                "REMARK nothing here\n")).getCode());
        assertEquals("PDB_IO", PdbStructureReader.parse(
                this.tempDir.resolve("missing.pdb")).getCode());
    }
}
