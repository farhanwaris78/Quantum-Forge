/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEElateAnalyzer.AverageRow;
import quantumforge.run.parser.QEElateAnalyzer.Band;
import quantumforge.run.parser.QEElateAnalyzer.ElateReport;
import quantumforge.run.parser.QEElateAnalyzer.ElateUnit;
import quantumforge.run.parser.QEElateAnalyzer.Plane;

/**
 * ELATE engine pins (Roadmap: ELATE integration). Cases with analytic truth
 * (isotropic tensor, cubic silk…) are exacted to formula; the silicon case
 * uses the verbatim upstream thermo_pw example13 stiffness digits (commit
 * b73edd6d) whose Voigt/Reuss/Hill bulk+shear MUST reproduce thermo_pw's own
 * printed values divided by 10 (kbar -> GPa), while the Young/Poisson rows
 * follow ELATE's closure - thermo_pw's own Hill print is a plain mean of
 * scheme rows instead (different but defensible conventions; ~0.012% on the
 * Hill Young row), documented in the report text.
 */
class QEElateAnalyzerTest {

    // [analytic] isotropic: Born-stable, K = 60, G = 30
    private static final String ISO = """
        100 40 40 0 0 0
        40 100 40 0 0 0
        40 40 100 0 0 0
        0 0 0 30 0 0
        0 0 0 0 30 0
        0 0 0 0 0 30
        """;

    // [upstream] example13 elastic_constants/output_el_cons.dat.g1 digits (kbar)
    private static final String SI_KBAR = """
        1588.860492 603.0012079 603.0012079 0 0 0
        603.0012079 1588.860492 603.0012079 0 0 0
        603.0012079 603.0012079 1588.860492 0 0 0
        0 0 0 800.4009608 0 0
        0 0 0 0 800.4009608 0
        0 0 0 0 0 800.4009608
        """;

    @Test
    void testIsotropicCollapseIsExactAcrossSchemes() {
        OperationResult<ElateReport> result = QEElateAnalyzer.analyze(ISO, ElateUnit.GPA);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElateReport report = result.getValue().orElseThrow();
        assertEquals(ElateUnit.GPA, report.getDeclaredUnit());
        assertEquals(3, report.getAverages().size());
        for (AverageRow row : report.getAverages()) {
            assertEquals(60.0, row.getBulkGpa(), 1e-9);
            assertEquals(30.0, row.getShearGpa(), 1e-9);
            assertEquals(77.14285714285714, row.getYoungGpa(), 1e-9);
            assertEquals(0.2857142857142857, row.getPoisson(), 1e-9);
        }
        double[] eigen = report.getEigenvaluesGpa();
        assertEquals(30.0, eigen[0], 1e-9);
        assertEquals(60.0, eigen[3], 1e-9);
        assertEquals(180.0, eigen[5], 1e-9);
        assertTrue(report.isMechanicallyStable());
        assertEquals(77.14285714285714, report.getMinYoung().getValue(), 1e-6);
        assertEquals(77.14285714285721, report.getMaxYoung().getValue(), 1e-4,
                "isotropy recovered through the same grid+refine ELATE uses");
        assertEquals(5.555555555555555, report.getMinLc().getValue(), 1e-6);
        assertEquals(30.0, report.getMinShear().getValue(), 1e-6);
        assertEquals(30.0, report.getMaxShear().getValue(), 1e-6);
        assertEquals(0.2857142857142857, report.getMinPoisson().getValue(), 1e-6);
        assertEquals("1.000", report.getYoungAnisotropy());
        assertEquals("1.0000", report.getLcAnisotropy());
        // Directional probes must collapse to the same isotropic constants.
        assertEquals(77.14285714285714, report.youngGpa(0.7, 1.3), 1e-9);
        assertEquals(5.555555555555556, report.lcTPa(0.7, 1.3), 1e-6);
        assertEquals(30.0, report.shearGpa(0.7, 1.3, 2.1), 1e-9);
        assertEquals(0.2857142857142857, report.poisson(0.7, 1.3, 2.1), 1e-9);
    }

