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
}
