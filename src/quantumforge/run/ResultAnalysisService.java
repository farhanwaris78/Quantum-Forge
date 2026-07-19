/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */

package quantumforge.run;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.app.project.editor.result.geometry.GeometryMeasurer;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.builder.QECitationManager;
import quantumforge.builder.QEHullThermodynamics;
import quantumforge.builder.QEPointDefectBuilder;
import quantumforge.input.QEInput;
import quantumforge.input.QEInputDiffPreview;
import quantumforge.input.QEKpointMeshAdvisor;
import quantumforge.input.QEPpChargePotentialBuilder;
import quantumforge.input.QEPpWavefunctionBuilder;
import quantumforge.input.QESCFInput;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectEnergies;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;
import quantumforge.pseudo.PseudoFamilyValidator;
import quantumforge.run.parser.CubeGridReader;
import quantumforge.run.parser.ElasticParser;
import quantumforge.run.parser.GeometryConvergenceValidator;
import quantumforge.run.parser.PhononDosThermodynamics;
import quantumforge.run.parser.QEAcousticSumRuleValidator;
import quantumforge.run.parser.QEBandsDataParser;
import quantumforge.run.parser.QEBerryPolarizationParser;
import quantumforge.run.parser.QEBornChargeDielectricParser;
import quantumforge.run.parser.QECarParrinelloParser;
import quantumforge.run.parser.QECastepLogParser;
import quantumforge.run.parser.QEElasticStabilityValidator;
import quantumforge.run.parser.QEEliashbergTcCalculator;
import quantumforge.run.parser.QEGipawNmrParser;
import quantumforge.run.parser.QEGridDensityDifference;
import quantumforge.run.parser.QEHubbardHpParser;
import quantumforge.run.parser.QELammpsThermoParser;
import quantumforge.run.parser.QEMagneticMomentParser;
import quantumforge.run.parser.QEMdDiffusionMsdParser;
import quantumforge.run.parser.QEPhono3pyKappaParser;
import quantumforge.run.parser.QESlabPlateauDiagnostic;
import quantumforge.run.parser.QESmearingConvergenceAnalyzer;
import quantumforge.run.parser.QETimingResourceParser;
import quantumforge.run.parser.QETensorAnalyzer;
import quantumforge.run.parser.ScfConvergenceAnalyzer;
import quantumforge.run.parser.ScfIterationRecord;
import quantumforge.symmetry.MagneticSpaceGroupDetector;
import quantumforge.run.parser.QEPwcondConductanceParser;
import quantumforge.run.parser.QEThermoPwEosParser;
import quantumforge.run.parser.QETurboSpectrumParser;
import quantumforge.run.parser.QEVasprunXmlParser;
import quantumforge.run.parser.QEWannier90SpreadParser;
import quantumforge.run.parser.QEXSpectraXanesParser;
import quantumforge.run.parser.QeXmlResultParser;
import quantumforge.symmetry.SeekPathResult;
import quantumforge.symmetry.SpglibService;
import quantumforge.symmetry.StandardizedCell;

/**
 * Deterministic, read-only result-analysis service bound to existing, individually
 * tested parser backends (roadmap items 32, 38, 43, 46, 51/54/55/58, 59, 60, 61,
 * 63, 64, 65, 66, 67, 68, 69, 74, 79, 106, 108, 113, 119, 134, 151, 156, and 165).
 * This class owns no process execution and writes nothing:
 * produced reports are returned to the caller (GUI or CLI) which decides what to
 * persist. Every report states units, source-file provenance, and the analysis'
 * limitations instead of presenting parsed numbers as validation evidence.
 */
public final class ResultAnalysisService {

    /** Maximum number of bytes read when scanning a log for the Fermi energy. */
    private static final long LOG_SCAN_BYTES = 2L * 1024L * 1024L;

    private static final Pattern FERMI_LINE = Pattern.compile(
            "the\\s+Fermi\\s+energy\\s+is\\s+([-+0-9.DdEe]+)\\s+ev", Pattern.CASE_INSENSITIVE);

    /**
     * Result-analysis kinds exposed through the project viewer. Labels are shown
     * to the user; every kind maps to a tested parser or input builder.
     */
    public enum AnalysisKind {
        BANDS_DATA("Band structure data (bands.x)"),
        PP_CHARGE_INPUT("pp.x charge-density input preview"),
        PP_POTENTIAL_INPUT("pp.x electrostatic-potential input preview"),
        PP_WAVEFUNCTION_INPUT("pp.x wavefunction input preview"),
        MAGNETIZATION("Magnetization from pw.x log"),
        BORN_DIELECTRIC("Born charges and dielectric tensor"),
        HUBBARD_HP("Hubbard U from hp.x output"),
        TDDFT_SPECTRUM("TDDFT dipole spectrum"),
        XANES("XANES cross section"),
        NMR_SHIELDING("NMR (GIPAW) shielding tensors"),
        PWCOND_TRANSMISSION("PWcond transmission"),
        WANNIER90_SPREAD("Wannier90 spread convergence"),
        THERMOPW_EOS("thermo_pw equation of state"),
        PHONO3PY_KAPPA("phono3py lattice thermal conductivity"),
        ELIASHBERG_TC("Allen-Dynes Tc from alpha2F"),
        DRY_RUN_PREFLIGHT("Dry-run preflight check"),
        RESTART_ASSESSMENT("Restart safety assessment"),
        SCRATCH_ESTIMATE("Scratch storage check"),
        RESOURCE_ESTIMATE("Resource and MPI layout estimate"),
        RUN_MANIFEST("Run manifest history"),
        GEOMETRY_MEASURE("Geometry measurement (bond/angle/dihedral)"),
        MD_MSD("MD diffusion from XYZ trajectory"),
        HULL_STABILITY("Convex-hull stability from phase CSV"),
        BERRY_POLARIZATION("Berry-phase polarization"),
        WORK_FUNCTION("Slab work function (planar-averaged potential)"),
        CP_TRAJECTORY("Car-Parrinello cp.x energy trajectory"),
        MAGNETIC_ORDER("Magnetic order classification"),
        CUBE_INSPECT("CUBE volumetric grid inspection"),
        CITATIONS("Citation / BibTeX bundle"),
        SCF_CONVERGENCE("SCF energy convergence"),
        TIMING_PROFILE("pw.x timing and resource profile"),
        SMEARING_ANALYSIS("Smearing entropy and degauss safety"),
        PHONON_DOS_THERMO("Phonon DOS harmonic thermodynamics"),
        ELASTIC_STABILITY("Elastic tensor stability (thermo_pw)"),
        ELASTIC_MODULI("Elastic moduli (Voigt/Reuss/Hill)"),
        LAMMPS_THERMO("LAMMPS MD thermo trajectory"),
        GEOMETRY_CONVERGENCE("Relax geometry convergence validation"),
        PSEUDO_FAMILY("Pseudopotential family consistency"),
        SYMMETRY_KPATH("spglib standardization and SeeK-path"),
        XML_SUMMARY("QE XML output cross-check"),
        VASP_VASPRUN("vasprun.xml inspection (parser only)"),
        CASTEP_LOG("CASTEP .castep log inspection (parser only)"),
        INPUT_DIFF("Input diff against reference input"),
        KMESH_QUALITY("k-point mesh quality"),
        DEFECT_PREVIEW("Point-defect preview (vacancy/substitution)"),
        CONVERGENCE_REVIEW("Convergence series review (ecut/k-mesh energies)"),
        SERIES_PLAN("Convergence series plan (preview)");

        private final String label;

        AnalysisKind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }

        /** Human-readable label for GUI selection lists. */
        @Override
        public String toString() {
            return this.label;
        }

        /** True when this kind normally reads the primary pw.x project log. */
        public boolean usesProjectLog() {
            return this == MAGNETIZATION || this == BORN_DIELECTRIC || this == THERMOPW_EOS;
        }

        /** True when the analysis synthesizes a pp.x input instead of parsing output. */
        public boolean isInputPreview() {
            return this == PP_CHARGE_INPUT || this == PP_POTENTIAL_INPUT || this == PP_WAVEFUNCTION_INPUT;
        }

        /** True when the kind needs the whole open project rather than a parsed file. */
        public boolean isProjectBound() {
            return this == DRY_RUN_PREFLIGHT || this == RESTART_ASSESSMENT
                    || this == SCRATCH_ESTIMATE || this == RESOURCE_ESTIMATE || this == RUN_MANIFEST
                    || this == GEOMETRY_MEASURE || this == MD_MSD || this == MAGNETIC_ORDER
                    || this == CITATIONS || this == BERRY_POLARIZATION
                    || this == GEOMETRY_CONVERGENCE || this == PSEUDO_FAMILY || this == SYMMETRY_KPATH
                    || this == INPUT_DIFF || this == KMESH_QUALITY || this == DEFECT_PREVIEW
                    || this == SERIES_PLAN;
        }

