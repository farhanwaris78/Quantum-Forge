/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */

package quantumforge.app.project.viewer.analysis;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.export.SvgSeriesPlotter;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.run.ResultAnalysisService;
import quantumforge.run.ResultAnalysisService.AnalysisKind;
import quantumforge.run.ResultAnalysisService.AnalysisReport;
import quantumforge.run.ResultAnalysisService.AnalysisParameters;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Project-viewer action "Analyze QE results": a single, honest entry point that
 * binds every tested result-analysis backend in {@link ResultAnalysisService} to
 * the GUI. The action never executes a command and never writes a file unless the
 * user explicitly saves a CSV export or a generated pp.x input preview.
 */
public final class AnalysisAction {

    private static final double REPORT_WIDTH = 960.0;
    private static final double REPORT_HEIGHT = 640.0;

    private final Project project;
    private final Stage stage;

    public AnalysisAction(Project project, Stage stage) {
        this.project = project;
        this.stage = stage;
    }

    /** Lets the user choose an analysis kind, collect parameters, and show the report. */
    public void showAnalysisChooser() {
        if (this.project == null) {
            showMessage("Result analysis", "No project is open.", AlertType.WARNING);
            return;
        }
        List<AnalysisKind> kinds = List.of(AnalysisKind.values());
        ChoiceDialog<AnalysisKind> chooser = new ChoiceDialog<>(kinds.get(0), kinds);
        chooser.setTitle("Analyze QE results");
        chooser.setHeaderText("Choose a deterministic result analysis");
        chooser.setContentText("Analysis:");
        // AnalysisKind.toString() feeds the list labels below.
        Optional<AnalysisKind> selected = chooser.showAndWait();
        if (selected.isEmpty()) {
            return;
        }
        run(selected.get());
    }

    private void run(AnalysisKind kind) {
        try {
            AnalysisParameters parameters = collectParameters(kind);
            if (parameters == null) {
                return; // User cancelled parameter entry.
            }
            if (kind.isProjectBound()) {
                File file = null;
                if (kind.needsDataFile()) {
                    file = resolveFile(kind);
                    if (file == null) {
                        return; // User cancelled the trajectory/phase file choice.
                    }
                }
                showReport(ResultAnalysisService.analyze(kind, this.project, file, parameters));
                return;
            }
            File file = resolveFile(kind);
            if (file == null && requiresFile(kind)) {
                return; // User cancelled the explicit file choice.
            }
            AnalysisReport report = ResultAnalysisService.analyze(kind, this.project.getProperty(),
                    this.project.getDirectory(), this.project.getPrefixName(),
                    this.project.getLogFileName(), file, parameters);
            showReport(report);
        } catch (RuntimeException ex) {
            showMessage("Result analysis", "Analysis failed closed: " + ex.getMessage(),
                    AlertType.ERROR);
        }
    }

    /** True when an analysis cannot proceed with the service's own failure report alone. */
    private static boolean requiresFile(AnalysisKind kind) {
        return !(kind.isInputPreview() || kind.usesProjectLog() || kind.isProjectBound());
    }

    /** File selection: discovered candidates first, explicit chooser when discovery fails. */
    private File resolveFile(AnalysisKind kind) {
        if (kind.isInputPreview()) {
            return null;
        }
        File directory = this.project.getDirectory();
        String logName = this.project.getLogFileName();
        List<File> candidates = ResultAnalysisService.discover(kind, directory, logName);
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.size() > 1) {
            ChoiceDialog<File> dialog = new ChoiceDialog<>(candidates.get(0), candidates);
            dialog.setTitle("Analyze QE results");
            dialog.setHeaderText("Several candidate files were found for:\n" + kind.getLabel());
            dialog.setContentText("File:");
            Optional<File> picked = dialog.showAndWait();
            if (picked.isPresent()) {
                return picked.get();
            }
            return null;
        }
        if (kind.usesProjectLog()) {
            return null; // Honest failure is produced by the service.
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select data file for: " + kind.getLabel());
        if (directory != null && directory.isDirectory()) {
            chooser.setInitialDirectory(directory);
        }
        return chooser.showOpenDialog(this.stage);
    }

