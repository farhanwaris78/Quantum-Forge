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
import quantumforge.run.parser.SeriesAlignmentMath.AlignedComparison;

class SeriesAlignmentMathTest {

    @TempDir
    private Path tempDir;

    private Path writeCsv() throws IOException {
        Path csv = this.tempDir.resolve("two-series-align.csv");
        Files.writeString(csv, "parameter,e1,e2\n"
                + "0.0,-10.0,-5.0\n"
                + "0.5,-11.0,-5.5\n"
                + "1.0,-12.0,-6.0\n");
        return csv;
    }

    @Test
    void explicitAlignmentAppliesTheStatedShift() throws IOException {
        OperationResult<AlignedComparison> result = SeriesAlignmentMath.align(
                writeCsv(), "vbm", -10.5, -5.4, Double.NaN);
        assertTrue(result.isSuccess(), result.toString());
        AlignedComparison aligned = result.getValue().orElseThrow();
        assertEquals("VBM", aligned.getMode(), "mode normalized to upper case");
        assertEquals(5.1, aligned.getReferenceShiftEv(), 1e-12,
                "ref2 - ref1 is the EXPLICIT shift difference");
        assertTrue(aligned.isLoudShift(), "5.1 eV exceeds the 1 eV loud flag");
        assertEquals(3, aligned.getRowCount());
        assertEquals(0.5715476066494083, aligned.getRmsEv(), 1e-9,
                "Python-verified aligned-delta RMS");
        assertEquals(0.4000000000000001, aligned.getMeanEv(), 1e-12);
        assertEquals(0.9, aligned.getMaxAbsEv(), 1e-9);
        assertEquals(1.0, aligned.getMaxAbsAtParameter(), 1e-12);
        double[] first = aligned.getRows().get(0);
        assertEquals(0.0, first[0], 1e-12);
        assertEquals(0.5, first[1], 1e-12, "-10.0 - (-10.5)");
        assertEquals(0.4, first[2], 1e-12, "-5.0 - (-5.4)");
        assertEquals(-0.1, first[3], 1e-12, "aligned delta = raw delta - shift");
    }

    @Test
    void quietShiftAndUserLandingPointKeepDeltasIdentical() throws IOException {
        Path csv = writeCsv();
        OperationResult<AlignedComparison> quiet = SeriesAlignmentMath.align(
                csv, "fermi", -10.0, -9.8, Double.NaN);
        assertTrue(quiet.isSuccess(), quiet.toString());
        AlignedComparison quietResult = quiet.getValue().orElseThrow();
        assertEquals(0.2, quietResult.getReferenceShiftEv(), 1e-12);
        assertFalse(quietResult.isLoudShift());
        assertEquals(5.315700016617441, quietResult.getRmsEv(), 1e-9,
                "Python-verified RMS for the 0.2 eV shift");
        assertEquals(4.8, quietResult.getRows().get(0)[3], 1e-12);

        OperationResult<AlignedComparison> user = SeriesAlignmentMath.align(
                csv, "user", -10.0, -9.8, 2.0);
        assertTrue(user.isSuccess(), user.toString());
        AlignedComparison userResult = user.getValue().orElseThrow();
        assertEquals("USER", userResult.getMode());
        assertEquals(2.0, userResult.getTargetEv(), 1e-12);
        assertEquals(2.0, userResult.getRows().get(0)[1], 1e-12,
                "reference 1 lands exactly at the USER target");
        assertEquals(4.8, userResult.getRows().get(0)[3], 1e-12,
                "aligned DELTAS identical across modes - the target cancels");
        assertEquals(quietResult.getRmsEv(), userResult.getRmsEv(), 1e-12);
    }

    @Test
    void unknownModesAndMissingReferencesFailClosed() throws IOException {
        Path csv = writeCsv();
        OperationResult<AlignedComparison> badMode = SeriesAlignmentMath.align(
                csv, "boltzmann", -10.0, -5.0, Double.NaN);
        assertFalse(badMode.isSuccess());
        assertEquals("ALIGN_MODE", badMode.getCode(),
                "unknown modes never become silent guesses");

        OperationResult<AlignedComparison> noRef = SeriesAlignmentMath.align(
                csv, "fermi", Double.NaN, -5.0, Double.NaN);
        assertFalse(noRef.isSuccess());
        assertEquals("ALIGN_VALUE", noRef.getCode(),
                "references are analyst input, never inferred");

        OperationResult<AlignedComparison> noTarget = SeriesAlignmentMath.align(
                csv, "user", -10.0, -5.0, Double.NaN);
        assertFalse(noTarget.isSuccess());
        assertEquals("ALIGN_VALUE", noTarget.getCode(),
                "USER mode without a target is incomplete, not defaulted");

        OperationResult<AlignedComparison> blankMode = SeriesAlignmentMath.align(
                csv, "  ", -10.0, -5.0, Double.NaN);
        assertFalse(blankMode.isSuccess());
        assertEquals("ALIGN_MODE", blankMode.getCode());
    }
}
