/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.CpInputPlanner.CpContext;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class CpInputPlannerTest {

    @Test
    void contextEchoesPrefixOutdirAndFlagsTheInvalidPwCalculation() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'nio'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'./out'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "'cp'"));
        OperationResult<CpContext> result = CpInputPlanner.extractContext(input);
        assertTrue(result.isSuccess(), result.toString());
        CpContext context = result.getValue().orElseThrow();
        assertEquals("nio", context.getPrefix());
        assertEquals("./out", context.getOutdir());
        assertEquals("cp", context.getCalculation());
        assertTrue(context.usesInvalidPwCalculation(),
                "calculation='cp' is NOT a valid pw.x calculation - flagged");
    }

    @Test
    void defaultsAndNonCpCalculations() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "'scf'"));
        CpContext context = CpInputPlanner.extractContext(input).getValue().orElseThrow();
        assertEquals("pwscf", context.getPrefix(), "QE default prefix");
        assertEquals("./", context.getOutdir(), "QE default outdir");
        assertFalse(context.usesInvalidPwCalculation());

        CpContext plain = CpInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        assertEquals(null, plain.getCalculation(), "unset calculation stays null");
    }

    @Test
    void draftIsDeliberatelyNotRunnableAndEchoesContext() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'nio'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'./out'"));
        String draft = CpInputPlanner.draft(
                CpInputPlanner.extractContext(input).getValue().orElseThrow());
        assertTrue(draft.startsWith("! cp.x Car-Parrinello input DRAFT"), draft);
        assertTrue(draft.contains("NOT runnable"), draft);
        assertTrue(draft.contains("&CONTROL"), draft);
        assertTrue(draft.contains("calculation    = 'cp'"), draft);
        assertTrue(draft.contains("prefix         = 'nio',"), draft);
        assertTrue(draft.contains("outdir         = './out',"), draft);
        assertTrue(draft.contains("&ELECTRONS"), draft);
        assertTrue(draft.contains("&IONS"), draft);
        int requiredEdits = draft.split("REQUIRED-EDIT", -1).length - 1;
        assertTrue(requiredEdits >= 6,
                "every CP-physics choice is a placeholder: " + requiredEdits);
        assertTrue(!draft.contains("ATOMIC_POSITIONS crystal"),
                "no structural cards are fabricated");
    }

    @Test
    void missingInputFailsClosed() {
        assertEquals("CP_INPUT", CpInputPlanner.extractContext(null).getCode());
    }
}
