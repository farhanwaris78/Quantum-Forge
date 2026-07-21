/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import quantumforge.input.QESCFInput;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.schema.QENamelistSchema.Kind;

/**
 * Batch 150 pins of the thin QEInput adapter: the exact same issues the
 * pair-level {@link QESchemaAudit} core returns, produced from a live
 * {@link QESCFInput}. The adapter adds no verdicts of its own - reachability
 * (eight namelist surfaces) and empty-input behaviour are its only contract.
 */
class QESchemaValidatorTest {

    private static boolean hasCode(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    private static QESCFInput minimalScf() {
        QESCFInput input = new QESCFInput();
        QENamelist control = input.getNamelist("CONTROL");
        control.setValue("calculation = 'scf'");
        QENamelist system = input.getNamelist("SYSTEM");
        system.setValue("ibrav = 2");
        system.setValue("nat = 1");
        system.setValue("ntyp = 1");
        system.setValue("ecutwfc = 40");
        system.setValue("gcscf_mu = 0");
        return input;
    }

    @Test
    void aCleanDeckAuditsCleanThroughTheAdapter() {
        List<ValidationIssue> issues = new QESchemaValidator().validate(minimalScf(), "7.5");
        assertTrue(issues.isEmpty(), () -> "unexpected findings: " + issues);
    }

    @Test
    void adapterIssuesMatchTheCoreVerifierVerbatim() {
        QESCFInput input = minimalScf();
        input.getNamelist("ELECTRONS").setValue("diago_ppcg_maxiter = 10");
        List<ValidationIssue> adapted = new QESchemaValidator().validate(input, "7.5");
        Map<String, Map<String, String>> pairs = Map.of(
                "CONTROL", new TreeMap<>(Map.of("calculation", "'scf'")),
                "ELECTRONS", new TreeMap<>(Map.of("diago_ppcg_maxiter", "10")),
                "SYSTEM", new TreeMap<>(Map.of("ibrav", "2", "nat", "1", "ntyp", "1",
                        "ecutwfc", "40", "gcscf_mu", "0")));
        List<ValidationIssue> core = new QESchemaAudit().validatePairs(Kind.PW, "7.5", pairs);
        assertEquals(core.stream().map(ValidationIssue::getCode).sorted().toList(),
                adapted.stream().map(ValidationIssue::getCode).sorted().toList(),
                "the adapter is a verbatim re-feed of the pair core");
        assertTrue(hasCode(adapted, QESchemaAudit.CODE_NOT_IN_VERSION),
                "diago_ppcg_maxiter left the grammar after 7.4 (mined ground truth)");
    }

    @Test
    void hardEnumRejectionSurfacesThroughTheAdapter() {
        QESCFInput input = minimalScf();
        input.getNamelist("CONTROL").setValue("calculation = 'banana'");
        List<ValidationIssue> issues = new QESchemaValidator().validate(input, "7.5");
        assertTrue(hasCode(issues, QESchemaAudit.CODE_VALUE_REJECTED));
    }

    @Test
    void aCaseOnlyValueStillRejectsLikeFortran() {
        QESCFInput input = minimalScf();
        input.getNamelist("CONTROL").setValue("calculation = 'SCF'");
        List<ValidationIssue> issues = new QESchemaValidator().validate(input, "7.5");
        assertTrue(hasCode(issues, QESchemaAudit.CODE_VALUE_REJECTED),
                "uppercase SCF must stay rejected - the QE switch is exact match");
    }

    @Test
    void typedLiteralAndUnknownAdvisoriesSurfaceUnchanged() {
        QESCFInput input = minimalScf();
        QENamelist system = input.getNamelist("SYSTEM");
        system.setValue("nspin = abc");
        system.setValue("made_up_keyword = 3");
        List<ValidationIssue> issues = new QESchemaValidator().validate(input, "7.5");
        assertTrue(hasCode(issues, QESchemaAudit.CODE_TYPE_MISMATCH));
        assertTrue(hasCode(issues, QESchemaAudit.CODE_UNKNOWN));
    }

    @Test
    void emptyInputsAuditToEmptyVerdictsNeverFabricated() {
        assertTrue(new QESchemaValidator().validate(null, "7.5").isEmpty(),
                "a null input has no keywords - nothing to report here");
    }

    @Test
    void unsupportedVersionsRefuseLoudlyNotSilently() {
        assertThrows(IllegalArgumentException.class,
                () -> new QESchemaValidator().validate(minimalScf(), "6.8"));
        assertThrows(NullPointerException.class,
                () -> new QESchemaValidator().validate(minimalScf(), null));
    }
}
