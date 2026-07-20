/* Copyright (C) 2025-2026 QuantumForge Development Team. */
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
import quantumforge.run.parser.OccupationLevelsParser.OccupationReview;

class OccupationLevelsParserTest {

    @TempDir
    private Path tempDir;

    @Test
    void pairLinesCarryLineProvenanceAndExactGaps() throws IOException {
        Path log = this.tempDir.resolve("scf.out");
        Files.writeString(log, "Program PWSCF v.7.2 starts...\n"
                + "     the SCF run converged-ish text\n"
                + "     highest occupied, lowest unoccupied level (ev):   -13.2500    -5.5000\n"
                + "     total energy =   -15.8 Ry\n"
                + "     highest occupied, lowest unoccupied level (ev):   -13.1000    -5.3000\n"
                + "     End of self-consistent calculation\n");
        OperationResult<OccupationReview> result = OccupationLevelsParser.review(log);
        assertTrue(result.isSuccess(), result.toString());
        OccupationReview review = result.getValue().orElseThrow();
        assertEquals(7, review.getLinesScanned(),
                "6 content lines plus the trailing-empty split element");
        assertEquals(2, review.getPairCount());
        assertEquals(0, review.getSingleCount());
        assertEquals(3, review.getOccurrences().get(0).getLineNumber(),
                "1-based line provenance, nothing hidden");
        assertEquals(5, review.getOccurrences().get(1).getLineNumber());
        assertEquals(-13.25, review.getOccurrences().get(0).getHomoEv(), 1e-12);
        assertEquals(-5.5, review.getOccurrences().get(0).getLumoEv(), 1e-12);
        assertEquals(7.75, review.getOccurrences().get(0).getGapEv(), 1e-9);
        assertEquals(7.8, review.getOccurrences().get(1).getGapEv(), 1e-9,
                "later SCF step has its own pair - no silent 'last wins'");
        assertTrue(review.getOccurrences().get(0).getVerbatim()
                .contains("highest occupied, lowest unoccupied"));
    }

    @Test
    void metallicSingleLinesMeanUndefinedGapAnStated() throws IOException {
        Path log = this.tempDir.resolve("metal.out");
        Files.writeString(log, "Program PWSCF\n"
                + "     highest occupied level (ev):    -6.7400\n"
                + "     smearing contrib. (-TS) =      -0.01 Ry\n");
        OperationResult<OccupationReview> result = OccupationLevelsParser.review(log);
        assertTrue(result.isSuccess(), result.toString());
        OccupationReview review = result.getValue().orElseThrow();
        assertEquals(1, review.getSingleCount());
        assertEquals(0, review.getPairCount());
        assertEquals(-6.74, review.getOccurrences().get(0).getHomoEv(), 1e-12);
        assertTrue(Double.isNaN(review.getOccurrences().get(0).getGapEv()),
                "no LUMO was printed - the gap stays UNDEFINED, never invented");
    }

    @Test
    void absentNeedleIsAnHonestEmptyResult() throws IOException {
        Path log = this.tempDir.resolve("early.out");
        Files.writeString(log, "Program PWSCF starts\nReading input\n");
        OperationResult<OccupationReview> result = OccupationLevelsParser.review(log);
        assertTrue(result.isSuccess(), "absent needle is success-empty, not a fail");
        assertEquals(0, result.getValue().orElseThrow().getOccurrences().size());

        OperationResult<OccupationReview> missing = OccupationLevelsParser.review(
                this.tempDir.resolve("absent.out"));
        assertFalse(missing.isSuccess());
        assertEquals("OCCLEVEL_IO", missing.getCode());
    }
}
