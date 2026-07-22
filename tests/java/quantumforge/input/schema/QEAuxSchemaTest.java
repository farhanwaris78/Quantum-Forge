/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class QEAuxSchemaTest {

    @Test
    void all24MandatedProgramsWithTheirOwnDocPages() {
        List<String> expected = List.of("bgw2pw", "pw2bgw", "pwcond", "pprism",
                "oscdft_et", "oscdft", "dynmat", "matdyn", "postahc", "q2r",
                "d3hess", "neb", "cp", "cppp", "lanczos", "spectrum", "davidson",
                "magnon", "eels", "xspectra", "spectra_correction",
                "spectra_manipulation", "ld1", "all_currents");
        assertEquals(expected, QEAuxSchema.programs(), "the 24 mandated programs, in order");
        assertEquals(667, QEAuxSchema.rowCount(), "mined keyword union, pinned");
        for (String program : expected) {
            assertTrue(QEAuxSchema.docPage(program)
                            .startsWith("https://www.quantum-espresso.org/Doc/"),
                    program + " doc page: " + QEAuxSchema.docPage(program));
            assertFalse(QEAuxSchema.entries(program).isEmpty(),
                    program + " has a mined grammar");
        }
        assertTrue(QEAuxSchema.docPage("xspectra").endsWith("INPUT_XSPECTRA"));
        assertTrue(QEAuxSchema.docPage("spectra_correction")
                .endsWith("INPUT_SPECTRA_CORRECTION"));
        assertTrue(QEAuxSchema.docPage("spectra_manipulation")
                .endsWith("INPUT_SPECTRA_MANIPULATION"));
    }

    @Test
    void namelistMembershipIsMinedNotGuessed() {
        assertEquals(List.of("INPUTCOND"), QEAuxSchema.namelists("pwcond"));
        assertEquals(List.of("INPUT_BGW2PW"), QEAuxSchema.namelists("bgw2pw"));
        assertEquals(List.of("INPUT_PW2BGW"), QEAuxSchema.namelists("pw2bgw"));
        assertTrue(QEAuxSchema.namelists("xspectra").containsAll(
                List.of("INPUT_XSPECTRA", "PLOT", "PSEUDOS", "CUT_OCC")),
                "the four source-declared namelists ride along");
        assertEquals(List.of("INPUT_MANIP"), QEAuxSchema.namelists("spectra_correction"));
        assertEquals(List.of("INPUT_MANIP"), QEAuxSchema.namelists("spectra_manipulation"),
                "both spectra tools read the same source-declared namelist");
        assertTrue(QEAuxSchema.namelists("cp").containsAll(
                List.of("CONTROL", "SYSTEM", "ELECTRONS")),
                "cp.x keeps its own deck structure");
        assertEquals(List.of("spectra_correction", "spectra_manipulation"),
                QEAuxSchema.programsForNamelist("INPUT_MANIP"),
                "INPUT_MANIP is shared by both spectra tools (their docs say so)");
        assertEquals(List.of("pwcond"), QEAuxSchema.programsForNamelist("INPUTCOND"));
    }

    @Test
    void driftIsCarriedInMasksNotProse() {
        // oscdft grew keywords across the window (doc sha drift at 7.4/7.5/7.6):
        QEAuxSchema.Row printDebug = QEAuxSchema.lookup("oscdft", "print_debug")
                .get(0);
        assertEquals(0x03, printDebug.getVersionMask(), "present only at 7.2+7.3");
        assertTrue(printDebug.presentIn("7.3"));
        assertFalse(printDebug.presentIn("7.6"));
        QEAuxSchema.Row debugPrint = QEAuxSchema.lookup("oscdft", "debug_print")
                .get(0);
        assertEquals(0x1C, debugPrint.getVersionMask(), "renamed keyword from 7.4 on");
        assertEquals("7.4", debugPrint.firstPresentLabel());
        QEAuxSchema.Row oscdftType = QEAuxSchema.lookup("oscdft", "oscdft_type")
                .get(0);
        assertEquals(0x18, oscdftType.getVersionMask(), "arrives at 7.5");
        // every one of the 667 rows names a real mask:
        for (String program : QEAuxSchema.programs()) {
            for (QEAuxSchema.Row row : QEAuxSchema.entries(program)) {
                assertTrue(row.getVersionMask() != 0,
                        program + "." + row.getName() + " lost its mask");
            }
        }
    }

    @Test
    void lookupIsCaseInsensitiveAndStripsArrayIndex() {
        assertFalse(QEAuxSchema.lookup("PWCOND", "OutDir").isEmpty());
        assertFalse(QEAuxSchema.lookup("dynmat", "ASR").isEmpty());
        assertTrue(QEAuxSchema.lookup("pwcond", "not_a_keyword").isEmpty());
        assertTrue(QEAuxSchema.lookup("pwcond", null).isEmpty());
        assertFalse(QEAuxSchema.entries("no_such_program").isEmpty() == false,
                "unknown programs audit to nothing - never fabricated");
        // REQUIRED flags + verbatim defaults:
        QEAuxSchema.Row prefix = QEAuxSchema.lookup("bgw2pw", "prefix").get(0);
        assertTrue(prefix.isRequired(), "bgw2pw.prefix mined REQUIRED");
        assertEquals(nullOrNoDefault(prefix), true, "no declared default rides along");
        QEAuxSchema.Row asr = QEAuxSchema.lookup("dynmat", "asr").get(0);
        assertEquals("'no'", asr.getDefaultText());
        assertEquals(5, asr.getOptions().size(), "no/simple/crystal/one-dim/zero-dim");
    }

    private static boolean nullOrNoDefault(QEAuxSchema.Row row) {
        return row.getDefaultText() == null;
    }

    @Test
    void hardFactsCarryTheSpectraStopSetVerbatim() {
        assertFalse(QEAuxSchema.facts().isEmpty());
        List<QEAuxSchema.MaskedFact> spectra = QEAuxSchema.factsFor("spectra_correction");
        assertEquals(1, spectra.size());
        String text = spectra.get(0).getText();
        assertTrue(text.contains("cut_occ_states") && text.contains("add_L2_L3")
                && text.contains("convolution"), text);
        assertTrue(text.contains("Option not recognized"), text);
        assertTrue(text.contains("stop"), "a STOP, not an errore - stated");
        assertEquals(0x1F, spectra.get(0).getVersionMask(), "stable across 7.2..7.6");
    }
}
