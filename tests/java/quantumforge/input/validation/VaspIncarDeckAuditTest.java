/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Batch-173 pins for {@link VaspIncarDeckAudit}: every consistency rule
 * fires on a crafted deck with its wiki-verbatim quote, and severities
 * mirror VASP's documented behavior (unknown tags are silent in VASP, so
 * they are WARNING here; crash-class facts are ERROR).
 */
class VaspIncarDeckAuditTest {

    private static List<ValidationIssue> audit(String deck) {
        return VaspIncarDeckAudit.auditDeckText(deck);
    }

    private static boolean hasCode(List<ValidationIssue> issues, String code) {
        return issues.stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    private static ValidationIssue find(List<ValidationIssue> issues,
            String code) {
        return issues.stream()
                .filter(issue -> issue.getCode().equals(code))
                .findFirst().orElseThrow();
    }

    @Test
    void tier1RulebookRejectsWrongTypesAndOptions() {
        List<ValidationIssue> issues = audit(
                "ENCUT = high\n"
                + "ISMEAR = -16\n"
                + "LDAU = maybe\n"
                + "MAGMOM = 2*5.0 broken\n"
                + "LDAUL = 2 -1 2.5\n");
        assertTrue(hasCode(issues, VaspIncarDeckAudit.CODE_TYPE),
                issues.toString());
        assertTrue(hasCode(issues, VaspIncarDeckAudit.CODE_OPTION),
                issues.toString());
        assertTrue(hasCode(issues, VaspIncarDeckAudit.CODE_ARRAY),
                issues.toString());
        // ENCUT is REAL, LDAU is LOGICAL -> two TYPE findings
        assertEquals(2, issues.stream().filter(issue -> issue.getCode()
                .equals(VaspIncarDeckAudit.CODE_TYPE)).count());
        // MAGMOM (real array) + LDAUL (integer array, '2.5' non-integral)
        assertEquals(2, issues.stream().filter(issue -> issue.getCode()
                .equals(VaspIncarDeckAudit.CODE_ARRAY)).count());
        ValidationIssue ismear = issues.stream()
                .filter(issue -> issue.getCode()
                        .equals(VaspIncarDeckAudit.CODE_OPTION))
                .findFirst().orElseThrow();
        assertEquals(ValidationSeverity.ERROR, ismear.getSeverity());
        assertTrue(ismear.getMessage().contains("-15"),
                "the pinned option set is quoted: " + ismear.getMessage());
        // ISMEAR's '>0' option accepts every positive integer (wiki TAGDEF)
        List<ValidationIssue> mp = audit("ENCUT = 400\nISMEAR = 2\n");
        assertFalse(hasCode(mp, VaspIncarDeckAudit.CODE_OPTION),
                "Methfessel-Paxton order 2 is a pinned option: " + mp);
        // ICHARG = 10 is the wiki's 'less convenient value' of the +10 family
        List<ValidationIssue> icharg = audit("ENCUT = 400\nICHARG = 10\n");
        assertFalse(hasCode(icharg, VaspIncarDeckAudit.CODE_OPTION),
                "ICHARG=10 is legal per the wiki text: " + icharg);
    }

    @Test
    void unknownTagsAreWarningsBecauseVaspSilentlyIgnoresThem() {
        List<ValidationIssue> issues = audit("ENCUT = 400\nENCUTT = 500\n");
        ValidationIssue issue = find(issues, VaspIncarDeckAudit.CODE_UNKNOWN);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity(),
                "VASP does NOT abort on unknown tags - the audit mirrors that");
        assertTrue(issue.getMessage().contains("silently ignored"),
                "the load-bearing truth must travel with the finding: "
                        + issue.getMessage());
        assertTrue(issue.getMessage().contains("ENCUTT"),
                "the offending tag is named verbatim: " + issue.getMessage());
        assertTrue(issue.getDocumentationUrl().contains("vasp.at"),
                "every finding carries a wiki URL");
        // tier-2 names are INFO 'recognized, not audited'
        List<ValidationIssue> tier2 = audit("ENCUT = 400\nELPH_KSPACING = 0.1\n");
        ValidationIssue info = find(tier2, VaspIncarDeckAudit.CODE_TIER2);
        assertEquals(ValidationSeverity.INFO, info.getSeverity());
        assertTrue(info.getMessage().contains("recognized"));
    }