    @Test
    void testSiliconKbarChannelReproducesThermoPwPrintsDividedByTen() {
        OperationResult<ElateReport> result = QEElateAnalyzer.analyze(SI_KBAR,
                ElateUnit.KBAR);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElateReport report = result.getValue().orElseThrow();
        assertEquals(158.8860492, report.getStiffnessGpa()[0][0], 1e-9,
                "the 0.1 factor ('10 kbar = 1 GPa') is applied exactly");
        assertTrue(report.getNotes().get(0).contains("10 kbar = 1 GPa"),
                report.getNotes().toString());
        AverageRow voigt = report.getAverages().get(0);
        AverageRow reuss = report.getAverages().get(1);
        AverageRow hill = report.getAverages().get(2);
        // thermo_pw prints (kbar): KV 931.62097, GV 677.41243, GR 640.57431,
        // GH 658.99337 - divided by 10 under the declared conversion.
        assertEquals(93.162097, voigt.getBulkGpa(), 1e-6);
        assertEquals(93.162097, reuss.getBulkGpa(), 1e-6);
        assertEquals(67.741243, voigt.getShearGpa(), 1e-6);
        assertEquals(64.057431, reuss.getShearGpa(), 1e-6);
        assertEquals(93.162097, hill.getBulkGpa(), 1e-6);
        assertEquals(65.899337, hill.getShearGpa(), 1e-6);
        // ELATE's closure Young/Poisson; thermo_pw prints 1635.76447 / 0.20736
        // (Voigt) and its own Hill convention - the ELATE row is pinned as-is.
        assertEquals(163.576447, voigt.getYoungGpa(), 5e-4);
        assertEquals(156.339698, reuss.getYoungGpa(), 5e-4);
        assertEquals(159.9774, hill.getYoungGpa(), 5e-4,
                "ELATE hill closure 1/(1/3G+1/9K) - NOT thermo_pw's mean-of-rows");
        assertEquals(0.20736, voigt.getPoisson(), 1e-3);
        assertEquals(0.22031, reuss.getPoisson(), 1e-3);
        // Cubic eigenvalues analysed exactly.
        double[] eigen = report.getEigenvaluesGpa();
        assertEquals(80.040096, eigen[0], 1e-5);   // C44 x3
        assertEquals(80.040096, eigen[2], 1e-5);
        assertEquals(98.585928, eigen[3], 1e-5);   // C11-C12 x2
        assertEquals(279.486291, eigen[5], 1e-5);  // C11+2C12
        // Extrema recovery through ELATE's own grid+refine scheme.
        assertEquals(125.707819, report.getMinYoung().getValue(), 0.6,
                "Emin at the [100] family, tolerance covers the 25x25 grid");
        assertEquals(186.663152, report.getMaxYoung().getValue(), 0.6,
                "Emax at the [111] family");
        double absMax = 0.0;
        for (double component : report.getMaxYoung().getFirstAxis()) {
            absMax = Math.max(absMax, Math.abs(Math.abs(component) - 1.0 / Math.sqrt(3)));
        }
        assertTrue(absMax < 0.06, "the [111] axis is recovered: "
                + java.util.Arrays.toString(report.getMaxYoung().getFirstAxis()));
        // Cubic linear compressibility is direction-independent.
        assertEquals(3.577993, report.getMinLc().getValue(), 1e-4);
        assertEquals(3.577993, report.getMaxLc().getValue(), 1e-4);
        assertEquals(report.youngGpa(Math.PI / 2, 0.0),
                report.getMinYoung().getValue(), 0.6);
        assertEquals("1.485", report.getYoungAnisotropy());
        // Directional probes:
        assertEquals(125.7078193, report.youngGpa(Math.PI / 2, 0.0), 1e-6);
        // Cubic closure E[111] = 3/(S11+2S12+S44) with S11=7.954954633e-3,
        // S12=-2.188480802e-3, S44=1.249373813e-2 (1/GPa, from thermo_pw's
        // compliance block) = the Emax the grid probe must recover exactly.
        double e111 = report.youngGpa(Math.acos(1.0 / Math.sqrt(3.0)), Math.PI / 4);
        assertEquals(3.0 / (7.954954633e-3 - 2.0 * 2.188480802e-3
                        + 1.249373813e-2),
                e111, 0.05, "E111 closure identity = 3/(S11+2S12+S44)");
        // shear/Poisson bands bracket sensibly and shear stays positive
        assertTrue(report.getMinShear().getValue() > 0.0);
        assertTrue(report.getMinShear().getValue() <= report.getMaxShear().getValue());
        assertEquals(80.040096, report.getMaxShear().getValue(), 0.5,
                "G([111],t)=C44 family recovered");
        assertTrue(report.getMinPoisson().getValue() < report.getMaxPoisson().getValue());
    }

