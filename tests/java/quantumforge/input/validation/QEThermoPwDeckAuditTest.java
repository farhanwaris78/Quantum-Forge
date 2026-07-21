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
