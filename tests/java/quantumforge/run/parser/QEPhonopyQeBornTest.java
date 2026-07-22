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
 * Pins {@link QEPhonopyQeBorn} against the upstream phonopy-qe-born parse
 * grammar (phonopy_qe_born_script.parse_ph_out @ 3a3e0f0) using the exact
 * block layout of phonopy's own test fixture
 * test/interface/qe/NaCl-ph/NaCl.ph.out (verbatim digits incl. its TWICE
 * printed dielectric/BEC pair - LAST-BLOCK-WINS - and the 'with asr applied'
 * sibling that must NEVER be parsed).
 */
class QEPhonopyQeBornTest {

    // exact block slice from the pinned fixture (lines 62 + 205-244 layout)
    private static String naclPhOut(String tail) {
        return "     Program PHONON v.7.3 starts ...\n"
                + "     number of atoms/cell      =            2\n"
                + "\n     End of electric fields calculation\n"
                + "\n"
                + "          Dielectric constant in cartesian axis\n"
                + "\n"
                + "          (       2.474410241       0.000000000      -0.000000000 )\n"
                + "          (      -0.000000000       2.474410241      -0.000000000 )\n"
                + "          (       0.000000000      -0.000000000       2.474410241 )\n"
                + "\n"
                + "          Effective charges (d Force / dE) in cartesian axis without"
                + " acoustic sum rule applied (asr)\n"
                + "\n"
                + "           atom    1  Na    Mean Z*:        1.09885\n"
                + "      Ex  (        1.09885        0.00000       -0.00000 )\n"
                + "      Ey  (        0.00000        1.09885       -0.00000 )\n"
                + "      Ez  (       -0.00000       -0.00000        1.09885 )\n"
                + "           atom    2  Cl    Mean Z*:       -1.10266\n"
                + "      Ex  (       -1.10266        0.00000       -0.00000 )\n"
                + "      Ey  (        0.00000       -1.10266       -0.00000 )\n"
                + "      Ez  (        0.00000        0.00000       -1.10266 )\n"
                + "\n"
                + "          Effective charges Sum: Mean:       -0.00381\n"
                + "             -0.00381        0.00000       -0.00000\n"
                + "              0.00000       -0.00381       -0.00000\n"
                + "             -0.00000       -0.00000       -0.00381\n"
                + "\n"
                + "          Effective charges (d Force / dE) in cartesian axis with asr"
                + " applied:\n"
                + "           atom    1  Na    Mean Z*:        1.10076\n"
                + "      E*x (        1.10076        0.00000        0.00000 )\n"
                + "      E*y (        0.00000        1.10076       -0.00000 )\n"
                + "      E*z (       -0.00000       -0.00000        1.10076 )\n"
                + "           atom    2  Cl    Mean Z*:       -1.10076\n"
                + "      E*x (       -1.10076       -0.00000       -0.00000 )\n"
                + "      E*y (        0.00000       -1.10076        0.00000 )\n"
                + "      E*z (        0.00000        0.00000       -1.10076 )\n"
                + tail;
    }

