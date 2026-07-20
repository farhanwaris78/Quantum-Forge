/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.neural.CompositionalBaselineMath.BaselineReport;
import quantumforge.neural.CompositionalBaselineMath.ScanResult;
import quantumforge.operation.OperationResult;

class CompositionalBaselineMathTest {

    /**
     * Exact-fit dataset: E = 1.0 - 2.0*nH - 5.0*nO. Frame 3 pins the Fortran
     * D-exponent conversion; frame 4 pins the free_energy fallback.
     */
    private static final String EXACT = """
            2
            Properties=species:S:1:pos:R:3 energy=-3.0
            H 0.0 0.0 0.0
            H 0.0 0.0 0.74
            3
            energy=-8.0
            H 0.0 0.0 0.0
            O 0.757 0.0 0.0
            H 0.0 0.586 0.0
            2
            energy=-9.0D0
            O 0.0 0.0 0.0
            O 0.0 0.0 1.21
            4
            free_energy=-13.0
            H 0.0 0.0 0.0
            H 0.0 0.0 0.74
            O 1.0 0.0 0.0
            O 1.0 0.0 1.21
            5
            energy=-12.0
            H 0.0 0.0 0.0
            H 0.0 0.0 0.74
            H 1.0 0.0 0.0
            H 1.0 0.0 0.74
            O 0.5 0.5 0.5
            """;

    @Test
    void exactFitRecoversTheCoefficients() {
        OperationResult<ScanResult> scan = CompositionalBaselineMath.scanText(EXACT);
        assertTrue(scan.isSuccess(), scan.toString());
        OperationResult<BaselineReport> fit =
                CompositionalBaselineMath.fit(scan.getValue().orElseThrow());
        assertTrue(fit.isSuccess(), fit.toString());
        BaselineReport report = fit.getValue().orElseThrow();
        assertEquals(java.util.List.of("H", "O"), report.getSpecies());
        assertEquals(1.0, report.getInterceptEv(), 1e-9);
        assertEquals(2, report.getCoefficientsEv().size());
        assertEquals(-2.0, report.getCoefficientsEv().get(0), 1e-9,
                "eV per H atom on THIS dataset");
        assertEquals(-5.0, report.getCoefficientsEv().get(1), 1e-9);
        assertEquals(5, report.getFramesUsed());
        assertEquals(0, report.getFramesSkippedNoEnergy());
        assertTrue(report.getRmsEv() < 1e-9, "exact family fits exactly: "
                + report.getRmsEv());
        assertTrue(report.getMaxAbsEv() < 1e-9);
        assertEquals(5, report.getOutliers().size(), "all frames listed when few");
    }

    @Test
    void noisyLabelBecomesTheTopOutlier() {
        String noisy = EXACT.replace("energy=-8.0", "energy=-8.5");
        OperationResult<BaselineReport> fit = CompositionalBaselineMath.fit(
                CompositionalBaselineMath.scanText(noisy).getValue().orElseThrow());
        assertTrue(fit.isSuccess(), fit.toString());
        BaselineReport report = fit.getValue().orElseThrow();
        assertEquals(0.8260869565217419, report.getInterceptEv(), 1e-9);
        assertEquals(0.19781414201873582, report.getRmsEv(), 1e-9,
                "verified against the normal-equations reference");
        assertEquals(2, report.getOutliers().get(0).getFrame(),
                "the mislabeled frame 2 is the largest residual");
        assertEquals(-0.391304347826087, report.getOutliers().get(0).getResidualEv(),
                1e-9);
        assertEquals(-8.5, report.getOutliers().get(0).getActualEv(), 1e-12);
    }

    @Test
    void unlabeledFramesAreExcludedNotGuessed() {
        String withSkip = EXACT + """
                1
                Properties=species:S:1:pos:R:3
                H 0.0 0.0 0.0
                3
                energy=not_a_number
                H 0.0 0.0 0.0
                O 0.757 0.0 0.0
                H 0.0 0.586 0.0
                """;
        OperationResult<BaselineReport> fit = CompositionalBaselineMath.fit(
                CompositionalBaselineMath.scanText(withSkip).getValue().orElseThrow());
        assertTrue(fit.isSuccess(), fit.toString());
        BaselineReport report = fit.getValue().orElseThrow();
        assertEquals(5, report.getFramesUsed(), "same labeled five as EXACT");
        assertEquals(2, report.getFramesSkippedNoEnergy(),
                "missing label plus unparseable label both excluded, counted");
        assertEquals(1.0, report.getInterceptEv(), 1e-9);
    }

    @Test
    void degenerateAndUnderdeterminedDatasetsFailClosed() {
        String sameComposition = """
                2
                energy=-3.0
                H 0.0 0.0 0.0
                H 0.0 0.0 0.74
                2
                energy=-3.1
                H 0.0 0.0 0.0
                H 0.0 0.0 0.75
                2
                energy=-2.9
                H 0.0 0.0 0.0
                H 0.0 0.0 0.73
                """;
        OperationResult<BaselineReport> degenerate = CompositionalBaselineMath.fit(
                CompositionalBaselineMath.scanText(sameComposition)
                        .getValue().orElseThrow());
        assertFalse(degenerate.isSuccess());
        assertEquals("BASELINE_DEGENERATE", degenerate.getCode(),
                "one composition only => no compositional information");

        String twoFrames = """
                2
                energy=-3.0
                H 0.0 0.0 0.0
                H 0.0 0.0 0.74
                2
                energy=-9.0
                O 0.0 0.0 0.0
                O 0.0 0.0 1.21
                """;
        OperationResult<ScanResult> scan =
                CompositionalBaselineMath.scanText(twoFrames);
        assertTrue(scan.isSuccess(), scan.toString());
        OperationResult<BaselineReport> under = CompositionalBaselineMath.fit(
                scan.getValue().orElseThrow());
        assertFalse(under.isSuccess());
        assertEquals("BASELINE_UNDERDETERMINED", under.getCode());

        OperationResult<ScanResult> none = CompositionalBaselineMath.scanText("""
                1
                Properties=species:S:1:pos:R:3
                H 0.0 0.0 0.0
                """);
        assertFalse(none.isSuccess());
        assertEquals("BASELINE_ENERGY", none.getCode(),
                "no labels => nothing fitted, nothing invented");

        OperationResult<ScanResult> badRow = CompositionalBaselineMath.scanText("""
                1
                energy=-1.0
                H 0.0 0.0
                """);
        assertFalse(badRow.isSuccess());
        assertEquals("BASELINE_SYNTAX", badRow.getCode());

        OperationResult<ScanResult> blank = CompositionalBaselineMath.scanText("  \n");
        assertFalse(blank.isSuccess());
        assertEquals("BASELINE_EMPTY", blank.getCode());
    }
}
