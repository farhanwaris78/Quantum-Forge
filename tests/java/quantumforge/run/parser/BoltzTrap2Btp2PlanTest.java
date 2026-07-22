/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.BoltzTrap2Btp2Plan.Plan;
import quantumforge.run.parser.BoltzTrap2Btp2Plan.Request;

/**
 * Pins {@link BoltzTrap2Btp2Plan} against the pinned upstream CLI
 * (gitlab.com/yiwang62/BoltzTraP2 branch 20210126 interface.py argparse
 * wiring + the sousaw wiki tutorial's verbatim commands + the readthedocs
 * install page). Every refusal mirrors an upstream argparse/lexit rule.
 */
final class BoltzTrap2Btp2PlanTest {

    @Test
    void liznsbPresetMirrorsTheWikiTutorialByteShape() {
        OperationResult<Plan> result =
                BoltzTrap2Btp2Plan.build(BoltzTrap2Btp2Plan.liznsbPreset());
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("BOLTZTRAP2_PLAN_OK", result.getCode());
        Plan plan = result.getValue().orElseThrow();
        String all = String.join("\n@@\n", plan.getSteps());
        // install page, verbatim content
        assertTrue(all.contains("pip install BoltzTraP2"));
        assertTrue(all.contains("python setup.py install"));
        assertTrue(all.contains("pytest -v tests"));
        assertTrue(all.contains("Cython NOT required"));
        // the tutorial's own commands
        assertTrue(all.contains("btp2 -vv interpolate -m 3 data/LiZnSb"));
        assertTrue(all.contains("xzcat interpolation.bt2 | more"));
        assertTrue(all.contains("xzcat interpolation.bt2 | jq . -C | less -r"));
        assertTrue(all.contains("btp2 integrate -t interpolation.bt2 300:500:1"));
        assertTrue(all.contains("interpolation.trace + interpolation.condtens +"
                + " interpolation.halltens + interpolation.btj"));
        assertTrue(all.contains("The reference for the chemical potential is"
                + " electroneutrality."));
        assertTrue(all.contains("btp2 plotbands interpolation.bt2"
                + " \"[[0.0, 0.0, 0.0], [0.5, 0.0, 0.0], [0.5, 0.5, 0.0]]\""));
        assertTrue(all.contains("50 points/segment default"));
        assertTrue(all.contains("btp2 plot -u -c '[\"xx\", \"zz\"]' -s 50"
                + " interpolation.btj S"));
        assertTrue(all.contains("abscissa: mu - fermi in Ha"));
        assertTrue(all.contains("btp2 describe interpolation.btj"));
        // honesty markers
        assertTrue(all.contains("QuantumForge NEVER runs these lines"));
        assertTrue(all.contains("xz-JSON"));
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("http://www.quantum-espresso.org/ns/qes/qes-1.0")));
        assertTrue(plan.getNotes().stream().anyMatch(n
                -> n.contains("refusing to interpolate to a sparser grid")));
        assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("PREVIEW")));
    }

    @Test
    void everyValidationRefusalMirrorsAnUpstreamRule() {
        assertEquals("BOLTZTRAP2_PLAN_INPUT", BoltzTrap2Btp2Plan.build(null).getCode());
        assertEquals("BOLTZTRAP2_PLAN_INPUT", BoltzTrap2Btp2Plan.build(
                new Request().output("with space.bt2")).getCode());
        // the mutually-exclusive REQUIRED group: neither set
        OperationResult<Plan> noGrid = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().multiplier(null));
        assertEquals("BOLTZTRAP2_PLAN_INPUT", noGrid.getCode());
        assertTrue(noGrid.getMessage().contains("REQUIRED"));
        assertTrue(noGrid.getMessage().contains("mutually-exclusive"));
        // upstream lexits, verbatim
        OperationResult<Plan> zeroWidth = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().window(0.1, 0.1));
        assertTrue(zeroWidth.getMessage().contains("zero-width energy window"));
        OperationResult<Plan> badTemps = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().temperature("0,-50"));
        assertTrue(badTemps.getMessage().contains("all temperatures must be positive"));
        OperationResult<Plan> emptyTemps = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().temperature(""));
        assertTrue(emptyTemps.getMessage().contains("empty temperature"));
        // np.arange(300, 100, 50) is EMPTY (stop excluded, positive step cannot
        // descend); upstream then lexits 'empty temperature specification'
        OperationResult<Plan> badRange = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().temperature("300:100:50"));
        assertTrue(badRange.getMessage().contains("empty temperature"));
        // a zero step would crash np.arange upstream (ZeroDivisionError): refused
        OperationResult<Plan> zeroStep = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().temperature("300:600:0"));
        assertTrue(zeroStep.getMessage().contains("zero step"));
        OperationResult<Plan> negBins = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.qePreset("./si.save").bins(0));
        assertTrue(negBins.getMessage().contains("-b bins"));
        OperationResult<Plan> negScissor = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.qePreset("./si.save").scissorEv(-0.5));
        assertTrue(negScissor.getMessage().contains("-s scissor"));
        OperationResult<Plan> noDoping = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.qePreset("./si.save").dope(true));
        assertTrue(noDoping.getMessage().contains("doping levels"));
        OperationResult<Plan> tensorNoComp = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().plot(true).quantity("sigma")
                        .componentReset());
        assertTrue(tensorNoComp.getMessage()
                .contains("no components have been specified"));
        OperationResult<Plan> badComp = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().plot(true).quantity("S")
                        .componentReset().component("x1"));
        assertTrue(badComp.getMessage().contains("components_argument"));
        // the Hall tensor needs THREE indices (upstream lexit, verbatim)
        OperationResult<Plan> hallTwoIndex = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.liznsbPreset().plot(true).quantity("RH")
                        .componentReset().component("xx"));
        assertTrue(hallTwoIndex.getMessage()
                .contains("need three indices"));
        OperationResult<Plan> fermiBad = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.qePreset("./si.save").fermisurface(true, "abc"));
        assertTrue(fermiBad.getMessage().contains("fermisurface mu"));
        OperationResult<Plan> verbose4 = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.qePreset("./si.save").verbose(4));
        assertTrue(verbose4.getMessage().contains("verbose"));
        // several issues are joined + counted, never silently dropped
        OperationResult<Plan> multi = BoltzTrap2Btp2Plan.build(
                new Request().output("a b").nkpointsPerSegment(0).plotBands(true));
        assertTrue(multi.getMessage().contains(" issue(s)): "));
        assertTrue(multi.getMessage().contains(" | "));
    }

    @Test
    void warningsAreStatedNotRefused() {
        OperationResult<Plan> dense = BoltzTrap2Btp2Plan.build(
                BoltzTrap2Btp2Plan.qePreset("./si.save").multiplier(25).bins(null));
        assertTrue(dense.isSuccess(), () -> dense.getMessage());
        Plan plan = dense.getValue().orElseThrow();
        assertEquals(2, plan.getWarnings().size());
        assertTrue(plan.getWarnings().get(0).contains("-m 25"));
        assertTrue(plan.getWarnings().get(0).contains("tutorial uses 3"));
        assertTrue(plan.getWarnings().get(1).contains("handle it"
                + " automatically"));
        assertTrue(dense.getMessage().contains("2 warning(s) stated"));
    }

    @Test
    void qePresetPropagatesEveryCustomFlag() {
        Request request = BoltzTrap2Btp2Plan.qePreset("./si.save")
                .kpoints(60000).derivatives(true)
                .window(-0.3, 0.15).absolute(true).bins(5000)
                .uniformLambda(true).scissorEv(1.17)
                .dope(true).dopingLevels("1e19,1e20,-1e20").doscar("DOSCAR")
                .nedos(40000).smooth(11).ugauss(4000.0)
                .plotBands(true).nkpointsPerSegment(120)
                .kpathVertex("0", "0", "0").kpathVertex("0.5", "0.5", "0")
                .kpathBreak().kpathVertex("0.5", "0", "0.5")
                .kpathVertex("0", "0", "0")
                .plot(true).quantity("L").component("scalar").abscissaMu(false)
                .subsample(10)
                .fermisurface(true, "-0.05");
        OperationResult<Plan> result = BoltzTrap2Btp2Plan.build(request);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        String all = String.join("\n", result.getValue().orElseThrow().getSteps());
        assertTrue(all.contains("btp2 -vv -n 4 interpolate -d -e -0.3 -E 0.15 -a"
                + " -k 60000 ./si.save"));
        assertTrue(all.contains("btp2 integrate -b 5000 -l -s 1.17 interpolation.bt2"
                + " 300:1001:100"));
        assertTrue(all.contains("btp2 dope -l -b 5000 -s 1.17 -dos DOSCAR -nd 40000"
                + " -sm 11 -ng 4000 interpolation.bt2 300:1001:100 1e19,1e20,-1e20"));
        assertTrue(all.contains(".dope.trace + interpolation.dope.condtens +"
                + " interpolation.dope.halltens + interpolation.dope.dos +"
                + " interpolation.dope.vvdos"));
        assertTrue(all.contains("_raw renames"));
        assertTrue(all.contains("btp2 plotbands -k 120 interpolation.bt2"
                + " \"[[0, 0, 0], [0.5, 0.5, 0], None, [0.5, 0, 0.5], [0, 0, 0]]\""));
        assertTrue(all.contains("btp2 plot -T -c '[\"scalar\"]' -s 10"
                + " interpolation.btj L"));
        assertTrue(all.contains("L0 = 2.44e-8"));
        assertTrue(all.contains("btp2 fermisurface interpolation.bt2 -0.05"));
        assertTrue(all.contains("VTK"));
        assertTrue(all.contains("keys"));
        assertTrue(all.contains(".trace / .condtens / .halltens / .dope.trace"));
    }

    @Test
    void suppressedStepsAreNotEmitted() {
        Request request = BoltzTrap2Btp2Plan.qePreset("./si.save")
                .integrate(false).describe(false);
        OperationResult<Plan> result = BoltzTrap2Btp2Plan.build(request);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        String all = String.join("\n", result.getValue().orElseThrow().getSteps());
        assertFalse(all.contains("btp2 integrate"));
        assertFalse(all.contains("btp2 describe"));
        assertFalse(all.contains("btp2 plot"));
        assertTrue(all.contains("btp2 -vv -n 4 interpolate -m 20 ./si.save"));
    }

    @Test
    void arrayArgumentMirrorsUpstreamNumpySemantics() {
        // ranges are start:stop:step with the STOP EXCLUDED (np.arange)
        List<Double> range = BoltzTrap2Btp2Plan.parseArrayArgument(
                "300:500:1", "temperature", new java.util.ArrayList<>());
        assertEquals(200, range.size());
        assertEquals(300.0, range.get(0), 1.0e-12);
        assertEquals(499.0, range.get(199), 1.0e-12);
        // comma mixes and sorting (np.unique)
        List<Double> mixed = BoltzTrap2Btp2Plan.parseArrayArgument(
                "300, 100:400:100, 800", "temperature", new java.util.ArrayList<>());
        assertEquals(List.of(100.0, 200.0, 300.0, 800.0), mixed);
        // holes are signed doping levels: allowed here, upstream range-checks later
        List<Double> holes = BoltzTrap2Btp2Plan.parseArrayArgument(
                "-1e20, 1e19", "doping_level", new java.util.ArrayList<>());
        assertEquals(List.of(-1.0e20, 1.0e19), holes);
    }
}
