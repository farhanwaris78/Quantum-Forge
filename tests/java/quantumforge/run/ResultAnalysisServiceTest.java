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

import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectProperty;
import quantumforge.run.ResultAnalysisService.AnalysisKind;
import quantumforge.run.ResultAnalysisService.AnalysisParameters;
import quantumforge.run.ResultAnalysisService.AnalysisReport;

class ResultAnalysisServiceTest {

    @TempDir
    Path tempDir;

    /** Minimal backend project stub mirroring DryRunPreflightTest's anonymous override set. */
    private Project stubProject(Path dir) {
        return stubProject(dir, null);
    }

    private Project stubProject(Path dir, Cell cellOverride) {
        return new Project(null, dir.toString()) {
            @Override public void setNetProject(Project project) { }
            @Override public boolean isValid() { return true; }
            @Override public boolean isSameAs(Project project) { return false; }
            @Override public ProjectProperty getProperty() {
                return new ProjectProperty(dir.toString(), "espresso");
            }
            @Override public String getPrefixName() { return "espresso"; }
            @Override public String getInpFileName(String ext) {
                return ext == null || ext.isBlank() ? "espresso.in" : "espresso.in." + ext;
            }
            @Override public String getLogFileName(String ext) {
                return ext == null || ext.isBlank() ? "espresso.log" : "espresso.log." + ext;
            }
            @Override public String getErrFileName(String ext) {
                return ext == null || ext.isBlank() ? "espresso.err" : "espresso.err." + ext;
            }
            @Override public String getExitFileName() { return "espresso.EXIT"; }
            @Override public QEInput getQEInputGeometry() { return null; }
            @Override public QEInput getQEInputScf() { return null; }
            @Override public QEInput getQEInputOptimiz() { return null; }
            @Override public QEInput getQEInputMd() { return null; }
            @Override public QEInput getQEInputDos() { return null; }
            @Override public QEInput getQEInputBand() { return null; }
            @Override public Cell getCell() { return cellOverride; }
            @Override protected void loadQEInputs() { }
            @Override public void resolveQEInputs() { }
            @Override public void markQEInputs() { }
            @Override public boolean isQEInputChanged() { return false; }
            @Override public void saveQEInputs(String directoryPath) { }
            @Override public void exportQEInputsTo(String directoryPath) { }
            @Override public Project cloneProject(String directoryPath) { return null; }
        };
    }

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
    void testDryRunPreflightProjectBound() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.DRY_RUN_PREFLIGHT,
                stubProject(this.tempDir), new AnalysisParameters());
        assertNotNull(report);
        assertTrue(report.getText().contains("Workflow type:"));
        assertTrue(report.getText().contains("No calculation was started."),
                "Preflight must stay non-destructive in its wording");
    }

    @Test
    void testRestartAssessmentEmptyProjectFailsClosed() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.RESTART_ASSESSMENT,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(report.isSuccess(), "A directory without a QE .save tree cannot be restart-safe");
    }

    @Test
    void testScratchAndResourceNeedUsableInput() {
        AnalysisReport scratch = ResultAnalysisService.analyze(AnalysisKind.SCRATCH_ESTIMATE,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(scratch.isSuccess());
        assertTrue(scratch.getText().contains("no current QE input"));

        AnalysisReport resources = ResultAnalysisService.analyze(AnalysisKind.RESOURCE_ESTIMATE,
                stubProject(this.tempDir), new AnalysisParameters().withTotalRanks(4));
        assertFalse(resources.isSuccess());
    }

    @Test
    void testRunManifestHistoryRendersTable() throws IOException {
        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.RUN_MANIFEST,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(missing.isSuccess(), "No manifest exists before any run");

        write(RunManifest.FILE_NAME,
                "{\"schema\":\"quantumforge.run-manifest.v1\",\"jobId\":\"job-2026-001\","
                + "\"stage\":\"scf\",\"status\":\"COMPLETED\",\"startedAt\":\"2026-07-19T10:00:00Z\","
                + "\"exitCode\":0,\"command\":[\"pw.x\"]}\n"
                + "not-a-manifest-line\n"
                + "{\"jobId\":\"job-2026-002\",\"stage\":\"bands\",\"status\":\"FAILED\","
                + "\"startedAt\":\"2026-07-19T11:00:00Z\",\"exitCode\":2}\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.RUN_MANIFEST,
                stubProject(this.tempDir), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("job-2026-001"));
        assertTrue(report.getText().contains("FAILED"));
        assertTrue(report.getText().contains("Skipped 1 malformed"),
                "Malformed manifest lines must be counted, not silently dropped");
    }

    @Test
    void testProjectBoundDelegationForFileKinds() throws IOException {
        File bands = write("si.dat.gnu", "0.0 1.0\n0.1 1.1\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.BANDS_DATA,
                stubProject(this.tempDir), new AnalysisParameters().withFermiEv(0.5));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("0.500000 eV (explicitly provided"));
    }

    @Test
    void testGeometryMeasureBondAngleDihedral() throws Exception {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.20, 0.0);
        cell.addAtom("Si", 0.15, 0.20, 0.30);

        AnalysisReport bond = ResultAnalysisService.analyze(AnalysisKind.GEOMETRY_MEASURE,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withAtomIndices(1, 2, 0, 0));
        assertTrue(bond.isSuccess(), bond.getText());
        assertTrue(bond.getText().contains("d(A1-B2)      = 1.5"), bond.getText());

        AnalysisReport dihedral = ResultAnalysisService.analyze(AnalysisKind.GEOMETRY_MEASURE,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withAtomIndices(1, 2, 3, 4));
        assertTrue(dihedral.getText().contains("dihedral(A1-B2-C3-D4)"), dihedral.getText());
        assertTrue(dihedral.getText().contains("minimum-image"), dihedral.getText());

        AnalysisReport badIndex = ResultAnalysisService.analyze(AnalysisKind.GEOMETRY_MEASURE,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withAtomIndices(2, 2, 0, 0));
        assertFalse(badIndex.isSuccess(), "Duplicate indices must fail closed");

        AnalysisReport outOfRange = ResultAnalysisService.analyze(AnalysisKind.GEOMETRY_MEASURE,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withAtomIndices(1, 99, 0, 0));
        assertFalse(outOfRange.isSuccess());
    }

    @Test
    void testXyzTrajectoryUnwrapAndDiffusion() throws Exception {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        StringBuilder xyz = new StringBuilder();
        double[][] positions = {
                {1.0, 0.0, 0.0}, {3.0, 0.0, 0.0}, {5.0, 0.0, 0.0},
                {7.0, 0.0, 0.0}, {9.0, 0.0, 0.0}, {3.0, 0.0, 0.0},
        };
        for (double[] position : positions) {
            xyz.append("1\ncomment\nSi ");
            xyz.append(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f%n",
                    position[0], position[1], position[2]));
        }
        File trajectory = write("md_run.xyz", xyz.toString());
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.MD_MSD,
                stubProject(this.tempDir, cell), trajectory,
                new AnalysisParameters().withFrameTimeStepPs(0.5));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Self-diffusion coefficient D:"), report.getText());
        assertTrue(report.getText().contains("Frames: 6")); 

        AnalysisReport shortRun = ResultAnalysisService.analyze(AnalysisKind.MD_MSD,
                stubProject(this.tempDir, cell), trajectory,
                new AnalysisParameters().withFrameTimeStepPs(-1.0));
        assertFalse(shortRun.isSuccess(), "Non-positive time step must fail closed");
    }

    @Test
    void testXyzTruncatedFailsClosed() throws Exception {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        File broken = write("broken.xyz", "2\ncomment\nSi 0 0 0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.MD_MSD,
                stubProject(this.tempDir, cell), broken, new AnalysisParameters());
        assertFalse(report.isSuccess());
        assertTrue(report.getText().contains("Truncated XYZ"), report.getText());
    }

    @Test
    void testHullStabilityFromCsv() throws IOException {
        File csv = write("phases.csv",
                "formula,fraction_B,formation_energy_eV_per_atom\n"
                + "AB2_meta,0.6667,-0.05\n"
                + "A,0.0,0.0\n"
                + "AB2_stable,0.6667,-0.30\n"
                + "B,1.0,0.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.HULL_STABILITY,
                new ProjectProperty(), this.tempDir.toFile(), "ab", "ab.log", csv,
                new AnalysisParameters());
        assertFalse(report.isSuccess(), "A candidate 0.25 eV/atom above hull is not stable");
        assertTrue(report.getText().contains("metastable"), report.getText());
        assertTrue(report.getText().contains("0.2500 eV/atom"), report.getText());

        File onlyTarget = write("single.csv", "AB,0.5,-0.4\n");
        AnalysisReport sparse = ResultAnalysisService.analyze(AnalysisKind.HULL_STABILITY,
                new ProjectProperty(), this.tempDir.toFile(), "ab", "ab.log", onlyTarget,
                new AnalysisParameters());
        assertFalse(sparse.isSuccess(), "A hull needs at least one competing phase");
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
