package quantumforge.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.app.project.viewer.result.convergence.QEFXConvergenceViewerController;
import quantumforge.app.project.viewer.result.convergence.QEFXConvergenceViewerController.ConvData;

class ProjectConvergenceTest {

    @Test
    void testEcutwfcConvergenceDecaysExponentially() {
        ConvData data = QEFXConvergenceViewerController.generateSimulatedConvergence("ecutwfc");
        assertNotNull(data);
        assertEquals("ecutwfc", data.parameter);
        assertTrue(data.points.size() >= 5);

        // Kinetic energy cutoff convergence must show monotonic increase of total energy
        // (decay of absolute truncation error) towards a flat asymptote.
        double firstEnergy = data.points.get(0).y;
        double lastEnergy = data.points.get(data.points.size() - 1).y;
        assertTrue(lastEnergy > firstEnergy, "Energy must converge upwards as plane-wave basis set completes");

        // Verify strictly decreasing steps (exponential flattening)
        // Cutoffs are not uniformly spaced (last step 50->60 is 10 Ry vs 5 Ry), so check per-Ry slope.
        double prevSlope = Double.MAX_VALUE;
        for (int i = 1; i < data.points.size(); i++) {
            double diff = data.points.get(i).y - data.points.get(i - 1).y;
            double dx = data.points.get(i).x - data.points.get(i - 1).x;
            assertTrue(diff > 0, "Truncational energy corrections must remain positive at i=" + i);
            double slope = diff / dx;
            assertTrue(slope > 0, "Slope must stay positive at i=" + i);
            assertTrue(slope < prevSlope, "Basis completion rate per Ry must decelerate, slope=" + slope + " prev=" + prevSlope + " at i=" + i);
            prevSlope = slope;
        }
    }

    @Test
    void testKpointsConvergenceOscillates() {
        ConvData data = QEFXConvergenceViewerController.generateSimulatedConvergence("k-points");
        assertNotNull(data);
        assertEquals("k-points", data.parameter);
        assertTrue(data.points.size() >= 3);

        // Brillouin zone integration over discrete Monkhorst-Pack grids 
        // typically exhibits oscillatory convergence depending on nesting and metal/insulator behavior.
        boolean oscillating = false;
        double firstDiff = data.points.get(1).y - data.points.get(0).y;
        for (int i = 2; i < data.points.size(); i++) {
            double diff = data.points.get(i).y - data.points.get(i - 1).y;
            if ((firstDiff > 0 && diff < 0) || (firstDiff < 0 && diff > 0)) {
                oscillating = true;
                break;
            }
        }
        assertTrue(oscillating, "Monkhorst-Pack grid sweeps must show oscillatory convergence");
    }

    @Test
    void testDegaussConvergenceIsSmooth() {
        ConvData data = QEFXConvergenceViewerController.generateSimulatedConvergence("degauss");
        assertNotNull(data);
        assertEquals("degauss", data.parameter);
        assertTrue(data.points.size() >= 4);

        // Free energy smearing dependence should be smooth and strictly parabolic/quadratic
        // as degauss (sigma) approaches zero.
        double prevVal = data.points.get(0).y;
        for (int i = 1; i < data.points.size(); i++) {
            double val = data.points.get(i).y;
            assertTrue(val > prevVal, "Free energy must increase smoothly with smearing width sigma");
            prevVal = val;
        }
    }
}
