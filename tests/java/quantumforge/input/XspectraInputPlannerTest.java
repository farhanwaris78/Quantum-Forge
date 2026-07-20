/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.XspectraInputPlanner.XspectraContext;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class XspectraInputPlannerTest {

    @Test
    void contextEchoesPrefixAndOutdirVerbatim() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'xanes_feo'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'/scratch/feo'"));
        OperationResult<XspectraContext> extracted = XspectraInputPlanner
                .extractContext(input);
        assertTrue(extracted.isSuccess(), extracted.toString());
        XspectraContext context = extracted.getValue().orElseThrow();
        assertEquals("xanes_feo", context.getPrefix(), "quotes stripped verbatim");
        assertEquals("/scratch/feo", context.getOutdir());

        XspectraContext defaults = XspectraInputPlanner.extractContext(new QESCFInput())
                .getValue().orElseThrow();
        assertEquals("pwscf", defaults.getPrefix(), "QE default named when unset");
        assertEquals("./", defaults.getOutdir());

        assertEquals("XSPEC_INPUT", XspectraInputPlanner.extractContext(null).getCode(),
                "no input fails closed");
    }

    @Test
    void draftIsDeliberatelyNonRunnableWithCountedPlaceholders() {
        XspectraContext context = XspectraInputPlanner.extractContext(
                new QESCFInput()).getValue().orElseThrow();
        String draft = XspectraInputPlanner.draft(context);
        assertEquals(8, XspectraInputPlanner.countRequiredEdits(draft),
                "2 guard mentions + 6 REQUIRED-EDIT physics placeholders pinned");
        assertTrue(draft.contains("NOT runnable"), draft);
        assertTrue(draft.contains("&input_xspectra"), draft);
        assertTrue(draft.contains("&plot"), draft);
        assertTrue(draft.contains("&pseudos"), draft);
        assertTrue(draft.contains("&cut_occ"), draft);
        assertTrue(draft.contains("xanes_dipole"), "the two curated options are named");
        assertTrue(draft.contains("xanes_quadrupole"), draft);
        assertTrue(draft.contains("'K', 'L1'"), "curated edge examples named");
        assertTrue(draft.contains("xnepoint     = 200"),
                "typical initial grid is stated as typical");
        assertTrue(draft.contains("core-hole pseudopotential"), draft);
        assertTrue(draft.contains("prefix = 'pwscf'"), "context echo present");
        assertFalse(draft.contains("ATOMIC_POSITIONS"),
                "structural setup is never fabricated into the draft");
        assertFalse(draft.contains("ecutwfc"),
                "no SCF physics is invented");
    }

    @Test
    void placeholderCountIsTheLoadBearingGuard() {
        // If future edits drop a placeholder the test must scream.
        String draft = XspectraInputPlanner.draft(XspectraInputPlanner
                .extractContext(new QESCFInput()).getValue().orElseThrow());
        long physics = XspectraInputPlanner.countRequiredEdits(draft) - 2L;
        assertEquals(6L, physics,
                "calculation, edge, xemin, xemax, filecore, cut_desmooth");
    }
}
