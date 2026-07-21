/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEThermoPwSeriesParser.Series;
import quantumforge.run.parser.QEThermoPwSeriesParser.SeriesKind;

/**
 * thermo_pw series grammar pins. Every data row marked [upstream] is verbatim
 * from the reference outputs of dalcorso/thermo_pw examples 04 (scf_disp),
 * 05 (mur_lc) and 09 (mur_lc_t) at commit
 * b73edd6d75b92df80f3a322279c6b12b301b9947 (fetched 2026-07-21); header lines
 * are quoted verbatim for unit provenance.
 */
class QEThermoPwSeriesParserTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path file = this.tempDir.resolve(name);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
        return file;
    }

    // [upstream] examples/example05/reference/energy_files/output_ev.dat (all 9 rows)
    private static final String EV = """
                 0.250000000000000E+03        -0.158487731110002E+02
                 0.253768781250000E+03        -0.158502397437950E+02
                 0.257575250000000E+03        -0.158512924680763E+02
                 0.261419593750000E+03        -0.158519312602394E+02
                 0.265302000000000E+03        -0.158521907871721E+02
                 0.269222656250000E+03        -0.158520698261383E+02
                 0.273181750000000E+03        -0.158515915807150E+02
                 0.277179468750000E+03        -0.158507765636749E+02
                 0.281216000000000E+03        -0.158496521491275E+02
        """;

    @Test
    void testEvCurveHeaderlessTableWithCrossPinnedUnits() throws IOException {
        Path file = write("output_ev.dat", EV);
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file, SeriesKind.EV_CURVE);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Series series = result.getValue().orElseThrow();
        assertEquals(9, series.getRowCount());
        assertEquals("V ((a.u.)^3)", series.getXLabel());
        assertEquals("E (Ry)", series.getYLabel(1));
        assertEquals(250.0, series.getX(0), 1e-9);
        assertEquals(-15.8487731110002, series.getY(0, 1), 1e-9);
        assertEquals(281.216, series.getX(8), 1e-9);
        // The minimum row is the 5th (upstream minimum of the E(V) arc).
        assertEquals(-15.8521907871721, series.getY(4, 1), 1e-9);
        assertTrue(series.getUnitProvenance().contains("cross-pinned"),
                series.getUnitProvenance());
        assertTrue(QEThermoPwSeriesParser.candidateKinds("output_ev.dat")
                .contains(SeriesKind.EV_CURVE));
    }

    @Test
    void testMurFitHeaderAndFourColumns() throws IOException {
        // [upstream] examples/example05/reference/energy_files/output_ev.dat_mur
        Path file = write("output_ev.dat_mur", """
            #   omega (a.u.)**3       energy (Ry)        enthalpy(p)(Ry)    pressure (kbar)
                245.0000000000      -15.8460821127      -15.6926585605       92.1199334692
                245.8368064000      -15.8465933680      -15.7001262412       87.6437442035
            """);
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file, SeriesKind.MUR_FIT);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Series series = result.getValue().orElseThrow();
        assertEquals(2, series.getRowCount());
        assertEquals("energy (Ry)", series.getYLabel(1));
        assertEquals("enthalpy(p) (Ry)", series.getYLabel(2));
        assertEquals("pressure (kbar)", series.getYLabel(3));
        assertEquals(245.0, series.getX(0), 1e-9);
        assertEquals(92.1199334692, series.getY(0, 3), 1e-9);
        assertEquals(-15.7001262412, series.getY(1, 2), 1e-9);
    }

    @Test
    void testMurFitRefusesForeignHeader() throws IOException {
        Path file = write("output_ev.dat_mur", """
            # beta is the volume thermal expansion
            #   T (K)         V(T) (a.u.)^3          F (T) (Ry)       beta (10^(-6) K^(-1))
             0.40000E+01    0.2686450606255E+03   -0.1582981437048E+02   -0.50093039E-03
            """);
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file, SeriesKind.MUR_FIT);
        assertFalse(result.isSuccess());
        assertEquals("THERMOPW_HEADER", result.getCode());
        assertTrue(result.getMessage().contains("omega (a.u.)**3"), result.getMessage());
    }

    @Test
    void testThermoHarmonicHeaderBlockCaptured() throws IOException {
        // [upstream] examples/example04/reference/therm_files/output_therm.dat.g1
        Path file = write("output_therm.dat.g1", """
            # Zero point energy: 0.00901 Ry/cell, 11.82693 kJ/(N mol),  2.82671 kcal/(N mol)
            # Temperature T in K,
            # Total number of states is:        6.00000,
            # Energy and free energy in Ry/cell,
            # Entropy in Ry/cell/K,
            # Heat capacity Cv in Ry/cell/K.
            # Multiply by 13.6057 to have energies in eV/cell etc..
            # N is the number of formula units per cell.
              0.79300000E+03  0.312183178515E-01 -0.197805118699E-01  0.643112606827E-04  0.366651343935E-04
              0.79600000E+03  0.313283278876E-01 -0.199736534704E-01  0.644497253241E-04  0.366748720764E-04
            """);
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file,
                SeriesKind.THERMO_HARMONIC);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Series series = result.getValue().orElseThrow();
        assertEquals(2, series.getRowCount());
        assertEquals("T (K)", series.getXLabel());
        assertEquals("E (Ry/cell)", series.getYLabel(1));
        assertEquals("F (Ry/cell)", series.getYLabel(2));
        assertEquals("S (Ry/cell/K)", series.getYLabel(3));
        assertEquals("Cv (Ry/cell/K)", series.getYLabel(4));
        assertEquals(793.0, series.getX(0), 1e-9);
        assertEquals(0.0312183178515, series.getY(0, 1), 1e-9);
        assertEquals(-0.0197805118699, series.getY(0, 2), 1e-9);
        assertTrue(series.getCommentLines().get(0).startsWith("# Zero point energy"),
                series.getCommentLines().toString());
        assertTrue(QEThermoPwSeriesParser.candidateKinds("output_therm.dat.g1")
                .contains(SeriesKind.THERMO_HARMONIC));
        assertTrue(QEThermoPwSeriesParser.candidateKinds("output_therm.dat.g12_ph")
                .contains(SeriesKind.THERMO_HARMONIC));
    }

    @Test
    void testAnharmMainBetaUnitsVerbatim() throws IOException {
        // [upstream] examples/example09/reference/anhar_files/output_anhar.dat
        Path file = write("output_anhar.dat", """
            # beta is the volume thermal expansion
            #   T (K)         V(T) (a.u.)^3          F (T) (Ry)       beta (10^(-6) K^(-1))
             0.40000E+01    0.2686450606255E+03   -0.1582981437048E+02   -0.50093039E-03
             0.70000E+01    0.2686450599006E+03   -0.1582981437475E+02   -0.19398809E-02
            """);
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file,
                SeriesKind.ANHARM_MAIN);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Series series = result.getValue().orElseThrow();
        assertEquals("V(T) ((a.u.)^3)", series.getYLabel(1));
        assertEquals("F(T) (Ry)", series.getYLabel(2));
        assertEquals("beta (10^(-6) K^(-1))", series.getYLabel(3));
        assertEquals(4.0, series.getX(0), 1e-9);
        assertEquals(268.6450606255, series.getY(0, 1), 1e-9);
        assertEquals(-0.50093039e-3, series.getY(0, 3), 1e-12);
    }

    @Test
    void testAnharmSidecarHeadersAndRows() throws IOException {
        // [upstream] example09 sidecars, first rows each
        Path bulk = write("output_anhar.dat.bulk_mod", """
            #
            #   T (K)        B_T(T) (kbar)        B_S(T) (kbar)        B_S(T)-B_T(T) (kbar)
             0.40000E+01   0.9223255093779E+03   0.9223255104033E+03   0.1025479718919E-05
            """);
        OperationResult<Series> bulkResult = QEThermoPwSeriesParser.parse(bulk,
                SeriesKind.ANHARM_BULK);
        assertTrue(bulkResult.isSuccess(), () -> bulkResult.getMessage());
        Series bulkSeries = bulkResult.getValue().orElseThrow();
        assertEquals("B_T(T) (kbar)", bulkSeries.getYLabel(1));
        assertEquals(922.3255093779, bulkSeries.getY(0, 1), 1e-6);

        Path heat = write("output_anhar.dat.heat", """
            #
            # T (K)        C_e(T) (Ry/cell/K)    (C_P-C_V)(T) (Ry/cell/K)     C_e+C_P-C_V(T) (Ry/cell/K)
             0.40000E+01   0.1520573074430E-08   0.1690636196894E-17   0.1520573076120E-08
            """);
        OperationResult<Series> heatResult = QEThermoPwSeriesParser.parse(heat,
                SeriesKind.ANHARM_HEAT);
        assertTrue(heatResult.isSuccess(), () -> heatResult.getMessage());
        assertEquals("(C_P-C_V)(T) (Ry/cell/K)",
                heatResult.getValue().orElseThrow().getYLabel(2));

        Path gamma = write("output_anhar.dat.gamma", """
            #
            # T (K)          gamma(T)             C_V(T) (Ry/cell/K)    beta B_T (kbar/K)
             0.40000E+01  -0.5548882073620E+00   0.1520573074430E-08  -0.4620208739007E-06
            """);
        OperationResult<Series> gammaResult = QEThermoPwSeriesParser.parse(gamma,
                SeriesKind.ANHARM_GAMMA);
        assertTrue(gammaResult.isSuccess(), () -> gammaResult.getMessage());
        Series gammaSeries = gammaResult.getValue().orElseThrow();
        assertEquals("gamma(T)", gammaSeries.getYLabel(1));
        assertEquals("beta B_T (kbar/K)", gammaSeries.getYLabel(3));
        assertEquals(-0.5548882073620, gammaSeries.getY(0, 1), 1e-9);
    }

    @Test
    void testAnharmThermSharesHarmonicGrammar() throws IOException {
        // [upstream] examples/example09/reference/anhar_files/output_anhar.dat.therm header
        Path file = write("output_anhar.dat.therm", """
            # Zero point energy: 0.00895 Ry/cell, 11.74816 kJ/(N mol),  2.80667 kcal/(N mol)
            # Temperature T in K,
            # Energy and free energy in Ry/cell,
            # Entropy in Ry/cell/K,
            # Heat capacity Cv in Ry/cell/K.
             0.40000E+01  0.3143836697910E-01 -0.2016720973740E-01  0.6458770552750E-04  0.3668450489270E-04
            """);
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file,
                SeriesKind.ANHARM_THERM);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("quasi-harmonic thermodynamics vs T",
                result.getValue().orElseThrow().getKind().getLabel());
    }

    @Test
    void testPartialTailDroppedOnlyAtLastLine() throws IOException {
        Path tail = write("output_ev.dat", EV + "        0.284");
        OperationResult<Series> tailResult = QEThermoPwSeriesParser.parse(tail,
                SeriesKind.EV_CURVE);
        assertTrue(tailResult.isSuccess(), () -> tailResult.getMessage());
        assertEquals(9, tailResult.getValue().orElseThrow().getRowCount());
        assertEquals(1, tailResult.getValue().orElseThrow().getPartialTailRows());
        assertTrue(tailResult.getMessage().contains("partial row"), tailResult.getMessage());

        String middleBad = EV.replace(
                "         0.265302000000000E+03        -0.158521907871721E+02\n",
                "         0.265302000000000E+03\n");
        Path mid = write("output_ev.dat.corrupt", middleBad);
        OperationResult<Series> midResult = QEThermoPwSeriesParser.parse(mid,
                SeriesKind.EV_CURVE);
        assertFalse(midResult.isSuccess());
        assertEquals("THERMOPW_CORRUPT", midResult.getCode());
        assertTrue(midResult.getMessage().contains("line 5"), midResult.getMessage());
        assertTrue(midResult.getMessage().contains("expected 2 columns"), midResult.getMessage());
    }

    @Test
    void testUnparseableTailDroppedButNonFiniteMidRowRefused() throws IOException {
        Path tail = write("output_ev.dat", EV + "         garbage");
        OperationResult<Series> tailResult = QEThermoPwSeriesParser.parse(tail,
                SeriesKind.EV_CURVE);
        assertTrue(tailResult.isSuccess(), () -> tailResult.getMessage());
        assertEquals(1, tailResult.getValue().orElseThrow().getPartialTailRows());

        String nanMiddle = EV.replace("-0.158512924680763E+02", "NaN");
        Path mid = write("output_ev.dat.nan", nanMiddle);
        OperationResult<Series> midResult = QEThermoPwSeriesParser.parse(mid,
                SeriesKind.EV_CURVE);
        assertFalse(midResult.isSuccess());
        assertEquals("THERMOPW_CORRUPT", midResult.getCode());
    }

    @Test
    void testFortranDExponentsTolerated() throws IOException {
        Path file = write("output_ev.dat", "  0.25000000D+03 -0.15848773D+02\n");
        OperationResult<Series> result = QEThermoPwSeriesParser.parse(file, SeriesKind.EV_CURVE);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals(250.0, result.getValue().orElseThrow().getX(0), 1e-9);
    }

    @Test
    void testEmptyFileAndUnsupportedNamesFailClosed() throws IOException {
        Path empty = write("output_ev.dat", "\n");
        OperationResult<Series> emptyResult = QEThermoPwSeriesParser.parse(empty,
                SeriesKind.EV_CURVE);
        assertFalse(emptyResult.isSuccess());
        assertEquals("THERMOPW_EMPTY", emptyResult.getCode());

        assertTrue(QEThermoPwSeriesParser.candidateKinds("output_anhar.dat.aux_grun").isEmpty(),
                "the aux_grun sidecar is outside the pinned set");
        assertTrue(QEThermoPwSeriesParser.candidateKinds("output_grun.dat").isEmpty());
        assertTrue(QEThermoPwSeriesParser.candidateKinds("output_therm.dat.g1_ph")
                .contains(SeriesKind.THERMO_HARMONIC));
        assertFalse(QEThermoPwSeriesParser.parse(this.tempDir.resolve("missing.dat"),
                SeriesKind.EV_CURVE).isSuccess());
    }
}
