package quantumforge.app.project.editor.result.phonon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import quantumforge.app.project.editor.result.phonon.PhononAnalyzer.PhononMode;

class PhononAnalyzerTest {

    @Test
    void testPhononModeAnimationWithComplexEigenvectorsAndPhaseShift() {
        // Mode index 1, frequency 150 cm-1, type optical
        PhononMode mode = new PhononMode(1, 150.0, PhononAnalyzer.MODE_OPTICAL);

        // Atom at (0,0,0)
        double[] positions = {0.0, 0.0, 0.0};
        String[] labels = {"Si"};
        mode.setAtomPositions(positions, labels);

        // Real eigenvector: [1.0, 0.0, 0.0]
        double[][] real = {{1.0, 0.0, 0.0}};
        mode.setDisplacementVectors(real);

        // Imaginary eigenvector: [0.0, 1.0, 0.0] (Represents a 90-degree phase difference between x and y vibrations!)
        double[][] imag = {{0.0, 1.0, 0.0}};
        mode.setDisplacementVectorsImag(imag);

        // Non-Gamma q-vector along x: [pi/2, 0, 0]
        double[] q = {Math.PI / 2.0, 0.0, 0.0};
        mode.setQVector(q);

        // 1. Time = 0, translation R = (0,0,0) -> phase = 0
        // u = real * cos(0) - imag * sin(0) = [1, 0, 0] * 1 - 0 = [1, 0, 0]
        double[] pos1 = mode.getAnimatedPositionComplex(0, 0.0, 2.0, new double[]{0.0, 0.0, 0.0});
        assertEquals(2.0, pos1[0], 1e-6); // x shift = amplitude * 1.0 = 2.0
        assertEquals(0.0, pos1[1], 1e-6); // y shift = 0.0

        // 2. Time = 0.25 (wt = pi/2), translation R = (0,0,0) -> phase = -pi/2
        // u = real * cos(-pi/2) - imag * sin(-pi/2) = [1,0,0] * 0 - [0,1,0] * (-1) = [0, 1, 0]
        double[] pos2 = mode.getAnimatedPositionComplex(0, 0.25, 2.0, new double[]{0.0, 0.0, 0.0});
        assertEquals(0.0, pos2[0], 1e-6);
        assertEquals(2.0, pos2[1], 1e-6); // y shift = amplitude * 1.0 = 2.0

        // 3. Time = 0, translation R = (1.0, 0, 0) -> phase = q.R = pi/2
        // u = real * cos(pi/2) - imag * sin(pi/2) = [1,0,0] * 0 - [0,1,0] * 1 = [0, -1, 0]
        double[] pos3 = mode.getAnimatedPositionComplex(0, 0.0, 2.0, new double[]{1.0, 0.0, 0.0});
        assertEquals(1.0, pos3[0], 1e-6); // base translation x = 1.0, u_x = 0
        assertEquals(-2.0, pos3[1], 1e-6); // u_y = amplitude * (-1.0) = -2.0
    }
}
