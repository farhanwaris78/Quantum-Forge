/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyDos.DosTable;

/**
 * Pins for {@link QEPhonopyDos}. The projected-DOS rows below are VERBATIM
 * from the phonopy output-files doc (the NaCl example, '# Sigma = 0.063253',
 * first data rows -0.6695362607 ..., commit 3a3e0f09 doc tree); the XYZ
 * variant shape (frequency atom1_x atom1_y atom1_z ...) is the same doc's
 * XYZ_PROJECTION section.
 */
class QEPhonopyDosTest {

    private static final String NACL_PROJECTED =
            "# Sigma = 0.063253\n"
            + "       -0.6695362607        0.0000000000        0.0000000000\n"
            + "       -0.6379098952        0.0000000000        0.0000000000\n"
            + "       -0.6062835296        0.0000000000        0.0000000000\n"
            + "       -0.5746571641        0.0000000000        0.0000000000\n"
            + "       -0.5430307986        0.0000000000        0.0000000000\n"
            + "       -0.5114044331        0.0000000000        0.0000000000\n"
            + "       -0.3532726054        0.0000000004        0.0000000006\n"
            + "       -0.3216462399        0.0000000044        0.0000000066\n"
            + "       -0.2900198744        0.0000000370        0.0000000551\n"
            + "       -0.2583935089        0.0000002410        0.0000003596\n"
            + "       -0.2267671433        0.0000012239        0.0000018260\n";

    @Test
    void testVerbatimNaclProjectedDosRows() {
        OperationResult<DosTable> result = QEPhonopyDos.parseText(NACL_PROJECTED,
                "projected_dos.dat");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_DOS_OK", result.getCode());
        DosTable table = result.getValue().orElseThrow();
        assertEquals(1, table.getComments().size());
        assertEquals("# Sigma = 0.063253", table.getComments().get(0),
                "the smearing note is kept verbatim");
        assertEquals(3, table.getColumnCount());
        assertEquals(2, table.getSeriesCount(),
                "Na and Cl columns of the primitive cell, in file order");
        assertEquals(11, table.getFrequencies().length);
        assertEquals(-0.6695362607, table.getFrequencies()[0], 1e-12);
        assertEquals(11, table.getNegativeFrequencyRows(),
                "this doc table starts UNDER zero - phonopy's imaginary-mode region,"
                        + " named never clamped");
        assertEquals(-0.2267671433, table.getMaxFrequency(), 1e-12);
        assertEquals(0.0000012239, table.getSeries()[0][10], 1e-16);
        assertEquals(0.0000018260, table.getSeries()[1][10], 1e-16);
        assertEquals(0, table.getPartialTailRows());
        assertTrue(table.getUnitNote().contains("THz"));
        assertTrue(QEPhonopyDos.seriesLabels(table).get(1).contains("atom 2"));
    }

    @Test
    void testTotalDosTwoColumnsAndPeak() {
        String text = "# Tetrahedron method\n"
                + "       -0.5000000000        0.0000000000\n"
                + "        0.0000000000        1.5000000000\n"
                + "        0.5000000000        3.2500000000\n"
                + "        1.0000000000        0.7500000000\n";
        OperationResult<DosTable> result = QEPhonopyDos.parseText(text,
                "total_dos.dat");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        DosTable table = result.getValue().orElseThrow();
        assertEquals("# Tetrahedron method", table.getComments().get(0));
        assertEquals(1, table.getSeriesCount());
        assertEquals(1, table.getNegativeFrequencyRows());
        double[] peak = table.peakSummary();
        assertEquals(3.25, peak[0], 1e-12);
        assertEquals(0.5, peak[1], 1e-12);
        assertTrue(QEPhonopyDos.seriesLabels(table).get(0).contains("total"));
    }