    @Test
    void verbatimFixtureValuesAndLastBlockWins() {
        // the pinned fixture carries the pair TWICE; the second copy differs
        String twice = naclPhOut("\n     Diagonalizing the dynamical matrix\n"
                + "          1   0.000000000   0.000000000   0.000000000\n"
                + "\n"
                + "          Dielectric constant in cartesian axis\n"
                + "\n"
                + "          (       9.999000000       0.000000000       0.000000000 )\n"
                + "          (       0.000000000       9.999000000       0.000000000 )\n"
                + "          (       0.000000000       0.000000000       9.999000000 )\n"
                + "\n"
                + "          Effective charges (d Force / dE) in cartesian axis without"
                + " acoustic sum rule applied (asr)\n"
                + "\n"
                + "           atom    1  Na    Mean Z*:        5.55555\n"
                + "      Ex  (        5.55555        0.00000        0.00000 )\n"
                + "      Ey  (        0.00000        5.55555        0.00000 )\n"
                + "      Ez  (        0.00000        0.00000        5.55555 )\n"
                + "           atom    2  Cl    Mean Z*:       -5.55555\n"
                + "      Ex  (       -5.55555        0.00000        0.00000 )\n"
                + "      Ey  (        0.00000       -5.55555        0.00000 )\n"
                + "      Ez  (        0.00000        0.00000       -5.55555 )\n");
        OperationResult<QEPhonopyQeBorn.QeBornExtract> result =
                QEPhonopyQeBorn.parseText(twice, "NaCl.ph.out", 2);
        assertTrue(result.isSuccess());
        assertEquals("PHONOPY_QEBORN_OK", result.getCode());
        QEPhonopyQeBorn.QeBornExtract x = result.getValue().orElseThrow();
        // LAST block wins - upstream's re-assign loop, never blended
        assertEquals(2, x.getDielectricHookCount());
        assertEquals(2, x.getBecHookCount());
        assertEquals(9.999, x.getDielectric()[0], 1e-12);
        assertEquals(5.55555, x.getCharges().get(0).getTensor()[0][0], 1e-12);
        assertEquals(-5.55555, x.getCharges().get(1).getTensor()[0][0], 1e-12);
        // the 'with asr applied' sibling: counted, never parsed
        assertEquals(1, x.getAsrAppliedBlockCount());
        assertTrue(x.getNotes().stream().anyMatch(n -> n.contains("LAST of 2")));
    }

    @Test
    void rawValuesSpeciesAndMeanZVerbatim() {
        OperationResult<QEPhonopyQeBorn.QeBornExtract> result =
                QEPhonopyQeBorn.parseText(naclPhOut(""), "NaCl.ph.out", 2);
        assertTrue(result.isSuccess());
        QEPhonopyQeBorn.QeBornExtract x = result.getValue().orElseThrow();
        // RAW values = upstream parse_ph_out, NOT the symmetrized +-1.10075500
        assertEquals(2.474410241, x.getDielectric()[0], 1e-10);
        assertEquals(2.474410241, x.getDielectric()[4], 1e-10);
        assertEquals(-0.0, x.getDielectric()[1], 1e-15);
        assertEquals(1.09885, x.getCharges().get(0).getTensor()[0][0], 1e-10);
        assertEquals(-1.10266, x.getCharges().get(1).getTensor()[1][1], 1e-10);
        // verbatim aids upstream discards
        assertEquals("Na", x.getCharges().get(0).getSpeciesLabel());
        assertEquals("Cl", x.getCharges().get(1).getSpeciesLabel());
        assertEquals("1.09885", x.getCharges().get(0).getMeanZText());
        assertEquals("-1.10266", x.getCharges().get(1).getMeanZText());
        assertEquals(1, x.getCharges().get(0).getAtomIndex());
        assertEquals(2, x.getCharges().get(1).getAtomIndex());
        assertEquals(Integer.valueOf(2), x.getFileNatom().orElseThrow());
        // the raw-vs-symmetrized boundary is STATED
        assertTrue(x.getNotes().stream().anyMatch(n -> n.contains("symmetrized")));
        // toBornText emits the upstream print shape with RAW values
        String born = x.toBornText();
        assertTrue(born.startsWith("# epsilon and Z* of atoms 1 2\n"));
        assertTrue(born.contains("1.09885000"));
        assertFalse(born.contains("1.10075500"));
        OperationResult<QEPhonopyBorn.BornFile> reread =
                QEPhonopyBorn.parseText(born, "BORN-from-ph");
        assertTrue(reread.isSuccess());
        assertEquals(2, reread.getValue().orElseThrow().getChargeCount());
    }

