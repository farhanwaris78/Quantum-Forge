/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyQ2rPlan.Plan;
import quantumforge.run.parser.QEPhonopyQ2rPlan.Request;

/**
 * Pins the Experimental q2r.x flow preview against the upstream-verbatim
 * templates (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e): doc/qe.md's NaCl.ph.in shape,
 * example/NaCl-QE-q2r's q2r.in + README swap commands, and the doc's own
 * honesty markers quoted verbatim.
 */
final class QEPhonopyQ2rPlanTest {

    @Test
    void naclPresetEmitsTheDocsOwnTemplates() {
        OperationResult<Plan> result = QEPhonopyQ2rPlan.build(QEPhonopyQ2rPlan.naclPreset());
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_Q2R_PLAN_OK", result.getCode());
        Plan plan = result.getValue().orElseThrow();
        List<String> steps = plan.getSteps();
        String all = String.join("\n@@\n", steps);

        // the doc's **Experimental** honesty marker, verbatim
        assertTrue(steps.get(0).contains("**Experimental**"));
        // doc NaCl.ph.in template shape (lines pinned verbatim)
        assertTrue(all.contains("&inputph"));
        assertTrue(all.contains("tr2_ph=1.0d-16,"));
        assertTrue(all.contains("prefix='NaCl',"));
        assertTrue(all.contains("ldisp=.true.,"));
        assertTrue(all.contains("nq1=4, nq2=4, nq3=4"));
        assertTrue(all.contains("amass(1)=22.98976928,"));
        assertTrue(all.contains("amass(2)=35.453,"));
        // per-q independence note
        assertTrue(all.contains("start_q=N last_q=N"));
        // the Gamma-only swap deck: epsil=.false. + bare 0 0 0 q line,
        // and it must NOT carry ldisp (that is the ldisp deck's flag)
        String gamma = steps.stream().filter(s -> s.contains(".ph-gamma.in"))
                .findFirst().orElseThrow();
        assertTrue(gamma.contains("epsil=.false.,"));
        assertTrue(gamma.endsWith("0.0 0.0 0.0"));
        assertFalse(gamma.contains("ldisp"));
        // README swap commands verbatim + the doc's reason quoted
        assertTrue(all.contains("cp NaCl.dyn1 NaCl.dyn1.bak"));
        assertTrue(all.contains("cp NaCl.dyn NaCl.dyn1"));
        assertTrue(all.contains("These are unnecessary for phonopy"));
        // example q2r.in verbatim
        assertTrue(all.contains("fildyn='NaCl.dyn', zasr='simple', flfrc='NaCl.fc'"));
        assertTrue(all.contains("q2r.x < q2r.in"));
        // post-process script + the doc's CLI honesty marker, verbatim
        assertTrue(all.contains("python make_fc_q2r.py NaCl.in NaCl.fc"));
        assertTrue(all.contains("Currently command-line user interface is not prepared."));
        // the doc's band command and the README's --band auto alternative
        assertTrue(all.contains("phonopy phonopy_params_q2r.yaml --band=\"0 0 0"
                + "  1/2 0 0  1/2 1/2 0  0 0 0  1/2 1/2 1/2\" -p"));
        assertTrue(all.contains("phonopy-load phonopy_params_q2r.yaml --band auto -p"));
        assertTrue(all.contains("requires seekpath"));
        // README BORN route + QuantumForge's own extraction pointer
        assertTrue(all.contains("python make_born_q2r.py NaCl.in NaCl.fc > BORN"));
        assertTrue(all.contains("EXTRACT BORN"));
        // notes: NAC doctrine quote + real-fixture provenance + PREVIEW doctrine
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains(
                "partially corrected by QE's implemented NAC method")));
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("18432/18432")));
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("PREVIEW")));
    }

    @Test
    void everyInputIssueIsANamedRefusal() {
        assertEquals("PHONOPY_Q2R_PLAN_INPUT",
                QEPhonopyQ2rPlan.build(null).getCode());
        assertEquals("PHONOPY_Q2R_PLAN_INPUT", QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().prefix("Na Cl")).getCode());
        assertEquals("PHONOPY_Q2R_PLAN_INPUT", QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().flfrc("Na'Cl.fc")).getCode());
        OperationResult<Plan> badNq = QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().nq(0, 4, 4));
        assertEquals("PHONOPY_Q2R_PLAN_INPUT", badNq.getCode());
        assertTrue(badNq.getMessage().contains("nq entries must be >= 1"));
        OperationResult<Plan> badMass = QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().amass("abc"));
        assertTrue(badMass.getMessage().contains("not plain decimal text"));
        OperationResult<Plan> negMass = QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().amass("-1.0"));
        assertTrue(negMass.getMessage().contains("must be > 0"));
        OperationResult<Plan> oneVertex = QEPhonopyQ2rPlan.build(
                new Request().amass("1.0").bandVertex("0", "0", "0"));
        assertTrue(oneVertex.getMessage().contains("at least TWO"));
        OperationResult<Plan> badVertex = QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().bandVertex("x-y", "0", "0"));
        assertEquals("PHONOPY_Q2R_PLAN_INPUT", badVertex.getCode());
        assertTrue(badVertex.getMessage().contains("not an int, a decimal, or a"
                + " fraction"));
        // several issues are joined and counted, never silently dropped
        OperationResult<Plan> multi = QEPhonopyQ2rPlan.build(
                new Request().prefix("a b").nq(-1, 1, 1).band(false));
        assertTrue(multi.getMessage().contains("issue(s)"));
        assertTrue(multi.getMessage().contains(" | "));
    }

    @Test
    void fineGridAndMissingMassesAreWarningsNotRefusals() {
        OperationResult<Plan> result = QEPhonopyQ2rPlan.build(
                QEPhonopyQ2rPlan.naclPreset().nq(48, 48, 48).amass());
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        assertEquals(2, plan.getWarnings().size());
        assertTrue(plan.getWarnings().get(0).contains("unusually fine"));
        assertTrue(plan.getWarnings().get(0).contains("4x4x4 / 8x8x8"));
        assertTrue(plan.getWarnings().get(1).contains("no amass(i)= entries"));
        assertTrue(result.getMessage().contains("2 warning(s) stated"));
        // the warning does not censor the emitted grid
        assertTrue(String.join("\n", plan.getSteps()).contains("nq1=48, nq2=48, nq3=48"));
    }

    @Test
    void customParametersPropagateVerbatimAndBandCanBeSuppressed() {
        OperationResult<Plan> result = QEPhonopyQ2rPlan.build(new Request()
                .prefix("Si2").cellFilename("si2.scf.in").fildyn("si2.dyn")
                .flfrc("si2.fc").zasr("crystal").nq(6, 6, 6)
                .amass("28.085").band(false));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Plan plan = result.getValue().orElseThrow();
        assertTrue(plan.getWarnings().isEmpty());
        String all = String.join("\n", plan.getSteps());
        assertTrue(all.contains("prefix='Si2',"));
        assertTrue(all.contains("nq1=6, nq2=6, nq3=6"));
        assertTrue(all.contains("amass(1)=28.085,"));
        assertTrue(all.contains("fildyn='si2.dyn', zasr='crystal', flfrc='si2.fc'"));
        assertTrue(all.contains("cp si2.dyn1 si2.dyn1.bak"));
        assertTrue(all.contains("python make_fc_q2r.py si2.scf.in si2.fc"));
        // band(false): neither band command is emitted
        assertFalse(all.contains("--band=\""));
        assertFalse(all.contains("--band auto"));
    }
}