    @Test
    void testCrossPinnedAgainstQuantumForgeTensorAnalyzer() {
        // One physics owner check: the ELATE surface must agree numerically with
        // QETensorAnalyzer (#125) on SPD input - divergence would be a bug.
        double[][] siGpa = parse6(SI_KBAR, 0.1);
        OperationResult<QETensorAnalyzer.ElasticModuli> theirs =
                QETensorAnalyzer.analyzeElastic(siGpa);
        assertTrue(theirs.isSuccess(), () -> theirs.getMessage());
        ElateReport ours = QEElateAnalyzer.analyze(SI_KBAR, ElateUnit.KBAR)
                .getValue().orElseThrow();
        QETensorAnalyzer.ElasticModuli moduli = theirs.getValue().orElseThrow();
        assertEquals(moduli.getBulkVoigt(), ours.getAverages().get(0).getBulkGpa(), 1e-9);
        assertEquals(moduli.getBulkReuss(), ours.getAverages().get(1).getBulkGpa(), 1e-9);
        assertEquals(moduli.getShearHill(), ours.getAverages().get(2).getShearGpa(), 1e-9);
        assertEquals(moduli.getYoungsModulusHill(),
                ours.getAverages().get(2).getYoungGpa(), 1e-9,
                "both use the Hill closure - no drift between the surfaces");
        // Full rank-4 Young vs the #125 orthorhombic-form helper on this cubic.
        OperationResult<Double> theirsDir = QETensorAnalyzer.youngsModulusInDirection(
                ours.getCompliance(), 1.0, 0.0, 0.0);
        assertTrue(theirsDir.isSuccess());
        assertEquals(theirsDir.getValue().orElseThrow(),
                ours.youngGpa(Math.PI / 2, 0.0), 1e-9);
    }

    private static double[][] parse6(String text, double scale) {
        String[] lines = text.trim().split("\n");
        double[][] mat = new double[6][6];
        for (int i = 0; i < 6; i++) {
            String[] tokens = lines[i].split("\\s+");
            for (int j = 0; j < 6; j++) {
                mat[i][j] = Double.parseDouble(tokens[j]) * scale;
            }
        }
        return mat;
    }

    @Test
    void testStabilityGateReportsButBlocksExtrema() {
        String unstable = """
            100 180 180 0 0 0
            180 100 180 0 0 0
            180 180 100 0 0 0
            0 0 0 30 0 0
            0 0 0 0 30 0
            0 0 0 0 0 30
            """;
        OperationResult<ElateReport> result = QEElateAnalyzer.analyze(unstable,
                ElateUnit.GPA);
        assertTrue(result.isSuccess(), "ELATE still reports averages+eigenvalues");
        ElateReport report = result.getValue().orElseThrow();
        assertFalse(report.isMechanicallyStable());
        assertEquals(-80.0, report.getEigenvaluesGpa()[0], 1e-9);
        assertEquals(460.0, report.getEigenvaluesGpa()[5], 1e-9);
        assertEquals(153.3333333333, report.getAverages().get(0).getBulkGpa(), 1e-9,
                "averages are still reported, exactly like elate_main_3D prints them"
                        + " before the gate");
        assertNull(report.getMinYoung(), "'No further analysis will be performed'");
        assertNull(report.getMaxPoisson());
        assertTrue(report.getNotes().toString().contains("not positive definite")
                        || report.getNotes().toString().contains("is mechanically"
                        + " unstable"),
                report.getNotes().toString());
    }