        /** True for project-bound kinds that additionally parse a user data file. */
        public boolean needsDataFile() {
            return this == MD_MSD || this == INPUT_DIFF;
        }
    }

    /** Optional, explicitly supplied analysis parameters. Defaults stay physical. */
    public static final class AnalysisParameters {
        private double fermiEv = Double.NaN;
        private int kpointIndex = 1;
        private int bandIndex = 1;
        private int spinComponent = 0;
        private boolean lsign = false;
        private double muStar = 0.10;
        private int totalRanks = 1;
        private int atomIndexA = 1;
        private int atomIndexB = 2;
        private int atomIndexC = 0; // 0 means "not supplied"
        private int atomIndexD = 0;
        private double frameTimeStepPs = 1.0;
        private double temperatureK = 300.0;
        private int atomCount = 1;
        private double forceThresholdRyBohr = 1.0e-3;
        private double pressureThresholdKbar = Double.NaN; // NaN means "no pressure check"
        private double symmetryTolerance = 1.0e-5;
        private String defectType = "vacancy";
        private String defectElement = "";
        private int defectCharge = 0;
        private String seriesKeyword = "ecutwfc";
        private double seriesStart = 30.0;
        private double seriesStep = 10.0;
        private int seriesCount = 6;
        private double energyToleranceRyPerAtom = 1.0e-3;

        public double getFermiEv() { return this.fermiEv; }
        public int getKpointIndex() { return this.kpointIndex; }
        public int getBandIndex() { return this.bandIndex; }
        public int getSpinComponent() { return this.spinComponent; }
        public boolean isLsign() { return this.lsign; }
        public double getMuStar() { return this.muStar; }
        public int getTotalRanks() { return this.totalRanks; }
        public int getAtomIndexA() { return this.atomIndexA; }
        public int getAtomIndexB() { return this.atomIndexB; }
        public int getAtomIndexC() { return this.atomIndexC; }
        public int getAtomIndexD() { return this.atomIndexD; }
        public double getFrameTimeStepPs() { return this.frameTimeStepPs; }
        public double getTemperatureK() { return this.temperatureK; }
        public int getAtomCount() { return this.atomCount; }
        public double getForceThresholdRyBohr() { return this.forceThresholdRyBohr; }
        public double getPressureThresholdKbar() { return this.pressureThresholdKbar; }
        public double getSymmetryTolerance() { return this.symmetryTolerance; }
        public String getDefectType() { return this.defectType; }
        public String getDefectElement() { return this.defectElement; }
        public int getDefectCharge() { return this.defectCharge; }
        public String getSeriesKeyword() { return this.seriesKeyword; }
        public double getSeriesStart() { return this.seriesStart; }
        public double getSeriesStep() { return this.seriesStep; }
        public int getSeriesCount() { return this.seriesCount; }
        public double getEnergyToleranceRyPerAtom() { return this.energyToleranceRyPerAtom; }

        public AnalysisParameters withFermiEv(double value) { this.fermiEv = value; return this; }
        public AnalysisParameters withKpointIndex(int value) { this.kpointIndex = value; return this; }
        public AnalysisParameters withBandIndex(int value) { this.bandIndex = value; return this; }
        public AnalysisParameters withSpinComponent(int value) { this.spinComponent = value; return this; }
        public AnalysisParameters withLsign(boolean value) { this.lsign = value; return this; }
        public AnalysisParameters withMuStar(double value) { this.muStar = value; return this; }
        public AnalysisParameters withTotalRanks(int value) { this.totalRanks = value; return this; }
        public AnalysisParameters withAtomIndices(int a, int b, int c, int d) {
            this.atomIndexA = a;
            this.atomIndexB = b;
            this.atomIndexC = c;
            this.atomIndexD = d;
            return this;
        }
        public AnalysisParameters withFrameTimeStepPs(double value) {
            this.frameTimeStepPs = value;
            return this;
        }
        public AnalysisParameters withTemperatureK(double value) {
            this.temperatureK = value;
            return this;
        }
        public AnalysisParameters withAtomCount(int value) {
            this.atomCount = value;
            return this;
        }
        public AnalysisParameters withForceThresholdRyBohr(double value) {
            this.forceThresholdRyBohr = value;
            return this;
        }
        public AnalysisParameters withPressureThresholdKbar(double value) {
            this.pressureThresholdKbar = value;
            return this;
        }
        public AnalysisParameters withSymmetryTolerance(double value) {
            this.symmetryTolerance = value;
            return this;
        }
        public AnalysisParameters withDefectType(String value) {
            this.defectType = value;
            return this;
        }
        public AnalysisParameters withDefectElement(String value) {
            this.defectElement = value;
            return this;
        }
        public AnalysisParameters withDefectCharge(int value) {
            this.defectCharge = value;
            return this;
        }
        public AnalysisParameters withSeriesKeyword(String value) {
            this.seriesKeyword = value;
            return this;
        }
        public AnalysisParameters withSeriesStart(double value) {
            this.seriesStart = value;
            return this;
        }
        public AnalysisParameters withSeriesStep(double value) {
            this.seriesStep = value;
            return this;
        }
        public AnalysisParameters withSeriesCount(int value) {
            this.seriesCount = value;
            return this;
        }
        public AnalysisParameters withEnergyToleranceRyPerAtom(double value) {
            this.energyToleranceRyPerAtom = value;
            return this;
        }
    }

    /** A completed, self-describing analysis result. */
    public static final class AnalysisReport {
        private final String title;
        private final boolean success;
        private final String text;
        private final List<String> csvLines;
        private final String generatedInput;

        public AnalysisReport(String title, boolean success, String text,
                              List<String> csvLines, String generatedInput) {
            this.title = title == null ? "Result analysis" : title;
            this.success = success;
            this.text = text == null ? "" : text;
            this.csvLines = csvLines == null ? List.of() : List.copyOf(csvLines);
            this.generatedInput = generatedInput;
        }

        public String getTitle() { return this.title; }
        public boolean isSuccess() { return this.success; }
        public String getText() { return this.text; }
        public List<String> getCsvLines() { return this.csvLines; }
        public String getGeneratedInput() { return this.generatedInput; }
        public boolean hasCsv() { return !this.csvLines.isEmpty(); }
    }

    private ResultAnalysisService() {
        // Service class.
    }

    /**
     * Finds bounded candidate files for an analysis kind inside the project
     * directory. Discovery is name-based and conservative: a wrong candidate is
     * rejected again later by the parser itself, so nothing is silently analyzed.
     */
    public static List<File> discover(AnalysisKind kind, File projectDir, String logFileName) {
        List<File> candidates = new ArrayList<>();
        if (kind == null || projectDir == null || !projectDir.isDirectory()) {
            return candidates;
        }

        if (kind.usesProjectLog() && logFileName != null && !logFileName.isBlank()) {
            File log = new File(projectDir, logFileName);
            if (log.isFile()) {
                candidates.add(log);
            }
        }

        File[] files = projectDir.listFiles(File::isFile);
        if (files == null) {
            return candidates;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (candidates.contains(file)) {
                continue;
            }
            if (matches(kind, file.getName().toLowerCase(Locale.ROOT))) {
                candidates.add(file);
            }
        }
        return candidates;
    }

    /** Lowercased filename matching, one place per analysis kind. */
    private static boolean matches(AnalysisKind kind, String name) {
        switch (kind) {
        case BANDS_DATA:
            return name.endsWith(".dat.gnu") || (name.startsWith("bands") && name.endsWith(".dat"));
        case HUBBARD_HP:
            return name.contains("hp") && (name.endsWith(".out") || name.endsWith(".log"));
        case TDDFT_SPECTRUM:
            return name.contains("plot_chi") || name.contains("turbospectrum")
                    || (name.contains("chi") && name.endsWith(".dat"));
        case XANES:
            return name.contains("xanes") || name.endsWith(".xmu");
        case NMR_SHIELDING:
            return name.contains("gipaw") && (name.endsWith(".out") || name.endsWith(".log"));
        case PWCOND_TRANSMISSION:
            return name.contains("pwcond") || name.contains("trans") || name.contains("conductance");
        case WANNIER90_SPREAD:
            return name.endsWith(".wout");
        case THERMOPW_EOS:
            return name.contains("eos") || name.contains("thermo");
        case PHONO3PY_KAPPA:
            return name.startsWith("kappa") || name.contains("thermal_conductivity");
        case ELIASHBERG_TC:
            return name.endsWith(".a2f") || name.contains("alpha2f") || name.contains("a2f");
        case MD_MSD:
            return name.endsWith(".xyz");
        case HULL_STABILITY:
            return name.endsWith(".csv");
        case WORK_FUNCTION:
            return name.contains("tavg") || name.contains("planar")
                    || (name.contains("avg") && (name.endsWith(".dat") || name.endsWith(".out")));
        case CP_TRAJECTORY:
            return name.contains("cp") && (name.endsWith(".out") || name.endsWith(".log"));
        case CUBE_INSPECT:
            return name.endsWith(".cube") || name.endsWith(".cub");
        case SCF_CONVERGENCE:
        case TIMING_PROFILE:
        case SMEARING_ANALYSIS:
            return name.endsWith(".log") || name.endsWith(".out");
        case PHONON_DOS_THERMO:
            return name.contains("phdos") || name.endsWith(".dos")
                    || (name.contains("dos") && name.endsWith(".dat"));
        case ELASTIC_STABILITY:
        case ELASTIC_MODULI:
            return name.contains("elastic");
        case LAMMPS_THERMO:
            return name.contains("lammps");
        case XML_SUMMARY:
            return name.endsWith(".xml") && (name.contains("data-file")
                    || name.contains("schema") || name.contains("qesys"));
        case VASP_VASPRUN:
            return name.contains("vasprun");
        case CASTEP_LOG:
            return name.endsWith(".castep");
        case INPUT_DIFF:
            return name.endsWith(".in") || name.endsWith(".inp") || name.contains(".in.");
        case CONVERGENCE_REVIEW:
            return name.contains("convergence") || name.contains("series")
                    || (name.contains("ecut") && name.endsWith(".csv"));
        default:
            return false;
        }
    }

    /**
     * Runs one analysis. The caller passes an already-resolved file when the
     * discovery list was presented to the user; otherwise the first discovered
     * candidate is used. No file is ever created or modified here.
     */
    public static AnalysisReport analyze(AnalysisKind kind, ProjectProperty property,
            File projectDir, String prefix, String logFileName, File file, AnalysisParameters parameters) {
        if (kind == null) {
            return failure("Result analysis", "No analysis type was selected.");
        }
        if (property == null) {
            return failure(kind.getLabel(), "Project property data is unavailable.");
        }
        AnalysisParameters params = parameters == null ? new AnalysisParameters() : parameters;

        if (kind.isInputPreview()) {
            return analyzeInputPreview(kind, prefix, projectDir, params);
        }

        File source = file != null ? file : firstCandidate(kind, projectDir, logFileName);
        if (source == null || !source.isFile()) {
            return failure(kind.getLabel(), "No usable data file was found for '"
                    + kind.getLabel() + "'.\nLooked in the project directory for the standard "
                    + "names of this analysis; pass the file explicitly if your engine named "
                    + "it differently.");
        }

        try {
            switch (kind) {
            case BANDS_DATA:
                return analyzeBands(property, projectDir, logFileName, source, params);
            case MAGNETIZATION:
                return analyzeMagnetization(property, source);
            case BORN_DIELECTRIC:
                return analyzeBornDielectric(property, source);
            case HUBBARD_HP:
                return analyzeHubbard(property, source);
            case TDDFT_SPECTRUM:
                return analyzeTddft(property, source);
            case XANES:
                return analyzeXanes(property, source);
            case NMR_SHIELDING:
                return analyzeNmr(property, source);
            case PWCOND_TRANSMISSION:
                return analyzePwcond(property, source);
            case WANNIER90_SPREAD:
                return analyzeWannier90(property, source);
            case THERMOPW_EOS:
                return analyzeEos(property, source);
            case PHONO3PY_KAPPA:
                return analyzeKappa(property, source);
            case ELIASHBERG_TC:
                return analyzeTc(property, source, params);
            case HULL_STABILITY:
                return analyzeHull(source);
            case WORK_FUNCTION:
                return analyzeWorkFunction(property, projectDir, logFileName, source, params);
            case CP_TRAJECTORY:
                return analyzeCpTrajectory(property, source);
            case CUBE_INSPECT:
                return analyzeCube(source);
            case SCF_CONVERGENCE:
                return analyzeScfConvergence(source);
            case TIMING_PROFILE:
                return analyzeTiming(property, source);
            case SMEARING_ANALYSIS:
                return analyzeSmearing(property, source, params);
            case PHONON_DOS_THERMO:
                return analyzePhononDosThermo(source, params);
            case ELASTIC_STABILITY:
                return analyzeElastic(property, source);
            case ELASTIC_MODULI:
                return analyzeElasticModuli(property, source);
            case LAMMPS_THERMO:
                return analyzeLammpsThermo(property, source);
            case XML_SUMMARY:
                return analyzeXmlSummary(property, projectDir, logFileName, source);
            case VASP_VASPRUN:
                return analyzeVasprun(property, source);
            case CASTEP_LOG:
                return analyzeCastepLog(property, source);
            case CONVERGENCE_REVIEW:
                return analyzeConvergenceReview(source, params);
            default:
                return failure(kind.getLabel(), "This analysis kind is not implemented.");
            }
        } catch (IOException | RuntimeException ex) {
            return failure(kind.getLabel(), "Analysis of " + source.getName()
                    + " failed closed: " + ex.getMessage());
        }
    }

    private static File firstCandidate(AnalysisKind kind, File projectDir, String logFileName) {
        List<File> candidates = discover(kind, projectDir, logFileName);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Runs a project-bound analysis (run readiness, restart safety, scratch and
     * resource estimates, or run-manifest history) against the open project.
     * File-based kinds are delegated through the project's context. Nothing is
     * executed and no file is created or modified.
     */
    public static AnalysisReport analyze(AnalysisKind kind, Project project,
            AnalysisParameters parameters) {
        return analyze(kind, project, null, parameters);
    }

    /**
     * Project-bound analysis entry with an optional user data file (used by the MD
     * trajectory analysis). File-based kinds ignore the file when it is null and fall
     * back to project-directory discovery.
     */
    public static AnalysisReport analyze(AnalysisKind kind, Project project, File file,
            AnalysisParameters parameters) {
        if (kind == null) {
            return failure("Result analysis", "No analysis type was selected.");
        }
        AnalysisParameters params = parameters == null ? new AnalysisParameters() : parameters;
        if (!kind.isProjectBound()) {
            if (project == null) {
                return failure(kind.getLabel(), "No project is open.");
            }
            return analyze(kind, project.getProperty(), project.getDirectory(),
                    project.getPrefixName(), project.getLogFileName(), file, params);
        }
        if (project == null) {
            return failure(kind.getLabel(), "No project is open.");
        }
        try {
            switch (kind) {
            case DRY_RUN_PREFLIGHT:
                return analyzePreflight(project);
            case RESTART_ASSESSMENT:
                return analyzeRestart(project);
            case SCRATCH_ESTIMATE:
                return analyzeScratch(project);
            case RESOURCE_ESTIMATE:
                return analyzeResources(project, params);
            case RUN_MANIFEST:
                return analyzeRunManifest(project);
            case GEOMETRY_MEASURE:
                return analyzeGeometryMeasure(project, params);
            case MD_MSD:
                return analyzeMdMsd(project, file, params);
            case MAGNETIC_ORDER:
                return analyzeMagneticOrder(project);
            case CITATIONS:
                return analyzeCitations(project);
            case BERRY_POLARIZATION:
                return analyzeBerry(project);
            case GEOMETRY_CONVERGENCE:
                return analyzeGeometryConvergence(project, params);
            case PSEUDO_FAMILY:
                return analyzePseudoFamily(project);
            case SYMMETRY_KPATH:
                return analyzeSymmetryKpath(project, params);
            case INPUT_DIFF:
                return analyzeInputDiff(project, file);
            case KMESH_QUALITY:
                return analyzeKmesh(project);
            case DEFECT_PREVIEW:
                return analyzeDefectPreview(project, params);
            case SERIES_PLAN:
                return analyzeSeriesPlan(params);
            default:
                return failure(kind.getLabel(), "This analysis kind is not implemented.");
            }
        } catch (RuntimeException ex) {
            return failure(kind.getLabel(), "Analysis failed closed: " + ex.getMessage());
        }
    }

    /** Deterministic binaries/disk/MPI/input/DAG preflight; identical to the runner's gate. */
    private static AnalysisReport analyzePreflight(Project project) {
        RunningType type = RunningType.getRunningType(project);
        if (type == null) {
            type = RunningType.SCF;
        }
        DryRunPreflight.Report report = DryRunPreflight.run(project, type, 1);
        StringBuilder text = new StringBuilder();
        text.append("Workflow type: ").append(type).append('\n');
        long errors = report.getIssues().stream()
                .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR).count();
        long warnings = report.getIssues().size() - errors;
        text.append("Blocking errors: ").append(errors).append("; warnings/info: ")
                .append(warnings).append("\n\n");
        for (ValidationIssue issue : report.getIssues()) {
            text.append(issue).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("  ").append(issue.getDocumentationUrl()).append('\n');
            }
        }
        if (report.getDag() != null) {
            text.append("\nPlanned command DAG:\n").append(report.getDag().describe());
        }
        text.append("\nNo calculation was started. A clean report is not convergence or "
                + "physical-suitability evidence.");
        return new AnalysisReport(AnalysisKind.DRY_RUN_PREFLIGHT.getLabel(),
                errors == 0L, text.toString(), List.of(), null);
    }

    /** Restart-mode recommendation after checking .save completeness and prefix/outdir. */
    private static AnalysisReport analyzeRestart(Project project) {
        File directory = project.getDirectory();
        if (directory == null) {
            return failure(AnalysisKind.RESTART_ASSESSMENT.getLabel(),
                    "The project has no on-disk directory to assess.");
        }
        OperationResult<RestartManager.RestartAssessment> result = RestartManager.assess(
                directory.toPath(), project.getPrefixName());
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            return failure(AnalysisKind.RESTART_ASSESSMENT.getLabel(), result.getMessage());
        }
        RestartManager.RestartAssessment assessment = result.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Save directory: ").append(assessment.getSaveDirectory()).append('\n');
        text.append("Restart safe: ").append(assessment.isRestartSafe()).append('\n');
        text.append("Recommended restart_mode: ").append(assessment.getRecommendedRestartMode())
                .append("\n\nDiagnostics:\n");
        for (String diagnostic : assessment.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        if (assessment.isRestartSafe()) {
            text.append("\nNamelist snippet for a resubmission input (review before use):\n")
                    .append(RestartManager.namelistSnippet(assessment));
        }
        text.append("\nNo restart files were modified or copied.");
        return new AnalysisReport(AnalysisKind.RESTART_ASSESSMENT.getLabel(),
                assessment.isRestartSafe(), text.toString(), List.of(), null);
    }

    /** Conservative scratch estimate (full k mesh, buffer factor) and writable/quota check. */
    private static AnalysisReport analyzeScratch(Project project) {
        QEContext context = requireInputAndCell(project, AnalysisKind.SCRATCH_ESTIMATE.getLabel());
        if (context.failure != null) {
            return context.failure;
        }
        QEScratchStoragePolicy policy = new QEScratchStoragePolicy();
        long estimateBytes = policy.estimateScratchSize(context.cell, context.input);
        StringBuilder text = new StringBuilder();
        text.append("Scratch root: ").append(policy.getScratchRoot()).append('\n');
        if (estimateBytes <= 0L) {
            text.append("A positive scratch estimate could not be produced from this input; "
                    + "verification fails closed by policy.");
            return new AnalysisReport(AnalysisKind.SCRATCH_ESTIMATE.getLabel(), false,
                    text.toString(), List.of(), null);
        }
        text.append(String.format(Locale.ROOT,
                "Estimated scratch need: %.2f GiB (includes buffer/restart factor)%n",
                estimateBytes / 1073741824.0));
        List<String> warnings = new ArrayList<>();
        boolean ok = policy.verifySpace(policy.getScratchRoot(), estimateBytes, warnings);
        text.append("Writable + quota check: ").append(ok ? "passed" : "FAILED").append('\n');
        for (String warning : warnings) {
            text.append(" - ").append(warning).append('\n');
        }
        text.append("\nNothing was created or deleted except the policy's own scratch-root "
                + "verification probe; cleanup is never triggered by this analysis.");
        return new AnalysisReport(AnalysisKind.SCRATCH_ESTIMATE.getLabel(), ok,
                text.toString(), List.of(), null);
    }

    /** Memory/core-hour estimate with confidence range plus QE pool-layout advice. */
    private static AnalysisReport analyzeResources(Project project, AnalysisParameters params) {
        QEContext context = requireInputAndCell(project, AnalysisKind.RESOURCE_ESTIMATE.getLabel());
        if (context.failure != null) {
            return context.failure;
        }
        if (params.getTotalRanks() <= 0) {
            return failure(AnalysisKind.RESOURCE_ESTIMATE.getLabel(),
                    "Total MPI ranks must be positive; received " + params.getTotalRanks() + ".");
        }
        QEResourceEstimator.Estimation estimation = QEResourceEstimator.estimate(
                context.cell, context.input);
        QEMpiTopologyAdvisor.TopologyRecommendation topology = QEMpiTopologyAdvisor.advise(
                context.input, params.getTotalRanks());
        StringBuilder text = new StringBuilder();
        if (estimation != null) {
            text.append(estimation.getReport()).append("\n\n");
        } else {
            text.append("No resource estimate could be produced from this input.\n\n");
        }
        text.append("MPI topology for ").append(params.getTotalRanks()).append(" rank(s):\n");
        text.append("  pools=").append(topology.getNumPools())
                .append(" band-groups=").append(topology.getNumBandGroups())
                .append(" task-groups=").append(topology.getNumTaskGroups())
                .append(" diag-groups=").append(topology.getNumDiagGroups()).append('\n');
        text.append("  QE arguments: ").append(topology.getCmdArguments()).append('\n');
        for (String warning : topology.getWarnings()) {
            text.append(" ! ").append(warning).append('\n');
        }
        for (String note : topology.getNotes()) {
            text.append(" - ").append(note).append('\n');
        }
        text.append("\nEstimates are model-based ranges, not measured benchmarks; validate "
                + "against a small pilot run before consuming allocation.");
        return new AnalysisReport(AnalysisKind.RESOURCE_ESTIMATE.getLabel(), estimation != null,
                text.toString(), List.of(), null);
    }

    private static final class QEContext {
        private Cell cell;
        private QEInput input;
        private AnalysisReport failure;
    }

    private static QEContext requireInputAndCell(Project project, String label) {
        QEContext context = new QEContext();
        try {
            project.resolveQEInputs();
        } catch (RuntimeException ex) {
            context.failure = failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
            return context;
        }
        context.input = project.getQEInputCurrent();
        if (context.input == null) {
            context.failure = failure(label, "The project has no current QE input to analyze.");
            return context;
        }
        context.cell = project.getCell();
        if (context.cell == null) {
            context.failure = failure(label, "The project has no atomic cell to analyze.");
        }
        return context;
    }

    /** Bounded, read-only history of this project's run manifest (JSONL). */
    private static AnalysisReport analyzeRunManifest(Project project) throws RuntimeException {
        File directory = project.getDirectory();
        if (directory == null) {
            return failure(AnalysisKind.RUN_MANIFEST.getLabel(),
                    "The project has no on-disk directory.");
        }
        File manifest = new File(directory, RunManifest.FILE_NAME);
        if (!manifest.isFile()) {
            return failure(AnalysisKind.RUN_MANIFEST.getLabel(),
                    "No run manifest yet: " + RunManifest.FILE_NAME
                    + " is created by launched calculations.");
        }
        String tail;
        try {
            tail = readTailUtf8(manifest.toPath(), LOG_SCAN_BYTES);
        } catch (IOException ex) {
            return failure(AnalysisKind.RUN_MANIFEST.getLabel(),
                    "Could not read the run manifest: " + ex.getMessage());
        }
        Pattern jobId = Pattern.compile("\"jobId\"\\s*:\\s*\"([^\"]*)\"");
        Pattern stage = Pattern.compile("\"stage\"\\s*:\\s*\"([^\"]*)\"");
        Pattern status = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"");
        Pattern started = Pattern.compile("\"startedAt\"\\s*:\\s*\"([^\"]*)\"");
        Pattern exitCode = Pattern.compile("\"exitCode\"\\s*:\\s*(null|-?\\d+)");
        StringBuilder table = new StringBuilder();
        table.append("job                     stage          status       exit  started (UTC)\n");
        int rows = 0;
        int unparsed = 0;
        for (String line : tail.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher mJob = jobId.matcher(line);
            if (!mJob.find()) {
                unparsed++;
                continue;
            }
            Matcher mStage = stage.matcher(line);
            Matcher mStatus = status.matcher(line);
            Matcher mStarted = started.matcher(line);
            Matcher mExit = exitCode.matcher(line);
            table.append(String.format(Locale.ROOT, "%-22s  %-13s  %-11s  %-4s  %s%n",
                    trimTo(mJob.group(1), 22), mStage.find() ? trimTo(mStage.group(1), 13) : "?",
                    mStatus.find() ? trimTo(mStatus.group(1), 11) : "?",
                    mExit.find() ? mExit.group(1) : "?",
                    mStarted.find() ? mStarted.group(1) : "?"));
            rows++;
        }
        if (rows == 0) {
            return failure(AnalysisKind.RUN_MANIFEST.getLabel(),
                    "The run manifest exists but no parsable job records were found in its tail.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Manifest: ").append(RunManifest.FILE_NAME)
                .append(" (bounded tail; full file is never loaded into the GUI)\n\n");
        text.append(table);
        if (unparsed > 0) {
            text.append("\nSkipped ").append(unparsed)
                    .append(" malformed/manifest-schema-mismatch line(s).");
        }
        text.append("\n\nEvery plotted result is attributable to exactly one of these "
                + "command/manifest records.");
        return new AnalysisReport(AnalysisKind.RUN_MANIFEST.getLabel(), true,
                text.toString(), List.of(), null);
    }

    private static String trimTo(String value, int width) {
        if (value == null) {
            return "?";
        }
        return value.length() <= width ? value : value.substring(0, Math.max(1, width - 1)) + "~";
    }

    /** Minimum-image bond/angle/dihedral on indices the user supplied (1-based). */
    private static AnalysisReport analyzeGeometryMeasure(Project project, AnalysisParameters params) {
        String label = AnalysisKind.GEOMETRY_MEASURE.getLabel();
        Cell cell = project.getCell();
        if (cell == null) {
            return failure(label, "The project has no atomic cell to measure.");
        }
        Atom[] atoms = cell.listAtoms();
        int natoms = atoms == null ? 0 : atoms.length;
        if (natoms < 2) {
            return failure(label, "At least two atoms are required; found " + natoms + ".");
        }
        int a = params.getAtomIndexA();
        int b = params.getAtomIndexB();
        int c = params.getAtomIndexC();
        int d = params.getAtomIndexD();
        if (a < 1 || a > natoms || b < 1 || b > natoms) {
            return failure(label, "Atom indices A and B must be 1-based within [1, " + natoms
                    + "]; received A=" + a + ", B=" + b + ".");
        }
        if ((c < 0 || c > natoms) || (d < 0 || d > natoms)) {
            return failure(label, "Optional indices C/D must be 0 (absent) or within [1, "
                    + natoms + "]; received C=" + c + ", D=" + d + ".");
        }
        if (d >= 1 && c < 1) {
            return failure(label, "A dihedral needs indices C and D; D was given without C.");
        }
        if (a == b || (c >= 1 && (c == a || c == b)) || (d >= 1 && (d == a || d == b || d == c))) {
            return failure(label, "Measurement indices must be distinct atoms.");
        }

        GeometryMeasurer measurer = new GeometryMeasurer();
        measurer.setCell(cell);
        measurer.setAtomA(atoms[a - 1]);
        measurer.setAtomB(atoms[b - 1]);
        if (c >= 1) {
            measurer.setAtomC(atoms[c - 1]);
        }
        if (d >= 1) {
            measurer.setAtomD(atoms[d - 1]);
        }
        if (!measurer.calculate()) {
            return failure(label, "The measurement could not be computed from these indices.");
        }

        StringBuilder text = new StringBuilder();
        text.append("Atoms are numbered 1-based in the project's current cell (")
                .append(natoms).append(" atoms).\n");
        text.append(String.format(Locale.ROOT, "d(A%d-B%d)      = %.6f Ang%n", a, b,
                measurer.getBondLengthAB()));
        if (c >= 1) {
            text.append(String.format(Locale.ROOT, "d(B%d-C%d)      = %.6f Ang%n", b, c,
                    measurer.getBondLengthBC()));
            text.append(String.format(Locale.ROOT, "angle(A%d-B%d-C%d) = %.4f deg%n", a, b, c,
                    measurer.getBondAngleABC()));
        }
        if (d >= 1) {
            text.append(String.format(Locale.ROOT, "d(C%d-D%d)      = %.6f Ang%n", c, d,
                    measurer.getBondLengthCD()));
            text.append(String.format(Locale.ROOT, "angle(B%d-C%d-D%d) = %.4f deg%n", b, c, d,
                    measurer.getBondAngleBCD()));
            text.append(String.format(Locale.ROOT, "dihedral(A%d-B%d-C%d-D%d) = %.4f deg%n",
                    a, b, c, d, measurer.getDihedralAngle()));
        }
        if (cell.copyLattice() != null) {
            text.append("\nDistances use the minimum-image convention of the periodic cell; "
                    + "angles follow the same periodic vectors. Coordinates are the project's "
                    + "Angstrom Cartesian convention.");
        } else {
            text.append("\nNo periodic lattice is set; distances are plain Cartesian "
                    + "differences in the project's Angstrom convention.");
        }
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** Unwrapped multi-frame XYZ trajectory -> multi-origin MSD -> D from the diffusive fit. */
    private static AnalysisReport analyzeMdMsd(Project project, File file,
            AnalysisParameters params) {
        String label = AnalysisKind.MD_MSD.getLabel();
        Cell cell = project.getCell();
        double[][] lattice = cell == null ? null : cell.copyLattice();
        if (lattice == null) {
            return failure(label, "Trajectory unwrapping needs the project's periodic cell; "
                    + "no lattice is set.");
        }
        if (file == null || !file.isFile()) {
            return failure(label, "Select an XYZ trajectory file (.xyz) inside or outside "
                    + "the project directory.");
        }
        double dtPs = params.getFrameTimeStepPs();
        if (!Double.isFinite(dtPs) || dtPs <= 0.0) {
            return failure(label, "Frame time step must be a positive number of picoseconds; "
                    + "received " + dtPs + ".");
        }

        List<double[][]> frames;
        try {
            frames = readXyzFrames(file);
        } catch (IOException ex) {
            return failure(label, "Could not read XYZ trajectory: " + ex.getMessage());
        }
        if (frames.size() < 2) {
            return failure(label, "Fewer than two complete frames were parsed from "
                    + file.getName() + ".");
        }

        QEMdDiffusionMsdParser parser = new QEMdDiffusionMsdParser(project.getProperty(), lattice);
        for (double[][] frame : frames) {
            parser.addFrame(frame);
        }
        parser.unwrapTrajectory();
        double[] msd = parser.computeMsd();

        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(file.getName()).append('\n');
        text.append("Frames: ").append(frames.size()).append("; atoms per frame: ")
                .append(frames.get(0).length).append('\n');
        text.append("Coordinates assumed Angstrom; box from the project lattice diagonal.\n");
        text.append(String.format(Locale.ROOT, "Frame time step: %.6f ps%n", dtPs));
        if (msd.length >= 2) {
            text.append(String.format(Locale.ROOT,
                    "MSD at last stored frame: %.6f Ang^2 (single-origin baseline)%n", msd[msd.length - 1]));
        }
        double diffusion = Double.NaN;
        if (frames.size() >= 5) {
            diffusion = parser.calculateDiffusionCoefficientSI(dtPs);
            text.append(String.format(Locale.ROOT,
                    "Self-diffusion coefficient D: %.6e cm^2/s%n", diffusion));
        } else {
            text.append("Trajectory is shorter than the 5-frame minimum; no D was fitted.\n");
        }
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nD uses the slope/6 of the MSD over the last 50% of frames; this fit "
                + "window is a diagnostic, not proof of the diffusive regime. Longer production "
                + "trajectories and unaltered output units remain the user's responsibility.");
        boolean ok = frames.size() >= 5 && Double.isFinite(diffusion);
        return new AnalysisReport(label, ok, text.toString(), List.of(), null);
    }

    private static final int MAX_XYZ_FRAMES = 10000;
    private static final int MAX_XYZ_ATOMS = 1000000;

    /** Bounded plain-XYZ multi-frame reader; frame count and atom count stay consistent. */
    private static List<double[][]> readXyzFrames(File file) throws IOException {
        List<double[][]> frames = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String countLine;
            while ((countLine = reader.readLine()) != null) {
                if (countLine.isBlank()) {
                    continue; // tolerate blank separators between frames
                }
                int natoms;
                try {
                    natoms = Integer.parseInt(countLine.trim());
                } catch (NumberFormatException ex) {
                    throw new IOException("Expected an atom-count line but found: '"
                            + countLine.trim() + "'");
                }
                if (natoms <= 0 || natoms > MAX_XYZ_ATOMS) {
                    throw new IOException("Atom count out of bounds: " + natoms);
                }
                if (reader.readLine() == null) {
                    throw new IOException("Truncated XYZ: missing comment line after atom count.");
                }
                double[][] frame = new double[natoms][3];
                for (int i = 0; i < natoms; i++) {
                    String row = reader.readLine();
                    if (row == null) {
                        throw new IOException("Truncated XYZ inside frame " + (frames.size() + 1));
                    }
                    String[] tokens = row.trim().split("\\s+");
                    if (tokens.length < 4) {
                        throw new IOException("Malformed XYZ atom row: '" + row.trim() + "'");
                    }
                    for (int axis = 0; axis < 3; axis++) {
                        double value;
                        try {
                            value = Double.parseDouble(normalizeExponent(tokens[axis + 1]));
                        } catch (NumberFormatException ex) {
                            throw new IOException("Non-numeric coordinate in row: '" + row.trim() + "'");
                        }
                        if (!Double.isFinite(value)) {
                            throw new IOException("Non-finite coordinate in row: '" + row.trim() + "'");
                        }
                        frame[i][axis] = value;
                    }
                }
                if (!frames.isEmpty() && frame.length != frames.get(0).length) {
                    throw new IOException("Inconsistent atom counts between frames: "
                            + frames.get(0).length + " vs " + frame.length);
                }
                frames.add(frame);
                if (frames.size() >= MAX_XYZ_FRAMES) {
                    throw new IOException("Frame bound exceeded (" + MAX_XYZ_FRAMES + "); "
                            + "truncate or decimate the trajectory first.");
                }
            }
        }
        return frames;
    }

    /** Binary convex hull from a phase CSV; the first data row is the evaluated candidate. */
    private static AnalysisReport analyzeHull(File source) throws IOException {
        String label = AnalysisKind.HULL_STABILITY.getLabel();
        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);
        QEHullThermodynamics hull = new QEHullThermodynamics();
        String targetFormula = null;
        double targetFraction = Double.NaN;
        double targetFormation = Double.NaN;
        int rejected = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("[,;]");
            if (columns.length < 3) {
                rejected++;
                continue;
            }
            String formula = columns[0].trim();
            double fraction;
            double formation;
            try {
                fraction = Double.parseDouble(normalizeExponent(columns[1].trim()));
                formation = Double.parseDouble(normalizeExponent(columns[2].trim()));
            } catch (NumberFormatException ex) {
                if (targetFormula == null && hull.getPhases().isEmpty()) {
                    continue; // header row before any data
                }
                rejected++;
                continue;
            }
            if (formula.isEmpty() || fraction < 0.0 || fraction > 1.0
                    || !Double.isFinite(formation)) {
                rejected++;
                continue;
            }
            if (targetFormula == null) {
                targetFormula = formula;
                targetFraction = fraction;
                targetFormation = formation;
            } else {
                hull.addPhase(formula, fraction, formation);
            }
        }
        if (targetFormula == null || hull.getPhases().isEmpty()) {
            return failure(label, "Need at least one candidate row and one competing-phase row: "
                    + "'formula,fraction_B,formation_energy_eV_per_atom'. Parsed target="
                    + (targetFormula == null ? "none" : targetFormula) + "; phases="
                    + hull.getPhases().size() + "; rejected rows=" + rejected + ".");
        }
        QEHullThermodynamics.StabilityResult result;
        try {
            result = hull.evaluateStability(targetFraction, targetFormation);
        } catch (IllegalArgumentException ex) {
            return failure(label, "Rejected target: " + ex.getMessage());
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Candidate: %s (x_B=%.4f, E_form=%.6f eV/atom) against %d competing phase(s)%n",
                targetFormula, targetFraction, targetFormation, hull.getPhases().size()));
        if (rejected > 0) {
            text.append("Rejected rows (malformed/out-of-range): ").append(rejected).append('\n');
        }
        text.append('\n').append(result.getSummary()).append('\n');
        text.append("\nFormation energies must all reference the same elemental chemical "
                + "potentials; the hull construction is binary (Monotone Chain) and does not "
                + "capture temperature/entropy contributions.");
        return new AnalysisReport(label, result.isStable(), text.toString(), List.of(), null);
    }

    private static AnalysisReport failure(String title, String message) {
        return new AnalysisReport(title, false, message, List.of(), null);
    }

    private static AnalysisReport analyzeBands(ProjectProperty property, File projectDir,
            String logFileName, File source, AnalysisParameters params) throws IOException {
        FermiReference fermi = resolveFermi(property, projectDir, logFileName, params.getFermiEv());
        QEBandsDataParser parser = new QEBandsDataParser(property);
        parser.parseWithFermi(source, fermi.valueEv);
        List<QEBandsDataParser.Band> bands = parser.getBands();
        if (bands.isEmpty()) {
            String diagnostics = parser.getDiagnostics().isEmpty()
                    ? "No monotonic band blocks were parsed." : String.join("\n", parser.getDiagnostics());
            return failure(AnalysisKind.BANDS_DATA.getLabel(), "Band parsing produced no bands in "
                    + source.getName() + ":\n" + diagnostics);
        }
        double minE = Double.POSITIVE_INFINITY;
        double maxE = Double.NEGATIVE_INFINITY;
        int kPoints = 0;
        List<String> csv = new ArrayList<>();
        csv.add("band_index,k_distance,dE_minus_reference_eV");
        for (int b = 0; b < bands.size(); b++) {
            QEBandsDataParser.Band band = bands.get(b);
            kPoints = Math.max(kPoints, band.size());
            double[] ks = band.getKDistance();
            double[] es = band.getEnergyEv();
            for (int i = 0; i < band.size(); i++) {
                minE = Math.min(minE, es[i]);
                maxE = Math.max(maxE, es[i]);
                csv.add(String.format(Locale.ROOT, "%d,%.8f,%.8f", b + 1, ks[i], es[i]));
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Bands: ").append(bands.size()).append("; max k-points per band: ")
                .append(kPoints).append('\n');
        text.append("Energy reference: ").append(fermi.description).append('\n');
        text.append(String.format(Locale.ROOT,
                "Energy range after referencing: %.6f to %.6f eV%n%n", minE, maxE));
        if (!parser.getDiagnostics().isEmpty()) {
            text.append("Parser notes:\n");
            for (String diagnostic : parser.getDiagnostics()) {
                text.append(" - ").append(diagnostic).append('\n');
            }
            text.append('\n');
        }
        text.append("Inspection only: no gap/directness claim is made from a path plot; "
                + "verify occupations and k-mesh convergence for quantitative use.");
        return new AnalysisReport(AnalysisKind.BANDS_DATA.getLabel(), true, text.toString(), csv, null);
    }

    /** Fermi energy is taken from the explicit override, stored property values, or a read-only log scan. */
    private static final class FermiReference {
        private final double valueEv;
        private final String description;

        FermiReference(double valueEv, String description) {
            this.valueEv = valueEv;
            this.description = description;
        }
    }

    private static FermiReference resolveFermi(ProjectProperty property, File projectDir,
            String logFileName, double explicitEv) {
        if (Double.isFinite(explicitEv)) {
            return new FermiReference(explicitEv, String.format(Locale.ROOT,
                    "%.6f eV (explicitly provided for this analysis)", explicitEv));
        }
        if (property != null) {
            ProjectEnergies stored = property.getFermiEnergies();
            if (stored != null && stored.numEnergies() > 0) {
                double value = stored.getEnergy(stored.numEnergies() - 1);
                if (Double.isFinite(value)) {
                    return new FermiReference(value, String.format(Locale.ROOT,
                            "%.6f eV (last Fermi energy stored by an earlier result parse)", value));
                }
            }
        }
        if (projectDir != null && logFileName != null) {
            File log = new File(projectDir, logFileName);
            if (log.isFile()) {
                try {
                    String tail = readTailUtf8(log.toPath(), LOG_SCAN_BYTES);
                    String found = null;
                    Matcher matcher = FERMI_LINE.matcher(tail);
                    while (matcher.find()) {
                        found = matcher.group(1);
                    }
                    if (found != null) {
                        double value = Double.parseDouble(normalizeExponent(found));
                        return new FermiReference(value, String.format(Locale.ROOT,
                                "%.6f eV (last 'Fermi energy' line in the project log tail)", value));
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Fall through to the unreferenced default below.
                }
            }
        }
        return new FermiReference(0.0,
                "0.0 (no stored Fermi energy was found; columns contain raw bands.x energies)");
    }

    static String normalizeExponent(String token) {
        return token == null ? "" : token.replace('D', 'E').replace('d', 'E');
    }

    static String readTailUtf8(Path path, long maximumBytes) throws IOException {
        long size = Files.size(path);
        long start = Math.max(0L, size - maximumBytes);
        int count = (int) (size - start);
        ByteBuffer bytes = ByteBuffer.allocate(count);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(start);
            while (bytes.hasRemaining() && channel.read(bytes) >= 0) {
                // Read until EOF or the requested bounded tail.
            }
        }
        String text = StandardCharsets.UTF_8.decode((ByteBuffer) bytes.flip()).toString();
        if (start > 0L) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline >= 0 ? text.substring(firstNewline + 1) : "";
        }
        return text;
    }

    private static AnalysisReport analyzeMagnetization(ProjectProperty property, File source)
            throws IOException {
        QEMagneticMomentParser parser = new QEMagneticMomentParser(property);
        parser.parse(source);
        List<QEMagneticMomentParser.AtomicMoment> moments = parser.getAtomicMoments();
        if (moments.isEmpty() && parser.getTotalMagnetizationBohr() == 0.0
                && parser.getAbsoluteMagnetizationBohr() == 0.0) {
            return failure(AnalysisKind.MAGNETIZATION.getLabel(),
                    "No magnetization records were found in " + source.getName()
                    + ". Confirm the calculation used spin polarization (nspin>=2 or noncollinear).");
        }
        double[] vector = parser.getTotalMagnetizationVector();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Noncollinear output detected: ").append(parser.isNoncollinear()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Total magnetization: %.5f Bohr mag/cell; absolute: %.5f Bohr mag/cell%n",
                parser.getTotalMagnetizationBohr(), parser.getAbsoluteMagnetizationBohr()));
        text.append(String.format(Locale.ROOT,
                "Total magnetization vector: [%.5f, %.5f, %.5f] Bohr mag/cell%n%n",
                vector[0], vector[1], vector[2]));
        List<String> csv = new ArrayList<>();
        csv.add("atom_index,mx_bohr_mag,my_bohr_mag,mz_bohr_mag,magnitude");
        int limit = Math.min(moments.size(), 100);
        for (int i = 0; i < limit; i++) {
            QEMagneticMomentParser.AtomicMoment moment = moments.get(i);
            text.append(String.format(Locale.ROOT, "atom %3d local moment: [% .5f % .5f % .5f]%n",
                    moment.getAtomIndex(), moment.getMx(), moment.getMy(), moment.getMz()));
            csv.add(String.format(Locale.ROOT, "%d,%.6f,%.6f,%.6f,%.6f", moment.getAtomIndex(),
                    moment.getMx(), moment.getMy(), moment.getMz(), moment.getMagnitude()));
        }
        if (moments.size() > limit) {
            text.append("Only the first ").append(limit).append(" atomic moments are shown.\n");
        }
        text.append("\nValues are raw pw.x records; ordering follows the log, and a converged "
                + "magnetic state is not implied by successful parsing.");
        return new AnalysisReport(AnalysisKind.MAGNETIZATION.getLabel(), true,
                text.toString(), csv, null);
    }

    private static AnalysisReport analyzeBornDielectric(ProjectProperty property, File source)
            throws IOException {
        QEBornChargeDielectricParser parser = new QEBornChargeDielectricParser(property);
        parser.parse(source);
        QEAcousticSumRuleValidator asr = new QEAcousticSumRuleValidator(property);
        asr.parse(source);
        List<QEBornChargeDielectricParser.BornCharge> charges = parser.getBornCharges();
        double[][] dielectric = parser.getDielectricTensor();
        if (charges.isEmpty() && dielectric == null) {
            return failure(AnalysisKind.BORN_DIELECTRIC.getLabel(),
                    "No Born effective-charge or dielectric blocks were found in "
                    + source.getName() + ". These tensors require a DFPT (ph.x eps=.true.) route.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        if (dielectric != null) {
            text.append("Electronic dielectric tensor (dimensionless):\n");
            appendMatrix(text, dielectric);
        }
        text.append("Born effective-charge tensors: ").append(charges.size()).append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("atom_index,z_xx,z_xy,z_xz,z_yx,z_yy,z_yz,z_zx,z_zy,z_zz");
        for (QEBornChargeDielectricParser.BornCharge charge : charges) {
            double[][] t = charge.getTensor();
            text.append("atom ").append(charge.getAtomIndex()).append(":\n");
            appendMatrix(text, t);
            csv.add(String.format(Locale.ROOT, "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    charge.getAtomIndex(), t[0][0], t[0][1], t[0][2], t[1][0], t[1][1], t[1][2],
                    t[2][0], t[2][1], t[2][2]));
        }
        text.append("\nBorn-charge acoustic sum rule satisfied: ")
                .append(parser.isAcsrPassed()).append('\n');
        text.append("Gamma acoustic-mode sum rule satisfied: ")
                .append(asr.isAsrCompliant()).append('\n');
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nTensor values are engine-unit records with symmetry/sum-rule checks; "
                + "they are not a substitute for k/q-mesh convergence evidence.");
        boolean ok = parser.isAcsrPassed() && (!charges.isEmpty() || dielectric != null);
        return new AnalysisReport(AnalysisKind.BORN_DIELECTRIC.getLabel(), ok,
                text.toString(), csv, null);
    }

    private static void appendMatrix(StringBuilder text, double[][] matrix) {
        for (double[] row : matrix) {
            text.append(String.format(Locale.ROOT, "   [% .5f % .5f % .5f]%n", row[0], row[1], row[2]));
        }
    }

    private static AnalysisReport analyzeHubbard(ProjectProperty property, File source)
            throws IOException {
        QEHubbardHpParser parser = new QEHubbardHpParser(property);
        parser.parse(source);
        List<QEHubbardHpParser.ParsedHubbardU> values = parser.getParsedParameters();
        if (values.isEmpty()) {
            return failure(AnalysisKind.HUBBARD_HP.getLabel(),
                    "No 'Hubbard U for atom ...' records were found in " + source.getName()
                    + ". Run hp.x (linear response) first and keep its standard output.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("atom_index,element,shell,u_ev");
        for (QEHubbardHpParser.ParsedHubbardU value : values) {
            text.append(String.format(Locale.ROOT, "atom %d (%s, %s shell): U = %.4f eV%n",
                    value.getAtomIndex(), value.getElement(), value.getShell(), value.getUValueEv()));
            csv.add(String.format(Locale.ROOT, "%d,%s,%s,%.6f", value.getAtomIndex(),
                    value.getElement(), value.getShell(), value.getUValueEv()));
        }
        String card = parser.generateHubbardCard();
        if (!card.isEmpty()) {
            text.append("\nSuggested HUBBARD card for subsequent pw.x runs (review before use):\n")
                    .append(card);
        }
        text.append("\nU values apply to the same structure/pseudopotential/projector set; "
                + "self-consistent U iteration history is reported by hp.x, not inferred here.");
        return new AnalysisReport(AnalysisKind.HUBBARD_HP.getLabel(), true,
                text.toString(), csv, null);
    }

    private static AnalysisReport analyzeTddft(ProjectProperty property, File source)
            throws IOException {
        QETurboSpectrumParser parser = new QETurboSpectrumParser(property);
        parser.parse(source);
        List<QETurboSpectrumParser.SpectrumPoint> points = parser.getSpectrumPoints();
        if (points.isEmpty()) {
            return failure(AnalysisKind.TDDFT_SPECTRUM.getLabel(),
                    "No turboSpectrum chi data rows were found in " + source.getName() + ".");
        }
        int peak = 0;
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).getIsotropicAbsorption() > points.get(peak).getIsotropicAbsorption()) {
                peak = i;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Spectrum points: ").append(points.size()).append('\n');
        text.append(String.format(Locale.ROOT, "Energy range: %.5f to %.5f eV%n",
                points.get(0).getEnergyEv(), points.get(points.size() - 1).getEnergyEv()));
        text.append(String.format(Locale.ROOT,
                "Largest isotropic absorption at %.5f eV (Im alpha average = %.6g)%n%n",
                points.get(peak).getEnergyEv(), points.get(peak).getIsotropicAbsorption()));
        List<String> csv = new ArrayList<>();
        csv.add("energy_ev,im_alpha_xx,im_alpha_yy,im_alpha_zz,isotropic_absorption");
        for (QETurboSpectrumParser.SpectrumPoint point : points) {
            csv.add(String.format(Locale.ROOT, "%.6f,%.8g,%.8g,%.8g,%.8g", point.getEnergyEv(),
                    point.getImAlphaXx(), point.getImAlphaYy(), point.getImAlphaZz(),
                    point.getIsotropicAbsorption()));
        }
        text.append("Raw turbo_lanczos/turbo_spectrum data are shown without extra broadening; "
                + "convergence with Lanczos iterations and empty states remains your responsibility.");
        return new AnalysisReport(AnalysisKind.TDDFT_SPECTRUM.getLabel(), true,
                text.toString(), csv, null);
    }

    private static AnalysisReport analyzeXanes(ProjectProperty property, File source)
            throws IOException {
        QEXSpectraXanesParser parser = new QEXSpectraXanesParser(property);
        parser.parse(source);
        List<QEXSpectraXanesParser.XanesPoint> points = parser.getSpectrum();
        if (points.isEmpty()) {
            return failure(AnalysisKind.XANES.getLabel(),
                    "No two-column XANES (energy, sigma) rows were found in " + source.getName() + ".");
        }
        int peak = 0;
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).getCrossSectionMb() > points.get(peak).getCrossSectionMb()) {
                peak = i;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Spectrum points: ").append(points.size()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Edge peak at %.5f eV with sigma = %.6g Mb%n%n",
                points.get(peak).getEnergyEv(), points.get(peak).getCrossSectionMb()));
        List<String> csv = new ArrayList<>();
        csv.add("energy_ev,sigma_mb");
        for (QEXSpectraXanesParser.XanesPoint point : points) {
            csv.add(String.format(Locale.ROOT, "%.6f,%.8g", point.getEnergyEv(),
                    point.getCrossSectionMb()));
        }
        text.append("Cross sections are raw xspectra.x values; polarization, broadening, and "
                + "core-hole convergence choices remain under user control.");
        return new AnalysisReport(AnalysisKind.XANES.getLabel(), true, text.toString(), csv, null);
    }

    private static AnalysisReport analyzeNmr(ProjectProperty property, File source)
            throws IOException {
        QEGipawNmrParser parser = new QEGipawNmrParser(property);
        parser.parse(source);
        List<QEGipawNmrParser.NmrShielding> shieldings = parser.getShieldings();
        if (shieldings.isEmpty()) {
            return failure(AnalysisKind.NMR_SHIELDING.getLabel(),
                    "No GIPAW shielding tensors were found in " + source.getName()
                    + ". A gipaw.x run with GIPAW-capable pseudopotentials is required.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("atom_index,element,sigma_iso_ppm,anisotropy_ppm,asymmetry");
        for (QEGipawNmrParser.NmrShielding shielding : shieldings) {
            text.append(String.format(Locale.ROOT,
                    "atom %d (%s): sigma_iso=%.4f ppm, anisotropy=%.4f ppm, eta=%.4f%n",
                    shielding.getAtomIndex(), shielding.getElement(), shielding.getIsotropicPpm(),
                    shielding.getAnisotropyPpm(), shielding.getAsymmetry()));
            csv.add(String.format(Locale.ROOT, "%d,%s,%.6f,%.6f,%.6f", shielding.getAtomIndex(),
                    shielding.getElement(), shielding.getIsotropicPpm(), shielding.getAnisotropyPpm(),
                    shielding.getAsymmetry()));
        }
        text.append("\nShielding-to-chemical-shift conversion needs an explicit reference; "
                + "no reference convention is applied silently here.");
        return new AnalysisReport(AnalysisKind.NMR_SHIELDING.getLabel(), true,
                text.toString(), csv, null);
    }

    private static AnalysisReport analyzePwcond(ProjectProperty property, File source)
            throws IOException {
        QEPwcondConductanceParser parser = new QEPwcondConductanceParser(property);
        parser.parse(source);
        List<QEPwcondConductanceParser.ConductancePoint> points = parser.getConductancePoints();
        if (points.isEmpty()) {
            return failure(AnalysisKind.PWCOND_TRANSMISSION.getLabel(),
                    "No transmission rows were found in " + source.getName() + ".");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Transmission points: ").append(points.size()).append('\n');
        text.append(String.format(Locale.ROOT, "Energy range: %.5f to %.5f eV%n%n",
                points.get(0).getEnergyEv(), points.get(points.size() - 1).getEnergyEv()));
        List<String> csv = new ArrayList<>();
        csv.add("energy_ev,transmission,conductance_g0");
        for (QEPwcondConductanceParser.ConductancePoint point : points) {
            csv.add(String.format(Locale.ROOT, "%.6f,%.8g,%.8g", point.getEnergyEv(),
                    point.getTransmission(), point.getConductanceInG0()));
        }
        text.append("Landauer conductance G = (2e^2/h) T(E); ballistic channels only, "
                + "verified lead/scattering-region setup remains required.");
        return new AnalysisReport(AnalysisKind.PWCOND_TRANSMISSION.getLabel(), true,
                text.toString(), csv, null);
    }

    private static AnalysisReport analyzeWannier90(ProjectProperty property, File source)
            throws IOException {
        QEWannier90SpreadParser parser = new QEWannier90SpreadParser(property);
        parser.parse(source);
        List<QEWannier90SpreadParser.WannierSpreadFrame> frames = parser.getConvergenceHistory();
        if (frames.isEmpty()) {
            return failure(AnalysisKind.WANNIER90_SPREAD.getLabel(),
                    "No 'CYCLE ... Spreads' records were found in " + source.getName() + ".");
        }
        QEWannier90SpreadParser.WannierSpreadFrame last = frames.get(frames.size() - 1);
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Minimization cycles parsed: ").append(frames.size()).append('\n');
        text.append(String.format(Locale.ROOT, "Final total spread: %.6f Ang^2; converged: %s%n",
                last.getTotalSpreadAng2(), parser.isConverged()));
        List<String> csv = new ArrayList<>();
        csv.add("cycle,total_spread_ang2");
        for (QEWannier90SpreadParser.WannierSpreadFrame frame : frames) {
            csv.add(String.format(Locale.ROOT, "%d,%.8f", frame.getCycle(), frame.getTotalSpreadAng2()));
        }
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nSpread convergence is a Wannierization-quality signal; “Wannier90-ready” "
                + "interpolation still needs a DFT-overlay error estimate before quantitative use.");
        return new AnalysisReport(AnalysisKind.WANNIER90_SPREAD.getLabel(),
                parser.isConverged(), text.toString(), csv, null);
    }

    private static AnalysisReport analyzeEos(ProjectProperty property, File source)
            throws IOException {
        QEThermoPwEosParser parser = new QEThermoPwEosParser(property);
        parser.parse(source);
        QEThermoPwEosParser.EosResult result = parser.getResult();
        if (!result.isSuccess()) {
            return failure(AnalysisKind.THERMOPW_EOS.getLabel(),
                    "No thermo_pw 'V0 = ..., B0 = ..., B'0 = ..., E0 = ...' summary with explicit "
                    + "volume units was found in " + source.getName() + ".");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT, "Equilibrium volume V0: %.6f Ang^3%n",
                result.getEquilibriumVolumeAng3()));
        text.append(String.format(Locale.ROOT, "Bulk modulus B0: %.4f GPa%n", result.getBulkModulusGpa()));
        text.append(String.format(Locale.ROOT, "Pressure derivative B'0: %.5f%n",
                result.getBulkModulusDerivative()));
        text.append(String.format(Locale.ROOT, "Minimum energy E0: %.8f Ry%n",
                result.getMinimumEnergyRy()));
        text.append(String.format(Locale.ROOT, "Self-check E(V0): %.8f Ry (must equal E0)%n%n",
                result.evaluateEnergyRy(result.getEquilibriumVolumeAng3())));
        text.append("Third-order Birch-Murnaghan parameters are evaluated in documented Ang^3/Ry "
                + "units; quasi-harmonic finite-temperature quantities are not derived here.");
        return new AnalysisReport(AnalysisKind.THERMOPW_EOS.getLabel(), true,
                text.toString(), List.of(), null);
    }

    private static AnalysisReport analyzeKappa(ProjectProperty property, File source)
            throws IOException {
        QEPhono3pyKappaParser parser = new QEPhono3pyKappaParser(property);
        parser.parse(source);
        List<QEPhono3pyKappaParser.ThermalConductivityPoint> points = parser.getKappaData();
        if (points.isEmpty()) {
            return failure(AnalysisKind.PHONO3PY_KAPPA.getLabel(),
                    "No phono3py 'Temp (K) kappa_xx ...' table was found in " + source.getName() + ".");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Temperature points: ").append(points.size()).append('\n');
        text.append("1/T single-mode relaxation-time scaling check: ")
                .append(parser.isPhysicalScaling() ? "consistent" : "VIOLATED - review anharmonic model")
                .append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("temperature_k,kappa_xx_w_mk,kappa_yy_w_mk,kappa_zz_w_mk,kappa_iso_w_mk");
        for (QEPhono3pyKappaParser.ThermalConductivityPoint point : points) {
            csv.add(String.format(Locale.ROOT, "%.2f,%.6f,%.6f,%.6f,%.6f", point.getTemperatureK(),
                    point.getKappaXx(), point.getKappaYy(), point.getKappaZz(),
                    point.getIsotropicKappa()));
        }
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nTensor values are the raw phono3py records; mesh/supercell/isotope/scattering "
                + "convergence evidence is not inferred from this table.");
        return new AnalysisReport(AnalysisKind.PHONO3PY_KAPPA.getLabel(),
                parser.isPhysicalScaling(), text.toString(), csv, null);
    }

    private static AnalysisReport analyzeTc(ProjectProperty property, File source, AnalysisParameters params)
            throws IOException {
        QEEliashbergTcCalculator calculator = new QEEliashbergTcCalculator(property);
        calculator.parse(source);
        List<QEEliashbergTcCalculator.EliashbergPoint> function = calculator.getSpectralFunction();
        if (function.size() < 2) {
            return failure(AnalysisKind.ELIASHBERG_TC.getLabel(),
                    "Fewer than two alpha2F(frequency) rows were found in " + source.getName()
                    + "; Tc is not estimated from incomplete data.");
        }
        double muStar = params.getMuStar();
        if (!Double.isFinite(muStar) || muStar < 0.0 || muStar > 0.3) {
            return failure(AnalysisKind.ELIASHBERG_TC.getLabel(),
                    "mu* must be a finite number in [0, 0.3]; received " + muStar
                    + ". No Tc was estimated.");
        }
        QEEliashbergTcCalculator.TcResult result = calculator.calculateTc(muStar);
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(result.getSummary()).append("\n\n");
        List<String> csv = new ArrayList<>();
        csv.add("frequency_cm1,alpha2f");
        for (QEEliashbergTcCalculator.EliashbergPoint point : function) {
            csv.add(String.format(Locale.ROOT, "%.6f,%.8g", point.frequencyCm1, point.alpha2F));
        }
        text.append("Tc comes from the McMillan/Allen-Dynes formula on the parsed alpha2F only; "
                + "converged q/k meshes and a justified mu* are required before publication use.");
        boolean finiteTc = Double.isFinite(result.getTcKelvin()) && result.getTcKelvin() >= 0.0;
        return new AnalysisReport(AnalysisKind.ELIASHBERG_TC.getLabel(), finiteTc,
                text.toString(), csv, null);
    }

    private static AnalysisReport analyzeInputPreview(AnalysisKind kind, String prefix,
            File projectDir, AnalysisParameters params) {
        String safePrefix = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();
        String outdir = ".";
        StringBuilder text = new StringBuilder();
        String input;
        if (kind == AnalysisKind.PP_WAVEFUNCTION_INPUT) {
            if (params.getKpointIndex() <= 0 || params.getBandIndex() <= 0) {
                return failure(kind.getLabel(),
                        "k-point and band indices must be positive (1-based). No input was generated.");
            }
            if (params.getSpinComponent() < 0 || params.getSpinComponent() > 2) {
                return failure(kind.getLabel(),
                        "Spin component must be 0 (unpolarized), 1, or 2. No input was generated.");
            }
            QEPpWavefunctionBuilder builder = new QEPpWavefunctionBuilder(safePrefix, outdir,
                    params.getKpointIndex(), params.getBandIndex(), params.getSpinComponent(),
                    params.isLsign(), safePrefix + "-psi.cube");
            input = builder.generateInput();
            text.append("pp.x wavefunction input (plot_num=7):\n");
            text.append(String.format(Locale.ROOT,
                    "k-point %d, band %d, spin component %d, lsign=%s%n%n",
                    builder.getKpointIndex(), builder.getBandIndex(), builder.getSpinComponent(),
                    builder.isLsign()));
        } else {
            boolean potential = kind == AnalysisKind.PP_POTENTIAL_INPUT;
            QEPpChargePotentialBuilder builder = new QEPpChargePotentialBuilder(safePrefix, outdir,
                    potential, safePrefix + (potential ? "-potential.cube" : "-charge.cube"));
            input = builder.generateInput();
            text.append(potential
                    ? "pp.x electrostatic-potential input (plot_num=11):\n\n"
                    : "pp.x charge-density input (plot_num=0):\n\n");
        }
        text.append(input).append('\n');
        text.append("\nThis is a preview synthesized from the project prefix with outdir='.'; "
                + "no file is written unless you explicitly save it, and the pp.x run itself is "
                + "not launched by this viewer.");
        return new AnalysisReport(kind.getLabel(), true, text.toString(), List.of(), input);
    }

    /** Berry-phase polarization: raw records plus per-direction polarization quanta. */
    private static AnalysisReport analyzeBerry(Project project) {
        String label = AnalysisKind.BERRY_POLARIZATION.getLabel();
        File directory = project.getDirectory();
        String logName = project.getLogFileName();
        if (directory == null || logName == null) {
            return failure(label, "The project has no log file context for Berry-phase parsing.");
        }
        File log = new File(directory, logName);
        if (!log.isFile()) {
            return failure(label, "No project log with Berry-phase output records was found: "
                    + log.getPath());
        }
        QEBerryPolarizationParser parser = new QEBerryPolarizationParser(project.getProperty());
        try {
            parser.parse(log);
        } catch (IOException ex) {
            return failure(label, "Could not parse Berry-phase output: " + ex.getMessage());
        }
        double ionic = parser.getIonicPolarizationBohr();
        double electronic = parser.getElectronicPolarizationBohr();
        double total = parser.getTotalPolarizationBohr();
        if (ionic == 0.0 && electronic == 0.0 && total == 0.0) {
            return failure(label, "No Berry-phase polarization records were found in "
                    + log.getName() + ". A supported pw.x Berry workflow (lberry/.true. or "
                    + "gipaw/polarization path) is required.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(log.getName()).append('\n');
        text.append(String.format(Locale.ROOT, "Ionic polarization:     %.6f (Bohr units)%n", ionic));
        text.append(String.format(Locale.ROOT, "Electronic polarization: %.6f (Bohr units)%n", electronic));
        text.append(String.format(Locale.ROOT, "Total polarization:      %.6f (Bohr units)%n%n", total));
        Cell cell = project.getCell();
        if (cell != null && cell.copyLattice() != null) {
            text.append("Polarization quantum along lattice directions (SI, C/m^2):\n");
            for (int direction = 0; direction < 3; direction++) {
                double quantum = parser.calculatePolarizationQuantumSI(cell, direction);
                text.append(String.format(Locale.ROOT, "  direction %d: %.8g%n",
                        direction + 1, quantum));
            }
        } else {
            text.append("No periodic cell is available; polarization quanta were not computed.\n");
        }
        text.append("\nOnly polarization CHANGES between states are physically meaningful; "
                + "absolute branches are undefined modulo the polarization quantum. No "
                + "two-state unwrapping is applied in this single-log analysis.");
        boolean ok = Double.isFinite(ionic) && Double.isFinite(electronic) && Double.isFinite(total);
        return new AnalysisReport(label, ok, text.toString(), List.of(), null);
    }

    /** Planar-averaged potential -> plateau diagnostic -> work functions on both terminations. */
    private static AnalysisReport analyzeWorkFunction(ProjectProperty property, File projectDir,
            String logFileName, File source, AnalysisParameters params) throws IOException {
        String label = AnalysisKind.WORK_FUNCTION.getLabel();
        List<Double> zValues = new ArrayList<>();
        List<Double> potentials = new ArrayList<>();
        for (String raw : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("@")) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) {
                continue;
            }
            try {
                double z = Double.parseDouble(normalizeExponent(tokens[0]));
                double v = Double.parseDouble(normalizeExponent(tokens[1]));
                if (Double.isFinite(z) && Double.isFinite(v)) {
                    zValues.add(z);
                    potentials.add(v);
                }
            } catch (NumberFormatException ex) {
                // Non-numeric header rows are skipped; data rows keep the parser honest.
            }
        }
        if (zValues.size() < 3) {
            return failure(label, "Fewer than three (z, V) rows were parsed from "
                    + source.getName() + ". A tavg.x/pp.x planar-averaged potential file is required.");
        }
        double dz = zValues.get(1) - zValues.get(0);
        if (!(dz > 0.0) || !Double.isFinite(dz)) {
            return failure(label, "The z grid is not strictly increasing; a uniform slab-grid "
                    + "potential cannot be assumed.");
        }
        for (int i = 2; i < zValues.size(); i++) {
            double spacing = zValues.get(i) - zValues.get(i - 1);
            if (Math.abs(spacing - dz) > 1.0e-6 * Math.max(1.0, dz)) {
                return failure(label, "Non-uniform z spacing at row " + (i + 1) + " (dz="
                        + spacing + " vs " + dz + "); the plateau analysis requires a uniform grid.");
            }
        }
        double[] v = new double[potentials.size()];
        for (int i = 0; i < potentials.size(); i++) {
            v[i] = potentials.get(i);
        }
        FermiReference fermi = resolveFermi(property, projectDir, logFileName, params.getFermiEv());
        QESlabPlateauDiagnostic.PlateauResult result = QESlabPlateauDiagnostic.analyzePotential(
                v, dz, fermi.valueEv, 1.0e-3);
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append("; samples: ").append(v.length)
                .append(String.format(Locale.ROOT, "; dz=%.6f Ang%n", dz));
        text.append("Fermi reference: ").append(fermi.description).append('\n');
        text.append("Local-slope plateau tolerance: 1.0e-3 (analysis dialog choice)\n\n");
        if (!result.isPlateauFound()) {
            text.append("No stable vacuum plateau was identified; work functions are not reported "
                    + "without a plateau.\n");
        } else {
            text.append(String.format(Locale.ROOT, "Left vacuum level:  %.6f eV%n",
                    result.getLeftVacuumLevel()));
            text.append(String.format(Locale.ROOT, "Right vacuum level: %.6f eV%n",
                    result.getRightVacuumLevel()));
            text.append(String.format(Locale.ROOT, "Dipole step:        %.6f eV%n",
                    result.getDipoleStep()));
            text.append(String.format(Locale.ROOT, "Left work function:  %.6f eV%n",
                    result.getLeftWorkFunction()));
            text.append(String.format(Locale.ROOT, "Right work function: %.6f eV%n",
                    result.getRightWorkFunction()));
        }
        for (String warning : result.getWarnings()) {
            text.append(" ! ").append(warning).append('\n');
        }
        text.append("\nWork function follows Phi = V_vac - E_F on each side; slab thickness and "
                + "vacuum-size convergence remain the user's responsibility.");
        return new AnalysisReport(label, result.isPlateauFound(), text.toString(), List.of(), null);
    }

    /** cp.x fictitious/electronic/ionic energy trajectory with adiabaticity flag and CSV. */
    private static AnalysisReport analyzeCpTrajectory(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.CP_TRAJECTORY.getLabel();
        QECarParrinelloParser parser = new QECarParrinelloParser(property);
        parser.parse(source);
        List<QECarParrinelloParser.CpMdFrame> frames = parser.getTrajectory();
        if (frames.isEmpty()) {
            return failure(label, "No 'nfi=..., ekinc=..., ekinh=..., etot=...' rows were found in "
                    + source.getName() + ". A dedicated cp.x run (not pw.x 'cp' mode) is required.");
        }
        QECarParrinelloParser.CpMdFrame last = frames.get(frames.size() - 1);
        double maxEkinc = 0.0;
        List<String> csv = new ArrayList<>();
        csv.add("step,ekinc_au,ekinh_au,etot_au");
        for (QECarParrinelloParser.CpMdFrame frame : frames) {
            maxEkinc = Math.max(maxEkinc, Math.abs(frame.getEkincAu()));
            csv.add(String.format(Locale.ROOT, "%d,%.8g,%.8g,%.8g", frame.getStep(),
                    frame.getEkincAu(), frame.getEkinhAu(), frame.getEtotAu()));
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("MD steps parsed: ").append(frames.size()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Last step %d: etot=%.8f au, ekinh=%.8f au, ekinc=%.8f au%n",
                last.getStep(), last.getEtotAu(), last.getEkinhAu(), last.getEkincAu()));
        text.append(String.format(Locale.ROOT, "Largest |ekinc| along trajectory: %.8g au%n",
                maxEkinc));
        text.append("Adiabaticity flag from parser heuristics: ").append(parser.isAdiabatic())
                .append('\n');
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nSmall, stable ekinc is necessary but not sufficient for Car-Parrinello "
                + "adiabatic separation; fictitious mass and timestep convergence are not "
                + "validated by this report.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Collinear/noncollinear magnetic-order and Shubnikov guess from site-moment properties. */
    private static AnalysisReport analyzeMagneticOrder(Project project) {
        String label = AnalysisKind.MAGNETIC_ORDER.getLabel();
        Cell cell = project.getCell();
        if (cell == null) {
            return failure(label, "The project has no atomic cell to classify.");
        }
        MagneticSpaceGroupDetector detector = new MagneticSpaceGroupDetector(cell);
        MagneticSpaceGroupDetector.MsgReport report = detector.analyzeMagneticSymmetry();
        StringBuilder text = new StringBuilder();
        text.append("Magnetic order: ").append(report.getOrder()).append('\n');
        text.append("Shubnikov type guess: ").append(report.getShubnikovTypeGuess()).append('\n');
        text.append(String.format(Locale.ROOT, "Net moment: %.6f; sum of absolute moments: %.6f%n",
                report.getNetMoment(), report.getAbsoluteMomentSum()));
        text.append('\n').append(report.getDescription()).append('\n');
        text.append("\nClassification uses per-atom starting_magnetization/magnetic_moment "
                + "properties (tolerance 1e-2/5e-2 Bohr mag). This is not a spglib magnetic "
                + "space-group determination; unitary/antiunitary operation analysis requires "
                + "the magnetic symmetry sidecar.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** Detects used workflows from project artifacts and compiles the exact BibTeX bundle. */
    private static AnalysisReport analyzeCitations(Project project) {
        String label = AnalysisKind.CITATIONS.getLabel();
        boolean phonons = false;
        boolean thermo = false;
        boolean wannier = false;
        File directory = project.getDirectory();
        if (directory != null) {
            File[] files = directory.listFiles(File::isFile);
            if (files != null) {
                for (File candidate : files) {
                    String name = candidate.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("matdyn") || name.endsWith(".freq") || name.endsWith(".freq.gp")
                            || name.startsWith("ph.")) {
                        phonons = true;
                    }
                    if (name.contains("thermo") || name.contains("eos")) {
                        thermo = true;
                    }
                    if (name.endsWith(".wout")) {
                        wannier = true;
                    }
                }
            }
        }
        QECitationManager manager = new QECitationManager();
        manager.registerFeatureCitations(phonons, thermo, wannier, false);
        String bibtex = manager.compileBibTex();
        if (bibtex == null || bibtex.isBlank()) {
            return failure(label, "No citations were registered; nothing to compile.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Citation keys registered for this project: ")
                .append(manager.getActiveCitationKeys()).append('\n');
        text.append("Detected workflow artifacts - phonons: ").append(phonons)
                .append("; thermo_pw: ").append(thermo)
                .append("; Wannier90: ").append(wannier).append('\n');
        text.append("Pseudopotential-family citations are NOT auto-registered without a verified "
                + "library manifest; add the exact SSSP/PSLibrary citation manually when used.\n\n");
        text.append("The BibTeX below can be saved explicitly from this dialog.");
        return new AnalysisReport(label, true, text.toString(), List.of(), bibtex);
    }

    /** Bounded CUBE header/data inspection with density-unit honesty; nothing is written. */
    private static AnalysisReport analyzeCube(File source) {
        String label = AnalysisKind.CUBE_INSPECT.getLabel();
        OperationResult<QEGridDensityDifference.Grid3D> result = CubeGridReader.read(source.toPath());
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            return failure(label, result.getMessage());
        }
        QEGridDensityDifference.Grid3D grid = result.getValue().orElseThrow();
        double[][][] values = grid.getValues();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        long count = 0L;
        for (double[][] plane : values) {
            for (double[] row : plane) {
                for (double value : row) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    sum += value;
                    count++;
                }
            }
        }
        double volume = grid.getVolume();
        double integral = grid.integrate();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Grid: ").append(grid.getNx()).append(" x ").append(grid.getNy())
                .append(" x ").append(grid.getNz()).append(" voxels (bounded reader \u2264 16 Mi)\n");
        text.append("Lattice (Ang):\n");
        appendMatrix(text, grid.getLattice());
        text.append(String.format(Locale.ROOT, "Cell volume: %.6f Ang^3%n", volume));
        if (count > 0L) {
            text.append(String.format(Locale.ROOT,
                    "Voxel values: min=%.8g max=%.8g mean=%.8g%n", min, max, sum / count));
            text.append(String.format(Locale.ROOT,
                    "Grid integral (trapezoidal voxel sum * dv): %.8g%n", integral));
        }
        text.append("\nValue units follow the CUBE payload (typically bohr^-3 for charge "
                + "density); an electron-count claim additionally requires the explicit density "
                + "convention and is not made here.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** SCF iteration history from a bounded log tail: energies, accuracies, trend, verdict. */
    private static AnalysisReport analyzeScfConvergence(File source) throws IOException {
        String label = AnalysisKind.SCF_CONVERGENCE.getLabel();
        String tail = readTailUtf8(source.toPath(), LOG_SCAN_BYTES);
        ScfConvergenceAnalyzer.Report report = ScfConvergenceAnalyzer.analyze(tail);
        List<ScfIterationRecord> iterations = report.getIterations();
        if (iterations.isEmpty()) {
            return failure(label, "No pw.x 'total energy' iteration lines were found in the last "
                    + (LOG_SCAN_BYTES / (1024L * 1024L)) + " MiB of " + source.getName()
                    + ". A pw.x electronic (scf/nscf/relax) log is required.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append(" (bounded ")
                .append(LOG_SCAN_BYTES / (1024L * 1024L)).append(" MiB tail scan)\n");
        text.append("Iterations parsed: ").append(iterations.size()).append('\n');
        text.append("Converged marker ('! total energy') found: ").append(report.isConverged())
                .append('\n');
        text.append("Explicit 'convergence NOT achieved': ")
                .append(report.isExplicitlyNotConverged()).append('\n');
        text.append("Accuracy trend classification: ").append(report.getTrend()).append('\n');
        if (report.getFinalEnergyRy() != null) {
            text.append(String.format(Locale.ROOT, "Final total energy: %.8f Ry (%.6f eV)%n",
                    report.getFinalEnergyRy(), report.getFinalEnergyRy() * 13.605693122994));
        }
        if (report.getFinalAccuracyRy() != null) {
            text.append(String.format(Locale.ROOT, "Final estimated SCF accuracy: %.8g Ry%n",
                    report.getFinalAccuracyRy()));
        }
        List<String> csv = new ArrayList<>();
        csv.add("iteration,total_energy_Ry,estimated_accuracy_Ry");
        for (ScfIterationRecord record : iterations) {
            csv.add(String.format(Locale.ROOT, "%d,%.8f,%s", record.getIteration(),
                    record.getTotalEnergyRy(),
                    record.getEstimatedAccuracyRy() == null ? ""
                            : String.format(Locale.ROOT, "%.8g", record.getEstimatedAccuracyRy())));
        }
        text.append("\nThe tail scan is bounded; long relax/vc-relax logs may hold several SCF "
                + "loops and the trend is computed on the parsed stream as-is. A parsed "
                + "iteration history is not by itself evidence that every geometry step "
                + "converged; the explicit markers above carry that information.");
        boolean success = report.isConverged() && !report.isExplicitlyNotConverged();
        return new AnalysisReport(label, success, text.toString(), csv, null);
    }

    /** pw.x MPI/FFT/memory/timing records with per-rank efficiency derivation and caveats. */
    private static AnalysisReport analyzeTiming(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.TIMING_PROFILE.getLabel();
        QETimingResourceParser parser = new QETimingResourceParser(property);
        parser.parse(source);
        double cpu = parser.getCpuTimeSeconds();
        double wall = parser.getWallTimeSeconds();
        double memoryMb = parser.getEstimatedMaxMemoryMb();
        int processors = parser.getNumProcessors();
        if (!Double.isFinite(cpu) && !Double.isFinite(wall) && processors <= 0
                && !Double.isFinite(memoryMb)) {
            return failure(label, "No pw.x timing, MPI, memory, or FFT records were found in "
                    + source.getName() + ". A pw.x output log is required.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("MPI processors: ")
                .append(processors > 0 ? String.valueOf(processors) : "not reported")
                .append('\n');
        text.append("FFT grid: ")
                .append("0 x 0 x 0".equals(parser.getFftGrid()) ? "not reported"
                        : parser.getFftGrid())
                .append('\n');
        text.append("Estimated max memory: ")
                .append(Double.isFinite(memoryMb)
                        ? String.format(Locale.ROOT, "%.2f MB", memoryMb) : "not reported")
                .append('\n');
        if (Double.isFinite(cpu)) {
            text.append(String.format(Locale.ROOT, "CPU time:  %.2f s%n", cpu));
        }
        if (Double.isFinite(wall)) {
            text.append(String.format(Locale.ROOT, "Wall time: %.2f s%n", wall));
        }
        if (Double.isFinite(cpu) && Double.isFinite(wall) && wall > 0.0) {
            text.append(String.format(Locale.ROOT,
                    "CPU/WALL ratio: %.4f (values far above 1 reflect multi-core CPU accounting)%n",
                    cpu / wall));
            if (processors > 0) {
                text.append(String.format(Locale.ROOT,
                        "Derived CPU-time-per-rank utilization: %.1f %%%n",
                        100.0 * cpu / (wall * processors)));
            }
        }
        text.append("\nThe memory figure is pw.x's own heuristic estimate, not a measurement; "
                + "scaling conclusions need a processor-count series, not a single run.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** Smearing -TS record with per-atom degauss safety verdicts for both metallic assumptions. */
    private static AnalysisReport analyzeSmearing(ProjectProperty property, File source,
            AnalysisParameters params) throws IOException {
        String label = AnalysisKind.SMEARING_ANALYSIS.getLabel();
        int natoms = params.getAtomCount();
        if (natoms < 1) {
            return failure(label, "The per-atom entropy check needs the number of atoms in the "
                    + "simulation cell (got " + natoms + ").");
        }
        QESmearingConvergenceAnalyzer analyzer = new QESmearingConvergenceAnalyzer(property);
        analyzer.parse(source);
        if (!analyzer.isSmearingFound()) {
            return failure(label, "No smearing entropy (-TS / demet) records were found in "
                    + source.getName() + ". The check applies to occupations='smearing' runs "
                    + "(a Methfessel-Paxton / Marzari-Vanderbilt / Gaussian SCF log).");
        }
        List<String> base = analyzer.getDiagnostics();
        boolean insulatorSafe = analyzer.verifySmearingSafe(natoms, false);
        List<String> afterInsulator = analyzer.getDiagnostics();
        boolean metalSafe = analyzer.verifySmearingSafe(natoms, true);
        List<String> afterMetal = analyzer.getDiagnostics();

        double perAtomRy = Math.abs(analyzer.getEntropyRy()) / natoms;
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT, "Total energy:        %.8f Ry%n",
                analyzer.getTotalEnergyRy()));
        text.append(String.format(Locale.ROOT, "Smearing -TS:        %.8f Ry%n",
                analyzer.getEntropyRy()));
        text.append(String.format(Locale.ROOT, "Total free energy:   %.8f Ry%n",
                analyzer.getFreeEnergyRy()));
        text.append(String.format(Locale.ROOT,
                "|-TS| per atom (N=%d): %.8f Ry = %.4f meV/atom%n%n", natoms, perAtomRy,
                perAtomRy * 13605.693122994));
        for (String diagnostic : base) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("If the system is an insulator/semiconductor: ")
                .append(insulatorSafe ? "SAFE" : "WARNING").append('\n');
        for (int i = base.size(); i < afterInsulator.size(); i++) {
            text.append("   ").append(afterInsulator.get(i)).append('\n');
        }
        text.append("If the system is metallic: ")
                .append(metalSafe ? "SAFE" : "WARNING").append('\n');
        for (int i = afterInsulator.size(); i < afterMetal.size(); i++) {
            text.append("   ").append(afterMetal.get(i)).append('\n');
        }
        text.append("\nSafety limits: |-TS|/atom > 0.001 Ry (~13.6 meV/atom) biases forces; a "
                + "near-zero -TS in a metal risks SCF oscillations. This single-point check "
                + "does not replace a degauss/smearing-scheme convergence series "
                + "(roadmap #38).");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** Harmonic ZPE/F/U/S/Cv from a two-column phonon DOS with explicit mode-count honesty. */
    private static AnalysisReport analyzePhononDosThermo(File source, AnalysisParameters params)
            throws IOException {
        String label = AnalysisKind.PHONON_DOS_THERMO.getLabel();
        double temperature = params.getTemperatureK();
        if (!(temperature > 0.0) || !Double.isFinite(temperature)) {
            return failure(label, "A positive finite temperature in kelvin is required (got "
                    + temperature + ").");
        }
        List<Double> frequencies = new ArrayList<>();
        List<Double> dosValues = new ArrayList<>();
        int rejected = 0;
        for (String raw : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("@")
                    || line.startsWith("!")) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) {
                rejected++;
                continue;
            }
            try {
                double frequency = Double.parseDouble(normalizeExponent(tokens[0]));
                double dos = Double.parseDouble(normalizeExponent(tokens[1]));
                if (Double.isFinite(frequency) && Double.isFinite(dos)) {
                    frequencies.add(frequency);
                    dosValues.add(dos);
                } else {
                    rejected++;
                }
            } catch (NumberFormatException ex) {
                rejected++;
            }
        }
        if (frequencies.size() < 2) {
            return failure(label, "Fewer than two (frequency, DOS) rows were parsed from "
                    + source.getName() + ". A matdyn.x/ph.x-derived DOS file in cm^-1 is "
                    + "required.");
        }
        double[] frequency = new double[frequencies.size()];
        double[] dos = new double[dosValues.size()];
        for (int i = 0; i < frequency.length; i++) {
            frequency[i] = frequencies.get(i);
            dos[i] = dosValues.get(i);
        }
        OperationResult<PhononDosThermodynamics.Result> integrated =
                PhononDosThermodynamics.integrate(frequency, dos, temperature);
        if (!integrated.isSuccess() || integrated.getValue().isEmpty()) {
            return failure(label, "Phonon DOS validation failed for " + source.getName()
                    + ": " + integrated.getMessage());
        }
        PhononDosThermodynamics.Result result = integrated.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append("; grid samples: ")
                .append(frequency.length);
        if (rejected > 0) {
            text.append("; rejected rows: ").append(rejected);
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT, "Frequency grid: %.4f .. %.4f cm^-1%n",
                frequency[0], frequency[frequency.length - 1]));
        text.append(String.format(Locale.ROOT, "Temperature: %.2f K%n", result.getTemperatureK()));
        text.append(String.format(Locale.ROOT, "Integrated DOS (mode count): %.6f%n",
                result.getIntegratedDos()));
        text.append(String.format(Locale.ROOT, "Zero-point energy:      %.8f eV%n",
                result.getZeroPointEnergyEv()));
        text.append(String.format(Locale.ROOT, "Helmholtz free energy: %.8f eV%n",
                result.getHelmholtzFreeEnergyEv()));
        text.append(String.format(Locale.ROOT, "Internal energy:       %.8f eV%n",
                result.getInternalEnergyEv()));
        text.append(String.format(Locale.ROOT, "Entropy:               %.8f eV/K%n",
                result.getEntropyEvPerK()));
        text.append(String.format(Locale.ROOT, "Heat capacity Cv:      %.8f eV/K = %.4f J/(mol K)%n",
                result.getHeatCapacityEvPerK(), result.getHeatCapacityEvPerK() * 96485.33212));
        text.append('\n').append(result.getNotes()).append('\n');
        text.append("\nThe integrated DOS should equal 3*natoms (3*natoms-3 without acoustic "
                + "modes) for a normalized phonon DOS; comparing it against your cell remains "
                + "your check. Values are harmonic (no thermal expansion) and sensitive to the "
                + "low-frequency grid near the acoustic branches.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** thermo_pw 6x6 elastic tensor with Sylvester Born-stability verdict and units caveat. */
    private static AnalysisReport analyzeElastic(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.ELASTIC_STABILITY.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " is " + (size / (1024L * 1024L))
                    + " MiB; pass the thermo_pw output containing the 'Elastic Constant "
                    + "Matrix' block (or an extract of it), bounded to 64 MiB.");
        }
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        if (!content.contains("Elastic Constant Matrix")) {
            return failure(label, "No 'Elastic Constant Matrix' block exists in "
                    + source.getName() + ". A thermo_pw elastic-constants output is required.");
        }
        ElasticParser parser = new ElasticParser(property);
        parser.parse(source);
        double[][] cij = parser.getCij();
        QEElasticStabilityValidator.StabilityResult stability = parser.getStabilityResult();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Elastic constant matrix Cij (units as printed; thermo_pw defaults to "
                + "kbar - divide by 10 for GPa, and verify the header lines before the "
                + "block):\n");
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                text.append(String.format(Locale.ROOT, "%12.6g", cij[i][j]));
            }
            text.append('\n');
        }
        text.append('\n');
        if (stability != null) {
            text.append("Mechanically stable (Sylvester leading-minors criterion): ")
                    .append(stability.isMechanicallyStable()).append('\n');
            for (String diagnostic : stability.getDiagnostics()) {
                text.append(" - ").append(diagnostic).append('\n');
            }
        }
        text.append("\nPositive-definiteness of the symmetrized Cij is the Born necessary "
                + "condition; it does not prove dynamical (phonon) or thermodynamic stability. "
                + "Convention and units stay exactly as thermo_pw printed them.");
        boolean success = stability == null || stability.isMechanicallyStable();
        return new AnalysisReport(label, success, text.toString(), List.of(), null);
    }

    /** LAMMPS default-thermo trajectory with averages, drift, and a full CSV export. */
    private static AnalysisReport analyzeLammpsThermo(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.LAMMPS_THERMO.getLabel();
        QELammpsThermoParser parser = new QELammpsThermoParser(property);
        parser.parse(source);
        List<QELammpsThermoParser.ThermoStep> steps = parser.getSteps();
        if (steps.isEmpty()) {
            return failure(label, "No 'Step Temp Press PotEng KinEng TotEng' thermo rows were "
                    + "found in " + source.getName() + ". A LAMMPS log with the default "
                    + "thermo_style columns is required.");
        }
        QELammpsThermoParser.ThermoStep first = steps.get(0);
        QELammpsThermoParser.ThermoStep last = steps.get(steps.size() - 1);
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Thermo steps parsed: ").append(steps.size()).append('\n');
        text.append(String.format(Locale.ROOT,
                "First step %d: TotEng=%.6f, Temp=%.2f K, Press=%.4f%n", first.getStep(),
                first.getTotalEnergyEv(), first.getTemperatureK(), first.getPressureBar()));
        text.append(String.format(Locale.ROOT,
                "Last step %d:  TotEng=%.6f, Temp=%.2f K, Press=%.4f%n", last.getStep(),
                last.getTotalEnergyEv(), last.getTemperatureK(), last.getPressureBar()));
        text.append(String.format(Locale.ROOT,
                "Total-energy drift over the run: %+.6f (energy units)%n",
                last.getTotalEnergyEv() - first.getTotalEnergyEv()));
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        List<String> csv = new ArrayList<>();
        csv.add("step,temp_K,press_bar,poteng,kineng,toteng");
        for (QELammpsThermoParser.ThermoStep step : steps) {
            csv.add(String.format(Locale.ROOT, "%d,%.4f,%.6f,%.8f,%.8f,%.8f", step.getStep(),
                    step.getTemperatureK(), step.getPressureBar(), step.getPotentialEnergyEv(),
                    step.getKineticEnergyEv(), step.getTotalEnergyEv()));
        }
        text.append("\nColumn units follow the LAMMPS 'units' command of the producing input "
                + "(the parser labels the common eV/bar convention of metal units); verify "
                + "against your log's 'units' line, which this report does not parse. The "
                + "drift sign carries no equilibration guarantee.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Relax criteria from BFGS/force markers + user thresholds; "optimized" is earned only. */
    private static AnalysisReport analyzeGeometryConvergence(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.GEOMETRY_CONVERGENCE.getLabel();
        File directory = project.getDirectory();
        String logName = project.getLogFileName();
        if (directory == null || logName == null) {
            return failure(label, "The project has no log file context for relax validation.");
        }
        File log = new File(directory, logName);
        if (!log.isFile()) {
            return failure(label, "No project log was found: " + log.getPath());
        }
        double forceThreshold = params.getForceThresholdRyBohr();
        if (!(forceThreshold > 0.0) || !Double.isFinite(forceThreshold)) {
            return failure(label, "The total-force threshold must be a positive finite value "
                    + "in Ry/bohr (got " + forceThreshold + ").");
        }
        Double pressureThreshold = Double.isFinite(params.getPressureThresholdKbar())
                ? params.getPressureThresholdKbar() : null;
        String tail;
        try {
            tail = readTailUtf8(log.toPath(), LOG_SCAN_BYTES);
        } catch (IOException ex) {
            return failure(label, "Could not read the project log: " + ex.getMessage());
        }
        ProjectGeometryList geometries = project.getProperty() == null ? null
                : project.getProperty().getOptList();
        GeometryConvergenceValidator.Result result = GeometryConvergenceValidator.validate(
                tail, geometries, forceThreshold, pressureThreshold);
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(log.getName()).append(" (bounded ")
                .append(LOG_SCAN_BYTES / (1024L * 1024L)).append(" MiB tail scan)\n");
        text.append("Stored optimization geometries: ")
                .append(geometries == null ? "not available" : "available from project data")
                .append('\n');
        text.append(String.format(Locale.ROOT,
                "User criteria: total force <= %.6g Ry/bohr%s%n", forceThreshold,
                pressureThreshold == null ? " (no pressure criterion)"
                        : String.format(Locale.ROOT, "; pressure <= %.6g kbar",
                                pressureThreshold)));
        text.append("Status: ").append(result.getStatus()).append('\n');
        text.append("BFGS end marker: ").append(result.isBfgsEndMarker())
                .append("; forces-converged marker: ").append(result.isForcesConvergedMarker())
                .append("; SCF converged at every step: ").append(result.isScfAlwaysConverged())
                .append('\n');
        if (result.getFinalTotalForce() != null) {
            text.append(String.format(Locale.ROOT, "Final total force: %.8f Ry/bohr%n",
                    result.getFinalTotalForce()));
        }
        if (result.getFinalPressureKbar() != null) {
            text.append(String.format(Locale.ROOT, "Final pressure: %.4f kbar%n",
                    result.getFinalPressureKbar()));
        }
        for (String diagnostic : result.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\n'Optimized' requires the engine markers and your user thresholds on the "
                + "parsed records; an automatically relaxed label is never granted without "
                + "this evidence. UNKNOWN/INCOMPLETE statuses mean the log does not carry the "
                + "required records.");
        return new AnalysisReport(label, result.isOptimized(), text.toString(), List.of(), null);
    }

    /** ATOMIC_SPECIES functional/relativity consistency plus library-metadata honesty. */
    private static AnalysisReport analyzePseudoFamily(Project project) {
        String label = AnalysisKind.PSEUDO_FAMILY.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: " + ex.getMessage());
        }
        if (input == null) {
            return failure(label, "The project has no current QE input to inspect.");
        }
        List<ValidationIssue> issues = PseudoFamilyValidator.validateFamilies(input);
        StringBuilder text = new StringBuilder();
        text.append("Checked the ATOMIC_SPECIES card of the current input against the local "
                + "pseudopotential library metadata.\n\n");
        if (issues.isEmpty()) {
            text.append("No mixed-functional or mixed-relativity inconsistency was found.\n");
        } else {
            int errors = 0;
            int warnings = 0;
            for (ValidationIssue issue : issues) {
                if (issue.getSeverity() == ValidationSeverity.ERROR) {
                    errors++;
                } else if (issue.getSeverity() == ValidationSeverity.WARNING) {
                    warnings++;
                }
                text.append("[").append(issue.getSeverity()).append("] ")
                        .append(issue.getCode()).append(": ").append(issue.getMessage())
                        .append('\n');
                if (issue.getDocumentationUrl() != null && !issue.getDocumentationUrl().isBlank()) {
                    text.append("    doc: ").append(issue.getDocumentationUrl()).append('\n');
                }
            }
            text.append(String.format(Locale.ROOT, "%n%d error(s), %d warning(s)%n",
                    errors, warnings));
        }
        text.append("\nA filename alone is never accepted as proof of XC/relativity "
                + "compatibility; pseudopotentials absent from the local library metadata are "
                + "reported as unverifiable. Family-manifest import and hash verification "
                + "(roadmap #35 manager) remain separate user actions.");
        boolean success = true;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationSeverity.ERROR) {
                success = false;
                break;
            }
        }
        return new AnalysisReport(label, success, text.toString(), List.of(), null);
    }

    /** spglib sidecar standardization plus SeeK-path; fails closed without the sidecar. */
    private static AnalysisReport analyzeSymmetryKpath(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.SYMMETRY_KPATH.getLabel();
        Cell cell = project.getCell();
        if (cell == null) {
            return failure(label, "The project has no atomic cell to standardize.");
        }
        double tolerance = params.getSymmetryTolerance();
        if (!(tolerance > 0.0) || !Double.isFinite(tolerance)) {
            return failure(label, "The symmetry tolerance must be a positive finite value "
                    + "in Angstrom (got " + tolerance + ").");
        }
        SpglibService service = SpglibService.detectDefault();
        if (!service.isAvailable()) {
            return failure(label, "spglib access needs the bundled Python sidecar "
                    + "(tools/spglib_sidecar.py) plus a Python interpreter with the spglib "
                    + "module; availability could not be confirmed. No local symmetry fallback "
                    + "is invented.");
        }
        OperationResult<StandardizedCell> standardized = service.standardize(cell, tolerance, false);
        if (!standardized.isSuccess() || standardized.getValue().isEmpty()) {
            return failure(label, "spglib standardization failed closed: "
                    + standardized.getMessage());
        }
        OperationResult<SeekPathResult> seek = service.seekPath(cell, tolerance);
        if (!seek.isSuccess() || seek.getValue().isEmpty()) {
            return failure(label, "SeeK-path evaluation failed closed: " + seek.getMessage());
        }
        StandardizedCell std = standardized.getValue().orElseThrow();
        SeekPathResult path = seek.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("spglib protocol ").append(SpglibService.PROTOCOL_VERSION)
                .append("; tolerance ").append(String.format(Locale.ROOT, "%.3g Ang", tolerance))
                .append('\n');
        text.append("Standardized cell kind: ").append(std.getKind())
                .append("; sites: ").append(std.getSites().size()).append('\n');
        text.append("Lattice (Ang):\n");
        appendMatrix(text, std.getLattice());
        text.append(String.format(Locale.ROOT,
                "Space group: %s (No. %d); seekpath %s%n", path.getSpaceGroupInternational(),
                path.getSpaceGroupNumber(), path.getSeekpathVersion()));
        text.append("Path summary: ").append(path.pathSummary()).append("\n\n");
        text.append("Path points (fractional reciprocal coordinates):\n");
        List<String> csv = new ArrayList<>();
        csv.add("label,kx,ky,kz");
        for (SeekPathResult.Point point : path.getPath()) {
            text.append(String.format(Locale.ROOT, "  %-6s %10.6f %10.6f %10.6f%n",
                    point.getLabel(), point.getKx(), point.getKy(), point.getKz()));
            csv.add(String.format(Locale.ROOT, "%s,%.8f,%.8f,%.8f", point.getLabel(),
                    point.getKx(), point.getKy(), point.getKz()));
        }
        text.append("\nLabels and coordinates follow the SeeK-path conventions for a "
                + "conventional cell; compare against your band-path input before running.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** data-file-schema.xml values with Optional-aware provenance and log cross-check. */
    private static AnalysisReport analyzeXmlSummary(ProjectProperty property, File projectDir,
            String logFileName, File source) throws IOException {
        String label = AnalysisKind.XML_SUMMARY.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " is " + (size / (1024L * 1024L))
                    + " MiB; the bounded cross-check accepts QE XML output up to 64 MiB.");
        }
        OperationResult<QeXmlResultParser.QeXmlResults> parsed =
                QeXmlResultParser.parseFile(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "QE XML parsing failed closed for " + source.getName()
                    + ": " + parsed.getMessage());
        }
        QeXmlResultParser.QeXmlResults data = parsed.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Schema version: ")
                .append(data.getSchemaVersion() == null ? "absent" : data.getSchemaVersion())
                .append('\n');
        text.append("nat: ").append(data.getNat().isPresent()
                ? String.valueOf(data.getNat().orElseThrow()) : "absent").append('\n');
        text.append("nspins: ").append(data.getNspins().isPresent()
                ? String.valueOf(data.getNspins().orElseThrow()) : "absent").append('\n');
        text.append("SCF converged: ").append(data.getScfConverged().isPresent()
                ? String.valueOf(data.getScfConverged().orElseThrow()) : "absent").append('\n');
        if (data.getTotalEnergyRy().isPresent()) {
            double energyRy = data.getTotalEnergyRy().orElseThrow();
            text.append(String.format(Locale.ROOT, "Total energy: %.8f Ry (%.6f eV)%n",
                    energyRy, energyRy * 13.605693122994));
        } else {
            text.append("Total energy: absent\n");
        }
        if (data.getTotalForce().isPresent()) {
            text.append(String.format(Locale.ROOT, "Total force: %.8f (XML units)%n",
                    data.getTotalForce().orElseThrow()));
        }
        if (data.getAtomicForces().isPresent()) {
            double[][] forcesRyBohr = data.getAtomicForcesRyPerBohr().orElse(null);
            double maxNorm = 0.0;
            if (forcesRyBohr != null) {
                for (double[] force : forcesRyBohr) {
                    maxNorm = Math.max(maxNorm, Math.sqrt(force[0] * force[0]
                            + force[1] * force[1] + force[2] * force[2]));
                }
            }
            text.append("Per-atom forces: ").append(data.getAtomicForces().orElseThrow().length)
                    .append(" entries; declared unit ").append(data.getAtomicForceUnit());
            if (forcesRyBohr != null) {
                text.append(String.format(Locale.ROOT,
                        "; max |F| normalized to Ry/bohr: %.8f", maxNorm));
            }
            text.append('\n');
        }
        if (data.getStressRyBohr3().isPresent()) {
            text.append("Stress tensor (Ry/bohr^3):\n");
            appendMatrix(text, data.getStressRyBohr3().orElseThrow());
        }
        if (data.getFermiEnergyEv().isPresent()) {
            double xmlFermi = data.getFermiEnergyEv().orElseThrow();
            text.append(String.format(Locale.ROOT, "%nXML Fermi energy: %.6f eV%n", xmlFermi));
            if (projectDir != null && logFileName != null) {
                File log = new File(projectDir, logFileName);
                if (log.isFile()) {
                    String tail = readTailUtf8(log.toPath(), LOG_SCAN_BYTES);
                    String found = null;
                    Matcher matcher = FERMI_LINE.matcher(tail);
                    while (matcher.find()) {
                        found = matcher.group(1);
                    }
                    if (found != null) {
                        double logFermi = Double.parseDouble(normalizeExponent(found));
                        double delta = Math.abs(xmlFermi - logFermi);
                        text.append(String.format(Locale.ROOT,
                                "Log-tail Fermi energy: %.6f eV; |delta| = %.6f eV -> %s%n",
                                logFermi, delta, delta <= 1.0e-3
                                        ? "CONSISTENT within 1 meV"
                                        : "MISMATCH above 1 meV - investigate provenance"));
                    } else {
                        text.append("No 'Fermi energy' line exists in the project log tail; "
                                + "the XML value stands without a cross-check.\n");
                    }
                }
            }
        } else {
            text.append("\nFermi energy: absent in this XML (insulator/SCF-only output)\n");
        }
        text.append("\nThe XML is parsed XXE-hardened and is the structured source of these "
                + "records; absent fields are reported as absent, never invented. Atomic-force "
                + "normalization follows the document-declared unit (Hartree vs Ry factor two).");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** vasprun.xml energy/Fermi/lattice/positions; parser-only, no VASP workflow advertised. */
    private static AnalysisReport analyzeVasprun(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.VASP_VASPRUN.getLabel();
        long size = Files.size(source.toPath());
        if (size > 256L * 1024L * 1024L) {
            return failure(label, source.getName() + " is " + (size / (1024L * 1024L))
                    + " MiB; the bounded inspection accepts vasprun.xml up to 256 MiB.");
        }
        QEVasprunXmlParser parser = new QEVasprunXmlParser(property);
        parser.parse(source);
        QEVasprunXmlParser.VasprunResults results = parser.getResults();
        if (results == null) {
            return failure(label, "No complete vasprun.xml results (final energy, Fermi level, "
                    + "lattice, positions) were found in " + source.getName() + ". The parser "
                    + "fails closed on incomplete files rather than inventing values.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT, "Final free energy (e_fr_energy as stored): %.8f eV%n",
                results.getTotalEnergyEv()));
        text.append(String.format(Locale.ROOT, "Fermi energy: %.6f eV%n", results.getFermiEnergyEv()));
        text.append("Final lattice (Ang):\n");
        appendMatrix(text, results.getFinalLattice());
        double[][] coords = results.getFinalFractionalCoords();
        text.append("Ionic positions: ").append(coords.length)
                .append(" entries (fractional coordinates of the final structure)\n");
        int limit = Math.min(coords.length, 50);
        for (int i = 0; i < limit; i++) {
            text.append(String.format(Locale.ROOT, "  atom %3d %12.8f %12.8f %12.8f%n",
                    i + 1, coords[i][0], coords[i][1], coords[i][2]));
        }
        if (coords.length > limit) {
            text.append("Only the first ").append(limit).append(" positions are shown.\n");
        }
        text.append("\nThis is a parser-only inspection: no POTCAR is bundled, no VASP "
                + "workflow is advertised, and use of VASP outputs requires your own license. "
                + "The e_fr_energy free energy is reported exactly as stored, without "
                + "re-derivation against sigma/entropy terms.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** CASTEP .castep energy/Fermi/geometry-completion; parser-only, no CASTEP workflow. */
    private static AnalysisReport analyzeCastepLog(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.CASTEP_LOG.getLabel();
        QECastepLogParser parser = new QECastepLogParser(property);
        parser.parse(source);
        List<String> diagnostics = parser.getDiagnostics();
        if (diagnostics.isEmpty()) {
            return failure(label, "No CASTEP 'Final energy' / 'Fermi energy' / geometry-"
                    + "completion records were found in " + source.getName()
                    + ". A .castep output file is required.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT, "Final energy: %.6f eV%n", parser.getFinalEnergyEv()));
        text.append(String.format(Locale.ROOT, "Fermi energy: %.6f eV%n", parser.getFermiEnergyEv()));
        text.append("Geometry optimization completion marker: ")
                .append(parser.isGeometryConverged()).append("\n\n");
        for (String diagnostic : diagnostics) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nThis is a parser-only inspection: no CASTEP input generation or "
                + "execution exists, and use of CASTEP outputs requires your own license. "
                + "Unparsed physics (dispersion, spin textures, band padding) is not claimed.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** Keyword-level diff between the project's current input and a user reference file. */
    private static AnalysisReport analyzeInputDiff(Project project, File file) {
        String label = AnalysisKind.INPUT_DIFF.getLabel();
        QEInput base;
        try {
            project.resolveQEInputs();
            base = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: " + ex.getMessage());
        }
        if (base == null) {
            return failure(label, "The project has no current QE input to compare.");
        }
        if (file == null || !file.isFile()) {
            return failure(label, "No reference input file was selected. Choose a pw.x-family "
                    + "input file to diff against the current input.");
        }
        QEInput modified;
        try {
            modified = new QESCFInput(file);
        } catch (IOException | RuntimeException ex) {
            return failure(label, "The reference file could not be parsed as a pw.x-family "
                    + "input: " + ex.getMessage());
        }
        int parsedValues = 0;
        for (String key : QEInput.listNamelistKeys()) {
            QENamelist namelist = modified.getNamelist(key);
            if (namelist != null) {
                parsedValues += namelist.numValues();
            }
        }
        if (parsedValues == 0) {
            return failure(label, "The reference file parsed to an empty input: no pw.x-family "
                    + "namelist values were recognized in " + file.getName() + ".");
        }
        List<QEInputDiffPreview.DiffItem> diffs = QEInputDiffPreview.compare(base, modified);
        StringBuilder text = new StringBuilder();
        text.append("Base: current project input; reference: ").append(file.getName())
                .append('\n');
        if (diffs.isEmpty()) {
            text.append("No namelist/card differences were detected between the two inputs.\n");
        } else {
            int added = 0;
            int removed = 0;
            int changed = 0;
            for (QEInputDiffPreview.DiffItem item : diffs) {
                switch (item.getType()) {
                case ADDED:
                    added++;
                    break;
                case REMOVED:
                    removed++;
                    break;
                default:
                    changed++;
                    break;
                }
            }
            text.append(String.format(Locale.ROOT,
                    "%d difference(s): %d added, %d removed, %d modified%n%n", diffs.size(),
                    added, removed, changed));
            int limit = Math.min(diffs.size(), 500);
            for (int i = 0; i < limit; i++) {
                text.append(" - ").append(diffs.get(i).toString()).append('\n');
            }
            if (diffs.size() > limit) {
                text.append("Only the first ").append(limit)
                        .append(" differences are shown; export the CSV for the full list.\n");
            }
        }
        List<String> csv = new ArrayList<>();
        csv.add("section,key,change,old_value,new_value");
        for (QEInputDiffPreview.DiffItem item : diffs) {
            csv.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s", item.getSection(), item.getKey(),
                    item.getType(), item.getOldValue() == null ? "" : item.getOldValue(),
                    item.getNewValue() == null ? "" : item.getNewValue()));
        }
        text.append("\nThe reference file is ingested with the standard pw.x-family reader "
                + "(control/system/electrons(+ions/cell) namelists and the usual cards); "
                + "numerically equivalent keyword spellings are treated as equal, and card "
                + "payloads are compared as presence/line-count summaries, not per-line "
                + "geometry. Nothing in the project input is modified.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Automatic K_POINTS mesh spacing against the live cell; explicit modes reported as-is. */
    private static AnalysisReport analyzeKmesh(Project project) {
        String label = AnalysisKind.KMESH_QUALITY.getLabel();
        QEContext context = requireInputAndCell(project, label);
        if (context.failure != null) {
            return context.failure;
        }
        QEKPoints kpoints = context.input.getCard(QEKPoints.class);
        if (kpoints == null) {
            return failure(label, "The current input has no K_POINTS card.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Input mode: ").append(context.input.getClass().getSimpleName()).append('\n');
        if (kpoints.isGamma()) {
            text.append("K_POINTS mode: gamma (single k-point).\n\n"
                    + "A Gamma-only mesh samples the Brillouin zone at one point; it is "
                    + "defensible for molecules or very large cells only and is never reported "
                    + "here as converged for periodic metals.");
            return new AnalysisReport(label, true, text.toString(), List.of(), null);
        }
        if (!kpoints.isAutomatic()) {
            text.append("K_POINTS mode: explicit list (").append(kpoints.numKPoints())
                    .append(" points).\n\nExplicit tpiba/crystal lists are reported as-is; "
                    + "spacing quality is only defined for automatic meshes in this analysis.");
            return new AnalysisReport(label, true, text.toString(), List.of(), null);
        }
        int[] grid = kpoints.getKGrid();
        int[] offset = kpoints.getKOffset();
        OperationResult<QEKpointMeshAdvisor.MeshReport> assessed =
                QEKpointMeshAdvisor.assess(context.cell.copyLattice(), grid, offset);
        if (!assessed.isSuccess() || assessed.getValue().isEmpty()) {
            return failure(label, "k-mesh assessment failed closed: " + assessed.getMessage());
        }
        QEKpointMeshAdvisor.MeshReport report = assessed.getValue().orElseThrow();
        text.append(String.format(Locale.ROOT, "K_POINTS automatic: %d %d %d with offset %d %d %d%n%n",
                grid[0], grid[1], grid[2], offset[0], offset[1], offset[2]));
        text.append(String.format(Locale.ROOT,
                "%-2s %-6s %-12s %-14s %-12s %-12s%n", "i", "n_i", "|a_i| (Ang)",
                "|b_i| (Ang^-1)", "spacing(Ang^-1)", "quality"));
        List<String> csv = new ArrayList<>();
        csv.add("direction,divisions,a_norm_ang,b_norm_inv_ang,spacing_inv_ang,range_ang,quality");
        for (QEKpointMeshAdvisor.DirectionReport direction : report.getDirections()) {
            text.append(String.format(Locale.ROOT,
                    "%-2d %-6d %-12.6f %-14.6f %-12.6f %-12s (%s %.3f Ang)%n",
                    direction.getIndex() + 1, direction.getDivisions(),
                    direction.getLatticeVectorNormAng(), direction.getReciprocalNormInvAng(),
                    direction.getSpacingInvAng(), direction.getQuality(), "range",
                    direction.getRangeAng()));
            csv.add(String.format(Locale.ROOT, "%d,%d,%.8f,%.8f,%.8f,%.8f,%s",
                    direction.getIndex() + 1, direction.getDivisions(),
                    direction.getLatticeVectorNormAng(), direction.getReciprocalNormInvAng(),
                    direction.getSpacingInvAng(), direction.getRangeAng(),
                    direction.getQuality()));
        }
        text.append(String.format(Locale.ROOT,
                "%nOverall mesh quality (worst direction): %s; full grid points: %d%n",
                report.getOverallQuality(), report.getTotalGridPoints()));
        for (String note : report.getNotes()) {
            text.append(" - ").append(note).append('\n');
        }
        text.append("\nSpacing is the exact |b_i|/n_i; the effective k-range 1/spacing is "
                + "classified with the QE-school 12/24 Angstrom heuristic used by the input "
                + "editor. This single-mesh label is not a convergence proof (roadmap #37).");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Defect planning preview: records the vacancy/substitution without touching the cell. */
    private static AnalysisReport analyzeDefectPreview(Project project, AnalysisParameters params) {
        String label = AnalysisKind.DEFECT_PREVIEW.getLabel();
        Cell cell = project.getCell();
        if (cell == null) {
            return failure(label, "The project has no atomic cell to plan a defect in.");
        }
        int atomCount = cell.numAtoms();
        if (atomCount < 1) {
            return failure(label, "The project cell contains no atoms.");
        }
        String type = params.getDefectType() == null ? "vacancy"
                : params.getDefectType().trim().toLowerCase(Locale.ROOT);
        int atomIndex = params.getAtomIndexA(); // 1-based user input
        if (atomIndex < 1 || atomIndex > atomCount) {
            return failure(label, "Defect target atom index must be 1.." + atomCount
                    + " (got " + atomIndex + ").");
        }
        QEPointDefectBuilder builder = new QEPointDefectBuilder(cell);
        try {
            if ("vacancy".equals(type)) {
                builder.addVacancy(atomIndex - 1, params.getDefectCharge());
            } else if ("substitution".equals(type)) {
                String element = params.getDefectElement() == null ? ""
                        : params.getDefectElement().trim();
                if (element.isEmpty()) {
                    return failure(label, "A substitution needs the replacement element "
                            + "symbol (e.g. B, N, Al).");
                }
                builder.addSubstitution(atomIndex - 1, element, params.getDefectCharge());
            } else {
                return failure(label, "Defect type must be 'vacancy' or 'substitution' in this "
                        + "preview (got '" + type + "'); interstitial placement stays an "
                        + "explicit editor action.");
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return failure(label, "Defect planning failed closed: " + ex.getMessage());
        }
        StringBuilder text = new StringBuilder();
        text.append("Defect plan for the live cell (").append(atomCount)
                .append(" atoms; NOTHING is applied to the project):\n\n");
        for (QEPointDefectBuilder.DefectRecord record : builder.getDefects()) {
            text.append(" - ").append(record.toString()).append('\n');
        }
        double minVector = Double.POSITIVE_INFINITY;
        double[][] lattice = cell.copyLattice();
        for (int i = 0; i < 3; i++) {
            double norm = Math.sqrt(lattice[i][0] * lattice[i][0] + lattice[i][1] * lattice[i][1]
                    + lattice[i][2] * lattice[i][2]);
            minVector = Math.min(minVector, norm);
        }
        text.append(String.format(Locale.ROOT,
                "%nSmallest lattice-vector norm: %.4f Ang (upper-bound scale of the "
                + "defect-defect periodic image spacing; the true minimum-image distance for "
                + "skew supercells additionally depends on the cell shape).%n", minVector));
        text.append("\nThe record is a preview only: symmetry-inequivalent defect enumeration "
                + "(roadmap #84) needs the spglib path, and the recorded charge state is "
                + "metadata - tot_charge is not rewritten into the input by this analysis.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /** Voigt/Reuss/Hill moduli from the thermo_pw elastic block; SPD-gated, units as printed. */
    private static AnalysisReport analyzeElasticModuli(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.ELASTIC_MODULI.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " is " + (size / (1024L * 1024L))
                    + " MiB; pass the thermo_pw output containing the 'Elastic Constant "
                    + "Matrix' block (or an extract of it), bounded to 64 MiB.");
        }
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        if (!content.contains("Elastic Constant Matrix")) {
            return failure(label, "No 'Elastic Constant Matrix' block exists in "
                    + source.getName() + ". A thermo_pw elastic-constants output is required.");
        }
        ElasticParser parser = new ElasticParser(property);
        parser.parse(source);
        OperationResult<QETensorAnalyzer.ElasticModuli> analyzed =
                QETensorAnalyzer.analyzeElastic(parser.getCij());
        if (!analyzed.isSuccess() || analyzed.getValue().isEmpty()) {
            return failure(label, "Elastic modulus derivation failed closed: "
                    + analyzed.getMessage());
        }
        QETensorAnalyzer.ElasticModuli moduli = analyzed.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("All moduli use the tensor's printed units (thermo_pw default kbar; "
                + "divide by 10 for GPa).\n\n");
        List<String> csv = new ArrayList<>();
        csv.add("quantity,value");
        java.util.function.BiConsumer<String, Double> row = (name, value) -> {
            text.append(String.format(Locale.ROOT, "%-34s %.6f%n", name, value));
            csv.add(String.format(Locale.ROOT, "%s,%.8f", name.replace(' ', '_'), value));
        };
        row.accept("Bulk modulus K Voigt", moduli.getBulkVoigt());
        row.accept("Bulk modulus K Reuss", moduli.getBulkReuss());
        row.accept("Bulk modulus K Hill", moduli.getBulkHill());
        row.accept("Shear modulus G Voigt", moduli.getShearVoigt());
        row.accept("Shear modulus G Reuss", moduli.getShearReuss());
        row.accept("Shear modulus G Hill", moduli.getShearHill());
        row.accept("Young's modulus E Hill", moduli.getYoungsModulusHill());
        row.accept("Poisson ratio nu Hill (unitless)", moduli.getPoissonRatioHill());
        row.accept("Pugh ratio K/G Hill (unitless)", moduli.getPughRatio());
        row.accept("Cauchy pressure C12-C44", moduli.getCauchyPressure());
        row.accept("Universal anisotropy A^U (unitless)", moduli.getUniversalAnisotropy());
        text.append("\nThe Voigt/Reuss bounds bracket the true polycrystalline average for a "
                + "random aggregate; the Hill mean is an estimator, not a measurement. The "
                + "tensor is SPD-verified before inversion. Anisotropy A^U is 0 only for an "
                + "isotropic medium.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Evidence review of an already-run series: per-step energy deltas and plateau location. */
    private static AnalysisReport analyzeConvergenceReview(File source, AnalysisParameters params)
            throws IOException {
        String label = AnalysisKind.CONVERGENCE_REVIEW.getLabel();
        int natoms = params.getAtomCount();
        if (natoms < 1) {
            return failure(label, "The per-atom energy criterion needs the atom count (got "
                    + natoms + ").");
        }
        double tolerance = params.getEnergyToleranceRyPerAtom();
        if (!(tolerance > 0.0) || !Double.isFinite(tolerance)) {
            return failure(label, "The energy tolerance must be a positive finite value in "
                    + "Ry/atom (got " + tolerance + ").");
        }
        List<Double> values = new ArrayList<>();
        List<Double> energies = new ArrayList<>();
        int rejected = 0;
        for (String raw : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] tokens = line.split("[,\\s]+");
            if (tokens.length < 2) {
                rejected++;
                continue;
            }
            try {
                double parameter = Double.parseDouble(normalizeExponent(tokens[0]));
                double energy = Double.parseDouble(normalizeExponent(tokens[1]));
                if (Double.isFinite(parameter) && Double.isFinite(energy)) {
                    values.add(parameter);
                    energies.add(energy);
                } else {
                    rejected++;
                }
            } catch (NumberFormatException ex) {
                rejected++; // Tolerated header row such as "ecutwfc,total_energy_Ry".
            }
        }
        if (values.size() < 3) {
            return failure(label, "Fewer than three (parameter, total_energy_Ry) rows were "
                    + "parsed from " + source.getName() + ". A convergence series needs at "
                    + "least three completed calculations - none are simulated here.");
        }
        for (int i = 1; i < values.size(); i++) {
            if (!(values.get(i) > values.get(i - 1))) {
                return failure(label, "Parameter values are not strictly increasing at row "
                        + (i + 1) + " (" + values.get(i - 1) + " then " + values.get(i)
                        + "); the plateau search requires a sorted series.");
            }
        }
        int recommendedIndex = -1;
        double maxAbsDelta = 0.0;
        boolean monotonic = true;
        List<String> csv = new ArrayList<>();
        csv.add("index,parameter,total_energy_Ry,delta_Ry,delta_Ry_per_atom");
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i < values.size(); i++) {
            double delta = energies.get(i) - energies.get(i - 1);
            double perAtom = delta / natoms;
            maxAbsDelta = Math.max(maxAbsDelta, Math.abs(delta));
            if (i > 1 && Math.abs(delta) > Math.abs(energies.get(i - 1) - energies.get(i - 2))) {
                monotonic = false;
            }
            if (recommendedIndex < 0 && Math.abs(perAtom) <= tolerance) {
                recommendedIndex = i - 1;
            }
            rows.append(String.format(Locale.ROOT,
                    "  %2d %12.6f %16.8f %+12.8f %+12.8f%n", i + 1, values.get(i),
                    energies.get(i), delta, perAtom));
            csv.add(String.format(Locale.ROOT, "%d,%.6f,%.8f,%.8f,%.8f", i + 1, values.get(i),
                    energies.get(i), delta, perAtom));
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append("; series points: ")
                .append(values.size());
        if (rejected > 0) {
            text.append("; skipped non-numeric rows (incl. any header): ").append(rejected);
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT,
                "Atom count: %d; energy-change tolerance: %.6g Ry/atom (%.4f meV/atom)%n",
                natoms, tolerance, tolerance * 13605.693122994));
        text.append(String.format(Locale.ROOT, "  %2s %12s %16s %12s %12s%n",
                "#", "parameter", "total E (Ry)", "dE (Ry)", "dE/atom (Ry)"));
        text.append(String.format(Locale.ROOT, "  %2d %12.6f %16.8f%n", 1, values.get(0),
                energies.get(0)));
        text.append(rows);
        text.append(String.format(Locale.ROOT,
                "Largest |dE| between neighbours: %.8f Ry; monotone decay of |dE|: %s%n",
                maxAbsDelta, monotonic));
        if (recommendedIndex >= 0) {
            text.append(String.format(Locale.ROOT,
                    "%nFirst parameter whose FOLLOWING |dE| change meets the per-atom "
                    + "tolerance: %.6f (row %d). This is evidence at this tolerance only - "
                    + "verify your production observable (forces/stress/properties) at the "
                    + "chosen value before relying on it (roadmap #36/#37).%n",
                    values.get(recommendedIndex), recommendedIndex + 1));
        } else {
            text.append("\nNo parameter in the series meets the per-atom energy-change "
                    + "tolerance; extend the series rather than trusting the last point.\n");
        }
        text.append("Energies were read as stored in Ry from completed calculations; this "
                + "review never extrapolates or fabricates missing points.");
        return new AnalysisReport(label, recommendedIndex >= 0, text.toString(), csv, null);
    }

    /** Convergence-series planner preview: the variant table only, never any file. */
    private static AnalysisReport analyzeSeriesPlan(AnalysisParameters params) {
        String label = AnalysisKind.SERIES_PLAN.getLabel();
        String keyword = params.getSeriesKeyword() == null ? "" : params.getSeriesKeyword().trim();
        if (keyword.isEmpty() || !keyword.matches("[A-Za-z][A-Za-z0-9_]{0,31}")) {
            return failure(label, "The series needs a valid QE keyword name (got '" + keyword
                    + "').");
        }
        int count = params.getSeriesCount();
        if (count < 2 || count > 20) {
            return failure(label, "The series length must be 2..20 points (got " + count + ").");
        }
        double start = params.getSeriesStart();
        double step = params.getSeriesStep();
        if (!Double.isFinite(start) || !Double.isFinite(step) || step == 0.0) {
            return failure(label, "Start and step must be finite numbers and step must be "
                    + "non-zero.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Convergence series plan (PREVIEW ONLY - no input files are written and "
                + "no jobs are launched by this analysis):\n\n");
        text.append(String.format(Locale.ROOT, "Keyword: %s; start: %.8g; step: %.8g; points: %d%n%n",
                keyword, start, step, count));
        List<String> csv = new ArrayList<>();
        csv.add("index," + keyword + ",suggested_job_name");
        for (int i = 0; i < count; i++) {
            double value = start + step * i;
            text.append(String.format(Locale.ROOT, "  %2d  %s = %.8g%n", i + 1, keyword, value));
            csv.add(String.format(Locale.ROOT, "%d,%.8g,%s_p%02d", i + 1, value, keyword, i + 1));
        }
        text.append("\nRelated keywords are NOT auto-adjusted: for an ecutwfc series, check "
                + "your ecutrho ratio per pseudopotential family; for a k-mesh series, keep "
                + "the spacing uniform across directions (see the k-mesh quality analysis). "
                + "Apply these values in the input editor and launch each job explicitly, then "
                + "review the energies with the convergence-series review analysis.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }
}
