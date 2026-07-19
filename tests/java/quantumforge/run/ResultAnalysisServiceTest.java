package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.project.property.ProjectProperty;
import quantumforge.run.ResultAnalysisService.AnalysisKind;
import quantumforge.run.ResultAnalysisService.AnalysisParameters;
import quantumforge.run.ResultAnalysisService.AnalysisReport;

class ResultAnalysisServiceTest {

    @TempDir
    Path tempDir;

    private File write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(content);
        }
        return path.toFile();
    }

    @Test
    void testBandsAnalysisProducesCsvAndHonestFermiFallback() throws IOException {
        File bands = write("si-bands.dat.gnu",
                "0.0000  -5.0000\n0.1000  -4.9000\n0.2000  -4.8000\n"
                + "\n"
                + "0.0000   1.0000\n0.1000   1.1000\n0.2000   1.2000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.BANDS_DATA,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", bands,
                new AnalysisParameters());
        assertTrue(report.isSuccess());
        assertTrue(report.getText().contains("Bands: 2"));
        assertTrue(report.getText().contains("no stored Fermi energy"),
                "Unreferenced data must reveal the missing Fermi provenance");
        assertTrue(report.hasCsv());
        assertEquals("band_index,k_distance,dE_minus_reference_eV", report.getCsvLines().get(0));
        assertEquals(7, report.getCsvLines().size(), "header plus 6 band points");
    }

    @Test
    void testBandsExplicitFermiShiftsEnergies() throws IOException {
        File bands = write("fe.dat.gnu", "0.0 10.0\n0.1 11.0\n\n0.0 12.0\n0.1 13.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.BANDS_DATA,
                new ProjectProperty(), this.tempDir.toFile(), "fe", "fe.log", bands,
                new AnalysisParameters().withFermiEv(9.5));
        assertTrue(report.isSuccess());
        assertTrue(report.getText().contains("9.500000 eV"));
        assertTrue(report.getCsvLines().get(1).endsWith(",0.50000000"),
                "Energies must be referenced to the explicit Fermi value");
    }

    @Test
    void testMissingFileFailsClosed() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ELIASHBERG_TC,
                new ProjectProperty(), this.tempDir.toFile(), "nb", "nb.log", null,
                new AnalysisParameters());
        assertFalse(report.isSuccess());
        assertTrue(report.getText().contains("No usable data file"));
    }

    @Test
    void testAllenDynesTcFromSyntheticAlpha2f() throws IOException {
        StringBuilder alpha2f = new StringBuilder("# w(cm-1) alpha2F\n");
        for (int i = 1; i <= 100; i++) {
            alpha2f.append(String.format(java.util.Locale.ROOT, "%d.0 %.6f%n", i, 0.02 * i / 100.0));
        }
        File file = write("nb.a2f", alpha2f.toString());
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ELIASHBERG_TC,
                new ProjectProperty(), this.tempDir.toFile(), "nb", "nb.log", file,
                new AnalysisParameters().withMuStar(0.13));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("lambda"), "Report must expose the coupling constant");
        assertTrue(report.hasCsv());

        AnalysisReport invalid = ResultAnalysisService.analyze(AnalysisKind.ELIASHBERG_TC,
                new ProjectProperty(), this.tempDir.toFile(), "nb", "nb.log", file,
                new AnalysisParameters().withMuStar(5.0));
        assertFalse(invalid.isSuccess(), "Unphysical mu* must fail closed");
    }

    @Test
    void testPhono3pyKappaAnalysis() throws IOException {
        File kappa = write("kappa-m111.hdf5.txt",
                "  Temp (K)     kappa_xx     kappa_yy     kappa_zz\n"
                + "   300.00       30.0000      30.0000      30.0000\n"
                + "   600.00       15.0000      15.0000      15.0000\n"
                + "   900.00       10.0000      10.0000      10.0000\n\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.PHONO3PY_KAPPA,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", kappa,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Temperature points: 3"));
        assertTrue(report.hasCsv());
        assertEquals("temperature_k,kappa_xx_w_mk,kappa_yy_w_mk,kappa_zz_w_mk,kappa_iso_w_mk",
                report.getCsvLines().get(0));
    }

    @Test
    void testWannier90SpreadReport() throws IOException {
        File wout = write("si.wout",
                " CYCLE      1  Spreads: (  5.00000 ) Total:    5.00000\n"
                + " CYCLE      2  Spreads: (  2.50000 ) Total:    2.50000\n"
                + " CYCLE      3  Spreads: (  1.00000 ) Total:    1.00000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.WANNIER90_SPREAD,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", wout,
                new AnalysisParameters());
        assertNotNull(report);
        assertTrue(report.getText().contains("Minimization cycles parsed: 3"));
        assertTrue(report.getText().contains("Final total spread: 1.000000 Ang^2"));
    }

    @Test
    void testXanesPeakReport() throws IOException {
        File xanes = write("c_k.xanes.dat",
                "280.0 0.10\n285.0 0.50\n290.0 1.20\n295.0 0.40\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.XANES,
                new ProjectProperty(), this.tempDir.toFile(), "c", "c.log", xanes,
                new AnalysisParameters());
        assertTrue(report.isSuccess());
        assertTrue(report.getText().contains("Edge peak at 290.00000 eV"));
        assertTrue(report.hasCsv());
    }

    @Test
    void testPpInputPreviews() {
        AnalysisReport charge = ResultAnalysisService.analyze(AnalysisKind.PP_CHARGE_INPUT,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", null,
                new AnalysisParameters());
        assertTrue(charge.isSuccess());
        assertNotNull(charge.getGeneratedInput());
        assertTrue(charge.getGeneratedInput().contains("plot_num = 0"));
        assertTrue(charge.getGeneratedInput().contains("prefix = 'si'"));

        AnalysisReport potential = ResultAnalysisService.analyze(AnalysisKind.PP_POTENTIAL_INPUT,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", null,
                new AnalysisParameters());
        assertTrue(potential.getGeneratedInput().contains("plot_num = 11"));

        AnalysisReport wfc = ResultAnalysisService.analyze(AnalysisKind.PP_WAVEFUNCTION_INPUT,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", null,
                new AnalysisParameters().withKpointIndex(2).withBandIndex(4).withSpinComponent(1));
        assertTrue(wfc.getGeneratedInput().contains("plot_num = 7"));
        assertTrue(wfc.getGeneratedInput().contains("kpoint = 2"));
        assertTrue(wfc.getGeneratedInput().contains("kband = 4"));
        assertTrue(wfc.getGeneratedInput().contains("spin_component = 1"));

        AnalysisReport bad = ResultAnalysisService.analyze(AnalysisKind.PP_WAVEFUNCTION_INPUT,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", null,
                new AnalysisParameters().withKpointIndex(0));
        assertFalse(bad.isSuccess(), "Non-positive k-point must fail closed");
    }

    @Test
    void testDiscoveryPatterns() {
        try {
            write("bands_run.dat.gnu", "0 0\n");
            write("alpha2f.dat", "1 2\n");
            write("sample.wout", "x\n");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        List<File> bands = ResultAnalysisService.discover(AnalysisKind.BANDS_DATA,
                this.tempDir.toFile(), "x.log");
        assertEquals(1, bands.size(), "bands_run.dat.gnu must be discovered");
        List<File> tc = ResultAnalysisService.discover(AnalysisKind.ELIASHBERG_TC,
                this.tempDir.toFile(), "x.log");
        assertEquals(1, tc.size(), "alpha2f.dat must be discovered");
        List<File> w90 = ResultAnalysisService.discover(AnalysisKind.WANNIER90_SPREAD,
                this.tempDir.toFile(), "x.log");
        assertEquals(1, w90.size(), "sample.wout must be discovered");
        assertTrue(ResultAnalysisService.discover(AnalysisKind.PP_CHARGE_INPUT,
                this.tempDir.toFile(), "x.log").isEmpty(), "Input previews need no source file");
    }

    @Test
    void testProjectLogIsPreferredForLogAnalyses() throws IOException {
        File log = write("run.log",
                "     total magnetization       =     2.0000 Bohr mag/cell\n"
                + "     absolute magnetization    =     2.5000 Bohr mag/cell\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.MAGNETIZATION,
                new ProjectProperty(), this.tempDir.toFile(), "fe", log.getName(), null,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Total magnetization: 2.00000 Bohr mag/cell"));
    }

    @Test
    void testTcCsvRoundingIsLocaleStable() throws IOException {
        File file = write("pb.a2f", "10.0 0.01\n20.0 0.02\n30.0 0.01\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ELIASHBERG_TC,
                new ProjectProperty(), this.tempDir.toFile(), "pb", "pb.log", file,
                new AnalysisParameters().withMuStar(0.10));
        assertTrue(report.isSuccess());
        assertEquals("frequency_cm1,alpha2f", report.getCsvLines().get(0));
        assertTrue(report.getCsvLines().get(1).matches("\\d+\\.\\d+,\\d+.*"),
                "CSV numbers must use dot decimals regardless of locale: "
                + report.getCsvLines().get(1));
    }
}