    @Test
    void testXyzProjectionSevenColumnsReadAndLabeled() {
        String text = "# Sigma = 0.063253\n"
                + "       -0.6695362607        0.0000000000        0.0000000000        0.0000000000        0.0000000000        0.0000000000        0.0000000000\n"
                + "       -0.3279715130        0.0000000009        0.0000000009        0.0000000009        0.0000000014        0.0000000014        0.0000000014\n";
        OperationResult<DosTable> result = QEPhonopyDos.parseText(text,
                "projected_dos.dat");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        DosTable table = result.getValue().orElseThrow();
        assertEquals(7, table.getColumnCount());
        java.util.List<String> labels = QEPhonopyDos.seriesLabels(table);
        assertEquals(6, labels.size());
        assertTrue(labels.get(0).contains("atom 1 x"));
        assertTrue(labels.get(5).contains("atom 2 z"));
    }

    @Test
    void testLivePartialTailHeldBack() {
        // the run is mid-append of the last row (truncated numbers / short row)
        String truncated = NACL_PROJECTED
                + "       -0.1951407778        0.0000";
        OperationResult<DosTable> result = QEPhonopyDos.parseText(truncated,
                "projected_dos.dat");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals(1, result.getValue().orElseThrow().getPartialTailRows());
        assertEquals(11, result.getValue().orElseThrow().getFrequencies().length);

        String shortRow = NACL_PROJECTED
                + "       -0.1951407778\n";
        OperationResult<DosTable> shortResult = QEPhonopyDos.parseText(shortRow,
                "projected_dos.dat");
        assertTrue(shortResult.isSuccess(), () -> shortResult.getMessage());
        assertEquals(1, shortResult.getValue().orElseThrow().getPartialTailRows());
    }

    @Test
    void testShapeAndEmptyRefusals() {
        String midRagged = NACL_PROJECTED.substring(0,
                NACL_PROJECTED.indexOf("-0.3532726054"))
                + "       -0.3532726054        HOLD\n"
                + NACL_PROJECTED.substring(NACL_PROJECTED.indexOf("-0.3216462399"));
        OperationResult<DosTable> ragged = QEPhonopyDos.parseText(midRagged,
                "projected_dos.dat");
        assertFalse(ragged.isSuccess());
        assertEquals("PHONOPY_DOS_SHAPE", ragged.getCode(),
                "a non-number mid-file is corrupt, never skipped");

        String columnChange = "        0.0000000000        1.5\n"
                + "        0.5000000000        1.5        2.5\n"
                + "        1.0000000000        1.5\n";
        OperationResult<DosTable> change = QEPhonopyDos.parseText(columnChange,
                "total_dos.dat");
        assertFalse(change.isSuccess());
        assertEquals("PHONOPY_DOS_SHAPE", change.getCode(),
                "a column-count change mid-file is corrupt, not a pdos guess");

        String commentAfter = "        0.0000000000        1.5\n"
                + "        0.5000000000        1.6\n# late comment\n"
                + "        1.0000000000        1.7\n";
        OperationResult<DosTable> lateComment = QEPhonopyDos.parseText(commentAfter,
                "total_dos.dat");
        assertFalse(lateComment.isSuccess());
        assertEquals("PHONOPY_DOS_SHAPE", lateComment.getCode());

        OperationResult<DosTable> commentsOnly = QEPhonopyDos.parseText(
                "# Sigma = 0.1\n# nothing else\n", "total_dos.dat");
        assertFalse(commentsOnly.isSuccess());
        assertEquals("PHONOPY_DOS_EMPTY", commentsOnly.getCode());

        OperationResult<DosTable> oneColumn = QEPhonopyDos.parseText(
                "        0.5000000000\n        1.0000000000\n", "total_dos.dat");
        assertFalse(oneColumn.isSuccess());
        assertEquals("PHONOPY_DOS_SHAPE", oneColumn.getCode());

        assertEquals("PHONOPY_DOS_INPUT", QEPhonopyDos.parseText(null, "x").getCode());
    }
}
