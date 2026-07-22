/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Pins {@link QEPhonopyForceConstants} against the upstream writer/reader
 * grammar (get_FORCE_CONSTANTS_lines / parse_FORCE_CONSTANTS @ 3a3e0f0):
 * '%4d %4d' dims (1-int also accepted square), '%d %d' block headers with
 * only the first token consumed, 3 rows x exactly 3 plain doubles.
 */
class QEPhonopyForceConstantsTest {

    private static String fc2x1() {
        return "   2    1\n"
                + "1 1\n"
                + "  1.000000000000000e+00  2.500000000000000e-01 -3.000000000000000e+00\n"
                + "  0.100000000000000e+00  0.200000000000000e+00  0.300000000000000e+00\n"
                + "  4.000000000000000e+00  5.000000000000000e+00  6.000000000000000e+00\n"
                + "2 1\n"
                + "  7.000000000000000e+00  8.000000000000000e+00  9.000000000000000e+00\n"
                + "  1.100000000000000e+00  1.200000000000000e+00  1.300000000000000e+00\n"
                + "  1.400000000000000e+00  1.500000000000000e+00  1.600000000000000e+00\n";
    }

    @Test
    void twoIntDimsFullParse() {
        OperationResult<QEPhonopyForceConstants.ForceConstants> result =
                QEPhonopyForceConstants.parseText(fc2x1(), "FORCE_CONSTANTS");
        assertTrue(result.isSuccess());
        assertEquals("PHONOPY_FC_OK", result.getCode());
        QEPhonopyForceConstants.ForceConstants fc = result.getValue().orElseThrow();
        assertEquals(2, fc.getDimI());
        assertEquals(1, fc.getDimJ());
        assertFalse(fc.isSingleIntHeader());
        assertEquals(2, fc.getCells().size());
        assertEquals(2, fc.getDistinctFirstIndices());
        assertEquals(1.0, fc.cellAt(0, 0)[0], 1e-15);
        assertEquals(-3.0, fc.cellAt(0, 0)[2], 1e-15);
        assertEquals(7.0, fc.cellAt(1, 0)[0], 1e-15);
        assertEquals(1.6, fc.cellAt(1, 0)[8], 1e-15);
        assertEquals(9.0, fc.getMaxAbsElement(), 1e-15);
        assertEquals(1, fc.getHeaders().get(0)[0]);
        assertEquals(1, fc.getHeaders().get(0)[1]);
        assertEquals(0, fc.getPartialBlocksHeld());
        // the p2s-map boundary is stated, never guessed
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("p2s_map")));
        String describe = QEPhonopyForceConstants.describe(fc);
        assertTrue(describe.contains("dims: 2 x 1"));
        assertTrue(describe.contains("max |element|: 9"));
    }

    @Test
    void singleIntDimsReadAsSquare() {
        // upstream: len(idx) == 1 -> idx = [idx[0], idx[0]]
        String text = "1\n1 1\n1 2 3\n4 5 6\n7 8 9\n";
        QEPhonopyForceConstants.ForceConstants fc =
                QEPhonopyForceConstants.parseText(text, "FC1").getValue().orElseThrow();
        assertTrue(fc.isSingleIntHeader());
        assertEquals(1, fc.getDimI());
        assertEquals(1, fc.getDimJ());
        assertEquals(9.0, fc.cellAt(0, 0)[8], 1e-15);
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("single-int")));
    }

    @Test
    void compactShapeDimsReportedVerbatim() {
        // (n_patom=1, n_satom=2): the compact layout; p2s_map lives elsewhere
        String text = "   1    2\n"
                + "1 1\n1 0 0\n0 1 0\n0 0 1\n"
                + "1 2\n2 0 0\n0 2 0\n0 0 2\n";
        QEPhonopyForceConstants.ForceConstants fc =
                QEPhonopyForceConstants.parseText(text, "FC").getValue().orElseThrow();
        assertEquals(1, fc.getDimI());
        assertEquals(2, fc.getDimJ());
        assertEquals(2, fc.getCells().size());
        assertEquals(1, fc.getDistinctFirstIndices()); // only i=1 appears
        assertEquals(2.0, fc.cellAt(0, 1)[0], 1e-15);
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("(n_patom, n_satom)")));
    }

    @Test
    void shapeRefusalsNeverSkipped() {
        // 3-token dims
        assertEquals("PHONOPY_FC_SHAPE",
                QEPhonopyForceConstants.parseText("1 2 3\n", "x").getCode());
        // non-integer dims
        assertEquals("PHONOPY_FC_SHAPE",
                QEPhonopyForceConstants.parseText("x y\n", "x").getCode());
        // zero dim
        assertEquals("PHONOPY_FC_SHAPE",
                QEPhonopyForceConstants.parseText("0 0\n", "x").getCode());
        // row with only 2 values mid-file
        assertEquals("PHONOPY_FC_SHAPE",
                QEPhonopyForceConstants.parseText("1\n1 1\n1 2\n4 5 6\n7 8 9\n", "x")
                        .getCode());
        // Fortran D exponent: upstream float() rejects it, so do we
        assertEquals("PHONOPY_FC_SHAPE",
                QEPhonopyForceConstants.parseText("1\n1 1\n1.0D0 2 3\n4 5 6\n7 8 9\n",
                        "x").getCode());
        // declared blocks missing entirely
        assertEquals("PHONOPY_FC_PARTIAL",
                QEPhonopyForceConstants.parseText("5 7\n", "x").getCode());
        // empty
        assertEquals("PHONOPY_FC_EMPTY",
                QEPhonopyForceConstants.parseText(" \n\n", "x").getCode());
        // dims beyond the safety bound
        assertEquals("PHONOPY_FC_SHAPE",
                QEPhonopyForceConstants.parseText("1000 1000\n", "x").getCode());
    }

    @Test
    void liveTrailingPartialBlockHeld() {
        // a SECOND declared block mid-write: only 2 rows present
        String live = "1 2\n"
                + "1 1\n1 0 0\n0 1 0\n0 0 1\n"
                + "1 2\n2 0 0\n0 2 0\n";
        OperationResult<QEPhonopyForceConstants.ForceConstants> result =
                QEPhonopyForceConstants.parseText(live, "live");
        assertTrue(result.isSuccess());
        QEPhonopyForceConstants.ForceConstants fc = result.getValue().orElseThrow();
        assertEquals(1, fc.getCells().size());
        assertEquals(1, fc.getPartialBlocksHeld());
        assertTrue(result.getMessage().contains("1/2"));
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("held back")));
    }
}
