package quantumforge.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

/** Batch-57 coverage for the extXYZ dataset validator (Roadmap #143). */
class ExtXyzDatasetValidatorTest {

    @TempDir
    private Path tempDir;

    private static final String FRAME_A =
            "2\n"
            + "Lattice=\"10.0 0.0 0.0 0.0 10.0 0.0 0.0 0.0 10.0\" "
            + "Properties=species:S:1:pos:R:3 energy=-15.5 pbc=\"T T T\"\n"
            + "Si 0.0 0.0 0.0\n"
            + "Si 1.35 1.35 1.35\n";

    private Path write(String name, String content) throws IOException {
        Path file = this.tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void validDatasetReportsStatsAndSchema() throws IOException {
        String frameB =
                "3\n"
                + "Lattice=\"11 0 0 0 11 0 0 0 11\" Properties=species:S:1:pos:R:3 "
                + "energy=-16.25 pbc=\"T T T\"\n"
                + "Si 0 0 0\nO 1 0 0\nO 0 1 0\n";
        OperationResult<ExtXyzDatasetValidator.DatasetReport> result =
                ExtXyzDatasetValidator.validate(write("dataset.extxyz", FRAME_A + frameB));
        assertTrue(result.isSuccess(), result.getMessage());
        ExtXyzDatasetValidator.DatasetReport report = result.getValue().orElseThrow();
        assertEquals(2, report.getFrameCount());
        assertEquals(2, report.getMinAtoms());
        assertEquals(3, report.getMaxAtoms());
        assertEquals(2, report.getFramesWithEnergy());
        assertEquals(-16.25, report.getMinEnergyEv(), 1.0e-12);
        assertEquals(-15.5, report.getMaxEnergyEv(), 1.0e-12);
        assertEquals(List.of("O", "Si"), report.getSpecies().stream().sorted().toList());
        assertEquals(2, report.getFramesWithLattice());
        assertEquals("species:S:1:pos:R:3", report.getPropertiesSchema());
        assertEquals(0, report.getDuplicateCount());
        assertTrue(report.getWarnings().isEmpty(), report.getWarnings().toString());
    }

    @Test
    void exactDuplicatesAreCountedAndIndexed() throws IOException {
        OperationResult<ExtXyzDatasetValidator.DatasetReport> result =
                ExtXyzDatasetValidator.validate(write("dup.extxyz", FRAME_A + FRAME_A));
        assertTrue(result.isSuccess(), result.getMessage());
        ExtXyzDatasetValidator.DatasetReport report = result.getValue().orElseThrow();
        assertEquals(1, report.getDuplicateCount());
        assertEquals(1, report.getDuplicatePairs().get(0)[0]);
        assertEquals(2, report.getDuplicatePairs().get(0)[1]);
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("duplicate")),
                report.getWarnings().toString());
    }

    @Test
    void missingLabelsAndBadLatticeWarnNotBlock() throws IOException {
        String frame =
                "1\n"
                + "Lattice=\"10 0 0 0 10\" comment without energy\n"
                + "H 0.0 0.0 0.0\n";
        OperationResult<ExtXyzDatasetValidator.DatasetReport> result =
                ExtXyzDatasetValidator.validate(write("nolabel.extxyz", frame));
        assertTrue(result.isSuccess(), result.getMessage());
        ExtXyzDatasetValidator.DatasetReport report = result.getValue().orElseThrow();
        assertEquals(0, report.getFramesWithEnergy());
        assertEquals(0, report.getFramesWithLattice());
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("9 finite numbers")),
                report.getWarnings().toString());
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("training set")),
                report.getWarnings().toString());
    }

    @Test
    void structuralDefectsBlock() throws IOException {
        Path nan = write("nan.extxyz",
                "1\ncomment\nH 0.0 NaN 0.0\n");
        OperationResult<ExtXyzDatasetValidator.DatasetReport> nanResult =
                ExtXyzDatasetValidator.validate(nan);
        assertFalse(nanResult.isSuccess());
        assertEquals("EXTXYZ_VALUE", nanResult.getCode());

        Path truncated = write("trunc.extxyz", "2\ncomment\nH 0 0 0\n");
        OperationResult<ExtXyzDatasetValidator.DatasetReport> cut =
                ExtXyzDatasetValidator.validate(truncated);
        assertFalse(cut.isSuccess());
        assertEquals("EXTXYZ_TRUNCATED", cut.getCode());

        Path badSpecies = write("species.extxyz", "1\ncomment\nxx 0 0 0\n");
        assertFalse(ExtXyzDatasetValidator.validate(badSpecies).isSuccess(),
                "'xx' is not a valid species token");

        Path empty = write("empty.extxyz", "");
        assertEquals("EXTXYZ_EMPTY", ExtXyzDatasetValidator.validate(empty).getCode());
    }
}
