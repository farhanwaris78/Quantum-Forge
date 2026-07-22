/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class QEHubbardPlannerTest {

    private static QESCFInput inputWithHubbard() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL)
                .setValue(QEValueBase.getInstance("calculation", "'scf'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL)
                .setValue(QEValueBase.getInstance("prefix", "'nio'"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("lda_plus_u", ".true."));
        input.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("hubbard_u(1)", "6.0"));
        return input;
    }

    @Test
    void extractsContextAndDraftsInputHp() {
        OperationResult<QEHubbardPlanner.HubbardContext> context =
                QEHubbardPlanner.extractContext(inputWithHubbard());
        assertTrue(context.isSuccess(), context.getMessage());
        QEHubbardPlanner.HubbardContext hubbard = context.getValue().orElseThrow();
        assertTrue(hubbard.isLdaPlusU());
        assertEquals(1, hubbard.getHubbardUEntries());
        assertTrue(hubbard.getPrefix().contains("nio"), hubbard.getPrefix());

        OperationResult<String> draft = QEHubbardPlanner.draft(hubbard, 2, 2, 2);
        assertTrue(draft.isSuccess(), draft.getMessage());
        String text = draft.getValue().orElseThrow();
        assertTrue(text.startsWith("&INPUTHP"), text);
        assertTrue(text.contains("prefix = 'nio',"), text);
        assertTrue(text.contains("nq1 = 2,"), text);
        assertTrue(text.contains("REVIEW before running hp.x"), text);
        assertTrue(text.contains("COMPLETED scf run"), text);
    }

    @Test
    void draftsWithUEntriesOnly() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("hubbard_u(1)", "5.0"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("hubbard_u(2)", "7.0"));
        OperationResult<QEHubbardPlanner.HubbardContext> context =
                QEHubbardPlanner.extractContext(input);
        assertTrue(context.isSuccess(), context.getMessage());
        assertEquals(2, context.getValue().orElseThrow().getHubbardUEntries());
        // No explicit prefix: a sensible non-blank default is documented instead.
        String draft = QEHubbardPlanner.draft(context.getValue().orElseThrow(), 1, 1, 4)
                .getValue().orElseThrow();
        assertTrue(draft.contains("outdir intentionally left"), draft);
    }

    @Test
    void failsClosedWithoutHubbardContext() {
        assertEquals("HP_INPUT", QEHubbardPlanner.extractContext(null).getCode());
        QESCFInput plain = new QESCFInput();
        plain.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("ecutwfc", "30.0"));
        assertEquals("HP_NO_HUBBARD", QEHubbardPlanner.extractContext(plain).getCode(),
                "No placeholder U setup is ever drafted");
    }

    @Test
    void failsClosedOnBadQGrid() {
        QEHubbardPlanner.HubbardContext context =
                QEHubbardPlanner.extractContext(inputWithHubbard()).getValue().orElseThrow();
        assertEquals("HP_QGRID", QEHubbardPlanner.draft(context, 0, 2, 2).getCode());
        assertEquals("HP_QGRID",
                QEHubbardPlanner.draft(context, 2, 2, QEHubbardPlanner.MAX_Q + 1).getCode());
        assertTrue(QEHubbardPlanner.draft(context, 1, 1, 1).isSuccess());
    }

    @Test
    void legacyDraftStaysByteIdenticalToThePreBatch154Shape() {
        // The version-typed overload must not leak into the legacy path:
        // no grammar-version line, no no_metq0, same first line as before.
        QEHubbardPlanner.HubbardContext context =
                QEHubbardPlanner.extractContext(inputWithHubbard()).getValue().orElseThrow();
        String legacy = QEHubbardPlanner.draft(context, 2, 2, 2).getValue().orElseThrow();
        assertTrue(legacy.startsWith("&INPUTHP"), legacy);
        assertFalse(legacy.contains("Target QE grammar"), legacy);
        assertFalse(legacy.contains("no_metq0"), legacy);
    }

    @Test
    void versionTypedDraftNamesTheGrammarAndFailsClosedOnUnknownVersion() {
        QEHubbardPlanner.HubbardContext context =
                QEHubbardPlanner.extractContext(inputWithHubbard()).getValue().orElseThrow();
        OperationResult<String> typed = QEHubbardPlanner.draft(context, 2, 2, 2, "7.4", false);
        assertTrue(typed.isSuccess(), typed.getMessage());
        String text = typed.getValue().orElseThrow();
        assertTrue(text.startsWith("&INPUTHP"), text);
        assertTrue(text.contains("nq1 = 2,"), text);
        assertTrue(text.contains("Target QE grammar: 7.4 (caller-pinned; mined window)"), text);
        assertFalse(text.contains("'last_q' is internal-only"),
                "the 7.6 drift note is windowed to 7.6: " + text);

        String newest = QEHubbardPlanner.draft(context, 2, 2, 2, null, false)
                .getValue().orElseThrow();
        assertTrue(newest.contains("Target QE grammar: 7.6 (newest mined window"), newest);
        assertTrue(newest.contains("'last_q' is internal-only"), newest);

        assertEquals("HP_VERSION",
                QEHubbardPlanner.draft(context, 2, 2, 2, "6.6", false).getCode());
    }

    @Test
    void noMetq0IsVersionGatedExactlyAtTheMinedBoundary() {
        QEHubbardPlanner.HubbardContext context =
                QEHubbardPlanner.extractContext(inputWithHubbard()).getValue().orElseThrow();
        OperationResult<String> refused = QEHubbardPlanner.draft(context, 2, 2, 2, "7.4", true);
        assertFalse(refused.isSuccess());
        assertEquals("HP_KEYWORD_WINDOW", refused.getCode());
        assertTrue(refused.getMessage().contains("7.5-7.6"), refused.getMessage());
        assertTrue(refused.getMessage().contains("aborts on an unknown"),
                refused.getMessage());

        OperationResult<String> accepted = QEHubbardPlanner.draft(context, 2, 2, 2,
                "7.5", true);
        assertTrue(accepted.isSuccess(), accepted.getMessage());
        String text = accepted.getValue().orElseThrow();
        assertTrue(text.contains("no_metq0 = .true.,"), text);
        assertTrue(text.contains("mined window 7.5-7.6"), text);
    }

    @Test
    void typedSelfAuditAgreesWithTheMinedGrammar() {
        assertTrue(QEHubbardPlanner.auditStaticEmissions().isEmpty(),
                String.join("; ", QEHubbardPlanner.auditStaticEmissions()));
    }
}
