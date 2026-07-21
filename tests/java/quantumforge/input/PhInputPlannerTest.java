/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.PhInputPlanner.PhContext;
import quantumforge.input.PhInputPlanner.PhOptions;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.operation.OperationResult;

class PhInputPlannerTest {

    private static QESCFInput inputWithPrefixOutdir() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL)
                .setValue(QEValueBase.getInstance("prefix", "'nio'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL)
                .setValue(QEValueBase.getInstance("outdir", "'./out'"));
        return input;
    }

    private static PhContext context() {
        return PhInputPlanner.extractContext(inputWithPrefixOutdir()).getValue().orElseThrow();
    }

    @Test
    void contextEchoesPrefixOutdirAndFailsClosedWithoutInput() {
        assertEquals("PH_INPUT", PhInputPlanner.extractContext(null).getCode());

        PhContext context = context();
        assertEquals("nio", context.getPrefix());
        assertEquals("./out", context.getOutdir());
        assertFalse(context.usesDefaultOutdir());

        PhContext fresh = PhInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        assertEquals("pwscf", fresh.getPrefix(), "ph.x own default stands");
        assertTrue(fresh.usesDefaultOutdir(), "unset outdir stays the QE default, named");
    }

    @Test
    void draftEchoesContextNamesTheGrammarAndStaysHonest() {
        OperationResult<String> draft = PhInputPlanner.draft(context(), "7.6", 2, 2, 2,
                PhOptions.base());
        assertTrue(draft.isSuccess(), draft.getMessage());
        assertEquals("PH_DRAFT", draft.getCode());
        String text = draft.getValue().orElseThrow();
        assertTrue(text.startsWith("! ph.x phonon input DRAFT"), text);
        assertTrue(text.contains("Target QE grammar: 7.6 (caller-pinned; mined window)"), text);
        assertTrue(text.contains(QENamelistSchema.INPUT_PH_URL), text);
        assertTrue(text.contains("&INPUTPH"), text);
        assertTrue(text.contains("prefix  = 'nio',"), text);
        assertTrue(text.contains("outdir  = './out',"), text);
        assertTrue(text.contains("tr2_ph  = 1e-12,"), text);
        assertTrue(text.contains("mined QE default across 7.2-7.6"), text);
        assertTrue(text.contains("nq1 = 2,") && text.contains("nq3 = 2,"), text);
        assertTrue(text.contains("NOT converged by default"), text);
        assertTrue(text.contains("COMPLETED scf run"), text);
        assertTrue(text.contains("'last_q' is internal-only"),
                "7.6-pinned drafts carry the mined last_q drift note: " + text);
        assertFalse(text.contains("epsil"), "untoggled options emit nothing");
    }

    @Test
    void blankVersionResolvesToNewestAndSaysSo() {
        String draft = PhInputPlanner.draft(context(), null, 4, 4, 2, PhOptions.base())
                .getValue().orElseThrow();
        assertTrue(draft.contains("Target QE grammar: 7.6 (newest mined window;"
                + " the caller did not pin a version)"), draft);
    }

    @Test
    void defaultOutdirStaysACommentNeverAFabricatedPath() {
        String draft = PhInputPlanner.draft(
                PhInputPlanner.extractContext(new QESCFInput()).getValue().orElseThrow(),
                "7.4", 2, 2, 2, PhOptions.base()).getValue().orElseThrow();
        assertTrue(draft.contains("outdir intentionally left to the QE default"), draft);
        assertTrue(draft.contains("Target QE grammar: 7.4 (caller-pinned"), draft);
        assertFalse(draft.contains("'last_q' is internal-only"),
                "the 7.6 drift note is windowed to 7.6: " + draft);
    }

    @Test
    void failsClosedOnBadVersionBadGridAndNullContext() {
        assertEquals("PH_VERSION",
                PhInputPlanner.draft(context(), "6.6", 2, 2, 2, PhOptions.base()).getCode());
        assertEquals("PH_QGRID",
                PhInputPlanner.draft(context(), "7.6", 0, 2, 2, PhOptions.base()).getCode());
        assertEquals("PH_QGRID", PhInputPlanner.draft(context(), "7.6", 2, 2,
                PhInputPlanner.MAX_Q + 1, PhOptions.base()).getCode());
        assertEquals("PH_INPUT",
                PhInputPlanner.draft(null, "7.6", 2, 2, 2, PhOptions.base()).getCode());
        assertTrue(PhInputPlanner.draft(context(), "7.6", 1, 1, 1, PhOptions.base())
                .isSuccess());
    }

    @Test
    void versionGatedOptionsRefuseOutsideTheMinedWindow() {
        OperationResult<String> lmulti74 = PhInputPlanner.draft(context(), "7.4", 2, 2, 2,
                PhOptions.base().withLmultipole(true));
        assertFalse(lmulti74.isSuccess());
        assertEquals("PH_KEYWORD_WINDOW", lmulti74.getCode());
        assertTrue(lmulti74.getMessage().contains("7.5-7.6"), lmulti74.getMessage());
        assertTrue(lmulti74.getMessage().contains("aborts on an unknown"),
                "the refusal names why (ph.x aborts): " + lmulti74.getMessage());

        OperationResult<String> upperfan76 = PhInputPlanner.draft(context(), "7.6", 2, 2, 2,
                PhOptions.base().withSkipUpperfan(true));
        assertEquals("PH_KEYWORD_WINDOW", upperfan76.getCode());
        assertTrue(upperfan76.getMessage().contains("7.2-7.4"), upperfan76.getMessage());
    }

    @Test
    void versionGatedOptionsEmitInsideTheMinedWindow() {
        String lmulti = PhInputPlanner.draft(context(), "7.5", 2, 2, 2,
                PhOptions.base().withLmultipole(true).withEpsil(true))
                .getValue().orElseThrow();
        assertTrue(lmulti.contains("lmultipole = .true.,"), lmulti);
        assertTrue(lmulti.contains("mined window 7.5-7.6"), lmulti);
        assertTrue(lmulti.contains("dielectric/Born"), lmulti);

        String upperfan = PhInputPlanner.draft(context(), "7.2", 2, 2, 2,
                PhOptions.base().withSkipUpperfan(true))
                .getValue().orElseThrow();
        assertTrue(upperfan.contains("skip_upperfan = .true.,"), upperfan);
        assertTrue(upperfan.contains("mined window 7.2-7.4"), upperfan);
    }

    @Test
    void typedSelfAuditAgreesWithTheMinedGrammar() {
        assertTrue(PhInputPlanner.auditStaticEmissions().isEmpty(),
                String.join("; ", PhInputPlanner.auditStaticEmissions()));
        assertTrue(QEHubbardPlanner.auditStaticEmissions().isEmpty(),
                String.join("; ", QEHubbardPlanner.auditStaticEmissions()));
    }
}
