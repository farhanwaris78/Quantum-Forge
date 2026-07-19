package quantumforge.app.project.viewer.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DiffusivityCalculatorTest {

    @Test
    void perfectLinearMsdYieldsEinsteinSlopeOverTwoD() {
        // msd(t) = 6*t (Ang^2 vs ps); in 3D D = slope/(2*3) = 1 Ang^2/ps exactly.
        double[] time = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] msd = {6.0, 12.0, 18.0, 24.0, 30.0};
        DiffusivityCalculator.DiffusionFit fit =
                DiffusivityCalculator.fit(msd, time, 3, 0, time.length);
        assertEquals(6.0, fit.getSlope(), 1.0e-12);
        assertEquals(1.0, fit.getDiffusivity(), 1.0e-12);
        assertEquals(0.0, fit.getIntercept(), 1.0e-12);
        assertEquals(1.0, fit.getRSquared(), 1.0e-12);
        assertEquals(0.0, fit.getDiffusivityStandardError(), 1.0e-12);
        assertEquals(1.0, DiffusivityCalculator.calculateD(msd, time, 3), 1.0e-12);

        // The same series in 1D means D = slope/2 = 3.
        assertEquals(3.0, DiffusivityCalculator.calculateD(msd, time, 1), 1.0e-12);
    }

    @Test
    void windowedFitIgnoresOutOfWindowSamples() {
        double[] time = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        double[] msd = {0.0, 0.0, 10.0, 16.0, 22.0, 999.0};
        DiffusivityCalculator.DiffusionFit fit =
                DiffusivityCalculator.fit(msd, time, 3, 2, 5);
        assertEquals(6.0, fit.getSlope(), 1.0e-12);
        assertEquals(2, fit.getStartIndex());
        assertEquals(5, fit.getEndIndexExclusive());
    }

    @Test
    void rejectsIllposedInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> DiffusivityCalculator.calculateD(new double[] {1.0}, new double[] {1.0, 2.0}, 3));
        assertThrows(IllegalArgumentException.class,
                () -> DiffusivityCalculator.calculateD(
                        new double[] {1.0, 2.0, 3.0}, new double[] {1.0, 2.0, 3.0}, 4));
        assertThrows(IllegalArgumentException.class,
                () -> DiffusivityCalculator.fit(new double[] {1.0, 2.0}, new double[] {1.0, 2.0},
                        3, 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> DiffusivityCalculator.calculateD(
                        new double[] {1.0, 2.0, 3.0}, new double[] {1.0, 3.0, 2.0}, 3));
        assertThrows(IllegalArgumentException.class,
                () -> DiffusivityCalculator.calculateD(
                        new double[] {1.0, Double.NaN, 3.0}, new double[] {1.0, 2.0, 3.0}, 3));
    }
}