    @Test
    void testSingularAsymmetricTriangularAndShapeRefusals() {
        String singular = """
            50 50 0 0 0 0
            50 50 0 0 0 0
            0 0 100 0 0 0
            0 0 0 30 0 0
            0 0 0 0 30 0
            0 0 0 0 0 30
            """;
        OperationResult<ElateReport> singularResult = QEElateAnalyzer.analyze(singular,
                ElateUnit.GPA);
        assertFalse(singularResult.isSuccess());
        assertEquals("ELATE_SINGULAR", singularResult.getCode());

        String asymmetric = """
            100 60 40 0 0 0
            40 100 40 0 0 0
            40 40 100 0 0 0
            0 0 0 30 0 0
            0 0 0 0 30 0
            0 0 0 0 0 30
            """;
        OperationResult<ElateReport> asymResult = QEElateAnalyzer.analyze(asymmetric,
                ElateUnit.GPA);
        assertFalse(asymResult.isSuccess());
        assertEquals("ELATE_ASYMMETRIC", asymResult.getCode());

        String nearlyAsymmetric = """
            100 40.0001 40 0 0 0
            40 100 40 0 0 0
            40 40 100 0 0 0
            0 0 0 30 0 0
            0 0 0 0 30 0
            0 0 0 0 0 30
            """;
        OperationResult<ElateReport> nearResult = QEElateAnalyzer.analyze(nearlyAsymmetric,
                ElateUnit.GPA);
        assertTrue(nearResult.isSuccess(), "ELATE symmetrizes within its 1e-3 norm");
        assertTrue(nearResult.getValue().orElseThrow().getNotes().toString()
                .contains("symmetrized"), nearResult.getValue().orElseThrow()
                .getNotes().toString());

        String upperTriangular = """
            100 40 40 0 0 0
            100 40 0 0 0
            100 0 0 0
            30 0 0
            30 0
            30
            """;
        OperationResult<ElateReport> triResult = QEElateAnalyzer.analyze(upperTriangular,
                ElateUnit.GPA);
        assertTrue(triResult.isSuccess(), "ELATE mirrors the upper triangle");
        assertEquals(60.0, triResult.getValue().orElseThrow().getAverages().get(0)
                .getBulkGpa(), 1e-9, "upper-triangular input collapses to the same tensor");

        String lowerTriangular = """
            100
            40 100
            40 40 100
            0 0 0 30
            0 0 0 0 30
            0 0 0 0 0 30
            """;
        assertTrue(QEElateAnalyzer.analyze(lowerTriangular, ElateUnit.GPA).isSuccess());

        OperationResult<ElateReport> twoD = QEElateAnalyzer.analyze(
                "100 40 0\n40 100 0\n0 0 30\n", ElateUnit.GPA);
        assertFalse(twoD.isSuccess());
        assertEquals("ELATE_2D", twoD.getCode());

        OperationResult<ElateReport> shape = QEElateAnalyzer.analyze(
                "100 40 40 0 0 0\n40 100 40 0 0 0\n40 40 100 0 0\n"
                        + "0 0 0 30 0 0\n0 0 0 0 30 0\n0 0 0 0 0 30\n", ElateUnit.GPA);
        assertFalse(shape.isSuccess());
        assertEquals("ELATE_SHAPE", shape.getCode());

        OperationResult<ElateReport> nan = QEElateAnalyzer.analyze(
                "100 40 40 0 0 0\n40 100 40 0 0 0\n40 40 xx 0 0 0\n"
                        + "0 0 0 30 0 0\n0 0 0 0 30 0\n0 0 0 0 0 30\n", ElateUnit.GPA);
        assertFalse(nan.isSuccess());
        assertEquals("ELATE_SHAPE", nan.getCode());

        assertFalse(QEElateAnalyzer.analyze(null, ElateUnit.GPA).isSuccess());
        assertFalse(QEElateAnalyzer.analyze(ISO, null).isSuccess(),
                "the input unit is never inferred");
    }