    @Test
    void natomMismatchAndMissingBlocksRefused() {
        // upstream: AssertionError when pw nat != the file's own natom print
        OperationResult<QEPhonopyQeBorn.QeBornExtract> mismatch =
                QEPhonopyQeBorn.parseText(naclPhOut(""), "NaCl.ph.out", 3);
        assertFalse(mismatch.isSuccess());
        assertEquals("PHONOPY_QEBORN_NATOM", mismatch.getCode());

        // no dielectric hook: upstream RuntimeError message verbatim
        String noEps = "     number of atoms/cell      =            2\n"
                + "          Effective charges (d Force / dE) in cartesian axis without"
                + " acoustic sum rule applied (asr)\n"
                + "\n"
                + "           atom    1  Na    Mean Z*:        1.09885\n"
                + "      Ex  (        1.09885        0.00000       -0.00000 )\n"
                + "      Ey  (        0.00000        1.09885       -0.00000 )\n"
                + "      Ez  (       -0.00000       -0.00000        1.09885 )\n"
                + "           atom    2  Cl    Mean Z*:       -1.10266\n"
                + "      Ex  (       -1.10266        0.00000       -0.00000 )\n"
                + "      Ey  (        0.00000       -1.10266       -0.00000 )\n"
                + "      Ez  (        0.00000        0.00000       -1.10266 )\n";
        OperationResult<QEPhonopyQeBorn.QeBornExtract> missing =
                QEPhonopyQeBorn.parseText(noEps, "NaCl-noborn.ph.out", 2);
        assertFalse(missing.isSuccess());
        assertEquals("PHONOPY_QEBORN_HEADER", missing.getCode());
        assertTrue(missing.getMessage()
                .contains("Could not find dielectric tensor in ph.x output."));

        // dielectric present but BEC absent
        String noBec = "          Dielectric constant in cartesian axis\n\n"
                + "          (       1.000000000       0.000000000       0.000000000 )\n"
                + "          (       0.000000000       1.000000000       0.000000000 )\n"
                + "          (       0.000000000       0.000000000       1.000000000 )\n";
        OperationResult<QEPhonopyQeBorn.QeBornExtract> noBecR =
                QEPhonopyQeBorn.parseText(noBec, "x.ph.out", 2);
        assertFalse(noBecR.isSuccess());
        assertTrue(noBecR.getMessage()
                .contains("Could not find Born effective charges in ph.x output."));

        // a MALFORMED row inside a block is SHAPE, never skipped
        String broken = naclPhOut("").replace(
                "      Ey  (        0.00000        1.09885       -0.00000 )",
                "      Ey  garbage");
        OperationResult<QEPhonopyQeBorn.QeBornExtract> brokenR =
                QEPhonopyQeBorn.parseText(broken, "broken.ph.out", 2);
        assertFalse(brokenR.isSuccess());
        assertEquals("PHONOPY_QEBORN_SHAPE", brokenR.getCode());
    }

    @Test
    void livePartialBlockHeldKeepsPreviousComplete() {
        // first pair complete, second dielectric truncated mid-rows (live write)
        String live = naclPhOut("")
                + "          Dielectric constant in cartesian axis\n\n"
                + "          (       7.700000000       0.000000000       0.000000000 )\n"
                + "          (       0.000000000       7.7000";
        OperationResult<QEPhonopyQeBorn.QeBornExtract> result =
                QEPhonopyQeBorn.parseText(live, "live.ph.out", 2);
        assertTrue(result.isSuccess());
        QEPhonopyQeBorn.QeBornExtract x = result.getValue().orElseThrow();
        assertEquals(2, x.getDielectricHookCount());
        assertEquals(2.474410241, x.getDielectric()[0], 1e-10); // previous kept
        assertEquals(1, x.getPartialBlocksHeld());
        assertTrue(x.getNotes().stream().anyMatch(n -> n.contains("held back")));
    }

    @Test
    void inputRefusalsEnumerated() {
        assertEquals("PHONOPY_QEBORN_INPUT",
                QEPhonopyQeBorn.parseText(null, "n", 2).getCode());
        assertEquals("PHONOPY_QEBORN_INPUT",
                QEPhonopyQeBorn.parseText("x", "n", 0).getCode());
        assertEquals("PHONOPY_QEBORN_INPUT",
                QEPhonopyQeBorn.parse(null, 2).getCode());
    }
}
