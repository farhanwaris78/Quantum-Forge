package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ScfConvergenceAnalyzerTest {

    @Test
    void parsesConvergedSiliconFixture() throws Exception {
        String log = Files.readString(
                Path.of("tests/fixtures/qe/scf_si_converged.log"), StandardCharsets.UTF_8);
        ScfConvergenceAnalyzer.Report report = ScfConvergenceAnalyzer.analyze(log);
        assertTrue(report.isConverged());
        assertFalse(report.isExplicitlyNotConverged());
        assertEquals(4, report.getIterations().size());
        assertTrue(report.getIterations().get(3).isFinalConvergedLine());
        assertEquals(-15.85245678, report.getFinalEnergyRy(), 1.0e-8);
        assertEquals(ScfConvergenceAnalyzer.Trend.DECREASING_ERROR, report.getTrend());
    }

    @Test
    void detectsExplicitNonConvergenceAndDivergence() throws Exception {
        String log = Files.readString(
                Path.of("tests/fixtures/qe/scf_not_converged.log"), StandardCharsets.UTF_8);
        ScfConvergenceAnalyzer.Report report = ScfConvergenceAnalyzer.analyze(log);
        assertFalse(report.isConverged());
        assertTrue(report.isExplicitlyNotConverged());
        assertTrue(report.getTrend() == ScfConvergenceAnalyzer.Trend.DIVERGING
                || report.getTrend() == ScfConvergenceAnalyzer.Trend.OSCILLATING
                || report.getTrend() == ScfConvergenceAnalyzer.Trend.UNKNOWN);
    }

    @Test
    void parsesFortranDExponents() {
        assertEquals(1.2e-3, ScfConvergenceAnalyzer.parseFortranDouble("1.2D-3"), 1.0e-15);
    }
}
