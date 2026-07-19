package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

/** Batch-55 coverage for the fail-closed FORCE_SETS reader (Roadmap #107). */
class PhonopyForceSetsReaderTest {

    @TempDir
    private Path tempDir;

    @Test
    void writerReaderRoundTripIsExact() throws IOException {
        QEPhonopyForceSetsWriter writer = new QEPhonopyForceSetsWriter();
        writer.addRecord(1, new double[] {0.01, 0.0, 0.0},
                new double[][] {{0.1, 0.0, 0.0}, {-0.1, 0.0, 0.0}});
        writer.addRecord(2, new double[] {0.0, 0.01, 0.0},
                new double[][] {{0.0, 0.05, 0.0}, {0.0, -0.05, 0.0}});
        Path file = this.tempDir.resolve("FORCE_SETS");
        writer.writeForceSetsFile(file, 2);

        OperationResult<PhonopyForceSetsReader.ForceSets> result =
                PhonopyForceSetsReader.parse(file);
        assertTrue(result.isSuccess(), result.getMessage());
        PhonopyForceSetsReader.ForceSets sets = result.getValue().orElseThrow();
        assertEquals(2, sets.getNumAtoms());
        assertEquals(2, sets.getSets().size());
        assertEquals(2, sets.countDistinctDisplacedAtoms());
        assertEquals(0, sets.getTrailingLines());
        PhonopyForceSetsReader.DisplacementSet first = sets.getSets().get(0);
        assertEquals(1, first.getAtomIndex());
        assertEquals(0.01, first.displacementNorm(), 1.0e-12);
        assertEquals(0.1, first.maxForceNorm(), 1.0e-12);
        assertEquals(0.1, first.meanForceNorm(), 1.0e-12);
        assertEquals(-0.1, first.getForces()[1][0], 1.0e-15);
        assertEquals(0.05, sets.getSets().get(1).maxForceNorm(), 1.0e-12);
    }

    @Test
    void rejectsBadHeaders() throws IOException {
        Path junk = write("2\nnotAnInt\n");
        OperationResult<PhonopyForceSetsReader.ForceSets> result =
                PhonopyForceSetsReader.parse(junk);
        assertFalse(result.isSuccess());
        assertEquals("FORCE_SETS_SYNTAX", result.getCode());

        Path zeroAtoms = write("0\n1\n");
        OperationResult<PhonopyForceSetsReader.ForceSets> zero =
                PhonopyForceSetsReader.parse(zeroAtoms);
        assertFalse(zero.isSuccess());
        assertEquals("FORCE_SETS_RANGE", zero.getCode());
    }

    @Test
    void rejectsOutOfRangeIndexAndTruncation() throws IOException {
        Path outOfRange = write("2\n1\n3\n0.01 0.0 0.0\n0.1 0.0 0.0\n-0.1 0.0 0.0\n");
        OperationResult<PhonopyForceSetsReader.ForceSets> range =
                PhonopyForceSetsReader.parse(outOfRange);
        assertFalse(range.isSuccess());
        assertEquals("FORCE_SETS_RANGE", range.getCode());
        assertTrue(range.getMessage().contains("displaced-atom index"), range.getMessage());

        Path truncated = write("2\n1\n1\n0.01 0.0 0.0\n0.1 0.0 0.0\n"); // one force row short
        OperationResult<PhonopyForceSetsReader.ForceSets> cut =
                PhonopyForceSetsReader.parse(truncated);
        assertFalse(cut.isSuccess());
        assertEquals("FORCE_SETS_TRUNCATED", cut.getCode());
    }

    @Test
    void rejectsNonFiniteAndWrongWidth() throws IOException {
        Path nan = write("2\n1\n1\n0.01 0.0 0.0\n0.1 0.0 0.0\nNaN 0.0 0.0\n");
        OperationResult<PhonopyForceSetsReader.ForceSets> nanResult =
                PhonopyForceSetsReader.parse(nan);
        assertFalse(nanResult.isSuccess(), "NaN must be refused explicitly");
        assertEquals("FORCE_SETS_SYNTAX", nanResult.getCode());

        Path wide = write("2\n1\n1\n0.01 0.0 0.0 1.0\n0.1 0.0 0.0\n0.1 0.0 0.0\n");
        assertFalse(PhonopyForceSetsReader.parse(wide).isSuccess(),
                "Four-column rows must be refused");

        Path fortranD = write("2\n1\n1\n1.0D-2 0 0\n0.1 0 0\n-0.1 0 0\n");
        OperationResult<PhonopyForceSetsReader.ForceSets> d =
                PhonopyForceSetsReader.parse(fortranD);
        assertTrue(d.isSuccess(), d.getMessage());
        assertEquals(0.01, d.getValue().orElseThrow().getSets().get(0).displacementNorm(),
                1.0e-12);
    }

    @Test
    void missingFileIsAnIoFailure() {
        OperationResult<PhonopyForceSetsReader.ForceSets> result =
                PhonopyForceSetsReader.parse(this.tempDir.resolve("no_such_file"));
        assertFalse(result.isSuccess());
        assertEquals("FORCE_SETS_IO", result.getCode());
    }

    private Path write(String content) throws IOException {
        Path file = this.tempDir.resolve("FORCE_SETS-" + content.hashCode());
        Files.write(file, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }
}