    @Test
    void duplicatesPinTheReviewBurdenWithALineCensus() {
        List<ValidationIssue> issues = audit(
                "ENCUT = 300\nISMEAR = 0\nENCUT = 500\n");
        ValidationIssue issue = find(issues,
                VaspIncarDeckAudit.CODE_DUPLICATE);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
        assertTrue(issue.getMessage().contains("lines 1, 3"),
                "the census carries both lines: " + issue.getMessage());
        assertTrue(issue.getMessage().contains(
                "does NOT state duplicate-tag semantics"),
                "the wiki window pins no duplicate semantics - honest quote: "
                        + issue.getMessage());
    }

    @Test
    void syntaxNotesSurfaceBackslashBlanksAndBareText() {
        List<ValidationIssue> issues = audit(
                "ALGO = \\ \n"
                + "  Fast\n"
                + "a bare prose line\n");
        assertTrue(hasCode(issues, VaspIncarDeckAudit.CODE_CONTINUATION),
                "the wiki-pinned backslash trap has its own code: " + issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.getCode()
                        .equals(VaspIncarDeckAudit.CODE_SYNTAX_NOTE)
                        && issue.getMessage().contains("no tag=values"
                                + " statement")),
                "bare text is surfaced with the pin-down advice: " + issues);
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage()
                        .contains("review")),
                "the review advice travels to the operator");
        List<ValidationIssue> quotes = audit("WANNIER90_WIN = \"\nopen\n");
        ValidationIssue quote = find(quotes, VaspIncarDeckAudit.CODE_QUOTES);
        assertEquals(ValidationSeverity.ERROR, quote.getSeverity(),
                "an unterminated quote swallows the file - VASP reading");
        List<ValidationIssue> refused = audit("");
        assertTrue(hasCode(refused, "VASP_INCAR_PARSE"),
                "a parse refusal becomes ONE typed error finding");
    }

    @Test
    void hybridRulesQuoteTheLhfcalcTrapVerbatim() {
        List<ValidationIssue> issues = audit(
                "ENCUT = 400\nLHFCALC = .TRUE.\nALGO = Fast\n");
        ValidationIssue issue = find(issues,
                VaspIncarDeckAudit.CODE_LHFCALC_FAST);
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
        assertTrue(issue.getMessage().contains("no warning is printed"),
                "wiki verbatim: " + issue.getMessage());
        List<ValidationIssue> damped = audit(
                "ENCUT = 400\nLHFCALC = .TRUE.\nALGO = Damped\n");
        assertFalse(hasCode(damped, VaspIncarDeckAudit.CODE_LHFCALC_FAST),
                "ALGO=Damped is the recommended hybrid route: " + damped);
    }

    @Test
    void parallelPrecedenceRuleQuotesTheWikiVerbatim() {
        List<ValidationIssue> issues = audit(
                "ENCUT = 400\nNCORE = 4\nNPAR = 2\n");
        ValidationIssue issue = find(issues,
                VaspIncarDeckAudit.CODE_NPAR_NCORE);
        assertTrue(issue.getMessage().contains("NPAR takes precedence"),
                "wiki verbatim precedence: " + issue.getMessage());
        List<ValidationIssue> clean = audit("ENCUT = 400\nNCORE = 4\n");
        assertFalse(hasCode(clean, VaspIncarDeckAudit.CODE_NPAR_NCORE));
    }

    @Test
    void dipoleRuleIsAnErrorWhenIdipolIsMissing() {
        List<ValidationIssue> issues = audit(
                "ENCUT = 400\nLDIPOL = .TRUE.\n");
        ValidationIssue issue = find(issues,
                VaspIncarDeckAudit.CODE_LDIPOL_IDIPOL);
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity());
        assertTrue(issue.getMessage().contains("IDIPOL has to be specified"),
                "wiki verbatim: " + issue.getMessage());
        List<ValidationIssue> clean = audit(
                "ENCUT = 400\nLDIPOL = .TRUE.\nIDIPOL = 3\n");
        assertFalse(hasCode(clean, VaspIncarDeckAudit.CODE_LDIPOL_IDIPOL));
    }

    @Test
    void densityLockingAndDftPlusURulesRecommendLmaxmix() {
        List<ValidationIssue> band = audit(
                "ENCUT = 400\nICHARG = 11\n");
        ValidationIssue ichargIssue = find(band,
                VaspIncarDeckAudit.CODE_ICHARG_LMAXMIX);
        assertTrue(ichargIssue.getMessage().contains("twice the maximum"
                + " l-quantum number"), "wiki verbatim: "
                + ichargIssue.getMessage());
        List<ValidationIssue> locked = audit(
                "ENCUT = 400\nICHARG = 11\nLMAXMIX = 4\n");
        assertFalse(hasCode(locked, VaspIncarDeckAudit.CODE_ICHARG_LMAXMIX),
                "LMAXMIX=4 satisfies the recommendation: " + locked);
        List<ValidationIssue> dftPlusU = audit(
                "ENCUT = 400\nLDAU = .TRUE.\nLDAUL = 2 -1\nLDAUU = 4.0 0.0\n"
                + "LDAUJ = 0.5 0.0\n");
        ValidationIssue ldauIssue = find(dftPlusU,
                VaspIncarDeckAudit.CODE_LDAU_LMAXMIX);
        assertEquals(ValidationSeverity.INFO, ldauIssue.getSeverity());
        assertTrue(ldauIssue.getMessage().contains("4 for d-electrons"),
                "wiki verbatim guidance: " + ldauIssue.getMessage());
    }

    @Test
    void mdRulesAreCrashClassErrors() {
        List<ValidationIssue> issues = audit(
                "ENCUT = 400\nIBRION = 0\n");
        ValidationIssue potim = find(issues,
                VaspIncarDeckAudit.CODE_POTIM_MD);
        assertEquals(ValidationSeverity.ERROR, potim.getSeverity());
        assertTrue(potim.getMessage().contains("crashes immediately"),
                "wiki verbatim: " + potim.getMessage());
        ValidationIssue nsw = find(issues, VaspIncarDeckAudit.CODE_NSW_MD);
        assertEquals(ValidationSeverity.ERROR, nsw.getSeverity());
        assertTrue(nsw.getMessage().contains("exits immediately"),
                "wiki verbatim: " + nsw.getMessage());
        List<ValidationIssue> md = audit(
                "ENCUT = 400\nIBRION = 0\nPOTIM = 1.0\nNSW = 200\n");
        assertFalse(hasCode(md, VaspIncarDeckAudit.CODE_POTIM_MD));
        assertFalse(hasCode(md, VaspIncarDeckAudit.CODE_NSW_MD),
                "a complete MD pin is clean: " + md);
    }

    @Test
    void rangeSeparationNeedsThePbeFamily() {
        List<ValidationIssue> issues = audit(
                "ENCUT = 400\nHFSCREEN = 0.2\nGGA = RP\n");
        ValidationIssue issue = find(issues,
                VaspIncarDeckAudit.CODE_HFSCREEN_GGA);
        assertTrue(issue.getMessage().contains("only when GGA=PE, PS or CA"),
                "wiki verbatim: " + issue.getMessage());
        List<ValidationIssue> pe = audit(
                "ENCUT = 400\nHFSCREEN = 0.2\nGGA = PE\nLHFCALC = .TRUE.\n"
                + "ALGO = Damped\n");
        assertFalse(hasCode(pe, VaspIncarDeckAudit.CODE_HFSCREEN_GGA),
                "GGA=PE is the HSE06 family: " + pe);
    }

    @Test
    void metaGgaAndTetrahedronRulesCarryTheirAdvisories() {
        List<ValidationIssue> issues = audit("ENCUT = 400\nMETAGGA = SCAN\n");
        ValidationIssue meta = find(issues, VaspIncarDeckAudit.CODE_META_LASPH);
        assertTrue(meta.getMessage().contains("LASPH=.TRUE."),
                "wiki verbatim advice: " + meta.getMessage());
        List<ValidationIssue> lasph = audit(
                "ENCUT = 400\nMETAGGA = SCAN\nLASPH = .TRUE.\n");
        assertFalse(hasCode(lasph, VaspIncarDeckAudit.CODE_META_LASPH),
                "LASPH=.TRUE. satisfies the accuracy advice: " + lasph);
        List<ValidationIssue> tetra = audit(
                "ENCUT = 400\nISMEAR = -5\n");
        ValidationIssue gamma = find(tetra,
                VaspIncarDeckAudit.CODE_TETRA_GAMMA);
        assertEquals(ValidationSeverity.INFO, gamma.getSeverity());
        assertTrue(gamma.getMessage().contains("Gamma-centered k-mesh"),
                "wiki verbatim tip: " + gamma.getMessage());
    }

    @Test
    void pinningAdvisoriesFireOnlyOnAbsence() {
        List<ValidationIssue> bare = audit("IBRION = -1\n");
        assertTrue(hasCode(bare, VaspIncarDeckAudit.CODE_ENCUT_MANUAL),
                "the wiki strongly recommends pinning ENCUT manually");
        ValidationIssue encut = find(bare, VaspIncarDeckAudit.CODE_ENCUT_MANUAL);
        assertTrue(encut.getMessage().contains("POTCAR"),
                "the licensed-file boundary is named: " + encut.getMessage());
        assertTrue(hasCode(bare, VaspIncarDeckAudit.CODE_ISTART_AUTO),
                "WAVECAR auto-detection is a restart trap");
        List<ValidationIssue> pinned = audit(
                "ENCUT = 400\nISTART = 0\nIBRION = -1\n");
        assertFalse(hasCode(pinned, VaspIncarDeckAudit.CODE_ENCUT_MANUAL));
        assertFalse(hasCode(pinned, VaspIncarDeckAudit.CODE_ISTART_AUTO));
        List<ValidationIssue> nupdown = audit(
                "ENCUT = 400\nISPIN = 2\nNUPDOWN = 4\n");
        ValidationIssue moment = find(nupdown,
                VaspIncarDeckAudit.CODE_NUPDOWN_MAGMOM);
        assertTrue(moment.getMessage().contains("convergence can slow down"),
                "wiki verbatim: " + moment.getMessage());
        List<ValidationIssue> seeded = audit(
                "ENCUT = 400\nISPIN = 2\nNUPDOWN = 4\nMAGMOM = 2*2.0 2*0.0\n");
        assertFalse(hasCode(seeded, VaspIncarDeckAudit.CODE_NUPDOWN_MAGMOM));
    }

    @Test
    void aCompleteScfDeckAuditsCleanOfErrors() {
        List<ValidationIssue> issues = audit(
                "SYSTEM = fcc Si scf\n"
                + "ISTART = 0\n"
                + "ENCUT = 400\n"
                + "EDIFF = 1E-6\n"
                + "ISMEAR = 0\n"
                + "SIGMA = 0.05\n"
                + "PREC = Accurate\n"
                + "LREAL = Auto\n");
        assertFalse(issues.stream().anyMatch(issue -> issue.getSeverity()
                        == ValidationSeverity.ERROR),
                "a well-pinned scf deck must not produce ERRORs: " + issues);
        assertFalse(issues.stream().anyMatch(issue -> issue.getSeverity()
                        == ValidationSeverity.WARNING),
                "nor WARNINGs: " + issues);
        // LREAL letter aliases of the wiki FLAGS options stay legal:
        List<ValidationIssue> alias = audit(
                "ENCUT = 400\nISTART = 0\nLREAL = A\n");
        assertFalse(hasCode(alias, VaspIncarDeckAudit.CODE_OPTION),
                "the wiki's LREAL = A alias is legal: " + alias);
    }
}
