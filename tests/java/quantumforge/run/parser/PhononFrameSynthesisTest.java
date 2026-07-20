/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * PhononFrameSynthesis phase-sampling contract (Roadmap #52 data layer): exact
 * displaced positions for a known mode, exact rejection of every invalid input,
 * and an exact frame-document structure.
 */
class PhononFrameSynthesisTest {

    private static OperationResult<String> oneAtom(double amplitude, int frames) {
        return PhononFrameSynthesis.frames(
                new double[][] {{0.6, 0.8, 0.0}},
                new double[][] {{1.0, 2.0, 3.0}},
                new String[] {"Si"},
                amplitude, frames, 100.0, 1);
    }

    @Test
    void synthesizesExactPhaseSampledPositions() {
        OperationResult<String> result = oneAtom(0.2, 4);
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("FRAMES_OK", result.getCode());
        String document = result.getValue().orElseThrow();
        String[] lines = document.split("\n");
        // Each frame: atom-count line, comment line, then one atom row.
        assertEquals(12, lines.length, "4 frames x (count + comment + 1 atom) lines");
        assertEquals("1", lines[0]);
        assertTrue(lines[1].startsWith("frame 1/4 phase=sin(2*pi*0/4)=+0.000000 mode=1"),
                lines[1]);
        assertTrue(lines[1].contains("omega=100.000000 cm-1"), lines[1]);
        assertTrue(lines[1].contains("amplitude=0.200000 A"), lines[1]);
        // Frame 1: phase 0 -> base position exactly.
        assertTrue(lines[2].startsWith("Si") && lines[2].contains("  1.00000000")
                && lines[2].contains("  2.00000000") && lines[2].contains("  3.00000000"),
                lines[2]);
        assertTrue(lines[4].startsWith("frame 2/4 phase=sin(2*pi*1/4)=+1.000000"),
                lines[4]);
        // Frame 2: phase +1 -> base + 0.2 * (0.6, 0.8, 0) = (1.12, 2.16, 3.0).
        assertTrue(lines[5].contains("  1.12000000"), lines[5]);
        assertTrue(lines[5].contains("  2.16000000"), lines[5]);
        assertTrue(lines[5].contains("  3.00000000"), lines[5]);
        // Frame 3: phase sin(pi) ~ 0 at the printed precision -> base position.
        assertTrue(lines[8].contains("  1.00000000"), lines[8]);
        // Frame 4: phase -1 -> (0.88, 1.84, 3.0).
        assertTrue(lines[11].contains("  0.88000000"), lines[11]);
        assertTrue(lines[11].contains("  1.84000000"), lines[11]);
        assertTrue(lines[11].contains("  3.00000000"), lines[11]);
    }

    @Test
    void rejectsOutOfRangeAmplitude() {
        OperationResult<String> zero = oneAtom(0.0, 4);
        assertFalse(zero.isSuccess());
        assertEquals("FRAMES_AMPLITUDE", zero.getCode());
        OperationResult<String> tooLarge =
                oneAtom(PhononFrameSynthesis.MAX_AMPLITUDE_ANG + 0.1, 4);
        assertFalse(tooLarge.isSuccess());
        assertEquals("FRAMES_AMPLITUDE", tooLarge.getCode());
        OperationResult<String> nan = oneAtom(Double.NaN, 4);
        assertFalse(nan.isSuccess());
    }

    @Test
    void rejectsOutOfRangeFrameCount() {
        OperationResult<String> tooFew = oneAtom(0.2, PhononFrameSynthesis.MIN_FRAMES - 1);
        assertFalse(tooFew.isSuccess());
        assertEquals("FRAMES_COUNT", tooFew.getCode());
        OperationResult<String> tooMany = oneAtom(0.2, PhononFrameSynthesis.MAX_FRAMES + 1);
        assertFalse(tooMany.isSuccess());
        assertEquals("FRAMES_COUNT", tooMany.getCode());
    }

    @Test
    void rejectsShapeAndValueProblems() {
        OperationResult<String> shape = PhononFrameSynthesis.frames(
                new double[][] {{0.6, 0.8, 0.0}, {0.0, 0.0, 1.0}},
                new double[][] {{1.0, 2.0, 3.0}},
                new String[] {"Si"},
                0.2, 4, 100.0, 1);
        assertFalse(shape.isSuccess());
        assertEquals("FRAMES_SHAPE", shape.getCode());

        OperationResult<String> shortRow = PhononFrameSynthesis.frames(
                new double[][] {{0.6, 0.8}},
                new double[][] {{1.0, 2.0, 3.0}},
                new String[] {"Si"},
                0.2, 4, 100.0, 1);
        assertFalse(shortRow.isSuccess());
        assertEquals("FRAMES_SHAPE", shortRow.getCode());

        OperationResult<String> nonFinite = PhononFrameSynthesis.frames(
                new double[][] {{0.6, 0.8, 0.0}},
                new double[][] {{1.0, Double.POSITIVE_INFINITY, 3.0}},
                new String[] {"Si"},
                0.2, 4, 100.0, 1);
        assertFalse(nonFinite.isSuccess());
        assertEquals("FRAMES_VALUE", nonFinite.getCode());

        OperationResult<String> badFrequency = PhononFrameSynthesis.frames(
                new double[][] {{0.6, 0.8, 0.0}},
                new double[][] {{1.0, 2.0, 3.0}},
                new String[] {"Si"},
                0.2, 4, Double.NaN, 1);
        assertFalse(badFrequency.isSuccess());
        assertEquals("FRAMES_FREQUENCY", badFrequency.getCode());
    }
}
