/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Pins {@link QEPhonopyBorn} against the upstream grammar
 * (get_born_parameters / get_BORN_lines @ 3a3e0f0) and the upstream example
 * files example/NaCl-QE/BORN and test/BORN_NaCl (verbatim digits).
 */
class QEPhonopyBornTest {

    // verbatim upstream example/NaCl-QE/BORN (doc/qe.md prints the same block)
    private static final String DOC_EXAMPLE =
            "default value\n"
            + "2.472958201 0 0 0 2.472958201 0 0 0 2.472958201\n"
            + "1.105385 0 0 0 1.105385 0 0 0 1.105385\n"
            + "-1.105385 0 0 0 -1.105385 0 0 0 -1.105385\n";

    // verbatim upstream test/BORN_NaCl
    private static final String TEST_FIXTURE =
            "14.400\n"
            + "2.43533967 0 0 0 2.43533967 0 0 0 2.43533967\n"
            + "1.086875 0 0 0 1.086875 0 0 0 1.086875\n"
            + "-1.086875 0 0 0 -1.086875 0 0 0 -1.086875\n";

    @Test
    void docExampleParsesWithDefaultFactorNote() {
        OperationResult<QEPhonopyBorn.BornFile> result =
                QEPhonopyBorn.parseText(DOC_EXAMPLE, "BORN");
        assertTrue(result.isSuccess());
        assertEquals("PHONOPY_BORN_OK", result.getCode());
        QEPhonopyBorn.BornFile born = result.getValue().orElseThrow();
        // 'default value' is NOT a float -> phonopy uses its calculator default
        assertFalse(born.getFactor().isPresent());
        assertEquals("default value", born.getFirstLine());
        assertEquals(2.472958201, born.getDielectric()[0], 1e-12);
        assertEquals(2.472958201, born.getDielectric()[4], 1e-12);
        assertEquals(0.0, born.getDielectric()[1], 1e-15);
        assertEquals(2, born.getChargeCount());
        assertEquals(1.105385, born.getCharges().get(0)[0], 1e-12);
        assertEquals(-1.105385, born.getCharges().get(1)[8], 1e-12);
        assertEquals(0, born.getPartialLinesHeld());
        // the symmetry-independence boundary is stated, not hidden
        assertTrue(born.getNotes().stream().anyMatch(n
                -> n.contains("symmetry-independent atoms")));
        assertTrue(born.getNotes().stream().anyMatch(n -> n.contains("default value")));
    }

    @Test
    void explicitFactorAndOptionalTokens() {
        OperationResult<QEPhonopyBorn.BornFile> result =
                QEPhonopyBorn.parseText(TEST_FIXTURE, "BORN_NaCl");
        assertTrue(result.isSuccess());
        QEPhonopyBorn.BornFile born = result.getValue().orElseThrow();
        assertEquals(14.4, born.getFactor().orElseThrow(), 1e-12);
        assertFalse(born.getGCutoff().isPresent());
        assertEquals(2.43533967, born.getDielectric()[0], 1e-12);
        assertEquals(2, born.getChargeCount());

        // three-token first line: factor + G_cutoff + Lambda (upstream grammar)
        String three = "14.400 4.0 0.6\n" + TEST_FIXTURE.substring(TEST_FIXTURE.indexOf('\n') + 1);
        QEPhonopyBorn.BornFile born3 = QEPhonopyBorn.parseText(three, "BORN3")
                .getValue().orElseThrow();
        assertEquals(14.4, born3.getFactor().orElseThrow(), 1e-12);
        assertEquals(4.0, born3.getGCutoff().orElseThrow(), 1e-12);
        assertEquals(0.6, born3.getLambda().orElseThrow(), 1e-12);

        // upstream writer's comment header is equally non-numeric
        String commented = TEST_FIXTURE.replace("14.400", "# epsilon and Z* of atoms 1 2");
        QEPhonopyBorn.BornFile bornC = QEPhonopyBorn.parseText(commented, "BORNC")
                .getValue().orElseThrow();
        assertFalse(bornC.getFactor().isPresent());
        assertEquals("# epsilon and Z* of atoms 1 2", bornC.getFirstLine());
    }