    /** Collects only the parameters an analysis kind actually consumes. */
    private AnalysisParameters collectParameters(AnalysisKind kind) {
        AnalysisParameters parameters = new AnalysisParameters();
        switch (kind) {
        case PP_WAVEFUNCTION_INPUT: {
            Integer kpoint = askInteger("Wavefunction k-point index (1-based)", 1);
            if (kpoint == null) {
                return null;
            }
            Integer band = askInteger("Wavefunction band index (1-based)", 1);
            if (band == null) {
                return null;
            }
            Integer spin = askInteger("Spin component (0 unpolarized, 1 up, 2 down)", 0);
            if (spin == null) {
                return null;
            }
            parameters.withKpointIndex(kpoint).withBandIndex(band).withSpinComponent(spin);
            break;
        }
        case ELIASHBERG_TC: {
            Double muStar = askDouble("Coulomb pseudopotential mu* (typically 0.10-0.15)", "0.10");
            if (muStar == null) {
                return null;
            }
            parameters.withMuStar(muStar);
            break;
        }
        case BANDS_DATA: {
            Double fermi = askDouble(
                    "Fermi reference in eV, or leave empty to use the stored/log value", "");
            if (fermi != null) {
                parameters.withFermiEv(fermi);
            }
            break;
        }
        case WORK_FUNCTION: {
            Double fermi = askDouble(
                    "Slab Fermi energy in eV, or leave empty to use the stored/log value", "");
            if (fermi != null) {
                parameters.withFermiEv(fermi);
            }
            break;
        }
        case RESOURCE_ESTIMATE: {
            Integer ranks = askInteger("Total MPI ranks for the layout advice", 1);
            if (ranks == null) {
                return null;
            }
            parameters.withTotalRanks(ranks);
            break;
        }
        case MPI_POOLS_ADVISOR: {
            Integer ranks = askInteger("Total MPI ranks R for the pool-divisor audit", 1);
            if (ranks == null) {
                return null;
            }
            Integer pools = askInteger("Existing -nk pool count to audit (0 = none)", 0);
            if (pools == null) {
                return null;
            }
            parameters.withTotalRanks(ranks).withCurrentPools(pools);
            break;
        }
        case UNIT_CONVERT: {
            Double value = askDouble("Finite value to convert", "1.0");
            if (value == null) {
                return null;
            }
            String from = askText("From unit (curated: ry, ev, mev, ha, kJ/mol, cm-1, "
                    + "THz, bohr, Angstrom, nm, pm, kbar, GPa)", "ry");
            if (from == null) {
                return null;
            }
            String to = askText("To unit (same curated registry)", "ev");
            if (to == null) {
                return null;
            }
            parameters.withUnitConversion(value, from, to);
            break;
        }
        case SERIES_REF_ALIGN: {
            String mode = askText("Reference mode (FERMI, VBM, VACUUM, USER) - the "
                    + "tool never infers references from the data", "FERMI");
            if (mode == null) {
                return null;
            }
            Double ref1 = askDouble("Series-1 reference value in eV "
                    + "(e.g. its Fermi/VBM/vacuum level, analyst-supplied)", "");
            if (ref1 == null) {
                return null; // cancel or blank: no reference means no alignment
            }
            Double ref2 = askDouble("Series-2 reference value in eV "
                    + "(same physical origin asserted)", "");
            if (ref2 == null) {
                return null;
            }
            Double target = askDouble("USER-mode landing point in eV "
                    + "(ignored for FERMI/VBM/VACUUM)", "0.0");
            if (target == null) {
                return null;
            }
            parameters.withAlignment(mode, ref1, ref2, target);
            break;
        }
        case BANDS_FERMI_REVIEW: {
            Double fermi = askDouble("Explicit Fermi reference in eV for E - E_F "
                    + "(analyst-supplied; the review never infers it)", "");
            if (fermi == null) {
                return null; // cancel or blank: no reference means no review
            }
            parameters.withFermiEv(fermi);
            break;
        }
        case BAND_GAP_BANDS: {
            Integer valence = askInteger("Valence-band count nV (analyst-supplied; "
                    + "must leave at least one conduction band)", 0);
            if (valence == null) {
                return null;
            }
            Double tolerance = askDouble("Metallicity tolerance in eV "
                    + "(finite, >= 0; the data cannot supply it)", "");
            if (tolerance == null) {
                return null; // cancel or blank: no tolerance means no verdict
            }
            Double kTolerance = askDouble("Directness k tolerance (strictly "
                    + "positive)", "1.0E-6");
            if (kTolerance == null) {
                return null;
            }
            parameters.withGapClassification(valence, tolerance, kTolerance);
            break;
        }
        case OPTIMADE_QUERY_DRAFT: {
            String base = askText("OPTIMADE provider base URL (http(s), path "
                    + "ending /v1; no credentials, no query string)",
                    "https://optimade.materialsproject.org/v1");
            if (base == null) {
                return null;
            }
            String elements = askText("Required elements (comma-separated, 1-8, "
                    + "e.g. Si,O)", "");
            if (elements == null) {
                return null;
            }
            Integer neMax = askInteger("nelements cap (0 omits the clause)", 0);
            if (neMax == null) {
                return null;
            }
            Integer nsMax = askInteger("nsites cap (0 omits the clause)", 0);
            if (nsMax == null) {
                return null;
            }
            Integer pageLimit = askInteger("page_limit 1..1000 (0 = default 20)", 0);
            if (pageLimit == null) {
                return null;
            }
            parameters.withOptimadeQuery(base, elements, neMax, nsMax, pageLimit);
            break;
        }
        case MP_QUERY_DRAFT: {
            String mpBase = askText("Materials Project API base URL (https only, "
                    + "API root; no credentials)", "https://api.materialsproject.org");
            if (mpBase == null) {
                return null;
            }
            String ids = askText("Material ids (comma-separated mp-NUMBER or "
                    + "mvc-NUMBER, 1-20)", "");
            if (ids == null) {
                return null;
            }
            String key = askText("API key (plain token; travels ONLY in the "
                    + "X-API-KEY header at fetch time, never echoed or embedded "
                    + "in the URL; leave blank to draft without a key)", "");
            if (key == null) {
                return null;
            }
            parameters.withMpQuery(mpBase, ids, key);
            break;
        }
        case SSH_CONFIG_DRAFT: {
            String alias = askText("Host alias ([A-Za-z0-9][A-Za-z0-9_-]{0,63})",
                    "hpc-cluster");
            if (alias == null) {
                return null;
            }
            String host = askText("HostName (hostname or IP)", "");
            if (host == null) {
                return null;
            }
            String user = askText("User (POSIX logname [a-z_][a-z0-9_-]{0,31})",
                    "");
            if (user == null) {
                return null;
            }
            Integer port = askInteger("Port (1..65535)", 22);
            if (port == null) {
                return null;
            }
            String identity = askText("IdentityFile path (blank = agent/default "
                    + "keys, stated honestly; whitespace/expansion chars refused)",
                    "");
            if (identity == null) {
                return null;
            }
            String knownHosts = askText("known_hosts path to exercise the runtime"
                    + " bridge (BLANK = feasibility statement only; a compiled config"
                    + " refuses blank known_hosts BY DESIGN - no connection is"
                    + " attempted either way)", "");
            if (knownHosts == null) {
                return null;
            }
            parameters.withSshTarget(alias, host, user, port, identity);
            parameters.withSshKnownHosts(knownHosts);
            break;
        }
        case SFTP_TRANSFER_PLAN: {
            String local = askText("Project-relative local file to stage "
                    + "(must already exist inside this project; no absolute "
                    + "paths, no '..'); its sha256 is pinned at draft time", "");
            if (local == null) {
                return null;
            }
            String remote = askText("Remote absolute POSIX FILE path "
                    + "(e.g. /home/you/qe/deck.cube; no trailing '/', no "
                    + "whitespace/expansion characters, no '..' climbs)", "");
            if (remote == null) {
                return null;
            }
            String overwrite = askText("Overwrite the remote file if it "
                    + "exists? Type exactly 'yes' to allow; anything else "
                    + "keeps the safe REFUSE-IF-EXISTS posture", "no");
            if (overwrite == null) {
                return null;
            }
            parameters.withSftpPlan(local, remote,
                    overwrite.trim().equalsIgnoreCase("yes"));
            break;
        }
        case SLURM_SCRIPT_DRAFT: {
            String jobName = askText("--job-name ([A-Za-z][A-Za-z0-9._-]{0,63})",
                    "qe-scf");
            if (jobName == null) {
                return null;
            }
            String partition = askText("--partition (blank = omit the directive, "
                    + "stated honestly in the script)", "");
            if (partition == null) {
                return null;
            }
            Integer nodes = askInteger("--nodes (1..1024 per drafted job)", 1);
            if (nodes == null) {
                return null;
            }
            Integer ntasks = askInteger("--ntasks (1..65536)", 1);
            if (ntasks == null) {
                return null;
            }
            String walltime = askText("--time strict HH:MM:SS (<= 7 days; "
                    + "e.g. 01:30:00)", "01:00:00");
            if (walltime == null) {
                return null;
            }
            String modules = askText("modules to load, comma-separated "
                    + "(blank = 'no modules declared' comment, not assumption)", "");
            if (modules == null) {
                return null;
            }
            String command = askText("payload: ONE reviewed command line "
                    + "(e.g. srun pw.x -in scf.in > scf.out; #SBATCH lines refuse)",
                    "");
            if (command == null) {
                return null;
            }
            parameters.withSlurmScript(jobName, partition, nodes, ntasks, walltime,
                    modules, command);
            break;
        }
        case KMESH_CONVERGENCE_PLAN: {
            String ladder = askText("k-mesh ladder, rungs separated by ';' "
                    + "(e.g. '4 4 4; 8 8 8; 12 12 12'; 2..8 rungs, never coarsening, "
                    + "order kept exactly)", "");
            if (ladder == null) {
                return null;
            }
            String offset = askText("shift triple, exactly three 0/1 values "
                    + "(e.g. '0 0 0' Gamma-inclusive, '1 1 1' half-step shifted; "
                    + "blank REFUSES - no default is invented)", "0 0 0");
            if (offset == null) {
                return null;
            }
            parameters.withKmeshPlan(ladder, offset);
            break;
        }
        case SITE_PROFILE_DRAFT: {
            String cluster = askText("cluster name ([A-Za-z][A-Za-z0-9._-]{0,63})",
                    "");
            if (cluster == null) {
                return null;
            }
            String scheduler = askText("scheduler - TYPED: slurm | pbs | pjm | sge "
                    + "(free-form strings refuse)", "slurm");
            if (scheduler == null) {
                return null;
            }
            String launcher = askText("launcher - TYPED: srun | mpirun | mpiexec "
                    + "(pairing with the scheduler is not judged)", "srun");
            if (launcher == null) {
                return null;
            }
            String partition = askText("default partition (blank = honest omission "
                    + "comment, not a default)", "");
            if (partition == null) {
                return null;
            }
            String account = askText("account/allocation (blank = honest omission "
                    + "comment)", "");
            if (account == null) {
                return null;
            }
            String scratch = askText("scratch root - REQUIRED absolute POSIX path "
                    + "(e.g. /scratch/you; no expansion characters)", "");
            if (scratch == null) {
                return null;
            }
            Integer maxNodes = askInteger("max nodes ceiling (1..100000; recorded, "
                    + "not enforced here)", 32);
            if (maxNodes == null) {
                return null;
            }
            String modules = askText("site modules, comma-separated (blank = "
                    + "'none declared' comment)", "");
            if (modules == null) {
                return null;
            }
            parameters.withSiteProfile(cluster, scheduler, launcher, partition,
                    account, scratch, maxNodes.intValue(), modules);
            break;
        }
        case NEB_INPUT_DRAFT: {
            Integer images = askInteger("num_of_images 2..64 (end points INCLUDED; "
                    + "must match -nimage)", 5);
            if (images == null) {
                return null;
            }
            Integer nstep = askInteger("nstep_path 1..10000", 100);
            if (nstep == null) {
                return null;
            }
            String opt = askText("opt_scheme - TYPED: sd | broyden | broyden2 | "
                    + "quick-min | lbfgs", "broyden");
            if (opt == null) {
                return null;
            }
            String ci = askText("CI_scheme - TYPED: no-CI | highest | spin "
                    + "('manual' refuses: needs the editor's image energies; any CI "
                    + "needs images >= 3)", "no-CI");
            if (ci == null) {
                return null;
            }
            Double kMin = askDouble("k_min [a.u.] (REQUIRED - no honest default; "
                    + "must be <= k_max)", "");
            Double kMax = askDouble("k_max [a.u.] (REQUIRED - no honest default)", "");
            Double ds = askDouble("ds, optimization step [a.u.] (REQUIRED)", "");
            Double pathThr = askDouble("path_thr, path convergence threshold [a.u.] "
                    + "(REQUIRED)", "");
            if (kMin == null || kMax == null || ds == null || pathThr == null) {
                return null;  // blank REQUIRED numerics refuse - nothing is invented
            }
            parameters.withNebDraft(images.intValue(), nstep.intValue(), opt, ci,
                    kMin.doubleValue(), kMax.doubleValue(), ds.doubleValue(),
                    pathThr.doubleValue());
            break;
        }
        case JOB_CANCEL_PLAN: {
            String scheduler = askText("scheduler - TYPED: slurm | pbs | pjm | sge",
                    "slurm");
            if (scheduler == null) {
                return null;
            }
            String jobId = askText("job id (numeric; SLURM array 'id_index' allowed; "
                    + "PBS '.server' suffix allowed)", "");
            if (jobId == null) {
                return null;
            }
            String confirm = askText("RETYPE the job id EXACTLY to confirm "
                    + "(compared untrimmed; a button click is not identity)", "");
            if (confirm == null) {
                return null;
            }
            parameters.withJobCancel(scheduler, jobId, confirm);
            break;
        }
        case SCHEDULER_ADAPTER_AUDIT: {
            String scheduler = askText("scheduler audit - TYPED: slurm | pbs | pjm | sge"
                    + " (BLANK = census of every registered adapter; unknown names refuse"
                    + " - there is no default)", "");
            if (scheduler == null) {
                return null;
            }
            String jobId = askText("job id for a per-id grammar verdict owned by the"
                    + " adapter itself (BLANK = census only; the verdict is rendered"
                    + " either way - nothing is executed)", "");
            if (jobId == null) {
                return null;
            }
            parameters.withSchedulerAudit(scheduler, jobId);
            break;
        }
        case JOB_MONITOR_AUDIT: {
            String scheduler = askText("monitor audit scheduler - TYPED: slurm | pbs |"
                    + " pjm | sge (BLANK = census of all four signal/needle tables;"
                    + " unknown names refuse - there is no default)", "");
            if (scheduler == null) {
                return null;
            }
            String jobId = askText("job id for the review status command (BLANK = skip;"
                    + " the rendered line is a review line only - no scheduler is"
                    + " contacted)", "");
            if (jobId == null) {
                return null;
            }
            parameters.withMonitorAudit(scheduler, jobId);
            break;
        }
        case SYNC_RUNTIME_AUDIT: {
            String workflow = askText("sync audit workflow - TYPED: scf | optimiz | md |"
                    + " dos | band | neb | phonon (BLANK = census of all seven;"
                    + " free-form names refuse)", "");
            if (workflow == null) {
                return null;
            }
            String prefix = askText("QE prefix (BLANK = 'espresso', the default;"
                    + " used only to render the manifest rows)", "");
            if (prefix == null) {
                return null;
            }
            String large = askText("include LARGE_OPTIONAL payloads in the census?"
                    + " (yes/no; BLANK = no - large files are opt-in; nothing is"
                    + " transferred either way)", "no");
            if (large == null) {
                return null;
            }
            boolean includeLarge = large.trim().equalsIgnoreCase("yes")
                    || large.trim().equalsIgnoreCase("y");
            parameters.withSyncRuntimeAudit(workflow, prefix, includeLarge);
            break;
        }
        case MONITOR_POLL_PLAN: {
            Double initial = askDouble("initial poll interval [s] (1..3600)", "30");
            if (initial == null) {
                return null;
            }
            Double max = askDouble("max poll interval [s] (>= initial, <= 86400)",
                    "300");
            if (max == null) {
                return null;
            }
            Double factor = askDouble("backoff factor (1.0..4.0; 1.0 = declared "
                    + "constant polling)", "2.0");
            if (factor == null) {
                return null;
            }
            Integer polls = askInteger("max polls (1..10000 - unbounded refuses by "
                    + "construction)", 60);
            if (polls == null) {
                return null;
            }
            parameters.withMonitorPoll(initial.doubleValue(), max.doubleValue(),
                    factor.doubleValue(), polls.intValue());
            break;
        }
        case SYNC_MANIFEST_DRAFT: {
            String required = askText("REQUIRED remote artifacts, comma-separated "
                    + "(e.g. 'pw.out, xml/data-file-schema.xml'; at least one - "
                    + "parsers' prerequisites belong here)", "pw.out");
            if (required == null) {
                return null;
            }
            String optional = askText("OPTIONAL artifacts, csv or '*.ext' top-level "
                    + "patterns (blank = none declared)", "");
            if (optional == null) {
                return null;
            }
            String large = askText("LARGE-on-demand artifacts (fetched only on "
                    + "request, csv, blank = none)", "");
            if (large == null) {
                return null;
            }
            String excluded = askText("EXCLUDED artifacts (never fetched, csv, "
                    + "blank = none; one role per name - duplicates refuse)", "");
            if (excluded == null) {
                return null;
            }
            parameters.withSyncManifest(required, optional, large, excluded);
            break;
        }
        case SMEARING_LADDER_PLAN: {
            String scheme = askText("smearing scheme - TYPED: gaussian | mp | mv | fd "
                    + "(one scheme per study)", "gaussian");
            if (scheme == null) {
                return null;
            }
            String ladder = askText("degauss DOWN-ladder in Ry, ';'-separated "
                    + "(e.g. '0.02; 0.01; 0.005'; 2..8 rungs, never widening; 0 is "
                    + "NOT a rung)", "");
            if (ladder == null) {
                return null;
            }
            parameters.withSmearingPlan(scheme, ladder);
            break;
        }
        case CUTOFF_LADDER_PLAN: {
            String ladder = askText("ecutwfc ladder in Ry, ASCENDING, ';'-separated "
                    + "(e.g. '30; 40; 60'; 2..8 rungs in the owned 5..500 band)", "");
            if (ladder == null) {
                return null;
            }
            Double ratio = askDouble("YOUR deck's ecutrho/ecutwfc ratio (REQUIRED, "
                    + ">= 1.0; no default invented)", "");
            if (ratio == null) {
                return null;  // blank REQUIRED ratio refuses - nothing is invented
            }
            parameters.withCutoffPlan(ladder, ratio.doubleValue());
            break;
        }
        case ARRAY_JOB_PLAN: {
            String base = askText("array base name ([A-Za-z][A-Za-z0-9._-]{0,63}; "
                    + "seeds <base>/task_<i> directories)", "sweep");
            if (base == null) {
                return null;
            }
            String values = askText("sweep values, comma-separated plain numbers "
                    + "(echoed VERBATIM; numeric duplicates refuse - e.g. '30, 30.0' "
                    + "is one point twice)", "");
            if (values == null) {
                return null;
            }
            String slurm = askText("incorporate a '#SBATCH --array=1-N' REVIEW line? "
                    + "type exactly 'yes' to opt in", "no");
            if (slurm == null) {
                return null;
            }
            parameters.withArrayJob(base, values, slurm.trim().equalsIgnoreCase("yes"));
            break;
        }
        case CONTAINER_PROFILE_DRAFT: {
            String runtime = askText("container runtime - TYPED: apptainer | "
                    + "singularity", "apptainer");
            if (runtime == null) {
                return null;
            }
            String image = askText("image reference with REQUIRED digest: "
                    + "name:tag@sha256:<64 hex> (floating tags refuse - moving "
                    + "targets)", "");
            if (image == null) {
                return null;
            }
            String binds = askText("bind paths, comma-separated absolute POSIX "
                    + "(blank = none declared; literal grammar - no expansion)", "");
            if (binds == null) {
                return null;
            }
            String mpi = askText("is the image host-MPI compatible? type exactly "
                    + "'yes' or 'no' (NEUTRAL REFUSES - your declaration, not "
                    + "verified here)", "");
            if (mpi == null) {
                return null;
            }
            parameters.withContainerProfile(runtime, image, binds, mpi);
            break;
        }
        case JOB_STATE_GUARD: {
            String mode = askText("mode - TYPED: transition | signal", "transition");
            if (mode == null) {
                return null;
            }
            if (mode.trim().equalsIgnoreCase("transition")) {
                String from = askText("from state (staged/submitted/pending/running/"
                        + "completed/failed/cancelled/unknown)", "pending");
                if (from == null) {
                    return null;
                }
                String to = askText("to state (same typed set)", "running");
                if (to == null) {
                    return null;
                }
                parameters.withJobState("transition", from, to, "", "");
            } else if (mode.trim().equalsIgnoreCase("signal")) {
                String scheduler = askText("scheduler - TYPED: slurm | pbs | pjm | sge",
                        "slurm");
                if (scheduler == null) {
                    return null;
                }
                String signal = askText("scheduler signal/code (e.g. PD, R, CD, qw, "
                        + "F; unrecognized maps to an HONEST UNKNOWN, never a guess)",
                        "");
                if (signal == null) {
                    return null;
                }
                parameters.withJobState("signal", "", "", scheduler, signal);
            } else {
                parameters.withJobState(mode, "", "", "", "");  // analyzer refuses typed
            }
            break;
        }
        case PHONON_GRID_PLAN: {
            String ladder = askText("phonon q-grid ladder, rungs ';'-separated "
                    + "(e.g. '2 2 2; 4 4 4'; 2..6 rungs, owned 1..32 band, never "
                    + "coarsening; commensurability with the deck k-grid is "
                    + "VERDICTED, not assumed)", "");
            if (ladder == null) {
                return null;
            }
            parameters.withPhononPlan(ladder);
            break;
        }
        case CHECKPOINT_RESUBMIT_PLAN: {
            String override = askText("prefix override - BLANK uses the project prefix "
                    + "(advice is READ-ONLY: nothing is written or submitted)", "");
            if (override == null) {
                return null;
            }
            parameters.withCheckpointPrefix(override);
            break;
        }
        case FINAL_GEOMETRY_APPLY: {
            // This kind is the first write-kind on the analysis channel: the menu pick
            // alone is not the consent. Retype-guard like the job-cancel flow.
            String consent = askText("This WRITES the last converged geometry into every "
                    + "resolved input deck (staged audit + verified write + rollback; "
                    + "pre-state pinned under .quantumforge/). Type 'apply' to proceed", "");
            if (consent == null || !consent.trim().equals("apply")) {
                return null; // Treated as cancelled - nothing mutates.
            }
            break;
        }
        case SLAB_MILLER_PREVIEW: {
            Integer h = askInteger("Miller index h (-16..16)", 1);
            if (h == null) {
                return null;
            }
            Integer k = askInteger("Miller index k (-16..16)", 0);
            if (k == null) {
                return null;
            }
            Integer l = askInteger("Miller index l (-16..16)", 0);
            if (l == null) {
                return null;
            }
            parameters.withMillerIndices(h, k, l);
            break;
        }
        case GEOMETRY_MEASURE: {
            int[] indices = askAtomIndices();
            if (indices == null) {
                return null;
            }
            parameters.withAtomIndices(indices[0], indices[1], indices[2], indices[3]);
            break;
        }
        case MD_MSD: {
            Double dt = askDouble("Time between stored trajectory frames (ps)", "1.0");
            if (dt == null) {
                return null;
            }
            parameters.withFrameTimeStepPs(dt);
            break;
        }
        case PHONON_DOS_THERMO: {
            Double temperature = askDouble(
                    "Temperature in kelvin for the harmonic phonon integration", "300.0");
            if (temperature != null) {
                parameters.withTemperatureK(temperature);
            }
            break;
        }
        case VOLTAGE_PROFILE: {
            Double charge = askDouble("Insertion-ion charge z (1 = Li/Na/K, ~2 = Mg/Zn)", "1.0");
            if (charge != null) {
                parameters.withIonCharge(charge);
            }
            break;
        }
        case ADSORPTION_PREVIEW: {
            String molecule = askText("Molecule template (CO, H2O, NH3, OH, NO)", "CO");
            if (molecule == null) {
                return null;
            }
            Double height = askDouble("Height above the topmost slab atom in Angstrom (>= 1.0)",
                    "2.0");
            if (height == null) {
                return null;
            }
            Double x = askDouble("Fractional surface position x (0-1)", "0.5");
            if (x == null) {
                return null;
            }
            Double y = askDouble("Fractional surface position y (0-1)", "0.5");
            if (y == null) {
                return null;
            }
            parameters.withMoleculeName(molecule).withAdsorbHeight(height).withAdsorbX(x)
                    .withAdsorbY(y);
            break;
        }
        case SMEARING_ANALYSIS: {
            int defaultAtoms = 1;
            if (this.project.getCell() != null && this.project.getCell().numAtoms() > 0) {
                defaultAtoms = this.project.getCell().numAtoms();
            }
            Integer atoms = askInteger("Number of atoms in the simulation cell "
                    + "(needed for the per-atom entropy safety check)", defaultAtoms);
            if (atoms == null) {
                return null;
            }
            parameters.withAtomCount(atoms);
            break;
        }
        case GEOMETRY_CONVERGENCE: {
            Double force = askDouble("Total-force threshold in Ry/bohr "
                    + "(the 'optimized' verdict is only granted below it)", "0.001");
            if (force != null) {
                parameters.withForceThresholdRyBohr(force);
            }
            Double pressure = askDouble("Pressure threshold in kbar, or leave empty "
                    + "to skip the pressure check", "");
            if (pressure != null) {
                parameters.withPressureThresholdKbar(pressure);
            }
            break;
        }
        case SYMMETRY_KPATH: {
            Double tolerance = askDouble("spglib symmetry tolerance in Angstrom", "1.0e-5");
            if (tolerance != null) {
                parameters.withSymmetryTolerance(tolerance);
            }
            break;
        }
        case CONVERGENCE_REVIEW: {
            int defaultAtoms = 1;
            if (this.project.getCell() != null && this.project.getCell().numAtoms() > 0) {
                defaultAtoms = this.project.getCell().numAtoms();
            }
            Integer atoms = askInteger("Number of atoms in the simulation cell "
                    + "(per-atom energy criterion)", defaultAtoms);
            if (atoms == null) {
                return null;
            }
            parameters.withAtomCount(atoms);
            Double tolerance = askDouble("Energy-change tolerance in Ry/atom"
                    + " (default 0.001 Ry ~ 13.6 meV/atom)", "0.001");
            if (tolerance != null) {
                parameters.withEnergyToleranceRyPerAtom(tolerance);
            }
            break;
        }
        case SERIES_PLAN: {
            String keyword = askText("QE keyword to vary (e.g. ecutwfc)", "ecutwfc");
            if (keyword == null) {
                return null;
            }
            Double start = askDouble("Start value", "30.0");
            if (start == null) {
                return null;
            }
            Double step = askDouble("Step between points", "10.0");
            if (step == null) {
                return null;
            }
            Integer count = askInteger("Number of points (2-20)", 6);
            if (count == null) {
                return null;
            }
            parameters.withSeriesKeyword(keyword).withSeriesStart(start).withSeriesStep(step)
                    .withSeriesCount(count);
            break;
        }
        case EXX_GUIDANCE: {
            Integer nq1 = askInteger("Fock q-grid nq1 (must divide nk1)", 1);
            if (nq1 == null) {
                return null;
            }
            Integer nq2 = askInteger("Fock q-grid nq2 (must divide nk2)", 1);
            if (nq2 == null) {
                return null;
            }
            Integer nq3 = askInteger("Fock q-grid nq3 (must divide nk3)", 1);
            if (nq3 == null) {
                return null;
            }
            parameters.withExxNqGrid(nq1, nq2, nq3);
            break;
        }
        case DEFECT_FORMATION: {
            Double defect = askDouble("Total energy of the DEFECT cell (eV)", "");
            if (defect == null || !Double.isFinite(defect)) {
                return null;
            }
            Double host = askDouble("Total energy of the pristine HOST cell (eV)", "");
            if (host == null || !Double.isFinite(host)) {
                return null;
            }
            Double chem = askDouble("Chemical-potential sum sum(n_i*mu_i) in eV "
                    + "(your own consistent references)", "");
            if (chem == null || !Double.isFinite(chem)) {
                return null;
            }
            Integer charge = askInteger("Defect charge state q (electrons)", 0);
            if (charge == null) {
                return null;
            }
            parameters.withDefectEnergyEv(defect).withHostEnergyEv(host)
                    .withChemPotSumEv(chem).withDefectCharge(charge);
            if (charge != 0) {
                Double vbm = askDouble("Host VBM energy in eV", "");
                if (vbm == null || !Double.isFinite(vbm)) {
                    return null;
                }
                Double fermi = askDouble("Fermi shift dE_F above the host VBM (eV)", "0.0");
                if (fermi == null) {
                    return null;
                }
                parameters.withVbmEv(vbm).withFermiEv(fermi);
            }
            Double corr = askDouble("Correction term E_corr in eV "
                    + "(finite-size/alignment; 0 = none)", "0.0");
            if (corr == null) {
                return null;
            }
            parameters.withCorrectionsEv(corr);
            break;
        }
        case ADSORPTION_ENERGY: {
            Double total = askDouble("Total energy of the slab+adsorbate cell (eV)", "");
            if (total == null || !Double.isFinite(total)) {
                return null;
            }
            Double slab = askDouble("Total energy of the bare slab (eV)", "");
            if (slab == null || !Double.isFinite(slab)) {
                return null;
            }
            Double molecule = askDouble("Total energy of the free molecule (eV)", "");
            if (molecule == null || !Double.isFinite(molecule)) {
                return null;
            }
            Double corr = askDouble("Correction term (dZPE - T*dS) in eV (0 = none)", "0.0");
            if (corr == null) {
                return null;
            }
            parameters.withDefectEnergyEv(total).withHostEnergyEv(slab)
                    .withMoleculeEnergyEv(molecule).withCorrectionsEv(corr);
            break;
        }
        case BARRIER_DIFFUSION: {
            Double barrier = askDouble("Migration barrier Ea in eV (>= 0)", "");
            if (barrier == null || !Double.isFinite(barrier)) {
                return null;
            }
            Double hop = askDouble("Hop length in Angstrom", "2.0");
            if (hop == null || !Double.isFinite(hop)) {
                return null;
            }
            Double attempt = askDouble("Attempt frequency in THz", "10.0");
            if (attempt == null || !Double.isFinite(attempt)) {
                return null;
            }
            Double temperature = askDouble("Temperature in K", "300.0");
            if (temperature == null) {
                return null;
            }
            Integer dimension = askInteger("Hopping dimensionality (1, 2, or 3)", 3);
            if (dimension == null) {
                return null;
            }
            parameters.withBarrierEv(barrier).withHopAngstrom(hop).withAttemptThz(attempt)
                    .withTemperatureK(temperature).withHopDimension(dimension);
            break;
        }
        case CONSTRAINTS_PREVIEW: {
            String spec = askText("Constraint spec, e.g. 1:000; 2-4:110 "
                    + "(1-based atoms, 0=frozen/1=free)", "");
            if (spec == null) {
                return null;
            }
            String mode = askText("Calculation mode that uses if_pos "
                    + "(relax, vc-relax, md)", "relax");
            if (mode == null) {
                return null;
            }
            parameters.withConstraintSpec(spec).withConstraintMode(mode);
            break;
        }
        case HUBBARD_HP_DRAFT: {
            Integer nq1 = askInteger("hp.x q-grid nq1 (1-16; converge U against it)", 2);
            if (nq1 == null) {
                return null;
            }
            Integer nq2 = askInteger("hp.x q-grid nq2 (1-16)", 2);
            if (nq2 == null) {
                return null;
            }
            Integer nq3 = askInteger("hp.x q-grid nq3 (1-16)", 2);
            if (nq3 == null) {
                return null;
            }
            parameters.withExxNqGrid(nq1, nq2, nq3);
            break;
        }
        case SUPERCELL_PREVIEW: {
            String spec = askText("Supercell matrix rows (integers), e.g. "
                    + "2 0 0; 0 2 0; 0 0 1", "");
            if (spec == null) {
                return null;
            }
            parameters.withSupercellSpec(spec);
            break;
        }
        case TRAJECTORY_WINDOW_SCAN: {
            Integer start = askInteger("Start frame (1-based)", 1);
            if (start == null) {
                return null;
            }
            Integer stride = askInteger("Stride between sampled frames (>= 1)", 1);
            if (stride == null) {
                return null;
            }
            parameters.withWindowStartFrame(start).withWindowStride(stride);
            break;
        }
        case RAMAN_IR_SPECTRUM: {
            String channel = askText("Spectrum channel (ir or raman)", "ir");
            if (channel == null) {
                return null;
            }
            Double fwhm = askDouble("Lorentzian FWHM in cm-1 (0 < w <= 200)", "5.0");
            if (fwhm == null || !Double.isFinite(fwhm)) {
                return null;
            }
            parameters.withSpectrumChannel(channel).withFwhmCm1(fwhm);
            break;
        }
        case ARRAY_SWEEP_PLAN: {
            String keyword = askText("QE keyword to sweep (e.g. ecutwfc)", "ecutwfc");
            if (keyword == null) {
                return null;
            }
            Double start = askDouble("Start value", "30.0");
            if (start == null || !Double.isFinite(start)) {
                return null;
            }
            Double step = askDouble("Step between tasks (non-zero)", "10.0");
            if (step == null || !Double.isFinite(step)) {
                return null;
            }
            Integer count = askInteger("Number of tasks (2-50)", 6);
            if (count == null) {
                return null;
            }
            String base = askText("Job base name ([A-Za-z0-9._-], 1-32 chars)", "sweep");
            if (base == null) {
                return null;
            }
            parameters.withSeriesKeyword(keyword).withSeriesStart(start)
                    .withSeriesStep(step).withSeriesCount(count).withJobBaseName(base);
            break;
        }
        case KEYWORD_HELP: {
            String keyword = askText("pw.x namelist keyword to look up "
                    + "(e.g. ecutwfc, conv_thr)", "ecutwfc");
            if (keyword == null) {
                return null;
            }
            parameters.withSeriesKeyword(keyword);
            break;
        }
        case HYPERFINE_LOOKUP: {
            String isotope = askText("Isotope label (covered: 1H 13C 14N 15N 29Si 31P)",
                    "13C");
            if (isotope == null) {
                return null;
            }
            Double rho = askDouble("GIPAW nuclear spin density rho(0) in a.u.^-3 "
                    + "(leave empty to only look up gN)", "");
            parameters.withIsotopeLabel(isotope)
                    .withNuclearSpinDensity(rho == null ? Double.NaN : rho.doubleValue());
            break;
        }
        case PHONON_MODE_FRAMES: {
            Integer mode = askInteger("Mode index as printed in the dynmat file (1-based)",
                    1);
            if (mode == null) {
                return null;
            }
            Double amplitude = askDouble("Visual amplitude in Angstrom (0 < A <= 5.0; "
                    + "mass-weighted modes are rendered, not physically scaled)", "0.5");
            if (amplitude == null) {
                return null;
            }
            Integer frames = askInteger("Frames per oscillation period (3-240)", 12);
            if (frames == null) {
                return null;
            }
            parameters.withModeIndex(mode).withFrameAmplitudeAng(amplitude)
                    .withFrameCount(frames);
            break;
        }
        case DEFECT_PREVIEW: {
            String type = askText("Defect type: 'vacancy' or 'substitution'", "vacancy");
            if (type == null) {
                return null;
            }
            int defaultIndex = 1;
            Integer index = askInteger("Target atom index (1-based)", defaultIndex);
            if (index == null) {
                return null;
            }
            parameters.withDefectType(type).withAtomIndices(index, 0, 0, 0);
            if ("substitution".equals(type.trim().toLowerCase(java.util.Locale.ROOT))) {
                String element = askText("Replacement element symbol (e.g. B, N, Al)", "B");
                if (element == null) {
                    return null;
                }
                parameters.withDefectElement(element);
            }
            Integer charge = askInteger("Defect charge state (metadata only, not written "
                    + "into the input)", 0);
            if (charge == null) {
                return null;
            }
            parameters.withDefectCharge(charge);
            break;
        }
        case LAMMPS_DATA_REVIEW: {
            String style = askText("LAMMPS atom_style (atomic, charge, molecular, full) - "
                    + "the data file does NOT record it", "atomic");
            if (style == null) {
                return null;
            }
            parameters.withSeriesKeyword(style);
            break;
        }
        case MOIRE_TWIST_PREVIEW: {
            Integer m = askInteger("Commensurate index m (1-128; swapped with n if n > m)",
                    2);
            if (m == null) {
                return null;
            }
            Integer n = askInteger("Commensurate index n (1-128)", 1);
            if (n == null) {
                return null;
            }
            Double ratio = askDouble("Second-layer lattice constant a2 / a1 "
                    + "(blank = identical lattices)", "");
            parameters.withMoireIndices(m, n)
                    .withLatticeRatio(ratio == null ? 1.0 : ratio.doubleValue());
            break;
        }
        case GB_CSL_PREVIEW: {
            Integer u = askInteger("CSL rotation axis component u (-16..16)", 0);
            if (u == null) {
                return null;
            }
            Integer v = askInteger("CSL rotation axis component v (-16..16)", 0);
            if (v == null) {
                return null;
            }
            Integer w = askInteger("CSL rotation axis component w (-16..16)", 1);
            if (w == null) {
                return null;
            }
            Integer m = askInteger("Ranganathan index m (1-1024)", 3);
            if (m == null) {
                return null;
            }
            Integer n = askInteger("Ranganathan index n (1-1024)", 1);
            if (n == null) {
                return null;
            }
            parameters.withCslAxis(u, v, w).withMoireIndices(m, n);
            break;
        }
        case QE_VERSION_CHECK: {
            String version = askText("Target QE minor version (curated window: 7.2, 7.3, "
                    + "7.4 or 7.5; blank audits against the uniform window)", "");
            if (version == null) {
                return null;
            }
            parameters.withSeriesKeyword(version);
            break;
        }
        case TEMPLATE_LIBRARY: {
            String name = askText("Template name (blank lists all; curated: scf-basic, "
                    + "relax-bfgs, vc-relax-crystal, bands-path, nscf-dos, phonon-gamma0)",
                    "");
            if (name == null) {
                return null;
            }
            parameters.withSeriesKeyword(name);
            break;
        }
        default:
            break;
        }
        return parameters;
    }