    @Test
    void testPolarCurvesMirrorElatePlaneCuts() {
        ElateReport iso = QEElateAnalyzer.analyze(ISO, ElateUnit.GPA)
                .getValue().orElseThrow();
        double[] xy = iso.polarYoung(Plane.XY, 36);
        double[] xz = iso.polarYoung(Plane.XZ, 36);
        assertEquals(36, xy.length);
        for (int i = 0; i < 36; i++) {
            assertEquals(77.14285714285714, xy[i], 1e-9, "isotropic curve is flat");
            assertEquals(xy[i], xz[i], 1e-9);
        }
        Band[] shearBand = iso.polarShearBand(Plane.XY, 12);
        assertEquals(12, shearBand.length);
        for (Band band : shearBand) {
            assertEquals(30.0, band.getMin(), 1e-6);
            assertEquals(30.0, band.getMax(), 1e-6);
        }
        Band[] poissonBand = iso.polarPoissonBand(Plane.YZ, 12);
        for (Band band : poissonBand) {
            assertEquals(0.2857142857142857, band.getMin(), 1e-6);
        }

        ElateReport si = QEElateAnalyzer.analyze(SI_KBAR, ElateUnit.KBAR)
                .getValue().orElseThrow();
        double[] siXy = si.polarYoung(Plane.XY, 72);
        assertEquals(125.7078193, siXy[0], 1e-3, "E along [100] at the xy cut");
        assertEquals(125.7078193, siXy[18], 1e-3, "[010] by cubic symmetry");
        // E[110] closure = 4 / (2 S11 + 2 S12 + S44)
        assertEquals(4.0 / (2.0 * 7.954954633e-3 - 2.0 * 2.188480802e-3
                        + 1.249373813e-2),
                siXy[9], 0.3, "E along [110] with the cubic closure");
        Band[] siBand = si.polarShearBand(Plane.XY, 24);
        for (Band band : siBand) {
            assertTrue(band.getMin() > 0.0, "stable Si shears stay positive");
            assertTrue(band.getMax() <= 80.05, "the C44 ceiling bounds the band");
        }
    }

    @Test
    void testTriclinicCouplingGoesThroughFullRank4() {
        // A tensor with s14-type coupling in the compliance (via a monoclinic
        // stiffness): the orthorhombic-only directional formula would MISS the
        // coupling; the ELATE rank-4 contraction must include it.
        String monoclinic = """
            100 40 40 5 0 0
            40 100 40 0 0 0
            40 40 100 0 0 0
            5 0 0 30 0 0
            0 0 0 0 30 0
            0 0 0 0 0 30
            """;
        OperationResult<ElateReport> result = QEElateAnalyzer.analyze(monoclinic,
                ElateUnit.GPA);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElateReport report = result.getValue().orElseThrow();
        // Along exactly [100] the s14 contraction term (a1^2 a2 a3) vanishes, so
        // both formulas must coincide there - a good self-check:
        assertEquals(76.3095238095238, report.youngGpa(Math.PI / 2, 0.0), 1e-9,
                "E[100] = 1/s11 even with the coupling present");
        // At theta=phi=45deg every direction cosine is non-zero, so the coupling
        // must show: full rank-4 (ELATE) 77.1992792536 vs the orthorhombic form
        // 76.8719550281 (independent pure-python Gauss-Jordan + SVoigt closure).
        double along = report.youngGpa(Math.PI / 4, Math.PI / 4);
        assertEquals(77.19927925364422, along, 1e-9,
                "full rank-4 contraction at theta=phi=45deg");
        double nx = Math.sin(Math.PI / 4) * Math.cos(Math.PI / 4);
        double ny = Math.sin(Math.PI / 4) * Math.sin(Math.PI / 4);
        double nz = Math.cos(Math.PI / 4);
        OperationResult<Double> orthoApprox = QETensorAnalyzer.youngsModulusInDirection(
                report.getCompliance(), nx, ny, nz);
        assertTrue(orthoApprox.isSuccess());
        assertTrue(Math.abs(along - orthoApprox.getValue().orElseThrow()) > 1e-6,
                "the C14 coupling changes E at 45/45deg: full rank-4 result " + along
                        + " differs from the orthorhombic-form "
                        + orthoApprox.getValue().orElseThrow());
    }
}
