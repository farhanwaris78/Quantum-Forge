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
import quantumforge.run.parser.QEThermoPwRunScanner.Artifact;
import quantumforge.run.parser.QEThermoPwRunScanner.RestartToken;
import quantumforge.run.parser.QEThermoPwRunScanner.ThermoScan;
import quantumforge.run.parser.QEThermoPwSeriesParser.Series;
import quantumforge.run.parser.QEThermoPwSeriesParser.SeriesKind;

/**
 * Run-directory census pins for the thermo_pw integration. The fabricated
 * tree mirrors the upstream example09 (mur_lc_t) layout - control excerpts
 * and one restart-token value are verbatim upstream rows (commit
 * b73edd6d75b92df80f3a322279c6b12b301b9947).
 */
class QEThermoPwRunScannerTest {

    @TempDir
    Path tempDir;

    private Path write(String relative, String content) throws IOException {
        Path file = this.tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private ThermoScan buildMurLcTree() throws IOException {
        // [upstream] examples/example09/reference/thermo_control
        write("thermo_control", " &INPUT_THERMO\n  what='mur_lc_t',\n"
                + "  lmurn=.TRUE.\n  deltat=3.\n /\n");
        // [upstream] first three rows of example05 energy_files/output_ev.dat
        write("energy_files/output_ev.dat",
                "         0.250000000000000E+03        -0.158487731110002E+02\n"
                        + "         0.253768781250000E+03        -0.158502397437950E+02\n");
        write("energy_files/output_ev.dat_mur",
                "#   omega (a.u.)**3       energy (Ry)        enthalpy(p)(Ry)    pressure (kbar)\n"
                        + "    245.0000000000      -15.8460821127      -15.6926585605       92.1199334692\n");
        write("energy_files/output_ev.dat.ev.out.xml", "<xml/>\n");
        write("therm_files/output_therm.dat.g1",
                "# Temperature T in K,\n# Energy and free energy in Ry/cell,\n"
                        + "  0.79300000E+03  0.312183178515E-01 -0.197805118699E-01"
                        + "  0.643112606827E-04  0.366651343935E-04\n");
        write("therm_files/output_therm.dat.g2",
                "# Temperature T in K,\n# Energy and free energy in Ry/cell,\n"
                        + "  0.79300000E+03  0.312183178515E-01 -0.197805118699E-01"
                        + "  0.643112606827E-04  0.366651343935E-04\n");
        write("therm_files/output_therm.dat.g2_ph",
                "# Temperature T in K,\n# Energy and free energy in Ry/cell,\n"
                        + "  0.79300000E+03  0.312183178515E-01 -0.197805118699E-01"
                        + "  0.643112606827E-04  0.366651343935E-04\n");
        write("anhar_files/output_anhar.dat",
                "# beta is the volume thermal expansion\n"
                        + "#   T (K)         V(T) (a.u.)^3          F (T) (Ry)       beta (10^(-6) K^(-1))\n"
                        + " 0.40000E+01    0.2686450606255E+03   -0.1582981437048E+02   -0.50093039E-03\n");
        write("anhar_files/output_anhar.dat.bulk_mod",
                "#\n#   T (K)        B_T(T) (kbar)        B_S(T) (kbar)        B_S(T)-B_T(T) (kbar) \n"
                        + " 0.40000E+01   0.9223255093779E+03   0.9223255104033E+03   0.1025479718919E-05\n");
        write("anhar_files/output_anhar.dat.aux_grun", "1 2 3\n");
        write("anhar_files/output_grun.dat", " &plot nbnd= 6, nks= 2 /\n");
        // [upstream] example05 restart token value
        write("restart/e_work_part.1.1", "  -15.848773111000199     \n");
        write("restart/e_work_part.2.1", "  -15.850239743795000     \n");
        write("restart/e_work_part.3.1", "  -15.851292468076300     \n");
        return QEThermoPwRunScanner.scan(this.tempDir);
    }

    @Test
    void testCensusMapsKindsTagsAndVariants() throws IOException {
        ThermoScan scan = buildMurLcTree();
        assertTrue(scan.getControl().isPresent());
        assertEquals("mur_lc_t", scan.getControl().getWhat());
        assertEquals(Boolean.TRUE, scan.getControl().getLmurn());
        assertEquals(3.0, scan.getControl().getDeltatK(), 1e-12);
        assertEquals(-1L, scan.getControl().getExplicitNgeoProduct(),
                "ngeo is absent upstream example09: no total is fabricated");

        List<Artifact> therm = scan.artifactsOfKind(SeriesKind.THERMO_HARMONIC);
        assertEquals(3, therm.size());
        boolean sawPh = false;
        boolean sawG1 = false;
        for (Artifact artifact : therm) {
            if (artifact.isPhVariant()) {
                sawPh = true;
            }
            if (Integer.valueOf(1).equals(artifact.getGeometryTag())) {
                sawG1 = true;
            }
            assertEquals("therm_files", artifact.getRole());
        }
        assertTrue(sawPh && sawG1, "geometry tags and the _ph variant flag must survive");
        assertEquals(1, scan.artifactsOfKind(SeriesKind.EV_CURVE).size());
        assertEquals(1, scan.artifactsOfKind(SeriesKind.MUR_FIT).size());
        assertEquals(1, scan.artifactsOfKind(SeriesKind.ANHARM_MAIN).size());
        assertEquals(1, scan.artifactsOfKind(SeriesKind.ANHARM_BULK).size());
        assertEquals(1, scan.artifactsOfKind(null).size(), "the control artifact is kind-less");

        List<String> uninterpreted = scan.getUninterpreted();
        assertTrue(uninterpreted.contains("anhar_files/output_anhar.dat.aux_grun"),
                uninterpreted.toString());
        assertTrue(uninterpreted.contains("anhar_files/output_grun.dat"),
                uninterpreted.toString());
        assertTrue(uninterpreted.contains("energy_files/output_ev.dat.ev.out.xml"),
                uninterpreted.toString());
    }

    @Test
    void testRestartTokensAreTheHonestTaskCounter() throws IOException {
        ThermoScan scan = buildMurLcTree();
        assertEquals(3, scan.getRestartCount());
        List<RestartToken> tokens = scan.getRestartTokens();
        assertEquals(-15.848773111000199, tokens.get(0).getValueRy(), 1e-12);
        assertEquals(1, tokens.get(0).getFirstIndex());
        assertEquals(2, tokens.get(1).getFirstIndex());
    }

    @Test
    void testLoadSeriesThroughTheScanner() throws IOException {
        ThermoScan scan = buildMurLcTree();
        Artifact ev = scan.artifactsOfKind(SeriesKind.EV_CURVE).get(0);
        OperationResult<Series> series = QEThermoPwRunScanner.loadSeries(ev);
        assertTrue(series.isSuccess(), () -> series.getMessage());
        assertEquals(2, series.getValue().orElseThrow().getRowCount());
        assertEquals(250.0, series.getValue().orElseThrow().getX(0), 1e-9);
        Artifact control = scan.artifactsOfKind(null).get(0);
        assertFalse(QEThermoPwRunScanner.loadSeries(control).isSuccess(),
                "the control artifact is enumerated but not parseable as a series");
    }

    @Test
    void testDescribeIsVerbatimAndHonest() throws IOException {
        ThermoScan scan = buildMurLcTree();
        String description = QEThermoPwRunScanner.describe(scan);
        assertTrue(description.contains("what='mur_lc_t'"), description);
        assertTrue(description.contains("deltat=3.00000 K"), description);
        assertTrue(description.contains("no task total is fabricated"), description);
        assertTrue(description.contains("restart tasks completed: 3"), description);

        Path plainDir = Files.createDirectories(this.tempDir.resolve("plain"));
        ThermoScan plain = QEThermoPwRunScanner.scan(plainDir);
        assertFalse(plain.getControl().isPresent());
        assertTrue(QEThermoPwRunScanner.describe(plain).contains("ABSENT"));
    }

    @Test
    void testSignatureTracksLiveGrowth() throws IOException {
        ThermoScan scan = buildMurLcTree();
        Artifact ev = scan.artifactsOfKind(SeriesKind.EV_CURVE).get(0);
        long[] before = QEThermoPwRunScanner.signature(ev.getPath());
        assertTrue(before[0] > 0L, "size signature must be positive for a real file");
        Files.writeString(ev.getPath(),
                Files.readString(ev.getPath())
                        + "         0.257575250000000E+03        -0.158512924680763E+02\n");
        long[] after = QEThermoPwRunScanner.signature(ev.getPath());
        assertTrue(after[0] > before[0], "a growing live file changes its signature");
        OperationResult<Series> series = QEThermoPwRunScanner.loadSeries(ev);
        assertTrue(series.isSuccess(), () -> series.getMessage());
        assertEquals(3, series.getValue().orElseThrow().getRowCount(),
                "re-parsing after growth reveals the new point");
        assertEquals(-1L, QEThermoPwRunScanner.signature(
                this.tempDir.resolve("does-not-exist"))[0]);
    }

    @Test
    void testExplicitNgeoProductOnlyWhenFullyWritten() throws IOException {
        write("thermo_control", "&INPUT_THERMO\n what='mur_lc',\n ngeo(1)=9, ngeo(2)=5,\n"
                + " ngeo(3)=1, ngeo(4)=1, ngeo(5)=1, ngeo(6)=1,\n pressure=12.5,\n /\n");
        ThermoScan scan = QEThermoPwRunScanner.scan(this.tempDir);
        assertEquals(45L, scan.getControl().getExplicitNgeoProduct());
        assertEquals(12.5, scan.getControl().getPressureKbar(), 1e-12);
        String description = QEThermoPwRunScanner.describe(scan);
        assertTrue(description.contains("explicit product 45"), description);
        assertTrue(description.contains("pressure=12.5000 kbar"), description);
    }
}
