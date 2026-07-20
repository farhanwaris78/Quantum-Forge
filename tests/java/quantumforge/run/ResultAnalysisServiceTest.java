package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.input.QESCFInput;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectProperty;
import quantumforge.run.ResultAnalysisService.AnalysisKind;
import quantumforge.run.ResultAnalysisService.AnalysisParameters;
import quantumforge.run.ResultAnalysisService.AnalysisReport;
import quantumforge.run.parser.QEPhonopyForceSetsWriter;

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

    /** Stub project whose geometry-mode current input and cell are real instances. */
    private Project stubProjectWithInput(Path dir, QEInput input, Cell cell) {
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
            @Override public QEInput getQEInputGeometry() { return input; }
            @Override public QEInput getQEInputScf() { return null; }
            @Override public QEInput getQEInputOptimiz() { return null; }
            @Override public QEInput getQEInputMd() { return null; }
            @Override public QEInput getQEInputDos() { return null; }
            @Override public QEInput getQEInputBand() { return null; }
            @Override public Cell getCell() { return cell; }
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

    @Test
    void testWorkFunctionFromUniformPlateauPotential() throws IOException {
        // 20-sample grid: flat 4.0 eV left vacuum, a monotonically sloped slab
        // region (never a plateau), and a flat 4.5 eV right vacuum.
        StringBuilder content = new StringBuilder("# z(Ang) V(eV)\n");
        for (int i = 0; i < 20; i++) {
            double v;
            if (i < 5) {
                v = 4.0;
            } else if (i < 15) {
                v = 4.0 - 0.5 * (i - 4);
            } else {
                v = 4.5;
            }
            content.append(i).append(' ').append(v).append(" 0.0\n");
        }
        File tavg = write("si-tavg.dat", content.toString());
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.WORK_FUNCTION,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", tavg,
                new AnalysisParameters().withFermiEv(2.0));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Left work function:  2.000000 eV"), report.getText());
        assertTrue(report.getText().contains("Right work function: 2.500000 eV"), report.getText());
        assertTrue(report.getText().contains("Dipole step:        0.500000 eV"), report.getText());
        assertTrue(report.getText().contains("Phi = V_vac - E_F"), report.getText());
    }

    @Test
    void testWorkFunctionRejectsNonUniformGridAndPlateauLessPotential() throws IOException {
        File nonUniform = write("si-tavg-nonuniform.dat",
                "0.0 4.0\n0.5 4.0\n1.5 4.0\n2.0 4.0\n3.0 4.0\n4.0 8.0\n5.0 8.0\n6.0 8.0\n");
        AnalysisReport badGrid = ResultAnalysisService.analyze(AnalysisKind.WORK_FUNCTION,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", nonUniform,
                new AnalysisParameters().withFermiEv(2.0));
        assertFalse(badGrid.isSuccess());
        assertTrue(badGrid.getText().contains("Non-uniform z spacing"), badGrid.getText());

        StringBuilder ramp = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            ramp.append(i).append(' ').append(0.5 * i).append("\n");
        }
        File noPlateau = write("si-tavg-ramp.dat", ramp.toString());
        AnalysisReport noFlat = ResultAnalysisService.analyze(AnalysisKind.WORK_FUNCTION,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", noPlateau,
                new AnalysisParameters().withFermiEv(2.0));
        assertFalse(noFlat.isSuccess(), "A constantly sloped potential has no vacuum plateau");
        assertTrue(noFlat.getText().contains("No stable vacuum plateau"), noFlat.getText());
    }

    @Test
    void testCpTrajectoryFromCpLog() throws IOException {
        File log = write("si-cp.out",
                "     nfi=     10, ekinc=   0.00012, ekinh=   0.01245, etot=  -12.78452\n"
                + "     nfi=     20, ekinc=   0.00015, ekinh=   0.01423, etot=  -12.78455\n"
                + "     nfi=     30, ekinc=   0.00013, ekinh=   0.01524, etot=  -12.78454\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CP_TRAJECTORY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", log,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("MD steps parsed: 3"), report.getText());
        assertTrue(report.getText().contains("etot=-12.78454000 au"), report.getText());
        assertTrue(report.getText().contains("Adiabaticity flag from parser heuristics: true"),
                report.getText());
        assertTrue(report.hasCsv());
        assertEquals("step,ekinc_au,ekinh_au,etot_au", report.getCsvLines().get(0));
        assertEquals(4, report.getCsvLines().size(), "header plus 3 trajectory rows");

        File fake = write("fake-cp.out", "Program PWSCF v.7.2 starts ...\nNo CP rows here.\n");
        AnalysisReport empty = ResultAnalysisService.analyze(AnalysisKind.CP_TRAJECTORY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", fake,
                new AnalysisParameters());
        assertFalse(empty.isSuccess(), "An SCF log must not be analysed as cp.x dynamics");
    }

    @Test
    void testCubeInspectionStatisticsAndTruncation() throws IOException {
        File cube = write("rho.cube",
                "comment\ncomment\n1 0 0 0\n2 1 0 0\n1 0 1 0\n1 0 0 1\n1 0 0 0 0\n1.0 3.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CUBE_INSPECT,
                new ProjectProperty(), this.tempDir.toFile(), "h", "h.log", cube,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Grid: 2 x 1 x 1 voxels"), report.getText());
        assertTrue(report.getText().contains("mean=2.0000000"), report.getText());
        assertTrue(report.getText().contains("min=1.0000000"), report.getText());

        File truncated = write("bad.cube",
                "a\nb\n0 0 0 0\n2 1 0 0\n1 0 1 0\n1 0 0 1\n1\n");
        AnalysisReport fail = ResultAnalysisService.analyze(AnalysisKind.CUBE_INSPECT,
                new ProjectProperty(), this.tempDir.toFile(), "h", "h.log", truncated,
                new AnalysisParameters());
        assertFalse(fail.isSuccess(), "A truncated CUBE must fail closed");
    }

    @Test
    void testMagneticOrderClassificationAndMissingCell() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Fe", 0.0, 0.0, 0.0);
        cell.addAtom("Fe", 0.5, 0.5, 0.5);
        Atom[] atoms = cell.listAtoms();
        atoms[0].setProperty("starting_magnetization", "0.5");
        atoms[1].setProperty("starting_magnetization", "-0.5");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.MAGNETIC_ORDER,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Magnetic order: ANTIFERROMAGNETIC"), report.getText());
        assertTrue(report.getText().contains("sum of absolute moments: 1.000000"), report.getText());
        assertTrue(report.getText().contains("not a spglib"), report.getText());

        AnalysisReport noCell = ResultAnalysisService.analyze(AnalysisKind.MAGNETIC_ORDER,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(noCell.isSuccess(), "A project without a cell must fail closed");
    }

    @Test
    void testCitationsDetectWorkflowArtifacts() throws IOException {
        write("si.wout", "... wannier90 disentanglement output ...\n");
        write("matdyn.freq.gp", "0.0 100.0 120.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CITATIONS,
                stubProject(this.tempDir), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Wannier90: true"), report.getText());
        assertTrue(report.getText().contains("phonons: true"), report.getText());
        assertTrue(report.getText().contains("QUANTUM_ESPRESSO"), report.getText());
        assertTrue(report.getText().contains("WANNIER90"), report.getText());
        String bibtex = report.getGeneratedInput();
        assertNotNull(bibtex, "The BibTeX bundle must be offered for explicit saving");
        assertTrue(bibtex.contains("@"), bibtex);
        assertTrue(bibtex.contains("pizzi2020wannier90"), bibtex);
    }

    @Test
    void testBerryPolarizationFromProjectLog() throws IOException {
        File log = new File(this.tempDir.toFile(), "espresso.log");
        try (FileWriter writer = new FileWriter(log)) {
            writer.write("     Ionic Polarization    =    1.54212 electrons * bohr\n");
            writer.write("     Electronic Polarization =   -0.51234 electrons * bohr\n");
        }
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(4.0));
        cell.addAtom("Ba", 0.0, 0.0, 0.0);
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.BERRY_POLARIZATION,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Ionic polarization:     1.542120 (Bohr units)"),
                report.getText());
        assertTrue(report.getText().contains("Total polarization:      1.029780 (Bohr units)"),
                report.getText());
        assertTrue(report.getText().contains("direction 1:"), report.getText());
        assertTrue(report.getText().contains("undefined modulo the polarization quantum"),
                report.getText());
    }

    @Test
    void testBerryWithoutLogFailsClosed() {
        AnalysisReport noLog = ResultAnalysisService.analyze(AnalysisKind.BERRY_POLARIZATION,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(noLog.isSuccess(), "A project without a Berry log must fail closed");
    }

    @Test
    void testScfConvergenceFromLogTail() throws IOException {
        File log = write("si-scf.out",
                "     iteration #  1     ecut=    25.00 Ry     beta= 0.70\n"
                + "     total energy              =     -15.84012345 Ry\n"
                + "     estimated scf accuracy    <       0.01200000 Ry\n"
                + "     iteration #  2     ecut=    25.00 Ry     beta= 0.70\n"
                + "!    total energy              =     -15.85245678 Ry\n"
                + "     estimated scf accuracy    <       0.00000001 Ry\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.SCF_CONVERGENCE,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", log,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Iterations parsed: 2"), report.getText());
        assertTrue(report.getText().contains("Converged marker ('! total energy') found: true"),
                report.getText());
        assertTrue(report.getText().contains("Final total energy: -15.85245678 Ry"),
                report.getText());
        assertEquals("iteration,total_energy_Ry,estimated_accuracy_Ry",
                report.getCsvLines().get(0));
        assertEquals(3, report.getCsvLines().size(), "header plus 2 iterations");
    }

    @Test
    void testScfConvergenceFailureStates() throws IOException {
        File notConverged = write("si-nc.out",
                "     total energy              =     -15.84012345 Ry\n"
                + "     total energy              =     -15.83012345 Ry\n"
                + "     convergence NOT achieved after 100 iterations: stopping\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.SCF_CONVERGENCE,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", notConverged,
                new AnalysisParameters());
        assertFalse(report.isSuccess(), "An explicitly unconverged SCF must not report success");
        assertTrue(report.getText().contains("Explicit 'convergence NOT achieved': true"),
                report.getText());

        File empty = write("empty.out", "Program PWSCF starts ...\nkinetic-energy cutoff\n");
        AnalysisReport noScf = ResultAnalysisService.analyze(AnalysisKind.SCF_CONVERGENCE,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", empty,
                new AnalysisParameters());
        assertFalse(noScf.isSuccess(), "A log without SCF iterations must fail closed");
    }

    @Test
    void testTimingProfileFromPwLog() throws IOException {
        File log = write("si-timing.out",
                "     Parallel version (MPI), running on     8 processors\n"
                + "     Estimated max_memory     =   120.50 MB\n"
                + "     FFT dimensions:  (  64,  64,  64)\n"
                + "     PWSCF        :      5m 12.34s CPU      5m 14.56s WALL\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.TIMING_PROFILE,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", log,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("MPI processors: 8"), report.getText());
        assertTrue(report.getText().contains("FFT grid: 64 x 64 x 64"), report.getText());
        assertTrue(report.getText().contains("Estimated max memory: 120.50 MB"), report.getText());
        assertTrue(report.getText().contains("CPU time:  312.34 s"), report.getText());
        assertTrue(report.getText().contains("Wall time: 314.56 s"), report.getText());
        assertTrue(report.getText().contains("Derived CPU-time-per-rank utilization: 12.4 %"),
                report.getText());

        File noTiming = write("noise.out", "nothing relevant here\n");
        AnalysisReport fail = ResultAnalysisService.analyze(AnalysisKind.TIMING_PROFILE,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", noTiming,
                new AnalysisParameters());
        assertFalse(fail.isSuccess());
    }

    @Test
    void testSmearingSafetyVerdicts() throws IOException {
        File log = write("cu-smearing.out",
                "     total energy              =   -12.703512 Ry\n"
                + "     smearing contrib. (-TS)   =    -0.004500 Ry\n"
                + "     total free energy         =   -12.708012 Ry\n");
        AnalysisReport unsafe = ResultAnalysisService.analyze(AnalysisKind.SMEARING_ANALYSIS,
                new ProjectProperty(), this.tempDir.toFile(), "cu", "cu.log", log,
                new AnalysisParameters().withAtomCount(2));
        assertTrue(unsafe.isSuccess(), unsafe.getText());
        assertTrue(unsafe.getText().contains("Smearing -TS:        -0.00450000 Ry"),
                unsafe.getText());
        assertTrue(unsafe.getText().contains("|-TS| per atom (N=2): 0.00225000 Ry"),
                unsafe.getText());
        assertTrue(unsafe.getText().contains("If the system is an insulator/semiconductor: WARNING"),
                "0.00225 Ry/atom exceeds the 0.001 Ry force-bias limit: " + unsafe.getText());

        AnalysisReport safe = ResultAnalysisService.analyze(AnalysisKind.SMEARING_ANALYSIS,
                new ProjectProperty(), this.tempDir.toFile(), "cu", "cu.log", log,
                new AnalysisParameters().withAtomCount(10));
        assertTrue(safe.getText().contains("If the system is an insulator/semiconductor: SAFE"),
                safe.getText());
        assertTrue(safe.getText().contains("If the system is metallic: SAFE"), safe.getText());

        AnalysisReport badCount = ResultAnalysisService.analyze(AnalysisKind.SMEARING_ANALYSIS,
                new ProjectProperty(), this.tempDir.toFile(), "cu", "cu.log", log,
                new AnalysisParameters().withAtomCount(0));
        assertFalse(badCount.isSuccess(), "A zero atom count must be rejected");

        File noSmearing = write("si- fixed.out", "     total energy = -12.0 Ry\n");
        AnalysisReport noData = ResultAnalysisService.analyze(AnalysisKind.SMEARING_ANALYSIS,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", noSmearing,
                new AnalysisParameters().withAtomCount(2));
        assertFalse(noData.isSuccess(), "A non-smearing log must fail closed");
    }

    @Test
    void testPhononDosThermodynamicsFromTwoColumnGrid() throws IOException {
        File dos = write("si-phdos.dat", "0.0 0.0\n100.0 1.0\n200.0 1.0\n300.0 0.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.PHONON_DOS_THERMO,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", dos,
                new AnalysisParameters().withTemperatureK(300.0));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Integrated DOS (mode count): 200.000000"),
                report.getText());
        assertTrue(report.getText().contains("Temperature: 300.00 K"), report.getText());
        assertTrue(report.getText().contains("Zero-point energy:"), report.getText());
        assertTrue(report.getText().contains("Heat capacity Cv:"), report.getText());
        assertTrue(report.getText().contains("3*natoms"), report.getText());
    }

    @Test
    void testPhononDosValidationFailures() throws IOException {
        File nonIncreasing = write("bad-phdos.dat", "100.0 1.0\n50.0 1.0\n300.0 0.0\n");
        AnalysisReport gridFail = ResultAnalysisService.analyze(AnalysisKind.PHONON_DOS_THERMO,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", nonIncreasing,
                new AnalysisParameters().withTemperatureK(300.0));
        assertFalse(gridFail.isSuccess());
        assertTrue(gridFail.getText().contains("Phonon DOS validation failed"), gridFail.getText());

        File dos = write("ok-phdos.dat", "0.0 0.0\n100.0 1.0\n200.0 0.0\n");
        AnalysisReport badTemp = ResultAnalysisService.analyze(AnalysisKind.PHONON_DOS_THERMO,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", dos,
                new AnalysisParameters().withTemperatureK(-5.0));
        assertFalse(badTemp.isSuccess(), "A negative temperature must be rejected");
    }

    @Test
    void testElasticStabilityFromThermoPwMatrix() throws IOException {
        File stable = write("elastic.out",
                "  Elastic Constant Matrix (kbar)\n"
                + "  5000 1000 1000    0    0    0\n"
                + "  1000 5000 1000    0    0    0\n"
                + "  1000 1000 5000    0    0    0\n"
                + "     0    0    0 2000    0    0\n"
                + "     0    0    0    0 2000    0\n"
                + "     0    0    0    0    0 2000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_STABILITY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", stable,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "Mechanically stable (Sylvester leading-minors criterion): true"),
                report.getText());
        assertTrue(report.getText().contains("Cubic Born mechanical criteria satisfied"),
                report.getText());
        assertTrue(report.getText().contains("kbar"), report.getText());

        File unstable = write("elastic-unstable.out",
                "  Elastic Constant Matrix (kbar)\n"
                + "   500 1000 1000    0    0    0\n"
                + "  1000  500 1000    0    0    0\n"
                + "  1000 1000  500    0    0    0\n"
                + "     0    0    0 2000    0    0\n"
                + "     0    0    0    0 2000    0\n"
                + "     0    0    0    0    0 2000\n");
        AnalysisReport bad = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_STABILITY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", unstable,
                new AnalysisParameters());
        assertFalse(bad.isSuccess(), "C11=500 < 2*C12=1000 must fail Born stability");
        assertTrue(bad.getText().contains("FAILED"), bad.getText());

        File noBlock = write("no-elastic.out", "SCF output only\n");
        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_STABILITY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", noBlock,
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "A log without the matrix block must fail closed");
    }

    @Test
    void testLammpsThermoTrajectoryAndCsv() throws IOException {
        File log = write("log.lammps",
                "Memory usage per processor = 2.45 Mbytes\n"
                + "Step Temp Press PotEng KinEng TotEng\n"
                + "   0  300.0  1.0  -150.0  15.0  -135.0\n"
                + " 100  310.0  2.0  -152.0  16.0  -136.0\n"
                + " 200  290.0  0.0  -148.0  14.0  -134.0\n"
                + "Loop time of 12.345s on 4 procs\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.LAMMPS_THERMO,
                new ProjectProperty(), this.tempDir.toFile(), "ml", "ml.log", log,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Thermo steps parsed: 3"), report.getText());
        assertTrue(report.getText().contains("Total-energy drift over the run: +1.000000"),
                report.getText());
        assertTrue(report.getText().contains("'units' command"), report.getText());
        assertEquals("step,temp_K,press_bar,poteng,kineng,toteng", report.getCsvLines().get(0));
        assertEquals(4, report.getCsvLines().size(), "header plus 3 thermo rows");

        File noThermo = write("lammps-broken.log", "LAMMPS (29 Sep 2021)\nERROR: pair style\n");
        AnalysisReport fail = ResultAnalysisService.analyze(AnalysisKind.LAMMPS_THERMO,
                new ProjectProperty(), this.tempDir.toFile(), "ml", "ml.log", noThermo,
                new AnalysisParameters());
        assertFalse(fail.isSuccess(), "A LAMMPS log without thermo rows must fail closed");
    }

    @Test
    void testGeometryConvergenceHonestyWithoutStoredSteps() throws IOException {
        Files.copy(Path.of("tests/fixtures/qe/relax_converged.log"),
                this.tempDir.resolve("espresso.log"));
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.GEOMETRY_CONVERGENCE,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(report.isSuccess(),
                "BFGS markers alone must not grant 'optimized' without stored ionic steps");
        assertTrue(report.getText().contains("Status: INCOMPLETE"), report.getText());
        assertTrue(report.getText().contains("BFGS end marker: true"), report.getText());
        assertTrue(report.getText().contains("Final total force: 0.00080000 Ry/bohr"),
                report.getText());
        assertTrue(report.getText().contains("No ionic steps"), report.getText());

        AnalysisReport badThreshold = ResultAnalysisService.analyze(
                AnalysisKind.GEOMETRY_CONVERGENCE, stubProject(this.tempDir),
                new AnalysisParameters().withForceThresholdRyBohr(-1.0e-3));
        assertFalse(badThreshold.isSuccess());
        assertTrue(badThreshold.getText().contains("positive finite"), badThreshold.getText());
    }

    @Test
    void testGeometryConvergenceMissingLogFailsClosed() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.GEOMETRY_CONVERGENCE,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(report.isSuccess());
    }

    @Test
    void testPseudoFamilyWithoutInputFailsClosed() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.PSEUDO_FAMILY,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(report.isSuccess());
        assertTrue(report.getText().contains("no current QE input"), report.getText());
    }

    @Test
    void testSymmetryKpathValidationAndCellRequirement() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(5.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.25, 0.25, 0.25);
        AnalysisReport badTolerance = ResultAnalysisService.analyze(AnalysisKind.SYMMETRY_KPATH,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withSymmetryTolerance(-1.0e-5));
        assertFalse(badTolerance.isSuccess(), "A non-positive tolerance must be rejected");
        assertTrue(badTolerance.getText().contains("positive finite"), badTolerance.getText());

        AnalysisReport noCell = ResultAnalysisService.analyze(AnalysisKind.SYMMETRY_KPATH,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(noCell.isSuccess(), "A project without a cell must fail closed");

        // Environment-dependent path: with a valid cell either the spglib sidecar runs or
        // the report fails closed with an explicit sidecar/Python explanation - never invented.
        AnalysisReport withCell = ResultAnalysisService.analyze(AnalysisKind.SYMMETRY_KPATH,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertTrue(withCell.getText().toLowerCase(java.util.Locale.ROOT).contains("spglib"),
                withCell.getText());
    }

    @Test
    void testXmlSummaryFromDataFileSchema() throws IOException {
        Files.copy(Path.of("tests/fixtures/qe/data-file-schema.xml"),
                this.tempDir.resolve("data-file-schema.xml"));
        File xml = this.tempDir.resolve("data-file-schema.xml").toFile();
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.XML_SUMMARY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", xml,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Total energy: -15.85245678 Ry"), report.getText());
        assertTrue(report.getText().contains("nat: 2"), report.getText());
        assertTrue(report.getText().contains("SCF converged: true"), report.getText());
        assertTrue(report.getText().contains("Per-atom forces: 2 entries"), report.getText());
        assertTrue(report.getText().contains("HARTREE_PER_BOHR"), report.getText());
        assertTrue(report.getText().contains("XML Fermi energy: 6.54"), report.getText());
        assertTrue(report.getText().contains("never invented"), report.getText());

        File garbage = write("data-file-schema-broken.xml", "<unclosed><xml");
        AnalysisReport bad = ResultAnalysisService.analyze(AnalysisKind.XML_SUMMARY,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", garbage,
                new AnalysisParameters());
        assertFalse(bad.isSuccess(), "Malformed XML must fail closed through the parser");
    }

    @Test
    void testElasticModuliFromIsotropicMatrix() throws IOException {
        File elastic = write("elastic-moduli.out",
                "  Elastic Constant Matrix (kbar)\n"
                + "  6000 2000 2000    0    0    0\n"
                + "  2000 6000 2000    0    0    0\n"
                + "  2000 2000 6000    0    0    0\n"
                + "     0    0    0 2000    0    0\n"
                + "     0    0    0    0 2000    0\n"
                + "     0    0    0    0    0 2000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_MODULI,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", elastic,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Bulk modulus K Hill"), report.getText());
        assertTrue(report.getText().contains("3333.333333"), report.getText());
        assertTrue(report.getText().contains("Young's modulus E Hill"), report.getText());
        assertTrue(report.getText().contains("5000.000000"), report.getText());
        assertTrue(report.getText().contains("Poisson ratio nu Hill (unitless)"), report.getText());
        assertTrue(report.getText().contains("0.250000"), report.getText());
        assertTrue(report.getText().contains("Universal anisotropy A^U (unitless)"),
                report.getText());
        assertEquals(12, report.getCsvLines().size(), "header plus 11 moduli rows");

        File unstab = write("elastic-unstable-moduli.out",
                "  Elastic Constant Matrix (kbar)\n"
                + "   500 1000 1000    0    0    0\n"
                + "  1000  500 1000    0    0    0\n"
                + "  1000 1000  500    0    0    0\n"
                + "     0    0    0 2000    0    0\n"
                + "     0    0    0    0 2000    0\n"
                + "     0    0    0    0    0 2000\n");
        AnalysisReport fail = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_MODULI,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", unstab,
                new AnalysisParameters());
        assertFalse(fail.isSuccess(), "A non-SPD tensor cannot produce Reuss moduli");
    }

    @Test
    void testVasprunInspectionAndIncompleteRefusal() throws IOException {
        File vasprun = write("vasprun.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<modeling>\n  <calculation>\n    <scstep>\n      <energy>\n"
                + "        <i name=\"e_fr_energy\"> -12.43500000 </i>\n"
                + "      </energy>\n    </scstep>\n    <dos>\n"
                + "      <i name=\"efermi\"> 4.32100000 </i>\n"
                + "    </dos>\n  </calculation>\n  <structure>\n    <crystal>\n"
                + "      <varray name=\"basis\">\n"
                + "        <v>   5.43000   0.00000   0.00000 </v>\n"
                + "        <v>   0.00000   5.43000   0.00000 </v>\n"
                + "        <v>   0.00000   0.00000   5.43000 </v>\n"
                + "      </varray>\n    </crystal>\n"
                + "    <varray name=\"positions\">\n"
                + "      <v>   0.00000   0.00000   0.00000 </v>\n"
                + "      <v>   0.25000   0.25000   0.25000 </v>\n"
                + "    </varray>\n  </structure>\n</modeling>\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.VASP_VASPRUN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", vasprun,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Fermi energy: 4.321000 eV"), report.getText());
        assertTrue(report.getText().contains("-12.43500000 eV"), report.getText());
        assertTrue(report.getText().contains("Ionic positions: 2 entries"), report.getText());
        assertTrue(report.getText().contains("no VASP workflow is advertised"), report.getText());

        File incomplete = write("vasprun-incomplete.xml",
                "<modeling><structure><varray name=\"basis\">"
                + "<v>1 0 0</v><v>0 1 0</v><v>0 0 1</v>"
                + "</varray></structure></modeling>");
        AnalysisReport bad = ResultAnalysisService.analyze(AnalysisKind.VASP_VASPRUN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", incomplete,
                new AnalysisParameters());
        assertFalse(bad.isSuccess(), "An incomplete vasprun.xml must not be analyzed");
    }

    @Test
    void testCastepLogInspection() throws IOException {
        File castep = write("si.castep",
                "     -------------------------------------------------------------------------\n"
                + "     Final energy, E             =  -1234.567890 eV\n"
                + "     Fermi energy                =   4.321000 eV\n"
                + "     Geometry optimization completed successfully\n"
                + "     -------------------------------------------------------------------------\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CASTEP_LOG,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", castep,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Final energy: -1234.567890 eV"), report.getText());
        assertTrue(report.getText().contains("Geometry optimization completion marker: true"),
                report.getText());
        assertTrue(report.getText().contains("no CASTEP input generation"), report.getText());

        File empty = write("no.castep", "not a castep file\n");
        AnalysisReport bad = ResultAnalysisService.analyze(AnalysisKind.CASTEP_LOG,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", empty,
                new AnalysisParameters());
        assertFalse(bad.isSuccess(), "A non-CASTEP file must fail closed");
    }

    @Test
    void testInputDiffAgainstReferenceFile() throws IOException {
        QESCFInput base = new QESCFInput();
        QENamelist baseSystem = base.getNamelist(QEInput.NAMELIST_SYSTEM);
        baseSystem.setValue(QEValueBase.getInstance("ecutwfc", "30.0"));
        baseSystem.setValue(QEValueBase.getInstance("nat", "2"));

        File reference = write("reference.in",
                "&CONTROL\n   calculation = 'scf'\n/\n"
                + "&SYSTEM\n   ibrav = 0, nat = 2, ntyp = 1, ecutwfc = 45.0\n/\n"
                + "&ELECTRONS\n/\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.INPUT_DIFF,
                stubProjectWithInput(this.tempDir, base, null), reference,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getCsvLines().stream()
                .anyMatch(line -> line.contains("ecutwfc,MODIFIED,30.0,45.0")),
                "ecutwfc change must appear in the CSV: " + report.getCsvLines());
        assertTrue(report.getCsvLines().stream()
                .anyMatch(line -> line.contains("ibrav,ADDED,,0")),
                "ibrav add must appear in the CSV: " + report.getCsvLines());
        assertTrue(report.getText().contains("Nothing in the project input is modified"),
                report.getText());

        AnalysisReport noFile = ResultAnalysisService.analyze(AnalysisKind.INPUT_DIFF,
                stubProjectWithInput(this.tempDir, base, null), null, new AnalysisParameters());
        assertFalse(noFile.isSuccess(), "A missing reference file must fail closed");

        File garbage = write("garbage.in", "hello world, no namelists here\n");
        AnalysisReport empty = ResultAnalysisService.analyze(AnalysisKind.INPUT_DIFF,
                stubProjectWithInput(this.tempDir, base, null), garbage,
                new AnalysisParameters());
        assertFalse(empty.isSuccess(), "A reference with zero parsed namelist values is refused");

        AnalysisReport noInput = ResultAnalysisService.analyze(AnalysisKind.INPUT_DIFF,
                stubProject(this.tempDir), reference, new AnalysisParameters());
        assertFalse(noInput.isSuccess(), "A project without a current input must fail closed");
    }

    @Test
    void testKmeshQualityForAutomaticGammaAndMissing() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);

        QESCFInput automatic = new QESCFInput();
        QEKPoints points = automatic.getCard(QEKPoints.class);
        points.setAutomatic();
        points.setKGrid(new int[] {8, 8, 8});
        points.setKOffset(new int[] {0, 0, 0});
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.KMESH_QUALITY,
                stubProjectWithInput(this.tempDir, automatic, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("K_POINTS automatic: 8 8 8 with offset 0 0 0"),
                report.getText());
        assertTrue(report.getText().contains("0.078540"), report.getText());
        assertTrue(report.getText().contains("RECOMMENDED"), report.getText());
        assertTrue(report.getText().contains("not a convergence proof"), report.getText());
        assertEquals(4, report.getCsvLines().size(), "header plus 3 directions");

        QESCFInput gamma = new QESCFInput();
        gamma.getCard(QEKPoints.class).setGamma();
        AnalysisReport gammaReport = ResultAnalysisService.analyze(AnalysisKind.KMESH_QUALITY,
                stubProjectWithInput(this.tempDir, gamma, cell), new AnalysisParameters());
        assertTrue(gammaReport.isSuccess(), gammaReport.getText());
        assertTrue(gammaReport.getText().contains("gamma (single k-point)"), gammaReport.getText());
        assertTrue(gammaReport.getText().contains("never reported"), gammaReport.getText());

        AnalysisReport noInput = ResultAnalysisService.analyze(AnalysisKind.KMESH_QUALITY,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertFalse(noInput.isSuccess(), "A project without a current input must fail closed");
    }

    @Test
    void testDefectPreviewVacancySubstitutionAndValidation() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.25, 0.25, 0.25);

        AnalysisReport vacancy = ResultAnalysisService.analyze(AnalysisKind.DEFECT_PREVIEW,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withDefectType("vacancy")
                        .withAtomIndices(2, 0, 0, 0).withDefectCharge(1));
        assertTrue(vacancy.isSuccess(), vacancy.getText());
        assertTrue(vacancy.getText().contains("VACANCY"), vacancy.getText());
        assertTrue(vacancy.getText().contains("NOTHING is applied"), vacancy.getText());
        assertTrue(vacancy.getText().contains("10.0000 Ang"), vacancy.getText());
        assertEquals(2, cell.numAtoms(), "The preview must not mutate the live cell");

        AnalysisReport substitution = ResultAnalysisService.analyze(AnalysisKind.DEFECT_PREVIEW,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withDefectType("substitution")
                        .withDefectElement("B").withAtomIndices(1, 0, 0, 0));
        assertTrue(substitution.isSuccess(), substitution.getText());
        assertTrue(substitution.getText().contains("SUBSTITUTION"), substitution.getText());

        AnalysisReport badIndex = ResultAnalysisService.analyze(AnalysisKind.DEFECT_PREVIEW,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withDefectType("vacancy").withAtomIndices(99, 0, 0, 0));
        assertFalse(badIndex.isSuccess(), "An out-of-range atom index must fail closed");

        AnalysisReport blankElement = ResultAnalysisService.analyze(AnalysisKind.DEFECT_PREVIEW,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withDefectType("substitution").withDefectElement("")
                        .withAtomIndices(1, 0, 0, 0));
        assertFalse(blankElement.isSuccess(), "A substitution needs a replacement element");

        AnalysisReport badType = ResultAnalysisService.analyze(AnalysisKind.DEFECT_PREVIEW,
                stubProject(this.tempDir, cell),
                new AnalysisParameters().withDefectType("interstitial").withAtomIndices(1, 0, 0, 0));
        assertFalse(badType.isSuccess(), "Only vacancy/substitution are previewed here");
    }

    @Test
    void testConvergenceReviewFindsPlateauFromEvidence() throws IOException {
        // E(ecut) series: deltas 0.1, 0.02, 0.004, 0.001 Ry -> with 2 atoms and
        // tol 0.001 Ry/atom, first qualifying following-change is |0.004|/2 = 0.002? No:
        // step 3->4 = 0.004/2 = 0.002 > 0.001; step 4->5 = 0.001/2 = 0.0005 <= 0.001,
        // so the recommendation is the parameter of row 4 (ecut=70).
        File csv = write("ecut-series.csv",
                "ecutwfc,total_energy_Ry\n"
                + "30.0,-30.10000000\n"
                + "40.0,-30.20000000\n"
                + "50.0,-30.22000000\n"
                + "60.0,-30.22400000\n"
                + "70.0,-30.22500000\n"
                + "80.0,-30.22520000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CONVERGENCE_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", csv,
                new AnalysisParameters().withAtomCount(2).withEnergyToleranceRyPerAtom(1.0e-3));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("meets the per-atom tolerance: 60.000000"),
                report.getText());
        assertTrue(report.getText().contains("(row 4)"), report.getText());
        assertTrue(report.getText().contains("skipped non-numeric rows (incl. any header): 1"),
                report.getText());
        assertEquals(6, report.getCsvLines().size(), "header plus 5 delta rows");

        File shortSeries = write("ecut-short.csv", "30.0,-30.1\n40.0,-30.2\n");
        AnalysisReport tooShort = ResultAnalysisService.analyze(AnalysisKind.CONVERGENCE_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", shortSeries,
                new AnalysisParameters().withAtomCount(2));
        assertFalse(tooShort.isSuccess(), "A two-point series cannot support a plateau search");

        File unsorted = write("ecut-unsorted.csv",
                "30.0,-30.1\n40.0,-30.2\n35.0,-30.15\n50.0,-30.25\n");
        AnalysisReport badOrder = ResultAnalysisService.analyze(AnalysisKind.CONVERGENCE_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", unsorted,
                new AnalysisParameters().withAtomCount(2));
        assertFalse(badOrder.isSuccess(), "An unsorted series must fail closed");

        File unconverged = write("ecut-unconverged.csv",
                "30.0,-30.0\n40.0,-30.5\n50.0,-31.0\n60.0,-31.5\n");
        AnalysisReport noPlateau = ResultAnalysisService.analyze(AnalysisKind.CONVERGENCE_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", unconverged,
                new AnalysisParameters().withAtomCount(2).withEnergyToleranceRyPerAtom(1.0e-3));
        assertFalse(noPlateau.isSuccess(),
                "A series that never meets the tolerance must not recommend a cutoff");
        assertTrue(noPlateau.getText().contains("extend the series"), noPlateau.getText());
    }

    @Test
    void testSeriesPlanPreviewValidation() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.SERIES_PLAN,
                stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("ecutwfc").withSeriesStart(30.0)
                        .withSeriesStep(10.0).withSeriesCount(4));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("ecutwfc = 60"), report.getText());
        assertTrue(report.getText().contains("no input files are written"), report.getText());
        assertEquals(5, report.getCsvLines().size(), "header plus 4 plan rows");
        assertEquals("index,ecutwfc,suggested_job_name", report.getCsvLines().get(0));

        AnalysisReport badCount = ResultAnalysisService.analyze(AnalysisKind.SERIES_PLAN,
                stubProject(this.tempDir),
                new AnalysisParameters().withSeriesCount(1));
        assertFalse(badCount.isSuccess(), "A single-point series must be rejected");

        AnalysisReport badKeyword = ResultAnalysisService.analyze(AnalysisKind.SERIES_PLAN,
                stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("ecut wfc!"));
        assertFalse(badKeyword.isSuccess(), "An invalid QE keyword must be rejected");

        AnalysisReport zeroStep = ResultAnalysisService.analyze(AnalysisKind.SERIES_PLAN,
                stubProject(this.tempDir),
                new AnalysisParameters().withSeriesStep(0.0));
        assertFalse(zeroStep.isSuccess(), "A zero step must be rejected");
    }

    @Test
    void testPhononModesAuditAndRejection() throws IOException {
        File dynmat = write("dynmat.out",
                "     omega(  1) =       0.394099 [THz] =      13.143245 [cm-1]\n"
                + "     (  0.707107  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
                + "     (  0.707106  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
                + "     omega(  2) =      -1.234567 [THz] =     -41.185683 [cm-1]\n"
                + "     ( -0.707107  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
                + "     (  0.707106  0.000000  0.000000  0.000000  0.000000  0.000000 )\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.PHONON_MODES,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", dynmat,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("PASSED"), report.getText());
        assertTrue(report.getText().contains("-41.185683"), report.getText());
        assertTrue(report.getText().contains("true"), report.getText());
        assertTrue(report.getText().contains("gauge freedom"), report.getText());
        assertEquals(3, report.getCsvLines().size(), "header plus 2 modes");
        assertEquals("mode_index,omega_thz,omega_cm1,imaginary,norm_deviation",
                report.getCsvLines().get(0));

        File unnormalized = write("dynmat-bad.out",
                "     omega(  1) =       1.000000 [THz] =      33.356000 [cm-1]\n"
                + "     (  1.000000  0.0  0.0  0.0  0.0  0.0 )\n"
                + "     (  1.000000  0.0  0.0  0.0  0.0  0.0 )\n");
        AnalysisReport bad = ResultAnalysisService.analyze(AnalysisKind.PHONON_MODES,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", unnormalized,
                new AnalysisParameters());
        assertFalse(bad.isSuccess(),
                "Modes failing the orthonormality audit must not be presented as validated");
        assertTrue(bad.getText().contains("FAILED"), bad.getText());

        File empty = write("dynmat-empty.out", "no omega records here\n");
        AnalysisReport none = ResultAnalysisService.analyze(AnalysisKind.PHONON_MODES,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", empty,
                new AnalysisParameters());
        assertFalse(none.isSuccess(), "A file without dynmat modes must fail closed");
    }

    @Test
    void testVoltageProfileFromHullCsv() throws IOException {
        File csv = write("battery.csv",
                "formula,fraction_B,formation_energy_eV_per_atom\n"
                + "A,0.0,0.0\n"
                + "AB,0.5,-1.0\n"
                + "AB2_meta,0.6667,-0.1\n"
                + "B,1.0,0.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.VOLTAGE_PROFILE,
                new ProjectProperty(), this.tempDir.toFile(), "ab", "ab.log", csv,
                new AnalysisParameters().withIonCharge(1.0));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("phases parsed: 4"), report.getText());
        assertTrue(report.getText().contains("x 0.0000 -> 0.5000 :   +2.0000 V")
                || report.getText().contains("x 0.0000 -> 0.5000 :  +2.0000 V"), report.getText());
        assertTrue(report.getText().contains("metastable phases excluded"), report.getText());
        assertEquals("x_left,x_right,voltage_V,left_phase,right_phase", report.getCsvLines().get(0));
        assertEquals(3, report.getCsvLines().size(),
                "header plus 2 plateaus; the metastable phase never produces one");

        File missingReference = write("battery-noref.csv",
                "A,0.0,0.0\nAB,0.5,-1.0\nAB2,0.8,-0.4\n");
        AnalysisReport fail = ResultAnalysisService.analyze(AnalysisKind.VOLTAGE_PROFILE,
                new ProjectProperty(), this.tempDir.toFile(), "ab", "ab.log", missingReference,
                new AnalysisParameters().withIonCharge(1.0));
        assertFalse(fail.isSuccess(), "A series without the pure-metal reference must fail closed");

        AnalysisReport badCharge = ResultAnalysisService.analyze(AnalysisKind.VOLTAGE_PROFILE,
                new ProjectProperty(), this.tempDir.toFile(), "ab", "ab.log", csv,
                new AnalysisParameters().withIonCharge(-1.0));
        assertFalse(badCharge.isSuccess(), "A non-positive ion charge must be rejected");
    }

    @Test
    void testAdsorptionPreviewCollisionResolutionAndValidation() {
        // Mirror the proven MoleculeAdsorberTest setup exactly.
        Cell slab = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        slab.addAtom("Pt", 5.0, 5.0, 2.0);

        AnalysisReport belowMinimum = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_PREVIEW,
                stubProject(this.tempDir, slab),
                new AnalysisParameters().withMoleculeName("CO").withAdsorbHeight(0.5)
                        .withAdsorbX(0.5).withAdsorbY(0.5));
        // Height 0.5 is below the builder minimum of 1.0 and must be refused, not clamped.
        assertFalse(belowMinimum.isSuccess(), belowMinimum.getText());

        AnalysisReport colliding = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_PREVIEW,
                stubProject(this.tempDir, slab),
                new AnalysisParameters().withMoleculeName("CO").withAdsorbHeight(1.1)
                        .withAdsorbX(0.5).withAdsorbY(0.5));
        assertFalse(colliding.isSuccess(), "Contact 1.1 Angstrom is below the 1.2 limit");
        assertTrue(colliding.getText().contains("Collision detected: true"), colliding.getText());

        AnalysisReport safe = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_PREVIEW,
                stubProject(this.tempDir, slab),
                new AnalysisParameters().withMoleculeName("CO").withAdsorbHeight(2.5)
                        .withAdsorbX(0.5).withAdsorbY(0.5));
        assertTrue(safe.isSuccess(), safe.getText());
        assertTrue(safe.getText().contains("combined preview cell atoms: 3"), safe.getText());
        assertTrue(safe.getText().contains("Collision detected: false"), safe.getText());
        assertEquals(1, slab.numAtoms(), "The live slab must not be modified by the preview");

        AnalysisReport unknown = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_PREVIEW,
                stubProject(this.tempDir, slab),
                new AnalysisParameters().withMoleculeName("C6H12"));
        assertFalse(unknown.isSuccess(), "Unknown templates must fail closed");

        AnalysisReport badPosition = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_PREVIEW,
                stubProject(this.tempDir, slab),
                new AnalysisParameters().withMoleculeName("CO").withAdsorbX(1.5));
        assertFalse(badPosition.isSuccess(), "Out-of-range fractional positions must be rejected");

        AnalysisReport noCell = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_PREVIEW,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(noCell.isSuccess(), "A project without a slab cell must fail closed");
    }

    @Test
    void testProvenanceAttachedByFileDispatch() throws IOException {
        File csv = write("hull-prov.csv",
                "formula,fraction_B,formation_energy_eV_per_atom\n"
                + "A,0.0,0.0\nB,1.0,0.0\nAB,0.5,-0.25\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.HULL_STABILITY,
                new ProjectProperty(), this.tempDir.toFile(), "ab", "ab.log", csv,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.hasProvenance(), "Dispatch must attach provenance records");
        assertTrue(report.getProvenanceLines().toString().contains("HULL_STABILITY"),
                report.getProvenanceLines().toString());
        assertTrue(report.getProvenanceLines().toString().contains("hull-prov.csv"),
                "The resolved source file is part of the provenance: "
                        + report.getProvenanceLines());
        assertTrue(report.getProvenanceLines().toString()
                        .contains("quantumforge.run.ResultAnalysisService"),
                "The producer is recorded: " + report.getProvenanceLines());
        assertTrue(report.renderFullText().contains("--- Provenance ---"), report.renderFullText());
        assertTrue(report.renderFullText().contains(report.getText().substring(0, 20)),
                "The rendered text must contain the original report body");
    }

    @Test
    void testProvenanceDiscoveryAndProjectContexts() {
        AnalysisReport projectReport = ResultAnalysisService.analyze(AnalysisKind.RUN_MANIFEST,
                stubProject(this.tempDir), new AnalysisParameters());
        assertTrue(projectReport.hasProvenance(), "Project-bound reports carry provenance too");
        assertTrue(projectReport.getProvenanceLines().toString().contains("RUN_MANIFEST"),
                projectReport.getProvenanceLines().toString());
        assertTrue(projectReport.getProvenanceLines().toString()
                        .contains(this.tempDir.toFile().getAbsolutePath()),
                "The project directory is the context: " + projectReport.getProvenanceLines());

        // Discovery-based resolution records the discovered file, not an invented one.
        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.CASTEP_LOG,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", null,
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "No CASTEP file must fail closed");
        assertFalse(missing.getProvenanceLines().toString().contains("source:"),
                "Nothing was read, so no source may be claimed: "
                        + missing.getProvenanceLines());
    }

    @Test
    void testLegacyReportConstructorHasNoProvenance() {
        AnalysisReport legacy = new AnalysisReport("t", true, "body text", List.of(), null);
        assertFalse(legacy.hasProvenance(), "The 5-arg constructor keeps provenance empty");
        assertEquals("body text", legacy.renderFullText(),
                "renderFullText degrades to the plain text without provenance");
        AnalysisReport copy = legacy.withProvenance(List.of("source: x.cube"));
        assertTrue(copy.hasProvenance());
        assertEquals("t", copy.getTitle());
        assertFalse(legacy.hasProvenance(), "withProvenance returns a copy; no mutation");
        assertTrue(copy.renderFullText().contains("source: x.cube"), copy.renderFullText());
        assertSame(legacy, legacy.withProvenance(List.of()),
                "Appending nothing keeps the immutable instance");
    }

    @Test
    void testSiteProfileCheckKind() throws IOException {
        File clean = write("cluster.yaml",
                "id: test-cluster\nscheduler: slurm\nstaging_root: /scratch/qf\n"
                        + "scratch_root: /scratch/qf\nmpi_launcher: srun\nmodule: qe/7.5\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.SITE_PROFILE_CHECK,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", clean,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Site id: test-cluster"), report.getText());
        assertTrue(report.getText().contains("Scheduler: slurm"), report.getText());
        assertTrue(report.getText().contains("static: no SSH connection"), report.getText());
        assertEquals(1, report.getCsvLines().size(), "header only: a clean profile has no rows");

        assertTrue(ResultAnalysisService.discover(AnalysisKind.SITE_PROFILE_CHECK,
                        this.tempDir.toFile(), "si.log").contains(clean),
                "Yaml discovery must find the profile");

        File container = write("container-site.yaml",
                "id: c\nscheduler: slurm\nstaging_root: /s\nscratch_root: /s\n"
                        + "module: qe/7.5\ncontainer_image: qe.sif\n");
        AnalysisReport containerReport = ResultAnalysisService.analyze(
                AnalysisKind.SITE_PROFILE_CHECK, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", container, new AnalysisParameters());
        assertTrue(containerReport.isSuccess(), "Warnings do not fail the report");
        assertTrue(containerReport.getText().contains("SITE_CONTAINER_DIGEST_MISSING"),
                containerReport.getText());
        assertTrue(containerReport.getText().contains("SITE_CONTAINER_MPI_ABI"),
                containerReport.getText());
        assertTrue(containerReport.getText().contains("Container image declared: qe.sif"),
                containerReport.getText());

        File bad = write("bad-site.yaml", "id: x\nscheduler: lsf\n");
        AnalysisReport fail = ResultAnalysisService.analyze(AnalysisKind.SITE_PROFILE_CHECK,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", bad,
                new AnalysisParameters());
        assertFalse(fail.isSuccess(), "An unknown scheduler must block");
        assertTrue(fail.getText().contains("SITE_SCHEDULER_UNKNOWN"), fail.getText());
    }

    @Test
    void testMlModelCheckKindAndDomainGate() throws IOException {
        File manifest = write("model-manifest.txt",
                "name: MACE-test\nversion: 1.0\nlicense: MIT\ncitation: ref\n"
                        + "cutoff_angstrom: 6.0\nspecies: Si, O\n"
                        + "sha256: " + "ab".repeat(32) + "\n");
        Cell covered = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        covered.addAtom("Si", 0.1, 0.1, 0.1);
        covered.addAtom("O", 0.6, 0.6, 0.6);
        AnalysisReport ok = ResultAnalysisService.analyze(AnalysisKind.ML_MODEL_CHECK,
                stubProject(this.tempDir, covered), manifest, new AnalysisParameters());
        assertTrue(ok.isSuccess(), ok.getText());
        assertTrue(ok.getText().contains("Model: MACE-test"), ok.getText());
        assertTrue(ok.getText().contains("all project elements are covered"), ok.getText());
        assertTrue(ok.getText().contains("No Python was started"), ok.getText());

        Cell outside = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        outside.addAtom("Si", 0.1, 0.1, 0.1);
        outside.addAtom("H", 0.4, 0.4, 0.4);
        AnalysisReport out = ResultAnalysisService.analyze(AnalysisKind.ML_MODEL_CHECK,
                stubProject(this.tempDir, outside), manifest, new AnalysisParameters());
        assertFalse(out.isSuccess(), "Out-of-domain chemistry must fail the report");
        assertTrue(out.getText().contains("OUT OF DOMAIN"), out.getText());
        assertTrue(out.getCsvLines().toString().contains("ML_OUT_OF_DOMAIN"),
                out.getCsvLines().toString());

        File noHash = write("model-nohash.txt",
                "name: x\nversion: 1\nlicense: MIT\ncitation: c\n"
                        + "cutoff_angstrom: 5.0\nspecies: Si\n");
        AnalysisReport unusable = ResultAnalysisService.analyze(AnalysisKind.ML_MODEL_CHECK,
                stubProject(this.tempDir, covered), noHash, new AnalysisParameters());
        assertFalse(unusable.isSuccess(), "A provenance-less model must fail closed");
        assertTrue(unusable.getText().contains("ML_HASH_MISSING"), unusable.getText());

        AnalysisReport noFile = ResultAnalysisService.analyze(AnalysisKind.ML_MODEL_CHECK,
                stubProject(this.tempDir, covered), null, new AnalysisParameters());
        assertFalse(noFile.isSuccess(), "A missing manifest selection must fail closed");
        assertFalse(noFile.getProvenanceLines().toString().contains("source:"),
                "Nothing may be claimed when no manifest was read");
    }

    @Test
    void testExxGuidanceKindValidationPaths() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);

        QESCFInput automatic = new QESCFInput();
        QEKPoints points = automatic.getCard(QEKPoints.class);
        points.setAutomatic();
        points.setKGrid(new int[] {8, 8, 8});
        points.setKOffset(new int[] {0, 0, 0});

        AnalysisReport ok = ResultAnalysisService.analyze(AnalysisKind.EXX_GUIDANCE,
                stubProjectWithInput(this.tempDir, automatic, cell),
                new AnalysisParameters().withExxNqGrid(2, 2, 2));
        assertTrue(ok.isSuccess(), ok.getText());
        assertTrue(ok.getText().contains("Current automatic k mesh: 8 8 8"), ok.getText());
        assertTrue(ok.getText().contains("pair count before symmetry reduction: 4096"),
                ok.getText());
        assertTrue(ok.getText().contains("does not start a hybrid calculation"), ok.getText());
        assertEquals(4, ok.getCsvLines().size(), "header plus three axes");

        AnalysisReport notDivisor = ResultAnalysisService.analyze(AnalysisKind.EXX_GUIDANCE,
                stubProjectWithInput(this.tempDir, automatic, cell),
                new AnalysisParameters().withExxNqGrid(3, 3, 3));
        assertFalse(notDivisor.isSuccess(), "nq=3 does not divide nk=8 and must block");
        assertTrue(notDivisor.getText().contains("EXX_NQ_NOT_DIVISOR"), notDivisor.getText());

        AnalysisReport zero = ResultAnalysisService.analyze(AnalysisKind.EXX_GUIDANCE,
                stubProjectWithInput(this.tempDir, automatic, cell),
                new AnalysisParameters().withExxNqGrid(0, 2, 2));
        assertFalse(zero.isSuccess(), "nq=0 must fail closed");
        assertTrue(zero.getText().contains("EXX_NQ_INVALID"), zero.getText());

        QESCFInput gamma = new QESCFInput();
        gamma.getCard(QEKPoints.class).setGamma();
        AnalysisReport gammaReport = ResultAnalysisService.analyze(AnalysisKind.EXX_GUIDANCE,
                stubProjectWithInput(this.tempDir, gamma, cell),
                new AnalysisParameters().withExxNqGrid(1, 1, 1));
        assertFalse(gammaReport.isSuccess(),
                "Gamma-only inputs get an honest no-grid statement, not advice");
        assertTrue(gammaReport.getText().contains("Gamma-only"), gammaReport.getText());
    }

    @Test
    void testBzGeometryKindOnKnownCells() {
        Cell cubic = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cubic.addAtom("Si", 0.1, 0.2, 0.3);
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.BZ_GEOMETRY,
                stubProject(this.tempDir, cubic), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Vertices: 8"), report.getText());
        assertTrue(report.getText().contains("Edges: 12"), report.getText());
        assertTrue(report.getText().contains("Faces: 6"), report.getText());
        assertTrue(report.getText().contains("expected (2 pi)^3/V_cell: 0.24805021"),
                report.getText());
        assertTrue(report.getText().contains("Euler characteristic V - E + F = 8 - 12 + 6 = 2"),
                report.getText());
        assertTrue(report.getText().contains("no high-symmetry point names"), report.getText());
        assertEquals(9, report.getCsvLines().size(), "header plus 8 zone vertices");

        AnalysisReport noCell = ResultAnalysisService.analyze(AnalysisKind.BZ_GEOMETRY,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(noCell.isSuccess(), "No cell must fail closed");
    }

    @Test
    void testBandGapKindHonestDirectness() throws IOException {
        File gapped = write("gapped-pw.out",
                "     the Fermi energy is      6.5400 ev\n"
                + "     highest occupied, lowest unoccupied level (ev):   -2.0000     1.5000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.BAND_GAP,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", gapped,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Gap: 3.500000 eV"), report.getText());
        assertTrue(report.getText().contains("Fermi energy: 6.540000 eV"), report.getText());
        assertTrue(report.getText().contains("Directness: unknown"), report.getText());
        assertTrue(report.getText().contains("gapped above the 0.01 eV analysis tolerance"),
                report.getText());
        assertTrue(report.getText().contains("not a convergence-tested band gap"),
                report.getText());

        File direct = write("direct-pw.out", "     direct band gap is    0.5000\n");
        AnalysisReport directReport = ResultAnalysisService.analyze(AnalysisKind.BAND_GAP,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", direct,
                new AnalysisParameters());
        assertTrue(directReport.isSuccess(), directReport.getText());
        assertTrue(directReport.getText().contains("explicitly reported direct"),
                directReport.getText());

        File none = write("nogap-pw.out", "     total energy = -100.0 Ry\n");
        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.BAND_GAP,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", none,
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "Logs without gap records must fail closed");
    }

    @Test
    void testDosIntegrationKindValidatedTrapezoid() throws IOException {
        File pdos = write("espresso.pdos_atm#1(Si)_wfc#2(p).dat",
                "# E (eV)   ldos(E)   pdos(E)\n"
                + "  0.0    0.0   0.0\n"
                + "  1.0    0.0   2.0\n"
                + "  3.0    0.0   4.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.DOS_INTEGRATION,
                new ProjectProperty(), this.tempDir.toFile(), "espresso", "espresso.log", pdos,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("atom #1 (Si), wfc #2 (l=p)"), report.getText());
        // Nonuniform trapezoid: 0.5*1*(0+2) + 0.5*2*(2+4) = 1 + 6 = 7.
        assertTrue(report.getText().contains("Integral (nonuniform trapezoid, energies in eV):"
                + " 7.000000"), report.getText());
        assertTrue(report.getText().contains("NOT an electron count"), report.getText());
        assertEquals(4, report.getCsvLines().size(), "header plus 3 grid rows");

        File headerless = write("espresso.pdos_atm#1(Si)_wfc#1(s).dat",
                "  0.0    0.0\n  1.0    1.0\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.DOS_INTEGRATION,
                new ProjectProperty(), this.tempDir.toFile(), "espresso", "espresso.log",
                headerless, new AnalysisParameters());
        assertFalse(refused.isSuccess(), "Headerless two-column data must be refused");

        File wrongName = write("random.dat", "# E (eV) ldos pdos\n 0.0 1.0 2.0\n 1.0 2.0 3.0\n");
        AnalysisReport nameless = ResultAnalysisService.analyze(AnalysisKind.DOS_INTEGRATION,
                new ProjectProperty(), this.tempDir.toFile(), "espresso", "espresso.log",
                wrongName, new AnalysisParameters());
        assertFalse(nameless.isSuccess(), "Files without the projectwfc naming are refused");
    }

    @Test
    void testElasticDirectionalIsotropicCollapse() throws IOException {
        File elastic = write("elastic-directional.out",
                "  Elastic Constant Matrix (kbar)\n"
                + "  6000 2000 2000    0    0    0\n"
                + "  2000 6000 2000    0    0    0\n"
                + "  2000 2000 6000    0    0    0\n"
                + "     0    0    0 2000    0    0\n"
                + "     0    0    0    0 2000    0\n"
                + "     0    0    0    0    0 2000\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_DIRECTIONAL,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", elastic,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Sampled directions: 175"), report.getText());
        // The 10x-isotropic fixture has E = 5000 kbar in every direction.
        assertTrue(report.getText().contains("E_min = 5000.000000"), report.getText());
        assertTrue(report.getText().contains("E_max = 5000.000000"), report.getText());
        assertTrue(report.getText().contains("E_max/E_min = 1.000000"), report.getText());
        assertEquals(176, report.getCsvLines().size(), "header plus 175 sampled directions");
        assertTrue(report.getText().contains("15-degree grid density"), report.getText());

        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.ELASTIC_DIRECTIONAL,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log",
                write("nope-elastic.out", "nothing here\n"), new AnalysisParameters());
        assertFalse(missing.isSuccess(), "Missing matrix blocks fail closed");
    }

    @Test
    void testMethodsTextKindTranscribesAndNeverFabricates() throws IOException {
        QESCFInput input = new QESCFInput();
        input.updateInputData("&CONTROL\n  calculation = 'relax'\n/\n"
                + "&SYSTEM\n  ibrav = 0, nat = 1, ntyp = 1, ecutwfc = 30.0\n/\n"
                + "ATOMIC_SPECIES\n  Si 28.086 Si.pz-vbc.UPF\n"
                + "K_POINTS automatic\n  4 4 4 0 0 0\n");
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.1, 0.1, 0.1);

        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.METHODS_TEXT,
                stubProjectWithInput(this.tempDir, input, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertNotNull(report.getGeneratedInput(), "The draft must be offered for explicit save");
        assertTrue(report.getGeneratedInput().contains("`relax`"), report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("`ecutwfc` = 30.00 Ry"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("Si <- Si.pz-vbc.UPF"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("### Not recorded"),
                report.getGeneratedInput());
        assertFalse(report.getGeneratedInput().contains("PBE"),
                "Functional names are never fabricated: " + report.getGeneratedInput());

        AnalysisReport noInput = ResultAnalysisService.analyze(AnalysisKind.METHODS_TEXT,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertFalse(noInput.isSuccess(),
                "Without an input the report is not a success, though it still renders");
        assertTrue(noInput.getText().contains("no current input"), noInput.getText());
    }

    @Test
    void testRoCrateKindHashesKnownArtifacts() throws IOException {
        write("espresso.in", "hello-in\n");
        write("espresso.log", "hello\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.RO_CRATE,
                stubProject(this.tempDir), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Included entries: 2"), report.getText());
        assertTrue(report.getText().contains("espresso.in"), report.getText());
        assertTrue(report.getText().contains("espresso.log"), report.getText());
        assertTrue(report.getText().contains("sha256=2cf24dba5fb0a30e26e83b2ac5b9e29e"
                + "1b161e5c1fa7425e73043362938b9824"), report.getText());
        assertNotNull(report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("ro/crate/1.1/context"),
                report.getGeneratedInput());
        assertTrue(report.getText().contains("Payload packaging"), report.getText());

        // A clean second directory (no input/log/manifest) fails closed.
        java.nio.file.Path empty = java.nio.file.Files.createTempDirectory("qf-empty");
        AnalysisReport emptyReport = ResultAnalysisService.analyze(AnalysisKind.RO_CRATE,
                stubProject(empty), new AnalysisParameters());
        assertFalse(emptyReport.isSuccess(), "An empty project directory must fail closed");
    }

    @Test
    void testDefectFormationKindExplicitTerms() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.DEFECT_FORMATION,
                stubProject(this.tempDir), new AnalysisParameters()
                        .withDefectEnergyEv(-151.0).withHostEnergyEv(-100.0)
                        .withChemPotSumEv(10.0).withDefectCharge(2)
                        .withVbmEv(2.0).withFermiEv(0.5).withCorrectionsEv(0.1));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("E_form = -55.900000 eV"), report.getText());
        assertTrue(report.getText().contains("not a defect concentration"), report.getText());
        assertTrue(report.getText().contains("(E_VBM 2.000000 + dE_F 0.500000)"),
                report.getText());

        AnalysisReport neutral = ResultAnalysisService.analyze(AnalysisKind.DEFECT_FORMATION,
                stubProject(this.tempDir), new AnalysisParameters()
                        .withDefectEnergyEv(-60.0).withHostEnergyEv(-55.0)
                        .withChemPotSumEv(-0.75).withDefectCharge(0));
        assertTrue(neutral.isSuccess(), neutral.getText());
        assertTrue(neutral.getText().contains("E_form = -4.250000 eV"), neutral.getText());

        AnalysisReport chargedMissingFermi = ResultAnalysisService.analyze(
                AnalysisKind.DEFECT_FORMATION, stubProject(this.tempDir),
                new AnalysisParameters().withDefectEnergyEv(-60.0).withHostEnergyEv(-55.0)
                        .withChemPotSumEv(0.0).withDefectCharge(-1));
        assertFalse(chargedMissingFermi.isSuccess(),
                "Charged defects without VBM/dE_F must fail closed");

        AnalysisReport nan = ResultAnalysisService.analyze(AnalysisKind.DEFECT_FORMATION,
                stubProject(this.tempDir), new AnalysisParameters().withChemPotSumEv(0.0));
        assertFalse(nan.isSuccess(), "Unsupplied energies stay NaN and must fail closed");
    }

    @Test
    void testAdsorptionEnergyKindExplicitTerms() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_ENERGY,
                stubProject(this.tempDir), new AnalysisParameters()
                        .withDefectEnergyEv(-233.0).withHostEnergyEv(-200.0)
                        .withMoleculeEnergyEv(-30.0).withCorrectionsEv(-0.05));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("E_ads = -3.050000 eV"), report.getText());
        assertTrue(report.getText().contains("negative = binding"), report.getText());
        assertTrue(report.getText().contains("coverage effects"), report.getText());

        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.ADSORPTION_ENERGY,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(missing.isSuccess(), "Unsupplied energies must fail closed");
    }

    @Test
    void testBarrierDiffusionKindExactValuesAndCaveats() {
        AnalysisReport zero = ResultAnalysisService.analyze(AnalysisKind.BARRIER_DIFFUSION,
                stubProject(this.tempDir), new AnalysisParameters()
                        .withBarrierEv(0.0).withTemperatureK(300.0)
                        .withHopAngstrom(2.0).withAttemptThz(1.0).withHopDimension(3));
        assertTrue(zero.isSuccess(), zero.getText());
        assertTrue(zero.getText().contains("D0 = 6.666667e-05 cm^2/s"), zero.getText());
        assertTrue(zero.getText().contains("D(300.0 K) = 6.666667e-05 cm^2/s"), zero.getText());
        // zero barrier -> activation factor exactly 1
        assertTrue(zero.getText().contains("Activation factor exp(-Ea/kBT) = 1.000000e+00"),
                zero.getText());
        assertTrue(zero.getText().contains("MUST NOT be presented as bulk ionic conductivity"),
                zero.getText());

        AnalysisReport negative = ResultAnalysisService.analyze(AnalysisKind.BARRIER_DIFFUSION,
                stubProject(this.tempDir), new AnalysisParameters()
                        .withBarrierEv(-0.2).withTemperatureK(300.0)
                        .withHopAngstrom(2.0).withAttemptThz(1.0));
        assertFalse(negative.isSuccess(), "Negative barriers must fail closed");

        AnalysisReport badHop = ResultAnalysisService.analyze(AnalysisKind.BARRIER_DIFFUSION,
                stubProject(this.tempDir), new AnalysisParameters()
                        .withBarrierEv(0.3).withTemperatureK(300.0)
                        .withHopAngstrom(0.0).withAttemptThz(1.0));
        assertFalse(badHop.isSuccess(), "Zero hop length must fail closed");
    }

    @Test
    void testEffectiveMassKindExactParabolicFit() throws IOException {
        // 27-point grid h=0.05 bohr^-1 with E = kx^2 + 2 ky^2 + 4 kz^2 (Ry):
        // inverse Hessian diag(1/2, 1/4, 1/8), masses m*/m_e = (0.25, 0.50, 1.00).
        StringBuilder fixture = new StringBuilder();
        double h = 0.05;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    double kx = i * h;
                    double ky = j * h;
                    double kz = k * h;
                    fixture.append(String.format(java.util.Locale.ROOT,
                            "%.8f,%.8f,%.8f,%.12f%n", kx, ky, kz,
                            kx * kx + 2.0 * ky * ky + 4.0 * kz * kz));
                }
            }
        }
        // Fortran D-exponent duplicate of the (0.05,0,0) row plus one junk row.
        fixture.append("5.0D-2, 0.0D0, 0.0D0, 2.5D-3\n");
        fixture.append("not,a,data,row\n");
        File mass = write("si-emk.csv", fixture.toString());
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.EFFECTIVE_MASS,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", mass,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Fit rows: 28; rejected rows: 1"),
                report.getText());
        assertTrue(report.getText().contains("Inverse-Hessian eigenvalues (sorted):"
                + " 1.25000000e-01 2.50000000e-01 5.00000000e-01"), report.getText());
        assertTrue(report.getText().contains("Physical atomic-unit masses m*/m_e = 2 x"
                + " eigenvalue (E was in Ry): 0.250000 0.500000 1.000000"), report.getText());
        assertTrue(report.getText().contains("POSITIVE eigenmass"), report.getText());
        assertEquals(6, report.getCsvLines().size(),
                "header + 3 tensor rows + eigenvalue + mass rows");
        assertTrue(report.getCsvLines().get(4).startsWith("eigenvalues_asc,"),
                report.getCsvLines().get(4));

        // Singular design: kx fixed at zero for every point cannot fit x curvature.
        StringBuilder flat = new StringBuilder();
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                flat.append(String.format(java.util.Locale.ROOT,
                        "0.0,%.8f,%.8f,%.12f%n", j * h, k * h,
                        2.0 * j * h * j * h + 4.0 * k * h * k * h));
            }
        }
        AnalysisReport singular = ResultAnalysisService.analyze(AnalysisKind.EFFECTIVE_MASS,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log",
                write("flat-mass.csv", flat.toString()), new AnalysisParameters());
        assertFalse(singular.isSuccess(), "Coplanar-only sampling must fail closed");
        assertTrue(singular.getText().contains("singular/ill-conditioned"),
                singular.getText());

        AnalysisReport tooFew = ResultAnalysisService.analyze(AnalysisKind.EFFECTIVE_MASS,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log",
                write("tiny-mass.csv", "0,0,0,1\n0.01,0,0,1\n0,0.01,0,1\n"),
                new AnalysisParameters());
        assertFalse(tooFew.isSuccess(), "Fewer than 7 rows must fail closed");
        assertTrue(tooFew.getText().contains("needs >= 7"), tooFew.getText());
    }

    @Test
    void testConstraintsPreviewKindExactFlagsAndValidation() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.20, 0.0);
        cell.addAtom("Si", 0.15, 0.20, 0.30);

        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CONSTRAINTS_PREVIEW,
                stubProject(this.tempDir, cell), new AnalysisParameters()
                        .withConstraintSpec("1:000; 2-3:110").withConstraintMode("relax"));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getGeneratedInput() != null
                && report.getGeneratedInput().startsWith("ATOMIC_POSITIONS angstrom"),
                report.getGeneratedInput());
        // Atom 1 fully frozen, atoms 2-3 fixed in z, atom 4 default free.
        String[] blockLines = report.getGeneratedInput().split("\n");
        assertEquals(5, blockLines.length, "header plus 4 position lines");
        assertTrue(blockLines[1].endsWith("   0  0  0"), blockLines[1]);
        assertTrue(blockLines[2].endsWith("   1  1  0"), blockLines[2]);
        assertTrue(blockLines[3].endsWith("   1  1  0"), blockLines[3]);
        assertTrue(blockLines[4].endsWith("   1  1  1"), blockLines[4]);
        assertTrue(report.getText().contains("explicitly constrained: 3"), report.getText());
        assertTrue(report.getText().contains("with any frozen axis: 3"), report.getText());
        assertTrue(report.getText().contains("PREVIEW FOR REVIEW ONLY"), report.getText());
        assertEquals(5, report.getCsvLines().size(), "header plus 4 atom rows");
        assertTrue(report.getCsvLines().get(1).startsWith("1,Si,0,0,0,true"),
                report.getCsvLines().get(1));
        assertTrue(report.getCsvLines().get(4).startsWith("4,Si,1,1,1,false"),
                report.getCsvLines().get(4));

        AnalysisReport duplicates = ResultAnalysisService.analyze(AnalysisKind.CONSTRAINTS_PREVIEW,
                stubProject(this.tempDir, cell), new AnalysisParameters()
                        .withConstraintSpec("1:000; 1:111").withConstraintMode("relax"));
        assertFalse(duplicates.isSuccess(), "Duplicated atom indices must fail closed");

        AnalysisReport outOfRange = ResultAnalysisService.analyze(AnalysisKind.CONSTRAINTS_PREVIEW,
                stubProject(this.tempDir, cell), new AnalysisParameters()
                        .withConstraintSpec("5:000"));
        assertFalse(outOfRange.isSuccess(), "Index beyond the cell must fail closed");

        AnalysisReport badMode = ResultAnalysisService.analyze(AnalysisKind.CONSTRAINTS_PREVIEW,
                stubProject(this.tempDir, cell), new AnalysisParameters()
                        .withConstraintSpec("1:000").withConstraintMode("scf"));
        assertFalse(badMode.isSuccess(), "scf does not interpret if_pos; must be refused");

        AnalysisReport emptySpec = ResultAnalysisService.analyze(AnalysisKind.CONSTRAINTS_PREVIEW,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertFalse(emptySpec.isSuccess(), "An empty specification must fail closed");

        AnalysisReport noCell = ResultAnalysisService.analyze(AnalysisKind.CONSTRAINTS_PREVIEW,
                stubProject(this.tempDir), new AnalysisParameters().withConstraintSpec("1:000"));
        assertFalse(noCell.isSuccess(), "A project without a cell must fail closed");
    }

    @Test
    void testPhonopyDataReviewKindRoundTripAndCaveats() throws IOException {
        QEPhonopyForceSetsWriter writer = new QEPhonopyForceSetsWriter();
        writer.addRecord(1, new double[] {0.01, 0.0, 0.0},
                new double[][] {{0.1, 0.0, 0.0}, {-0.1, 0.0, 0.0}});
        writer.addRecord(2, new double[] {0.0, 0.01, 0.0},
                new double[][] {{0.0, 0.05, 0.0}, {0.0, -0.05, 0.0}});
        File forceSets = this.tempDir.resolve("FORCE_SETS").toFile();
        writer.writeForceSetsFile(forceSets, 2);

        Cell matching = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        matching.addAtom("Si", 0.0, 0.0, 0.0);
        matching.addAtom("Si", 0.25, 0.25, 0.25);
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.PHONOPY_DATA_REVIEW,
                stubProject(this.tempDir, matching), forceSets, new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "Atoms per set: 2; displacement sets: 2; distinct displaced atoms: 2"),
                report.getText());
        assertTrue(report.getText().contains("Global max |F| = 0.1000000000"),
                report.getText());
        assertTrue(report.getText().contains("Global mean |F| = 0.0750000000"),
                report.getText());
        assertTrue(report.getText().contains("atom counts numerically match (2)"),
                report.getText());
        assertTrue(report.getText().contains("unitless"), report.getText());
        assertTrue(report.getText().contains("Ry/bohr -> eV/Ang = 25.71"), report.getText());
        assertEquals(3, report.getCsvLines().size(), "header plus 2 set rows");
        assertTrue(report.getCsvLines().get(1).startsWith("1,1,0.0100000000"),
                report.getCsvLines().get(1));

        Cell larger = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        larger.addAtom("Si", 0.0, 0.0, 0.0);
        larger.addAtom("Si", 0.25, 0.25, 0.25);
        larger.addAtom("Si", 0.25, 0.25, 0.75);
        AnalysisReport mismatch = ResultAnalysisService.analyze(AnalysisKind.PHONOPY_DATA_REVIEW,
                stubProject(this.tempDir, larger), forceSets, new AnalysisParameters());
        assertTrue(mismatch.isSuccess(), mismatch.getText());
        assertTrue(mismatch.getText().contains("EXPECTED when FORCE_SETS belongs to a "
                + "supercell"), mismatch.getText());

        File truncated = write("FORCE_SETS-bad",
                "2\n1\n1\n0.01 0.0 0.0\n0.1 0.0 0.0\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.PHONOPY_DATA_REVIEW,
                stubProject(this.tempDir, matching), truncated, new AnalysisParameters());
        assertFalse(refused.isSuccess(), "A truncated FORCE_SETS must fail closed");

        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.PHONOPY_DATA_REVIEW,
                stubProject(this.tempDir, matching), null, new AnalysisParameters());
        assertFalse(missing.isSuccess(), "A missing file must fail closed");
    }

    @Test
    void testTrajectoryIndexKindStreamingAndFailClosed() throws IOException {
        String frame = "2\ncomment\nSi 0.0 0.0 0.0\nSi 1.0 1.0 1.0\n";
        File traj = write("md-traj.xyz", frame + frame + frame);
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.TRAJECTORY_INDEX,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", traj,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("complete frames: 3; atoms per frame: 2"),
                report.getText());
        assertTrue(report.getText().contains("Stored frame offsets: 3 (complete)"),
                report.getText());
        assertTrue(report.getText().contains("Truncated tail frame after the last complete "
                + "frame: no"), report.getText());
        assertEquals(4, report.getCsvLines().size(), "header plus 3 offset rows");
        assertTrue(report.getCsvLines().get(1).equals("1,0"), report.getCsvLines().get(1));
        int frameBytes = frame.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(report.getCsvLines().get(2).equals("2," + frameBytes),
                report.getCsvLines().get(2));

        File cut = write("md-trunc.xyz", frame + "2\ncomment\nSi 0.0 0.0\n");
        AnalysisReport tail = ResultAnalysisService.analyze(AnalysisKind.TRAJECTORY_INDEX,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", cut,
                new AnalysisParameters());
        assertTrue(tail.isSuccess(), tail.getText());
        assertTrue(tail.getText().contains("complete frames: 1"), tail.getText());
        assertTrue(tail.getText().contains("YES (reported, not indexed)"), tail.getText());

        File mixed = write("md-mixed.xyz",
                frame + "3\ncomment\nH 0 0 0\nH 0 0 0\nH 0 0 0\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.TRAJECTORY_INDEX,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", mixed,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "Changing topology must fail closed");
        assertTrue(refused.getText().contains("TRAJ_INCONSISTENT")
                || refused.getText().contains("fixed-topology"), refused.getText());
    }

    @Test
    void testMlpDatasetCheckKindSchemaAndLeakReview() throws IOException {
        String frameA = "2\nLattice=\"10 0 0 0 10 0 0 0 10\" "
                + "Properties=species:S:1:pos:R:3 energy=-15.5 pbc=\"T T T\"\n"
                + "Si 0 0 0\nSi 1.35 1.35 1.35\n";
        File valid = write("dataset.extxyz", frameA);
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.MLP_DATASET_CHECK,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", valid,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Frames: 1; atoms per frame: 2..2"),
                report.getText());
        assertTrue(report.getText().contains("Frames with energy/free_energy label: 1 of 1"),
                report.getText());
        assertTrue(report.getText().contains("-15.5000000000"), report.getText());
        assertTrue(report.getText().contains("Properties schema: \"species:S:1:pos:R:3\""),
                report.getText());
        assertTrue(report.getText().contains("schema-level validation only"),
                report.getText());

        File duplicated = write("dataset-dup.extxyz", frameA + frameA);
        AnalysisReport dup = ResultAnalysisService.analyze(AnalysisKind.MLP_DATASET_CHECK,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", duplicated,
                new AnalysisParameters());
        assertTrue(dup.isSuccess(), dup.getText());
        assertTrue(dup.getText().contains("Exact-byte duplicate frames: 1"), dup.getText());
        assertTrue(dup.getText().contains("leakage risk"), dup.getText());
        assertEquals(2, dup.getCsvLines().size(), "header plus one duplicate pair");
        assertTrue(dup.getCsvLines().get(1).startsWith("1,2,true"), dup.getCsvLines().get(1));

        File nolabel = write("dataset-nolabel.extxyz",
                "1\ncomment\nH 0 0 0\n");
        AnalysisReport warned = ResultAnalysisService.analyze(AnalysisKind.MLP_DATASET_CHECK,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", nolabel,
                new AnalysisParameters());
        assertTrue(warned.isSuccess(), warned.getText());
        assertTrue(warned.getText().contains("training set"), warned.getText());
        assertTrue(warned.getText().contains("periodic"), warned.getText());

        File broken = write("dataset-broken.extxyz", "1\ncomment\nH 0 NaN 0\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.MLP_DATASET_CHECK,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", broken,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "Non-finite coordinates must fail closed");
    }

    @Test
    void testSeriesCompareKindExactMetricsAndHonesty() throws IOException {
        File series = write("a-vs-b-series.csv",
                "param,E_Ry_A,E_Ry_B\n"
                + "30,-5.0,-4.5\n"
                + "40,-6.0,-6.25\n"
                + "50,-7.0,-7.75\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ENERGY_SERIES_COMPARE,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", series,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Delta E2 - E1: RMS 0.5400617249"),
                report.getText());
        assertTrue(report.getText().contains("mean signed -0.1666666667"), report.getText());
        assertTrue(report.getText().contains("Max |delta| 0.7500000000 at param = 50.0000"),
                report.getText());
        assertTrue(report.getText().contains("sign crossings of delta: 1"), report.getText());
        assertTrue(report.getText().contains("NO reference alignment was applied"),
                report.getText());
        assertEquals(4, report.getCsvLines().size(), "header plus 3 rows with deltas");
        assertTrue(report.getCsvLines().get(1).endsWith(",0.5000000000"),
                report.getCsvLines().get(1));
        assertTrue(report.getCsvLines().get(3).endsWith(",-0.7500000000"),
                report.getCsvLines().get(3));

        File shallow = write("vs.csv", "a,b,c\n30,1,2\n");
        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.ENERGY_SERIES_COMPARE, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", shallow, new AnalysisParameters());
        assertFalse(refused.isSuccess(), "A single-row series must fail closed");
    }

    @Test
    void testTensorEigenKindExactEigenvaluesAndRefusals() throws IOException {
        File tensor = write("dielectric-tensor.dat",
                "2.0 0.0 0.0\n0.0 5.0 0.0\n0.0 0.0 8.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.TENSOR_EIGEN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", tensor,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "Eigenvalues (sorted ascending): 2.0000000000e+00  5.0000000000e+00"
                        + "  8.0000000000e+00"), report.getText());
        assertTrue(report.getText().contains("Trace: direct 1.5000000000e+01"), report.getText());
        assertTrue(report.getText().contains("Determinant: cofactor 8.0000000000e+01"),
                report.getText());
        assertTrue(report.getText().contains("Structure: SPD"), report.getText());
        assertTrue(report.getText().contains("Anisotropy spread"), report.getText());
        assertEquals(4, report.getCsvLines().size(), "header plus 3 eigenvalue rows");
        assertTrue(report.getCsvLines().get(3).startsWith("3,8.0000000000e+00"),
                report.getCsvLines().get(3));

        // Rotated tensor R^T diag(0.5, 0.25, 0.125) R with a 30-degree z rotation.
        double c = Math.cos(Math.toRadians(30.0));
        double s = Math.sin(Math.toRadians(30.0));
        double d1 = 0.5;
        double d2 = 0.25;
        double m11 = c * c * d1 + s * s * d2;
        double m22 = s * s * d1 + c * c * d2;
        double m12 = c * s * (d1 - d2);
        File rotated = write("rotated-tensor.dat",
                String.format(java.util.Locale.ROOT, "%.12f %.12f 0\n%.12f %.12f 0\n0 0 0.125\n",
                        m11, m12, m12, m22));
        AnalysisReport rotatedReport = ResultAnalysisService.analyze(AnalysisKind.TENSOR_EIGEN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", rotated,
                new AnalysisParameters());
        assertTrue(rotatedReport.isSuccess(), rotatedReport.getText());
        assertTrue(rotatedReport.getText().contains(
                "Eigenvalues (sorted ascending): 1.2500000000e-01  2.5000000000e-01"
                        + "  5.0000000000e-01"), rotatedReport.getText());

        File asymmetric = write("asymmetric-tensor.dat",
                "1.0 0.5 0.0\n0.5001 1.0 0.0\n0.0 0.0 1.0\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.TENSOR_EIGEN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", asymmetric,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "Asymmetric tensors must fail closed");
        assertTrue(refused.getText().contains("symmetric"), refused.getText());

        File shallow = write("short-tensor.dat", "1 2 3\n4 5 6\n");
        AnalysisReport tooShort = ResultAnalysisService.analyze(AnalysisKind.TENSOR_EIGEN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", shallow,
                new AnalysisParameters());
        assertFalse(tooShort.isSuccess(), "Two rows are not a 3x3 tensor");

        File indefinite = write("indefinite-tensor.dat",
                "1.0 0.0 0.0\n0.0 -2.0 0.0\n0.0 0.0 3.0\n");
        AnalysisReport neg = ResultAnalysisService.analyze(AnalysisKind.TENSOR_EIGEN,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", indefinite,
                new AnalysisParameters());
        assertTrue(neg.isSuccess(), neg.getText());
        assertTrue(neg.getText().contains("INDEFINITE"), neg.getText());
    }

    @Test
    void testPhononModeFramesKindSynthesisAndFailClosed() throws IOException {
        String dynmat =
                "     Diagonalizing the dynamical matrix\n"
                + "     q = (    0.000000000   0.000000000   0.000000000 )\n"
                + " **************************************************************************\n"
                + "     omega(  1) =       0.394099 [THz] =      13.143245 [cm-1]\n"
                + " **************************************************************************\n"
                + "     (  0.707107  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
                + "     (  0.707106  0.000000  0.000000  0.000000  0.000000  0.000000 )\n"
                + " **************************************************************************\n"
                + "     omega(  2) =      -1.234567 [THz] =     -41.185683 [cm-1]\n"
                + " **************************************************************************\n"
                + "     ( -0.707107  0.000000  0.000000  0.100000  0.000000  0.000000 )\n"
                + "     (  0.707100  0.000000  0.000000  0.000000  0.000000  0.000000 )\n";
        File modesFile = write("dynmat.modes.out", dynmat);
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);       // (0, 0, 0) angstrom
        cell.addAtom("Si", 0.15, 0.0, 0.0);      // (1.5, 0, 0) angstrom

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, cell), modesFile,
                new AnalysisParameters().withModeIndex(1).withFrameCount(4)
                        .withFrameAmplitudeAng(0.2));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getGeneratedInput() != null, "Frames must need explicit save");
        String document = report.getGeneratedInput();
        assertTrue(document.contains("frame 1/4 phase=sin(2*pi*0/4)=+0.000000 mode=1"),
                document);
        // Frame 2 (phase +1): atom1 x = 0 + 0.2*0.707107 = 0.1414214,
        // atom2 x = 1.5 + 0.2*0.707106 = 1.6414212.
        assertTrue(document.contains("  0.14142140"), document);
        assertTrue(document.contains("  1.64142120"), document);
        // Frame 4 (phase -1): atom1 x = -0.1414214, atom2 x = 1.3585788.
        assertTrue(document.contains(" -0.14142140"), document);
        assertTrue(document.contains("  1.35857880"), document);
        assertTrue(report.getText().contains("omega = 0.394099 THz = 13.143245 cm-1"),
                report.getText());
        assertTrue(report.getText().contains("VISUAL scaling"), report.getText());
        assertEquals(5, report.getCsvLines().size(), "header plus 4 frame rows");
        assertEquals("1,0.00000000", report.getCsvLines().get(1));
        assertEquals("2,1.00000000", report.getCsvLines().get(2));

        // Imaginary mode still synthesizes (instability eigenvector) but says so.
        AnalysisReport imaginary = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, cell), modesFile,
                new AnalysisParameters().withModeIndex(2).withFrameCount(3)
                        .withFrameAmplitudeAng(0.1));
        assertTrue(imaginary.isSuccess(), imaginary.getText());
        assertTrue(imaginary.getText().contains("IMAGINARY"), imaginary.getText());
        assertTrue(imaginary.getText().contains("instability eigenvector"),
                imaginary.getText());
        assertTrue(imaginary.getText().contains("0.10000000"),
                "max dropped imaginary component 0.1 must be reported");

        AnalysisReport absent = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, cell), modesFile,
                new AnalysisParameters().withModeIndex(9));
        assertFalse(absent.isSuccess(), "A mode outside the file's index range must fail");
        assertTrue(absent.getText().contains("1..2"), absent.getText());

        AnalysisReport nonPositive = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, cell), modesFile,
                new AnalysisParameters().withModeIndex(0));
        assertFalse(nonPositive.isSuccess(), "Mode index 0 is not 1-based");

        AnalysisReport noFile = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, cell), null,
                new AnalysisParameters());
        assertFalse(noFile.isSuccess(), "Missing modes file must fail closed");

        AnalysisReport noCell = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir), modesFile,
                new AnalysisParameters());
        assertFalse(noCell.isSuccess(), "Missing live cell must fail closed");

        Cell threeAtoms = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        threeAtoms.addAtom("Si", 0.0, 0.0, 0.0);
        threeAtoms.addAtom("Si", 0.15, 0.0, 0.0);
        threeAtoms.addAtom("Si", 0.15, 0.20, 0.0);
        AnalysisReport mismatch = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, threeAtoms),
                modesFile, new AnalysisParameters().withModeIndex(1));
        assertFalse(mismatch.isSuccess(), "Cell/mode atom-count mismatch must fail closed");
        assertTrue(mismatch.getText().contains("Atom-count mismatch"), mismatch.getText());

        AnalysisReport badAmplitude = ResultAnalysisService.analyze(
                AnalysisKind.PHONON_MODE_FRAMES, stubProject(this.tempDir, cell), modesFile,
                new AnalysisParameters().withModeIndex(1).withFrameAmplitudeAng(0.0));
        assertFalse(badAmplitude.isSuccess(), "Zero amplitude must be refused");
    }

    @Test
    void testHyperfineLookupKindCoverageAndFailClosed() {
        AnalysisReport lookup = ResultAnalysisService.analyze(AnalysisKind.HYPERFINE_LOOKUP,
                stubProject(this.tempDir),
                new AnalysisParameters().withIsotopeLabel("13C"));
        assertTrue(lookup.isSuccess(), lookup.getText());
        assertTrue(lookup.getText().contains("gN = 1.404824"), lookup.getText());
        assertTrue(lookup.getText().contains("A_iso not computed"), lookup.getText());
        assertTrue(lookup.getText().contains("FERMI CONTACT"), lookup.getText());
        assertEquals(7, lookup.getCsvLines().size(), "header plus 6 covered isotopes");

        AnalysisReport coupled = ResultAnalysisService.analyze(AnalysisKind.HYPERFINE_LOOKUP,
                stubProject(this.tempDir),
                new AnalysisParameters().withIsotopeLabel("13C").withNuclearSpinDensity(2.5));
        assertTrue(coupled.isSuccess(), coupled.getText());
        // 44.757237 * 1.404824 * 2.5 = 157.19010... MHz
        assertTrue(coupled.getText().contains("A_iso = 157.190102 MHz"), coupled.getText());
        assertEquals(8, coupled.getCsvLines().size(), "coverage rows plus the A_iso row");
        assertTrue(coupled.getCsvLines().get(7).startsWith("13C_A_ISO_MHZ,157.19010"),
                coupled.getCsvLines().get(7));

        // A negative spin density on a negative-gN isotope yields a positive coupling.
        AnalysisReport signed = ResultAnalysisService.analyze(AnalysisKind.HYPERFINE_LOOKUP,
                stubProject(this.tempDir),
                new AnalysisParameters().withIsotopeLabel("29Si").withNuclearSpinDensity(-1.2));
        assertTrue(signed.isSuccess(), signed.getText());
        // 44.757237 * (-1.11058) * (-1.2) = +59.6477907... MHz
        assertTrue(signed.getText().contains("A_iso = 59.647791 MHz"), signed.getText());

        AnalysisReport unknown = ResultAnalysisService.analyze(AnalysisKind.HYPERFINE_LOOKUP,
                stubProject(this.tempDir), new AnalysisParameters().withIsotopeLabel("99Xx"));
        assertFalse(unknown.isSuccess(), "Uncovered isotopes must fail closed");
        assertTrue(unknown.getText().contains("13C"), "Covered set must be listed");

        AnalysisReport blank = ResultAnalysisService.analyze(AnalysisKind.HYPERFINE_LOOKUP,
                stubProject(this.tempDir), new AnalysisParameters().withIsotopeLabel("  "));
        assertFalse(blank.isSuccess(), "A blank isotope label must fail closed");
    }

    @Test
    void testKeywordHelpKindLookupCrossCheckAndFailClosed() {
        QESCFInput base = new QESCFInput();
        base.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("ecutwfc", "30.0"));

        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.KEYWORD_HELP,
                stubProjectWithInput(this.tempDir, base, null),
                new AnalysisParameters().withSeriesKeyword("ECUTWFC"));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Keyword: ecutwfc   (namelist &SYSTEM)"),
                report.getText());
        assertTrue(report.getText().contains("Ry"), report.getText());
        assertTrue(report.getText().contains("INPUT_PW.html"), report.getText());
        assertTrue(report.getText().contains("Current input: &SYSTEM sets ecutwfc ="),
                report.getText());
        assertTrue(report.getText().contains("curated, human-vetted subset"),
                report.getText());
        assertEquals(2, report.getCsvLines().size());

        AnalysisReport absent = ResultAnalysisService.analyze(AnalysisKind.KEYWORD_HELP,
                stubProjectWithInput(this.tempDir, base, null),
                new AnalysisParameters().withSeriesKeyword("smearing"));
        assertTrue(absent.isSuccess(), absent.getText());
        assertTrue(absent.getText().contains("&SYSTEM does not set smearing"),
                absent.getText());

        AnalysisReport noInput = ResultAnalysisService.analyze(AnalysisKind.KEYWORD_HELP,
                stubProject(this.tempDir), new AnalysisParameters().withSeriesKeyword("conv_thr"));
        assertTrue(noInput.isSuccess(), noInput.getText());
        assertTrue(noInput.getText().contains("Input cross-check skipped"), noInput.getText());

        AnalysisReport unknown = ResultAnalysisService.analyze(AnalysisKind.KEYWORD_HELP,
                stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("notakeyword"));
        assertFalse(unknown.isSuccess(), "Uncovered keywords fail closed, not improvised");
        assertTrue(unknown.getText().contains("INPUT_PW.html"), unknown.getText());
        assertTrue(unknown.getText().contains("ecutwfc"),
                "Covered names must be listed: " + unknown.getText());

        AnalysisReport blank = ResultAnalysisService.analyze(AnalysisKind.KEYWORD_HELP,
                stubProject(this.tempDir), new AnalysisParameters().withSeriesKeyword(" "));
        assertFalse(blank.isSuccess(), "A blank keyword must fail closed");
    }

    @Test
    void testArraySweepPlanKindManifestAndRefusals() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.ARRAY_SWEEP_PLAN,
                stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("ecutwfc").withSeriesStart(30.0)
                        .withSeriesStep(10.0).withSeriesCount(3).withJobBaseName("si-cut"));
        assertTrue(report.isSuccess(), report.getText());
        String manifest = report.getGeneratedInput();
        assertTrue(manifest != null, "The JSONL manifest must require explicit save");
        assertEquals(3, manifest.trim().split("\n").length);
        assertTrue(manifest.contains("{\"task_index\":3,\"keyword\":\"ecutwfc\","
                + "\"value\":50.0,\"directory\":\"si-cut-003\"}"), manifest);
        assertTrue(report.getText().contains("exit 2  # REQUIRED-EDIT guard"),
                report.getText());
        assertTrue(report.getText().contains("#SBATCH --array=1-3"), report.getText());
        assertTrue(report.getText().contains("nothing was submitted"), report.getText());
        assertEquals(4, report.getCsvLines().size());
        assertEquals("1,30.0,si-cut-001", report.getCsvLines().get(1));
        assertEquals("3,50.0,si-cut-003", report.getCsvLines().get(3));

        AnalysisReport badKeyword = ResultAnalysisService.analyze(
                AnalysisKind.ARRAY_SWEEP_PLAN, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("9bad"));
        assertFalse(badKeyword.isSuccess(), "Malformed keywords must fail closed");

        AnalysisReport zeroStep = ResultAnalysisService.analyze(
                AnalysisKind.ARRAY_SWEEP_PLAN, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("ecutwfc").withSeriesStep(0.0));
        assertFalse(zeroStep.isSuccess(), "A zero step must fail closed");

        AnalysisReport oneTask = ResultAnalysisService.analyze(
                AnalysisKind.ARRAY_SWEEP_PLAN, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("ecutwfc").withSeriesCount(1));
        assertFalse(oneTask.isSuccess(), "One task is not an array sweep");

        AnalysisReport badName = ResultAnalysisService.analyze(
                AnalysisKind.ARRAY_SWEEP_PLAN, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("ecutwfc")
                        .withJobBaseName("bad name!"));
        assertFalse(badName.isSuccess(), "Unsafe job base names must fail closed");
    }

    @Test
    void testCellExtXyzExportKindDocumentAndRefusals() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.0, 0.0);
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CELL_EXTXYZ_EXPORT,
                stubProject(this.tempDir, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        String document = report.getGeneratedInput();
        assertTrue(document != null, "The extXYZ document must require explicit save");
        String[] lines = document.split("\n");
        assertEquals(4, lines.length);
        assertEquals("2", lines[0]);
        assertTrue(lines[1].contains("Lattice=\"10.0 0.0 0.0 0.0 10.0 0.0 0.0 0.0 10.0\""),
                lines[1]);
        assertTrue(lines[1].contains("pbc=\"T T T\""), lines[1]);
        assertEquals("Si 1.5 0.0 0.0", lines[3]);
        assertTrue(report.getText().contains("geometry ONLY"), report.getText());
        assertEquals(2, report.getCsvLines().size());

        AnalysisReport noCell = ResultAnalysisService.analyze(AnalysisKind.CELL_EXTXYZ_EXPORT,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(noCell.isSuccess(), "A project without atoms must fail closed");
    }

    @Test
    void testRamanIrSpectrumKindSelfCheckAndRefusals() throws IOException {
        File modes = write("dynmat.spectra.out",
                "      mode   1   freq    120.45 cm-1   IR intensity    0.5000"
                        + "   Raman activity    1.2412\n"
                + "      mode   2   freq    -50.00 cm-1   IR intensity    0.3000"
                        + "   Raman activity    2.0000\n"
                + "      mode   3   freq    300.00 cm-1   IR intensity    0.0000"
                        + "   Raman activity    0.0000\n"
                + "      mode   4   freq    500.00 cm-1   IR intensity    1.0000"
                        + "   Raman activity    0.2500\n");

        AnalysisReport ir = ResultAnalysisService.analyze(AnalysisKind.RAMAN_IR_SPECTRUM,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", modes,
                new AnalysisParameters().withSpectrumChannel("ir").withFwhmCm1(5.0));
        assertTrue(ir.isSuccess(), ir.getText());
        assertTrue(ir.getText().contains("Channel: IR"), ir.getText());
        assertTrue(ir.getText().contains("included (real frequency, positive IR activity): 2"),
                ir.getText());
        assertTrue(ir.getText().contains("imaginary (skipped): 1"), ir.getText());
        assertTrue(ir.getText().contains("zero-activity (skipped): 1"), ir.getText());
        assertTrue(ir.getText().contains("-50.0000"), ir.getText());
        assertTrue(ir.getText().contains("total included activity 1.50000000"), ir.getText());
        assertTrue(ir.getText().contains("SELF-CHECK - grid integral"), ir.getText());
        // Grid: max(0, 120.45-50) = 70.45 .. 550.0, step 0.5 -> 960 points + header.
        assertEquals(961, ir.getCsvLines().size(), "header plus 960 grid points");
        assertTrue(ir.getText().contains("DISPLAY choice"), ir.getText());
        assertTrue(ir.getText().contains("NO Bose-Einstein"), ir.getText());

        AnalysisReport raman = ResultAnalysisService.analyze(AnalysisKind.RAMAN_IR_SPECTRUM,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", modes,
                new AnalysisParameters().withSpectrumChannel("raman").withFwhmCm1(5.0));
        assertTrue(raman.isSuccess(), raman.getText());
        assertTrue(raman.getText().contains("Channel: RAMAN"), raman.getText());
        // 1.2412 + 0.2500 = 1.4912
        assertTrue(raman.getText().contains("total included activity 1.49120000"),
                raman.getText());
        assertEquals("frequency_cm1,intensity_raman", raman.getCsvLines().get(0));

        AnalysisReport badChannel = ResultAnalysisService.analyze(
                AnalysisKind.RAMAN_IR_SPECTRUM, new ProjectProperty(), this.tempDir.toFile(),
                "si", "si.log", modes,
                new AnalysisParameters().withSpectrumChannel("green"));
        assertFalse(badChannel.isSuccess(), "Unknown channels must fail closed");

        AnalysisReport badFwhm = ResultAnalysisService.analyze(AnalysisKind.RAMAN_IR_SPECTRUM,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", modes,
                new AnalysisParameters().withFwhmCm1(0.0));
        assertFalse(badFwhm.isSuccess(), "A zero FWHM must fail closed");

        AnalysisReport hugeFwhm = ResultAnalysisService.analyze(
                AnalysisKind.RAMAN_IR_SPECTRUM, new ProjectProperty(), this.tempDir.toFile(),
                "si", "si.log", modes,
                new AnalysisParameters().withFwhmCm1(500.0));
        assertFalse(hugeFwhm.isSuccess(), "FWHM beyond the 200 cm-1 cap must fail closed");

        File noModes = write("empty.spectra.out", "nothing spectroscopic here\n");
        AnalysisReport empty = ResultAnalysisService.analyze(AnalysisKind.RAMAN_IR_SPECTRUM,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", noModes,
                new AnalysisParameters());
        assertFalse(empty.isSuccess(), "A file without mode rows must fail closed");

        File allSkipped = write("allzero.spectra.out",
                "      mode   1   freq    120.45 cm-1   IR intensity    0.0000"
                        + "   Raman activity    0.0000\n");
        AnalysisReport none = ResultAnalysisService.analyze(AnalysisKind.RAMAN_IR_SPECTRUM,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", allSkipped,
                new AnalysisParameters().withSpectrumChannel("ir"));
        assertFalse(none.isSuccess(), "Zero-activity files must fail closed, not plot zeros");
    }

    @Test
    void testTrajectoryWindowScanKindSampledStatsAndRefusals() throws IOException {
        File traj = write("scan.xyz",
                "2\nframe 1\nSi 0.0 0.0 0.0\nSi 1.0 0.0 0.0\n"
                + "2\nframe 2\nSi 0.0 1.0 0.0\nSi 2.0 1.0 0.0\n"
                + "2\nframe 3\nSi 0.0 0.0 2.0\nSi 4.0 0.0 2.0\n");

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.TRAJECTORY_WINDOW_SCAN, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", traj,
                new AnalysisParameters().withWindowStartFrame(1).withWindowStride(2));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Complete frames indexed: 3"), report.getText());
        assertTrue(report.getText().contains("Sampled 2 frame(s) at stride 2"),
                report.getText());
        // Centroid drift frame 1 -> 3: (0.5,0,0) -> (2,0,2) = sqrt(2.25+4) = 2.5
        assertTrue(report.getText().contains("mean 2.500000 A"), report.getText());
        assertTrue(report.getText().contains("max 2.500000 A"), report.getText());
        assertEquals(3, report.getCsvLines().size(), "header plus 2 sampled frames");
        assertTrue(report.getCsvLines().get(1)
                .startsWith("1,0.50000000,0.00000000,0.00000000,"), report.getCsvLines().get(1));
        assertTrue(report.getCsvLines().get(2)
                .startsWith("3,2.00000000,0.00000000,2.00000000,"), report.getCsvLines().get(2));
        assertTrue(report.getText().contains("NO periodic unwrapping"), report.getText());

        File withTail = write("tail.xyz",
                "2\nframe 1\nSi 0.0 0.0 0.0\nSi 1.0 0.0 0.0\n"
                + "2\nframe 2\nSi 0.0 1.0 0.0\nSi 2.0 1.0 0.0\n"
                + "2\npartial\nSi 0.0 0.0 2.0\n");
        AnalysisReport tail = ResultAnalysisService.analyze(
                AnalysisKind.TRAJECTORY_WINDOW_SCAN, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", withTail,
                new AnalysisParameters());
        assertTrue(tail.isSuccess(), tail.getText());
        assertTrue(tail.getText().contains("truncated tail: yes (excluded from sampling)"),
                tail.getText());
        assertTrue(tail.getText().contains("Complete frames indexed: 2"), tail.getText());

        AnalysisReport badStride = ResultAnalysisService.analyze(
                AnalysisKind.TRAJECTORY_WINDOW_SCAN, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", traj,
                new AnalysisParameters().withWindowStride(0));
        assertFalse(badStride.isSuccess(), "A zero stride must fail closed");

        AnalysisReport badStart = ResultAnalysisService.analyze(
                AnalysisKind.TRAJECTORY_WINDOW_SCAN, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", traj,
                new AnalysisParameters().withWindowStartFrame(9));
        assertFalse(badStart.isSuccess(), "A start past the last frame must fail closed");
    }

    @Test
    void testTensorDirectionalKindEigenBasisAndGrid() throws IOException {
        File diag = write("diag-tensor.dat", "2 0 0\n0 5 0\n0 0 8\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.TENSOR_DIRECTIONAL, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", diag, new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Self-check: residual 0.000e+00"),
                report.getText());
        assertTrue(report.getText().contains("2.0000000000e+00"), report.getText());
        assertTrue(report.getText().contains("min 2.0000000000e+00"), report.getText());
        assertTrue(report.getText().contains("max 8.0000000000e+00"), report.getText());
        assertEquals(313, report.getCsvLines().size(), "header plus 312 grid samples");
        assertTrue(report.getText().contains("quadratic-form directional surface"),
                report.getText());

        // 30-degree rotated diag(0.125, 0.25, 0.5): eigen grid hits (az 30, pol 90).
        File rotated = write("rot-tensor.dat",
                "0.15625 -0.05412658773852742 0.0\n"
                + "-0.05412658773852742 0.21875 0.0\n"
                + "0.0 0.0 0.5\n");
        AnalysisReport rot = ResultAnalysisService.analyze(
                AnalysisKind.TENSOR_DIRECTIONAL, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", rotated, new AnalysisParameters());
        assertTrue(rot.isSuccess(), rot.getText());
        assertTrue(rot.getText().contains("1.2500000000e-01"), rot.getText());
        assertTrue(rot.getText().contains("0.86602540"),
                "Principal direction must be recovered: " + rot.getText());
        assertTrue(rot.getText().contains("min 1.2500000000e-01 at (az 30.0, pol 90.0)"),
                rot.getText());
        assertTrue(rot.getText().contains("gauge"), rot.getText());

        File asymmetric = write("asym-tensor.dat", "1 0.5 0\n0.4 1 0\n0 0 1\n");
        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.TENSOR_DIRECTIONAL, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", asymmetric,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "Asymmetric tensors must fail closed");

        File shortFile = write("short-tensor2.dat", "1 0 0\n0 1 0\n");
        AnalysisReport shallow = ResultAnalysisService.analyze(
                AnalysisKind.TENSOR_DIRECTIONAL, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", shortFile,
                new AnalysisParameters());
        assertFalse(shallow.isSuccess(), "Two rows are not a tensor");
    }

    @Test
    void testDensityDifferenceKindGateAndStats() throws IOException {
        java.util.function.BiFunction<String, String, Path> cube = (directory, values) -> {
            try {
                Path dir = Files.createDirectories(this.tempDir.resolve(directory));
                String header = "comment\ncomment\n1 0 0 0\n2 2 0 0\n1 0 2 0\n1 0 0 2\n"
                        + "1 0 0 0 0\n";
                Files.writeString(dir.resolve("system.cube"), header + "1.0 2.0\n");
                Files.writeString(dir.resolve("component.cube"), header + values + "\n");
                return dir;
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        };

        Path pair = cube.apply("case-ok", "0.5 1.5");
        AnalysisReport ok = ResultAnalysisService.analyze(AnalysisKind.DENSITY_DIFFERENCE,
                stubProject(pair), pair.resolve("system.cube").toFile(),
                new AnalysisParameters());
        assertTrue(ok.isSuccess(), ok.getText());
        assertTrue(ok.getText().contains("delta = SYSTEM - COMPONENT"), ok.getText());
        assertTrue(ok.getText().contains("SYSTEM    = system.cube"), ok.getText());
        assertTrue(ok.getText().contains("COMPONENT = component.cube"), ok.getText());
        // delta = (0.5, 0.5) over 2 voxels -> min/max/mean|delta| all 0.5
        assertTrue(ok.getText().contains("min 0.50000000"), ok.getText());
        assertTrue(ok.getText().contains("max 0.50000000"), ok.getText());
        assertTrue(ok.getText().contains("mean|delta| 0.50000000"), ok.getText());
        assertTrue(ok.getText().contains("negative fraction 0.000000"), ok.getText());
        // sum(delta)*V/N = 1.0 * (16*bohr^3 in A^3)/2 voxels = 1.1854777 (repaired pin:
        // the previous 2.3709554 figure was the cell volume, not the V/N integral)
        assertTrue(ok.getText().contains("Integrated difference: 1.1854777"), ok.getText());
        assertEquals("integrated_delta,1.1854777", ok.getCsvLines().get(5));
        assertTrue(ok.getText().contains("NO alignment, resampling or unit conversion"),
                ok.getText());

        Path pairNeg = cube.apply("case-neg", "3.0 1.0");
        AnalysisReport neg = ResultAnalysisService.analyze(AnalysisKind.DENSITY_DIFFERENCE,
                stubProject(pairNeg), pairNeg.resolve("system.cube").toFile(),
                new AnalysisParameters());
        assertTrue(neg.isSuccess(), neg.getText());
        assertTrue(neg.getText().contains("negative fraction 0.500000"), neg.getText());
        assertTrue(neg.getText().contains("min -2.0000000"), neg.getText());

        // Incompatible component grid: nx 3 on the reference, values must follow.
        Path dirBad = Files.createDirectories(this.tempDir.resolve("case-bad"));
        String header = "comment\ncomment\n1 0 0 0\n2 2 0 0\n1 0 2 0\n1 0 0 2\n"
                + "1 0 0 0 0\n";
        String header3 = "comment\ncomment\n1 0 0 0\n3 2 0 0\n1 0 2 0\n1 0 0 2\n"
                + "1 0 0 0 0\n";
        Files.writeString(dirBad.resolve("system.cube"), header + "1.0 2.0\n");
        Files.writeString(dirBad.resolve("component.cube"), header3 + "0.5 1.0 1.5\n");
        AnalysisReport incompatible = ResultAnalysisService.analyze(
                AnalysisKind.DENSITY_DIFFERENCE, stubProject(dirBad),
                dirBad.resolve("system.cube").toFile(), new AnalysisParameters());
        assertFalse(incompatible.isSuccess(),
                "Mismatched grids must fail closed, never subtract");
        assertTrue(incompatible.getText().contains("NOT compatible"),
                incompatible.getText());

        Path dirNone = Files.createDirectories(this.tempDir.resolve("case-none"));
        Files.writeString(dirNone.resolve("system.cube"), header + "1.0 2.0\n");
        AnalysisReport none = ResultAnalysisService.analyze(AnalysisKind.DENSITY_DIFFERENCE,
                stubProject(dirNone), dirNone.resolve("system.cube").toFile(),
                new AnalysisParameters());
        assertFalse(none.isSuccess(), "A missing component must fail closed");

        Path dirTwo = Files.createDirectories(this.tempDir.resolve("case-two"));
        Files.writeString(dirTwo.resolve("system.cube"), header + "1.0 2.0\n");
        Files.writeString(dirTwo.resolve("a.cube"), header + "0.5 1.5\n");
        Files.writeString(dirTwo.resolve("b.cube"), header + "0.5 1.5\n");
        AnalysisReport ambiguous = ResultAnalysisService.analyze(
                AnalysisKind.DENSITY_DIFFERENCE, stubProject(dirTwo),
                dirTwo.resolve("system.cube").toFile(), new AnalysisParameters());
        assertFalse(ambiguous.isSuccess(), "Ambiguous components must fail closed");
        assertTrue(ambiguous.getText().contains("a.cube"), ambiguous.getText());
        assertTrue(ambiguous.getText().contains("b.cube"), ambiguous.getText());

        AnalysisReport noSystem = ResultAnalysisService.analyze(
                AnalysisKind.DENSITY_DIFFERENCE, stubProject(this.tempDir), null,
                new AnalysisParameters());
        assertFalse(noSystem.isSuccess(), "A missing system cube must fail closed");
    }

    @Test
    void testSupercellPreviewKindExactTransformAndRefusals() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        cell.addAtom("Si", 0.15, 0.0, 0.0);

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters().withSupercellSpec("2 0 0; 0 2 0; 0 0 2"));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("det(M) = 8"), report.getText());
        assertTrue(report.getText().contains("Atoms: 2 -> 16"), report.getText());
        assertTrue(report.getText().contains("Cell volume: 1000.00000000 -> 8000.00000000"),
                report.getText());
        String block = report.getGeneratedInput();
        assertTrue(block != null && block.startsWith("CELL_PARAMETERS angstrom\n"),
                block);
        assertTrue(block.contains("20.00000000"), block);
        assertTrue(report.getText().contains("deviation 0.0e+00"), report.getText());
        assertEquals(9, report.getCsvLines().size());

        AnalysisReport shear = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters().withSupercellSpec("1 1 0; 0 1 0; 0 0 1"));
        assertTrue(shear.isSuccess(), shear.getText());
        assertTrue(shear.getText().contains("det(M) = 1"), shear.getText());
        assertTrue(shear.getText().contains("Atoms: 2 -> 2"), shear.getText());
        assertTrue(shear.getCsvLines().get(6).equals("new_lattice_row_1,10.0000000000 "
                + "10.0000000000 0.0000000000"), shear.getCsvLines().get(6));

        AnalysisReport singular = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters().withSupercellSpec("1 1 0; 2 2 0; 0 0 1"));
        assertFalse(singular.isSuccess(), "Singular matrices must fail closed");

        AnalysisReport handed = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters().withSupercellSpec("-1 0 0; 0 1 0; 0 0 1"));
        assertFalse(handed.isSuccess(), "Handedness flips must fail closed");

        AnalysisReport bound = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters().withSupercellSpec("9 0 0; 0 1 0; 0 0 1"));
        assertFalse(bound.isSuccess(), "Over-bound entries must fail closed");

        AnalysisReport empty = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters());
        assertFalse(empty.isSuccess(), "An empty spec must fail closed");

        AnalysisReport noCell = ResultAnalysisService.analyze(
                AnalysisKind.SUPERCELL_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withSupercellSpec("2 0 0; 0 2 0; 0 0 1"));
        assertFalse(noCell.isSuccess(), "No live cell must fail closed");
    }

    @Test
    void testHubbardHpDraftKindContextGateAndDraft() {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL)
                .setValue(QEValueBase.getInstance("prefix", "'nio'"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("lda_plus_u", ".true."));
        input.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("hubbard_u(1)", "6.0"));
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(8.0));
        cell.addAtom("Ni", 0.0, 0.0, 0.0);

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.HUBBARD_HP_DRAFT, stubProjectWithInput(this.tempDir, input, cell),
                new AnalysisParameters().withExxNqGrid(2, 2, 4));
        assertTrue(report.isSuccess(), report.getText());
        String draft = report.getGeneratedInput();
        assertTrue(draft != null, "The draft must require explicit save");
        assertTrue(draft.startsWith("&INPUTHP"), draft);
        assertTrue(draft.contains("prefix = 'nio',"), draft);
        assertTrue(draft.contains("nq3 = 4,"), draft);
        assertTrue(draft.contains("REVIEW before running hp.x"), draft);
        assertTrue(report.getText().contains("lda_plus_u=true"), report.getText());
        assertTrue(report.getText().contains("q grid 2 x 2 x 4"), report.getText());
        assertEquals(6, report.getCsvLines().size());

        QESCFInput plain = new QESCFInput();
        plain.getNamelist(QEInput.NAMELIST_SYSTEM)
                .setValue(QEValueBase.getInstance("ecutwfc", "30.0"));
        AnalysisReport noHubbard = ResultAnalysisService.analyze(
                AnalysisKind.HUBBARD_HP_DRAFT,
                stubProjectWithInput(this.tempDir, plain, cell), new AnalysisParameters());
        assertFalse(noHubbard.isSuccess(),
                "A placeholder U setup must never be drafted");

        AnalysisReport badQ = ResultAnalysisService.analyze(
                AnalysisKind.HUBBARD_HP_DRAFT, stubProjectWithInput(this.tempDir, input, cell),
                new AnalysisParameters().withExxNqGrid(0, 2, 2));
        assertFalse(badQ.isSuccess(), "An invalid q grid must fail closed");

        AnalysisReport noInput = ResultAnalysisService.analyze(
                AnalysisKind.HUBBARD_HP_DRAFT, stubProject(this.tempDir),
                new AnalysisParameters());
        assertFalse(noInput.isSuccess(), "A missing input must fail closed");
    }

    @Test
    void testTimingResourceKindTableAndRefusals() throws IOException {
        File log = write("timing.log",
                "some noisy output\n"
                + "     init_run     :      1.24s CPU      1.30s WALL (        1 calls)\n"
                + "     electrons    :     10.50s CPU     11.00s WALL (        9 calls)\n"
                + "     c_bands      :      8.25s CPU      8.40s WALL (       90 calls)\n"
                + "     PWSCF        :     26.10s CPU     27.05s WALL\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.TIMING_RESOURCE, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", log, new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("pw.x total: CPU 26.10 s, WALL 27.05 s"),
                report.getText());
        assertTrue(report.getText().contains("electronics routines nest and overlap")
                || report.getText().contains("QE timers nest and overlap"),
                report.getText());
        // electrons: 11.00/27.05*100 = 40.67%
        assertTrue(report.getText().contains("electrons"), report.getText());
        assertTrue(report.getText().contains("40.67"), report.getText());
        assertEquals(4, report.getCsvLines().size(), "header plus 3 routines");
        assertEquals("routine,cpu_s,wall_s,calls", report.getCsvLines().get(0));
        assertEquals("electrons,10.50,11.00,9", report.getCsvLines().get(1),
                "CSV keeps wall order");
        assertTrue(report.getText().contains("unfinished"), report.getText());

        File partial = write("partial-timing.log",
                "     init_run     :      1.24s CPU      1.30s WALL (        1 calls)\n");
        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.TIMING_RESOURCE, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", partial, new AnalysisParameters());
        assertFalse(refused.isSuccess(), "No PWSCF total = unfinished run = fail closed");
    }

    @Test
    void testWorkspaceSearchKindCatalogueAndRefusals() throws IOException {
        Files.writeString(this.tempDir.resolve("si.in"),
                "&CONTROL\n   calculation = 'scf'\n/\n"
                + "&SYSTEM\n   ibrav = 2, nat = 2, ntyp = 1, ecutwfc = 30.0\n/\n"
                + "&ELECTRONS\n/\n"
                + "ATOMIC_SPECIES\n Si 28.0855 Si.pz-vbc.UPF\n"
                + "ATOMIC_POSITIONS crystal\n Si 0.0 0.0 0.0\n Si 0.25 0.25 0.25\n"
                + "K_POINTS automatic\n 4 4 4 0 0 0\n");
        Files.writeString(this.tempDir.resolve("si.log"), "work\nJOB DONE.\n");
        Files.writeString(this.tempDir.resolve("notes.txt"), "side file\n");

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.WORKSPACE_SEARCH, stubProject(this.tempDir),
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Catalogued 2 artifact(s)"), report.getText());
        assertTrue(report.getText().contains("1 non-artifact file(s)"), report.getText());
        assertTrue(report.getText().contains("si.in"), report.getText());
        assertTrue(report.getText().contains("completed (marker: JOB DONE.)"),
                report.getText());
        assertTrue(report.getText().contains("not the indexed"), report.getText());
        assertEquals(3, report.getCsvLines().size());
        assertTrue(report.getCsvLines().get(1).startsWith("si.in,INPUT,Si,2,scf"),
                report.getCsvLines().get(1));

        Path empty = Files.createDirectories(this.tempDir.resolve("empty-ws"));
        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.WORKSPACE_SEARCH, stubProject(empty), new AnalysisParameters());
        assertFalse(refused.isSuccess(), "An empty directory must fail closed");
    }

    @Test
    void testTemplateLibraryKindListDetailAndFailClosed() {
        AnalysisReport list = ResultAnalysisService.analyze(
                AnalysisKind.TEMPLATE_LIBRARY, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword(""));
        assertTrue(list.isSuccess(), list.getText());
        assertTrue(list.getText().contains("Curated templates (6)"), list.getText());
        for (String name : new String[] {"scf-basic", "relax-bfgs", "vc-relax-crystal",
                "bands-path", "nscf-dos", "phonon-gamma0"}) {
            assertTrue(list.getText().contains(name), name + " missing: " + list.getText());
        }
        assertTrue(list.getText().contains("REVIEWED STARTING POINTS"), list.getText());
        assertEquals(7, list.getCsvLines().size(), "header + 6 template rows");

        AnalysisReport detail = ResultAnalysisService.analyze(
                AnalysisKind.TEMPLATE_LIBRARY, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("VC-RELAX-crystal"));
        assertTrue(detail.isSuccess(), detail.getText());
        assertTrue(detail.getText().contains("PREREQUISITES"), detail.getText());
        assertTrue(detail.getText().contains("ecutrho"), detail.getText());
        assertTrue(detail.getText().contains("Known pitfalls"), detail.getText());

        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.TEMPLATE_LIBRARY, stubProject(this.tempDir),
                new AnalysisParameters().withSeriesKeyword("made-up-flow"));
        assertFalse(refused.isSuccess(), "Unknown templates fail closed, nothing improvised");
        assertTrue(refused.getText().contains("scf-basic"),
                "The refusal must list the curated set: " + refused.getText());
    }

    @Test
    void testPoscarReviewKindParsesAndRefuses() throws IOException {
        File poscar = write("POSCAR_quartz",
                "SiO2 quartz cell\n"
                + "1.0\n"
                + "4.9 0.0 0.0\n"
                + "-2.45 4.24352 0.0\n"
                + "0.0 0.0 5.4\n"
                + "Si O\n"
                + "1 2\n"
                + "Direct\n"
                + "0.0 0.0 0.0\n"
                + "0.33 0.33 0.33\n"
                + "0.5 0.5 0.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.POSCAR_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "si", "si.log", poscar,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("SiO2 quartz cell"), report.getText());
        assertTrue(report.getText().contains("Cell volume: 112.283539"), report.getText());
        assertTrue(report.getText().contains("composition Si=1,O=2"), report.getText());
        assertTrue(report.getText().contains("Direct (fractional)"), report.getText());
        assertTrue(report.getText().contains("REVIEW only"), report.getText());
        assertTrue(report.getText().contains("NOT applied"), report.getText());
        assertEquals(4, report.getCsvLines().size(), "header + 3 atoms");
        assertEquals("2,O,0.8085000000,1.4003616000,1.7820000000,",
                report.getCsvLines().get(2));

        File vasp4 = write("Fe.vasp",
                "bcc Fe volume-scaled\n"
                + "-27.0\n"
                + "3.0 0.0 0.0\n"
                + "0.0 3.0 0.0\n"
                + "0.0 0.0 3.0\n"
                + "2\n"
                + "Cartesian\n"
                + "1.0 1.0 1.0\n"
                + "2.0 2.0 2.0\n");
        AnalysisReport scaled = ResultAnalysisService.analyze(AnalysisKind.POSCAR_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "fe", "fe.log", vasp4,
                new AnalysisParameters());
        assertTrue(scaled.isSuccess(), scaled.getText());
        assertTrue(scaled.getText().contains("VOLUME-scaled"), scaled.getText());
        assertTrue(scaled.getText().contains("= 1.0000000000 applied"), scaled.getText());
        assertTrue(scaled.getText().contains("ABSENT (VASP 4 layout"), scaled.getText());

        File broken = write("POSCAR_bad",
                "c\n0.0\n1 0 0\n0 1 0\n0 0 1\nH\n1\nDirect\n0 0 0\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.POSCAR_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "h", "h.log", broken,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "A zero scale factor must fail closed");
        assertTrue(refused.getText().contains("[POSCAR_SCALE]"), refused.getText());
    }

    @Test
    void testElasticElateDraftKindStableGatedAndRefusals() throws IOException {
        String cubic = "  Elastic Constant Matrix (kbar)\n"
                + "  5000 1000 1000    0    0    0\n"
                + "  1000 5000 1000    0    0    0\n"
                + "  1000 1000 5000    0    0    0\n"
                + "     0    0    0 2000    0    0\n"
                + "     0    0    0    0 2000    0\n"
                + "     0    0    0    0    0 2000\n";
        File stable = write("elate-stable.out", cubic);
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.ELASTIC_ELATE_DRAFT, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", stable, new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("max |C_ij - C_ji| = 0"), report.getText());
        assertTrue(report.getText().contains("STABLE"), report.getText());
        assertTrue(report.getText().contains("Voigt 1=xx 2=yy 3=zz"), report.getText());
        assertTrue(report.getText().contains("NO unit conversion"), report.getText());
        assertTrue(report.getGeneratedInput() != null
                && report.getGeneratedInput().contains("TENSOR = ["),
                "the draft travels the generated-input channel only");
        assertTrue(report.getGeneratedInput().contains("[5000.0, 1000.0"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("CONVERT_TO_GPA = False"),
                report.getGeneratedInput());
        assertEquals(7, report.getCsvLines().size(), "header + 6 Voigt rows");
        assertEquals("1,5000.000000,1000.000000,1000.000000,0.000000000,0.000000000,"
                + "0.000000000", report.getCsvLines().get(1));

        File unstable = write("elate-unstable.out", cubic.replace("  5000 1000 1000",
                "   500 1000 1000"));
        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.ELASTIC_ELATE_DRAFT, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", unstable, new AnalysisParameters());
        assertFalse(refused.isSuccess(),
                "an unstable tensor gets NO draft - directional numbers would be unphysical");
        assertTrue(refused.getText().contains("FAILED Born mechanical stability"),
                refused.getText());
        assertTrue(refused.getGeneratedInput() == null,
                "no artifact for unstable tensors");

        File asymmetric = write("elate-asym.out", cubic.replace("  1000 5000 1000",
                "  1200 5000 1000"));
        AnalysisReport asymRefused = ResultAnalysisService.analyze(
                AnalysisKind.ELASTIC_ELATE_DRAFT, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", asymmetric, new AnalysisParameters());
        assertFalse(asymRefused.isSuccess());
        assertTrue(asymRefused.getText().contains("[ELATE_ASYMMETRY]"),
                asymRefused.getText());

        File none = write("elate-none.out", "scf output only\n");
        AnalysisReport noBlock = ResultAnalysisService.analyze(
                AnalysisKind.ELASTIC_ELATE_DRAFT, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", none, new AnalysisParameters());
        assertFalse(noBlock.isSuccess());
        assertTrue(noBlock.getText().contains("[ELATE_BLOCK]"), noBlock.getText());
    }

    @Test
    void testSpinCubeMagnetizationKindMomentAndRefusals() throws IOException {
        String header = "comment\\ncomment\\n1 0 0 0\\n2 2 0 0\\n1 0 2 0\\n1 0 0 2\\n"
                + "1 0 0 0 0\\n";
        Path pair = Files.createDirectories(this.tempDir.resolve("spin-ok"));
        Files.writeString(pair.resolve("spin_up.cube"), header + "3.0 1.0\\n");
        Files.writeString(pair.resolve("spin_down.cube"), header + "1.0 1.0\\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.SPIN_CUBE_MAGNETIZATION, stubProject(pair),
                pair.resolve("spin_up.cube").toFile(), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("MAJORITY (up)   = spin_up.cube"),
                report.getText());
        assertTrue(report.getText().contains("MINORITY (down) = spin_down.cube"),
                report.getText());
        // N_up = (2.3709554/2) * 4.0 = 4.74191077 ; N_down = *2.0 = 2.37095538
        assertTrue(report.getText().contains("N_up = 4.741911"), report.getText());
        assertTrue(report.getText().contains("N_down = 2.370955"), report.getText());
        assertTrue(report.getText().contains("Spin excess (N_up - N_down) = 2.370955"),
                report.getText());
        assertTrue(report.getText().contains("TOTAL MAGNETIZATION = 2.370955 mu_B"),
                report.getText());
        assertTrue(report.getText().contains("Spin polarization of the charge = 0.3333"),
                report.getText());
        assertTrue(report.getText().contains("min 0.000000, max 2.000000"),
                report.getText());
        assertTrue(report.getText().contains("minority-sign voxels: 0 of 2 (0.00%"),
                report.getText());
        assertTrue(report.getText().contains("COLLINEAR two-file protocol"),
                report.getText());
        assertEquals("magnetization_per_cell,2.37095538,mu_B", report.getCsvLines().get(5));
        assertEquals("total_charge,7.11286615,electrons", report.getCsvLines().get(3));

        // Sign-honest reversed pairing: excess goes negative, moment sign follows.
        AnalysisReport reversed = ResultAnalysisService.analyze(
                AnalysisKind.SPIN_CUBE_MAGNETIZATION, stubProject(pair),
                pair.resolve("spin_down.cube").toFile(), new AnalysisParameters());
        assertTrue(reversed.isSuccess(), reversed.getText());
        assertTrue(reversed.getText().contains("TOTAL MAGNETIZATION = -2.370955 mu_B"),
                reversed.getText());

        // Three cubes: the minority choice is ambiguous and must refuse.
        Files.writeString(pair.resolve("third.cube"), header + "0.0 0.0\\n");
        AnalysisReport ambiguous = ResultAnalysisService.analyze(
                AnalysisKind.SPIN_CUBE_MAGNETIZATION, stubProject(pair),
                pair.resolve("spin_up.cube").toFile(), new AnalysisParameters());
        assertFalse(ambiguous.isSuccess(), "2 leftover candidates must refuse, not guess");
        assertTrue(ambiguous.getText().contains("Ambiguous minority choice"),
                ambiguous.getText());

        // One cube only: no minority partner.
        Path solo = Files.createDirectories(this.tempDir.resolve("spin-solo"));
        Files.writeString(solo.resolve("spin_up.cube"), header + "3.0 1.0\\n");
        AnalysisReport lonely = ResultAnalysisService.analyze(
                AnalysisKind.SPIN_CUBE_MAGNETIZATION, stubProject(solo),
                solo.resolve("spin_up.cube").toFile(), new AnalysisParameters());
        assertFalse(lonely.isSuccess(), "No minority cube must fail closed");
    }

    @Test
    void testEsmSlabCheckKindVerdictsAndGeometryGate() {
        double[][] lattice = {{10.0, 0.0, 0.0}, {0.0, 10.0, 0.0}, {0.0, 0.0, 30.0}};
        Cell cell = new Cell(lattice);
        cell.addAtom("Cu", 0.5, 0.5, 0.4);
        cell.addAtom("Cu", 0.5, 0.5, 0.6);
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("assume_isolated", "'esm'"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("esm_bc", "'bc1'"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("esm_w", "-0.2"));

        AnalysisReport ready = ResultAnalysisService.analyze(AnalysisKind.ESM_SLAB_CHECK,
                stubProjectWithInput(this.tempDir, input, cell), new AnalysisParameters());
        assertTrue(ready.isSuccess(), ready.getText());
        assertTrue(ready.getText().contains("assume_isolated = 'esm'"), ready.getText());
        assertTrue(ready.getText().contains("esm_bc          = 'bc1'"), ready.getText());
        assertTrue(ready.getText().contains("-2.00000e-01"),
                "esm_w verbatim: " + ready.getText());
        assertTrue(ready.getText().contains("Keyword verdict: READY"), ready.getText());
        assertTrue(ready.getText().contains("Geometry gate: PASS"), ready.getText());
        assertTrue(ready.getText().contains("vacuum gap = 24.000000"), ready.getText());
        assertTrue(ready.getText().contains("Overall ESM readiness: YES"), ready.getText());
        assertTrue(ready.getText().contains("STATIC audit"), ready.getText());
        assertTrue(ready.getCsvLines().contains("vacuum_gap_ang,24.00000000,geometry"),
                ready.getCsvLines().toString());
        assertTrue(ready.getCsvLines().contains("verdict,READY,keyword-audit"),
                ready.getCsvLines().toString());

        QESCFInput periodic = new QESCFInput();
        periodic.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("assume_isolated", "'esm'"));
        AnalysisReport pbc = ResultAnalysisService.analyze(AnalysisKind.ESM_SLAB_CHECK,
                stubProjectWithInput(this.tempDir, periodic, cell), new AnalysisParameters());
        assertTrue(pbc.isSuccess(), pbc.getText());
        assertTrue(pbc.getText().contains("ESM ACTIVE BUT PERIODIC"), pbc.getText());
        assertTrue(pbc.getText().contains("(unset - QE default 'pbc')"), pbc.getText());
        assertTrue(pbc.getText().contains("Overall ESM readiness: NO"), pbc.getText());

        QESCFInput plain = new QESCFInput();
        AnalysisReport notEsm = ResultAnalysisService.analyze(AnalysisKind.ESM_SLAB_CHECK,
                stubProjectWithInput(this.tempDir, plain, cell), new AnalysisParameters());
        assertTrue(notEsm.isSuccess(), notEsm.getText());
        assertTrue(notEsm.getText().contains("Keyword verdict: NOT ESM"), notEsm.getText());

        double[][] skewed = {{10.0, 0.0, 0.5}, {0.0, 10.0, 0.0}, {0.0, 0.0, 30.0}};
        Cell skewCell = new Cell(skewed);
        skewCell.addAtom("Cu", 0.5, 0.5, 0.5);
        AnalysisReport skewReport = ResultAnalysisService.analyze(
                AnalysisKind.ESM_SLAB_CHECK, stubProjectWithInput(this.tempDir, input,
                        skewCell), new AnalysisParameters());
        assertTrue(skewReport.isSuccess(), skewReport.getText());
        assertTrue(skewReport.getText().contains("Geometry gate: FAIL"),
                skewReport.getText());
        assertTrue(skewReport.getText().contains("Overall ESM readiness: NO"),
                skewReport.getText());

        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.ESM_SLAB_CHECK,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(missing.isSuccess(), "No current input must fail closed");
    }

    @Test
    void testMoireTwistPreviewKindExactMathAndRefusals() {
        double[][] graphite = {{2.46, 0.0, 0.0}, {0.0, 2.46, 0.0}, {0.0, 0.0, 10.0}};
        Cell cell = new Cell(graphite);
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.MOIRE_TWIST_PREVIEW, stubProject(this.tempDir, cell),
                new AnalysisParameters().withMoireIndices(2, 1).withLatticeRatio(1.0));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Commensurate pair: (m, n) = (2, 1)"),
                report.getText());
        assertTrue(report.getText().contains("CSL index: Sigma = 7"), report.getText());
        assertTrue(report.getText().contains("cos(theta) = 13/14 EXACTLY"),
                report.getText());
        assertTrue(report.getText().contains("theta = 21.786789 degrees"),
                report.getText());
        assertTrue(report.getText().contains("L = 2.645751 * a1"), report.getText());
        assertTrue(report.getText().contains("= 0.0000% (identical lattices"),
                report.getText());
        assertTrue(report.getText().contains("honeycomb bilayer: 4 x 7 = 28 atoms"),
                report.getText());
        assertTrue(report.getText().contains("in-plane |a1| = 2.460000")
                        && report.getText().contains("L ~= 6.5085 Ang"),
                "live-cell context: " + report.getText());
        assertTrue(report.getCsvLines().contains("sigma,7,int"),
                report.getCsvLines().toString());
        assertTrue(report.getCsvLines().contains("moire_period_over_a1,2.64575131,1"),
                report.getCsvLines().toString());

        AnalysisReport mismatch = ResultAnalysisService.analyze(
                AnalysisKind.MOIRE_TWIST_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withMoireIndices(2, 1).withLatticeRatio(0.98));
        assertTrue(mismatch.isSuccess(), mismatch.getText());
        assertTrue(mismatch.getText().contains("= 2.0408% (WITHOUT it"),
                mismatch.getText());
        assertTrue(mismatch.getText().contains("L = 2.615427 * a1"), mismatch.getText());

        AnalysisReport duplicate = ResultAnalysisService.analyze(
                AnalysisKind.MOIRE_TWIST_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withMoireIndices(6, 3).withLatticeRatio(1.0));
        assertTrue(duplicate.isSuccess(), duplicate.getText());
        assertTrue(duplicate.getText().contains("common factor 3"), duplicate.getText());

        AnalysisReport untwisted = ResultAnalysisService.analyze(
                AnalysisKind.MOIRE_TWIST_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withMoireIndices(1, 1).withLatticeRatio(1.0));
        assertFalse(untwisted.isSuccess(),
                "theta = 0 on identical lattices is the primitive cell, no moire");
        assertTrue(untwisted.getText().contains("[MOIRE_RATIO]"), untwisted.getText());

        AnalysisReport outOfRange = ResultAnalysisService.analyze(
                AnalysisKind.MOIRE_TWIST_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withMoireIndices(200, 1).withLatticeRatio(1.0));
        assertFalse(outOfRange.isSuccess());
        assertTrue(outOfRange.getText().contains("[MOIRE_BOUNDS]"),
                outOfRange.getText());
    }

    @Test
    void testPdbReviewKindHonestCompositionAndRefusals() throws IOException {
        File pdb = write("ala.pdb",
                "HEADER    small alanine example\n"
                + "CRYST1   30.000   40.000   50.000  90.00  90.00  90.00\n"
                + "ATOM      1  N   ALA A   1      11.104  13.207  10.325  1.00 20.00           N\n"
                + "ATOM      2  CA  ALA A   1      12.104  13.500  10.500  0.50 20.00           C\n"
                + "HETATM  101  O   HOH A 101      20.000  20.000  20.000  1.00 30.00\n"
                + "END\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.PDB_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "ala", "ala.log", pdb,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("3 atoms (2 ATOM + 1 HETATM)"),
                report.getText());
        assertTrue(report.getText().contains("N=1,C=1"), report.getText());
        assertTrue(report.getText().contains("Atoms WITHOUT an element column: 1"),
                report.getText());
        assertTrue(report.getText().contains("Partial-occupancy atoms (0 < occ < 1): 1"),
                report.getText());
        assertTrue(report.getText().contains("cell volume 60000.0000 Ang^3"),
                report.getText());
        assertTrue(report.getText().contains("REVIEW only"), report.getText());
        assertTrue(report.getText().contains("not trusted as a DFT cell")
                        || report.getText().contains("not\ntrusted"),
                report.getText());
        assertEquals(4, report.getCsvLines().size(), "header + 3 atoms");
        assertEquals("3,HETATM,O,HOH,,20.0000,20.0000,20.0000,1.00",
                report.getCsvLines().get(3));

        File multimodel = write("multi.pdb",
                "MODEL        1\n"
                + "ATOM      1  N   ALA A   1       1.0 1.0 1.0  1.00 20.00           N\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.PDB_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "m", "m.log", multimodel,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "multi-model files refuse, never silently split");
        assertTrue(refused.getText().contains("[PDB_MODEL]"), refused.getText());

        File shortLine = write("short.pdb",
                "ATOM      1  N   ALA A   1       1.0 1.0 1.0  1.00\n");
        AnalysisReport shortRefused = ResultAnalysisService.analyze(AnalysisKind.PDB_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "s", "s.log", shortLine,
                new AnalysisParameters());
        assertFalse(shortRefused.isSuccess());
        assertTrue(shortRefused.getText().contains("[PDB_SYNTAX]"), shortRefused.getText());
    }

    @Test
    void testLammpsDataReviewKindStyleGateAndRefusals() throws IOException {
        File charge = write("charge.data",
                "silica charge example\n"
                + "4 atoms\n"
                + "1 atom types\n"
                + "0.0 10.0 xlo xhi\n"
                + "0.0 10.0 ylo yhi\n"
                + "0.0 10.0 zlo zhi\n"
                + "Masses\n"
                + "1 28.0855\n"
                + "Atoms # charge\n"
                + "1 1 0.5 1.0 1.0 1.0\n"
                + "2 1 -0.5 2.0 2.0 2.0\n"
                + "3 1 0.5 3.0 3.0 3.0\n"
                + "4 1 -0.5 4.0 4.0 4.0\n"
                + "Velocities\n"
                + "1 0.0 0.0 0.0\n"
                + "2 0.0 0.0 0.0\n"
                + "3 0.0 0.0 0.0\n"
                + "4 0.0 0.0 0.0\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.LAMMPS_DATA_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", charge,
                new AnalysisParameters().withSeriesKeyword("CHARGE"));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("(title: 'silica charge example')"),
                report.getText());
        assertTrue(report.getText().contains("atom_style used: CHARGE"), report.getText());
        assertTrue(report.getText().contains("Atoms: 4 (matches the declared header"),
                report.getText());
        assertTrue(report.getText().contains("Masses (VERBATIM, per type; NO element "
                + "inference): 1=28.0855"), report.getText());
        assertTrue(report.getText().contains("Total charge (summed from the rows): 0"),
                report.getText());
        assertTrue(report.getText().contains("Atoms outside the box: 0"), report.getText());
        assertTrue(report.getText().contains("skipped by name (counted, not "
                + "interpreted): [Velocities]"), report.getText());
        assertTrue(report.getText().contains("REVIEW only"), report.getText());
        assertEquals(5, report.getCsvLines().size(), "header + 4 rows");
        assertEquals("2,-1,1,-0.5,2.000000,2.000000,2.000000", report.getCsvLines().get(2));

        AnalysisReport wrongStyle = ResultAnalysisService.analyze(
                AnalysisKind.LAMMPS_DATA_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", charge,
                new AnalysisParameters().withSeriesKeyword("atomic"));
        assertFalse(wrongStyle.isSuccess(),
                "6-column rows under ATOMIC must fail - the style is user's truth");
        assertTrue(wrongStyle.getText().contains("[LAMMPS_STYLE]"), wrongStyle.getText());

        AnalysisReport unknownStyle = ResultAnalysisService.analyze(
                AnalysisKind.LAMMPS_DATA_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "si", "si.log", charge,
                new AnalysisParameters().withSeriesKeyword("hybrid"));
        assertFalse(unknownStyle.isSuccess(),
                "styles outside the covered set fail closed, listed");
        assertTrue(unknownStyle.getText().contains("MOLECULAR"), unknownStyle.getText());
    }

    @Test
    void testCpInputDraftKindInvalidPatternAndGuardedDraft() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Ni", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'nio'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "'cp'"));

        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CP_INPUT_DRAFT,
                stubProjectWithInput(this.tempDir, input, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("prefix = 'nio'"), report.getText());
        assertTrue(report.getText().contains("calculation = 'cp'"), report.getText());
        assertTrue(report.getText().contains("WARNING: calculation='cp' is NOT a valid "
                + "pw.x calculation"), report.getText());
        assertTrue(report.getText().contains("DELIBERATELY NOT RUNNABLE"),
                report.getText());
        String draft = report.getGeneratedInput();
        assertTrue(draft != null, "the draft travels the explicit-save channel only");
        assertTrue(draft.contains("required-edit") || draft.contains("REQUIRED-EDIT"),
                draft);
        assertTrue(draft.contains("prefix         = 'nio',"), draft);
        assertTrue(draft.startsWith("! cp.x Car-Parrinello input DRAFT"), draft);
        assertTrue(report.getCsvLines().contains("invalid_pw_cp,true,audit"),
                report.getCsvLines().toString());
        assertTrue(report.getCsvLines().contains(
                "required_edit_placeholders,8,draft-guard"),
                report.getCsvLines().toString());

        QESCFInput scf = new QESCFInput();
        scf.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "'scf'"));
        AnalysisReport plain = ResultAnalysisService.analyze(AnalysisKind.CP_INPUT_DRAFT,
                stubProjectWithInput(this.tempDir, scf, cell), new AnalysisParameters());
        assertTrue(plain.isSuccess(), plain.getText());
        assertTrue(!plain.getText().contains("WARNING: calculation='cp'"),
                "a scf input carries no invalid-pattern warning");
        assertTrue(plain.getCsvLines().contains("invalid_pw_cp,false,audit"),
                plain.getCsvLines().toString());

        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.CP_INPUT_DRAFT,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(missing.isSuccess(), "No current input must fail closed");
        assertTrue(missing.getText().contains("[CP_INPUT]"), missing.getText());
    }

    @Test
    void testW90WinDraftKindMeshEchoAndRefusals() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(5.43));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        QEKPoints points = input.getCard(QEKPoints.class);
        points.setAutomatic();
        points.setKGrid(new int[] {4, 4, 4});
        points.setKOffset(new int[] {0, 0, 0});
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("nbnd", "64"));

        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.W90_WIN_DRAFT,
                stubProjectWithInput(this.tempDir, input, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "Mesh echoed verbatim: automatic 4 x 4 x 4, offset 0 0 0"),
                report.getText());
        assertTrue(report.getText().contains("echoed as num_bands = 64"),
                report.getText());
        assertTrue(report.getText().contains("kpoints block: GENERATED"),
                report.getText());
        assertTrue(report.getText().contains("nosym=.true./noinv"), report.getText());
        String draft = report.getGeneratedInput();
        assertTrue(draft != null && draft.contains("mp_grid = 4 4 4"), draft);
        assertTrue(draft.contains("begin kpoints"), draft);
        assertTrue(report.getCsvLines().contains("mp_grid,4x4x4,verbatim"),
                report.getCsvLines().toString());
        assertTrue(report.getCsvLines().contains("num_bands,64,echo-or-required"),
                report.getCsvLines().toString());

        QESCFInput gamma = new QESCFInput();
        gamma.getCard(QEKPoints.class).setGamma();
        AnalysisReport gammaRefused = ResultAnalysisService.analyze(
                AnalysisKind.W90_WIN_DRAFT, stubProjectWithInput(this.tempDir, gamma, cell),
                new AnalysisParameters());
        assertFalse(gammaRefused.isSuccess(),
                "Gamma-only cannot feed a Wannierization - refused");
        assertTrue(gammaRefused.getText().contains("[W90_MESH]"), gammaRefused.getText());

        AnalysisReport missing = ResultAnalysisService.analyze(AnalysisKind.W90_WIN_DRAFT,
                stubProject(this.tempDir), new AnalysisParameters());
        assertFalse(missing.isSuccess(), "No current input must fail closed");
        assertTrue(missing.getText().contains("[W90_INPUT]"), missing.getText());
    }

    @Test
    void testGbCslPreviewKindExactPinsAndRefusals() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.GB_CSL_PREVIEW,
                stubProject(this.tempDir),
                new AnalysisParameters().withCslAxis(0, 0, 1).withMoireIndices(3, 1));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Rotation axis [0 0 1]"), report.getText());
        assertTrue(report.getText().contains("Commensurate pair (m, n) = (3, 1)"),
                report.getText());
        assertTrue(report.getText().contains("Axis norm N = u^2+v^2+w^2 = 1"),
                report.getText());
        assertTrue(report.getText().contains("Exact cosine fraction: cos(theta) = 4/5"),
                report.getText());
        assertTrue(report.getText().contains("Rotation angle: 36.869898 deg"),
                report.getText());
        assertTrue(report.getText().contains(
                "CSL Sigma = 5  (odd part of m^2 + N n^2 = 10; divided by 2 1 time)"),
                report.getText());
        assertFalse(report.getText().contains("LATTICE SYMMETRY"), report.getText());
        assertTrue(report.getText().contains("SIMPLE CUBIC"), report.getText());
        assertTrue(report.getText().contains("boundary plane (hkl)"), report.getText());
        assertEquals(13, report.getCsvLines().size(), "header + 12 rows");
        assertTrue(report.getCsvLines().contains("sigma,5,exact"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("cos_theta,4/5,exact-reduced"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("angle_deg,36.869898,computed-from-exact"),
                String.join("\n", report.getCsvLines()));

        AnalysisReport symmetry = ResultAnalysisService.analyze(
                AnalysisKind.GB_CSL_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withCslAxis(0, 0, 1).withMoireIndices(1, 1));
        assertTrue(symmetry.isSuccess(), symmetry.getText());
        assertTrue(symmetry.getText().contains("LATTICE SYMMETRY operation"),
                symmetry.getText());
        assertTrue(symmetry.getCsvLines().contains("lattice_symmetry,true,sigma==1"),
                String.join("\n", symmetry.getCsvLines()));

        AnalysisReport normalized = ResultAnalysisService.analyze(
                AnalysisKind.GB_CSL_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withCslAxis(0, 2, 2).withMoireIndices(3, 1));
        assertTrue(normalized.isSuccess(), normalized.getText());
        assertTrue(normalized.getText().contains(
                "(normalized: a common factor 2 was removed from the supplied axis)"),
                normalized.getText());
        assertTrue(normalized.getText().contains("CSL Sigma = 11"), normalized.getText());

        AnalysisReport zeroAxis = ResultAnalysisService.analyze(
                AnalysisKind.GB_CSL_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withCslAxis(0, 0, 0).withMoireIndices(3, 1));
        assertFalse(zeroAxis.isSuccess(), "a zero axis must fail closed");
        assertTrue(zeroAxis.getText().contains("[CSL_VECTOR]"), zeroAxis.getText());

        AnalysisReport bounds = ResultAnalysisService.analyze(
                AnalysisKind.GB_CSL_PREVIEW, stubProject(this.tempDir),
                new AnalysisParameters().withCslAxis(0, 0, 1).withMoireIndices(3, 1025));
        assertFalse(bounds.isSuccess(), "out-of-range indices must fail closed");
        assertTrue(bounds.getText().contains("[CSL_BOUNDS]"), bounds.getText());
    }


    @Test
    void testQeVersionCheckKindAuditVerdictsAndRefusals() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("wf_collect", ".true."));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "'cp'"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("smearing", "'cold'"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("input_dft", "'PBE'"));

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.QE_VERSION_CHECK, stubProjectWithInput(this.tempDir, input,
                        cell), new AnalysisParameters().withSeriesKeyword("7.4"));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Requested version filter: 7.4"),
                report.getText());
        assertTrue(report.getText().contains("REMOVED_KEYWORD"),
                "wf_collect must be the flagged removal: " + report.getText());
        assertTrue(report.getText().contains("VALUE_WARNING"),
                "calculation='cp' must be the flagged value: " + report.getText());
        assertTrue(report.getText().contains("NOT_IN_CURATED"),
                "input_dft is outside the curated slice: " + report.getText());
        assertTrue(report.getText().contains("delete it"), report.getText());
        assertTrue(report.getText().contains("cp.x"), report.getText());
        assertTrue(report.getText().contains("Audited "), report.getText());
        assertTrue(report.getText().contains("not-in-curated."), report.getText());
        assertTrue(report.getText().contains("QE 7.2-7.5"), report.getText());
        assertTrue(report.getCsvLines().stream().anyMatch(line ->
                        line.startsWith("CONTROL,calculation,'cp',VALUE_WARNING")),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().stream().anyMatch(line ->
                        line.contains("wf_collect") && line.contains("REMOVED_KEYWORD")),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().stream().anyMatch(line ->
                        line.startsWith("SYSTEM,smearing,'cold',OK")),
                "cold is the marzari-vanderbilt alias - OK: "
                        + String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().stream().anyMatch(line ->
                        line.contains("input_dft") && line.contains("NOT_IN_CURATED")),
                String.join("\n", report.getCsvLines()));

        AnalysisReport uniform = ResultAnalysisService.analyze(
                AnalysisKind.QE_VERSION_CHECK, stubProjectWithInput(this.tempDir, input,
                        cell), new AnalysisParameters().withSeriesKeyword(""));
        assertTrue(uniform.isSuccess(), uniform.getText());
        assertTrue(uniform.getText().contains(
                "(none - auditing against the uniform 7.2-7.5 window)"),
                uniform.getText());

        AnalysisReport unsupported = ResultAnalysisService.analyze(
                AnalysisKind.QE_VERSION_CHECK, stubProjectWithInput(this.tempDir, input,
                        cell), new AnalysisParameters().withSeriesKeyword("6.8"));
        assertFalse(unsupported.isSuccess(), "outside the window must fail closed");
        assertTrue(unsupported.getText().contains("[VERSION_UNSUPPORTED]"),
                unsupported.getText());

        AnalysisReport missing = ResultAnalysisService.analyze(
                AnalysisKind.QE_VERSION_CHECK, stubProject(this.tempDir),
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "no current input must fail closed");
    }


    @Test
    void testMpiPoolsAdvisorKindExactAuditAndRefusals() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Si", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        QEKPoints points = input.getCard(QEKPoints.class);
        points.setAutomatic();
        points.setKGrid(new int[] {4, 4, 4});
        points.setKOffset(new int[] {0, 0, 0});

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.MPI_POOLS_ADVISOR, stubProjectWithInput(this.tempDir, input,
                        cell), new AnalysisParameters().withTotalRanks(24)
                        .withCurrentPools(8));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "Exact uniform mesh: 4 x 4 x 4 = 64 k points"), report.getText());
        assertTrue(report.getText().contains("Total MPI ranks R = 24"), report.getText());
        assertTrue(report.getText().contains(
                "Pool divisors of N (the rigorous window): 1, 2, 4, 8, 16, 32, 64"),
                report.getText());
        assertTrue(report.getText().contains(
                "Admissible pools (divide N AND R, p <= R): 1, 2, 4, 8"), report.getText());
        assertTrue(report.getText().contains(
                "Recommendation: -nk 8  (8 pools of 3 rank(s) each)"), report.getText());
        assertTrue(report.getText().contains(
                "Audit of the supplied -nk 8: VERIFIED VALID"), report.getText());
        assertTrue(report.getText().contains("IRREDUCIBLE"), report.getText());
        assertTrue(report.getText().contains("RESOURCE_ESTIMATE"), report.getText());
        assertTrue(report.getCsvLines().contains("mesh,4x4x4,verbatim-automatic"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("admissible,1;2;4;8,divide-N-and-R"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("recommended,8,largest-admissible"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("ranks_per_pool,3,exact"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("current_valid,true,audit"),
                String.join("\n", report.getCsvLines()));

        AnalysisReport invalid = ResultAnalysisService.analyze(
                AnalysisKind.MPI_POOLS_ADVISOR, stubProjectWithInput(this.tempDir, input,
                        cell), new AnalysisParameters().withTotalRanks(24)
                        .withCurrentPools(5));
        assertTrue(invalid.isSuccess(), invalid.getText());
        assertTrue(invalid.getText().contains(
                "Audit of the supplied -nk 5: INVALID"), invalid.getText());
        assertTrue(invalid.getCsvLines().contains("current_valid,false,audit"),
                String.join("\n", invalid.getCsvLines()));

        AnalysisReport oversubscribed = ResultAnalysisService.analyze(
                AnalysisKind.MPI_POOLS_ADVISOR, stubProjectWithInput(this.tempDir, input,
                        cell), new AnalysisParameters().withTotalRanks(96));
        assertTrue(oversubscribed.isSuccess(), oversubscribed.getText());
        assertTrue(oversubscribed.getText().contains("Recommendation: -nk 32"),
                "64 % 96 != 0 trims everything above 32: " + oversubscribed.getText());

        QESCFInput gamma = new QESCFInput();
        gamma.getCard(QEKPoints.class).setGamma();
        AnalysisReport gammaRefused = ResultAnalysisService.analyze(
                AnalysisKind.MPI_POOLS_ADVISOR, stubProjectWithInput(this.tempDir, gamma,
                        cell), new AnalysisParameters().withTotalRanks(8));
        assertFalse(gammaRefused.isSuccess(), "Gamma-only must fail closed");
        assertTrue(gammaRefused.getText().contains("[POOL_MESH]"),
                gammaRefused.getText());

        AnalysisReport missing = ResultAnalysisService.analyze(
                AnalysisKind.MPI_POOLS_ADVISOR, stubProject(this.tempDir),
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "no current input must fail closed");
        assertTrue(missing.getText().contains("[POOL_INPUT]"), missing.getText());
    }


    @Test
    void testUnitConvertKindConversionsAndRefusals() {
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.UNIT_CONVERT,
                stubProject(this.tempDir),
                new AnalysisParameters().withUnitConversion(1.0, "ha", "ev"));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("1.0 Ha = 27.211386245988 eV"),
                report.getText());
        assertTrue(report.getText().contains("CODATA-2018"), report.getText());
        assertFalse(report.getText().contains("Spectroscopic bridge crossed"),
                report.getText());
        assertTrue(report.getCsvLines().contains("value_to,27.211386245988,converted"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("spectroscopic_bridge,false,SI-exact-hc"),
                String.join("\n", report.getCsvLines()));

        AnalysisReport bridge = ResultAnalysisService.analyze(AnalysisKind.UNIT_CONVERT,
                stubProject(this.tempDir),
                new AnalysisParameters().withUnitConversion(300.0, "cm-1", "mev"));
        assertTrue(bridge.isSuccess(), bridge.getText());
        assertTrue(bridge.getText().contains("300.0 cm^-1 = 37.195259529960076 meV"),
                bridge.getText());
        assertTrue(bridge.getText().contains("Spectroscopic bridge crossed"),
                bridge.getText());

        AnalysisReport unknown = ResultAnalysisService.analyze(AnalysisKind.UNIT_CONVERT,
                stubProject(this.tempDir),
                new AnalysisParameters().withUnitConversion(1.0, "furlong", "ev"));
        assertFalse(unknown.isSuccess(), "unknown tokens must fail closed");
        assertTrue(unknown.getText().contains("[UNIT_UNKNOWN]"), unknown.getText());

        AnalysisReport domain = ResultAnalysisService.analyze(AnalysisKind.UNIT_CONVERT,
                stubProject(this.tempDir),
                new AnalysisParameters().withUnitConversion(1.0, "ev", "bohr"));
        assertFalse(domain.isSuccess(), "incompatible domains must fail closed");
        assertTrue(domain.getText().contains("[UNIT_DOMAIN]"), domain.getText());
    }


    @Test
    void testLogErrorDiagnosisKindMatchesAndHonestEmpty() throws IOException {
        File log = write("pw.log",
                "Program PWSCF v.7.2 starts ...\n"
                + "     io routine, version 7.2\n"
                + "convergence NOT achieved after 100 iterations: stopping\n"
                + "convergence NOT achieved after 100 iterations: stopping\n"
                + "convergence NOT achieved after 100 iterations: stopping\n"
                + "convergence NOT achieved after 100 iterations: stopping\n"
                + "\n"
                + "     Error in routine electrons (2):\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.LOG_ERROR_DIAGNOSIS, new ProjectProperty(),
                this.tempDir.toFile(), "metallic", log.getName(), null,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Scanned 8 lines"), report.getText());
        assertTrue(report.getText().contains("[scf-not-converged] ERROR - line 3"),
                report.getText());
        assertTrue(report.getText().contains("electron_maxstep"), report.getText());
        assertTrue(report.getText().contains("mixing_beta"), report.getText());
        assertTrue(report.getText().contains(
                "scf-not-converged: 4 match(es), 3 verbatim quote(s) kept "
                        + "(1 repeats suppressed - counted, not hidden)"),
                report.getText());
        assertTrue(report.getText().contains("Matched signatures (2 distinct)"),
                report.getText());
        assertTrue(report.getText().contains("[generic-error-routine] ERROR - line 8"),
                report.getText());
        assertTrue(report.getText().contains("user_guide"), report.getText());
        assertTrue(report.getCsvLines().stream().anyMatch(line ->
                        line.startsWith("scf-not-converged,ERROR,3,")),
                String.join("\n", report.getCsvLines()));

        File crash = write("CRASH",
                " %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n"
                + "     Error in routine read_namelists (1):\n"
                + "     bad line in namelist &system: \"nospin=2\" (error could be in the "
                        + "input)\n");
        AnalysisReport crashReport = ResultAnalysisService.analyze(
                AnalysisKind.LOG_ERROR_DIAGNOSIS, new ProjectProperty(),
                this.tempDir.toFile(), " metallic", log.getName(), crash,
                new AnalysisParameters());
        assertTrue(crashReport.isSuccess(), crashReport.getText());
        assertTrue(crashReport.getText().contains(
                "[generic-error-routine] ERROR - line 2"), crashReport.getText());

        File clean = write("clean.out", "Program PWSCF\njob done.\n");
        AnalysisReport empty = ResultAnalysisService.analyze(
                AnalysisKind.LOG_ERROR_DIAGNOSIS, new ProjectProperty(),
                this.tempDir.toFile(), "insulator", log.getName(), clean,
                new AnalysisParameters());
        assertTrue(empty.isSuccess(), empty.getText());
        assertTrue(empty.getText().contains("No curated signature matched"),
                empty.getText());
        assertTrue(empty.getText().contains("NOT proof the run is healthy"),
                empty.getText());
    }


    @Test
    void testXspectraDraftKindContextAndGuard() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("Fe", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'xanes_feo'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'./work'"));

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.XSPECTRA_INPUT_DRAFT, stubProjectWithInput(this.tempDir,
                        input, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "save context echoed from the live input"), report.getText());
        assertTrue(report.getText().contains("prefix = 'xanes_feo'"), report.getText());
        assertTrue(report.getText().contains("outdir = './work'"), report.getText());
        assertTrue(report.getText().contains(
                "DELIBERATELY NOT RUNNABLE: 8 REQUIRED-EDIT placeholders"),
                report.getText());
        assertTrue(report.getText().contains("core-hole pseudopotential"),
                report.getText());
        assertTrue(report.getGeneratedInput() != null
                        && report.getGeneratedInput().contains("&input_xspectra"),
                "the draft travels the generated-input channel");
        assertTrue(report.getGeneratedInput().contains("xanes_dipole"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("filecore"),
                report.getGeneratedInput());
        assertTrue(report.getCsvLines().contains(
                "required_edit_placeholders,8,draft-guard"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("prefix,xanes_feo,verbatim"),
                String.join("\n", report.getCsvLines()));

        AnalysisReport missing = ResultAnalysisService.analyze(
                AnalysisKind.XSPECTRA_INPUT_DRAFT, stubProject(this.tempDir),
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "no current input must fail closed");
        assertTrue(missing.getText().contains("[XSPEC_INPUT]"), missing.getText());
    }


    @Test
    void testGipawDraftKindContextAndGuards() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("C", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'nmr_glucose'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'./nmr_work'"));

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.GIPAW_INPUT_DRAFT, stubProjectWithInput(this.tempDir,
                        input, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("prefix = 'nmr_glucose'"), report.getText());
        assertTrue(report.getText().contains("tmp_dir (outdir) = './nmr_work'"),
                report.getText());
        assertTrue(report.getText().contains("MINIMAL"), report.getText());
        assertTrue(report.getText().contains("CHEMICAL"), report.getText());
        assertTrue(report.getText().contains("GIPAW-capable pseudopotentials"),
                report.getText());
        assertTrue(report.getGeneratedInput() != null
                        && report.getGeneratedInput().contains("&inputgipaw"),
                "the draft travels the generated-input channel");
        assertTrue(report.getGeneratedInput().contains("job          = 'gipaw'"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("q_gipaw      = ..."),
                report.getGeneratedInput());
        assertTrue(report.getCsvLines().contains(
                "required_edit_placeholders,4,draft-guard"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("prefix,nmr_glucose,verbatim"),
                String.join("\n", report.getCsvLines()));

        AnalysisReport missing = ResultAnalysisService.analyze(
                AnalysisKind.GIPAW_INPUT_DRAFT, stubProject(this.tempDir),
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "no current input must fail closed");
        assertTrue(missing.getText().contains("[GIPAW_INPUT]"), missing.getText());
    }


    @Test
    void testSlabMillerPreviewKindGeometryAndGate() {
        Cell cubic = new Cell(quantumforge.com.math.Matrix3D.unit(5.43));
        cubic.addAtom("Si", 0.0, 0.0, 0.0);

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.SLAB_MILLER_PREVIEW, stubProject(this.tempDir, cubic),
                new AnalysisParameters().withMillerIndices(1, 1, 0));
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Requested plane: (1 1 0)"),
                report.getText());
        assertTrue(report.getText().contains(
                "d-spacing = 3.839590 Ang"), report.getText());
        assertTrue(report.getText().contains("|G(1 1 0)| = 1.636421 1/Ang"),
                report.getText());
        assertTrue(report.getText().contains("Surface normal (Cartesian): (0.707107, "
                + "0.707107, 0.000000)"), report.getText());
        assertTrue(report.getText().contains("ESM z-gate: NOT along +z"),
                report.getText());
        assertTrue(report.getCsvLines().contains("plane,(1 1 0),normalized"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().stream().anyMatch(line ->
                        line.startsWith("d_spacing_ang,3.839590")),
                String.join("\n", report.getCsvLines()));

        AnalysisReport aligned = ResultAnalysisService.analyze(
                AnalysisKind.SLAB_MILLER_PREVIEW, stubProject(this.tempDir, cubic),
                new AnalysisParameters().withMillerIndices(0, 0, 2));
        assertTrue(aligned.isSuccess(), aligned.getText());
        assertTrue(aligned.getText().contains("(normalized: common factor 2 removed; "
                + "the SAME family is (0 0 1))"), aligned.getText());
        assertTrue(aligned.getText().contains("ESM z-gate: ALIGNED"), aligned.getText());
        assertTrue(aligned.getCsvLines().contains("plane,(0 0 1),normalized"),
                String.join("\n", aligned.getCsvLines()));

        AnalysisReport zero = ResultAnalysisService.analyze(
                AnalysisKind.SLAB_MILLER_PREVIEW, stubProject(this.tempDir, cubic),
                new AnalysisParameters().withMillerIndices(0, 0, 0));
        assertFalse(zero.isSuccess(), "(0 0 0) must fail closed");
        assertTrue(zero.getText().contains("[SLAB_VECTOR]"), zero.getText());

        AnalysisReport bounds = ResultAnalysisService.analyze(
                AnalysisKind.SLAB_MILLER_PREVIEW, stubProject(this.tempDir, cubic),
                new AnalysisParameters().withMillerIndices(0, 0, 17));
        assertFalse(bounds.isSuccess(), "out-of-range indices must fail closed");
        assertTrue(bounds.getText().contains("[SLAB_BOUNDS]"), bounds.getText());
    }


    @Test
    void testCifReviewKindSubsetMetricsAndRefusals() throws IOException {
        File rutile = write("rutile.cif",
                "data_rutile_TiO2\n"
                + "_cell_length_a 4.593\n"
                + "_cell_length_b 4.593\n"
                + "_cell_length_c 2.959\n"
                + "_cell_angle_alpha 90\n"
                + "_cell_angle_beta 90\n"
                + "_cell_angle_gamma 90\n"
                + "_chemical_formula_sum 'Ti2 O4'\n"
                + "_symmetry_space_group_name_H-M 'P 42/m n m'\n"
                + "loop_\n_atom_site_label\n_atom_site_type_symbol\n"
                + "_atom_site_fract_x\n_atom_site_fract_y\n_atom_site_fract_z\n"
                + "Ti1 Ti 0.0 0.0 0.0\n"
                + "O1 O 0.305(2) 0.305(2) 0.0\n"
                + "O2 O 0.5 0.0 0.5\n");
        AnalysisReport report = ResultAnalysisService.analyze(AnalysisKind.CIF_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "rutile", "run.log",
                rutile, new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("data_ block: rutile_TiO2"),
                report.getText());
        assertTrue(report.getText().contains("volume = 62.4220 Ang^3"), report.getText());
        assertTrue(report.getText().contains(
                "Composition (from the type_symbol column ONLY): O=2,Ti=1"),
                report.getText());
        assertTrue(report.getText().contains("ASYMMETRIC ONLY"), report.getText());
        assertTrue(report.getText().contains("REVIEW only"), report.getText());
        assertTrue(report.getCsvLines().contains(
                "Ti1,Ti,0.000000,0.000000,0.000000,1.0000,false"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains(
                "O1,O,0.305000,0.305000,0.000000,1.0000,true"),
                String.join("\n", report.getCsvLines()));

        File anonymous = write("anon.cif",
                "data_an\n"
                + "_cell_length_a 10\n_cell_length_b 10\n_cell_length_c 10\n"
                + "_cell_angle_alpha 90\n_cell_angle_beta 90\n_cell_angle_gamma 90\n"
                + "loop_\n_atom_site_label\n"
                + "_atom_site_fract_x\n_atom_site_fract_y\n_atom_site_fract_z\n"
                + "FE1 0.0 0.0 0.0\nCA1 0.5 0.5 0.5\n");
        AnalysisReport anon = ResultAnalysisService.analyze(AnalysisKind.CIF_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "an", "run.log", anonymous,
                new AnalysisParameters());
        assertTrue(anon.isSuccess(), anon.getText());
        assertTrue(anon.getText().contains(
                "Atoms anonymous (no type symbol; labels NEVER guessed): 2"),
                anon.getText());
        assertTrue(anon.getText().contains("(no type_symbol column present)"),
                anon.getText());

        File multi = write("two.cif", "data_one\n_cell_length_a 5\ndata_two\n");
        AnalysisReport refused = ResultAnalysisService.analyze(AnalysisKind.CIF_REVIEW,
                new ProjectProperty(), this.tempDir.toFile(), "two", "run.log", multi,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "multi-block must fail closed");
        assertTrue(refused.getText().contains("[CIF_MULTIBLOCK]"), refused.getText());
        assertFalse(refused.getText().contains("data_two-reviewed"), refused.getText());
    }

    @Test
    void testMolSdfReviewKindSubsetMetricsAndRefusals() throws IOException {
        File methane = write("methane.mol",
                "methane test\n"
                + "  QuantumForge\n"
                + "\n"
                + "  5  4  0  0  0  0  0  0  0  0  0  0    V2000\n"
                + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0\n"
                + "    0.6291    0.6291    0.6291 H   0  0  0  0  0  0\n"
                + "   -0.6291   -0.6291    0.6291 H   0  0  0  0  0  0\n"
                + "   -0.6291    0.6291   -0.6291 H   0  0  0  0  0  0\n"
                + "    0.6291   -0.6291   -0.6291 H   0  0  0  0  0  0\n"
                + "  1  2  1  0  0  0  0\n"
                + "  1  3  1  0  0  0  0\n"
                + "  1  4  1  0  0  0  0\n"
                + "  1  5  1  0  0  0  0\n"
                + "M  END\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.MOL_SDF_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "methane", "run.log", methane,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("Record title (verbatim line 1): "
                + "methane test"), report.getText());
        assertTrue(report.getText().contains(
                "Counts: 5 atom(s), 4 bond(s);  version marker 'V2000' present"),
                report.getText());
        assertTrue(report.getText().contains(
                "Composition (from the atom-block element column ONLY): C=1,H=4"),
                report.getText());
        assertTrue(report.getText().contains("type 1=4"), report.getText());
        assertTrue(report.getText().contains("NEVER guessed): 0"), report.getText());
        assertTrue(report.getText().contains("sum = +0"), report.getText());
        assertTrue(report.getText().contains("REVIEW only"), report.getText());
        assertTrue(report.getCsvLines().contains(
                "1,C,0.0000,0.0000,0.0000,false"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains(
                "2,H,0.6291,0.6291,0.6291,false"),
                String.join("\n", report.getCsvLines()));

        File query = write("query.sdf",
                "query record\n"
                + "  QF\n"
                + "\n"
                + "  3  0  0  0  0  0  0  0  0  0  0  0    V2000\n"
                + "    0.0000    0.0000    0.0000 Q   0  0  0  0  0  0\n"
                + "    1.0000    0.0000    0.0000 Na  0  0  0  0  0  0\n"
                + "    2.0000    0.0000    0.0000 Cl  0  0  0  0  0  0\n"
                + "M  CHG  2  2  1  3 -1\n"
                + "M  END\n");
        AnalysisReport pseudo = ResultAnalysisService.analyze(
                AnalysisKind.MOL_SDF_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "query", "run.log", query,
                new AnalysisParameters());
        assertTrue(pseudo.isSuccess(), pseudo.getText());
        assertTrue(pseudo.getText().contains("NEVER guessed): 1"), pseudo.getText());
        assertTrue(pseudo.getText().contains("Composition (from the atom-block "
                + "element column ONLY): Cl=1,Na=1"), pseudo.getText());
        assertTrue(pseudo.getText().contains("sum = +0"), pseudo.getText());
        assertTrue(pseudo.getCsvLines().contains("1,,0.0000,0.0000,0.0000,true"),
                String.join("\n", pseudo.getCsvLines()));

        File bundle = write("bundle.sdf", "mol one\n A\n\n"
                + "  1  0  0  0  0  0  0  0  0  0  0  0    V2000\n"
                + "    0.0000    0.0000    0.0000 C   0  0  0  0  0  0\n"
                + "M  END\n$$$$\nmol two\n");
        AnalysisReport refused = ResultAnalysisService.analyze(
                AnalysisKind.MOL_SDF_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "bundle", "run.log", bundle,
                new AnalysisParameters());
        assertFalse(refused.isSuccess(), "multi-record bundles must fail closed");
        assertTrue(refused.getText().contains("[SDF_MULTIRECORD]"),
                refused.getText());

        File v3000 = write("extended.mol", "mol\n A\n\n"
                + "  1  0  0  0  0  0  0  0  0  0  0  0    V3000\n");
        AnalysisReport v3 = ResultAnalysisService.analyze(
                AnalysisKind.MOL_SDF_REVIEW, new ProjectProperty(),
                this.tempDir.toFile(), "extended", "run.log", v3000,
                new AnalysisParameters());
        assertFalse(v3.isSuccess(), "V3000 must fail closed");
        assertTrue(v3.getText().contains("[SDF_V3000]"), v3.getText());
    }

    @Test
    void testTddfptDraftKindContextAndGuards() {
        Cell cell = new Cell(quantumforge.com.math.Matrix3D.unit(10.0));
        cell.addAtom("C", 0.0, 0.0, 0.0);
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("prefix", "'bnz_lrtddft'"));
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("outdir", "'./lr_work'"));

        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.TDDFPT_INPUT_DRAFT, stubProjectWithInput(this.tempDir,
                        input, cell), new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains("prefix = 'bnz_lrtddft'"),
                report.getText());
        assertTrue(report.getText().contains("outdir = './lr_work'"),
                report.getText());
        assertTrue(report.getText().contains("NON-RUNNABLE"), report.getText());
        assertTrue(report.getText().contains("LINEAR-RESPONSE"), report.getText());
        assertTrue(report.getText().contains("never labelled RT-TDDFT"),
                report.getText());
        assertTrue(report.getText().contains("CONVERGED pw.x SCF save"),
                report.getText());
        assertTrue(report.getText().contains("TDDFT_SPECTRUM"), report.getText());
        assertTrue(report.getGeneratedInput() != null
                        && report.getGeneratedInput().contains("&lr_input"),
                "the draft travels the generated-input channel");
        assertTrue(report.getGeneratedInput().contains("&lr_control"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("prefix   = 'bnz_lrtddft'"),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("num_init  = ..."),
                report.getGeneratedInput());
        assertTrue(report.getGeneratedInput().contains("charge_response  = ..."),
                report.getGeneratedInput());
        assertTrue(report.getCsvLines().contains(
                "required_edit_placeholders,5,draft-guard"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("prefix,bnz_lrtddft,verbatim"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains(
                "namelists,lr_input+lr_control,skeleton"),
                String.join("\n", report.getCsvLines()));

        AnalysisReport missing = ResultAnalysisService.analyze(
                AnalysisKind.TDDFPT_INPUT_DRAFT, stubProject(this.tempDir),
                new AnalysisParameters());
        assertFalse(missing.isSuccess(), "no current input must fail closed");
        assertTrue(missing.getText().contains("[TDDFPT_INPUT]"), missing.getText());
    }

    @Test
    void testMlDatasetBaselineKindFitAndRefusals() throws IOException {
        File exact = write("dataset.extxyz",
                "2\n"
                + "Properties=species:S:1:pos:R:3 energy=-3.0\n"
                + "H 0.0 0.0 0.0\nH 0.0 0.0 0.74\n"
                + "3\n"
                + "energy=-8.5\n"
                + "H 0.0 0.0 0.0\nO 0.757 0.0 0.0\nH 0.0 0.586 0.0\n"
                + "2\n"
                + "energy=-9.0D0\n"
                + "O 0.0 0.0 0.0\nO 0.0 0.0 1.21\n"
                + "4\n"
                + "free_energy=-13.0\n"
                + "H 0.0 0.0 0.0\nH 0.0 0.0 0.74\n"
                + "O 1.0 0.0 0.0\nO 1.0 0.0 1.21\n"
                + "5\n"
                + "energy=-12.0\n"
                + "H 0.0 0.0 0.0\nH 0.0 0.0 0.74\n"
                + "H 1.0 0.0 0.0\nH 1.0 0.0 0.74\nO 0.5 0.5 0.5\n");
        AnalysisReport report = ResultAnalysisService.analyze(
                AnalysisKind.ML_DATASET_BASELINE, new ProjectProperty(),
                this.tempDir.toFile(), "ds", "run.log", exact,
                new AnalysisParameters());
        assertTrue(report.isSuccess(), report.getText());
        assertTrue(report.getText().contains(
                "Frames: 5 used (energy-labeled); 0 EXCLUDED"), report.getText());
        assertTrue(report.getText().contains("intercept = 0.826086957 eV"),
                report.getText());
        assertTrue(report.getText().contains("c[H] = -1.989130435 eV/atom"),
                report.getText());
        assertTrue(report.getText().contains("c[O] = -4.956521739 eV/atom"),
                report.getText());
        assertTrue(report.getText().contains("residual RMS = 0.197814142 eV"),
                report.getText());
        assertTrue(report.getText().contains(
                "frame 2: residual -0.391304 eV (label -8.500000 eV vs fit "
                        + "-8.108696 eV)"), report.getText());
        assertTrue(report.getText().contains("SCREEN only"), report.getText());
        assertTrue(report.getCsvLines().contains(
                "intercept_ev,0.826086957,least-squares constant"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains(
                "H,-1.989130435,eV per atom (this-dataset fit)"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains(
                "O,-4.956521739,eV per atom (this-dataset fit)"),
                String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains("top_outlier_frame,2,-0.391304 eV "
                + "residual"), String.join("\n", report.getCsvLines()));
        assertTrue(report.getCsvLines().contains(
                "frames_excluded_no_label,0,never guessed"),
                String.join("\n", report.getCsvLines()));

        File flat = write("flat.extxyz",
                "2\nenergy=-3.0\nH 0.0 0.0 0.0\nH 0.0 0.0 0.74\n"
                + "2\nenergy=-3.1\nH 0.0 0.0 0.0\nH 0.0 0.0 0.75\n"
                + "2\nenergy=-2.9\nH 0.0 0.0 0.0\nH 0.0 0.0 0.73\n");
        AnalysisReport degenerate = ResultAnalysisService.analyze(
                AnalysisKind.ML_DATASET_BASELINE, new ProjectProperty(),
                this.tempDir.toFile(), "ds", "run.log", flat,
                new AnalysisParameters());
        assertFalse(degenerate.isSuccess(),
                "a single repeated composition must fail closed");
        assertTrue(degenerate.getText().contains("[BASELINE_DEGENERATE]"),
                degenerate.getText());

        File unlabeled = write("nolabel.extxyz",
                "1\nProperties=species:S:1:pos:R:3\nH 0.0 0.0 0.0\n");
        AnalysisReport noenergy = ResultAnalysisService.analyze(
                AnalysisKind.ML_DATASET_BASELINE, new ProjectProperty(),
                this.tempDir.toFile(), "ds", "run.log", unlabeled,
                new AnalysisParameters());
        assertFalse(noenergy.isSuccess(), "no labels => nothing fitted");
        assertTrue(noenergy.getText().contains("[BASELINE_ENERGY]"),
                noenergy.getText());
    }
}
