/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import quantumforge.input.schema.QENamelistSchema.Kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the version-aware schema audit end to end on raw namelist maps:
 * every severity decision must mirror what the QE binaries actually do with
 * the offending input (abort at namelist read / errore validation vs silent
 * remap vs tolerate), and unknown input stays reported-but-unjudged. The
 * REQUIRED set is the mined one (PW: ibrav, nat, ntyp, ecutwfc, gcscf_mu,
 * fcp_mu@FCP, nsolv@RISM; PH: ahc_nbnd) and the tests name it exactly.
 */
class QESchemaAuditTest {

    private final QESchemaAudit audit = new QESchemaAudit();

    private static Map<String, String> map(String... kv) {
        Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put(kv[i], kv[i + 1]);
        }
        return out;
    }

    private static Map<String, Map<String, String>> pairs(Object... namelists) {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i + 1 < namelists.length; i += 2) {
            out.put((String) namelists[i], (Map<String, String>) namelists[i + 1]);
        }
        return out;
    }

    /** A SYSTEM deck whose REPORTABLE required keys are all set (gcscf_mu included). */
    private static Map<String, String> fullSystem(String... extra) {
        Map<String, String> out = map("ibrav", "2", "nat", "2", "ntyp", "1",
                "ecutwfc", "40", "gcscf_mu", "0");
        for (int i = 0; i + 1 < extra.length; i += 2) {
            out.put(extra[i], extra[i + 1]);
        }
        return out;
    }

    private static List<ValidationIssue> withCode(List<ValidationIssue> issues, String code) {
        return issues.stream().filter(i -> code.equals(i.getCode())).toList();
    }

    private static ValidationIssue onlyWithCode(List<ValidationIssue> issues, String code) {
        List<ValidationIssue> matching = withCode(issues, code);
        assertEquals(1, matching.size(), () -> "expected exactly one " + code + " in " + issues);
        return matching.get(0);
    }

    @Test
    void aCompleteDeckAuditsClean() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem("occupations", "'fixed'", "celldm(1)", "7.64"),
                      "CONTROL", map("calculation", "'scf'", "tprnfor", ".true.")));
        assertTrue(issues.isEmpty(), () -> "unexpected issues: " + issues);
    }

    @Test
    void requiredBindsOnlyToNamelistsInUse() {
        // deck without &FCP: the conditional namelist owes nothing
        List<ValidationIssue> noFcp = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem()));
        assertTrue(withCode(noFcp, QESchemaAudit.CODE_REQUIRED_MISSING).isEmpty(),
                () -> "fcp_mu must not fire without &FCP: " + noFcp);
        // deck with &FCP present: its REQUIRED keyword is now owed
        List<ValidationIssue> withFcp = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem(), "FCP", map()));
        ValidationIssue issue = onlyWithCode(withFcp, QESchemaAudit.CODE_REQUIRED_MISSING);
        assertTrue(issue.getMessage().contains("fcp_mu"));
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
    }

    @Test
    void requiredAdvisoryNamesTheMinedSystemSet() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", map("ibrav", "2", "nat", "2")));
        List<ValidationIssue> missing = withCode(issues, QESchemaAudit.CODE_REQUIRED_MISSING);
        assertEquals(3, missing.size(), () -> "ntyp, ecutwfc and gcscf_mu advisories: " + issues);
        assertTrue(missing.stream().allMatch(i -> i.getSeverity() == ValidationSeverity.WARNING));
        assertTrue(missing.stream().anyMatch(i -> i.getMessage().contains("ntyp")));
        assertTrue(missing.stream().anyMatch(i -> i.getMessage().contains("ecutwfc")));
        assertTrue(missing.stream().anyMatch(i -> i.getMessage().contains("gcscf_mu")));
        assertTrue(missing.stream().allMatch(i -> i.getDocumentationUrl().contains("INPUT_PW")));
        assertTrue(withCode(issues, QESchemaAudit.CODE_UNKNOWN).isEmpty());
    }

    @Test
    void unknownKeywordIsReportedNeverJudged() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem("turbo_magic", "3")));
        ValidationIssue issue = onlyWithCode(issues, QESchemaAudit.CODE_UNKNOWN);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
        assertTrue(issue.getMessage().contains("not present in the mined"));
        assertTrue(issue.getMessage().contains("not judged"));
        assertEquals(1, issues.size(), () -> "only the unknown advisory is expected: " + issues);
    }

    @Test
    void keywordInTheWrongNamelistIsAFatalReadError() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("CONTROL", map("calculation", "'scf'", "ecutwfc", "40")));
        ValidationIssue placement = onlyWithCode(issues, QESchemaAudit.CODE_WRONG_NAMELIST);
        assertEquals(ValidationSeverity.ERROR, placement.getSeverity());
        assertTrue(placement.getMessage().contains("SYSTEM"),
                "must say where the schema places ecutwfc");
        // REQUIRED advisories bind only to namelists in use: &SYSTEM is absent
        // from this deck entirely, so none fire (nothing is invented about an
        // absent namelist).
        assertTrue(withCode(issues, QESchemaAudit.CODE_REQUIRED_MISSING).isEmpty(),
                () -> "no required advisories for absent namelists: " + issues);
        assertEquals(1, issues.size(), () -> "only the placement error: " + issues);
    }

    @Test
    void versionDroppedKeywordStopsThatVersion() {
        // Ground-truth check (miner provenance): diago_ppcg_maxiter leaves the
        // ELECTRONS namelist after 7.4; hubbard_alpha was considered first and
        // rejected as a pin BECAUSE it survives all five tags (scalar->array).
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem(), "ELECTRONS",
                        map("diago_ppcg_maxiter", "10")));
        ValidationIssue issue = onlyWithCode(issues, QESchemaAudit.CODE_NOT_IN_VERSION);
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity());
        assertTrue(issue.getMessage().contains("7.2-7.4"),
                "must name the window where the keyword is valid");
        assertEquals(1, issues.size(), () -> "only the version finding: " + issues);
    }

    @Test
    void versionDroppedKeywordIsLegalInsideItsWindow() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.2",
                pairs("SYSTEM", fullSystem(), "ELECTRONS",
                        map("diago_ppcg_maxiter", "10")));
        assertTrue(issues.isEmpty(), () -> "unexpected issues at 7.2: " + issues);
    }

    @Test
    void versionJoinedValueStillRejectsForTheEarlierTag() {
        // 'direct' joins the diagonalization switch only at 7.6 (verified per
        // tag against pw_input.f90): legal at 7.6, a stop-sign at 7.2.
        List<ValidationIssue> at76 = audit.validatePairs(Kind.PW, "7.6",
                pairs("SYSTEM", fullSystem(), "ELECTRONS",
                        map("diagonalization", "'direct'")));
        assertTrue(withCode(at76, QESchemaAudit.CODE_VALUE_REJECTED).isEmpty(),
                () -> "'direct' must be legal at 7.6: " + at76);
        List<ValidationIssue> at72 = audit.validatePairs(Kind.PW, "7.2",
                pairs("SYSTEM", fullSystem(), "ELECTRONS",
                        map("diagonalization", "'direct'")));
        ValidationIssue issue = onlyWithCode(at72, QESchemaAudit.CODE_VALUE_REJECTED);
        assertTrue(issue.getMessage().contains("QE 7.2"),
                "the rejection names the exact version whose switch lacks the arm");
    }

    @Test
    void hardEnumRejectionNamesTheAcceptedValues() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("CONTROL", map("calculation", "'banana'"), "SYSTEM", fullSystem()));
        ValidationIssue issue = onlyWithCode(issues, QESchemaAudit.CODE_VALUE_REJECTED);
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity());
        assertTrue(issue.getMessage().contains("scf"));
        assertTrue(issue.getMessage().contains("aborts"));
        assertEquals(1, issues.size(), () -> "only the rejection: " + issues);
    }

    @Test
    void hardEnumComparisonIsCaseExactLikeFortran() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("CONTROL", map("calculation", "'SCF'"), "SYSTEM", fullSystem()));
        assertEquals(1, withCode(issues, QESchemaAudit.CODE_VALUE_REJECTED).size(),
                () -> "uppercase SCF must not be folded valid: " + issues);
    }

    @Test
    void softEnumDriftIsAWarningNotARejection() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("CONTROL", map("calculation", "'scf'", "disk_io", "'banana'"),
                      "SYSTEM", fullSystem()));
        ValidationIssue issue = onlyWithCode(issues, QESchemaAudit.CODE_VALUE_UNDOCUMENTED);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
        assertTrue(issue.getMessage().contains("SILENTLY"));
        assertTrue(issue.getMessage().contains("nowf"));
        assertEquals(1, issues.size(), () -> "only the soft advisory: " + issues);
    }

    @Test
    void typedLiteralsAreCheckedLikeTheFortranReader() {
        List<ValidationIssue> bad = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem("nspin", "abc", "degauss", "soon", "force_symmorphic", "yes")));
        assertEquals(3, withCode(bad, QESchemaAudit.CODE_TYPE_MISMATCH).size(),
                () -> "three type mismatches expected: " + bad);
        assertTrue(withCode(bad, QESchemaAudit.CODE_TYPE_MISMATCH).stream()
                .allMatch(i -> i.getSeverity() == ValidationSeverity.ERROR));
    }

    @Test
    void fortranExponentAndSignLiteralsAreAccepted() {
        List<ValidationIssue> ok = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem("ecutwfc", "4.0D1", "degauss", "2.5d-3",
                                "tot_charge", "-1", "force_symmorphic", ".TRUE."),
                      "ELECTRONS", map("conv_thr", "1.D-8", "electron_maxstep", "300")));
        assertTrue(ok.isEmpty(), () -> "valid Fortran literals misread: " + ok);
    }

    @Test
    void indexedSpellingResolvesToTheBaseKeyword() {
        List<ValidationIssue> issues = audit.validatePairs(Kind.PW, "7.5",
                pairs("SYSTEM", fullSystem("starting_magnetization(2)", "0.4")));
        assertTrue(issues.isEmpty(), () -> "indexed array keyword misread: " + issues);
    }

    @Test
    void phAndHpDecksAuditAgainstTheirOwnGrammar() {
        // a SYSTEM namelist does not exist in the ph.x grammar at all
        List<ValidationIssue> ph = audit.validatePairs(Kind.PH, "7.5",
                pairs("SYSTEM", map("tr2_ph", "1.D-12", "ldisp", ".true.", "nq1", "4")));
        assertEquals(3, withCode(ph, QESchemaAudit.CODE_WRONG_NAMELIST).size(),
                () -> "ph keywords belong in INPUTPH: " + ph);
        // no ahc_nbnd advisory either: INPUTPH is absent, and REQUIRED binds
        // only to namelists in use
        assertTrue(withCode(ph, QESchemaAudit.CODE_REQUIRED_MISSING).isEmpty(),
                () -> "no required advisories for absent INPUTPH: " + ph);

        List<ValidationIssue> cleanPh = audit.validatePairs(Kind.PH, "7.5",
                pairs("INPUTPH", map("tr2_ph", "1.D-12", "ldisp", ".true.", "nq1", "4",
                        "ahc_nbnd", "8")));
        assertTrue(cleanPh.isEmpty(), () -> "clean INPUTPH deck misread: " + cleanPh);

        List<ValidationIssue> cleanHp = audit.validatePairs(Kind.HP, "7.5",
                pairs("INPUTHP", map("prefix", "'pwscf'", "find_atpert", "1", "docc_thr", "5.D-5")));
        assertTrue(cleanHp.isEmpty(), () -> "clean INPUTHP deck misread: " + cleanHp);

        // a pw.x keyword framed for hp.x fails closed, reported-but-unjudged
        List<ValidationIssue> wrong = audit.validatePairs(Kind.HP, "7.5",
                pairs("INPUTHP", map("ecutwfc", "40")));
        ValidationIssue issue = onlyWithCode(wrong, QESchemaAudit.CODE_UNKNOWN);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
        assertEquals(1, wrong.size(), () -> "only the unknown advisory: " + wrong);
    }

    @Test
    void guardsRefuseBadCalls() {
        assertThrows(NullPointerException.class,
                () -> audit.validatePairs(null, "7.5", Map.of()));
        assertThrows(NullPointerException.class,
                () -> audit.validatePairs(Kind.PW, null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> audit.validatePairs(Kind.PW, "6.8", Map.of()));
    }
}
