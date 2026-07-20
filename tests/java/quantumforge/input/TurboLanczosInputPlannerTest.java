/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.TurboLanczosInputPlanner.LrContext;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class TurboLanczosInputPlannerTest {

    @Test
    void contextEchoesSaveLocationVerbatim() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'bnz_lrtddft'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'/fast/lr'"));
        LrContext context = TurboLanczosInputPlanner.extractContext(input)
                .getValue().orElseThrow();
        assertEquals("bnz_lrtddft", context.getPrefix());
        assertEquals("/fast/lr", context.getOutdir());

        LrContext defaults = TurboLanczosInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        assertEquals("pwscf", defaults.getPrefix());
        assertEquals("./", defaults.getOutdir());

        OperationResult<LrContext> missing = TurboLanczosInputPlanner
                .extractContext(null);
        assertEquals("TDDFPT_INPUT", missing.getCode(), "no input fails closed");
    }

    @Test
    void draftGuardsEveryPhysicsChoiceAndNamesTheBoundary() {
        LrContext context = TurboLanczosInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        String draft = TurboLanczosInputPlanner.draft(context);
        assertEquals(5, TurboLanczosInputPlanner.countRequiredEdits(draft),
                "1 header guard + num_init + num_eign + ipol + charge_response");
        assertTrue(draft.contains("&lr_input"), draft);
        assertTrue(draft.contains("&lr_control"), draft);
        assertTrue(draft.contains("prefix   = 'pwscf'"), draft);
        assertTrue(draft.contains("outdir   = './'"), draft);
        assertTrue(draft.contains("num_init  = ..."), draft);
        assertTrue(draft.contains("num_eign  = ..."), draft);
        assertTrue(draft.contains("ipol            = ..."), draft);
        assertTrue(draft.contains("charge_response  = ..."), draft);
        assertTrue(draft.contains("LINEAR-RESPONSE (charge-susceptibility"), draft,
                "the LR-vs-RT boundary is mandatory");
        assertTrue(draft.contains("NOT real-time propagation"), draft);
        assertTrue(draft.contains("4 computes the three"), draft,
                "ipol=4 full-tensor semantics documented");
        assertTrue(draft.contains("CONVERGED pw.x SCF save"), draft);
        assertTrue(draft.contains("TDDFT_SPECTRUM"), draft,
                "the draft names the existing spectrum parser kind");
        assertTrue(draft.contains("#64 depth"), draft);
    }
}
