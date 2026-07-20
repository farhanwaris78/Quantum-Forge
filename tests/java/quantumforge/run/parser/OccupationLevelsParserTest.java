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

    @Test
    void spinPairedFixtureKeepsAllFourPairsProvenanced() throws IOException {
        // tests/fixtures/qe/scf_spin_paired.log - the spin-polarized insulator
        // class of the #47 acceptance matrix (batch 148): nspin=2 with fixed
        // occupations prints one "highest occupied, lowest unoccupied level
        // (ev)" pair per SCF step. The parser keeps ALL of them with line
        // provenance; per-channel attribution is never guessed from these
        // lines (the needle carries no spin label).
        Path log = Path.of("tests/fixtures/qe/scf_spin_paired.log");
        OperationResult<OccupationReview> result = OccupationLevelsParser.review(log);
        assertTrue(result.isSuccess(), result.toString());
        OccupationReview review = result.getValue().orElseThrow();
        assertEquals(36, review.getLinesScanned(),
                "35 content lines plus the trailing-empty split element");
        assertEquals(4, review.getPairCount());
        assertEquals(0, review.getSingleCount(),
                "a pair step must never triple-count as a single line");
        assertEquals(12, review.getOccurrences().get(0).getLineNumber());
        assertEquals(30, review.getOccurrences().get(3).getLineNumber());
        assertEquals(-18.72, review.getOccurrences().get(0).getHomoEv(), 1e-12);
        assertEquals(-9.41, review.getOccurrences().get(0).getLumoEv(), 1e-12);
        assertEquals(9.31, review.getOccurrences().get(0).getGapEv(), 1e-9);
        assertEquals(9.34, review.getOccurrences().get(3).getGapEv(), 1e-9,
                "every SCF step's pair stands - no silent last-wins crowning");
        assertTrue(Files.readString(log).contains("nspin"),
                "fixture is genuinely spin-polarized; attribution stays unguessed");
    }

    @Test
    void partialOccupationFixtureLeavesBothGapsUndefined() throws IOException {
        // tests/fixtures/qe/scf_partial_occupation.log - the partial-
        // occupation (alkali-like, smearing) class: QE prints only the
        // single-value "highest occupied level (ev)" needle, so both gaps
        // remain UNDEFINED - no LUMO was printed and none is invented.
        Path log = Path.of("tests/fixtures/qe/scf_partial_occupation.log");
        OperationResult<OccupationReview> result = OccupationLevelsParser.review(log);
        assertTrue(result.isSuccess(), result.toString());
        OccupationReview review = result.getValue().orElseThrow();
        assertEquals(21, review.getLinesScanned());
        assertEquals(0, review.getPairCount());
        assertEquals(2, review.getSingleCount());
        assertEquals(10, review.getOccurrences().get(0).getLineNumber());
        assertEquals(15, review.getOccurrences().get(1).getLineNumber());
        assertEquals(-1.25, review.getOccurrences().get(0).getHomoEv(), 1e-12);
        assertEquals(-1.24, review.getOccurrences().get(1).getHomoEv(), 1e-12);
        assertTrue(Double.isNaN(review.getOccurrences().get(0).getGapEv()),
                "smearing print carries no LUMO - the gap stays UNDEFINED");
        assertTrue(Double.isNaN(review.getOccurrences().get(1).getGapEv()));
        assertTrue(Files.readString(log).contains("Fermi energy"),
                "metal class marker stands next to the undefined gap");
    }
}
