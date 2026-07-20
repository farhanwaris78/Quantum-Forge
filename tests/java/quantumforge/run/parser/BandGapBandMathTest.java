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
import quantumforge.run.parser.BandGapBandMath.GapClassification;
import quantumforge.run.parser.BandGapBandMath.Verdict;

class BandGapBandMathTest {

    @TempDir
    private Path tempDir;

    private Path writeBands() throws IOException {
        Path file = this.tempDir.resolve("gap.bands.dat.gnu");
        Files.writeString(file,
                "0.0 -10.0\n0.5 -9.0\n1.0 -9.5\n"
                + "\n"
                + "0.0 -2.0\n0.5 -1.0\n1.0 -3.0\n"
                + "\n"
                + "0.0 2.5\n0.5 1.5\n1.0 1.0\n"
                + "\n"
                + "0.0 5.0\n0.5 6.0\n1.0 5.5\n");
        return file;
    }

    @Test
    void indirectGapClassifiesWithAuditableDefinitions() throws IOException {
        OperationResult<GapClassification> result = BandGapBandMath.classify(
                writeBands(), 2, 0.01, 1.0e-6);
        assertTrue(result.isSuccess(), result.toString());
        GapClassification gap = result.getValue().orElseThrow();
        assertEquals(-1.0, gap.getVbmEv(), 1e-12, "max over k of curve 2");
        assertEquals(0.5, gap.getVbmAtK(), 1e-12);
        assertEquals(1.0, gap.getCbmEv(), 1e-12, "min over k of curve 3");
        assertEquals(1.0, gap.getCbmAtK(), 1e-12);
        assertEquals(2.0, gap.getGapEv(), 1e-12);
        assertEquals(Verdict.GAP_INDIRECT, gap.getVerdict(),
                "|0.5 - 1.0| = 0.5 exceeds the k tolerance");
        assertEquals(2, gap.getValenceBands());
        assertEquals(4, gap.getBandCount());

        OperationResult<GapClassification> direct = BandGapBandMath.classify(
                writeBands(), 2, 0.01, 0.6);
        assertTrue(direct.isSuccess(), direct.toString());
        assertEquals(Verdict.GAP_DIRECT, direct.getValue().orElseThrow().getVerdict(),
                "a 0.6 k tolerance covers |0.5 - 1.0| = 0.5");
    }

    @Test
    void overlapAndToleranceDegeneracyAreNamedHonestly() throws IOException {
        Path metal = this.tempDir.resolve("metal.bands.dat.gnu");
        Files.writeString(metal,
                "0.0 -1.0\n0.5 0.5\n1.0 -0.5\n"
                + "\n"
                + "0.0 0.0\n0.5 1.0\n1.0 0.2\n");
        OperationResult<GapClassification> overlap = BandGapBandMath.classify(
                metal, 1, 0.01, 1.0e-6);
        assertTrue(overlap.isSuccess(), overlap.toString());
        assertEquals(Verdict.METALLIC_OVERLAP,
                overlap.getValue().orElseThrow().getVerdict(),
                "VBM 0.5 above CBM 0.0 beyond tolerance");
        assertEquals(-0.5, overlap.getValue().orElseThrow().getGapEv(), 1e-12);

        Path borderline = this.tempDir.resolve("borderline.bands.dat.gnu");
        Files.writeString(borderline,
                "0.0 -1.0\n0.5 -0.9\n1.0 -1.1\n"
                + "\n"
                + "0.0 -0.86\n0.5 0.0\n1.0 -0.84\n");
        OperationResult<GapClassification> degenerate = BandGapBandMath.classify(
                borderline, 1, 0.05, 1.0e-6);
        assertTrue(degenerate.isSuccess(), degenerate.toString());
        GapClassification within = degenerate.getValue().orElseThrow();
        assertEquals(Verdict.DEGENERATE_WITHIN_TOLERANCE, within.getVerdict(),
                "gap 0.04 inside the 0.05 eV tolerance: data cannot decide");
        assertEquals(0.04, within.getGapEv(), 1e-12);
    }

    @Test
    void refusesMissingValenceCountAndUnequalGrids() throws IOException {
        Path file = writeBands();
        OperationResult<GapClassification> noValence = BandGapBandMath.classify(
                file, 0, 0.01, 1.0e-6);
        assertFalse(noValence.isSuccess());
        assertEquals("GAPMATH_VALENCE", noValence.getCode());

        OperationResult<GapClassification> noConduction = BandGapBandMath.classify(
                file, 4, 0.01, 1.0e-6);
        assertFalse(noConduction.isSuccess());
        assertEquals("GAPMATH_VALENCE", noConduction.getCode(),
                "nV must leave at least one conduction band");

        Path ragged = this.tempDir.resolve("ragged.bands.dat.gnu");
        Files.writeString(ragged, "0.0 -1.0\n1.0 -0.5\n\n0.0 1.0\n");
        OperationResult<GapClassification> grid = BandGapBandMath.classify(
                ragged, 1, 0.01, 1.0e-6);
        assertFalse(grid.isSuccess());
        assertEquals("GAPMATH_GRID", grid.getCode(),
                "unequal k-grids are refused, not interpolated");

        OperationResult<GapClassification> badTol = BandGapBandMath.classify(
                file, 2, -0.1, 1.0e-6);
        assertFalse(badTol.isSuccess());
        assertEquals("GAPMATH_VALUE", badTol.getCode());

        OperationResult<GapClassification> badKTol = BandGapBandMath.classify(
                file, 2, 0.01, 0.0);
        assertFalse(badKTol.isSuccess());
        assertEquals("GAPMATH_VALUE", badKTol.getCode());

        OperationResult<GapClassification> missing = BandGapBandMath.classify(
                this.tempDir.resolve("absent.dat.gnu"), 2, 0.01, 1.0e-6);
        assertFalse(missing.isSuccess());
        assertEquals("GAPMATH_IO", missing.getCode());
    }
}
