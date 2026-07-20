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
import quantumforge.run.parser.BandsFermiReviewMath.BandsReview;

class BandsFermiReviewMathTest {

    @TempDir
    private Path tempDir;

    private Path writeBands() throws IOException {
        Path file = this.tempDir.resolve("si.bands.dat.gnu");
        Files.writeString(file,
                "0.0 -10.0\n0.5 -9.0\n1.0 -8.0\n"
                + "\n"
                + "0.0 -7.0\n0.5 -5.0\n1.0 -4.0\n"
                + "\n"
                + "0.0 0.0\n0.5 1.0\n1.0 2.0\n");
        return file;
    }

    @Test
    void explicitFermiShiftReviewsBandsExactly() throws IOException {
        OperationResult<BandsReview> result = BandsFermiReviewMath.review(
                writeBands(), -6.0);
        assertTrue(result.isSuccess(), result.toString());
        BandsReview review = result.getValue().orElseThrow();
        assertEquals(-6.0, review.getFermiEv(), 1e-12,
                "the analyst reference is echoed, never adjusted");
        assertEquals(3, review.getBandCount());
        assertEquals(9, review.getTotalPoints());
        assertEquals(0.0, review.getKMin(), 1e-12);
        assertEquals(1.0, review.getKMax(), 1e-12);
        assertEquals(1, review.getCrossingCount(), "band 2 straddles E_F");
        assertEquals(-1.0, review.getVbmSideEv(), 1e-12,
                "max E-E_F <= 0 across ALL points incl. the crossing band");
        assertEquals(1.0, review.getCbmSideEv(), 1e-12);
        assertEquals(2.0, review.getNaiveGapEv(), 1e-12, "point-sampled only");
        assertEquals(-4.0, review.getPerBand().get(0).getMinShiftedEv(), 1e-12);
        assertEquals(-2.0, review.getPerBand().get(0).getMaxShiftedEv(), 1e-12);
        assertEquals(-1.0, review.getPerBand().get(1).getMinShiftedEv(), 1e-12);
        assertEquals(2.0, review.getPerBand().get(1).getMaxShiftedEv(), 1e-12);
        assertTrue(review.getPerBand().get(1).crossesZero());
        assertFalse(review.getPerBand().get(2).crossesZero());
        assertEquals(3, review.getPerBand().get(2).getPoints());
        assertEquals(0, review.getDiagnosticsTotal());
        assertEquals("BANDS_REVIEW_OK", result.getCode());
    }

    @Test
    void skippedRowsAreSurfacedNotHidden() throws IOException {
        Path file = this.tempDir.resolve("messy.bands.dat.gnu");
        Files.writeString(file, "0.0 -10.0\nnot_a_row at all\n0.5 -9.0\n\n0.0 -1.0\n");
        OperationResult<BandsReview> result = BandsFermiReviewMath.review(file, 0.0);
        assertTrue(result.isSuccess(), result.toString());
        BandsReview review = result.getValue().orElseThrow();
        assertEquals(2, review.getBandCount(), "blank line still splits bands");
        assertEquals(1, review.getDiagnosticsTotal(),
                "the malformed row is counted, not hidden");
        assertEquals(1, review.getDiagnostics().size());
        assertTrue(review.getDiagnostics().get(0).contains("malformed"),
                review.getDiagnostics().get(0));
    }

    @Test
    void refusesWithoutFermiOrBands() throws IOException {
        Path file = writeBands();
        OperationResult<BandsReview> noFermi = BandsFermiReviewMath.review(
                file, Double.NaN);
        assertFalse(noFermi.isSuccess());
        assertEquals("BANDS_REVIEW_FERMI", noFermi.getCode());

        OperationResult<BandsReview> missing = BandsFermiReviewMath.review(
                this.tempDir.resolve("absent.dat.gnu"), 0.0);
        assertFalse(missing.isSuccess());
        assertEquals("BANDS_REVIEW_IO", missing.getCode());

        Path empty = this.tempDir.resolve("empty.dat.gnu");
        Files.writeString(empty, "# only a comment\n\n  \n");
        OperationResult<BandsReview> noBands = BandsFermiReviewMath.review(empty, 0.0);
        assertFalse(noBands.isSuccess());
        assertEquals("BANDS_REVIEW_EMPTY", noBands.getCode());
    }
}