    @Test
    void shapeRefusalsMirrorUpstreamMessages() {
        // dielectric row not 9 floats
        OperationResult<QEPhonopyBorn.BornFile> bad2 = QEPhonopyBorn.parseText(
                "14.4\n1 2 3\n1 0 0 0 1 0 0 0 1\n", "bad");
        assertFalse(bad2.isSuccess());
        assertEquals("PHONOPY_BORN_SHAPE", bad2.getCode());
        assertTrue(bad2.getMessage().contains("line 2 is incorrect"));

        // malformed MID-file charge line is never skipped
        OperationResult<QEPhonopyBorn.BornFile> badMid = QEPhonopyBorn.parseText(
                "14.4\n1 0 0 0 1 0 0 0 1\n"
                + "1 0 0 garbage\n"
                + "1 0 0 0 1 0 0 0 1\n", "badmid");
        assertFalse(badMid.isSuccess());
        assertEquals("PHONOPY_BORN_SHAPE", badMid.getCode());

        // header-only / empty / zero-charge refusals
        assertEquals("PHONOPY_BORN_EMPTY", QEPhonopyBorn.parseText("  \n \n", "e").getCode());
        assertEquals("PHONOPY_BORN_HEADER",
                QEPhonopyBorn.parseText("14.4\n", "h").getCode());
        assertEquals("PHONOPY_BORN_HEADER",
                QEPhonopyBorn.parseText("14.4\n1 0 0 0 1 0 0 0 1\n", "z").getCode());
    }

    @Test
    void liveTrailingPartialHeldNotGuessed() {
        String partial = TEST_FIXTURE.substring(0, TEST_FIXTURE.length() - 1)
                + "\n1.0 0.0 0.0\n"; // 3 floats of a fresh 4th charge line
        OperationResult<QEPhonopyBorn.BornFile> result =
                QEPhonopyBorn.parseText(partial, "live");
        assertTrue(result.isSuccess());
        QEPhonopyBorn.BornFile born = result.getValue().orElseThrow();
        assertEquals(2, born.getChargeCount());
        assertEquals(1, born.getPartialLinesHeld());
        assertTrue(born.getNotes().stream().anyMatch(n -> n.contains("held back")));
    }

    @Test
    void writerRoundTripsThroughTheGrammar() {
        double[] eps = {2.472958201, 0, 0, 0, 2.472958201, 0, 0, 0, 2.472958201};
        double[] z1 = {1.105385, 0, 0, 0, 1.105385, 0, 0, 0, 1.105385};
        double[] z2 = new double[9];
        z2[0] = -1.105385; z2[4] = -1.105385; z2[8] = -1.105385;
        String text = QEPhonopyBorn.bornText(List.of(z1, z2), eps);
        // upstream print shape: comment names the 1-based atom indices
        assertTrue(text.startsWith("# epsilon and Z* of atoms 1 2\n"));
        String[] rows = text.split("\n");
        assertEquals(4, rows.length);
        // %13.8f x9 with the trailing space, exactly like the upstream writer
        assertTrue(rows[1].endsWith(" "));
        assertEquals(9, rows[1].trim().split("\\s+").length);
        assertTrue(rows[1].contains("  2.47295820 ")); // %13.8f rounds the 9th digit

        OperationResult<QEPhonopyBorn.BornFile> reread =
                QEPhonopyBorn.parseText(text, "roundtrip");
        assertTrue(reread.isSuccess());
        QEPhonopyBorn.BornFile born = reread.getValue().orElseThrow();
        assertEquals(2, born.getChargeCount());
        assertEquals(2.47295820, born.getDielectric()[0], 1e-12);
        assertFalse(born.getFactor().isPresent()); // comment -> default, by design
    }
}
