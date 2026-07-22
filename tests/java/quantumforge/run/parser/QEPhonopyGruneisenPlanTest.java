/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyGruneisenPlan.Plan;
import quantumforge.run.parser.QEPhonopyGruneisenPlan.Request;

/**
 * Pins {@link QEPhonopyGruneisenPlan} against doc/gruneisen.md and the
 * phonopy_gruneisen_script CLI surface @ 3a3e0f09: three positional
 * directories, the doc's own command shape, the --factor deprecation, the
 * 3-volume relaxation doctrine, and enumerated INPUT refusals.
 */
class QEPhonopyGruneisenPlanTest {

    @Test
    void siPresetMirrorsTheDocCommandShape() {
        OperationResult<Plan> result =
                QEPhonopyGruneisenPlan.build(QEPhonopyGruneisenPlan.siBandPreset());
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_GRUNEISEN_PLAN_OK", result.getCode());
        Plan plan = result.getValue().orElseThrow();
        String cmd = plan.getGruneisenCommands().get(0);
        // doc: phonopy-gruneisen orig plus minus --dim="2 2 2" --pa="0 1/2 1/2 ..."
        //      --band="1/2 1/4 3/4 0 0 0 1/2 1/2 1/2 1/2 0.0 1/2" -p -c POSCAR-unitcell
        assertTrue(cmd.startsWith("phonopy-gruneisen orig plus minus"), cmd);
        assertTrue(cmd.contains("--dim=\"2 2 2\""), cmd);
        assertTrue(cmd.contains("--pa=\"0 1/2 1/2 1/2 0 1/2 1/2 1/2 0\""), cmd);
        assertTrue(cmd.contains("--band=\"1/2 1/4 3/4  0 0 0  1/2 1/2 1/2  1/2 0.0 1/2\""),
                cmd);
        assertTrue(cmd.contains("--qe"), cmd);
        assertTrue(cmd.contains("-c pw.in"), cmd);
        assertTrue(cmd.contains("-p -s"), cmd);
        assertTrue(cmd.contains("gruneisen.yaml"), cmd);
        // per-volume doctrine: three dirs each with the batch-168 QE flow
        assertEquals(13, plan.getPerVolumeCommands().size());
        assertTrue(plan.getPerVolumeCommands().stream().anyMatch(c
                -> c.contains("under the constraint of") || c.contains("UNDER THE CONSTRAINT")));
        assertTrue(plan.getPerVolumeCommands().stream().anyMatch(c
                -> c.contains("cd minus") && c.contains("phonopy-init -f")));
        // the physics statement note rides along (doc verbatim definition)
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("-V/(2*omega^2)") && n.contains("finite difference")));
    }

    @Test
    void meshModeFlagsAndReadfcNac() {
        OperationResult<Plan> result = QEPhonopyGruneisenPlan.build(new Request()
                .origDir("equiv").plusDir("pos").minusDir("neg")
                .cellFilename("Si.in").supercellDim(2, 2, 2)
                .readfc(true).nac(true).mesh(20, 20, 20).colorScheme("RGB"));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        String cmd = plan.getGruneisenCommands().get(0);
        assertTrue(cmd.startsWith("phonopy-gruneisen equiv pos neg"), cmd);
        assertTrue(cmd.contains("--mesh=\"20 20 20\""), cmd);
        assertFalse(cmd.contains("--band"), cmd);
        assertTrue(cmd.contains("--readfc"), cmd);
        assertTrue(cmd.contains("--nac"), cmd);
        assertTrue(cmd.contains("--color=\"RGB\""), cmd);
        assertTrue(cmd.contains("gruneisen_mesh.yaml"), cmd);
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("deprecated at v2.44")));
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("DIVERGE")));
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("BORN file") && n.contains("EACH directory")), 
                plan.getNotes().toString());
        // a bad color scheme is warned, not refused (emitted as given)
        OperationResult<Plan> oddColor = QEPhonopyGruneisenPlan.build(new Request()
                .supercellDim(2, 2, 2).mesh(8, 8, 8).colorScheme("CMYK"));
        assertTrue(oddColor.isSuccess());
        assertTrue(oddColor.getValue().orElseThrow().getWarnings().stream()
                .anyMatch(w -> w.contains("CMYK")));
    }

    @Test
    void refusalsAreEnumeratedInputViolations() {
        // same directory twice
        OperationResult<Plan> same = QEPhonopyGruneisenPlan.build(new Request()
                .origDir("x").plusDir("x").minusDir("y").supercellDim(2, 2, 2)
                .mesh(4, 4, 4));
        assertFalse(same.isSuccess());
        assertEquals("PHONOPY_GRUNEISEN_PLAN_INPUT", same.getCode());
        assertTrue(same.getMessage().contains("THREE distinct volumes"));

        // missing DIM + single band vertex (and the doc's '--band auto' note)
        OperationResult<Plan> noDim = QEPhonopyGruneisenPlan.build(new Request()
                .bandVertex("0", "0", "0"));
        assertFalse(noDim.isSuccess());
        assertTrue(noDim.getMessage().contains("DIM missing"));
        assertTrue(noDim.getMessage().contains("at least TWO vertices"));

        // bad vertex grammar + band_points <= 1 + bad mesh
        OperationResult<Plan> badVertex = QEPhonopyGruneisenPlan.build(new Request()
                .supercellDim(2, 2, 2).bandVertex("0", "0", "0")
                .bandVertex("0,5", "0", "0").bandPoints(1));
        assertFalse(badVertex.isSuccess());
        assertTrue(badVertex.getMessage().contains("fraction a/b"));
        assertTrue(badVertex.getMessage().contains("--band_points must be > 1"));

        OperationResult<Plan> badMesh = QEPhonopyGruneisenPlan.build(new Request()
                .supercellDim(2, 2, 2).mesh(0, 4, 4));
        assertFalse(badMesh.isSuccess());
        assertTrue(badMesh.getMessage().contains("mesh entries must be >= 1"));

        assertEquals("PHONOPY_GRUNEISEN_PLAN_INPUT",
                QEPhonopyGruneisenPlan.build(null).getCode());
    }

    @Test
    void planIsPreviewNeverExecution() {
        Plan plan = QEPhonopyGruneisenPlan.build(QEPhonopyGruneisenPlan.siBandPreset())
                .getValue().orElseThrow();
        // the honesty doctrine: preview commands only, never claimed executed
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("PREVIEW") && n.contains("never claims it ran")));
        // band-crossing doc truth: frequencies may not be ordered
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("may NOT be ordered")));
        // the WATCH hookup hint names the studio handoff
        assertTrue(plan.getGruneisenCommands().get(1).contains("phonopy studio"));
    }
}
