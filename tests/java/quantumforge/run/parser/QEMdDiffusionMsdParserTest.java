package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.com.math.Matrix3D;
import quantumforge.project.property.ProjectProperty;

class QEMdDiffusionMsdParserTest {

    @Test
    void testParserUnwrapsPeriodicCrossingsAndFitsMsdDiffusion() {
        // 10x10x10 cubic cell box
        double[][] lattice = Matrix3D.unit(10.0);
        ProjectProperty property = new ProjectProperty();
        QEMdDiffusionMsdParser parser = new QEMdDiffusionMsdParser(property, lattice);

        // Frame 0: Atom 1 is at (1.0, 0, 0)
        double[][] f0 = {{1.0, 0.0, 0.0}};
        // Frame 1: Atom 1 is at (5.0, 0, 0)
        double[][] f1 = {{5.0, 0.0, 0.0}};
        // Frame 2: Atom 1 is at (9.0, 0, 0)
        double[][] f2 = {{9.0, 0.0, 0.0}};
        // Frame 3: Atom 1 crosses the boundary! Wraps to (3.0, 0, 0)
        // Shift dx from 9.0 to 3.0 is -6.0 (which is > 5.0 (half box)).
        // So unwrapped coordinate must be 3.0 + 10.0 = 13.0 Angstroms!
        double[][] f3 = {{3.0, 0.0, 0.0}};

        parser.addFrame(f0);
        parser.addFrame(f1);
        parser.addFrame(f2);
        parser.addFrame(f3);

        assertEquals(4, parser.getWrappedTrajectory().size());

        // Unwrap trajectory
        parser.unwrapTrajectory();
        List<double[][]> unwrapped = parser.getUnwrappedTrajectory();
        assertNotNull(unwrapped);
        assertEquals(4, unwrapped.size());

        // Check unwrapped positions
        assertEquals(1.0, unwrapped.get(0)[0][0], 1e-6);
        assertEquals(5.0, unwrapped.get(1)[0][0], 1e-6);
        assertEquals(9.0, unwrapped.get(2)[0][0], 1e-6);
        assertEquals(13.0, unwrapped.get(3)[0][0], 1e-6, "Coordinate crossing must unwrap smoothly to 13.0 Angstroms");

        // Compute MSD values
        double[] msd = parser.computeMsd();
        assertNotNull(msd);
        assertEquals(4, msd.length);

        // MSD = sum(dx^2 + dy^2 + dz^2) / numAtoms
        // Step 0: dx = 0 -> MSD = 0
        assertEquals(0.0, msd[0], 1e-6);
        // Step 1: dx = 5.0 - 1.0 = 4.0 -> MSD = 16.0
        assertEquals(16.0, msd[1], 1e-6);
        // Step 2: dx = 9.0 - 1.0 = 8.0 -> MSD = 64.0
        assertEquals(64.0, msd[2], 1e-6);
        // Step 3: dx = 13.0 - 1.0 = 12.0 -> MSD = 144.0
        assertEquals(144.0, msd[3], 1e-6);
    }
}
