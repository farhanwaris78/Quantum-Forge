package quantumforge.app.project.viewer.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import quantumforge.app.project.viewer.result.special.ExcitonAnalyzer;
import quantumforge.app.project.viewer.result.special.HyperfineMapper;
import quantumforge.app.project.viewer.result.special.PourbaixTool;
import quantumforge.app.project.viewer.result.special.StabilityAnalyzer;
import quantumforge.app.project.viewer.result.special.SuperconductingTcAnalyzer;
import quantumforge.app.project.viewer.result.special.WorkFunctionMapper;
import quantumforge.capability.ScientificFeatureUnavailableException;

class ScientificHelpersTest {
    @Test
    void diffusivityUsesRegressionAndReportsFitQuality() {
        double[] time = {0, 1, 2, 3, 4};
        double[] msd = {1, 7, 13, 19, 25}; // slope 6 => D=1 in three dimensions
        DiffusivityCalculator.DiffusionFit fit = DiffusivityCalculator.fit(msd, time, 3, 0, 5);
        assertEquals(1.0, fit.getDiffusivity(), 1.0e-12);
        assertEquals(1.0, fit.getRSquared(), 1.0e-12);
        assertEquals(0.0, fit.getSlopeStandardError(), 1.0e-12);
        assertThrows(IllegalArgumentException.class,
                () -> DiffusivityCalculator.fit(msd, time, 0, 0, 5));
    }

    @Test
    void explicitLowLevelModelsValidateTheirAssumptions() {
        assertEquals(0.0850355820187125,
                ExcitonAnalyzer.estimateWannierMottBindingEnergy(0.2, 0.2, 4.0), 1.0e-12);
        assertEquals(1.0, new WorkFunctionMapper(4.0, 5.0).getWorkFunction(), 1.0e-12);
        assertEquals(-1.0, StabilityAnalyzer.calculateFormationEnergy(-5.0,
                Map.of("Si", -2.0), Map.of("Si", 2)), 1.0e-12);
        assertThrows(IllegalArgumentException.class, () -> StabilityAnalyzer.calculateFormationEnergy(
                -5.0, Map.of(), Map.of("Si", 2)));

        double corrected = PourbaixTool.applyCheCorrection(0.0, 1, 0.5, 1, 7.0, 298.15);
        assertTrue(corrected < 0.0 && corrected > -0.2);
        assertTrue(SuperconductingTcAnalyzer.calculateMcMillanTc(1.0, 300.0, 0.1) > 0.0);
    }

    @Test
    void fabricatedAdvancedResultsNowFailClosed() {
        assertThrows(ScientificFeatureUnavailableException.class,
                () -> new WorkFunctionMapper(4.0, 5.0).generateMap(3, 3));
        // HyperfineMapper is fail-closed by contract: unknown isotopes and
        // non-finite inputs throw instead of returning a default 1.0/0.0 MHz.
        assertThrows(IllegalArgumentException.class,
                () -> HyperfineMapper.calculateAiso(1.0, "99Xx"));
        assertThrows(IllegalArgumentException.class,
                () -> HyperfineMapper.calculateAiso(Double.NaN, 1.0));
        assertTrue(Double.isNaN(HyperfineMapper.getNuclearGFactor("99Xx")),
                "An unknown isotope must surface NaN, not the old 1.0 fallback");
    }
}
