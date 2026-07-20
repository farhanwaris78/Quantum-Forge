/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.GipawInputPlanner.GipawContext;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class GipawInputPlannerTest {

    @Test
    void contextEchoesSaveLocationVerbatim() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'nmr_glucose'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'/fast/nmr'"));
        GipawContext context = GipawInputPlanner.extractContext(input)
                .getValue().orElseThrow();
        assertEquals("nmr_glucose", context.getPrefix());
        assertEquals("/fast/nmr", context.getOutdir());

        GipawContext defaults = GipawInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        assertEquals("pwscf", defaults.getPrefix());
        assertEquals("./", defaults.getOutdir());

        OperationResult<GipawContext> missing = GipawInputPlanner.extractContext(null);
        assertEquals("GIPAW_INPUT", missing.getCode(), "no input fails closed");
    }

    @Test
    void draftStatesDefaultsAndGuardsThePhysics() {
        GipawContext context = GipawInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        String draft = GipawInputPlanner.draft(context);
        assertEquals(4, GipawInputPlanner.countRequiredEdits(draft),
                "2 guard mentions + q_gipaw placeholder + sigma_ref caveat pinned");
        assertTrue(draft.contains("&inputgipaw"), draft);
        assertTrue(draft.contains("job          = 'gipaw'"), draft);
        assertTrue(draft.contains("prefix       = 'pwscf'"), draft);
        assertTrue(draft.contains("tmp_dir      = './'"), draft);
        assertTrue(draft.contains("q_gipaw      = ..."), draft);
        assertTrue(draft.contains("GIPAW-capable pseudopotentials"), draft);
        assertTrue(draft.contains("NOT a chemical shift"), draft,
                "the sigma-vs-shift honesty line is mandatory");
        assertTrue(draft.contains("sigma_ref"), draft);
    }
}
