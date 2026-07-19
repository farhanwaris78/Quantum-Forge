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
import quantumforge.builder.QEHullThermodynamics;
import quantumforge.input.QEInput;
import quantumforge.input.QEPpChargePotentialBuilder;
import quantumforge.input.QEPpWavefunctionBuilder;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectEnergies;
import quantumforge.project.property.ProjectProperty;
import quantumforge.run.parser.QEAcousticSumRuleValidator;
import quantumforge.run.parser.QEBandsDataParser;
import quantumforge.run.parser.QEBornChargeDielectricParser;
import quantumforge.run.parser.QEEliashbergTcCalculator;
import quantumforge.run.parser.QEGipawNmrParser;
import quantumforge.run.parser.QEHubbardHpParser;
import quantumforge.run.parser.QEMagneticMomentParser;
import quantumforge.run.parser.QEMdDiffusionMsdParser;
import quantumforge.run.parser.QEPhono3pyKappaParser;
import quantumforge.run.parser.QEPwcondConductanceParser;
import quantumforge.run.parser.QEThermoPwEosParser;
import quantumforge.run.parser.QETurboSpectrumParser;
import quantumforge.run.parser.QEWannier90SpreadParser;
import quantumforge.run.parser.QEXSpectraXanesParser;

/**
 * Deterministic, read-only result-analysis service bound to existing, individually
 * tested parser backends (roadmap items 46, 55, 58, 59, 61, 63, 64, 65, 66, 68,
 * 69, 106, 108, and 165). This class owns no process execution and writes nothing:
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
        HULL_STABILITY("Convex-hull stability from phase CSV");

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
                    || this == GEOMETRY_MEASURE || this == MD_MSD;
        }

        /** True for project-bound kinds that additionally parse a user data file. */
        public boolean needsDataFile() {
            return this == MD_MSD;
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
}
