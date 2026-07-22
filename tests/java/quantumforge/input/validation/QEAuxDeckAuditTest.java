/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class QEAuxDeckAuditTest {

    private static boolean hasCode(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    private static ValidationIssue byCode(List<ValidationIssue> issues, String code) {
        return issues.stream().filter(issue -> issue.getCode().equals(code))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "missing " + code + " in " + issues));
    }

    @Test
    void conventionalAuxiliaryDecksAuditClean() {
        String pwcond = "&INPUTCOND\n   prefixt = 'c6h6'\n   prefixl = 'c6h6'\n"
                + "   tran_prefix = 'c6h6'\n/\n";
        assertTrue(new QEAuxDeckAudit().auditDeckText("pwcond", pwcond, "7.6")
                .isEmpty());
        String bgw2pw = "&INPUT_BGW2PW\n   prefix = 'c6h6'\n/\n";
        assertTrue(new QEAuxDeckAudit().auditDeckText("bgw2pw", bgw2pw, "7.6")
                .isEmpty());
        String dynmat = "&INPUT\n   asr = 'simple'\n   fildyn = 'matdyn'\n/\n";
        assertTrue(new QEAuxDeckAudit().auditDeckText("dynmat", dynmat, "7.6")
                .isEmpty());
        // ld1.title rides &INPUT; ld1.file_pseudopw is REQUIRED under &INPUTP:
        String ld1 = "&INPUT\n   title = 'Si'\n/\n&INPUTP\n"
                + "   file_pseudopw = 'Si.upf'\n/\n";
        assertTrue(new QEAuxDeckAudit().auditDeckText("ld1", ld1, "7.6")
                .isEmpty(), "ld1.title and ld1.file_pseudopw are declared");
        String spectra = "&INPUT_MANIP\n   option = 'cut_occ_states'\n"
                + "   cross_section_file = 'xanes.dat'\n/\n";
        assertTrue(new QEAuxDeckAudit()
                .auditDeckText("spectra_correction", spectra, "7.6").isEmpty());
    }

    @Test
    void unknownKeywordsAreFatalReadErrors() {
        String deck = "&INPUTCOND\n   prefqx = 'c6h6'\n/\n";
        List<ValidationIssue> issues =
                new QEAuxDeckAudit().auditDeckText("pwcond", deck, "7.6");
        assertTrue(hasCode(issues, QEAuxDeckAudit.CODE_UNKNOWN), issues.toString());
        ValidationIssue issue = byCode(issues, QEAuxDeckAudit.CODE_UNKNOWN);
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity(),
                "the Fortran namelist READ aborts - ERROR, never a warning");
        assertTrue(issue.getMessage().contains("prefqx"));
    }

    @Test
    void wrongNamelistPlacementIsTheSameFatalPath() {
        // prefixt is INPUTCOND - placing it under &INPUT aborts the READ there.
        String deck = "&INPUT\n   prefixt = 'c6h6'\n/\n";
        List<ValidationIssue> issues =
                new QEAuxDeckAudit().auditDeckText("pwcond", deck, "7.6");
        assertTrue(hasCode(issues, QEAuxDeckAudit.CODE_WRONG_NAMELIST),
                issues.toString());
        assertEquals(ValidationSeverity.ERROR,
                byCode(issues, QEAuxDeckAudit.CODE_WRONG_NAMELIST).getSeverity());
    }

    @Test
    void versionWindowPinsAbsentKeywords() {
        // oscdft.debug_print exists only from 7.4 on:
        String deck = "&OSCDFT\n   debug_print = .true.\n/\n";
        List<ValidationIssue> at75 = new QEAuxDeckAudit().auditDeckText("oscdft",
                deck, "7.5");
        assertFalse(hasCode(at75, QEAuxDeckAudit.CODE_ABSENT_AT_VERSION),
                "declared at 7.5: " + at75);
        assertFalse(hasCode(at75, QEAuxDeckAudit.CODE_UNKNOWN), at75.toString());
        assertTrue(hasCode(at75, QEAuxDeckAudit.CODE_REQUIRED),
                "the four unrelated REQUIRED oscdft keywords stay honestly flagged");
        List<ValidationIssue> old = new QEAuxDeckAudit().auditDeckText("oscdft",
                deck, "7.3");
        assertTrue(hasCode(old, QEAuxDeckAudit.CODE_ABSENT_AT_VERSION), old.toString());
        assertEquals(ValidationSeverity.ERROR,
                byCode(old, QEAuxDeckAudit.CODE_ABSENT_AT_VERSION).getSeverity());
        assertTrue(byCode(old, QEAuxDeckAudit.CODE_ABSENT_AT_VERSION).getMessage()
                .contains("7.4"), "first-appearance label named");
        // null version audits at the newest tag:
        List<ValidationIssue> newest = new QEAuxDeckAudit().auditDeckText("oscdft",
                deck, null);
        assertFalse(hasCode(newest, QEAuxDeckAudit.CODE_ABSENT_AT_VERSION),
                newest.toString());
        assertFalse(hasCode(newest, QEAuxDeckAudit.CODE_UNKNOWN), newest.toString());
    }

    @Test
    void spectraStopSetAbortsBadOrMissingOptions() {
        String bad = "&INPUT_MANIP\n   option = 'shift_spectrum'\n/\n";
        List<ValidationIssue> issues = new QEAuxDeckAudit()
                .auditDeckText("spectra_correction", bad, "7.6");
        assertTrue(hasCode(issues, QEAuxDeckAudit.CODE_STOP_SET), issues.toString());
        ValidationIssue issue = byCode(issues, QEAuxDeckAudit.CODE_STOP_SET);
        assertTrue(issue.getMessage().contains("Option not recognized"));
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity());

        String missing = "&INPUT_MANIP\n   cross_section_file = 'x.dat'\n/\n";
        assertTrue(hasCode(new QEAuxDeckAudit()
                        .auditDeckText("spectra_correction", missing, "7.6"),
                QEAuxDeckAudit.CODE_STOP_SET),
                "a blank option fails all three guard comparisons and STOPS");

        for (String good : List.of("cut_occ_states", "add_L2_L3", "convolution")) {
            assertFalse(hasCode(new QEAuxDeckAudit().auditDeckText("spectra_correction",
                            "&INPUT_MANIP\n   option = '" + good + "'\n/\n", "7.6"),
                    QEAuxDeckAudit.CODE_STOP_SET), good);
        }
    }

    @Test
    void requiredDefaultsAndSoftOptionLayersAreHonest() {
        // bgw2pw.prefix is REQUIRED without a declared default:
        assertTrue(hasCode(new QEAuxDeckAudit().auditDeckText("bgw2pw",
                        "&INPUT_BGW2PW\n/\n", "7.6"), QEAuxDeckAudit.CODE_REQUIRED));
        assertFalse(hasCode(new QEAuxDeckAudit().auditDeckText("bgw2pw",
                        "&INPUT_BGW2PW\n   prefix='x'\n/\n", "7.6"),
                QEAuxDeckAudit.CODE_REQUIRED));
        // oscdft_et marks four keywords REQUIRED with no declared default;
        // giving only initial_prefix leaves the other three honestly flagged:
        List<ValidationIssue> etIssues = new QEAuxDeckAudit().auditDeckText(
                "oscdft_et", "&OSCDFT_ET_NAMELIST\n   initial_prefix='a'\n/\n", "7.6");
        assertTrue(hasCode(etIssues, QEAuxDeckAudit.CODE_REQUIRED),
                "final_prefix/initial_dir/final_dir stay missing: " + etIssues);
        assertEquals(3, etIssues.stream()
                .filter(i -> i.getCode().equals(QEAuxDeckAudit.CODE_REQUIRED))
                .count(), "exactly the three un-provided REQUIRED keywords");
        // documented literals are a SOFT layer -> WARNING, not refusal:
        String deck = "&INPUT\n   asr = 'sometimes'\n/\n";
        List<ValidationIssue> issues =
                new QEAuxDeckAudit().auditDeckText("dynmat", deck, "7.6");
        assertTrue(hasCode(issues, QEAuxDeckAudit.CODE_OPTION_UNDOCUMENTED),
                issues.toString());
        assertEquals(ValidationSeverity.WARNING,
                byCode(issues, QEAuxDeckAudit.CODE_OPTION_UNDOCUMENTED).getSeverity(),
                "doc literals never refuse by themselves");
    }

    @Test
    void typeShapeIsAdvisoryAgainstDeclaredTypes() {
        String deck = "&PLOT\n   iflag = 'seven'\n   output_format = 5\n/\n";
        List<ValidationIssue> issues =
                new QEAuxDeckAudit().auditDeckText("pprism", deck, "7.6");
        assertTrue(hasCode(issues, QEAuxDeckAudit.CODE_TYPE_SHAPE), issues.toString());
        assertEquals(ValidationSeverity.WARNING,
                byCode(issues, QEAuxDeckAudit.CODE_TYPE_SHAPE).getSeverity());
    }

    @Test
    void detectionIsConservativeAndAmbiguityHonest() {
        QEAuxDeckAudit audit = new QEAuxDeckAudit();
        Optional<String> pwcond = audit.detectProgram("&INPUTCOND\n/\n");
        assertTrue(pwcond.isPresent() && pwcond.get().equals("pwcond"));
        // INPUT_MANIP is shared by both spectra tools -> ambiguous:
        assertTrue(audit.detectProgram("&INPUT_MANIP\n/\n").isEmpty());
        assertTrue(audit.candidatePrograms("&INPUT_MANIP\n/\n")
                .containsAll(List.of("spectra_correction", "spectra_manipulation")));
    }

    @Test
    void boundariesStayHonest() {
        // unknown program refuses instead of pretending:
        List<ValidationIssue> unknown = new QEAuxDeckAudit()
                .auditDeckText("ph.x", "&INPUT\n/\n", "7.6");
        assertEquals(1, unknown.size());
        assertEquals("AUX_PROGRAM_UNKNOWN", unknown.get(0).getCode());
        // blank texts audit empty:
        assertTrue(new QEAuxDeckAudit().auditDeckText("pwcond", null, "7.6").isEmpty());
        assertTrue(new QEAuxDeckAudit().auditDeckText("pwcond", "  \n", "7.6").isEmpty());
        // decks without any namelist marker get the structural note:
        assertTrue(hasCode(new QEAuxDeckAudit()
                        .auditDeckText("pwcond", "3\n0.0 0.0 0.0\n", "7.6"),
                QEAuxDeckAudit.CODE_NO_NAMELIST));
        // XML input decks hit the batch-158 dialect boundary:
        List<ValidationIssue> xml = new QEAuxDeckAudit().auditDeckText("pwcond",
                "<?xml version=\"1.0\"?>\n<qes:espresso/>\n", "7.6");
        assertEquals(List.of(QEDeckDialect.CODE_XML_DIALECT),
                xml.stream().map(ValidationIssue::getCode).toList());
    }
}
