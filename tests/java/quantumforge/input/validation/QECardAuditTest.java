/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class QECardAuditTest {

    private static boolean hasCode(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    private static ValidationIssue byCode(List<ValidationIssue> issues, String code) {
        return issues.stream().filter(issue -> issue.getCode().equals(code))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "missing " + code + " in " + issues));
    }

    @Test
    void aConventionalDeckAuditsClean() {
        String deck = "&CONTROL\n   calculation = 'scf'\n/\n"
                + "&SYSTEM\n   ibrav = 2, nat = 1, ntyp = 1, ecutwfc = 30.0\n/\n"
                + "&ELECTRONS\n/\n"
                + "ATOMIC_SPECIES\n"
                + "  Si 28.0855 Si.pz-vbc.UPF\n"
                + "ATOMIC_POSITIONS {angstrom}\n"
                + "  Si 0.0 0.0 0.0\n"
                + "K_POINTS {automatic}\n"
                + "  4 4 4 1 1 1\n"
                + "CELL_PARAMETERS {alat}\n"
                + "  3.9 0.0 0.0\n"
                + "  0.0 3.9 0.0\n"
                + "  0.0 0.0 3.9\n";
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(deck, "7.6");
        assertTrue(issues.isEmpty(), () -> "unexpected findings: " + issues);
    }

    @Test
    void unknownCardsWarnBecausePwSilentlyIgnoresThem() {
        String deck = "ATOMIC_SPECIES\n  Si 28.0 Si.UPF\n"
                + "KPOINTS {gamma}\n"; // misspelled: lacks the underscore
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(deck, "7.6");
        assertTrue(hasCode(issues, QECardAudit.CODE_UNKNOWN_CARD), issues.toString());
        ValidationIssue issue = byCode(issues, QECardAudit.CODE_UNKNOWN_CARD);
        assertTrue(issue.getMessage().contains("ignored"), issue.getMessage());
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity(),
                "the read_cards fallback only WRITES a warning - never an error");
    }

    @Test
    void contentLinesWithoutBracesAreNeverMistakenForCards() {
        // 'Si 0.0 0.0 0.0' starts with an alpha word but carries no braces:
        // pw.x only dispatches whole-line card headers, and the audit mirrors
        // that - flagging content lines would be a fabricated finding.
        String deck = "ATOMIC_POSITIONS crystal\n"
                + "  Si 0.00 0.00 0.00\n"
                + "  Si 0.25 0.25 0.25\n"
                + "K_POINTS tpiba\n"
                + "  2\n"
                + "  0.0 0.0 0.0 1.0\n"
                + "  0.5 0.5 0.5 1.0\n";
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(deck, "7.6");
        assertFalse(hasCode(issues, QECardAudit.CODE_UNKNOWN_CARD), issues.toString());
    }

    @Test
    void removedCardsAreFatalNotWarnings() {
        for (String card : List.of("DIPOLE", "ESR")) {
            List<ValidationIssue> issues = new QECardAudit()
                    .auditDeckText(card + "\n", "7.6");
            assertTrue(hasCode(issues, QECardAudit.CODE_CARD_REMOVED),
                    card + ": " + issues);
            ValidationIssue issue = byCode(issues, QECardAudit.CODE_CARD_REMOVED);
            assertEquals(ValidationSeverity.ERROR, issue.getSeverity());
            assertTrue(issue.getMessage().contains("no longer exists"),
                    issue.getMessage());
        }
    }

    @Test
    void hubbardTrapsMirrorTheExactChainOrder() {
        // Dash-less / misspelled projector names abort (mined sanity traps).
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("HUBBARD {orthoatomic}\n", "7.6"),
                QECardAudit.CODE_TRAP));
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("HUBBARD {normatomic}\n", "7.6"),
                QECardAudit.CODE_TRAP));
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("HUBBARD {pseudo-atomic}\n", "7.6"),
                QECardAudit.CODE_TRAP),
                "the -ATOMIC trap arm precedes the ATOMIC option: pw.x aborts");
        // ...while the valid spellings pass the SAME trap arms in pw.x.
        assertTrue(new QECardAudit()
                .auditDeckText("HUBBARD {ortho-atomic}\n", "7.6").isEmpty());
        assertTrue(new QECardAudit()
                .auditDeckText("HUBBARD {norm-atomic}\n", "7.6").isEmpty());
        assertTrue(new QECardAudit()
                .auditDeckText("HUBBARD {wf}\n", "7.6").isEmpty());
        assertTrue(new QECardAudit()
                .auditDeckText("HUBBARD {pseudo}\n", "7.6").isEmpty());
    }

    @Test
    void bareHubbardAndUnknownHubbardAreBothFatal() {
        String bare = "HUBBARD\n";
        List<ValidationIssue> bareIssues =
                new QECardAudit().auditDeckText(bare, "7.6");
        assertTrue(hasCode(bareIssues, QECardAudit.CODE_BARE_CARD),
                bareIssues.toString());
        assertEquals(ValidationSeverity.ERROR,
                byCode(bareIssues, QECardAudit.CODE_BARE_CARD).getSeverity(),
                "bare HUBBARD errores with 'None or wrong Hubbard projectors'");

        String wrong = "HUBBARD {gaussian}\n";
        List<ValidationIssue> wrongIssues =
                new QECardAudit().auditDeckText(wrong, "7.6");
        assertTrue(hasCode(wrongIssues, QECardAudit.CODE_UNKNOWN_OPTION),
                wrongIssues.toString());
        assertEquals(ValidationSeverity.ERROR,
                byCode(wrongIssues, QECardAudit.CODE_UNKNOWN_OPTION).getSeverity(),
                "'unknown option for HUBBARD' is an abort, not a warning");
    }

    @Test
    void atomicPositionsUnknownOptionIsFatalButBareNameIsDeprecated() {
        String wrong = "ATOMIC_POSITIONS {furlong}\n";
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(wrong, "7.6");
        assertTrue(hasCode(issues, QECardAudit.CODE_UNKNOWN_OPTION), issues.toString());
        assertEquals(ValidationSeverity.ERROR,
                byCode(issues, QECardAudit.CODE_UNKNOWN_OPTION).getSeverity(),
                "'unknown option for ATOMIC_POSITION' aborts pw.x");

        String bare = "ATOMIC_POSITIONS\n  Si 0.0 0.0 0.0\n";
        List<ValidationIssue> bareIssues =
                new QECardAudit().auditDeckText(bare, "7.6");
        assertTrue(hasCode(bareIssues, QECardAudit.CODE_BARE_CARD),
                bareIssues.toString());
        ValidationIssue bareIssue = byCode(bareIssues, QECardAudit.CODE_BARE_CARD);
        assertEquals(ValidationSeverity.WARNING, bareIssue.getSeverity(),
                "bare ATOMIC_POSITIONS is DEPRECATED-defaulted (bohr/alat), not fatal");
        assertTrue(bareIssue.getMessage().contains("bohr")
                && bareIssue.getMessage().contains("alat"),
                "the prog-dependent defaults must be named: " + bareIssue.getMessage());

        String sg = "ATOMIC_POSITIONS {crystal_sg}\n  Si 0.0 0.0 0.0\n";
        assertFalse(hasCode(new QECardAudit().auditDeckText(sg, "7.6"),
                QECardAudit.CODE_UNKNOWN_OPTION), "CRYSTAL_SG is a mined option");
    }

    @Test
    void kpointsUnknownOptionIsToleratedWithSilentTpibaDefault() {
        String deck = "K_POINTS {nonsense}\n  1\n  0.0 0.0 0.0 1.0\n";
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(deck, "7.6");
        assertTrue(hasCode(issues, QECardAudit.CODE_UNKNOWN_OPTION), issues.toString());
        ValidationIssue issue = byCode(issues, QECardAudit.CODE_UNKNOWN_OPTION);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity(),
                "card_kpoints silently defaults to tpiba - tolerate, never abort");
        assertTrue(issue.getMessage().contains("tpiba"), issue.getMessage());
    }

    @Test
    void cellParametersStrangeUnitIsDeprecatedNotFatal() {
        String deck = "CELL_PARAMETERS {furlong}\n"
                + "  1 0 0\n  0 1 0\n  0 0 1\n";
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(deck, "7.6");
        assertTrue(hasCode(issues, QECardAudit.CODE_UNKNOWN_OPTION), issues.toString());
        assertEquals(ValidationSeverity.WARNING,
                byCode(issues, QECardAudit.CODE_UNKNOWN_OPTION).getSeverity(),
                "CELL_PARAMETERS ELSE arm is DEPRECATED + cell_units='none'");
    }

    @Test
    void automaticMeshConstraintsAreTheMinedOnes() {
        assertTrue(new QECardAudit()
                .auditDeckText("K_POINTS {automatic}\n  4 4 4 1 1 1\n", "7.6")
                .isEmpty());
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("K_POINTS {automatic}\n  0 4 4 1 1 1\n", "7.6"),
                QECardAudit.CODE_MESH), "nk_i must be > 0 (mined errore)");
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("K_POINTS {automatic}\n  4 4 4 1 1 2\n", "7.6"),
                QECardAudit.CODE_MESH), "offsets must be 0 or 1 (mined errore)");
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("K_POINTS {automatic}\n  4 4 4\n", "7.6"),
                QECardAudit.CODE_MESH), "the READ needs six integers");
    }

    @Test
    void namelistBodiesAndCommentsAreNotCardScanned() {
        String deck = "&CONTROL\n   pseudo_dir = './pseudo'\n   prefix = 'K_POINTS'\n/\n"
                + "! HUBBARD {orthoatomic} is only a comment here\n"
                + "! DIPOLE\n";
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(deck, "7.5");
        assertTrue(issues.isEmpty(), () -> "unexpected findings: " + issues);
    }

    @Test
    void progGatesMirrorReadCardsButKpointsStaysCleanUnderPw() {
        // KSOUT is processed under PW only to be warned-ignored afterwards.
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("KSOUT\n", "7.6"),
                QECardAudit.CODE_GATE));
        // WANNIER_AC dispatches only under prog='WA': ignored by plain pw.x.
        assertTrue(hasCode(new QECardAudit()
                        .auditDeckText("WANNIER_AC\n", "7.6"),
                QECardAudit.CODE_GATE));
        // SOLVENTS needs the trism switch - the audit names the condition.
        ValidationIssue solvents = byCode(new QECardAudit()
                        .auditDeckText("SOLVENTS {1/cell}\n", "7.6"),
                QECardAudit.CODE_GATE);
        assertTrue(solvents.getMessage().contains("trism"), solvents.getMessage());
        // ...but K_POINTS warns only under CP, so a pw.x deck earns nothing.
        assertFalse(hasCode(new QECardAudit()
                        .auditDeckText("K_POINTS {gamma}\n", "7.6"),
                QECardAudit.CODE_GATE), "K_POINTS must stay clean under prog='PW'");
    }

    @Test
    void versionWindowIsHonouredAndUnknownVersionsFallToNewest() {
        String wrong = "HUBBARD {gaussian}\n";
        List<ValidationIssue> oldest =
                new QECardAudit().auditDeckText(wrong, "7.2");
        assertTrue(hasCode(oldest, QECardAudit.CODE_UNKNOWN_OPTION),
                "the HUBBARD switch is mined stable 0x1F across 7.2..7.6");
        List<ValidationIssue> defaulted =
                new QECardAudit().auditDeckText(wrong, null);
        assertTrue(hasCode(defaulted, QECardAudit.CODE_UNKNOWN_OPTION));
        assertTrue(byCode(defaulted, QECardAudit.CODE_UNKNOWN_OPTION)
                .getMessage().contains("7.6"), "null version audits at the newest tag");
    }

    @Test
    void blankAndNullAuditEmptyAndOversizeRefuses() {
        assertTrue(new QECardAudit().auditDeckText(null, "7.6").isEmpty());
        assertTrue(new QECardAudit().auditDeckText("   \n", "7.6").isEmpty());
        String huge = "K_POINTS {gamma}\n" + "0.0 ".repeat(400_000);
        List<ValidationIssue> issues =
                new QECardAudit().auditDeckText(huge, "7.6");
        assertTrue(hasCode(issues, QECardAudit.CODE_TEXT_LIMIT),
                "oversize text refuses honestly instead of truncating");
    }
}
