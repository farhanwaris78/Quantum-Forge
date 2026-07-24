/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.QEVersionRuleCatalog.AuditEntry;
import quantumforge.input.QEVersionRuleCatalog.AuditVerdict;
import quantumforge.input.QEVersionRuleCatalog.Rule;
import quantumforge.input.namelist.QEValueBase;

class QEVersionRuleCatalogTest {

    @Test
    void curatedSnapshotCoversThePlannedSlice() {
        assertEquals(35, QEVersionRuleCatalog.listRules().size(),
                "16 CONTROL + 19 SYSTEM curated rules");
        assertTrue(QEVersionRuleCatalog.listRules().stream()
                        .allMatch(rule -> !rule.getNote().isBlank()),
                "every curated rule carries a human-readable note");
        assertTrue(QEVersionRuleCatalog.listRules().stream()
                        .allMatch(rule -> rule.getDocsUrl().contains("INPUT_PW")),
                "every rule points at the stable upstream docs URL");
        Rule calculation = QEVersionRuleCatalog.find("CONTROL", "calculation")
                .orElseThrow();
        assertEquals(7, calculation.getAllowedValues().size(),
                "scf nscf bands relax md vc-relax vc-md - cp deliberately absent");
        assertTrue(calculation.getAllowedValues().contains("vc-md"));
        assertFalse(calculation.getAllowedValues().contains("cp"));
    }

    @Test
    void caseFreeLookupAndHonestMisses() {
        assertTrue(QEVersionRuleCatalog.find("system", "Occupations").isPresent(),
                "keyword matching is case-free");
        assertTrue(QEVersionRuleCatalog.find("CONTROL", "wf_collect")
                        .orElseThrow().isRemoved(),
                "wf_collect is the classic 7.x removal");
        assertTrue(QEVersionRuleCatalog.find("CONTROL", "made_up_keyword").isEmpty(),
                "an unknown keyword is simply not in the curated slice");
        assertTrue(QEVersionRuleCatalog.find("ELECTRONS", "calculation").isEmpty(),
                "namelist attribution is part of the rule key");
    }

    @Test
    void supportedVersionsDelimitTheWindow() {
        assertTrue(QEVersionRuleCatalog.isSupportedVersion("7.5"));
        assertTrue(QEVersionRuleCatalog.isSupportedVersion("7.2"));
        assertTrue(QEVersionRuleCatalog.isSupportedVersion(" 7.4 "),
                "whitespace-tolerant");
        assertFalse(QEVersionRuleCatalog.isSupportedVersion("6.8"),
                "outside the curated window - never judged");
        assertFalse(QEVersionRuleCatalog.isSupportedVersion(null));
    }

    @Test
    void valueMembershipIsQuoteStrippedAndLowerCase() {
        assertEquals("m-v", QEVersionRuleCatalog.normalizeForComparison("'M-V'"));
        assertEquals("smearing",
                QEVersionRuleCatalog.normalizeForComparison(" \"SMearing\" "));
        assertEquals("", QEVersionRuleCatalog.normalizeForComparison(null));
    }

    @Test
    void auditVerdictsPinTheFourOutcomes() {
        AuditEntry ok = QEVersionRuleCatalog.audit("SYSTEM",
                QEValueBase.getInstance("smearing", "'cold'"));
        assertEquals(AuditVerdict.OK, ok.getVerdict(),
                "cold is the marzari-vanderbilt alias in the window");
        assertEquals("'cold'", ok.getValueEcho(), "the echo stays verbatim");

        AuditEntry cp = QEVersionRuleCatalog.audit("CONTROL",
                QEValueBase.getInstance("calculation", "'cp'"));
        assertEquals(AuditVerdict.VALUE_WARNING, cp.getVerdict(),
                "cp is not a valid pw.x calculation - flagged, ties to CP_INPUT_DRAFT");
        assertTrue(cp.getNote().contains("outside the curated CONTROL.calculation set"), cp.getNote());

        AuditEntry badSmear = QEVersionRuleCatalog.audit("SYSTEM",
                QEValueBase.getInstance("smearing", "'not-a-smearing'"));
        assertEquals(AuditVerdict.VALUE_WARNING, badSmear.getVerdict());
        assertTrue(badSmear.getNote().contains("curated SYSTEM.smearing set"),
                badSmear.getNote());

        AuditEntry removed = QEVersionRuleCatalog.audit("CONTROL",
                QEValueBase.getInstance("wf_collect", ".true."));
        assertEquals(AuditVerdict.REMOVED_KEYWORD, removed.getVerdict());
        assertTrue(removed.getNote().contains("delete it"), removed.getNote());

        AuditEntry outside = QEVersionRuleCatalog.audit("SYSTEM",
                QEValueBase.getInstance("input_dft", "'PBE'"));
        assertEquals(AuditVerdict.NOT_IN_CURATED, outside.getVerdict(),
                "reported, deliberately NOT judged");
        assertTrue(outside.getNote().contains("NOT judged"), outside.getNote());
    }

    @Test
    void freeFormRulesSkipValueChecks() {
        AuditEntry ibrav = QEVersionRuleCatalog.audit("SYSTEM",
                QEValueBase.getInstance("ibrav", "4"));
        assertEquals(AuditVerdict.OK, ibrav.getVerdict(),
                "ibrav has no curated value set in this slice - echoed, not checked");

        AuditEntry ecutwfc = QEVersionRuleCatalog.audit("SYSTEM",
                QEValueBase.getInstance("ecutwfc", "60.0"));
        assertEquals(AuditVerdict.OK, ecutwfc.getVerdict());
    }
}
