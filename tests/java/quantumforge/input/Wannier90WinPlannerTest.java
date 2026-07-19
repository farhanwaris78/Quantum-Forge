/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.Wannier90WinPlanner.WinDraft;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.operation.OperationResult;

class Wannier90WinPlannerTest {

    private static QESCFInput automaticInput(int n1, int n2, int n3, int o1, int o2,
            int o3) {
        QESCFInput input = new QESCFInput();
        QEKPoints points = input.getCard(QEKPoints.class);
        points.setAutomatic();
        points.setKGrid(new int[] {n1, n2, n3});
        points.setKOffset(new int[] {o1, o2, o3});
        return input;
    }

    @Test
    void echoesMeshAndNbndGeneratesExactKpoints() {
        QESCFInput input = automaticInput(4, 4, 4, 0, 0, 0);
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("nbnd", "64"));
        OperationResult<WinDraft> result = Wannier90WinPlanner.plan(input);
        assertTrue(result.isSuccess(), result.toString());
        WinDraft draft = result.getValue().orElseThrow();
        assertTrue(draft.getDraft().contains("num_bands = 64"), draft.getDraft());
        assertTrue(draft.getDraft().contains("mp_grid = 4 4 4"), draft.getDraft());
        assertTrue(draft.isKpointsGenerated());
        assertTrue(draft.getDraft().contains("begin kpoints"), draft.getDraft());
        assertTrue(draft.getDraft().contains("end kpoints"), draft.getDraft());
        // 64 points each with weight 1/64 and the unshifted grid
        long kLines = draft.getDraft().lines()
                .filter(line -> line.endsWith("0.0156250000")).count();
        assertEquals(64L, kLines, "every generated point carries weight 1/64");
        assertTrue(draft.getDraft().contains("0.2500000000 0.0000000000 0.0000000000"),
                draft.getDraft());
        assertTrue(draft.countRequiredEdits() >= 3,
                "num_wann, projections and windows stay REQUIRED-EDIT");
    }

    @Test
    void shiftedMeshGeneratesMonkhorstPackPoints() {
        QESCFInput input = automaticInput(2, 2, 2, 1, 1, 1);
        WinDraft draft = Wannier90WinPlanner.plan(input).getValue().orElseThrow();
        assertTrue(draft.getDraft().contains("mp_grid = 2 2 2"), draft.getDraft());
        assertTrue(draft.getDraft().contains("0.7500000000"), "(2i+1)/2n = 3/4");
        assertTrue(draft.getDraft().contains("0.2500000000"), "(2i+1)/2n = 1/4");
        assertTrue(draft.getDraft().contains("0.1250000000"), "weight 1/8");
        assertEquals(null, draft.getNbnd(), "unset nbnd stays REQUIRED-EDIT");
        assertTrue(draft.getDraft().contains("num_bands = ..."), draft.getDraft());
    }

    @Test
    void gammaOnlyAndExplicitListsAreRefusedHonest() {
        QESCFInput gamma = new QESCFInput();
        gamma.getCard(QEKPoints.class).setGamma();
        assertEquals("W90_MESH", Wannier90WinPlanner.plan(gamma).getCode(),
                "Gamma-only cannot feed a Wannierization");

        QESCFInput tpiba = new QESCFInput();
        tpiba.getCard(QEKPoints.class).setTpiba();
        assertEquals("W90_MESH", Wannier90WinPlanner.plan(tpiba).getCode(),
                "an explicit list is not a uniform mp_grid");

        assertEquals("W90_INPUT", Wannier90WinPlanner.plan(null).getCode());
    }
}