    /** Free-text prompt; returns null on cancel. */
    private String askText(String prompt, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle("Analysis parameter");
        dialog.setHeaderText(prompt);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        return result.isEmpty() ? null : result.get();
    }

    /** Parses "A,B[,C[,D]]" 1-based atom indices; absent C/D become 0. */
    private int[] askAtomIndices() {
        TextInputDialog dialog = new TextInputDialog("1,2,3,4");
        dialog.setTitle("Geometry measurement");
        dialog.setHeaderText("Atom indices A,B,C,D (1-based); omit C/D for a bond measurement");
        dialog.setContentText("Indices:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        String[] parts = result.get().split(",");
        if (parts.length < 2 || parts.length > 4) {
            showMessage("Geometry measurement", "Provide 2 to 4 comma-separated indices.",
                    AlertType.ERROR);
            return null;
        }
        int[] indices = {0, 0, 0, 0};
        for (int i = 0; i < parts.length; i++) {
            String token = parts[i].trim();
            if (token.isEmpty() && i >= 2) {
                continue; // trailing ", ," means absent C/D
            }
            try {
                indices[i] = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                showMessage("Geometry measurement", "Not an integer index: '" + token + "'",
                        AlertType.ERROR);
                return null;
            }
        }
        return indices;
    }

    private Integer askInteger(String prompt, int defaultValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(defaultValue));
        dialog.setTitle("Analysis parameter");
        dialog.setHeaderText(prompt);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(result.get().trim());
        } catch (NumberFormatException ex) {
            showMessage("Analysis parameter", "Not an integer: " + result.get(), AlertType.ERROR);
            return null;
        }
    }

    /** Returns null when cancelled; empty input means "not supplied". */
    private Double askDouble(String prompt, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle("Analysis parameter");
        dialog.setHeaderText(prompt);
        dialog.setContentText("Value:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        String text = result.get().trim();
        if (text.isEmpty()) {
            return null; // Interpreted by the caller as "use defaults".
        }
        try {
            return Double.valueOf(text.replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            showMessage("Analysis parameter", "Not a number: " + text, AlertType.ERROR);
            return null;
        }
    }

    /**
     * Shows the report (including its provenance section when present, Roadmap
     * #128). CSV export, pp.x saving, and whole-report saving all stay explicit
     * user actions; the dialog itself writes nothing.
     */
    private void showReport(AnalysisReport report) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Analyze QE results");
        dialog.setHeaderText(report.getTitle()
                + (report.isSuccess() ? "" : " - no usable result (see notes)"));

        TextArea area = new TextArea(report.renderFullText());
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'monospace';");
        VBox.setVgrow(area, Priority.ALWAYS);
        VBox content = new VBox(area);
        content.setPadding(new Insets(8.0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(REPORT_WIDTH, REPORT_HEIGHT);
        dialog.setResizable(true);

        ButtonType close = new ButtonType("Close", ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        ButtonType saveReport = new ButtonType("Save report ...", ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().add(saveReport);
        ButtonType exportCsv = null;
        ButtonType exportSvg = null;
        ButtonType saveInput = null;
        if (report.hasCsv()) {
            exportCsv = new ButtonType("Export CSV ...", ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().add(exportCsv);
            exportSvg = new ButtonType("Export SVG plot ...", ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().add(exportSvg);
        }
        if (report.getGeneratedInput() != null && !report.getGeneratedInput().isEmpty()) {
            saveInput = new ButtonType("Save input file ...", ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().add(saveInput);
        }

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        if (exportCsv != null && answer.get() == exportCsv) {
            saveText("Export analysis CSV", "analysis.csv",
                    report.getCsvLines().stream().collect(Collectors.joining("\n")) + "\n");
        } else if (exportSvg != null && answer.get() == exportSvg) {
            OperationResult<String> plotted = SvgSeriesPlotter.plot(report.getCsvLines(),
                    report.getTitle());
            if (!plotted.isSuccess() || plotted.getValue().isEmpty()) {
                showMessage("Export SVG plot", "Not a plottable two-column series: "
                        + plotted.getMessage(), AlertType.WARNING);
                showReport(report); // Return to the report so CSV export remains available.
                return;
            }
            saveText("Export SVG plot", "plot.svg", plotted.getValue().orElseThrow());
        } else if (saveInput != null && answer.get() == saveInput) {
            saveText("Save generated pp.x input", "pp.in", report.getGeneratedInput());
        } else if (answer.get() == saveReport) {
            saveText("Save analysis report", "report.txt", report.renderFullText());
        }
    }

    private void saveText(String title, String proposedName, String content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(proposedName);
        if (this.project.getDirectory() != null && this.project.getDirectory().isDirectory()) {
            chooser.setInitialDirectory(this.project.getDirectory());
        }
        File target = chooser.showSaveDialog(this.stage);
        if (target == null) {
            return;
        }
        try {
            AtomicFileWriter.writeUtf8(target.toPath(), content);
            showMessage(title, "Wrote " + target.getAbsolutePath(), AlertType.INFORMATION);
        } catch (IOException ex) {
            showMessage(title, "Could not write " + target.getName() + ": " + ex.getMessage(),
                    AlertType.ERROR);
        }
    }

    private void showMessage(String title, String message, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setPrefWidth(700.0);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
