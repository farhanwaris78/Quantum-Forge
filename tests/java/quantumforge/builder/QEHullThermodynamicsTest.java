package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.builder.QEHullThermodynamics.StabilityResult;

class QEHullThermodynamicsTest {

    @Test
    void testConvexHullThermodynamicsAndTieLineInterpolation() {
        QEHullThermodynamics hull = new QEHullThermodynamics();

        // Binary system A-B:
        // Pure Element A: x=0.0, E = 0.0 eV/atom
        hull.addPhase("A", 0.0, 0.0);
        // Pure Element B: x=1.0, E = 0.0 eV/atom
        hull.addPhase("B", 1.0, 0.0);

        // Competing stable phase: AB2 at x=2/3, E = -1.50 eV/atom (highly stable, on the hull!)
        hull.addPhase("AB2", 2.0 / 3.0, -1.50);

        // 1. Evaluate stability of a target on the hull (AB2 at x=2/3, E = -1.50)
        StabilityResult resStable = hull.evaluateStability(2.0 / 3.0, -1.50);
        assertNotNull(resStable);
        assertTrue(resStable.isStable());
        assertEquals(0.0, resStable.getDistanceToHullEv(), 1e-6);

        // 2. Evaluate stability of a metastable phase: AB at x=0.50, E = -0.50 eV/atom.
        // The stable tie-line connects A (0.0, 0.0) and AB2 (x=2/3, -1.50).
        // At x = 0.50, the interpolated hull energy is:
        // E_hull = 0.0 + (0.50 - 0.0) * (-1.50 - 0.0) / (2/3 - 0.0) = -1.125 eV/atom.
        // Since target E is -0.50, it is above the hull by: -0.50 - (-1.125) = +0.625 eV/atom!
        StabilityResult resMetastable = hull.evaluateStability(0.50, -0.50);
        assertNotNull(resMetastable);
        assertFalse(resMetastable.isStable());
        assertEquals(0.625, resMetastable.getDistanceToHullEv(), 1e-6);

        // Decomposition products should be a mix of A and AB2:
        // Fraction of AB2 = (0.5 - 0.0) / (0.6667 - 0.0) = 75%
        // Fraction of A = 25%
        String summary = resMetastable.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("metastable"));
        assertTrue(resMetastable.getDecompositionProducts().contains("AB2"));
        assertTrue(resMetastable.getDecompositionProducts().contains("A"));
    }
}
