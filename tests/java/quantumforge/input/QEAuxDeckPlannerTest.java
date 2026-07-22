/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import quantumforge.input.validation.QEAuxDeckAudit;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.operation.OperationResult;

class QEAuxDeckPlannerTest {

    private static Map<String, String> assignments(String... pairs) {
        Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static boolean hasCode(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    @Test
    void forProgramRefusesUnknownLabelsInsteadOfGuessing() {
        OperationResult<QEAuxDeckPlanner> bogus =
                QEAuxDeckPlanner.forProgram("ph.x", "7.6");
        assertFalse(bogus.isSuccess());
        assertEquals(QEAuxDeckPlanner.CODE_PROGRAM, bogus.getCode());
        assertTrue(bogus.getMessage().contains("pwcond"),
                "the refusal lists the supported programs: " + bogus.getMessage());
        OperationResult<QEAuxDeckPlanner> blank =
                QEAuxDeckPlanner.forProgram("   ", "7.6");
        assertFalse(blank.isSuccess());
        assertEquals(QEAuxDeckPlanner.CODE_PROGRAM, blank.getCode());
    }

    @Test
    void versionWindowIsFailClosedAndBlankResolvesNewest() {
        OperationResult<QEAuxDeckPlanner> bogus =
                QEAuxDeckPlanner.forProgram("dynmat", "8.1");
        assertFalse(bogus.isSuccess());
        assertEquals(QEAuxDeckPlanner.CODE_VERSION, bogus.getCode());
        assertTrue(bogus.getMessage().contains("7.2") && bogus.getMessage().contains("7.6"));
        OperationResult<QEAuxDeckPlanner> blank =
                QEAuxDeckPlanner.forProgram("dynmat", null);
        assertTrue(blank.isSuccess());
        assertEquals("QEAUX_OK_NEWEST", blank.getCode(),
                "blank version resolves to the newest tag and SAYS so");
        assertEquals("7.6", blank.getValue().orElseThrow().getVersion());
    }

    @Test
    void fieldWindowsFollowTheMinedMasks() {
        QEAuxDeckPlanner dynmat =
                QEAuxDeckPlanner.forProgram("dynmat", "7.6").getValue().orElseThrow();
        assertTrue(dynmat.fields().stream().anyMatch(f -> f.getName().equals("asr")));
        assertTrue(dynmat.fields().stream().noneMatch(f -> f.getName().equals("fildynx")),
                "not in the grammar - never promptable");

        // oscdft drift: print_debug (7.2/7.3) renamed to debug_print (7.4+):
        QEAuxDeckPlanner old =
                QEAuxDeckPlanner.forProgram("oscdft", "7.3").getValue().orElseThrow();
        assertTrue(old.fields().stream().anyMatch(f -> f.getName().equals("print_debug")));
        assertTrue(old.fields().stream().noneMatch(f -> f.getName().equals("debug_print")));
        assertTrue(old.fields().stream().noneMatch(f -> f.getName().equals("oscdft_type")),
                "arrives at 7.5 only");
        QEAuxDeckPlanner mid =
                QEAuxDeckPlanner.forProgram("oscdft", "7.5").getValue().orElseThrow();
        assertTrue(mid.fields().stream().noneMatch(f -> f.getName().equals("print_debug")));
        assertTrue(mid.fields().stream().anyMatch(f -> f.getName().equals("debug_print")));
        assertTrue(mid.fields().stream().anyMatch(f -> f.getName().equals("oscdft_type")));
    }

    @Test
    void requiredFlagAndDefaultsRideVerbatim() {
        QEAuxDeckPlanner bgw2pw =
                QEAuxDeckPlanner.forProgram("bgw2pw", "7.6").getValue().orElseThrow();
        QEAuxDeckPlanner.FieldRow prefix = bgw2pw.fields().stream()
                .filter(f -> f.getName().equals("prefix")).findFirst().orElseThrow();
        assertTrue(prefix.isRequired());
        assertEquals(null, prefix.getDefaultText(),
                "REQUIRED without a declared default, mined");
        assertEquals("INPUT_BGW2PW", prefix.getNamelist());
        QEAuxDeckPlanner dynmat =
                QEAuxDeckPlanner.forProgram("dynmat", "7.6").getValue().orElseThrow();
        QEAuxDeckPlanner.FieldRow asr = dynmat.fields().stream()
                .filter(f -> f.getName().equals("asr")).findFirst().orElseThrow();
        assertEquals("'no'", asr.getDefaultText());
        assertEquals(List.of("no", "simple", "crystal", "one-dim", "zero-dim"),
                asr.getOptions());
        assertTrue(asr.promptLabel().contains("asr") && asr.promptLabel().contains("&INPUT"));
    }

    @Test
    void renderingIsMechanicalDeterministicAndGrouped() {
        QEAuxDeckPlanner pwcond =
                QEAuxDeckPlanner.forProgram("pwcond", "7.6").getValue().orElseThrow();
        Map<String, String> draft = assignments(
                "prefixt", "c6h6", "prefixl", "c6h6", "tran_prefix", "c6h6");
        OperationResult<String> render = pwcond.renderDraft(draft);
        assertTrue(render.isSuccess());
        String expected = "&INPUTCOND\n"
                + "   prefixt = 'c6h6'\n"
                + "   prefixl = 'c6h6'\n"
                + "   tran_prefix = 'c6h6'\n"
                + "/\n";
        assertEquals(expected, render.getValue().orElseThrow(),
                "mined field order inside the namelist, CHARACTER single-quoted");
        assertEquals(render.getValue().get(), pwcond.renderDraft(draft).getValue().get(),
                "same assignments render byte-identical decks");
        // ld1 multi-namelist: &INPUT then &INPUTP (mined namelist order):
        QEAuxDeckPlanner ld1 =
                QEAuxDeckPlanner.forProgram("ld1", "7.6").getValue().orElseThrow();
        OperationResult<String> ld1Render = ld1.renderDraft(assignments(
                "title", "Si", "file_pseudopw", "Si.UPF"));
        String ld1Deck = ld1Render.getValue().orElseThrow();
        assertTrue(ld1Deck.startsWith("&INPUT\n   title = 'Si'\n/\n"), ld1Deck);
        assertTrue(ld1Deck.contains("&INPUTP\n   file_pseudopw = 'Si.UPF'\n/\n"), ld1Deck);
    }

    @Test
    void quotingRulesNeverDoubleQuoteAndQuoteOnlyCharacters() {
        QEAuxDeckPlanner dynmat =
                QEAuxDeckPlanner.forProgram("dynmat", "7.6").getValue().orElseThrow();
        String deck = dynmat.renderDraft(assignments(
                "asr", "'simple'",       // already quoted -> verbatim
                "fildyn", "matdyn",      // CHARACTER -> quoted
                "filmol", "\"gw\""       // double-quoted stays verbatim
        )).getValue().orElseThrow();
        assertTrue(deck.contains("asr = 'simple'"), deck);
        assertTrue(deck.contains("fildyn = 'matdyn'"), deck);
        assertTrue(deck.contains("filmol = \"gw\""), deck);
        assertFalse(deck.contains("''simple''"), "never double-quoted");
        // Fortran '' escaping for a quote inside a CHARACTER value:
        String escaped = dynmat.renderDraft(assignments("fildyn", "ob'1"))
                .getValue().orElseThrow();
        assertTrue(escaped.contains("fildyn = 'ob''1'"), escaped);
        // xspectra prefix is UNKNOWN-typed: text quotes, literals stay bare:
        QEAuxDeckPlanner xspectra =
                QEAuxDeckPlanner.forProgram("xspectra", "7.6").getValue().orElseThrow();
        String xdeck = xspectra.renderDraft(assignments("prefix", "si"))
                .getValue().orElseThrow();
        assertTrue(xdeck.contains("prefix = 'si'"), xdeck);
    }

    @Test
    void renderRefusesUndeclaredAndVersionAbsentKeywords() {
        QEAuxDeckPlanner pwcond =
                QEAuxDeckPlanner.forProgram("pwcond", "7.6").getValue().orElseThrow();
        OperationResult<String> unknown = pwcond.renderDraft(assignments(
                "prefqx", "c6h6"));
        assertFalse(unknown.isSuccess());
        assertEquals(QEAuxDeckPlanner.CODE_KEYWORD, unknown.getCode());
        assertTrue(unknown.getMessage().contains("prefqx"));
        // real keyword, wrong window: debug_print arrives at 7.4:
        QEAuxDeckPlanner oscdft =
                QEAuxDeckPlanner.forProgram("oscdft", "7.3").getValue().orElseThrow();
        OperationResult<String> drifted = oscdft.renderDraft(assignments(
                "debug_print", ".true."));
        assertFalse(drifted.isSuccess());
        assertEquals(QEAuxDeckPlanner.CODE_KEYWORD_VERSION, drifted.getCode());
        assertTrue(drifted.getMessage().contains("7.4"), drifted.getMessage());
        // nothing assigned -> typed status, empty text:
        OperationResult<String> empty = pwcond.renderDraft(assignments("prefixt", "  "));
        assertTrue(empty.isSuccess());
        assertEquals(QEAuxDeckPlanner.CODE_EMPTY, empty.getCode());
        assertEquals("", empty.getValue().orElseThrow());
    }

    @Test
    void draftThenAuditRoundTripsThroughTheSingleGrammar() {
        QEAuxDeckPlanner pwcond =
                QEAuxDeckPlanner.forProgram("pwcond", "7.6").getValue().orElseThrow();
        List<ValidationIssue> clean = pwcond.auditDraft(assignments(
                "prefixt", "c6h6", "prefixl", "c6h6", "tran_prefix", "c6h6"))
                .getValue().orElseThrow();
        assertTrue(clean.isEmpty(), clean.toString());
        // REQUIRED surfaces through the deck, not through planner policy:
        QEAuxDeckPlanner bgw2pw =
                QEAuxDeckPlanner.forProgram("bgw2pw", "7.6").getValue().orElseThrow();
        List<ValidationIssue> missing = bgw2pw.auditDraft(assignments(
                "outdir", "tmp")).getValue().orElseThrow();
        assertTrue(hasCode(missing, QEAuxDeckAudit.CODE_REQUIRED));
        // spectra STOP-set fires on a rendered bad option:
        QEAuxDeckPlanner spectra =
                QEAuxDeckPlanner.forProgram("spectra_correction", "7.6")
                        .getValue().orElseThrow();
        List<ValidationIssue> stopped = spectra.auditDraft(assignments(
                "option", "shift_spectrum")).getValue().orElseThrow();
        assertTrue(hasCode(stopped, QEAuxDeckAudit.CODE_STOP_SET));
        List<ValidationIssue> passed = spectra.auditDraft(assignments(
                "option", "cut_occ_states", "cross_section_file", "x.dat"))
                .getValue().orElseThrow();
        assertFalse(hasCode(passed, QEAuxDeckAudit.CODE_STOP_SET));
    }

    @Test
    void namelistMetadataAndDocPagesStayMined() {
        QEAuxDeckPlanner dynmat =
                QEAuxDeckPlanner.forProgram("dynmat", "7.6").getValue().orElseThrow();
        assertEquals(List.of("INPUT"), dynmat.namelists());
        assertTrue(dynmat.docPage().endsWith("INPUT_DYNMAT.html"));
        QEAuxDeckPlanner xspectra =
                QEAuxDeckPlanner.forProgram("xspectra", "7.6").getValue().orElseThrow();
        assertTrue(xspectra.namelists().containsAll(
                List.of("INPUT_XSPECTRA", "PLOT", "PSEUDOS", "CUT_OCC")));
        assertTrue(xspectra.docPage().endsWith("INPUT_XSPECTRA"));
        assertFalse(xspectra.fields("INPUT_XSPECTRA").isEmpty());
        assertTrue(xspectra.fields("NOPE").isEmpty());
    }
}
