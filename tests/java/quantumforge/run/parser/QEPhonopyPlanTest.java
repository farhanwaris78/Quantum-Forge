/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyPlan.Plan;

/**
 * Pins for {@link QEPhonopyPlan}: the emitted conf uses the upstream conf
 * grammar (test/cui/phonopy_command/*.conf shapes), the phonopy-load
 * one-liner reproduces the user's own command #4 structurally (flat vertex
 * list inside --band, trailing-space label quoting, --band_connection, -p),
 * and every refusal ENUMERATES its issues instead of guessing.
 */
class QEPhonopyPlanTest {

    @Test
    void testCubicBccPresetReproducesTheUsersOwnCommand() {
        OperationResult<Plan> result = QEPhonopyPlan.build(
                QEPhonopyPlan.cubicBccPreset().cellFilename("NaCl.in"));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_PLAN_OK", result.getCode());
        Plan plan = result.getValue().orElseThrow();
        String conf = plan.getConfText();
        assertTrue(conf.contains("DIM = 2 2 2\n"), conf);
        // canonical comma-separated segments between consecutive vertices
        assertTrue(conf.contains(
                "BAND = 0.0 0.0 0.0 0.5 0.0 0.0, "
                        + "0.5 0.0 0.0 0.5 0.5 0.5, "
                        + "0.5 0.5 0.5 0.5 0.5 0.0, "
                        + "0.5 0.5 0.0 0.0 0.0 0.0, "
                        + "0.0 0.0 0.0 0.5 0.5 0.5\n"),
                conf);
        assertTrue(conf.contains("BAND_POINTS = 101\n"), conf);
        assertTrue(conf.contains("BAND_LABELS = G X R M G R\n"), conf);
        assertTrue(conf.contains("BAND_CONNECTION = .TRUE.\n"), conf);
        assertTrue(conf.contains("MESH = 8 8 8\n"), conf);
        assertTrue(conf.contains("DOS = .TRUE.\n"), conf);
        assertFalse(conf.contains("TPROP"), conf);

        // the one-liner reproduces the user's command #4 shape EXACTLY
        String expected = "phonopy-load --band "
                + "\"0.0 0.0 0.0  0.5 0.0 0.0  0.5 0.5 0.5  0.5 0.5 0.0"
                + "  0.0 0.0 0.0  0.5 0.5 0.5\""
                + " --band_labels \"G X R M G R \" --band_connection -p";
        assertTrue(plan.getLoadCommands().stream().anyMatch(c -> c.startsWith(expected)),
                "got: " + plan.getLoadCommands());
        // and adds -s so the artifacts land for this app's viewer
        assertTrue(plan.getLoadCommands().stream().anyMatch(c -> c.contains("-p -s")
                || c.endsWith("-s    # same, but also writes band.yaml for this viewer")
                || c.contains("band.yaml")), plan.getLoadCommands().toString());

        // the four-step QE flow (doc/qe.md)
        assertEquals(4, plan.getFlowCommands().size());
        assertTrue(plan.getFlowCommands().get(0).startsWith(
                "phonopy-init --qe -d --dim=\"2 2 2\" -c NaCl.in"));
        assertTrue(plan.getFlowCommands().get(2).startsWith("phonopy-init -f "));
        assertTrue(plan.getFlowCommands().get(3).startsWith(
                "phonopy --qe -p -s --config band.conf"));
        assertTrue(plan.getWarnings().isEmpty(),
                "6 labels for 6 connected vertices: the writer's pairing matches");
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("THz")));
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("PREVIEW")),
                "never claims execution happened");
    }

    @Test
    void testTpropAndPdosEmission() {
        OperationResult<Plan> result = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .cellFilename("pw.in").confName("tprop.conf")
                .supercellDim(2, 2, 2)
                .mesh(11, 11, 11)
                .pdos("1,", "2")
                .tprop(0, 2000, 20));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        String conf = plan.getConfText();
        assertTrue(conf.contains("MESH = 11 11 11\n"), conf);
        assertTrue(conf.contains("DOS = .TRUE.\n"), conf);
        assertTrue(conf.contains("PDOS = 1, 2\n"),
                "pdos tokens kept verbatim: '" + conf + "'");
        assertTrue(conf.contains("TPROP = .TRUE.\n"), conf);
        assertTrue(conf.contains("TMIN = 0\n"), conf);
        assertTrue(conf.contains("TMAX = 2000\n"), conf);
        assertTrue(conf.contains("TSTEP = 20\n"), conf);
        assertFalse(conf.contains("BAND ="), "mesh-only plan: no band section");
        assertTrue(plan.getLoadCommands().stream()
                .anyMatch(c -> c.contains("--pdos \"1, 2\"")), plan.getLoadCommands().toString());
        assertTrue(plan.getLoadCommands().stream()
                .anyMatch(c -> c.contains("--tprop") && c.contains("--tmax 2000")),
                plan.getLoadCommands().toString());
    }

    @Test
    void testLabelCountMismatchWarnsNeverRefuses() {
        OperationResult<Plan> result = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .supercellDim(2, 2, 2)
                .bandVertex("0", "0", "0")
                .bandVertex("1/2", "0", "1/2")
                .bandLabels("G", "X", "EXTRA")
                .dos(false));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        assertEquals(1, plan.getWarnings().size());
        assertTrue(plan.getWarnings().get(0).contains("3 labels"),
                "phonopy itself tolerates the mismatch; we SAY it shifts ticks");
        assertTrue(plan.getConfText().contains("BAND = 0 0 0 1/2 0 1/2\n"),
                "fraction token kept verbatim in the conf, like upstream's own confs");
    }

    @Test
    void testRefusalsAreEnumerated() {
        OperationResult<Plan> nullResult = QEPhonopyPlan.build(null);
        assertEquals("PHONOPY_PLAN_INPUT", nullResult.getCode());

        OperationResult<Plan> nothing = QEPhonopyPlan.build(
                new QEPhonopyPlan.Request().supercellDim(2, 2, 2));
        assertFalse(nothing.isSuccess());
        assertTrue(nothing.getMessage().contains("nothing requested"));

        OperationResult<Plan> issues = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .supercellDim(0, -1, 2)
                .bandVertex("x", "0", "0")
                .bandVertex("1/2", "0", "1/2")
                .bandPoints(1));
        assertFalse(issues.isSuccess());
        assertEquals("PHONOPY_PLAN_INPUT", issues.getCode());
        assertTrue(issues.getMessage().contains(">= 1"), issues.getMessage());
        assertTrue(issues.getMessage().contains("not an int, a decimal, or a fraction"),
                issues.getMessage());
        assertTrue(issues.getMessage().contains("BAND_POINTS"), issues.getMessage());

        OperationResult<Plan> singular = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .supercellDim(1, 1, 1, 1, 1, 1, 1, 1, 1)
                .bandVertex("0", "0", "0").bandVertex("0.5", "0", "0"));
        assertFalse(singular.isSuccess());
        assertTrue(singular.getMessage().contains("singular"),
                "det = 0 for the all-ones matrix");

        OperationResult<Plan> zeroDenom = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .supercellDim(2, 2, 2)
                .bandVertex("0", "0", "0").bandVertex("1/0", "0", "0"));
        assertFalse(zeroDenom.isSuccess());
        assertTrue(zeroDenom.getMessage().contains("zero denominator"));

        OperationResult<Plan> temperatures = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .supercellDim(2, 2, 2).mesh(8, 8, 8).tprop(100, 50, 0));
        assertFalse(temperatures.isSuccess());
        assertTrue(temperatures.getMessage().contains("TSTEP")
                && temperatures.getMessage().contains("TMAX < TMIN"));
    }

    @Test
    void testNineIntegerMatrixWithDetNote() {
        OperationResult<Plan> result = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .supercellDim(0, 1, 1, 1, 0, 1, 1, 1, 0) // the doc's own Ms example
                .bandVertex("0", "0", "0").bandVertex("1/2", "1/2", "1/2")
                .bandConnection(false));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        assertTrue(plan.getConfText().contains("DIM = 0 1 1 1 0 1 1 1 0\n"),
                plan.getConfText());
        assertTrue(plan.getNotes().stream()
                        .anyMatch(n -> n.contains("determinant 2")),
                "the doc's own matrix: det = 2, stated - got " + plan.getNotes());
        assertTrue(plan.getConfText().contains("BAND_CONNECTION = .FALSE.\n"));
        // the flow's dim string mirrors the conf's
        assertTrue(plan.getFlowCommands().get(0).contains(
                "--dim=\"0 1 1 1 0 1 1 1 0\""));
    }

    @Test
    void testNacEmissionMirrorsDocQeMdFlow() {
        OperationResult<Plan> result = QEPhonopyPlan.build(new QEPhonopyPlan.Request()
                .cellFilename("NaCl.in").supercellDim(2, 2, 2)
                .bandVertex("0", "0", "0").bandVertex("1/2", "1/2", "1/2")
                .bandLabels("G", "L").nac(true));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        // conf tag pinned to settings.py: confs["nac"] = ".true."
        assertTrue(plan.getConfText().contains("NAC = .TRUE.\n"), plan.getConfText());
        // doc/qe.md NAC flow: SCF -> ph.x epsil -> phonopy-qe-born, before post-process
        assertTrue(plan.getFlowCommands().stream().anyMatch(c
                -> c.contains("ph.x -i NaCl.ph.in") && c.contains("epsil = .true.")),
                plan.getFlowCommands().toString());
        assertTrue(plan.getFlowCommands().stream().anyMatch(c
                -> c.equals("phonopy-qe-born NaCl.in NaCl.ph.out | tee BORN    # step 4:"
                + " dielectric + Born charges into the BORN file (upstream helper"
                + " command); QuantumForge's studio can also BUILD this BORN text from"
                + " the ph.out (raw values)")),
                plan.getFlowCommands().toString());
        String post = plan.getFlowCommands().get(plan.getFlowCommands().size() - 1);
        assertTrue(post.contains("step 5") && post.startsWith("phonopy --qe -p -s"));
        // the v4 removal note (verbatim upstream argparse claim) is stated
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("--nonac") && n.contains("removed in phonopy v4")
                && n.contains("phonopy.yaml")), plan.getNotes().toString());
    }

    @Test
    void testNacOffByDefaultEmitsNothing() {
        Plan plan = QEPhonopyPlan.build(QEPhonopyPlan.cubicBccPreset())
                .getValue().orElseThrow();
        assertTrue(!plan.getConfText().contains("NAC"));
        assertTrue(plan.getFlowCommands().stream().noneMatch(c -> c.contains("born")));
        // 1 init + 1 pw + 1 -f + 1 post = 4 steps without NAC
        assertTrue(plan.getFlowCommands().get(plan.getFlowCommands().size() - 1)
                .contains("step 4"));
    }
}
