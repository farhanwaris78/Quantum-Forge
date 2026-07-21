/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class QEThermoPwDeckAuditTest {

    private static boolean hasCode(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    @Test
    void aValidMurLcTControlAuditsClean() {
        String deck = "&INPUT_THERMO\n"
                + "   what = 'mur_lc_t'\n"
                + "   ngeo = 6\n"
                + "   tmin = 0.0\n"
                + "   tmax = 800.0\n"
                + "   deltat = 3.0\n"
                + "   lgnuplot = .false.\n"
                + "/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(deck);
        assertTrue(issues.isEmpty(), () -> "unexpected findings: " + issues);
        // the same deck pins cleanly at every window label it uses:
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(deck, "2.0.0").isEmpty());
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(deck, "2.1.1").isEmpty());
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(deck, "master").isEmpty());
    }

    @Test
    void masterOnlyKeywordsAreFatalAtOlderPinnedReleases() {
        String deck = "&INPUT_THERMO\n   what = 'mur_lc_t'\n   ltau_from_file = .true.\n/\n";
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(deck, "master").isEmpty());
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(deck, "2.1.1");
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_ABSENT_AT_VERSION),
                issues.toString());
        ValidationIssue issue = issues.stream()
                .filter(i -> i.getCode().equals(QEThermoPwDeckAudit.CODE_ABSENT_AT_VERSION))
                .findFirst().orElseThrow();
        assertTrue(issue.getMessage().contains("first declared in master"),
                issue.getMessage());
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity(),
                "at 2.1.1 the namelist READ aborts exactly like an unknown keyword");

        // tag-only keywords get the removed-after reading at master:
        String eps = "&INPUT_THERMO\n   what = 'scf_elastic_constants'\n   epsilon_0 = 0.0\n/\n";
        List<ValidationIssue> masterIssues =
                new QEThermoPwDeckAudit().auditDeckText(eps, "master");
        assertTrue(hasCode(masterIssues, QEThermoPwDeckAudit.CODE_ABSENT_AT_VERSION),
                masterIssues.toString());
        assertTrue(masterIssues.stream()
                .filter(i -> i.getCode().equals(QEThermoPwDeckAudit.CODE_ABSENT_AT_VERSION))
                .findFirst().orElseThrow().getMessage().contains("removed"),
                "epsilon_0 was removed from the NAMELIST before master");
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(eps, "2.1.1").isEmpty());
    }

    @Test
    void magneticWhatValuesDispatchOnlyAtMaster() {
        String deck = "&INPUT_THERMO\n   what = 'scf_magnetic_susceptibility'\n/\n";
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(deck, "master").isEmpty());
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(deck, "2.1.1");
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_WHAT_ABSENT_AT_VERSION),
                issues.toString());
        assertTrue(issues.stream()
                .filter(i -> i.getCode().equals(QEThermoPwDeckAudit.CODE_WHAT_ABSENT_AT_VERSION))
                .findFirst().orElseThrow().getMessage().contains("first appears in master"));
    }

    @Test
    void gruneisenRulesBindFrom21xAndAreMaskGuarded() {
        String bad = "&INPUT_THERMO\n   what = 'mur_lc'\n"
                + "   lgruneisen_gen = .true.\n   lmurn = .true.\n/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(bad, "2.1.1");
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_GRUNEISEN_LMURN), issues.toString());
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_GRUNEISEN_WHAT), issues.toString());
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_GRUNEISEN_SILENT),
                "the silent poly_degree overwrite is always reported when active");

        // at 2.0.3 the keyword itself is absent -> a DIFFERENT fatal code, and
        // the gruneisen cross-rules stay silent (they don't exist there).
        List<ValidationIssue> old = new QEThermoPwDeckAudit().auditDeckText(bad, "2.0.3");
        assertTrue(hasCode(old, QEThermoPwDeckAudit.CODE_ABSENT_AT_VERSION), old.toString());
        assertFalse(hasCode(old, QEThermoPwDeckAudit.CODE_GRUNEISEN_LMURN));
        assertFalse(hasCode(old, QEThermoPwDeckAudit.CODE_GRUNEISEN_WHAT));

        // the sanctioned combination stays clean on the cross-rules:
        String good = "&INPUT_THERMO\n   what = 'mur_lc_t'\n"
                + "   lgruneisen_gen = .true.\n/\n";
        List<ValidationIssue> goodIssues =
                new QEThermoPwDeckAudit().auditDeckText(good, "2.1.0");
        assertFalse(hasCode(goodIssues, QEThermoPwDeckAudit.CODE_GRUNEISEN_LMURN));
        assertFalse(hasCode(goodIssues, QEThermoPwDeckAudit.CODE_GRUNEISEN_WHAT));
        assertTrue(hasCode(goodIssues, QEThermoPwDeckAudit.CODE_GRUNEISEN_SILENT),
                "the silent overwrite remains WARNED even in the valid combination");
    }

    @Test
    void typeAdjudicationUsesThePinnedRelease() {
        // old_ec is LOGICAL('.TRUE.') at 2.0.0 and INTEGER(0) since 2.0.1:
        String deck = "&INPUT_THERMO\n   what = 'scf_elastic_constants'\n   old_ec = .true.\n/\n";
        List<ValidationIssue> at200 = new QEThermoPwDeckAudit().auditDeckText(deck, "2.0.0");
        assertFalse(hasCode(at200, QEThermoPwDeckAudit.CODE_TYPE_MISMATCH),
                ".true. IS the mined 2.0.0 shape: " + at200);
        List<ValidationIssue> atMaster = new QEThermoPwDeckAudit().auditDeckText(deck, "master");
        assertTrue(hasCode(atMaster, QEThermoPwDeckAudit.CODE_TYPE_MISMATCH),
                ".true. is not an integer literal at master: " + atMaster);
    }

    @Test
    void unknownKeywordsAreErrorsBecauseTheReadAborts() {
        String deck = "&INPUT_THERMO\n   what = 'mur_lc'\n   tnin = 5.0\n/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(deck);
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_UNKNOWN), issues.toString());
        ValidationIssue issue = issues.stream()
                .filter(i -> i.getCode().equals(QEThermoPwDeckAudit.CODE_UNKNOWN))
                .findFirst().orElseThrow();
        assertTrue(issue.getMessage().contains("tnin"), issue.getMessage());
        assertTrue(issue.getMessage().contains("aborts"), issue.getMessage());
    }

    @Test
    void missingAndRejectedWhatAreBothFatalAndNamed() {
        String noWhat = "&INPUT_THERMO\n   tmin = 0.0\n/\n";
        assertTrue(hasCode(new QEThermoPwDeckAudit().auditDeckText(noWhat),
                QEThermoPwDeckAudit.CODE_WHAT_MISSING), "blank-by-absence what aborts");

        String blankWhat = "&INPUT_THERMO\n   what = ' '\n/\n";
        assertTrue(hasCode(new QEThermoPwDeckAudit().auditDeckText(blankWhat),
                QEThermoPwDeckAudit.CODE_WHAT_MISSING), "explicitly blank what aborts");

        String misTyped = "&INPUT_THERMO\n   what = 'scf_elastic'\n/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(misTyped);
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_WHAT_REJECTED), issues.toString());

        String upperCase = "&INPUT_THERMO\n   what = 'SCF'\n/\n";
        assertTrue(hasCode(new QEThermoPwDeckAudit().auditDeckText(upperCase),
                QEThermoPwDeckAudit.CODE_WHAT_REJECTED),
                "Fortran string equality is case-significant: 'SCF' aborts");

        String exact = "&INPUT_THERMO\n   what = 'scf'\n/\n";
        assertFalse(hasCode(new QEThermoPwDeckAudit().auditDeckText(exact),
                QEThermoPwDeckAudit.CODE_WHAT_REJECTED));
    }

    @Test
    void aggregateRuleBindsOnlyTheTwoSupportedWhatValues() {
        String bad = "&INPUT_THERMO\n   what = 'mur_lc'\n   all_geometries_together = .true.\n/\n";
        assertTrue(hasCode(new QEThermoPwDeckAudit().auditDeckText(bad),
                QEThermoPwDeckAudit.CODE_AGGREGATE_RULE));

        String good = "&INPUT_THERMO\n   what = 'mur_lc_t'\n"
                + "   all_geometries_together = .true.\n/\n";
        assertFalse(hasCode(new QEThermoPwDeckAudit().auditDeckText(good),
                QEThermoPwDeckAudit.CODE_AGGREGATE_RULE), good);

        String falseForm = "&INPUT_THERMO\n   what = 'mur_lc'\n"
                + "   all_geometries_together = .false.\n/\n";
        assertFalse(hasCode(new QEThermoPwDeckAudit().auditDeckText(falseForm),
                QEThermoPwDeckAudit.CODE_AGGREGATE_RULE),
                "'.false.' must not be read as true (it contains a 't')");
    }

    @Test
    void flextRemapIsAdvisoryAndNamed() {
        String deck = "&INPUT_THERMO\n   what = 'scf'\n   flext = '.png'\n/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(deck);
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_FLEXT_REMAPPED), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.getCode().equals(
                QEThermoPwDeckAudit.CODE_FLEXT_REMAPPED)
                && i.getMessage().contains("'.ps'")), issues.toString());

        String pdf = "&INPUT_THERMO\n   what = 'scf'\n   flext = '.pdf'\n/\n";
        assertFalse(hasCode(new QEThermoPwDeckAudit().auditDeckText(pdf),
                QEThermoPwDeckAudit.CODE_FLEXT_REMAPPED), pdf);
    }

    @Test
    void typeShapesAreAdvisoryWarningsNotErrors() {
        String deck = "&INPUT_THERMO\n   what = 'mur_lc'\n   ngeo = 'lots'\n/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(deck);
        assertTrue(hasCode(issues, QEThermoPwDeckAudit.CODE_TYPE_MISMATCH), issues.toString());
        assertTrue(issues.stream().allMatch(i -> i.getCode().equals(
                QEThermoPwDeckAudit.CODE_TYPE_MISMATCH)
                ? i.getMessage().contains("INFERRED") : true), issues.toString());
    }

    @Test
    void boundariesFailClosedAndIndexedArraysStayKnown() {
        assertTrue(new QEThermoPwDeckAudit().auditDeckText(null).isEmpty());
        assertTrue(new QEThermoPwDeckAudit().auditDeckText("  \n ").isEmpty());

        String indexed = "&INPUT_THERMO\n   what = 'mur_lc_t'\n   temp_plot(1) = 200.0\n/\n";
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(indexed);
        assertFalse(hasCode(issues, QEThermoPwDeckAudit.CODE_UNKNOWN), issues.toString());
    }
}
