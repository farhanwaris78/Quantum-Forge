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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.app.project.editor.result.geometry.GeometryMeasurer;
import quantumforge.app.project.viewer.result.special.EffectiveMassTensor;
import quantumforge.app.project.viewer.result.special.HyperfineMapper;
import quantumforge.com.math.SymmetricEigen3;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.builder.CifStructureReader;
import quantumforge.builder.SdfStructureReader;
import quantumforge.builder.TransformJournal;
import quantumforge.builder.JournalReplayMath;
import quantumforge.builder.CslSigmaMath;
import quantumforge.builder.SlabMillerMath;
import quantumforge.builder.MoireTwistMath;
import quantumforge.builder.QEBatteryVoltage;
import quantumforge.builder.QECitationManager;
import quantumforge.builder.adsorption.MoleculeAdsorber;
import quantumforge.builder.QEHullThermodynamics;
import quantumforge.builder.QEPointDefectBuilder;
import quantumforge.export.ELateTensorDraft;
import quantumforge.export.MethodsTextBuilder;
import quantumforge.export.RoCrateExporter;
import quantumforge.builder.QEDiffusionBarrierLink;
import quantumforge.builder.SupercellMatrixValidator;
import quantumforge.builder.QEThermochemistryMath;
import quantumforge.builder.ExtXyzCellExporter;
import quantumforge.builder.LammpsDataReader;
import quantumforge.builder.PdbStructureReader;
import quantumforge.builder.PoscarStructureReader;
import quantumforge.builder.QEConstraintSpec;
import quantumforge.builder.QEIonicConstraintManager;
import quantumforge.hpc.ArrayDeckTemplate;
import quantumforge.hpc.ArraySubmitPlan;
import quantumforge.hpc.ArraySweepPlanner;
import quantumforge.hpc.ArrayTaskIntent;
import quantumforge.hpc.SiteProfile;
import quantumforge.hpc.SiteProfileValidator;
import quantumforge.input.QEExxPlanner;
import quantumforge.neural.MlModelManifest;
import quantumforge.input.CpInputPlanner;
import quantumforge.input.GipawInputPlanner;
import quantumforge.input.TurboLanczosInputPlanner;
import quantumforge.input.NebPathAudit;
import quantumforge.input.Wannier90WinPlanner;
import quantumforge.input.XspectraInputPlanner;
import quantumforge.input.QEEsmAuditor;
import quantumforge.input.QEHubbardPlanner;
import quantumforge.input.QEInput;
import quantumforge.input.QEKeywordHelp;
import quantumforge.input.QEWorkflowTemplateLibrary;
import quantumforge.input.QEInputDiffPreview;
import quantumforge.input.QEKpointMeshAdvisor;
import quantumforge.input.QEPpChargePotentialBuilder;
import quantumforge.input.QEPpWavefunctionBuilder;
import quantumforge.hpc.PoolDivisorMath;
import quantumforge.hpc.JobDbSchema;
import quantumforge.hpc.JobQueueAudit;
import quantumforge.hpc.JobState;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.hpc.SchedulerAdapters;
import quantumforge.hpc.RemoteJobMonitor;
import quantumforge.hpc.ResultSyncManifest;
import quantumforge.remote.OptimadeQueryBuilder;
import quantumforge.remote.OptimadeStructuresParser;
import quantumforge.remote.MpApiQueryBuilder;
import quantumforge.remote.MpSummaryParser;
import quantumforge.input.NebInputPlanner;
import quantumforge.remote.ArrayJobPlan;
import quantumforge.remote.ContainerLaunchBridge;
import quantumforge.remote.ContainerProfileSpec;
import quantumforge.remote.JobCancelPlan;
import quantumforge.remote.JobStateGuard;
import quantumforge.remote.MonitorPollPlan;
import quantumforge.remote.SftpTransferPlan;
import quantumforge.remote.SiteProfileSpec;
import quantumforge.remote.SlurmScriptBuilder;
import quantumforge.remote.SshTargetSpec;
import quantumforge.remote.SyncManifestBuilder;
import quantumforge.com.math.QEUnits;
import quantumforge.input.QESCFInput;
import quantumforge.input.QEVersionRuleCatalog;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.schema.QEAuxSchema;
import quantumforge.input.schema.QECardSchema;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.input.schema.QEThermoPwSchema;
import quantumforge.input.validation.QEAuxDeckAudit;
import quantumforge.input.validation.QECardAudit;
import quantumforge.input.validation.QESchemaValidator;
import quantumforge.input.validation.QEThermoPwDeckAudit;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.WorkspaceLightIndex;
import quantumforge.project.property.ProjectEnergies;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;
import quantumforge.pseudo.PseudoFamilyValidator;
import quantumforge.run.parser.BandGapParser;
import quantumforge.run.parser.BoltzTrap2TraceParser;
import quantumforge.run.parser.CubeGridReader;
import quantumforge.run.parser.ElasticParser;
import quantumforge.run.parser.FinalGeometryTransaction;
import quantumforge.run.parser.GeometryConvergenceValidator;
import quantumforge.run.parser.PhononDosThermodynamics;
import quantumforge.run.parser.QEAcousticSumRuleValidator;
import quantumforge.run.parser.QEBandsDataParser;
import quantumforge.run.parser.QEBerryPolarizationParser;
import quantumforge.run.parser.QEBornChargeDielectricParser;
import quantumforge.run.parser.QECarParrinelloParser;
import quantumforge.run.parser.QECastepLogParser;
import quantumforge.run.parser.QEDynmatModesParser;
import quantumforge.run.parser.QERamanIRSpectraParser;
import quantumforge.run.parser.QETimingParser;
import quantumforge.run.parser.PhononFrameSynthesis;
import quantumforge.run.parser.QEElasticStabilityValidator;
import quantumforge.run.parser.QEEliashbergTcCalculator;
import quantumforge.run.parser.QEGipawNmrParser;
import quantumforge.run.parser.QEGridDensityDifference;
import quantumforge.run.parser.QEHubbardHpParser;
import quantumforge.run.parser.QELammpsThermoParser;
import quantumforge.run.parser.QEMagneticMomentParser;
import quantumforge.run.parser.QEMdDiffusionMsdParser;
import quantumforge.run.parser.QEPdosParser;
import quantumforge.run.parser.QEPhono3pyKappaParser;
import quantumforge.run.parser.QEPhonopyForceSetsWriter;
import quantumforge.run.parser.PhonopyForceSetsReader;
import quantumforge.run.parser.QESlabPlateauDiagnostic;
import quantumforge.run.parser.QESmearingConvergenceAnalyzer;
import quantumforge.run.parser.QETimingResourceParser;
import quantumforge.run.parser.QETensorAnalyzer;
import quantumforge.run.parser.TrajectoryIndexReader;
import quantumforge.run.parser.TrajectoryWindowReader;
import quantumforge.run.parser.EnergySeriesComparer;
import quantumforge.neural.ExtXyzDatasetValidator;
import quantumforge.neural.CompositionalBaselineMath;
import quantumforge.run.parser.SeriesAlignmentMath;
import quantumforge.run.parser.BandsFermiReviewMath;
import quantumforge.run.parser.BandGapBandMath;
import quantumforge.run.parser.OccupationLevelsParser;
import quantumforge.run.parser.ScfConvergenceAnalyzer;
import quantumforge.run.parser.ScfIterationRecord;
import quantumforge.symmetry.MagneticSpaceGroupDetector;
import quantumforge.symmetry.QEBrillouinZoneGeometry;
import quantumforge.run.parser.QEPwcondConductanceParser;
import quantumforge.run.parser.QEElateAnalyzer;
import quantumforge.run.parser.QEThermoPwElasticParser;
import quantumforge.run.parser.QEThermoPwEosParser;
import quantumforge.run.parser.QEThermoPwRunScanner;
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
        THERMO_PW_DECK_AUDIT("thermo_pw thermo_control grammar audit (mined)"),
        THERMO_PW_RUN_SUMMARY("thermo_pw run summary (directory census + stdout EOS extracts)"),
        ELATE_TENSOR_ANALYSIS("ELATE elastic tensor analysis (averages/eigen/extrema)"),
        QE_CARD_AUDIT("pw.x card grammar audit (mined read_cards)"),
        QE_AUX_DECK_AUDIT("auxiliary QE input grammar audit (24 programs)"),
        THERMOPW_EOS("thermo_pw equation of state"),
        PHONO3PY_KAPPA("phono3py lattice thermal conductivity"),
        BOLTZTRAP2_TRANSPORT("BoltzTraP2 transport tables (.trace/.condtens)"),
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
        SERIES_PLAN("Convergence series plan (preview)"),
        PHONON_MODES("Phonon eigenvector audit (dynmat.x)"),
        VOLTAGE_PROFILE("Battery voltage profile from hull CSV"),
        ADSORPTION_PREVIEW("Molecule adsorption preview (site/collision check)"),
        SITE_PROFILE_CHECK("HPC site profile validation (scheduler/container)"),
        ML_MODEL_CHECK("ML potential model-manifest validation"),
        EXX_GUIDANCE("Exact-exchange (hybrid) k/q grid guidance"),
        BZ_GEOMETRY("Brillouin-zone polyhedron (lattice geometry)"),
        BAND_GAP("Band-gap summary from pw.x log"),
        DOS_INTEGRATION("Projected DOS integration (projwfc.x)"),
        ELASTIC_DIRECTIONAL("Directional Young\u0027s modulus (elastic tensor)"),
        METHODS_TEXT("Methods-section draft (input transcription)"),
        RO_CRATE("RO-Crate metadata draft (artifact checksums)"),
        DEFECT_FORMATION("Defect formation energy (explicit terms, eV)"),
        ADSORPTION_ENERGY("Adsorption energy (explicit terms, eV)"),
        BARRIER_DIFFUSION("Barrier-based diffusivity estimate (Arrhenius hop)"),
        EFFECTIVE_MASS("Effective-mass tensor fit (k,E series)"),
        CONSTRAINTS_PREVIEW("Ionic constraint preview (if_pos flags)"),
        PHONOPY_DATA_REVIEW("phonopy FORCE_SETS review (finite displacements)"),
        TRAJECTORY_INDEX("XYZ trajectory streaming index (frame offsets)"),
        MLP_DATASET_CHECK("ML dataset validation (extXYZ schema/labels/leaks)"),
        ENERGY_SERIES_COMPARE("Energy-series comparison (same grid, eV deltas)"),
        TENSOR_EIGEN("Symmetric 3x3 tensor eigenanalysis"),
        PHONON_MODE_FRAMES("Phonon mode animation frames (multi-frame XYZ)"),
        HYPERFINE_LOOKUP("Hyperfine isotope g-factor + Fermi contact A_iso"),
        KEYWORD_HELP("pw.x keyword reference (offline curated subset)"),
        ARRAY_SWEEP_PLAN("Scheduler array sweep manifest (JSONL + sbatch preview)"),
        CELL_EXTXYZ_EXPORT("Extended-XYZ export of the live cell (geometry only)"),
        RAMAN_IR_SPECTRUM("Powder IR/Raman spectrum (Lorentzian broadening)"),
        TRAJECTORY_WINDOW_SCAN("Trajectory window scan (bbox/centroid per sampled frame)"),
        TENSOR_DIRECTIONAL("Tensor directional surface n^T.T.n (eigen basis)"),
        DENSITY_DIFFERENCE("Grid density difference (compatible CUBE pair)"),
        SUPERCELL_PREVIEW("Supercell transformation preview (integer 3x3 matrix)"),
        HUBBARD_HP_DRAFT("hp.x input draft (existing DFT+U context required)"),
        TIMING_RESOURCE("pw.x timing/resource table (CPU/WALL per routine)"),
        WORKSPACE_SEARCH("Light workspace catalogue (project dir, status markers)"),
        TEMPLATE_LIBRARY("Curated workflow templates (with prerequisites/pitfalls)"),
        POSCAR_REVIEW("POSCAR structure review (VASP 4/5, fail-closed parser)"),
        ELASTIC_ELATE_DRAFT("ELATE elastic tensor draft (Voigt, stability-gated)"),
        SPIN_CUBE_MAGNETIZATION("Spin magnetization from paired up/down CUBE files"),
        ESM_SLAB_CHECK("ESM/slab readiness audit (assume_isolated/esm_bc + vacuum)"),
        MOIRE_TWIST_PREVIEW("Commensurate twist preview (exact hexagonal (m,n) math)"),
        PDB_REVIEW("PDB structure review (fail-closed, no element guessing)"),
        LAMMPS_DATA_REVIEW("LAMMPS data-file review (explicit atom_style, no guessing)"),
        CP_INPUT_DRAFT("cp.x Car-Parrinello input draft (required-edit guarded)"),
        W90_WIN_DRAFT("Wannier90 .win draft (mesh-echoed, required-edit guarded)"),
        GB_CSL_PREVIEW("Grain-boundary CSL rotation preview (exact Ranganathan law)"),
        QE_VERSION_CHECK("QE version keyword audit (curated 7.2-7.5 window snapshot)"),
        MPI_POOLS_ADVISOR("MPI pool-divisor audit (exact uniform mesh, -nk/-npool)"),
        UNIT_CONVERT("Scientific unit conversion (curated registry, pinned constants)"),
        LOG_ERROR_DIAGNOSIS("QE log error diagnosis (curated signature KB, 7.x window)"),
        XSPECTRA_INPUT_DRAFT("xspectra.x XANES input draft (required-edit guarded)"),
        GIPAW_INPUT_DRAFT("gipaw.x NMR/EFG input draft (required-edit guarded)"),
        TDDFPT_INPUT_DRAFT("turbo_lanczos.x LR-TDDFT input draft (required-edit guarded)"),
        SLAB_MILLER_PREVIEW("Surface Miller plane geometry (d-spacing, normal, ESM gate)"),
        CIF_REVIEW("CIF structure review (fail-closed subset, no element guessing)"),
        MOL_SDF_REVIEW("MOL/SDF molecule review (V2000 single-record subset)"),
        ML_DATASET_BASELINE("ML dataset compositional baseline (physics-informed screen)"),
        SERIES_REF_ALIGN("Two-series explicit reference alignment (Fermi/VBM/vacuum/user)"),
        BANDS_FERMI_REVIEW("Band structure E-E_F review (explicit Fermi, crossing stats)"),
        BAND_GAP_BANDS("Band-gap classification (valence count, metallicity tolerance)"),
        PROVENANCE_JOURNAL_REVIEW("Structure provenance journal verify (hash-chained)"),
        JOB_DB_SCHEMA_PLAN("Job database schema + migration plan (SQLite WAL target)"),
        OPTIMADE_QUERY_DRAFT("OPTIMADE /structures query draft (validated, unfetched)"),
        OCCUPATION_LEVELS_REVIEW("HOMO/LUMO occupation-level review (line-provenanced)"),
        OPTIMADE_RESPONSE_PARSE("OPTIMADE structures response parse (local JSON, unfetched)"),
        MP_SUMMARY_PARSE("Materials Project summary response parse (local JSON, unfetched)"),
        MP_QUERY_DRAFT("Materials Project summary query draft (validated, unfetched, key-safe)"),
        SSH_CONFIG_DRAFT("SSH target ssh_config draft (publickey-only, validated)"),
        SFTP_TRANSFER_PLAN("SFTP staging plan (upload, hash-pinned, explicit overwrite)"),
        SLURM_SCRIPT_DRAFT("SLURM submit-script draft (typed directives, reviewed payload)"),
        KMESH_CONVERGENCE_PLAN("k-mesh convergence ladder plan (spacing arithmetic, energies unaudited)"),
        SITE_PROFILE_DRAFT("HPC site-profile draft (typed scheduler/launcher, owned grammar)"),
        NEB_INPUT_DRAFT("neb.x &PATH namelist draft (typed, image-checklisted)"),
        JOB_CANCEL_PLAN("Scheduler job-cancellation review plan (typed id, retype-confirm)"),
        SCHEDULER_ADAPTER_AUDIT("Scheduler adapter registry audit (adapter-owned grammar census, per-id verdicts)"),
        JOB_MONITOR_AUDIT("Remote monitor runtime audit (stated backoff/absence/signal semantics, no contact)"),
        SYNC_RUNTIME_AUDIT("Result-sync runtime audit (probed per-workflow manifests, verdict boundary, no transfer)"),
        MONITOR_POLL_PLAN("Remote-monitoring poll plan (bounded backoff, offline semantics)"),
        SYNC_MANIFEST_DRAFT("Selective result-sync manifest draft (role-per-name, intent not facts)"),
        SMEARING_LADDER_PLAN("Smearing down-ladder plan (degauss Ry/eV, never declares convergence)"),
        CUTOFF_LADDER_PLAN("Cutoff convergence ladder (ecutwfc Ry/eV + implied ecutrho)"),
        ARRAY_JOB_PLAN("Scheduler array-job plan (1-based mapping, verbatim sweep tokens)"),
        ARRAY_JOB_AUDIT("Scheduler array-products audit (both #100 mappings side by side, probed grammars)"),
        CONTAINER_PROFILE_DRAFT("Apptainer/Singularity profile draft (digest-pinned, MPI declared)"),
        JOB_STATE_GUARD("Job state transition/signal guard (typed edges, unknown-honest)"),
        PHONON_GRID_PLAN("Phonon q-grid ladder plan (k/q commensurability verdicts, named)"),
        CHECKPOINT_RESUBMIT_PLAN("Checkpoint resubmission advice (typed stop reason, no writes)"),
        JOB_QUEUE_AUDIT("Job queue store audit (read-only; raw malformed/duplicate/chain verdicts)"),
        WORKFLOW_EXPORT_AUDIT("Exported workflow audit (stage census, sync vs current, read-only)"),
        NEB_PATH_AUDIT("NEB path ladder audit (multi-frame XYZ geometry; verdicts, no edits)"),
        FINAL_GEOMETRY_APPLY("Final geometry transactional apply (staged, hashed, rollback-verified)");

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
            return this == MAGNETIZATION || this == BORN_DIELECTRIC || this == THERMOPW_EOS
                    || this == LOG_ERROR_DIAGNOSIS || this == OCCUPATION_LEVELS_REVIEW;
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
                    || this == SERIES_PLAN || this == ADSORPTION_PREVIEW || this == ML_MODEL_CHECK
                    || this == EXX_GUIDANCE || this == BZ_GEOMETRY || this == METHODS_TEXT
                    || this == RO_CRATE || this == DEFECT_FORMATION || this == ADSORPTION_ENERGY
                    || this == BARRIER_DIFFUSION || this == CONSTRAINTS_PREVIEW
                    || this == PHONON_MODE_FRAMES || this == HYPERFINE_LOOKUP
                    || this == KEYWORD_HELP || this == ARRAY_SWEEP_PLAN
                    || this == CELL_EXTXYZ_EXPORT || this == DENSITY_DIFFERENCE
                    || this == SUPERCELL_PREVIEW || this == HUBBARD_HP_DRAFT
                    || this == WORKSPACE_SEARCH || this == TEMPLATE_LIBRARY
                    || this == SPIN_CUBE_MAGNETIZATION || this == ESM_SLAB_CHECK
                    || this == MOIRE_TWIST_PREVIEW || this == CP_INPUT_DRAFT
                    || this == W90_WIN_DRAFT || this == QE_VERSION_CHECK
                    || this == MPI_POOLS_ADVISOR || this == GB_CSL_PREVIEW
                    || this == UNIT_CONVERT || this == XSPECTRA_INPUT_DRAFT
                    || this == GIPAW_INPUT_DRAFT || this == SLAB_MILLER_PREVIEW
                    || this == TDDFPT_INPUT_DRAFT || this == JOB_DB_SCHEMA_PLAN
                    || this == OPTIMADE_QUERY_DRAFT || this == MP_QUERY_DRAFT
                    || this == SSH_CONFIG_DRAFT || this == SFTP_TRANSFER_PLAN
                    || this == SLURM_SCRIPT_DRAFT || this == KMESH_CONVERGENCE_PLAN
                    || this == SITE_PROFILE_DRAFT || this == NEB_INPUT_DRAFT
                    || this == JOB_CANCEL_PLAN || this == MONITOR_POLL_PLAN
                    || this == SCHEDULER_ADAPTER_AUDIT || this == JOB_MONITOR_AUDIT
                    || this == SYNC_RUNTIME_AUDIT
                    || this == SYNC_MANIFEST_DRAFT || this == SMEARING_LADDER_PLAN
                    || this == CUTOFF_LADDER_PLAN || this == ARRAY_JOB_PLAN
                    || this == ARRAY_JOB_AUDIT
                    || this == CONTAINER_PROFILE_DRAFT || this == JOB_STATE_GUARD
                    || this == PHONON_GRID_PLAN || this == CHECKPOINT_RESUBMIT_PLAN
                    || this == WORKFLOW_EXPORT_AUDIT || this == FINAL_GEOMETRY_APPLY;
        }

        /** True for project-bound kinds that additionally parse a user data file. */
        public boolean needsDataFile() {
            return this == MD_MSD || this == INPUT_DIFF || this == ML_MODEL_CHECK
                    || this == PHONOPY_DATA_REVIEW || this == PHONON_MODE_FRAMES
                    || this == DENSITY_DIFFERENCE || this == SPIN_CUBE_MAGNETIZATION;
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
        private String submitScheduler = "";   // ARRAY_SWEEP_PLAN submit-lane review; blank = skip
        private double seriesStep = 10.0;
        private int seriesCount = 6;
        private double energyToleranceRyPerAtom = 1.0e-3;
        private double ionCharge = 1.0;
        private String moleculeName = "CO";
        private double adsorbHeight = 2.0;
        private double adsorbX = 0.5;
        private double adsorbY = 0.5;
        private int exxNq1 = 0; // 0 means "must be supplied explicitly"
        private int exxNq2 = 0;
        private int exxNq3 = 0;
        private double defectEnergyEv = Double.NaN;   // NaN means "must be supplied"
        private double hostEnergyEv = Double.NaN;
        private double moleculeEnergyEv = Double.NaN;
        private double chemPotSumEv = Double.NaN;
        private double vbmEv = Double.NaN;
        private double correctionsEv = 0.0;
        private double barrierEv = Double.NaN;
        private double hopAngstrom = Double.NaN;
        private double attemptThz = Double.NaN;
        private int hopDimension = 3;
        private double amplitudeAng = 0.5;       // visual mode-frame amplitude
        private String isotopeLabel = "";        // empty means "must be supplied"
        private double nuclearSpinDensity = Double.NaN; // NaN = A_iso not requested
        private int modeIndex = 1;               // 1-based dynmat mode index
        private int frameCount = 12;             // frames per oscillation period
        private String jobBaseName = "sweep";    // scheduler-array job base name
        private double fwhmCm1 = 5.0;            // Lorentzian full width at half maximum
        private String spectrumChannel = "ir";   // "ir" or "raman"
        private int windowStartFrame = 1;        // 1-based trajectory window start
        private int windowStride = 1;            // frames between samples
        private String supercellSpec = "";       // 3x3 integer matrix, rows by ';'
        private String constraintSpec = "";      // empty means "must be supplied"
        private String constraintMode = "relax"; // relax, vc-relax, or md
        private int moireM = 2;              // commensurate pair (m, n), m >= n >= 1
        private int moireN = 1;
        private double latticeRatio = 1.0;   // second-layer a2 / a1 (1.0 = identical)
        private int cslU = 0;                // CSL rotation axis [u v w]
        private int cslV = 0;
        private int cslW = 1;
        private int currentPools = 0;        // existing -nk pool count to audit (0 = none)
        private double quantityValue = Double.NaN; // UNIT_CONVERT: value (NaN = unset)
        private String unitFrom = "ry";      // UNIT_CONVERT: source unit token
        private String unitTo = "ev";        // UNIT_CONVERT: target unit token
        private int millerH = 1;             // SLAB_MILLER_PREVIEW: plane (h k l)
        private int millerK = 0;
        private int millerL = 0;
        private String alignMode = "";       // SERIES_ALIGN: FERMI, VBM, VACUUM, USER
        private double alignReferenceEv1 = Double.NaN; // series-1 reference, eV (NaN = unset)
        private double alignReferenceEv2 = Double.NaN; // series-2 reference, eV
        private double alignTargetEv = 0.0;  // USER-mode landing point, eV
        private int gapValenceBands = 0;      // BAND_GAP_BANDS: 0 = must be supplied
        private double gapToleranceEv = Double.NaN; // metallicity tolerance (NaN = unset)
        private double gapKTolerance = 1.0e-6;    // directness k tolerance
        private String optimadeBase = "https://optimade.materialsproject.org/v1";
        private String optimadeElements = "";   // csv, REQUIRED for the draft
        private int optimadeNeMax = 0;          // 0 omits nelements<=N
        private int optimadeNsMax = 0;          // 0 omits nsites<=M
        private int optimadePageLimit = 0;      // 0 maps to builder default
        private String mpBase = "https://api.materialsproject.org";
        private String mpMaterialIds = "";    // csv of mp-/mvc- ids, REQUIRED
        private String mpApiKey = "";         // never echoed back into reports
        private String sshAlias = "";           // SSH_CONFIG_DRAFT host alias
        private String sshHost = "";
        private String sshUser = "";
        private int sshPort = 22;
        private String sshIdentityFile = "";  // empty = agent/default keys noted
        private String sshKnownHosts = "";    // SSH bridge; blank = feasibility trail only
        private String sftpLocalName = "";     // SFTP_TRANSFER_PLAN project-relative file
        private String sftpRemotePath = "";    // absolute POSIX remote FILE path
        private boolean sftpOverwriteAllowed = false;  // default posture: refuse clobber
        private String sftpStagingRoot = "";           // SFTP bridge; blank = feasibility trail only
        private String slurmJobName = "";      // SLURM_SCRIPT_DRAFT owned directives
        private String slurmPartition = "";    // blank = directive omitted honestly
        private int slurmNodes = 1;
        private int slurmNtasks = 1;
        private String slurmWalltime = "";
        private String slurmModules = "";      // csv, blank = no-module comment
        private String slurmCommand = "";      // one analyst-reviewed payload line
        private String kmeshLadder = "";       // "4 4 4; 8 8 8; ..." rungs, order kept
        private String kmeshOffset = "";       // exactly three 0/1 shifts, never defaulted
        private String siteCluster = "";       // SITE_PROFILE_DRAFT owned values
        private String siteScheduler = "";     // typed enum: slurm/pbs/pjm/sge
        private String siteLauncher = "";      // typed enum: srun/mpirun/mpiexec
        private String sitePartition = "";     // blank = honest omission comment
        private String siteAccount = "";       // blank = honest omission comment
        private String siteScratchDir = "";    // absolute POSIX scratch root, required
        private int siteMaxNodes = 1;
        private String siteModules = "";       // csv, blank = none-declared comment
        private int nebNumImages = 5;          // NEB_INPUT_DRAFT: end points INCLUDED
        private int nebNstepPath = 100;
        private String nebOptScheme = "broyden";
        private String nebCiScheme = "no-ci";
        private double nebKMin = Double.NaN;   // NaN = unset; there is no honest default
        private double nebKMax = Double.NaN;
        private double nebDs = Double.NaN;
        private double nebPathThr = Double.NaN;
        private String cancelScheduler = "";   // JOB_CANCEL_PLAN typed scheduler
        private String cancelJobId = "";       // owned per-scheduler grammar
        private String cancelConfirm = "";     // must retype the id EXACTLY (untrimmed)
        private String schedulerAuditName = "";   // SCHEDULER_ADAPTER_AUDIT; blank = census of all
        private String schedulerAuditJobId = "";  // blank = census only, no per-id verdict
        private String monitorScheduler = "";     // JOB_MONITOR_AUDIT; blank = census of all four
        private String monitorJobId = "";         // blank = no status-command review line
        private String syncWorkflow = "";         // SYNC_RUNTIME_AUDIT; blank = census of all seven
        private String syncPrefix = "";           // blank = 'espresso' (the QE default, stated)
        private boolean syncIncludeLarge = false; // explicit opt-in; large files are skipped by default
        private double monitorInitialSec = 30.0;  // MONITOR_POLL_PLAN policy
        private double monitorMaxSec = 300.0;
        private double monitorFactor = 2.0;    // 1.0 = honest constant polling
        private int monitorMaxPolls = 60;
        private String syncRequired = "";      // SYNC_MANIFEST_DRAFT csv lists
        private String syncOptional = "";
        private String syncLarge = "";
        private String syncExcluded = "";
        private String smearScheme = "gaussian";  // SMEARING_LADDER_PLAN typed scheme
        private String smearLadder = "";          // descending degauss values in Ry
        private String cutoffLadder = "";         // ascending ecutwfc values in Ry
        private double cutoffRhoRatio = Double.NaN;  // REQUIRED, no invented default
        private String arrayBase = "";            // ARRAY_JOB_PLAN directory-seeding base
        private String arrayAuditBase = "";       // ARRAY_JOB_AUDIT; blank = canned 'sweep' example
        private int arrayAuditCount = 3;          // display-only task count, 1..50
        private String arrayValues = "";          // verbatim sweep tokens
        private boolean arraySlurmLine = false;   // opt-IN review line only
        private String containerRuntime = "";     // CONTAINER_PROFILE_DRAFT typed runtime
        private String containerImageRef = "";    // name:tag@sha256:<64hex> REQUIRED digest
        private String containerBinds = "";       // csv absolute POSIX, literal grammar
        private String containerExecCommand = ""; // blank = canned 'pw.x -i pw.in' preview
        private String containerMpiAnswer = "";   // exactly 'yes'/'no' - neutral refuses
        private String containerSiteProfile = ""; // CONTAINER_PROFILE_DRAFT bridge path; blank skips
        private String jobStateMode = "";         // JOB_STATE_GUARD: transition|signal
        private String jobStateFrom = "";
        private String jobStateTo = "";
        private String jobStateScheduler = "";
        private String jobStateSignal = "";
        private String phononLadder = "";       // PHONON_GRID_PLAN "2 2 2; 4 4 4" rungs
        private String checkpointPrefixOverride = "";  // CHECKPOINT_RESUBMIT_PLAN: blank = project prefix

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
        public String getSubmitScheduler() { return this.submitScheduler; }
        public double getSeriesStep() { return this.seriesStep; }
        public int getSeriesCount() { return this.seriesCount; }
        public double getEnergyToleranceRyPerAtom() { return this.energyToleranceRyPerAtom; }
        public double getIonCharge() { return this.ionCharge; }
        public String getMoleculeName() { return this.moleculeName; }
        public double getAdsorbHeight() { return this.adsorbHeight; }
        public double getAdsorbX() { return this.adsorbX; }
        public double getAdsorbY() { return this.adsorbY; }
        public int getExxNq1() { return this.exxNq1; }
        public int getExxNq2() { return this.exxNq2; }
        public int getExxNq3() { return this.exxNq3; }
        public double getDefectEnergyEv() { return this.defectEnergyEv; }
        public double getHostEnergyEv() { return this.hostEnergyEv; }
        public double getMoleculeEnergyEv() { return this.moleculeEnergyEv; }
        public double getChemPotSumEv() { return this.chemPotSumEv; }
        public double getVbmEv() { return this.vbmEv; }
        public double getCorrectionsEv() { return this.correctionsEv; }
        public double getBarrierEv() { return this.barrierEv; }
        public double getHopAngstrom() { return this.hopAngstrom; }
        public double getAttemptThz() { return this.attemptThz; }
        public int getHopDimension() { return this.hopDimension; }
        public double getFrameAmplitudeAng() { return this.amplitudeAng; }
        public String getIsotopeLabel() { return this.isotopeLabel; }
        public double getNuclearSpinDensity() { return this.nuclearSpinDensity; }
        public int getModeIndex() { return this.modeIndex; }
        public int getFrameCount() { return this.frameCount; }
        public String getJobBaseName() { return this.jobBaseName; }
        public double getFwhmCm1() { return this.fwhmCm1; }
        public String getSpectrumChannel() { return this.spectrumChannel; }
        public int getWindowStartFrame() { return this.windowStartFrame; }
        public int getWindowStride() { return this.windowStride; }
        public String getSupercellSpec() { return this.supercellSpec; }
        public String getConstraintSpec() { return this.constraintSpec; }
        public String getConstraintMode() { return this.constraintMode; }
        public int getMoireM() { return this.moireM; }
        public int getMoireN() { return this.moireN; }
        public double getLatticeRatio() { return this.latticeRatio; }

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

        public AnalysisParameters withSubmitScheduler(String value) {
            this.submitScheduler = value == null ? "" : value;
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
        public AnalysisParameters withIonCharge(double value) {
            this.ionCharge = value;
            return this;
        }
        public AnalysisParameters withMoleculeName(String value) {
            this.moleculeName = value;
            return this;
        }
        public AnalysisParameters withAdsorbHeight(double value) {
            this.adsorbHeight = value;
            return this;
        }
        public AnalysisParameters withAdsorbX(double value) {
            this.adsorbX = value;
            return this;
        }
        public AnalysisParameters withAdsorbY(double value) {
            this.adsorbY = value;
            return this;
        }
        public AnalysisParameters withExxNqGrid(int q1, int q2, int q3) {
            this.exxNq1 = q1;
            this.exxNq2 = q2;
            this.exxNq3 = q3;
            return this;
        }
        public AnalysisParameters withDefectEnergyEv(double value) {
            this.defectEnergyEv = value;
            return this;
        }
        public AnalysisParameters withHostEnergyEv(double value) {
            this.hostEnergyEv = value;
            return this;
        }
        public AnalysisParameters withMoleculeEnergyEv(double value) {
            this.moleculeEnergyEv = value;
            return this;
        }
        public AnalysisParameters withChemPotSumEv(double value) {
            this.chemPotSumEv = value;
            return this;
        }
        public AnalysisParameters withVbmEv(double value) {
            this.vbmEv = value;
            return this;
        }
        public AnalysisParameters withCorrectionsEv(double value) {
            this.correctionsEv = value;
            return this;
        }
        public AnalysisParameters withBarrierEv(double value) {
            this.barrierEv = value;
            return this;
        }
        public AnalysisParameters withHopAngstrom(double value) {
            this.hopAngstrom = value;
            return this;
        }
        public AnalysisParameters withAttemptThz(double value) {
            this.attemptThz = value;
            return this;
        }
        public AnalysisParameters withHopDimension(int value) {
            this.hopDimension = value;
            return this;
        }
        public AnalysisParameters withFrameAmplitudeAng(double value) {
            this.amplitudeAng = value;
            return this;
        }
        public AnalysisParameters withIsotopeLabel(String value) {
            this.isotopeLabel = value == null ? "" : value;
            return this;
        }
        public AnalysisParameters withModeIndex(int value) {
            this.modeIndex = value;
            return this;
        }

        public AnalysisParameters withFrameCount(int value) {
            this.frameCount = value;
            return this;
        }

        public AnalysisParameters withJobBaseName(String value) {
            this.jobBaseName = value == null ? "" : value;
            return this;
        }

        public AnalysisParameters withFwhmCm1(double value) {
            this.fwhmCm1 = value;
            return this;
        }

        public AnalysisParameters withWindowStartFrame(int value) {
            this.windowStartFrame = value;
            return this;
        }

        public AnalysisParameters withWindowStride(int value) {
            this.windowStride = value;
            return this;
        }

        public AnalysisParameters withSupercellSpec(String value) {
            this.supercellSpec = value == null ? "" : value;
            return this;
        }

        public AnalysisParameters withSpectrumChannel(String value) {
            this.spectrumChannel = value == null ? "" : value;
            return this;
        }

        public AnalysisParameters withNuclearSpinDensity(double value) {
            this.nuclearSpinDensity = value;
            return this;
        }
        public AnalysisParameters withConstraintSpec(String value) {
            this.constraintSpec = value == null ? "" : value;
            return this;
        }
        public AnalysisParameters withMoireIndices(int m, int n) {
            this.moireM = m;
            this.moireN = n;
            return this;
        }

        public AnalysisParameters withLatticeRatio(double value) {
            this.latticeRatio = value;
            return this;
        }

        public AnalysisParameters withConstraintMode(String value) {
            this.constraintMode = value == null ? "relax" : value;
            return this;
        }

        public AnalysisParameters withCslAxis(int u, int v, int w) {
            this.cslU = u;
            this.cslV = v;
            this.cslW = w;
            return this;
        }

        public int[] getCslAxis() {
            return new int[] {this.cslU, this.cslV, this.cslW};
        }

        public AnalysisParameters withCurrentPools(int value) {
            this.currentPools = value;
            return this;
        }

        public int getCurrentPools() { return this.currentPools; }

        public AnalysisParameters withUnitConversion(double value, String from,
                String to) {
            this.quantityValue = value;
            this.unitFrom = from == null ? "" : from;
            this.unitTo = to == null ? "" : to;
            return this;
        }

        public double getQuantityValue() { return this.quantityValue; }
        public String getUnitFrom() { return this.unitFrom; }
        public String getUnitTo() { return this.unitTo; }

        public AnalysisParameters withMillerIndices(int h, int k, int l) {
            this.millerH = h;
            this.millerK = k;
            this.millerL = l;
            return this;
        }

        public int[] getMillerIndices() {
            return new int[] {this.millerH, this.millerK, this.millerL};
        }

        public String getAlignMode() { return this.alignMode; }
        public double getAlignReferenceEv1() { return this.alignReferenceEv1; }
        public double getAlignReferenceEv2() { return this.alignReferenceEv2; }
        public double getAlignTargetEv() { return this.alignTargetEv; }

        public AnalysisParameters withAlignment(String mode, double ref1,
                double ref2, double target) {
            this.alignMode = mode == null ? "" : mode.trim();
            this.alignReferenceEv1 = ref1;
            this.alignReferenceEv2 = ref2;
            this.alignTargetEv = target;
            return this;
        }

        public int getGapValenceBands() { return this.gapValenceBands; }
        public double getGapToleranceEv() { return this.gapToleranceEv; }
        public double getGapKTolerance() { return this.gapKTolerance; }

        public AnalysisParameters withGapClassification(int valenceBands,
                double toleranceEv, double kTolerance) {
            this.gapValenceBands = valenceBands;
            this.gapToleranceEv = toleranceEv;
            this.gapKTolerance = kTolerance;
            return this;
        }

        public String getOptimadeBase() { return this.optimadeBase; }
        public String getOptimadeElements() { return this.optimadeElements; }
        public int getOptimadeNeMax() { return this.optimadeNeMax; }
        public int getOptimadeNsMax() { return this.optimadeNsMax; }
        public int getOptimadePageLimit() { return this.optimadePageLimit; }

        public AnalysisParameters withOptimadeQuery(String base, String elements,
                int neMax, int nsMax, int pageLimit) {
            this.optimadeBase = base == null ? "" : base.trim();
            this.optimadeElements = elements == null ? "" : elements.trim();
            this.optimadeNeMax = neMax;
            this.optimadeNsMax = nsMax;
            this.optimadePageLimit = pageLimit;
            return this;
        }

        public String getMpBase() { return this.mpBase; }
        public String getMpMaterialIds() { return this.mpMaterialIds; }
        public String getMpApiKey() { return this.mpApiKey; }

        public AnalysisParameters withMpQuery(String base, String ids, String key) {
            this.mpBase = base == null ? "" : base.trim();
            this.mpMaterialIds = ids == null ? "" : ids.trim();
            this.mpApiKey = key == null ? "" : key;
            return this;
        }

        public String getSshAlias() { return this.sshAlias; }
        public String getSshHost() { return this.sshHost; }
        public String getSshUser() { return this.sshUser; }
        public int getSshPort() { return this.sshPort; }
        public String getSshIdentityFile() { return this.sshIdentityFile; }

        public AnalysisParameters withSshTarget(String alias, String host,
                String user, int port, String identityFile) {
            this.sshAlias = alias == null ? "" : alias;
            this.sshHost = host == null ? "" : host;
            this.sshUser = user == null ? "" : user;
            this.sshPort = port;
            this.sshIdentityFile = identityFile == null ? "" : identityFile;
            return this;
        }

        public String getSshKnownHosts() { return this.sshKnownHosts; }

        /** Blank known_hosts = feasibility trail only (the bridge is not exercised). */
        public AnalysisParameters withSshKnownHosts(String knownHosts) {
            this.sshKnownHosts = knownHosts == null ? "" : knownHosts;
            return this;
        }

        public String getSftpLocalName() { return this.sftpLocalName; }
        public String getSftpRemotePath() { return this.sftpRemotePath; }
        public String getSftpStagingRoot() { return this.sftpStagingRoot; }

        /** Blank staging root = feasibility trail only (the bridge is not exercised). */
        public AnalysisParameters withSftpStagingRoot(String stagingRoot) {
            this.sftpStagingRoot = stagingRoot == null ? "" : stagingRoot;
            return this;
        }
        public boolean isSftpOverwriteAllowed() { return this.sftpOverwriteAllowed; }

        public AnalysisParameters withSftpPlan(String localName, String remotePath,
                boolean overwriteAllowed) {
            this.sftpLocalName = localName == null ? "" : localName;
            this.sftpRemotePath = remotePath == null ? "" : remotePath;
            this.sftpOverwriteAllowed = overwriteAllowed;
            return this;
        }

        public String getSlurmJobName() { return this.slurmJobName; }
        public String getSlurmPartition() { return this.slurmPartition; }
        public int getSlurmNodes() { return this.slurmNodes; }
        public int getSlurmNtasks() { return this.slurmNtasks; }
        public String getSlurmWalltime() { return this.slurmWalltime; }
        public String getSlurmModules() { return this.slurmModules; }
        public String getSlurmCommand() { return this.slurmCommand; }

        public AnalysisParameters withSlurmScript(String jobName, String partition,
                int nodes, int ntasks, String walltime, String modules, String command) {
            this.slurmJobName = jobName == null ? "" : jobName;
            this.slurmPartition = partition == null ? "" : partition;
            this.slurmNodes = nodes;
            this.slurmNtasks = ntasks;
            this.slurmWalltime = walltime == null ? "" : walltime;
            this.slurmModules = modules == null ? "" : modules;
            this.slurmCommand = command == null ? "" : command;
            return this;
        }

        public String getKmeshLadder() { return this.kmeshLadder; }
        public String getKmeshOffset() { return this.kmeshOffset; }

        public AnalysisParameters withKmeshPlan(String ladder, String offset) {
            this.kmeshLadder = ladder == null ? "" : ladder;
            this.kmeshOffset = offset == null ? "" : offset;
            return this;
        }

        public String getSiteCluster() { return this.siteCluster; }
        public String getSiteScheduler() { return this.siteScheduler; }
        public String getSiteLauncher() { return this.siteLauncher; }
        public String getSitePartition() { return this.sitePartition; }
        public String getSiteAccount() { return this.siteAccount; }
        public String getSiteScratchDir() { return this.siteScratchDir; }
        public int getSiteMaxNodes() { return this.siteMaxNodes; }
        public String getSiteModules() { return this.siteModules; }

        public AnalysisParameters withSiteProfile(String cluster, String scheduler,
                String launcher, String partition, String account, String scratchDir,
                int maxNodes, String modules) {
            this.siteCluster = cluster == null ? "" : cluster;
            this.siteScheduler = scheduler == null ? "" : scheduler;
            this.siteLauncher = launcher == null ? "" : launcher;
            this.sitePartition = partition == null ? "" : partition;
            this.siteAccount = account == null ? "" : account;
            this.siteScratchDir = scratchDir == null ? "" : scratchDir;
            this.siteMaxNodes = maxNodes;
            this.siteModules = modules == null ? "" : modules;
            return this;
        }

        public int getNebNumImages() { return this.nebNumImages; }
        public int getNebNstepPath() { return this.nebNstepPath; }
        public String getNebOptScheme() { return this.nebOptScheme; }
        public String getNebCiScheme() { return this.nebCiScheme; }
        public double getNebKMin() { return this.nebKMin; }
        public double getNebKMax() { return this.nebKMax; }
        public double getNebDs() { return this.nebDs; }
        public double getNebPathThr() { return this.nebPathThr; }

        public AnalysisParameters withNebDraft(int numOfImages, int nstepPath,
                String optScheme, String ciScheme, double kMin, double kMax,
                double ds, double pathThr) {
            this.nebNumImages = numOfImages;
            this.nebNstepPath = nstepPath;
            this.nebOptScheme = optScheme == null ? "" : optScheme;
            this.nebCiScheme = ciScheme == null ? "" : ciScheme;
            this.nebKMin = kMin;
            this.nebKMax = kMax;
            this.nebDs = ds;
            this.nebPathThr = pathThr;
            return this;
        }

        public String getCancelScheduler() { return this.cancelScheduler; }
        public String getCancelJobId() { return this.cancelJobId; }
        public String getCancelConfirm() { return this.cancelConfirm; }

        public AnalysisParameters withJobCancel(String scheduler, String jobId,
                String confirmation) {
            this.cancelScheduler = scheduler == null ? "" : scheduler;
            this.cancelJobId = jobId == null ? "" : jobId;
            this.cancelConfirm = confirmation == null ? "" : confirmation;
            return this;
        }

        public String getSchedulerAuditName() { return this.schedulerAuditName; }
        public String getSchedulerAuditJobId() { return this.schedulerAuditJobId; }

        /** Blank scheduler = census of every registered adapter (explicit, not a default). */
        public AnalysisParameters withSchedulerAudit(String scheduler, String jobId) {
            this.schedulerAuditName = scheduler == null ? "" : scheduler;
            this.schedulerAuditJobId = jobId == null ? "" : jobId;
            return this;
        }

        public String getMonitorScheduler() { return this.monitorScheduler; }
        public String getMonitorJobId() { return this.monitorJobId; }

        /** Blank scheduler = census of all four signal tables (explicit, not a default). */
        public AnalysisParameters withMonitorAudit(String scheduler, String jobId) {
            this.monitorScheduler = scheduler == null ? "" : scheduler;
            this.monitorJobId = jobId == null ? "" : jobId;
            return this;
        }

        public String getSyncWorkflow() { return this.syncWorkflow; }
        public String getSyncPrefix() { return this.syncPrefix; }
        public boolean isSyncIncludeLarge() { return this.syncIncludeLarge; }

        /**
         * Blank workflow = census of all seven typed workflows (explicit, not a
         * default); blank prefix = 'espresso' (the QE default, stated);
         * includeLarge defaults FALSE - large payloads are opt-in by design.
         */
        public AnalysisParameters withSyncRuntimeAudit(String workflow, String prefix,
                boolean includeLarge) {
            this.syncWorkflow = workflow == null ? "" : workflow;
            this.syncPrefix = prefix == null ? "" : prefix;
            this.syncIncludeLarge = includeLarge;
            return this;
        }

        public double getMonitorInitialSec() { return this.monitorInitialSec; }
        public double getMonitorMaxSec() { return this.monitorMaxSec; }
        public double getMonitorFactor() { return this.monitorFactor; }
        public int getMonitorMaxPolls() { return this.monitorMaxPolls; }

        public AnalysisParameters withMonitorPoll(double initialSec, double maxSec,
                double factor, int maxPolls) {
            this.monitorInitialSec = initialSec;
            this.monitorMaxSec = maxSec;
            this.monitorFactor = factor;
            this.monitorMaxPolls = maxPolls;
            return this;
        }

        public String getSyncRequired() { return this.syncRequired; }
        public String getSyncOptional() { return this.syncOptional; }
        public String getSyncLarge() { return this.syncLarge; }
        public String getSyncExcluded() { return this.syncExcluded; }

        public AnalysisParameters withSyncManifest(String required, String optional,
                String large, String excluded) {
            this.syncRequired = required == null ? "" : required;
            this.syncOptional = optional == null ? "" : optional;
            this.syncLarge = large == null ? "" : large;
            this.syncExcluded = excluded == null ? "" : excluded;
            return this;
        }

        public String getSmearScheme() { return this.smearScheme; }
        public String getSmearLadder() { return this.smearLadder; }

        public AnalysisParameters withSmearingPlan(String scheme, String ladder) {
            this.smearScheme = scheme == null ? "" : scheme;
            this.smearLadder = ladder == null ? "" : ladder;
            return this;
        }

        public String getCutoffLadder() { return this.cutoffLadder; }
        public double getCutoffRhoRatio() { return this.cutoffRhoRatio; }

        public AnalysisParameters withCutoffPlan(String ladder, double rhoRatio) {
            this.cutoffLadder = ladder == null ? "" : ladder;
            this.cutoffRhoRatio = rhoRatio;
            return this;
        }

        public String getArrayBase() { return this.arrayBase; }
        public String getArrayValues() { return this.arrayValues; }
        public boolean isArraySlurmLine() { return this.arraySlurmLine; }

        public AnalysisParameters withArrayJob(String base, String values,
                boolean slurmLine) {
            this.arrayBase = base == null ? "" : base;
            this.arrayValues = values == null ? "" : values;
            this.arraySlurmLine = slurmLine;
            return this;
        }

        public String getArrayAuditBase() { return this.arrayAuditBase; }
        public int getArrayAuditCount() { return this.arrayAuditCount; }

        /** Blank base = the canned 'sweep' example; count 1..50 display rows. */
        public AnalysisParameters withArrayAudit(String base, int count) {
            this.arrayAuditBase = base == null ? "" : base;
            this.arrayAuditCount = count;
            return this;
        }

        public String getContainerRuntime() { return this.containerRuntime; }
        public String getContainerImageRef() { return this.containerImageRef; }
        public String getContainerBinds() { return this.containerBinds; }
        public String getContainerMpiAnswer() { return this.containerMpiAnswer; }

        public String getContainerExecCommand() { return this.containerExecCommand; }
        public String getContainerSiteProfile() { return this.containerSiteProfile; }

        /** Blank skips the launch bridge (stated as not_exercised, never silent). */
        public AnalysisParameters withContainerSiteProfile(String siteProfilePath) {
            this.containerSiteProfile = siteProfilePath == null ? "" : siteProfilePath;
            return this;
        }

        /** Blank = the canned 'pw.x -i pw.in' preview tokens (stated). */
        public AnalysisParameters withContainerExec(String commandTokens) {
            this.containerExecCommand = commandTokens == null ? "" : commandTokens;
            return this;
        }

        public AnalysisParameters withContainerProfile(String runtime, String imageRef,
                String binds, String mpiAnswer) {
            this.containerRuntime = runtime == null ? "" : runtime;
            this.containerImageRef = imageRef == null ? "" : imageRef;
            this.containerBinds = binds == null ? "" : binds;
            this.containerMpiAnswer = mpiAnswer == null ? "" : mpiAnswer;
            return this;
        }

        public String getJobStateMode() { return this.jobStateMode; }
        public String getJobStateFrom() { return this.jobStateFrom; }
        public String getJobStateTo() { return this.jobStateTo; }
        public String getJobStateScheduler() { return this.jobStateScheduler; }
        public String getJobStateSignal() { return this.jobStateSignal; }

        public AnalysisParameters withJobState(String mode, String from, String to,
                String scheduler, String signal) {
            this.jobStateMode = mode == null ? "" : mode;
            this.jobStateFrom = from == null ? "" : from;
            this.jobStateTo = to == null ? "" : to;
            this.jobStateScheduler = scheduler == null ? "" : scheduler;
            this.jobStateSignal = signal == null ? "" : signal;
            return this;
        }

        public String getPhononLadder() { return this.phononLadder; }

        public AnalysisParameters withPhononPlan(String ladder) {
            this.phononLadder = ladder == null ? "" : ladder;
            return this;
        }

        public String getCheckpointPrefixOverride() { return this.checkpointPrefixOverride; }

        public AnalysisParameters withCheckpointPrefix(String override) {
            this.checkpointPrefixOverride = override == null ? "" : override.trim();
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
        private final List<String> provenanceLines;

        public AnalysisReport(String title, boolean success, String text,
                              List<String> csvLines, String generatedInput) {
            this(title, success, text, csvLines, generatedInput, List.of());
        }

        public AnalysisReport(String title, boolean success, String text,
                              List<String> csvLines, String generatedInput,
                              List<String> provenanceLines) {
            this.title = title == null ? "Result analysis" : title;
            this.success = success;
            this.text = text == null ? "" : text;
            this.csvLines = csvLines == null ? List.of() : List.copyOf(csvLines);
            this.generatedInput = generatedInput;
            this.provenanceLines = provenanceLines == null ? List.of() : List.copyOf(provenanceLines);
        }

        public String getTitle() { return this.title; }
        public boolean isSuccess() { return this.success; }
        public String getText() { return this.text; }
        public List<String> getCsvLines() { return this.csvLines; }
        public String getGeneratedInput() { return this.generatedInput; }
        public boolean hasCsv() { return !this.csvLines.isEmpty(); }

        /**
         * Provenance records of the form "label: source" describing where the report
         * came from (analysis kind, source file or project context, producer). Empty
         * for reports constructed before the dispatch provenance pass (Roadmap #128).
         */
        public List<String> getProvenanceLines() { return this.provenanceLines; }
        public boolean hasProvenance() { return !this.provenanceLines.isEmpty(); }

        /** Returns a copy of this report with the given provenance records appended. */
        public AnalysisReport withProvenance(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return this;
            }
            List<String> merged = new ArrayList<>(this.provenanceLines);
            merged.addAll(lines);
            return new AnalysisReport(this.title, this.success, this.text, this.csvLines,
                    this.generatedInput, merged);
        }

        /**
         * Report text followed by a "--- Provenance ---" section when provenance
         * records exist. This is exactly the content shown in the report dialog and
         * written by the explicit "Save report ..." action.
         */
        public String renderFullText() {
            if (this.provenanceLines.isEmpty()) {
                return this.text;
            }
            StringBuilder builder = new StringBuilder(this.text);
            builder.append("\n\n--- Provenance ---\n");
            for (String line : this.provenanceLines) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    /**
     * Attaches the standard provenance records (Roadmap #128) to a dispatch result:
     * the analysis kind, the resolved source file or the project context, and the
     * deterministic producer. Fail-closed before any file existed still reports no
     * source rather than inventing one.
     */
    private static AnalysisReport attachProvenance(AnalysisReport report, AnalysisKind kind,
            File sourceFile, File projectDir) {
        if (report == null || kind == null) {
            return report;
        }
        List<String> lines = new ArrayList<>();
        lines.add("analysis: " + kind.name() + " - " + kind.getLabel());
        if (sourceFile != null) {
            lines.add("source: " + sourceFile.getAbsolutePath());
        } else if (projectDir != null) {
            lines.add("context: project directory " + projectDir.getAbsolutePath());
        } else {
            lines.add("context: in-memory project (no on-disk directory)");
        }
        lines.add("producer: quantumforge.run.ResultAnalysisService dispatch "
                + "(deterministic; no command executed; nothing written)");
        return report.withProvenance(lines);
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
        case THERMO_PW_DECK_AUDIT:
            return name.equals("thermo_control");
        case THERMO_PW_RUN_SUMMARY:
            return false; // manual select only: ANY *.out would else hijack run routing
        case ELATE_TENSOR_ANALYSIS:
            return false; // manual select only: shares the same *.out / .dat* surface
        case QE_CARD_AUDIT:
            return false; // manual select only: EVERY pw input is a candidate
        case QE_AUX_DECK_AUDIT:
            return false; // manual select only: any .in could be an aux deck

        case THERMOPW_EOS:
            return name.contains("eos") || name.contains("thermo");
        case PHONO3PY_KAPPA:
            return name.startsWith("kappa") || name.contains("thermal_conductivity");
        case BOLTZTRAP2_TRANSPORT:
            return name.endsWith(".trace") || name.contains("condtens")
                    || name.contains("seebeck");
        case ELIASHBERG_TC:
            return name.endsWith(".a2f") || name.contains("alpha2f") || name.contains("a2f");
        case MD_MSD:
            return name.endsWith(".xyz");
        case JOB_QUEUE_AUDIT:
            return name.equals("job-queue.jsonl") || name.endsWith(".jsonl");
        case NEB_PATH_AUDIT:
            return name.endsWith(".path") || (name.contains("neb") && name.endsWith(".xyz"));
        case HULL_STABILITY:
        case VOLTAGE_PROFILE:
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
        case BAND_GAP:
            return name.endsWith(".log") || name.endsWith(".out");
        case PHONON_DOS_THERMO:
            return name.contains("phdos") || name.endsWith(".dos")
                    || (name.contains("dos") && name.endsWith(".dat"));
        case DOS_INTEGRATION:
            return name.contains("pdos") && name.contains("atm#");
        case ELASTIC_STABILITY:
        case ELASTIC_MODULI:
        case ELASTIC_DIRECTIONAL:
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
        case PHONON_MODES:
            return name.contains("dynmat") || name.contains("modes");
        case SITE_PROFILE_CHECK:
            return name.endsWith(".yaml") || name.endsWith(".yml");
        case ML_MODEL_CHECK:
            return name.contains("manifest") || name.endsWith(".mlmf");
        case PHONOPY_DATA_REVIEW:
            return name.contains("force_sets");
        case EFFECTIVE_MASS:
            return name.contains("mass") || name.contains("emk");
        case TRAJECTORY_INDEX:
            return (name.endsWith(".xyz") || name.contains("extxyz"))
                    && !name.contains("force");
        case MLP_DATASET_CHECK:
            return name.endsWith(".xyz") || name.contains("extxyz");
        case ENERGY_SERIES_COMPARE:
            return name.endsWith(".csv")
                    && (name.contains("series") || name.contains("compare")
                            || name.contains("vs"));
        case TENSOR_EIGEN:
            return name.contains("tensor") || name.contains("dielectric")
                    || name.endsWith(".t3");
        case RAMAN_IR_SPECTRUM:
            return name.contains("dynmat") || name.contains("raman")
                    || name.contains("spectr");
        case TRAJECTORY_WINDOW_SCAN:
            return (name.endsWith(".xyz") || name.contains("extxyz"))
                    && !name.contains("force");
        case TENSOR_DIRECTIONAL:
            return name.contains("tensor") || name.contains("direction");
        case DENSITY_DIFFERENCE:
            return name.endsWith(".cube");
        case SPIN_CUBE_MAGNETIZATION:
            return (name.endsWith(".cube") || name.endsWith(".cub"))
                    && (name.contains("up") || name.contains("spin")
                            || name.contains("rho") || name.contains("charge"));
        case TIMING_RESOURCE:
            return name.contains("timing") || name.endsWith(".log")
                    || name.endsWith(".out");
        case PHONON_MODE_FRAMES:
            return name.contains("dynmat") || name.contains("modes");
        case POSCAR_REVIEW:
            return name.contains("poscar") || name.contains("contcar")
                    || name.endsWith(".vasp");
        case ELASTIC_ELATE_DRAFT:
            return name.contains("elastic") || name.contains("elate");
        case PDB_REVIEW:
            return name.endsWith(".pdb") || name.endsWith(".ent");
        case LAMMPS_DATA_REVIEW:
            return name.endsWith(".data") || name.contains("lammps.data");
        case LOG_ERROR_DIAGNOSIS:
            return name.equals("crash") || name.endsWith(".crash")
                    || name.endsWith(".err") || name.endsWith(".log")
                    || name.endsWith(".out");
        case CIF_REVIEW:
            return name.endsWith(".cif");
        case MOL_SDF_REVIEW:
            return name.endsWith(".mol") || name.endsWith(".sdf");
        case OCCUPATION_LEVELS_REVIEW:
            return name.endsWith(".log") || name.endsWith(".out");
        case OPTIMADE_RESPONSE_PARSE:
            return name.endsWith(".json") && (name.contains("optimade")
                    || name.contains("structures"));
        case MP_SUMMARY_PARSE:
            return name.endsWith(".json") && (name.contains("mp")
                    || name.contains("materialsproject") || name.contains("summary"));
        case ML_DATASET_BASELINE:
            return name.endsWith(".xyz") || name.contains("extxyz");
        case SERIES_REF_ALIGN:
            return name.contains("align");
        case BANDS_FERMI_REVIEW:
            return name.endsWith(".dat.gnu") || (name.startsWith("bands")
                    && name.endsWith(".dat"));
        case BAND_GAP_BANDS:
            return name.endsWith(".dat.gnu") || (name.startsWith("bands")
                    && name.endsWith(".dat"));
        case PROVENANCE_JOURNAL_REVIEW:
            return name.endsWith(".qfj") || name.contains("journal");
        default:
            return false;
        }
    }

    /**
     * Runs one analysis. The caller passes an already-resolved file when the
     * discovery list was presented to the user; otherwise the first discovered
     * candidate is used. No file is ever created or modified here. The returned
     * report carries the standard dispatch provenance section (Roadmap #128).
     */
    public static AnalysisReport analyze(AnalysisKind kind, ProjectProperty property,
            File projectDir, String prefix, String logFileName, File file, AnalysisParameters parameters) {
        AnalysisReport report = analyzeFileBound(kind, property, projectDir, prefix,
                logFileName, file, parameters);
        if (report == null || kind == null || projectDir == null) {
            return report;
        }
        File source = file;
        if (source == null && !kind.isInputPreview()) {
            source = firstCandidate(kind, projectDir, logFileName);
        }
        return attachProvenance(report, kind, source, projectDir);
    }

    /**
     * Internal file-bound dispatch. The public {@code analyze} overload wraps this
     * result with the standard provenance records.
     */
    private static AnalysisReport analyzeFileBound(AnalysisKind kind, ProjectProperty property,
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
            case THERMO_PW_DECK_AUDIT:
                return analyzeThermoPwDeckAudit(property, source);
            case THERMO_PW_RUN_SUMMARY:
                return analyzeThermoPwRunSummary(property, source);
            case ELATE_TENSOR_ANALYSIS:
                return analyzeElateTensor(property, source);
            case QE_CARD_AUDIT:
                return analyzeQeCardAudit(property, source);
            case QE_AUX_DECK_AUDIT:
                return analyzeQeAuxDeckAudit(property, source);
            case THERMOPW_EOS:
                return analyzeEos(property, source);
            case PHONO3PY_KAPPA:
                return analyzeKappa(property, source);
            case BOLTZTRAP2_TRANSPORT:
                return analyzeBoltzTrap2Transport(property, source);
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
            case ELASTIC_DIRECTIONAL:
                return analyzeElasticDirectional(property, source);
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
            case PHONON_MODES:
                return analyzePhononModes(property, source);
            case VOLTAGE_PROFILE:
                return analyzeVoltageProfile(source, params);
            case BAND_GAP:
                return analyzeBandGapSummary(source);
            case DOS_INTEGRATION:
                return analyzeDosIntegration(source);
            case EFFECTIVE_MASS:
                return analyzeEffectiveMass(source);
            case TRAJECTORY_INDEX:
                return analyzeTrajectoryIndex(source);
            case MLP_DATASET_CHECK:
                return analyzeDatasetCheck(source);
            case ENERGY_SERIES_COMPARE:
                return analyzeSeriesCompare(source);
            case TENSOR_EIGEN:
                return analyzeTensorEigen(source);
            case TENSOR_DIRECTIONAL:
                return analyzeTensorDirectional(source);
            case RAMAN_IR_SPECTRUM:
                return analyzeRamanIrSpectrum(property, source, params);
            case TRAJECTORY_WINDOW_SCAN:
                return analyzeTrajectoryWindowScan(source, params);
            case TIMING_RESOURCE:
                return analyzeTimingResource(source);
            case SITE_PROFILE_CHECK:
                return analyzeSiteProfile(source);
            case POSCAR_REVIEW:
                return analyzePoscarReview(source);
            case ELASTIC_ELATE_DRAFT:
                return analyzeElasticElateDraft(source);
            case PDB_REVIEW:
                return analyzePdbReview(source);
            case LAMMPS_DATA_REVIEW:
                return analyzeLammpsDataReview(source, params);
            case LOG_ERROR_DIAGNOSIS:
                return analyzeLogErrorDiagnosis(source);
            case CIF_REVIEW:
                return analyzeCifReview(source);
            case MOL_SDF_REVIEW:
                return analyzeMolSdfReview(source);
            case OCCUPATION_LEVELS_REVIEW:
                return analyzeOccupationLevelsReview(source);
            case OPTIMADE_RESPONSE_PARSE:
                return analyzeOptimadeResponseParse(source);
            case MP_SUMMARY_PARSE:
                return analyzeMpSummaryParse(source);
            case ML_DATASET_BASELINE:
                return analyzeMlDatasetBaseline(source);
            case SERIES_REF_ALIGN:
                return analyzeSeriesRefAlign(source, params);
            case BANDS_FERMI_REVIEW:
                return analyzeBandsFermiReview(source, params);
            case BAND_GAP_BANDS:
                return analyzeBandGapBands(source, params);
            case PROVENANCE_JOURNAL_REVIEW:
                return analyzeProvenanceJournalReview(source);
            case JOB_QUEUE_AUDIT:
                return analyzeJobQueueAudit(source);
            case NEB_PATH_AUDIT:
                return analyzeNebPathAudit(source);
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
     * back to project-directory discovery. The returned report carries the standard
     * dispatch provenance section (Roadmap #128).
     */
    public static AnalysisReport analyze(AnalysisKind kind, Project project, File file,
            AnalysisParameters parameters) {
        if (kind != null && !kind.isProjectBound()) {
            if (project == null) {
                return failure(kind.getLabel(), "No project is open.");
            }
            // The file-bound public dispatch attaches its own provenance.
            return analyze(kind, project.getProperty(), project.getDirectory(),
                    project.getPrefixName(), project.getLogFileName(), file, parameters);
        }
        AnalysisReport report = analyzeProjectBound(kind, project, file, parameters);
        if (report == null || kind == null || project == null) {
            return report;
        }
        return attachProvenance(report, kind, file, project.getDirectory());
    }

    /**
     * Internal project-bound dispatch. The public {@code analyze} overload wraps this
     * result with the standard provenance records.
     */
    private static AnalysisReport analyzeProjectBound(AnalysisKind kind, Project project,
            File file, AnalysisParameters parameters) {
        if (kind == null) {
            return failure("Result analysis", "No analysis type was selected.");
        }
        AnalysisParameters params = parameters == null ? new AnalysisParameters() : parameters;
        // Non-project-bound kinds are routed by the public overload before this
        // dispatch is reached.
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
            case ADSORPTION_PREVIEW:
                return analyzeAdsorptionPreview(project, params);
            case ML_MODEL_CHECK:
                return analyzeMlModel(project, file);
            case EXX_GUIDANCE:
                return analyzeExxGuidance(project, params);
            case BZ_GEOMETRY:
                return analyzeBzGeometry(project);
            case METHODS_TEXT:
                return analyzeMethodsText(project);
            case RO_CRATE:
                return analyzeRoCrate(project);
            case DEFECT_FORMATION:
                return analyzeDefectFormation(params);
            case ADSORPTION_ENERGY:
                return analyzeAdsorptionEnergy(params);
            case BARRIER_DIFFUSION:
                return analyzeBarrierDiffusion(params);
            case CONSTRAINTS_PREVIEW:
                return analyzeConstraintsPreview(project, params);
            case PHONOPY_DATA_REVIEW:
                return analyzeForceSetsReview(project, file);
            case PHONON_MODE_FRAMES:
                return analyzePhononModeFrames(project, file, params);
            case HYPERFINE_LOOKUP:
                return analyzeHyperfineLookup(params);
            case KEYWORD_HELP:
                return analyzeKeywordHelp(project, params);
            case ARRAY_SWEEP_PLAN:
                return analyzeArraySweepPlan(project, params);
            case CELL_EXTXYZ_EXPORT:
                return analyzeCellExtXyzExport(project);
            case DENSITY_DIFFERENCE:
                return analyzeDensityDifference(project, file);
            case SUPERCELL_PREVIEW:
                return analyzeSupercellPreview(project, params);
            case HUBBARD_HP_DRAFT:
                return analyzeHubbardHpDraft(project, params);
            case WORKSPACE_SEARCH:
                return analyzeWorkspaceSearch(project);
            case TEMPLATE_LIBRARY:
                return analyzeTemplateLibrary(params);
            case SPIN_CUBE_MAGNETIZATION:
                return analyzeSpinCubeMagnetization(project, file);
            case ESM_SLAB_CHECK:
                return analyzeEsmSlabCheck(project);
            case MOIRE_TWIST_PREVIEW:
                return analyzeMoireTwistPreview(project, params);
            case CP_INPUT_DRAFT:
                return analyzeCpInputDraft(project);
            case W90_WIN_DRAFT:
                return analyzeW90WinDraft(project);
            case GB_CSL_PREVIEW:
                return analyzeGbCslPreview(params);
            case QE_VERSION_CHECK:
                return analyzeQeVersionCheck(project, params);
            case MPI_POOLS_ADVISOR:
                return analyzeMpiPoolsAdvisor(project, params);
            case UNIT_CONVERT:
                return analyzeUnitConvert(params);
            case XSPECTRA_INPUT_DRAFT:
                return analyzeXspectraInputDraft(project);
            case GIPAW_INPUT_DRAFT:
                return analyzeGipawInputDraft(project);
            case TDDFPT_INPUT_DRAFT:
                return analyzeTddfptInputDraft(project);
            case JOB_DB_SCHEMA_PLAN:
                return analyzeJobDbSchemaPlan();
            case OPTIMADE_QUERY_DRAFT:
                return analyzeOptimadeQueryDraft(params);
            case MP_QUERY_DRAFT:
                return analyzeMpQueryDraft(params);
            case SSH_CONFIG_DRAFT:
                return analyzeSshConfigDraft(params);
            case SFTP_TRANSFER_PLAN:
                return analyzeSftpTransferPlan(project, params);
            case SLURM_SCRIPT_DRAFT:
                return analyzeSlurmScriptDraft(params);
            case KMESH_CONVERGENCE_PLAN:
                return analyzeKmeshConvergencePlan(project, params);
            case SITE_PROFILE_DRAFT:
                return analyzeSiteProfileDraft(params);
            case NEB_INPUT_DRAFT:
                return analyzeNebInputDraft(params);
            case JOB_CANCEL_PLAN:
                return analyzeJobCancelPlan(params);
            case SCHEDULER_ADAPTER_AUDIT:
                return analyzeSchedulerAdapterAudit(params);
            case JOB_MONITOR_AUDIT:
                return analyzeJobMonitorAudit(params);
            case SYNC_RUNTIME_AUDIT:
                return analyzeSyncRuntimeAudit(params);
            case MONITOR_POLL_PLAN:
                return analyzeMonitorPollPlan(params);
            case SYNC_MANIFEST_DRAFT:
                return analyzeSyncManifestDraft(params);
            case SMEARING_LADDER_PLAN:
                return analyzeSmearingLadderPlan(params);
            case CUTOFF_LADDER_PLAN:
                return analyzeCutoffLadderPlan(params);
            case ARRAY_JOB_PLAN:
                return analyzeArrayJobPlan(params);
            case ARRAY_JOB_AUDIT:
                return analyzeArrayJobAudit(params);
            case CONTAINER_PROFILE_DRAFT:
                return analyzeContainerProfileDraft(params);
            case JOB_STATE_GUARD:
                return analyzeJobStateGuard(params);
            case PHONON_GRID_PLAN:
                return analyzePhononGridPlan(project, params);
            case CHECKPOINT_RESUBMIT_PLAN:
                return analyzeCheckpointResubmit(project, params);
            case WORKFLOW_EXPORT_AUDIT:
                return analyzeWorkflowExportAudit(project);
            case FINAL_GEOMETRY_APPLY:
                return analyzeFinalGeometryApply(project);
            case SLAB_MILLER_PREVIEW:
                return analyzeSlabMillerPreview(project, params);
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

    /**
     * Roadmap #109 (output side): reads a BoltzTraP2 transport table through
     * {@link BoltzTrap2TraceParser} and reports the isotropic Seebeck/conductivity
     * screening state. Units surface VERBATIM from the file's own header tokens;
     * nothing is executed and no scattering model is re-derived.
     */
    /**
     * Batch 156 (QE-integration roadmap R6): thermo_control grammar audit
     * against the mined thermo_pw &INPUT_THERMO grammar (keywords + the
     * hard what set + verbatim consistency facts; commit provenance in
     * QEThermoPwSchemaData). Severities mirror the binary: an unknown namelist
     * keyword, a missing/out-of-set what, and a violated mined consistency
     * rule ABORT (ERROR); the flext silent remap and inferred-from-default
     * type shapes are warnings only.
     */
    private static AnalysisReport analyzeThermoPwDeckAudit(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.THERMO_PW_DECK_AUDIT.getLabel();
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        List<ValidationIssue> issues = new QEThermoPwDeckAudit().auditDeckText(content);
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        text.append(String.format(Locale.ROOT,
                "== thermo_pw thermo_control grammar audit (mined &INPUT_THERMO: %d keywords"
                        + " in the window union, %d accepted what values; window tags 2.0.0..2.1.1"
                        + " + fingerprinted master, pinned here at master) ==%n",
                QEThermoPwSchema.entryCount(), QEThermoPwSchema.whatAcceptedValues().size()));
        text.append("Source: ").append(source.getName()).append('\n');
        csv.add("thermo-deck-audit,severity,code,message");
        int errors = 0;
        int warnings = 0;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationSeverity.ERROR) {
                errors += 1;
            } else {
                warnings += 1;
            }
            text.append("  ").append(issue.getSeverity())
                    .append(" [").append(issue.getCode()).append("] ")
                    .append(issue.getMessage()).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("      docs: ").append(issue.getDocumentationUrl()).append('\n');
            }
            csv.add(String.format(Locale.ROOT, "thermo-deck-audit,%s,%s,%s",
                    issue.getSeverity(), csvCell(issue.getCode()), csvCell(issue.getMessage())));
        }
        if (issues.isEmpty()) {
            text.append(String.format(Locale.ROOT,
                    "  No grammar findings: every keyword of this thermo_control is inside the%n"
                            + "  mined grammar, 'what' initializes to one of the %d dispatch%n"
                            + "  values, and no mined consistency rule trips.%n",
                    QEThermoPwSchema.whatAcceptedValues().size()));
        }
        text.append(String.format(Locale.ROOT,
                "%nVerdict: %d ERROR (thermo_pw aborts at namelist read or a consistency"
                        + " check) and %d WARNING (silently remapped or advisory type-shape -"
                        + " check intent).%n",
                errors, warnings));
        text.append("\nHonesty boundary: this is a GRAMMAR audit, never a physics or"
                + " convergence review. Group labels are the thermo_pw code's own bookkeeping"
                + " comments (a navigation aid, not a law). The grammar window is"
                + " thermo_pw-tag-indexed (2.0.0 .. 2.1.1 + the fingerprinted development"
                + " master; batch-158 masks per keyword/what/fact, drift verbatim) and"
                + " this report pins the master column - a release-specific adjudication"
                + " belongs to the version-pinned audit API, not this auto-route. Defaults"
                + " shown are the procedural assignments (last-assignment-wins); the audit"
                + " is the grammar, not a thermodynamics result.\n");
        return new AnalysisReport(label, errors == 0, text.toString(), csv, null);
    }

    /**
     * thermo_pw RUN SUMMARY (batch 166): a whole run-directory census through
     * {@link QEThermoPwRunScanner} (user-guide §2.5 layout) rendered as one
     * report - verbatim thermo_control extracts, the artifact census with
     * kinds/geometry/ph/suffix tags, restart-token counts, and the verbatim
     * stdout EOS extracts with 1-based line numbers. The run directory is
     * the picked file's parent (an explicit thermo_control, the run's
     * stdout, or any sibling artifact all resolve to the same census); the
     * kind is manual-select ONLY because every *.out would else hijack
     * routing. Verdict: success when the directory yields any positive
     * census fact (control present, an artifact, a restart token, or a
     * stdout extract); an empty directory is an honest failure, not an
     * empty summary.
     */
    private static AnalysisReport analyzeThermoPwRunSummary(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.THERMO_PW_RUN_SUMMARY.getLabel();
        File runDir = source.isDirectory() ? source : source.getParentFile();
        if (runDir == null || !runDir.isDirectory()) {
            return failure(label, source.getName() + " has no resolvable thermo_pw run"
                    + " directory - pick the run's thermo_control, its stdout, or any"
                    + " file inside the run directory.");
        }
        QEThermoPwRunScanner.ThermoScan scan = QEThermoPwRunScanner.scan(runDir.toPath());
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        text.append("== thermo_pw run summary (directory census per user-guide §2.5;"
                + " every value a verbatim extract - nothing re-derived) ==\n");
        text.append("Picked file: ").append(source.getAbsolutePath()).append('\n');
        text.append("Run directory: ").append(runDir.getAbsolutePath()).append('\n');
        text.append(QEThermoPwRunScanner.describe(scan)).append('\n');
        csv.add("thermo-run-summary,field,value");
        csv.add(String.format(Locale.ROOT, "thermo-run-summary,what,%s",
                csvCell(scan.getControl().getWhat())));
        csv.add(String.format(Locale.ROOT, "thermo-run-summary,restart_tasks,%d",
                scan.getRestartCount()));
        csv.add(String.format(Locale.ROOT, "thermo-run-summary,series_artifacts,%d",
                scan.getArtifacts().size()));

        if (!scan.getArtifacts().isEmpty()) {
            text.append("\nSeries census (role/file -> kind, tags):\n");
            for (QEThermoPwRunScanner.Artifact artifact : scan.getArtifacts()) {
                text.append("  ").append(artifact.getRole()).append('/')
                        .append(artifact.getPath().getFileName());
                if (artifact.getKind() == null) {
                    text.append(" -> [control, enumerated not charted]");
                } else {
                    text.append(" -> ").append(artifact.getKind().getLabel());
                    if (artifact.getGeometryTag() != null) {
                        text.append(" [geometry ").append(artifact.getGeometryTag())
                                .append(']');
                    }
                    if (artifact.isPhVariant()) {
                        text.append(" [ph variant]");
                    }
                    if (artifact.getSuffixTag() != null) {
                        text.append(" [verbatim plot-tag ").append(artifact.getSuffixTag())
                                .append(']');
                    }
                }
                text.append('\n');
            }
        }

        if (scan.getStdoutSummaries().isEmpty()) {
            text.append("\nNo top-level *.out stdout file in the run directory - the EOS"
                    + " line-block extracts are simply absent, never assumed.\n");
        } else {
            text.append("\nStdout extracts (verbatim tokens with 1-based line numbers; the"
                    + " EOS numbers are the run's own fit claim, NOT re-derived here):\n");
            for (QEThermoPwRunScanner.StdoutSummary summary : scan.getStdoutSummaries()) {
                String name = summary.getPath().getFileName().toString();
                if (summary.isOversized()) {
                    text.append("  ").append(name).append(": exceeds the ")
                            .append(QEThermoPwRunScanner.MAX_STDOUT_BYTES)
                            .append("-byte stdout bound - named, not parsed.\n");
                    continue;
                }
                text.append("  ").append(name).append(": ")
                        .append(summary.getUnitCellVolumeCount())
                        .append(" 'unit-cell volume (a.u.)^3' print(s), EOS block ")
                        .append(summary.getEosLineCount()).append("/4 lines.\n");
                if (summary.getLatticeConstantToken() != null) {
                    text.append(String.format(Locale.ROOT,
                            "    line %d: equilibrium lattice constant %s a.u.%n",
                            summary.getLatticeConstantLine(), summary.getLatticeConstantToken()));
                    csv.add(String.format(Locale.ROOT, "thermo-run-summary,eos_lattice_au,%s",
                            csvCell(summary.getLatticeConstantToken())));
                }
                if (summary.getBulkModulusToken() != null) {
                    text.append(String.format(Locale.ROOT,
                            "    line %d: bulk modulus %s kbar%n",
                            summary.getBulkModulusLine(), summary.getBulkModulusToken()));
                    csv.add(String.format(Locale.ROOT, "thermo-run-summary,eos_bulk_kbar,%s",
                            csvCell(summary.getBulkModulusToken())));
                }
                if (summary.getDerivativeToken() != null) {
                    text.append(String.format(Locale.ROOT,
                            "    line %d: pressure derivative of the bulk modulus %s%n",
                            summary.getDerivativeLine(), summary.getDerivativeToken()));
                    csv.add(String.format(Locale.ROOT,
                            "thermo-run-summary,eos_bulk_derivative,%s",
                            csvCell(summary.getDerivativeToken())));
                }
                if (summary.getMinEnergyToken() != null) {
                    text.append(String.format(Locale.ROOT,
                            "    line %d: total energy at the minimum %s Ry%n",
                            summary.getMinEnergyLine(), summary.getMinEnergyToken()));
                    csv.add(String.format(Locale.ROOT, "thermo-run-summary,eos_min_energy_ry,%s",
                            csvCell(summary.getMinEnergyToken())));
                }
                if (summary.getEosLineCount() < 4) {
                    text.append("    (partial or absent EOS block - a live run is reported"
                            + " as-is; nothing is completed by guesswork)\n");
                }
            }
        }

        if (!scan.getUninterpreted().isEmpty()) {
            text.append("\nUninterpreted sidecars (named, never parsed): ");
            int shown = 0;
            for (String name : scan.getUninterpreted()) {
                if (shown++ >= 12) {
                    text.append("... (+").append(scan.getUninterpreted().size() - 12)
                            .append(" more)");
                    break;
                }
                if (shown > 1) {
                    text.append(", ");
                }
                text.append(name);
            }
            text.append('\n');
        }
        boolean anyFact = scan.getControl().isPresent() || !scan.getArtifacts().isEmpty()
                || scan.getRestartCount() > 0
                || scan.getStdoutSummaries().stream().anyMatch(s -> !s.isOversized()
                        && (s.getEosLineCount() > 0 || s.getUnitCellVolumeCount() > 0));
        text.append("\nHonesty boundary: a directory census over pinned file NAME grammars"
                + " plus verbatim control/stdout extracts. The EOS block is the run's own"
                + " fit summary (ieos selection and fit quality belong to the run, not to"
                + " this report); the pgrun dotted index tails are preserved verbatim"
                + " without asserting band-vs-segment semantics; files outside the pinned"
                + " set are named as uninterpreted; no thermodynamics review is implied.\n");
        return new AnalysisReport(label, anyFact, text.toString(), csv, null);
    }

    /**
     * ELATE TENSOR ANALYSIS (batch 167): one 6x6 stiffness tensor from the
     * pinned thermo_pw elastic channels (output_el_cons.dat[.gN], or the LAST
     * 'Elastic constants C_ij (kbar)' block of a run stdout, both declared
     * kbar) run through {@link QEElateAnalyzer} - the clean-room mirror of
     * the published ELATE workflow (coudertlab/elate commit 0627e636a). The
     * report shows stability + eigenvalues + Voigt/Reuss/Hill rows always,
     * extrema only when ELATE's own positive-definite gate passes; the
     * thermo_pw-vs-ELATE Hill CONVENTION difference is stated wherever the
     * stdout's printed Hill row is also quoted. Manual-select ONLY (shares
     * the *.out / .dat* name surface with many other kinds). Anything that
     * matches neither pinned grammar is refused with the dialog paste-route
     * pointer - never auto-guessed.
     */
    private static AnalysisReport analyzeElateTensor(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.ELATE_TENSOR_ANALYSIS.getLabel();
        if (source == null || !source.isFile()) {
            return failure(label, "Pick a thermo_pw output_el_cons.dat[.gN] constants file"
                    + " or a run stdout (*.out) holding the C_ij block. To analyse a tensor"
                    + " from any other source, use the ELATE dialog's paste route (declared"
                    + " unit) - this report never guesses a grammar.");
        }
        String name = source.getName();
        String matrixText;
        StringBuilder provenance = new StringBuilder();
        StringBuilder verbatim = new StringBuilder();
        if (name.startsWith("output_el_cons.dat")) {
            OperationResult<QEThermoPwElasticParser.ElasticConstantsFile> parsed =
                    QEThermoPwElasticParser.parseElasticConstantsFile(source.toPath());
            if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
                return failure(label, "[" + parsed.getCode() + "] " + parsed.getMessage());
            }
            QEThermoPwElasticParser.ElasticConstantsFile file = parsed.getValue()
                    .orElseThrow();
            matrixText = file.toElateMatrixText();
            provenance.append("constants file ").append(name)
                    .append(file.getGeometryTag() != null
                            ? " (geometry " + file.getGeometryTag() + ")" : "")
                    .append(" - ").append(file.getUnitProvenance()).append('\n');
            provenance.append("compliance block: ").append(file.hasCompliance()
                    ? "present (1/Mbar)" : "not written (yet)").append('\n');
        } else if (name.endsWith(".out") || name.endsWith(".log")) {
            OperationResult<QEThermoPwElasticParser.ElasticStdout> parsed =
                    QEThermoPwElasticParser.parseElasticStdout(source.toPath());
            if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
                return failure(label, "[" + parsed.getCode() + "] " + parsed.getMessage());
            }
            QEThermoPwElasticParser.ElasticStdout stdout = parsed.getValue().orElseThrow();
            matrixText = stdout.toElateMatrixText();
            provenance.append("run stdout ").append(name)
                    .append(" - LAST C_ij block at line ")
                    .append(stdout.getStiffnessFirstLine())
                    .append(" (stdout digits are the run's ROUNDED prints, used as-is).\n");
            for (QEThermoPwElasticParser.StdoutScheme scheme : stdout.getSchemes()) {
                verbatim.append(String.format(Locale.ROOT,
                        "  line %d  %s:  B=%s kbar  E=%s kbar  G=%s kbar  n=%s%n",
                        scheme.getFirstLine(), scheme.getScheme(), scheme.getBulkKbar(),
                        scheme.getYoungKbar(), scheme.getShearKbar(), scheme.getPoisson()));
            }
            if (!stdout.getSoundTokens().isEmpty()) {
                verbatim.append("  sound velocities (m/s tokens): ")
                        .append(String.join(", ", stdout.getSoundTokens())).append('\n');
            }
            if (!stdout.getDebyeTokens().isEmpty()) {
                verbatim.append("  Debye tokens: ")
                        .append(String.join(", ", stdout.getDebyeTokens())).append('\n');
            }
        } else {
            return failure(label, name + " matches neither pinned elastic channel"
                    + " (output_el_cons.dat[.gN] / run stdout). Use the ELATE dialog's"
                    + " paste route for ad-hoc tensors - nothing is auto-guessed here.");
        }
        OperationResult<QEElateAnalyzer.ElateReport> analysed =
                QEElateAnalyzer.analyze(matrixText, QEElateAnalyzer.ElateUnit.KBAR);
        if (!analysed.isSuccess() || analysed.getValue().isEmpty()) {
            return failure(label, "[" + analysed.getCode() + "] " + analysed.getMessage());
        }
        QEElateAnalyzer.ElateReport report = analysed.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        text.append("== ELATE elastic tensor analysis (Java mirror of the published\n");
        text.append("   coudertlab/elate workflow, commit 0627e636a7c97e8678f71aea44d0851455650d3a)\n");
        text.append("   ==\n");
        text.append(provenance);
        double[] eigen = report.getEigenvaluesGpa();
        text.append(String.format(Locale.ROOT,
                "%nStability: %s (smallest eigenvalue %.6g GPa)%neigenvalues (GPa):",
                report.isMechanicallyStable() ? "mechanically STABLE"
                        : "mechanically UNSTABLE - ELATE's gate: no spatial extrema",
                eigen[0]));
        for (double value : eigen) {
            text.append(String.format(Locale.ROOT, "  %.6g", value));
        }
        text.append('\n');
        csv.add("elate,field,value");
        csv.add(String.format(Locale.ROOT, "elate,stable,%s", report.isMechanicallyStable()));
        csv.add(String.format(Locale.ROOT, "elate,lambda_min_gpa,%.6g", eigen[0]));
        text.append("\nVoigt / Reuss / Hill averages (GPa; Poisson dimensionless):\n");
        text.append(String.format(Locale.ROOT, "  %-16s %12s %12s %12s %10s%n",
                "scheme", "K (bulk)", "E (Young)", "G (shear)", "nu"));
        for (QEElateAnalyzer.AverageRow row : report.getAverages()) {
            text.append(String.format(Locale.ROOT, "  %-16s %12.5f %12.5f %12.5f %10.5f%n",
                    row.getScheme(), row.getBulkGpa(), row.getYoungGpa(),
                    row.getShearGpa(), row.getPoisson()));
            csv.add(String.format(Locale.ROOT, "elate,bulk_gpa_%s,%.6f",
                    row.getScheme(), row.getBulkGpa()));
            csv.add(String.format(Locale.ROOT, "elate,young_gpa_%s,%.6f",
                    row.getScheme(), row.getYoungGpa()));
        }
        if (report.getMinYoung() != null) {
            text.append("\nSpatial extrema (ELATE grid + refine):\n");
            text.append(String.format(Locale.ROOT,
                    "  E: min %.6g / max %.6g GPa (anisotropy %s)%n",
                    report.getMinYoung().getValue(), report.getMaxYoung().getValue(),
                    report.getYoungAnisotropy()));
            text.append(String.format(Locale.ROOT,
                    "  G: min %.6g / max %.6g GPa (anisotropy %s)%n",
                    report.getMinShear().getValue(), report.getMaxShear().getValue(),
                    report.getShearAnisotropy()));
            text.append(String.format(Locale.ROOT,
                    "  nu: min %.6g / max %.6g (LC: %.6g..%.6g TPa^-1)%n",
                    report.getMinPoisson().getValue(), report.getMaxPoisson().getValue(),
                    report.getMinLc().getValue(), report.getMaxLc().getValue()));
        }
        if (verbatim.length() > 0) {
            text.append("\nverbatim thermo_pw stdout prints (the run's own claims):\n")
                    .append(verbatim);
        }
        text.append("\nHill convention note: thermo_pw prints Hill as the MEAN of its two"
                + " scheme rows; ELATE closes Hill from K_H, G_H - on upstream Si"
                + " (example13) 159.958 vs 159.977 GPa, ~0.012%, a convention difference"
                + " shown, not reconciled.\n");
        text.append("\nHonesty boundary: kbar -> GPa by the declared 0.1 (thermo_pw's own"
                + " printed aid); the constants file carries no unit text (cross-pinned"
                + " from the sibling stdout of upstream example13, commit b73edd6d); no"
                + " stability REVIEW is implied beyond Born positive-definiteness.\n");
        boolean stable = report.isMechanicallyStable();
        return new AnalysisReport(label, true, text.toString(), csv, null,
                stable ? List.of() : List.of("tensor is not positive definite: spatial"
                        + " extrema withheld by ELATE's own gate"));
    }

    /**
     * pw.x CARD grammar audit (R4): the deck's card-adjacent lines adjudicated
     * against the mined read_cards.f90 grammar (dispatch chain, option IF-chain
     * arms with HUBBARD sanity traps, ELSE-arm dispositions, prog gates, and
     * the K_POINTS automatic-mesh constraints; tag sha256 provenance in
     * QECardSchemaData). Severities mirror the binary: removed cards, traps,
     * FATAL dispositions and mesh violations ABORT (ERROR); unknown cards and
     * tolerated-with-default options are what pw.x itself survives (WARNING).
     * Version: null falls to the newest mined tag, stated in the header.
     */
    private static AnalysisReport analyzeQeCardAudit(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.QE_CARD_AUDIT.getLabel();
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        List<ValidationIssue> issues = new QECardAudit().auditDeckText(content, null);
        String newest = QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        text.append(String.format(Locale.ROOT,
                "== pw.x card grammar audit (mined read_cards.f90: %d dispatch cards, %d option"
                        + " grammars, tags qe-7.2..qe-%s; version not pinned -> newest tag) ==%n",
                QECardSchema.dispatchChain().size(), QECardSchema.grammars().size(), newest));
        text.append("Source: ").append(source.getName()).append('\n');
        csv.add("qe-card-audit,severity,code,message");
        int errors = 0;
        int warnings = 0;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationSeverity.ERROR) {
                errors += 1;
            } else {
                warnings += 1;
            }
            text.append("  ").append(issue.getSeverity())
                    .append(" [").append(issue.getCode()).append("] ")
                    .append(issue.getMessage()).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("      docs: ").append(issue.getDocumentationUrl()).append('\n');
            }
            csv.add(String.format(Locale.ROOT, "qe-card-audit,%s,%s,%s",
                    issue.getSeverity(), csvCell(issue.getCode()), csvCell(issue.getMessage())));
        }
        if (issues.isEmpty()) {
            text.append("  No card-grammar findings: every card-shaped line is a card pw.x\n"
                    + "  dispatches on, every option matches the mined IF-chain, and no\n"
                    + "  mined consistency trap trips.\n");
        }
        text.append(String.format(Locale.ROOT,
                "%nVerdict: %d ERROR (pw.x aborts: removed cards / HUBBARD traps / fatal"
                        + " options / mesh constraints) and %d WARNING (pw.x tolerates:"
                        + " unknown cards are IGNORED, tolerated options silently default).%n",
                errors, warnings));
        text.append("\nHonesty boundary: this is a GRAMMAR audit of card-shaped lines, never"
                + " a physics or completeness review. Content rules of known cards stay with"
                + " QEInputValidator; prog gates are adjudicated for a plain pw.x run"
                + " (K_POINTS warns only under CP and earns no finding here). Unknown cards are"
                + " WARNINGS because read_cards only writes 'Warning: card ... ignored' - but a"
                + " typo there silently drops the intended physics. Audit version window:"
                + " qe-7.2 .. qe-" + newest + ".\n");
        return new AnalysisReport(label, errors == 0, text.toString(), csv, null);
    }

    /**
     * Auxiliary-program input grammar audit (batch 159): decks of the 24
     * mandated auxiliary programs adjudicated against the mined grammar
     * (21 INPUT_*.def machine grammars + the XSpectra-family namelist
     * declarations mined from compilable source; masks per tag qe-7.2..qe-7.6;
     * tag sha256 provenance and the spectra_correction option STOP-set fact
     * in QEAuxSchemaData). Program detection is conservative: an unambiguous
     * namelist signature picks the program (INPUTCOND -> pwcond); ambiguous
     * families (INPUT_MANIP -> the two spectra tools, &INPUT collisions) are
     * audited as the first candidate with every alternative NAMED, never
     * silently guessed. Severities mirror the binaries: unknown keywords,
     * wrong-namelist placements, version-absent keywords and the spectra
     * option STOP-set abort the programs outright (ERROR); REQUIRED keywords
     * without a default, declared-type shapes and undocumented option
     * literals are advisory layers (WARNING, doc layer honestly SOFT).
     * Version: null falls to the newest mined tag, stated in the header.
     */
    private static AnalysisReport analyzeQeAuxDeckAudit(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.QE_AUX_DECK_AUDIT.getLabel();
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        QEAuxDeckAudit audit = new QEAuxDeckAudit();
        String newest = QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
        Optional<String> detected = audit.detectProgram(content);
        List<String> candidates = detected.isPresent() ? List.of(detected.get())
                : audit.candidatePrograms(content);
        if (candidates.isEmpty()) {
            return failure(label, source.getName() + " names no namelist owned by any of the"
                    + " 24 mined auxiliary programs, so the audit ran nothing (a refusal, not"
                    + " a pass). This kind audits decks for: "
                    + String.join(", ", QEAuxSchema.programs()) + ". If the file really is an"
                    + " auxiliary deck, its namelist header may be missing or foreign; the"
                    + " grammar table covers "
                    + QEAuxSchema.rowCount() + " keyword rows across tags qe-7.2..qe-"
                    + newest + ".");
        }
        String program = candidates.get(0);
        List<ValidationIssue> issues = audit.auditDeckText(program, content, null);
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        text.append(String.format(Locale.ROOT,
                "== auxiliary QE input grammar audit: %s (mined grammar: %d keyword rows,"
                        + " %d namelist(s); tags qe-7.2..qe-%s; version not pinned -> newest"
                        + " tag) ==%n",
                program, QEAuxSchema.entries(program).size(),
                QEAuxSchema.namelists(program).size(), newest));
        text.append("Source: ").append(source.getName()).append('\n');
        if (detected.isPresent()) {
            text.append("Program detection: unambiguous namelist signature -> ")
                    .append(detected.get()).append('\n');
        } else if (candidates.size() > 1) {
            text.append("Program detection: ambiguous signature - audited as ")
                    .append(program).append(" (alternatives: ")
                    .append(String.join(", ", candidates.subList(1, candidates.size())))
                    .append("); re-check the others if this deck was meant for them.\n");
        }
        text.append("Doc page of record: ").append(QEAuxSchema.docPage(program)).append('\n');
        csv.add("qe-aux-deck-audit,severity,code,message");
        int errors = 0;
        int warnings = 0;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationSeverity.ERROR) {
                errors += 1;
            } else {
                warnings += 1;
            }
            text.append("  ").append(issue.getSeverity())
                    .append(" [").append(issue.getCode()).append("] ")
                    .append(issue.getMessage()).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("      docs: ").append(issue.getDocumentationUrl()).append('\n');
            }
            csv.add(String.format(Locale.ROOT, "qe-aux-deck-audit,%s,%s,%s",
                    issue.getSeverity(), csvCell(issue.getCode()), csvCell(issue.getMessage())));
        }
        if (issues.isEmpty()) {
            text.append("  No grammar findings: every keyword belongs to the mined " + program
                    + " grammar at the audited version, sits under its declared namelist,"
                    + " and no mined hard rule (REQUIRED assignments, documented-option"
                    + " layers, verbatim STOP sets) trips.\n");
        }
        text.append(String.format(Locale.ROOT,
                "%nVerdict: %d ERROR (the program aborts: unknown keywords / wrong-namelist"
                        + " placements / version-absent keywords / verbatim STOP sets) and"
                        + " %d WARNING (advisory layers: REQUIRED-without-default, declared"
                        + " type shapes, undocumented option literals).%n",
                errors, warnings));
        text.append("\nHonesty boundary: this is a GRAMMAR audit of namelist assignments,"
                + " never run-readiness, physics, or a completeness review. Decks are read"
                + " through the production QEInputReader grammar; unknown keywords are ERROR"
                + " because the Fortran namelist READ aborts on them in every mined program,"
                + " while documented option literals are a SOFT doc layer that never refuses"
                + " by itself. Hard sets exist only where mined verbatim (the"
                + " spectra_correction option guard writes 'Option not recognized' and"
                + " STOPS). Audit version window: qe-7.2 .. qe-" + newest + ".\n");
        return new AnalysisReport(label, errors == 0, text.toString(), csv, null);
    }

    private static AnalysisReport analyzeBoltzTrap2Transport(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.BOLTZTRAP2_TRANSPORT.getLabel();
        BoltzTrap2TraceParser parser = new BoltzTrap2TraceParser(property);
        parser.parse(source);
        BoltzTrap2TraceParser.FileKind kind = parser.getFileKind();
        if (kind == BoltzTrap2TraceParser.FileKind.TENSOR_OTHER) {
            return failure(label, source.getName() + " is a 30-column tensor file whose header "
                    + "names RH[...] (a Hall tensor file), not sigma. It is NOT transport data, "
                    + "so no Seebeck/conductivity screening was derived from it.\n"
                    + "Provenance: " + parser.getFamilyNote());
        }
        List<BoltzTrap2TraceParser.TransportRow> rows = parser.getRows();
        if (rows.size() < 2) {
            return failure(label, "Fewer than two clean transport rows were parsed from "
                    + source.getName() + " (clean rows: " + rows.size() + ", skipped rows: "
                    + parser.getSkippedRowCount() + "). A single point is not a curve, and "
                    + "ragged rows are never silently healed.\nProvenance: "
                    + (parser.getFamilyNote().isEmpty() ? "file empty or grammar foreign"
                            : parser.getFamilyNote()));
        }

        BoltzTrap2TraceParser.TransportRow best = parser.maxAbsSeebeck();
        BoltzTrap2TraceParser.TransportRow bestPf = rows.get(0);
        for (BoltzTrap2TraceParser.TransportRow row : rows) {
            if (BoltzTrap2TraceParser.powerFactor(row) > BoltzTrap2TraceParser.powerFactor(bestPf)) {
                bestPf = row;
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("File family: ").append(kind)
                .append(kind == BoltzTrap2TraceParser.FileKind.CONDTENS
                        ? " (full 3x3 tensors, Fortran-ordered blocks; isotropic = trace/3)"
                        : " (isotropic columns as written)").append('\n');
        text.append("Scattering model (header-legible): ").append(parser.getScatteringModel())
                .append('\n');
        text.append("sigma units verbatim: ")
                .append(parser.getSigmaUnits().isEmpty() ? "(no header token)" : parser.getSigmaUnits())
                .append(" / kappa units verbatim: ")
                .append(parser.getKappaUnits().isEmpty() ? "(no header token)" : parser.getKappaUnits())
                .append('\n');
        text.append("Provenance: ").append(parser.getFamilyNote()).append('\n');
        text.append("Clean rows: ").append(rows.size())
                .append(" | skipped ragged/foreign rows: ").append(parser.getSkippedRowCount())
                .append(" | temperatures: ").append(parser.getTemperatures().size()).append(" K values\n\n");

        text.append(String.format(Locale.ROOT,
                "Peak |Seebeck| (isotropic): %.4g V/K at T=%.3g K, mu=%.6g Ry (%.6g eV, 1 Ry = 13.605693122994 eV)%n",
                best.getSeebeckVK(), best.getTemperatureK(), best.getMuRy(), best.getMuEv()));
        text.append(String.format(Locale.ROOT,
                "Peak power factor S^2.sigma (isotropic-average approximation): %.4g at T=%.3g K, mu=%.6g Ry%n",
                BoltzTrap2TraceParser.powerFactor(bestPf), bestPf.getTemperatureK(),
                bestPf.getMuRy()));
        if (kind == BoltzTrap2TraceParser.FileKind.CONDTENS) {
            double[] spread = parser.seebeckDiagonalSpread(best);
            if (spread != null) {
                text.append(String.format(Locale.ROOT,
                        "Seebeck diagonal anisotropy at that point: min=%.4g, max=%.4g, spread=%.4g V/K (xx/yy/zz diagonal of the tensor)%n",
                        spread[0], spread[1], spread[2]));
            }
        }
        text.append("\nHonesty block: units come VERBATIM from the header tokens (the custom_tau "
                + "writer's 1e-9 factor is already inside the written numbers; nothing is "
                + "re-scaled here). The power factor above is the isotropic-average "
                + "approximation S^2.sigma, NOT the tensor-consistent tr(S^2.sigma)/3; "
                + "convergence in k-mesh, scattering model and chemical-potential grid is the "
                + "publication burden left to the user - a table alone never proves it.");

        List<String> csv = new ArrayList<>();
        csv.add("mu_ry,mu_ev,temperature_k,carriers_e_per_uc,seebeck_iso_v_k,sigma_iso,kappa_iso,power_factor_iso");
        for (BoltzTrap2TraceParser.TransportRow row : rows) {
            csv.add(String.format(Locale.ROOT, "%.8g,%.8g,%.8g,%.8g,%.8g,%.8g,%.8g,%.8g",
                    row.getMuRy(), row.getMuEv(), row.getTemperatureK(), row.getCarriersPerCell(),
                    row.getSeebeckVK(), row.getSigmaOverTau(), row.getKappaOverTau(),
                    BoltzTrap2TraceParser.powerFactor(row)));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
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
    /** Artifact detection + registered citations shared by citation/methods kinds. */
    private static final class CitationContext {
        boolean phonons;
        boolean thermo;
        boolean wannier;
        QECitationManager manager;
    }

    private static CitationContext citationContextFor(Project project) {
        CitationContext context = new CitationContext();
        File directory = project.getDirectory();
        if (directory != null) {
            File[] files = directory.listFiles(File::isFile);
            if (files != null) {
                for (File candidate : files) {
                    String name = candidate.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("matdyn") || name.endsWith(".freq") || name.endsWith(".freq.gp")
                            || name.startsWith("ph.")) {
                        context.phonons = true;
                    }
                    if (name.contains("thermo") || name.contains("eos")) {
                        context.thermo = true;
                    }
                    if (name.endsWith(".wout")) {
                        context.wannier = true;
                    }
                }
            }
        }
        context.manager = new QECitationManager();
        context.manager.registerFeatureCitations(context.phonons, context.thermo,
                context.wannier, false);
        return context;
    }

    private static AnalysisReport analyzeCitations(Project project) {
        String label = AnalysisKind.CITATIONS.getLabel();
        CitationContext context = citationContextFor(project);
        QECitationManager manager = context.manager;
        boolean phonons = context.phonons;
        boolean thermo = context.thermo;
        boolean wannier = context.wannier;
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
        // Batch 56: cross-check the mesh spacings against the zone's own geometry.
        double worstSpacing = 0.0;
        for (QEKpointMeshAdvisor.DirectionReport direction : report.getDirections()) {
            worstSpacing = Math.max(worstSpacing, direction.getSpacingInvAng());
        }
        OperationResult<QEBrillouinZoneGeometry.BzReport> zone =
                QEBrillouinZoneGeometry.compute(context.cell.copyLattice());
        if (zone.isSuccess() && zone.getValue().isPresent()
                && zone.getValue().get().isConsistent()
                && Double.isFinite(zone.getValue().get().getNearestFacetDistanceInvAng())) {
            double facet = zone.getValue().get().getNearestFacetDistanceInvAng();
            text.append(String.format(Locale.ROOT,
                    "%nZone-geometry check: nearest BZ facet distance d* = %.6f Ang^-1 "
                            + "(validated Wigner-Seitz construction). Worst mesh spacing = "
                            + "%.6f Ang^-1.%n", facet, worstSpacing));
            String verdict = worstSpacing < facet
                    ? "every axis samples finer than the nearest facet scale"
                    : "an axis spacing is COARSER than the nearest facet scale - the mesh "
                            + "cannot resolve the zone boundary there; refine it";
            text.append("Verdict: ").append(verdict).append('\n');
            csv.add(String.format(Locale.ROOT, "zone_facet_distance_inv_ang,%.8f", facet));
            csv.add(String.format(Locale.ROOT,
                    "worst_spacing_ang_inverse_vs_facet,%.8f,%.8f", worstSpacing, facet));
        } else {
            text.append("\nZone-geometry check: the validated Wigner-Seitz construction "
                    + "failed closed on this cell (" + zone.getMessage() + "); the "
                    + "facet-resolution cross-check is UNAVAILABLE - the spacing table above "
                    + "stands on its own and no zone claim is made.\n");
        }
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

    /**
     * Directional Young's modulus map (Roadmap #119): samples the compliance
     * tensor on a (theta, phi) grid in the tensor's printed frame and reports
     * minimum/maximum/mean with their directions, all in the printed units.
     */
    private static AnalysisReport analyzeElasticDirectional(ProjectProperty property,
            File source) throws IOException {
        String label = AnalysisKind.ELASTIC_DIRECTIONAL.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " exceeds the 64 MiB parse bound; pass "
                    + "the thermo_pw file with the 'Elastic Constant Matrix' block.");
        }
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        if (!content.contains("Elastic Constant Matrix")) {
            return failure(label, "No 'Elastic Constant Matrix' block exists in "
                    + source.getName() + ".");
        }
        ElasticParser parser = new ElasticParser(property);
        parser.parse(source);
        OperationResult<double[][]> complianceResult =
                QETensorAnalyzer.complianceMatrix(parser.getCij());
        if (!complianceResult.isSuccess() || complianceResult.getValue().isEmpty()) {
            return failure(label, "Compliance inversion failed closed: "
                    + complianceResult.getMessage());
        }
        double[][] compliance = complianceResult.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("All values use the tensor's printed units (thermo_pw default kbar; "
                + "divide by 10 for GPa). Angles are in the tensor's printed frame: "
                + "theta from the z axis, phi in the xy plane.\n\n");
        List<String> csv = new ArrayList<>();
        csv.add("theta_deg,phi_deg,youngs_modulus");
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        int count = 0;
        double tpMin = 0.0;
        double ppMin = 0.0;
        double tpMax = 0.0;
        double ppMax = 0.0;
        for (int tp = 0; tp <= 90; tp += 15) {
            double theta = Math.toRadians(tp);
            for (int pp = 0; pp <= 360; pp += 15) {
                double phi = Math.toRadians(pp);
                double l1 = Math.sin(theta) * Math.cos(phi);
                double l2 = Math.sin(theta) * Math.sin(phi);
                double l3 = Math.cos(theta);
                OperationResult<Double> modulus =
                        QETensorAnalyzer.youngsModulusInDirection(compliance, l1, l2, l3);
                if (!modulus.isSuccess() || modulus.getValue().isEmpty()) {
                    return failure(label, "Directional evaluation failed closed: "
                            + modulus.getMessage());
                }
                double value = modulus.getValue().orElseThrow();
                csv.add(String.format(Locale.ROOT, "%d,%d,%.8f", tp, pp, value));
                sum += value;
                count++;
                if (value < min) {
                    min = value;
                    tpMin = tp;
                    ppMin = pp;
                }
                if (value > max) {
                    max = value;
                    tpMax = tp;
                    ppMax = pp;
                }
            }
        }
        text.append(String.format(Locale.ROOT,
                "Sampled directions: %d%n", count));
        text.append(String.format(Locale.ROOT,
                "E_min = %.6f at (theta=%d, phi=%d)%n", min, (int) tpMin, (int) ppMin));
        text.append(String.format(Locale.ROOT,
                "E_max = %.6f at (theta=%d, phi=%d)%n", max, (int) tpMax, (int) ppMax));
        text.append(String.format(Locale.ROOT,
                "Sampled mean = %.6f; anisotropy measure E_max/E_min = %.6f%n",
                sum / count, max / min));
        text.append("\nThe sampled extrema bound the true directional extrema only up to the "
                + "15-degree grid density; refine by exporting the CSV and evaluating finer "
                + "grids. The full map is in the CSV export.");
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

    /** dynmat.x mode table with orthonormality/imagination audit, gauge-phase honesty. */
    private static AnalysisReport analyzePhononModes(ProjectProperty property, File source)
            throws IOException {
        String label = AnalysisKind.PHONON_MODES.getLabel();
        QEDynmatModesParser parser = new QEDynmatModesParser(property);
        parser.parse(source);
        List<QEDynmatModesParser.VibrationalMode> modes = parser.getModes();
        if (modes.isEmpty()) {
            StringBuilder reason = new StringBuilder("No usable dynmat.x mode records were "
                    + "parsed from ").append(source.getName()).append('.');
            for (String diagnostic : parser.getDiagnostics()) {
                reason.append(' ').append(diagnostic);
            }
            return failure(label, reason.toString());
        }
        boolean consistent = parser.isNormalizationConsistent(
                QEDynmatModesParser.DEFAULT_NORM_TOLERANCE);
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Atoms per mode (consistent across the file): ").append(parser.getAtomCount())
                .append('\n');
        text.append(String.format(Locale.ROOT,
                "Orthonormality audit (|norm-1| <= %.4f): max deviation %.8f -> %s%n%n",
                QEDynmatModesParser.DEFAULT_NORM_TOLERANCE, parser.getMaxNormDeviation(),
                consistent ? "PASSED" : "FAILED"));
        text.append(String.format(Locale.ROOT, " %-5s %-12s %-14s %-10s %-12s%n",
                "mode", "omega (THz)", "omega (cm-1)", "imaginary", "norm dev"));
        List<String> csv = new ArrayList<>();
        csv.add("mode_index,omega_thz,omega_cm1,imaginary,norm_deviation");
        for (QEDynmatModesParser.VibrationalMode mode : modes) {
            text.append(String.format(Locale.ROOT, " %-5d %-12.6f %-14.6f %-10s %.8f%n",
                    mode.getIndex(), mode.getOmegaThz(), mode.getOmegaCm1(),
                    mode.isImaginary(), mode.getNormDeviation()));
            csv.add(String.format(Locale.ROOT, "%d,%.8f,%.8f,%s,%.8g", mode.getIndex(),
                    mode.getOmegaThz(), mode.getOmegaCm1(), mode.isImaginary(),
                    mode.getNormDeviation()));
        }
        text.append('\n');
        for (String diagnostic : parser.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nEach mode's overall sign/phase is gauge freedom and is deliberately "
                + "not normalized away; eigenvector animation and supercell phase replication "
                + "(roadmap #52) consume exactly these validated rows. Negative frequencies are "
                + "imaginary modes as printed by dynmat.x.");
        return new AnalysisReport(label, consistent, text.toString(), csv, null);
    }

    /**
     * Roadmap #52 data layer: synthesizes a multi-frame XYZ animation document for one
     * validated dynmat mode by phase-sampling base + amplitude * Re(e_i). Project-bound
     * because base positions and element names must come from the live cell model -
     * inventing them from the modes file would fabricate a structure. Nothing is saved
     * implicitly; the document leaves only through the explicit save action.
     */
    private static AnalysisReport analyzePhononModeFrames(Project project, File file,
            AnalysisParameters params) {
        String label = AnalysisKind.PHONON_MODE_FRAMES.getLabel();
        if (file == null) {
            return failure(label, "A dynmat modes file must be selected explicitly; the "
                    + "frames are synthesized from that file plus the live cell model.");
        }
        Cell cell = project.getCell();
        if (cell == null || cell.numAtoms() <= 0) {
            return failure(label, "The project has no atoms; the base positions for the "
                    + "frames must come from the live cell model.");
        }
        QEDynmatModesParser parser = new QEDynmatModesParser(project.getProperty());
        try {
            parser.parse(file);
        } catch (IOException ex) {
            return failure(label, "Reading " + file.getName() + " failed closed: "
                    + ex.getMessage());
        }
        List<QEDynmatModesParser.VibrationalMode> modes = parser.getModes();
        if (modes.isEmpty()) {
            StringBuilder reason = new StringBuilder("No usable dynmat.x mode records were "
                    + "parsed from ").append(file.getName()).append('.');
            for (String diagnostic : parser.getDiagnostics()) {
                reason.append(' ').append(diagnostic);
            }
            return failure(label, reason.toString());
        }
        if (!parser.isNormalizationConsistent(QEDynmatModesParser.DEFAULT_NORM_TOLERANCE)) {
            return failure(label, String.format(Locale.ROOT,
                    "The eigenvector orthonormality audit failed (max |norm-1| = %.8f, "
                            + "tolerance %.4f). Displacement amplitudes from untrusted "
                            + "eigenvectors would be misleading, so no frames are rendered.",
                    parser.getMaxNormDeviation(),
                    QEDynmatModesParser.DEFAULT_NORM_TOLERANCE));
        }
        int requested = params.getModeIndex();
        if (requested < 1) {
            return failure(label, "The mode index is 1-based as printed in the dynmat "
                    + "file; " + requested + " is not valid.");
        }
        QEDynmatModesParser.VibrationalMode mode = null;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (QEDynmatModesParser.VibrationalMode candidate : modes) {
            lowest = Math.min(lowest, candidate.getIndex());
            highest = Math.max(highest, candidate.getIndex());
            if (candidate.getIndex() == requested) {
                mode = candidate;
            }
        }
        if (mode == null) {
            return failure(label, "Mode " + requested + " is not present in "
                    + file.getName() + "; the file carries mode indices " + lowest + ".."
                    + highest + ".");
        }
        if (mode.getAtomCount() != cell.numAtoms()) {
            return failure(label, "Atom-count mismatch: the mode has " + mode.getAtomCount()
                    + " atom(s) but the live cell has " + cell.numAtoms() + ". Frames are "
                    + "refused rather than mapped onto the wrong structure (a supercell "
                    + "mode file vs. a primitive cell would silently scramble atoms).");
        }
        Atom[] atoms = cell.listAtoms();
        double[][] realParts = new double[atoms.length][3];
        double[][] basePositions = new double[atoms.length][3];
        String[] elements = new String[atoms.length];
        double maxImagComponent = 0.0;
        for (int i = 0; i < atoms.length; i++) {
            double[] row = mode.getDisplacements()[i];
            realParts[i][0] = row[0];
            realParts[i][1] = row[1];
            realParts[i][2] = row[2];
            maxImagComponent = Math.max(maxImagComponent, Math.max(Math.abs(row[3]),
                    Math.max(Math.abs(row[4]), Math.abs(row[5]))));
            basePositions[i][0] = atoms[i].getX();
            basePositions[i][1] = atoms[i].getY();
            basePositions[i][2] = atoms[i].getZ();
            elements[i] = atoms[i].getName();
        }
        OperationResult<String> frames = PhononFrameSynthesis.frames(realParts,
                basePositions, elements, params.getFrameAmplitudeAng(),
                params.getFrameCount(), mode.getOmegaCm1(), requested);
        if (!frames.isSuccess() || frames.getValue().isEmpty()) {
            return failure(label, "Frame synthesis refused: " + frames.getMessage());
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(file.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Mode %d: omega = %.6f THz = %.6f cm-1%s%n", mode.getIndex(),
                mode.getOmegaThz(), mode.getOmegaCm1(),
                mode.isImaginary() ? " (IMAGINARY)" : ""));
        if (mode.isImaginary()) {
            text.append("Note: this is an imaginary (unstable) mode; the frames still trace "
                    + "the instability eigenvector but the periodic sinusoid is a display "
                    + "convention, not a trajectory.\n");
        }
        text.append(String.format(Locale.ROOT,
                "Atoms: %d; frames per period: %d; visual amplitude: %.6f A%n",
                atoms.length, params.getFrameCount(), params.getFrameAmplitudeAng()));
        text.append(String.format(Locale.ROOT,
                "Orthonormality audit (|norm-1| <= %.4f): max deviation %.8f -> PASSED%n%n",
                QEDynmatModesParser.DEFAULT_NORM_TOLERANCE, parser.getMaxNormDeviation()));
        List<String> csv = new ArrayList<>();
        csv.add("frame_index,phase_scale");
        for (int frame = 0; frame < params.getFrameCount(); frame++) {
            csv.add(String.format(Locale.ROOT, "%d,%.8f", frame + 1,
                    Math.sin(2.0 * Math.PI * frame / params.getFrameCount())));
        }
        text.append("Honesty boundary: dynmat eigenvectors are mass-weighted and "
                + "orthonormal, so the fixed amplitude is a VISUAL scaling in Angstrom, not "
                + "a physical displacement field; absolute amplitudes carry no thermal or "
                + "energetic meaning. Imaginary eigenvector components are dropped "
                + "(max |im| here: ").append(String.format(Locale.ROOT, "%.8f",
                maxImagComponent)).append("). The per-mode sign/phase is gauge freedom. No "
                + "supercell replicas or q-phase factors are generated. The document leaves "
                + "this dialog only through the explicit save action; animate the saved "
                + "multi-frame XYZ in an external viewer (XCrySDen, VMD, Ovito).");
        return new AnalysisReport(label, true, text.toString(), csv, frames.getValue().get());
    }

    /**
     * Roadmap #166 lookup + calculator: reports the covered nuclear g-factor table
     * and, only when the user supplies a GIPAW spin density, the Fermi contact
     * A_iso. Fail-closed for uncovered isotopes - no g-factor is ever invented.
     */
    private static AnalysisReport analyzeHyperfineLookup(AnalysisParameters params) {
        String label = AnalysisKind.HYPERFINE_LOOKUP.getLabel();
        String isotope = params.getIsotopeLabel() == null
                ? "" : params.getIsotopeLabel().trim();
        List<String> covered = HyperfineMapper.coveredIsotopes();
        List<String> csv = new ArrayList<>();
        csv.add("isotope,nuclear_g_factor");
        for (String known : covered) {
            csv.add(String.format(Locale.ROOT, "%s,%.6f",
                    csvCell(known), HyperfineMapper.getNuclearGFactor(known)));
        }
        if (isotope.isEmpty()) {
            return failure(label, "Supply an isotope label (e.g. 13C). Covered isotopes: "
                    + covered + " - anything else is refused rather than silently "
                    + "assigned a default g-factor.");
        }
        double gn = HyperfineMapper.getNuclearGFactor(isotope);
        if (!Double.isFinite(gn)) {
            return failure(label, "Isotope \"" + isotope + "\" is not in the covered set "
                    + covered + ". A g-factor is not invented; extend the CODATA-sourced "
                    + "table with a reviewed value before using this isotope.");
        }
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Isotope %s: nuclear g-factor gN = %.6f%n", isotope, gn));
        text.append(String.format(Locale.ROOT,
                "Covered table (%d isotopes, CODATA-sourced values): %s%n%n",
                covered.size(), covered));
        text.append(String.format(Locale.ROOT,
                "Fermi contact model: A_iso = %.6f * gN * rho(0) MHz, with rho(0) the "
                        + "nuclear spin density in a.u.^-3 (a0^-3). The compiled constant "
                        + "embeds (mu0/4pi)*(8pi/3)*ge*muB*muN; treat its last digits as "
                        + "subject to CODATA revision.%n%n", HyperfineMapper.HYPERFINE_FACTOR_MHZ));
        if (Double.isFinite(params.getNuclearSpinDensity())) {
            double aiso;
            try {
                aiso = HyperfineMapper.calculateAiso(params.getNuclearSpinDensity(), gn);
            } catch (IllegalArgumentException ex) {
                return failure(label, ex.getMessage());
            }
            text.append(String.format(Locale.ROOT,
                    "rho(0) = %.10e a.u.^-3 -> A_iso = %.6f MHz%n", params.getNuclearSpinDensity(),
                    aiso));
            csv.add(String.format(Locale.ROOT, "%s_A_ISO_MHZ,%.6f", csvCell(isotope), aiso));
            text.append("The sign is the product's sign: a negative spin density or negative "
                    + "gN is reported as-is.\n");
        } else {
            text.append("A_iso not computed: no nuclear spin density was supplied -\n"
                    + "supply the value from your GIPAW run to evaluate the coupling.\n");
        }
        text.append("\nHonesty boundary: this is the FERMI CONTACT (isotropic) term only; "
                + "the anisotropic dipolar tensor is not covered. The spin density must come "
                + "from your own converged GIPAW calculation - this analysis does not parse "
                + "GIPAW output and cannot verify the number's provenance. Compare against "
                + "experiment only with your reference/shift convention stated.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #129 offline keyword reference: looks a keyword up in the curated
     * table, always prints the upstream URL, and cross-checks the current project
     * input for the keyword's presence/value when one is resolvable. Anything
     * outside the curated subset fails closed and points upstream - nothing is
     * improvised, and this table is named as a subset of Roadmap #22's future
     * version-generated schema.
     */
    private static AnalysisReport analyzeKeywordHelp(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.KEYWORD_HELP.getLabel();
        String keyword = params.getSeriesKeyword() == null
                ? "" : params.getSeriesKeyword().trim();
        List<String> covered = QEKeywordHelp.coveredNames();
        if (keyword.isEmpty()) {
            return failure(label, "Supply a pw.x namelist keyword to look up. The offline "
                    + "table covers " + covered.size() + " curated keywords; anything else "
                    + "opens upstream: " + QEKeywordHelp.INPUT_PW_URL);
        }
        Optional<QEKeywordHelp.KeywordEntry> found = QEKeywordHelp.lookup(keyword);
        if (found.isEmpty()) {
            // Batch 150 deepened: outside the curated subset falls back to the
            // machine-mined schema (QE 7.2-7.6) before failing closed.
            Optional<QENamelistSchema.Entry> mined =
                    QENamelistSchema.lookup(QENamelistSchema.Kind.PW, keyword);
            if (mined.isPresent()) {
                QENamelistSchema.Entry row = mined.get();
                StringBuilder text = new StringBuilder();
                text.append(String.format(Locale.ROOT,
                        "Keyword: %s   (namelist &%s, pw.x)%n", row.getName(), row.getNamelist()));
                text.append("Source: machine-mined namelist schema, QE tags qe-7.2 .. qe-7.6 "
                        + "(scripts/qe_schema_miner.py); NOT the curated comments of the "
                        + covered.size() + "-keyword offline table.\n\n");
                text.append("Type: ").append(row.getType())
                        .append(row.isArray() ? " (indexed array)" : "").append('\n');
                text.append("Present in QE: ").append(row.versionRange()).append('\n');
                text.append("Required by the schema: ").append(row.isRequired() ? "yes" : "no")
                        .append('\n');
                text.append("Documented default: ")
                        .append(row.getDefaultText() == null ? "(none recorded)" : row.getDefaultText())
                        .append('\n');
                if (!row.getAcceptedValues().isEmpty()) {
                    text.append("Runtime-accepted values (pw.x aborts otherwise): ")
                            .append(String.join(", ", row.getAcceptedValues())).append('\n');
                }
                if (!row.getDocumentedValues().isEmpty()) {
                    text.append("Documented values (silently remapped otherwise): ")
                            .append(String.join(", ", row.getDocumentedValues())).append('\n');
                }
                if (row.getDescription() != null) {
                    text.append("\nSchema doc: ").append(row.getDescription()).append('\n');
                }
                text.append("\nUpstream reference (match your QE version): ")
                        .append(row.getDocsUrl()).append('\n');
                return new AnalysisReport(label, true, text.toString(), List.of(
                        "field,value",
                        "keyword," + csvCell(row.getName()),
                        "namelist," + csvCell(row.getNamelist()),
                        "type," + row.getType(),
                        "versions," + csvCell(row.versionRange()),
                        "required," + row.isRequired(),
                        "default," + csvCell(row.getDefaultText() == null
                                ? "(none recorded)" : row.getDefaultText())), null);
            }
            return failure(label, "\"" + keyword + "\" is outside the curated offline "
                    + "table (" + covered.size() + " keywords) AND outside the machine-mined "
                    + "QE 7.2-7.6 schema (430 pw.x namelist keywords). Nothing is improvised; "
                    + "consult the version-matched upstream docs: "
                    + QEKeywordHelp.INPUT_PW_URL + ". Covered keywords: " + covered);
        }
        QEKeywordHelp.KeywordEntry entry = found.get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Keyword: %s   (namelist &%s)%n",
                entry.getName(), entry.getSection()));
        text.append(entry.getSummary()).append("\n\n");
        text.append("Upstream reference (match your QE version): ")
                .append(QEKeywordHelp.INPUT_PW_URL).append("\n\n");
        // Cross-check the live project input without blocking the lookup itself.
        QEInput input = null;
        String inputNote = "the project has no resolvable current input";
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            inputNote = "resolving the current input failed: " + ex.getMessage();
        }
        if (input == null) {
            text.append("Input cross-check skipped: ").append(inputNote).append('\n');
        } else {
            QENamelist namelist = input.getNamelist(entry.getSection());
            QEValue value = namelist == null ? null : namelist.getValue(entry.getName());
            if (value == null) {
                text.append(String.format(Locale.ROOT,
                        "Current input: &%s does not set %s (the QE default, if any, "
                                + "applies).%n", entry.getSection(), entry.getName()));
            } else {
                text.append(String.format(Locale.ROOT,
                        "Current input: &%s sets %s = %s%n", entry.getSection(),
                        entry.getName(), value.toString().trim()));
            }
        }
        text.append("\nHonesty boundary: this is a curated, human-vetted subset (")
                .append(String.valueOf(covered.size()))
                .append(" keywords) - NOT the complete, version-generated schema that "
                        + "Roadmap #22 targets. Defaults, allowed values and version "
                        + "constraints are intentionally NOT restated here; the upstream "
                        + "version-matched documentation is the authority.");
        List<String> csv = new ArrayList<>();
        csv.add("keyword,section,summary");
        csv.add(String.format(Locale.ROOT, "%s,%s,%s", csvCell(entry.getName()),
                csvCell(entry.getSection()), csvCell(entry.getSummary())));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #100 data layer: validates one keyword sweep into a JSONL task
     * manifest (explicit save) plus a REQUIRED-EDIT-guarded sbatch preview in the
     * report text. Nothing is submitted, no directory is created.
     */
    private static AnalysisReport analyzeArraySweepPlan(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.ARRAY_SWEEP_PLAN.getLabel();
        OperationResult<ArraySweepPlanner.SweepPlan> planned = ArraySweepPlanner.plan(
                params.getSeriesKeyword(), params.getSeriesStart(), params.getSeriesStep(),
                params.getSeriesCount(), params.getJobBaseName());
        if (!planned.isSuccess() || planned.getValue().isEmpty()) {
            return failure(label, "Sweep refused: " + planned.getMessage());
        }
        ArraySweepPlanner.SweepPlan plan = planned.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Keyword: %s; %d task(s) from %.8g in steps of %.8g%n", plan.getKeyword(),
                plan.getValues().size(), plan.getValues().get(0),
                plan.getValues().size() > 1
                        ? plan.getValues().get(1) - plan.getValues().get(0) : 0.0));
        text.append("Task directories: ").append(plan.taskDirectory(1)).append(" .. ")
                .append(plan.taskDirectory(plan.getValues().size())).append("\n\n");
        text.append("SLURM array preview (REVIEW ONLY - the guard refuses to run as-is):\n");
        text.append(plan.sbatchPreview()).append('\n');
        DeckTemplateOutcome deck = templatesProjectDeck(project, plan);
        text.append('\n').append(deck.reportSection);
        String intentSection = "";
        String intentCsvRow = "task_intent,not_exercised,deck templating did not produce "
                + "a validated template";
        if (deck.template != null) {
            OperationResult<ArrayTaskIntent.TaskIntentPlan> intents =
                    ArrayTaskIntent.plan(plan, deck.template);
            ArrayTaskIntent.TaskIntentPlan intentPlan =
                    intents.getValue().orElse(null);
            StringBuilder section = new StringBuilder(
                    "\nPer-task run intents (the #28 provenance seam):\n");
            if (intents.isSuccess() && intentPlan != null) {
                List<ArrayTaskIntent.TaskIntent> tasks = intentPlan.getTasks();
                section.append(String.format(Locale.ROOT,
                        "  [TASK_INTENT_OK] %d intent(s); each task's rendered deck is "
                                + "hashed NOW so its future site-side run manifest can be "
                                + "DIFFED against this plan (the input-sha field name "
                                + "deliberately mirrors the execution-time writer).%n",
                        tasks.size()));
                section.append("  first intent: ").append(intentPlan.toJsonLine(tasks.get(0)))
                        .append('\n');
                section.append("  last intent:  ")
                        .append(intentPlan.toJsonLine(tasks.get(tasks.size() - 1)))
                        .append('\n');
                section.append("  Boundary: stage is pinned to 'rendered-deck-only' - no "
                        + "task ran, no directory was created; the site-side executor "
                        + "would write the real provenance record and the verifier checks "
                        + "input_sha256, never directory-name trust alone.\n");
                intentCsvRow = String.format(Locale.ROOT,
                        "task_intent,TASK_INTENT_OK,%d intents; first sha %s", tasks.size(),
                        tasks.get(0).getInputSha256());
            } else {
                section.append(String.format(Locale.ROOT,
                        "  [TASK_DECK_DUPLICATE] %s (a FINDING; the sweep and the deck "
                                + "template above stand).%n", intents.getMessage()));
                intentCsvRow = String.format(Locale.ROOT, "task_intent,refused,%s",
                        csvCell(intents.getCode()));
            }
            intentSection = section.toString();
        }
        List<String> csv = new ArrayList<>();
        csv.add("task_index,value,directory");
        for (int i = 0; i < plan.getValues().size(); i++) {
            csv.add(String.format(Locale.ROOT, "%d,%s,%s", i + 1,
                    Double.toString(plan.getValues().get(i)), plan.taskDirectory(i + 1)));
        }
        csv.add(deck.csvRow);
        csv.add(intentCsvRow);
        text.append(intentSection);
        SubmitLaneOutcome submitLane = submitLaneOutcome(plan, params.getSubmitScheduler());
        text.append(submitLane.reportSection);
        csv.add(submitLane.csvRow);
        text.append("\nHonesty boundary: the JSONL task manifest and the sbatch preview are "
                + "drafts - nothing was submitted, no directory was created, and the script "
                + "carries an exit-2 guard plus REQUIRED-EDIT lines so it cannot be queued "
                + "without review. The sweep rewrites exactly one keyword; convergence "
                + "claims come from analyzing the finished runs, not from the manifest. "
                + "Per-task run manifests (#28) and scheduler submit integration (#93) "
                + "remain the remaining #100 work.");
        return new AnalysisReport(label, true, text.toString(), csv, plan.toJsonLines());
    }

    /** Report-section carrier for the deck-templating extension. */
    private static final class DeckTemplateOutcome {
        private final String reportSection;
        private final String csvRow;
        private final ArrayDeckTemplate.DeckTemplate template;  // non-null only on success

        DeckTemplateOutcome(String reportSection, String csvRow) {
            this(reportSection, csvRow, null);
        }

        DeckTemplateOutcome(String reportSection, String csvRow,
                ArrayDeckTemplate.DeckTemplate template) {
            this.reportSection = reportSection;
            this.csvRow = csvRow;
            this.template = template;
        }
    }

    /**
     * #100 deck-templating slice: rewrites the project deck's swept-keyword
     * line via {@link ArrayDeckTemplate} (single owner of the one-line
     * grammar). Missing decks render as a NOT-exercised finding, grammar
     * refusals as FINDINGS - the sweep plan above stands either way.
     */
    private static DeckTemplateOutcome templatesProjectDeck(Project project,
            ArraySweepPlanner.SweepPlan plan) {
        StringBuilder section = new StringBuilder("Deck templating (project deck, "
                + "one-line grammar owned by ArrayDeckTemplate):\n");
        java.nio.file.Path deckPath = null;
        String deckText = null;
        try {
            deckPath = java.nio.file.Path.of(project.getDirectoryPath(),
                    project.getInpFileName(null));
            if (java.nio.file.Files.isRegularFile(deckPath)) {
                deckText = java.nio.file.Files.readString(deckPath);
            }
        } catch (Exception readFail) {
            deckPath = null;
        }
        if (deckText == null) {
            section.append("  NOT exercised: no readable project deck (expected ")
                    .append(project.getInpFileName(null))
                    .append(" in the project directory). Without it the sweep would "
                            + "have to guess the deck - it refuses; the manifest and the "
                            + "guarded sbatch preview above stand unchanged.\n");
            return new DeckTemplateOutcome(section.toString(),
                    "deck_template,not_exercised,no readable project deck - the sweep plan stands");
        }
        OperationResult<ArrayDeckTemplate.DeckTemplate> validated =
                ArrayDeckTemplate.validate(plan, deckText);
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            section.append(String.format(Locale.ROOT,
                    "  Refused as a FINDING [%s] %s (the deck itself is untouched; the "
                            + "sweep plan above stands).%n",
                    validated.getCode(), validated.getMessage()));
            return new DeckTemplateOutcome(section.toString(),
                    String.format(Locale.ROOT, "deck_template,refused,%s",
                            csvCell(validated.getCode())));
        }
        ArrayDeckTemplate.DeckTemplate template = validated.getValue().get();
        section.append(String.format(Locale.ROOT,
                "  [DECK_TEMPLATE_OK] sweep point: '%s' -> '%s'%n",
                template.getLineBefore(), template.getLineAfter()));
        section.append(String.format(Locale.ROOT,
                "  Exact task values: task 1 renders '%s'; task %d renders '%s'.%n",
                template.renderTaskLine(1), template.getTaskCount(),
                template.renderTaskLine(template.getTaskCount())));
        section.append("  Render intent (preview only, same exit-2 guard boundary): "
                + "each array task substitutes its own value for "
                + ArrayDeckTemplate.PLACEHOLDER + " at the site - the placeholder is "
                + "literal-safe by construction; NO full deck is dumped here (the project "
                + "deck on disk stays exactly as you saved it).\n");
        return new DeckTemplateOutcome(section.toString(), String.format(Locale.ROOT,
                "deck_template,DECK_TEMPLATE_OK,one sweep point; %d tasks render exactly",
                template.getTaskCount()), template);
    }

    /** Report-section carrier for the guarded submit-lane review. */
    private static final class SubmitLaneOutcome {
        private final String reportSection;
        private final String csvRow;

        SubmitLaneOutcome(String reportSection, String csvRow) {
            this.reportSection = reportSection;
            this.csvRow = csvRow;
        }
    }

    /**
     * #93/#100 guarded submit-lane review: the array's submission SEQUENCE
     * as a strictly-comment draft from the typed adapter owners. Blank
     * scheduler input skips honestly; unknown names refuse as FINDINGS.
     */
    private static SubmitLaneOutcome submitLaneOutcome(ArraySweepPlanner.SweepPlan plan,
            String schedulerName) {
        String name = schedulerName == null ? "" : schedulerName.trim().toLowerCase(
                Locale.ROOT);
        if (name.isEmpty()) {
            return new SubmitLaneOutcome("\nSubmit-lane review: NOT exercised (no "
                    + "scheduler selected - the sweep, deck and intents above stand; "
                    + "pick slurm/pjm/sge for a single-array draft or pbs to see its "
                    + "honest loop).\n",
                    "submit_plan,not_exercised,no scheduler selected");
        }
        java.util.Optional<SchedulerAdapter> resolved = SchedulerAdapters.forName(name);
        if (resolved.isEmpty()) {
            return new SubmitLaneOutcome(String.format(Locale.ROOT,
                    "%nSubmit-lane review: REFUSED as a FINDING [SCHEDULER_NAME] unknown "
                            + "scheduler '%s' (supported: %s; the registry is the single "
                            + "owner - no default is ever picked). The sweep plan above "
                            + "stands.%n", name, SchedulerAdapters.supportedNames()),
                    String.format(Locale.ROOT, "submit_plan,refused,%s",
                            csvCell("SCHEDULER_NAME")));
        }
        SchedulerAdapter adapter = resolved.get();
        OperationResult<ArraySubmitPlan.SubmitPlan> drafted = ArraySubmitPlan.plan(plan,
                adapter);
        ArraySubmitPlan.SubmitPlan draftedPlan = drafted.getValue().orElse(null);
        if (!drafted.isSuccess() || draftedPlan == null) {
            return new SubmitLaneOutcome(String.format(Locale.ROOT,
                    "%nSubmit-lane review: REFUSED as a FINDING [%s] %s (the sweep plan "
                            + "above stands).%n", drafted.getCode(), drafted.getMessage()),
                    String.format(Locale.ROOT, "submit_plan,refused,%s",
                            csvCell(drafted.getCode())));
        }
        StringBuilder section = new StringBuilder();
        section.append(String.format(Locale.ROOT,
                "%nSubmit-lane review (guarded draft from the '%s' adapter's owned "
                        + "tokens):%n", adapter.name()));
        section.append(draftedPlan.reviewBlock());
        String shapeNote = draftedPlan.getShape() == ArraySubmitPlan.Shape.SINGLE_ARRAY
                ? "shape SINGLE_ARRAY" : "shape PER_TASK_LOOP (adapter's stated refusal)";
        return new SubmitLaneOutcome(section.toString(),
                String.format(Locale.ROOT, "submit_plan,SUBMIT_DRAFT_OK,%s on %s",
                        shapeNote, adapter.name()));
    }

    /**
     * Roadmap #77 seam: ASE-style extended-XYZ of the live cell via the tested
     * exporter; geometry only, explicit save is the only exit.
     */
    private static AnalysisReport analyzeCellExtXyzExport(Project project) {
        String label = AnalysisKind.CELL_EXTXYZ_EXPORT.getLabel();
        Cell cell = project.getCell();
        OperationResult<String> exported = ExtXyzCellExporter.export(cell);
        if (!exported.isSuccess() || exported.getValue().isEmpty()) {
            return failure(label, "Export refused: " + exported.getMessage());
        }
        String document = exported.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(exported.getMessage()).append("\n\n");
        text.append("Document preview (explicit save writes exactly these bytes):\n");
        text.append(document).append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("field,value");
        csv.add(String.format(Locale.ROOT, "atoms,%d", cell.numAtoms()));
        text.append("Honesty boundary: geometry ONLY - this document carries species, "
                + "positions and the lattice with pbc=\"T T T\" (the QE periodic "
                + "assumption); it carries NO energy/force/stress labels, so it is not a "
                + "training set - labelling belongs to the validated dataset pipeline "
                + "(#143/#144). Coordinates are Angstrom, rendered losslessly. The file is "
                + "written only through the explicit save action.");
        return new AnalysisReport(label, true, text.toString(), csv, document);
    }

    /**
     * Roadmap #53 spectrum layer: powder IR/Raman spectrum from a dynmat/ph modes
     * file via the tested Lorentzian parser. Grid, self-check and skipped-mode
     * accounting are done here; the broaden physics statement stays explicit.
     */
    private static AnalysisReport analyzeRamanIrSpectrum(ProjectProperty property,
            File source, AnalysisParameters params) throws IOException {
        String label = AnalysisKind.RAMAN_IR_SPECTRUM.getLabel();
        String channel = params.getSpectrumChannel() == null
                ? "ir" : params.getSpectrumChannel().trim().toLowerCase(Locale.ROOT);
        if (!"ir".equals(channel) && !"raman".equals(channel)) {
            return failure(label, "The channel must be \"ir\" or \"raman\"; got \""
                    + channel + "\".");
        }
        double fwhm = params.getFwhmCm1();
        if (!Double.isFinite(fwhm) || fwhm <= 0.0 || fwhm > 200.0) {
            return failure(label, "The FWHM must be a finite value within (0, 200] cm-1; "
                    + "got " + fwhm + ".");
        }
        QERamanIRSpectraParser parser = new QERamanIRSpectraParser(property);
        parser.parse(source);
        List<QERamanIRSpectraParser.SpectroMode> modes = parser.getModes();
        if (modes.isEmpty()) {
            return failure(label, "No spectroscopic mode rows (frequency + IR intensity "
                    + "+ Raman activity) were parsed from " + source.getName() + ".");
        }
        boolean raman = "raman".equals(channel);
        List<QERamanIRSpectraParser.SpectroMode> included = new ArrayList<>();
        int imaginarySkipped = 0;
        List<Double> imaginaryFreqs = new ArrayList<>();
        int zeroActivitySkipped = 0;
        double totalActivity = 0.0;
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (QERamanIRSpectraParser.SpectroMode mode : modes) {
            double activity = raman ? mode.getRamanActivity() : mode.getIrIntensity();
            if (mode.getFrequencyCm1() <= 0.0) {
                imaginarySkipped++;
                imaginaryFreqs.add(mode.getFrequencyCm1());
                continue;
            }
            if (activity <= 0.0) {
                zeroActivitySkipped++;
                continue;
            }
            included.add(mode);
            totalActivity += activity;
            lowest = Math.min(lowest, mode.getFrequencyCm1());
            highest = Math.max(highest, mode.getFrequencyCm1());
        }
        if (included.isEmpty()) {
            return failure(label, "No mode with positive " + channel.toUpperCase(Locale.ROOT)
                    + " activity and a real frequency remains; an empty spectrum is not "
                    + "evidence (imaginary skipped: " + imaginarySkipped
                    + ", zero-activity skipped: " + zeroActivitySkipped + ").");
        }
        // Auto grid: 10*FWHM margins either side, FWHM/10 sampling; hard 200k cap.
        double margin = 10.0 * fwhm;
        double gridMin = Math.max(0.0, lowest - margin);
        double gridMax = highest + margin;
        double gridStep = fwhm / 10.0;
        long expectedPoints = (long) Math.floor((gridMax - gridMin) / gridStep) + 1L;
        if (expectedPoints > 200_000L) {
            return failure(label, "The automatic grid would need " + expectedPoints
                    + " points (cap 200000); increase the FWHM or cover a narrower range.");
        }
        List<QERamanIRSpectraParser.SpectrumPoint> spectrum = parser.computePowderSpectra(
                gridMin, gridMax, gridStep, fwhm, raman);
        if (spectrum.isEmpty()) {
            return failure(label, "The spectrum evaluator returned no points for the "
                    + "computed grid; refusing to fabricate one.");
        }
        // Self-check: the grid integral must track the total included activity
        // (Lorentzians are area-normalized; window-edge tails are truncated).
        double integral = 0.0;
        for (int i = 1; i < spectrum.size(); i++) {
            integral += 0.5 * (spectrum.get(i - 1).intensity + spectrum.get(i).intensity)
                    * (spectrum.get(i).frequency - spectrum.get(i - 1).frequency);
        }
        double peakIntensity = 0.0;
        double peakFrequency = Double.NaN;
        for (QERamanIRSpectraParser.SpectrumPoint point : spectrum) {
            if (point.intensity > peakIntensity) {
                peakIntensity = point.intensity;
                peakFrequency = point.frequency;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Channel: %s; FWHM: %.4f cm-1 (Lorentzian broadening)%n",
                channel.toUpperCase(Locale.ROOT), fwhm));
        text.append(String.format(Locale.ROOT,
                "Modes parsed: %d; included (real frequency, positive %s activity): %d; "
                        + "imaginary (skipped): %d; zero-activity (skipped): %d%n",
                modes.size(), channel.toUpperCase(Locale.ROOT), included.size(),
                imaginarySkipped, zeroActivitySkipped));
        if (!imaginaryFreqs.isEmpty()) {
            StringBuilder freqs = new StringBuilder();
            for (int i = 0; i < Math.min(5, imaginaryFreqs.size()); i++) {
                freqs.append(i == 0 ? "" : ", ")
                        .append(String.format(Locale.ROOT, "%.4f", imaginaryFreqs.get(i)));
            }
            if (imaginaryFreqs.size() > 5) {
                freqs.append(", ...");
            }
            text.append("Imaginary frequencies skipped (unstable modes are not IR/Raman "
                    + "peaks): ").append(freqs).append(" cm-1\n");
        }
        text.append(String.format(Locale.ROOT,
                "Grid: %.4f .. %.4f cm-1 in steps of %.4f (%d points)%n", gridMin, gridMax,
                gridStep, spectrum.size()));
        text.append(String.format(Locale.ROOT,
                "SELF-CHECK - grid integral %.8f vs total included activity %.8f (ratio %.6f;"
                        + " deviation is the expected window-edge Lorentzian-tail "
                        + "truncation)%n", integral, totalActivity,
                totalActivity > 0.0 ? integral / totalActivity : Double.NaN));
        text.append(String.format(Locale.ROOT,
                "Strongest grid-sampled point: %.4f cm-1 at %.8f (display units, see "
                        + "honesty note)%n", peakFrequency, peakIntensity));
        List<String> csv = new ArrayList<>();
        csv.add("frequency_cm1,intensity_" + channel);
        int rows = 0;
        for (QERamanIRSpectraParser.SpectrumPoint point : spectrum) {
            if (rows >= 20_000) {
                csv.add("# remaining points omitted: CSV capped at 20000 rows");
                break;
            }
            csv.add(String.format(Locale.ROOT, "%.4f,%.8g", point.frequency,
                    point.intensity));
            rows++;
        }
        text.append("\nHonesty boundary: intensities/activities are the SCALAR values the "
                + "QE route printed (IR: as printed, D^2/A^2-amu convention; Raman: A^4/amu "
                + "activity) - the Lorentzian broadening is a DISPLAY choice, not tensor "
                + "orientation averaging (no depolarization ratio, no powder tensor "
                + "invariants), and NO Bose-Einstein temperature factor is applied to the "
                + "Raman channel. Peak positions come from grid sampling near the printed "
                + "mode frequencies, not from line fitting. Units on the intensity axis "
                + "inherit the source route's conventions.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Ion-insertion voltage profile from a hull CSV; plateaus from hull vertices only. */
    private static AnalysisReport analyzeVoltageProfile(File source, AnalysisParameters params)
            throws IOException {
        String label = AnalysisKind.VOLTAGE_PROFILE.getLabel();
        double charge = params.getIonCharge();
        List<QEHullThermodynamics.CompetingPhase> phases = new ArrayList<>();
        int rejected = 0;
        for (String raw : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split(",");
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
                rejected++; // Tolerated header row such as "formula,fraction_B,...".
                continue;
            }
            if (formula.isEmpty()) {
                rejected++;
                continue;
            }
            phases.add(new QEHullThermodynamics.CompetingPhase(formula, fraction, formation));
        }
        OperationResult<QEBatteryVoltage.VoltageProfile> built =
                QEBatteryVoltage.build(phases, charge);
        if (!built.isSuccess() || built.getValue().isEmpty()) {
            return failure(label, "Voltage-profile construction failed closed for "
                    + source.getName() + ": " + built.getMessage()
                    + (rejected > 0 ? " (" + rejected + " rows were rejected)" : ""));
        }
        QEBatteryVoltage.VoltageProfile profile = built.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append("; phases parsed: ")
                .append(phases.size());
        if (rejected > 0) {
            text.append("; rejected rows: ").append(rejected);
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT, "Insertion charge z = %.6g%n%n", profile.getIonCharge()));
        text.append("Lower-hull vertices (metastable phases excluded):\n");
        for (QEBatteryVoltage.HullVertex vertex : profile.getVertices()) {
            text.append(String.format(Locale.ROOT, "  %-14s x=%.4f  E_form=%+.6f eV/atom%n",
                    vertex.getFormula(), vertex.getFractionB(), vertex.getFormationEnergyEv()));
        }
        text.append("\nTwo-phase voltage plateaus:\n");
        List<String> csv = new ArrayList<>();
        csv.add("x_left,x_right,voltage_V,left_phase,right_phase");
        for (QEBatteryVoltage.Plateau plateau : profile.getPlateaus()) {
            text.append(String.format(Locale.ROOT, "  x %.4f -> %.4f : %+8.4f V  (%s -> %s)%n",
                    plateau.getLeft().getFractionB(), plateau.getRight().getFractionB(),
                    plateau.getVoltageV(), plateau.getLeft().getFormula(),
                    plateau.getRight().getFormula()));
            csv.add(String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%s,%s",
                    plateau.getLeft().getFractionB(), plateau.getRight().getFractionB(),
                    plateau.getVoltageV(), plateau.getLeft().getFormula(),
                    plateau.getRight().getFormula()));
        }
        text.append('\n');
        for (String note : profile.getNotes()) {
            text.append(" - ").append(note).append('\n');
        }
        text.append("\nFormation energies must be per atom and referenced consistently; the "
                + "fraction-1 phase anchors mu_B as printed. The CSV uses "
                + "formula,fraction_B,formation_energy_eV_per_atom with an optional header.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Templated-molecule placement preview with collision check; the live cell is untouched. */
    private static AnalysisReport analyzeAdsorptionPreview(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.ADSORPTION_PREVIEW.getLabel();
        Cell cell = project.getCell();
        if (cell == null) {
            return failure(label, "The project has no slab cell to adsorb onto.");
        }
        if (cell.numAtoms() < 1) {
            return failure(label, "The project cell contains no slab atoms.");
        }
        String moleculeName = params.getMoleculeName() == null ? ""
                : params.getMoleculeName().trim();
        Cell molecule = MoleculeAdsorber.createMolecule(moleculeName);
        if (molecule == null || molecule.numAtoms() < 1) {
            return failure(label, "Unknown molecule template '" + moleculeName
                    + "'. Supported templates: CO, H2O, NH3, OH, NO.");
        }
        double height = params.getAdsorbHeight();
        if (!(height >= 1.0) || !Double.isFinite(height)) {
            return failure(label, "The adsorption height must be a finite value >= 1.0 "
                    + "Angstrom (the placement builder's own minimum; got " + height + ").");
        }
        double x = params.getAdsorbX();
        double y = params.getAdsorbY();
        if (!(x >= 0.0 && x <= 1.0 && y >= 0.0 && y <= 1.0
                && Double.isFinite(x) && Double.isFinite(y))) {
            return failure(label, "Surface position must be fractional coordinates within "
                    + "[0,1] x [0,1] (got " + x + ", " + y + ").");
        }
        int slabAtoms = cell.numAtoms();
        MoleculeAdsorber adsorber = new MoleculeAdsorber(cell);
        adsorber.setMolecule(molecule);
        adsorber.setPosition(x, y);
        adsorber.setHeight(height);
        Cell combined = adsorber.adsorb();
        if (combined == null) {
            return failure(label, "The placement builder returned no combined cell.");
        }
        if (cell.numAtoms() != slabAtoms) {
            // Defensive honesty: the builder is contractually non-destructive.
            return failure(label, "Internal error: the placement mutated the project cell; "
                    + "the preview result was discarded.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Molecule template: ").append(moleculeName).append(" (")
                .append(molecule.numAtoms()).append(" atoms, fixed template geometry)\n");
        text.append(String.format(Locale.ROOT,
                "Fractional surface position: (%.4f, %.4f); height above topmost slab atom: %.4f Ang%n",
                x, y, height));
        text.append("Slab atoms: ").append(slabAtoms).append("; combined preview cell atoms: ")
                .append(combined.numAtoms()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Minimum adsorbate-slab contact distance (builder metric, 1.2 Ang limit): %.6f Ang%n",
                adsorber.getMinimumContactDistance()));
        text.append("Collision detected: ").append(adsorber.isCollisionDetected()).append('\n');
        for (String diagnostic : adsorber.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nThis preview places a fixed-geometry template molecule with a "
                + "collision check only; it does not rank adsorption sites, relax the "
                + "adsorbate/slab, or compute binding energies - those remain explicit engine "
                + "calculations. Nothing in the project cell was modified.");
        boolean success = !adsorber.isCollisionDetected();
        return new AnalysisReport(label, success, text.toString(), List.of(), null);
    }

    /**
     * Explicit band-gap summary parsing (Roadmap #47): the parser reads only
     * QE occupied/unoccupied or stated-gap lines; directness is reported only
     * when the engine states it, never inferred from a text summary.
     */
    private static AnalysisReport analyzeBandGapSummary(File source) {
        String label = AnalysisKind.BAND_GAP.getLabel();
        BandGapParser parser = new BandGapParser(source.getAbsolutePath());
        if (!parser.parse()) {
            String details = parser.getDiagnostics().isEmpty()
                    ? "No explicit or occupied/unoccupied band-gap summary was found."
                    : String.join("\n", parser.getDiagnostics());
            return failure(label, "Band-gap parsing failed closed for " + source.getName()
                    + ":\n" + details);
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT, "Gap: %.6f eV%n", parser.getBandGap()));
        if (Double.isFinite(parser.getFermiEnergy())) {
            text.append(String.format(Locale.ROOT, "Fermi energy: %.6f eV%n",
                    parser.getFermiEnergy()));
        }
        if (parser.isDirectKnown()) {
            text.append(parser.isDirect()
                    ? "Directness: explicitly reported direct.\n"
                    : "Directness: explicitly reported indirect.\n");
        } else {
            text.append("Directness: unknown; this QE log summary is not k-resolved "
                    + "evidence.\n");
        }
        text.append("Classification: ").append(parser.isInsulator()
                ? "gapped above the 0.01 eV analysis tolerance." : "metallic/small-gap "
                        + "within the 0.01 eV analysis tolerance.").append('\n');
        if (!parser.getDiagnostics().isEmpty()) {
            text.append("\nParser diagnostics:\n");
            for (String diagnostic : parser.getDiagnostics()) {
                text.append(" - ").append(diagnostic).append('\n');
            }
        }
        text.append("\nParsed statements are repeated as found in the log; a parsed gap "
                + "summary is not a convergence-tested band gap (k-mesh/functional evidence "
                + "is separate).");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /**
     * Nonuniform trapezoidal integration of one validated projwfc.x component
     * file (Roadmap #49, single-projection level). The integral is deliberately
     * NOT called an electron count without an occupation/Fermi convention.
     */
    private static AnalysisReport analyzeDosIntegration(File source) {
        String label = AnalysisKind.DOS_INTEGRATION.getLabel();
        QEPdosParser.PdosComponent component;
        try {
            component = QEPdosParser.parseSingleFile(source);
        } catch (IOException | RuntimeException ex) {
            return failure(label, "PDOS parsing failed closed for " + source.getName() + ": "
                    + ex.getMessage());
        }
        if (component == null) {
            return failure(label, "Not a validated projwfc.x PDOS file: " + source.getName()
                    + "\nExpected the atm#N(element)_wfc#N(l) projectwfc naming and a header "
                    + "line identifying PDOS columns; headerless files are refused.");
        }
        double[] energies = component.getEnergies();
        double[] pdos = component.getPdos();
        double integral;
        try {
            integral = QEPdosParser.integratePdos(energies, pdos);
        } catch (IllegalArgumentException ex) {
            return failure(label, "The PDOS grid failed validation: " + ex.getMessage());
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Projection: atom #").append(component.getAtomIndex()).append(" (")
                .append(component.getElement()).append("), wfc #").append(component.getWfcIndex())
                .append(" (l=").append(component.getOrbitalL()).append("), spin channel ")
                .append(component.getSpinChannel()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Emission energy range of this file: %.6f .. %.6f eV (%d grid points)%n",
                energies[0], energies[energies.length - 1], energies.length));
        text.append(String.format(Locale.ROOT,
                "Integral (nonuniform trapezoid, energies in eV): %.6f%n", integral));
        List<String> csv = new ArrayList<>();
        csv.add("energy_eV,pdos_per_eV");
        int cap = Math.min(energies.length, 20000);
        for (int i = 0; i < cap; i++) {
            csv.add(String.format(Locale.ROOT, "%.10f,%.10f", energies[i], pdos[i]));
        }
        if (energies.length > cap) {
            text.append(String.format(Locale.ROOT,
                    "CSV export truncated at %d of %d rows.%n", cap, energies.length));
        }
        text.append("\nThe integral is the summed projection area over the emitted interval "
                + "only; it is NOT an electron count without a spin/orbital-degeneracy and "
                + "occupation convention, and it says nothing about states outside the "
                + "emitted energy window.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Effective-mass tensor from a local quadratic least-squares fit (Roadmap
     * #159, data layer): reads a CSV of (kx,ky,kz in bohr^-1, E in Ry) rows,
     * fits with the tested EffectiveMassTensor, and decomposes the symmetric
     * inverse-Hessian eigenvalues with the tested Jacobi solver.
     */
    private static AnalysisReport analyzeEffectiveMass(File source) throws IOException {
        String label = AnalysisKind.EFFECTIVE_MASS.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " exceeds the 64 MiB parse bound.");
        }
        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);
        List<double[]> kpoints = new ArrayList<>();
        List<Double> energies = new ArrayList<>();
        int rejected = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] cells = trimmed.split("[,\\s]+");
            if (cells.length < 4) {
                rejected++;
                continue;
            }
            try {
                double kx = Double.parseDouble(normalizeExponent(cells[0]));
                double ky = Double.parseDouble(normalizeExponent(cells[1]));
                double kz = Double.parseDouble(normalizeExponent(cells[2]));
                double energy = Double.parseDouble(normalizeExponent(cells[3]));
                if (!Double.isFinite(kx) || !Double.isFinite(ky) || !Double.isFinite(kz)
                        || !Double.isFinite(energy)) {
                    rejected++;
                    continue;
                }
                kpoints.add(new double[] {kx, ky, kz});
                energies.add(energy);
            } catch (NumberFormatException ex) {
                rejected++;
            }
        }
        if (kpoints.size() < 7) {
            return failure(label, "Only " + kpoints.size() + " numeric (kx,ky,kz,E) rows were "
                    + "parsed (rejected rows: " + rejected + "); the quadratic fit needs >= 7 "
                    + "points spanning the local region around the extremum. The file contract "
                    + "is: k in bohr^-1, E in Ry, one row per sampled k point.");
        }
        double[][] kArray = kpoints.toArray(new double[0][]);
        double[] eArray = energies.stream().mapToDouble(Double::doubleValue).toArray();
        double[][] tensor = EffectiveMassTensor.calculateEffectiveMassTensor(kArray, eArray);
        if (tensor == null) {
            return failure(label, "The quadratic fit was singular/ill-conditioned for these "
                    + "k points (the sampled region must span all curvature directions).");
        }
        SymmetricEigen3.EigenResult eigen;
        try {
            eigen = SymmetricEigen3.eigenvalues(tensor);
        } catch (IllegalArgumentException ex) {
            return failure(label, "The fitted tensor failed eigen decomposition: "
                    + ex.getMessage());
        }
        if (!eigen.isConverged()) {
            return failure(label, "The eigen decomposition did not converge within the Jacobi "
                    + "sweep budget; not reporting suspicious eigenmasses.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Fit rows: ").append(kpoints.size()).append("; rejected rows: ")
                .append(rejected).append('\n');
        text.append("Units: k in bohr^-1, E in Ry (class contract).\n\n");
        text.append("Inverse-Hessian (m*) tensor as fitted, rows (Ry^-1 bohr^-2):\n");
        for (double[] row : tensor) {
            text.append(String.format(Locale.ROOT, "  % .8e % .8e % .8e%n",
                    row[0], row[1], row[2]));
        }
        List<String> csv = new ArrayList<>();
        csv.add("tensor_row,col_1_reverse_hessian,col_2,col_3");
        for (double[] row : tensor) {
            csv.add(String.format(Locale.ROOT, "%.10e,%.10e,%.10e", row[0], row[1], row[2]));
        }
        double[] values = eigen.getEigenvalues();
        text.append(String.format(Locale.ROOT,
                "%nInverse-Hessian eigenvalues (sorted): %.8e %.8e %.8e%n",
                values[0], values[1], values[2]));
        text.append(String.format(Locale.ROOT,
                "Physical atomic-unit masses m*/m_e = 2 x eigenvalue (E was in Ry): "
                        + "%.6f %.6f %.6f%n",
                2.0 * values[0], 2.0 * values[1], 2.0 * values[2]));
        csv.add(String.format(Locale.ROOT, "eigenvalues_asc,%.10e,%.10e,%.10e",
                values[0], values[1], values[2]));
        csv.add(String.format(Locale.ROOT, "masses_me_2x,%.10f,%.10f,%.10f",
                2.0 * values[0], 2.0 * values[1], 2.0 * values[2]));
        text.append(String.format(Locale.ROOT,
                "Jacobi converged in %d sweep(s).%n", eigen.getSweeps()));
        text.append("\nHonesty boundary: the fit is an unweighted 7-parameter quadratic "
                + "(normal equations; no covariance is computed by the class). Sample a LOCAL "
                + "region around the band extremum only - distant points violate the "
                + "parabolic assumption and distort the tensor. The x2 unit factor comes from "
                + "Ry-vs-Hartree in the class contract; verify against a known parabolic "
                + "reference before reporting m*. A POSITIVE eigenmass means an electron-like "
                + "local minimum; a NEGATIVE one means a hole-like local maximum (negative "
                + "curvature) - the sign is the curvature, not an error.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Streaming trajectory index (Roadmap #127 data layer): one O(1)-heap linear
     * pass that maps frames to byte offsets for future windowed/decimated reads.
     * Coordinates are not parsed here; validation belongs to the bounded MD_MSD
     * analysis. A truncated tail frame is reported, never indexed as complete.
     */
    private static AnalysisReport analyzeTrajectoryIndex(File source) {
        String label = AnalysisKind.TRAJECTORY_INDEX.getLabel();
        OperationResult<TrajectoryIndexReader.TrajectoryIndex> indexed =
                TrajectoryIndexReader.index(source.toPath());
        if (!indexed.isSuccess() || indexed.getValue().isEmpty()) {
            return failure(label, "Trajectory index failed closed: " + indexed.getMessage());
        }
        TrajectoryIndexReader.TrajectoryIndex index = indexed.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "File bytes: %d; complete frames: %d; atoms per frame: %d%n",
                index.getFileBytes(), index.getFrameCount(), index.getAtomsPerFrame()));
        text.append(String.format(Locale.ROOT,
                "Stored frame offsets: %d%s (storage cap %d)%n", index.getOffsets().size(),
                index.isOffsetsComplete() ? " (complete)" : " (CAPPED - later frames counted "
                        + "but not stored)", TrajectoryIndexReader.MAX_STORED_OFFSETS));
        text.append("Truncated tail frame after the last complete frame: ")
                .append(index.isTruncatedTail() ? "YES (reported, not indexed)" : "no")
                .append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("frame_index,byte_offset");
        int csvCap = 20000;
        List<Long> offsets = index.getOffsets();
        for (int i = 0; i < offsets.size() && i < csvCap; i++) {
            csv.add(String.format(Locale.ROOT, "%d,%d", i + 1, offsets.get(i)));
        }
        if (offsets.size() > csvCap) {
            text.append(String.format(Locale.ROOT, "CSV truncated at %d offsets.%n", csvCap));
        }
        text.append("\nThis index was built in one streaming pass with constant heap - the "
                + "grounds for large-trajectory work (Roadmap #127). Coordinates were not "
                + "parsed or validated here; windowed, decimated reads and the bounded "
                + "MD_MSD analysis remain the consuming layers.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #127 consumption: windowed/decimated per-frame bounding-box and
     * centroid statistics on top of the tested streaming index - seeks instead of
     * scans, samples instead of materializing.
     */
    private static AnalysisReport analyzeTrajectoryWindowScan(File source,
            AnalysisParameters params) throws IOException {
        String label = AnalysisKind.TRAJECTORY_WINDOW_SCAN.getLabel();
        OperationResult<TrajectoryIndexReader.TrajectoryIndex> indexed =
                TrajectoryIndexReader.index(source.toPath());
        if (!indexed.isSuccess() || indexed.getValue().isEmpty()) {
            return failure(label, "Indexing refused: " + indexed.getMessage());
        }
        TrajectoryIndexReader.TrajectoryIndex index = indexed.getValue().get();
        if (!index.isOffsetsComplete()) {
            return failure(label, "The index stored only the first "
                    + TrajectoryIndexReader.MAX_STORED_OFFSETS
                    + " frame offsets for this file; the window reader requires a complete "
                    + "offset table and refuses to sample from a partial one (decimate the "
                    + "file upstream if you need this many frames).");
        }
        OperationResult<TrajectoryWindowReader.WindowStats> sampled =
                TrajectoryWindowReader.sample(source, index.getOffsets(),
                        index.getAtomsPerFrame(), params.getWindowStartFrame(),
                        params.getWindowStride());
        if (!sampled.isSuccess() || sampled.getValue().isEmpty()) {
            return failure(label, "Window sampling refused: " + sampled.getMessage());
        }
        TrajectoryWindowReader.WindowStats stats = sampled.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Complete frames indexed: %d (atoms/frame %d); truncated tail: %s%n",
                index.getFrameCount(), index.getAtomsPerFrame(),
                index.isTruncatedTail() ? "yes (excluded from sampling)" : "no"));
        text.append(String.format(Locale.ROOT,
                "%s; start frame %d; sampled frames capped at %d%n", sampled.getMessage(),
                params.getWindowStartFrame(), TrajectoryWindowReader.MAX_SAMPLED_FRAMES));
        double[] globalMin = stats.getGlobalMin();
        double[] globalMax = stats.getGlobalMax();
        text.append(String.format(Locale.ROOT,
                "Global sampled bounding box (Angstrom, as printed):%n"
                        + "  x: %.6f .. %.6f%n  y: %.6f .. %.6f%n  z: %.6f .. %.6f%n",
                globalMin[0], globalMax[0], globalMin[1], globalMax[1],
                globalMin[2], globalMax[2]));
        // Consecutive-centroid drift across the sampled window.
        double meanDrift = 0.0;
        double maxDrift = 0.0;
        int driftPairs = 0;
        int maxPairFrom = 0;
        List<TrajectoryWindowReader.FrameStats> rows = stats.getFrames();
        for (int i = 1; i < rows.size(); i++) {
            double[] a = rows.get(i - 1).getCentroid();
            double[] b = rows.get(i).getCentroid();
            double drift = Math.sqrt((b[0] - a[0]) * (b[0] - a[0])
                    + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
            meanDrift += drift;
            driftPairs++;
            if (drift > maxDrift) {
                maxDrift = drift;
                maxPairFrom = rows.get(i - 1).getFrameIndex();
            }
        }
        if (driftPairs > 0) {
            meanDrift /= driftPairs;
            text.append(String.format(Locale.ROOT,
                    "Centroid drift between consecutive SAMPLED frames: mean %.6f A, "
                            + "max %.6f A (from frame %d)%n", meanDrift, maxDrift,
                    maxPairFrom));
        }
        List<String> csv = new ArrayList<>();
        csv.add("frame_index,centroid_x,centroid_y,centroid_z,"
                + "min_x,min_y,min_z,max_x,max_y,max_z");
        for (TrajectoryWindowReader.FrameStats row : rows) {
            double[] min = row.getMin();
            double[] max = row.getMax();
            double[] centroid = row.getCentroid();
            csv.add(String.format(Locale.ROOT, "%d,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f",
                    row.getFrameIndex(), centroid[0], centroid[1], centroid[2],
                    min[0], min[1], min[2], max[0], max[1], max[2]));
        }
        text.append("\nHonesty boundary: reads land on byte offsets from the batch-56 "
                + "index (seek, not scan); statistics cover the SAMPLED window only - with "
                + "stride > 1 they do not describe skipped frames. Coordinates are used as "
                + "printed (XYZ Angstrom convention, unvalidated; extXYZ columns beyond "
                + "species/x/y/z are not interpreted), and NO periodic unwrapping is "
                + "applied - MSD/diffusion consumers must unwrap against the cell first "
                + "(#156). Partial files fail closed rather than reporting partial rows.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * extXYZ ML dataset validation (Roadmap #143 data layer): schema, label
     * presence, lattice/properties metadata, species coverage, energy range and
     * exact-byte duplicate (leakage) review through the tested
     * {@link ExtXyzDatasetValidator}. Force-energy consistency and split hygiene
     * need a model and are stated as outside this validator.
     */
    private static AnalysisReport analyzeDatasetCheck(File source) {
        String label = AnalysisKind.MLP_DATASET_CHECK.getLabel();
        OperationResult<ExtXyzDatasetValidator.DatasetReport> validated =
                ExtXyzDatasetValidator.validate(source.toPath());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "Dataset validation failed closed: " + validated.getMessage());
        }
        ExtXyzDatasetValidator.DatasetReport report = validated.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Frames: %d; atoms per frame: %d..%d%n", report.getFrameCount(),
                report.getMinAtoms(), report.getMaxAtoms()));
        text.append(String.format(Locale.ROOT,
                "Frames with energy/free_energy label: %d of %d%n",
                report.getFramesWithEnergy(), report.getFrameCount()));
        if (report.getFramesWithEnergy() > 0) {
            text.append(String.format(Locale.ROOT,
                    "Energy label range: %.10f .. %.10f (label units - ASE convention: eV)%n",
                    report.getMinEnergyEv(), report.getMaxEnergyEv()));
        }
        text.append(String.format(Locale.ROOT,
                "Species (%d%s): %s%n", report.getSpecies().size(),
                report.isSpeciesListComplete() ? "" : "+, list capped",
                String.join(" ", report.getSpecies())));
        text.append(String.format(Locale.ROOT,
                "Frames with a valid 9-number Lattice: %d of %d%n",
                report.getFramesWithLattice(), report.getFrameCount()));
        if (!report.getPropertiesSchema().isEmpty()) {
            text.append("First-frame Properties schema: \"").append(report.getPropertiesSchema())
                    .append("\"\n");
        }
        text.append(String.format(Locale.ROOT, "Exact-byte duplicate frames: %d%n",
                report.getDuplicateCount()));
        List<String> csv = new ArrayList<>();
        csv.add("duplicate_frame_a,duplicate_frame_b,sha256_equal");
        for (int[] pair : report.getDuplicatePairs()) {
            csv.add(pair[0] + "," + pair[1] + ",true");
        }
        for (String warning : report.getWarnings()) {
            text.append("WARNING: ").append(warning).append('\n');
        }
        text.append("\nHonesty boundary: this is schema-level validation only - force-energy "
                + "consistency, descriptor distances and split/leakage review require a model "
                + "and references; energy 'units' are the label's own convention per the ASE "
                + "extXYZ schema and are never converted here. Files above 64 MiB belong to "
                + "offline tooling.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Same-grid energy-series comparison (Roadmap #124 data layer): deltas,
     * RMS/max deviation and sign crossings between two energy columns that
     * share one parameter column. Reference alignment (Fermi/VBM/vacuum) is
     * never applied silently - aligned overlays are an explicit user choice.
     */
    private static AnalysisReport analyzeSeriesCompare(File source) {
        String label = AnalysisKind.ENERGY_SERIES_COMPARE.getLabel();
        OperationResult<EnergySeriesComparer.SeriesComparison> compared =
                EnergySeriesComparer.compare(source.toPath());
        if (!compared.isSuccess() || compared.getValue().isEmpty()) {
            return failure(label, "Series comparison failed closed: " + compared.getMessage());
        }
        EnergySeriesComparer.SeriesComparison comparison =
                compared.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Columns: ").append(comparison.getParameterLabel()).append(" | ")
                .append(comparison.getFirstSeriesLabel()).append(" | ")
                .append(comparison.getSecondSeriesLabel())
                .append("  (first-line treatment is a header heuristic)\n");
        text.append(String.format(Locale.ROOT,
                "Rows compared: %d; rejected rows: %d; out-of-order parameters: %d%n",
                comparison.getRowCount(), comparison.getRejectedRows(),
                comparison.getOutOfOrderRows()));
        text.append(String.format(Locale.ROOT,
                "Delta E2 - E1: RMS %.10f; mean signed %.10f%n",
                comparison.getRmsDeltaEv(), comparison.getMeanSignedDeltaEv()));
        text.append(String.format(Locale.ROOT,
                "Max |delta| %.10f at %s = %.6g%n", comparison.getMaxAbsDeltaEv(),
                comparison.getParameterLabel(), comparison.getMaxAbsDeltaAtParameter()));
        text.append(String.format(Locale.ROOT,
                "First/last row deltas: %.10f / %.10f; sign crossings of delta: %d%n",
                comparison.getFirstDeltaEv(), comparison.getLastDeltaEv(),
                comparison.getSignCrossings()));
        List<String> csv = new ArrayList<>();
        csv.add(csvCell(comparison.getParameterLabel()) + "," + csvCell(
                comparison.getFirstSeriesLabel()) + "," + csvCell(
                        comparison.getSecondSeriesLabel()) + ",delta_e2_minus_e1");
        int cap = 20000;
        List<double[]> rows = comparison.getRows();
        for (int i = 0; i < rows.size() && i < cap; i++) {
            double[] row = rows.get(i);
            csv.add(String.format(Locale.ROOT, "%.10f,%.10f,%.10f,%.10f",
                    row[0], row[1], row[2], row[2] - row[1]));
        }
        if (rows.size() > cap) {
            text.append(String.format(Locale.ROOT, "CSV truncated at %d rows.%n", cap));
        }
        text.append("\nHonesty boundary: both series share one parameter column, so the "
                + "grid agreement is by construction (unsorted rows are counted as notes, "
                + "not silently re-sorted). NO reference alignment was applied - if these "
                + "energies come from different states/codes, align Fermi/VBM/vacuum "
                + "references explicitly before trusting the deltas.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Symmetric 3x3 tensor eigenanalysis (Roadmap #125 partial): the first
     * three non-blank lines each supply three finite numbers (matrix rows);
     * the tested Jacobi solver ({@link SymmetricEigen3}) gives sorted
     * eigenvalues only when the decomposition converges. An internal
     * determinant cross-check (cofactor vs eigenvalue product) guards the
     * result; the report attaches no physical interpretation - tensor type is
     * the caller's context (dielectric, stress, inertia, ...).
     */
    private static AnalysisReport analyzeTensorEigen(File source) throws IOException {
        String label = AnalysisKind.TENSOR_EIGEN.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " exceeds the 64 MiB parse bound.");
        }
        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);
        double[][] matrix = new double[3][3];
        int rows = 0;
        for (String line : lines) {
            if (rows >= 3) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] cells = trimmed.split("[,\\s]+");
            if (cells.length < 3) {
                return failure(label, "Line \"" + trimmed + "\" has fewer than 3 numeric "
                        + "values; the contract is the 3x3 matrix rows, three per line.");
            }
            for (int col = 0; col < 3; col++) {
                try {
                    matrix[rows][col] = Double.parseDouble(normalizeExponent(cells[col]));
                } catch (NumberFormatException ex) {
                    return failure(label, "Non-numeric value \"" + cells[col]
                            + "\" in matrix row " + (rows + 1) + ".");
                }
                if (!Double.isFinite(matrix[rows][col])) {
                    return failure(label, "Non-finite value \"" + cells[col]
                            + "\" in matrix row " + (rows + 1) + ".");
                }
            }
            rows++;
        }
        if (rows < 3) {
            return failure(label, "Only " + rows + " matrix row(s) were found; three numeric "
                    + "rows (the first three non-blank lines) are required.");
        }
        SymmetricEigen3.EigenResult eigen;
        try {
            eigen = SymmetricEigen3.eigenvalues(matrix);
        } catch (IllegalArgumentException ex) {
            return failure(label, "Tensor refused: " + ex.getMessage()
                    + " (an asymmetric tensor has no real eigenbasis; this analysis covers "
                    + "symmetric tensors only).");
        }
        if (!eigen.isConverged()) {
            return failure(label, "The Jacobi eigen decomposition did not converge within "
                    + "the sweep budget; not reporting suspicious eigenvalues.");
        }
        double[] values = eigen.getEigenvalues();
        double traceDirect = matrix[0][0] + matrix[1][1] + matrix[2][2];
        double traceEigen = values[0] + values[1] + values[2];
        double detDirect = matrix[0][0] * (matrix[1][1] * matrix[2][2]
                - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
        double detEigen = values[0] * values[1] * values[2];
        double detDeviation = Math.abs(detEigen - detDirect)
                / Math.max(1.0e-30, Math.max(Math.abs(detEigen), Math.abs(detDirect)));
        double traceDeviation = Math.abs(traceEigen - traceDirect)
                / Math.max(1.0e-30, Math.max(Math.abs(traceEigen), Math.abs(traceDirect)));
        if (detDeviation > 1.0e-9 || traceDeviation > 1.0e-12) {
            return failure(label, String.format(Locale.ROOT,
                    "Internal self-check failed: trace deviation %.3e, determinant deviation "
                            + "%.3e; refusing to display inconsistent eigensystem.",
                    traceDeviation, detDeviation));
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append("Tensor as read (rows):\n");
        for (double[] row : matrix) {
            text.append(String.format(Locale.ROOT, "  % .10e % .10e % .10e%n",
                    row[0], row[1], row[2]));
        }
        text.append(String.format(Locale.ROOT,
                "%nEigenvalues (sorted ascending): %.10e  %.10e  %.10e%n",
                values[0], values[1], values[2]));
        text.append(String.format(Locale.ROOT,
                "Trace: direct %.10e vs eigen-sum %.10e (dev %.1e)%n", traceDirect,
                traceEigen, traceDeviation));
        text.append(String.format(Locale.ROOT,
                "Determinant: cofactor %.10e vs eigen-product %.10e (dev %.1e)%n",
                detDirect, detEigen, detDeviation));
        if (values[0] > 0.0) {
            text.append("Structure: SPD - all three eigenvalues are strictly positive.\n");
        } else if (values[0] == 0.0) {
            text.append("Structure: positive semi-definite (smallest eigenvalue is zero).\n");
        } else {
            text.append("Structure: INDEFINITE - negative eigenvalue(s) present.\n");
        }
        double spread = Math.abs(values[2]) > 0.0
                ? Math.abs(values[2] - values[0]) / Math.abs(values[2]) : 0.0;
        text.append(String.format(Locale.ROOT,
                "Anisotropy spread |lambda_max - lambda_min|/|lambda_max| = %.6f%n",
                spread));
        List<String> csv = new ArrayList<>();
        csv.add("eigen_index,eigenvalue");
        for (int i = 0; i < 3; i++) {
            csv.add(String.format(Locale.ROOT, "%d,%.10e", i + 1, values[i]));
        }
        text.append("\nHonesty boundary: no physical interpretation is attached - the same "
                + "arithmetic serves dielectric, stress, inertia or other symmetric tensors; "
                + "units and meaning come from the source you selected. Eigenvectors are not "
                + "reported at this layer (Roadmap #125 viewer work).");
        text.append(String.format(Locale.ROOT, "Jacobi converged in %d sweep(s).",
                eigen.getSweeps()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #125 directional layer: eigen-pairs plus the quadratic-form
     * directional surface n^T.T.n on a fixed 15-degree grid, with residual and
     * orthonormality self-checks that fail closed.
     */
    private static AnalysisReport analyzeTensorDirectional(File source) throws IOException {
        String label = AnalysisKind.TENSOR_DIRECTIONAL.getLabel();
        long size = Files.size(source.toPath());
        if (size > 64L * 1024L * 1024L) {
            return failure(label, source.getName() + " exceeds the 64 MiB parse bound.");
        }
        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);
        double[][] matrix = new double[3][3];
        int rowsRead = 0;
        for (String line : lines) {
            if (rowsRead >= 3) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] cells = trimmed.split("[,\\s]+");
            if (cells.length < 3) {
                return failure(label, "Line \"" + trimmed + "\" has fewer than 3 numeric "
                        + "values; the contract is the 3x3 matrix rows, three per line.");
            }
            for (int col = 0; col < 3; col++) {
                try {
                    matrix[rowsRead][col] = Double.parseDouble(normalizeExponent(cells[col]));
                } catch (NumberFormatException ex) {
                    return failure(label, "Non-numeric value \"" + cells[col]
                            + "\" in matrix row " + (rowsRead + 1) + ".");
                }
                if (!Double.isFinite(matrix[rowsRead][col])) {
                    return failure(label, "Non-finite value \"" + cells[col]
                            + "\" in matrix row " + (rowsRead + 1) + ".");
                }
            }
            rowsRead++;
        }
        if (rowsRead < 3) {
            return failure(label, "Only " + rowsRead + " matrix row(s) were found; three "
                    + "numeric rows (the first three non-blank lines) are required.");
        }
        SymmetricEigen3.EigenDecomposition eigen;
        try {
            eigen = SymmetricEigen3.eigenvectors(matrix);
        } catch (IllegalArgumentException ex) {
            return failure(label, "Tensor refused: " + ex.getMessage()
                    + " (an asymmetric tensor has no real eigenbasis; this analysis covers "
                    + "symmetric tensors only).");
        }
        if (!eigen.isConverged()) {
            return failure(label, "The Jacobi eigen decomposition did not converge within "
                    + "the sweep budget; not reporting suspicious eigensystem.");
        }
        double[] values = eigen.getEigenvalues();
        double[][] vectors = eigen.getEigenvectors();
        double scale = Math.max(1.0, Math.abs(values[2]));
        double tolerance = 1.0e-9 * scale;
        // Self-check: residuals and orthonormality must pass before any report.
        double maxResidual = 0.0;
        double maxOrtho = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) {
                double av = matrix[k][0] * vectors[i][0] + matrix[k][1] * vectors[i][1]
                        + matrix[k][2] * vectors[i][2];
                maxResidual = Math.max(maxResidual,
                        Math.abs(av - values[i] * vectors[i][k]));
            }
            for (int j = 0; j < 3; j++) {
                double dot = vectors[i][0] * vectors[j][0] + vectors[i][1] * vectors[j][1]
                        + vectors[i][2] * vectors[j][2];
                maxOrtho = Math.max(maxOrtho, Math.abs(dot - (i == j ? 1.0 : 0.0)));
            }
        }
        if (maxResidual > tolerance || maxOrtho > tolerance) {
            return failure(label, String.format(Locale.ROOT,
                    "Internal self-check failed: residual %.3e, orthonormality %.3e "
                            + "(tolerance %.3e); refusing to display an unreliable "
                            + "eigenbasis.", maxResidual, maxOrtho, tolerance));
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Self-check: residual %.3e, orthonormality %.3e (tolerance %.3e)%n%n",
                maxResidual, maxOrtho, tolerance));
        text.append(String.format(Locale.ROOT,
                " %-8s %-16s %-34s%n", "eigenvalue", "value", "principal direction"));
        for (int i = 0; i < 3; i++) {
            text.append(String.format(Locale.ROOT, " %-8d %-16.10e (% .8f, % .8f, % .8f)%n",
                    i + 1, values[i], vectors[i][0], vectors[i][1], vectors[i][2]));
        }
        // Directional surface on a fixed 15-degree grid (13 x 24 = 312 samples).
        List<String> csv = new ArrayList<>();
        csv.add("azimuth_deg,polar_deg,directional_value");
        double gridMin = Double.POSITIVE_INFINITY;
        double gridMax = Double.NEGATIVE_INFINITY;
        double gridMinAz = 0.0;
        double gridMinPol = 0.0;
        double gridMaxAz = 0.0;
        double gridMaxPol = 0.0;
        for (int ip = 0; ip <= 12; ip++) {
            double polar = Math.toRadians(15.0 * ip);
            for (int ia = 0; ia < 24; ia++) {
                double azimuth = Math.toRadians(15.0 * ia);
                double nx = Math.sin(polar) * Math.cos(azimuth);
                double ny = Math.sin(polar) * Math.sin(azimuth);
                double nz = Math.cos(polar);
                double value = nx * (matrix[0][0] * nx + matrix[0][1] * ny
                        + matrix[0][2] * nz)
                        + ny * (matrix[1][0] * nx + matrix[1][1] * ny + matrix[1][2] * nz)
                        + nz * (matrix[2][0] * nx + matrix[2][1] * ny + matrix[2][2] * nz);
                if (value < gridMin) {
                    gridMin = value;
                    gridMinAz = 15.0 * ia;
                    gridMinPol = 15.0 * ip;
                }
                if (value > gridMax) {
                    gridMax = value;
                    gridMaxAz = 15.0 * ia;
                    gridMaxPol = 15.0 * ip;
                }
                csv.add(String.format(Locale.ROOT, "%.1f,%.1f,%.10e", 15.0 * ia, 15.0 * ip,
                        value));
            }
        }
        // Consistency: sampled values must stay inside the eigen-bounds.
        if (gridMin < values[0] - tolerance || gridMax > values[2] + tolerance) {
            return failure(label, String.format(Locale.ROOT,
                    "Sampled directional values escaped the eigen-bounds (grid %.6e..%.6e "
                            + "vs eigen %.6e..%.6e); internal error.", gridMin, gridMax,
                    values[0], values[2]));
        }
        text.append(String.format(Locale.ROOT,
                "%nDirectional grid (15 deg, %d samples): min %.10e at (az %.1f, pol %.1f); "
                        + "max %.10e at (az %.1f, pol %.1f)%n", csv.size() - 1, gridMin,
                gridMinAz, gridMinPol, gridMax, gridMaxAz, gridMaxPol));
        text.append(String.format(Locale.ROOT,
                "Eigen-bounds for reference: [%.10e, %.10e]; grid min/max approach them "
                        + "at 15-degree resolution.%n", values[0], values[2]));
        text.append("\nHonesty boundary: this is the quadratic-form directional surface "
                + "n^T.T.n of a rank-2 symmetric tensor only - rank-4 elastic surfaces "
                + "(anisotropic stiffness evaluation, #119 ELATE) are NOT covered. "
                + "Eigenvector signs are gauge (canonical display sign applied); the "
                + "surface itself is sign-invariant. Units and physical meaning come from "
                + "the source you selected; no interpretation is attached at this layer.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #81 preview: general integer 3x3 supercell transformation with an
     * exact integer determinant - new lattice, volume and atom count only; no
     * atoms are written, generated or modified.
     */
    private static AnalysisReport analyzeSupercellPreview(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.SUPERCELL_PREVIEW.getLabel();
        Cell cell = project.getCell();
        if (cell == null || cell.numAtoms() <= 0) {
            return failure(label, "The project has no atoms/cell to preview a supercell of.");
        }
        OperationResult<SupercellMatrixValidator.SupercellTransform> validated =
                SupercellMatrixValidator.validate(params.getSupercellSpec());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "Matrix refused: " + validated.getMessage());
        }
        SupercellMatrixValidator.SupercellTransform transform =
                validated.getValue().get();
        double[][] lattice = cell.copyLattice();
        if (lattice == null || lattice.length < 3) {
            return failure(label, "The live cell has no 3x3 lattice to transform.");
        }
        double volume = lattice[0][0] * (lattice[1][1] * lattice[2][2]
                - lattice[1][2] * lattice[2][1])
                - lattice[0][1] * (lattice[1][0] * lattice[2][2]
                        - lattice[1][2] * lattice[2][0])
                + lattice[0][2] * (lattice[1][0] * lattice[2][1]
                        - lattice[1][1] * lattice[2][0]);
        if (Math.abs(volume) < 1.0e-12 || !Double.isFinite(volume)) {
            return failure(label, "The live cell lattice is numerically degenerate "
                    + "(|det| too small); refusing to preview a supercell.");
        }
        double[][] newLattice = transform.applyToLattice(lattice);
        double newVolume = newLattice[0][0] * (newLattice[1][1] * newLattice[2][2]
                - newLattice[1][2] * newLattice[2][1])
                - newLattice[0][1] * (newLattice[1][0] * newLattice[2][2]
                        - newLattice[1][2] * newLattice[2][0])
                + newLattice[0][2] * (newLattice[1][0] * newLattice[2][1]
                        - newLattice[1][1] * newLattice[2][0]);
        double expected = transform.getDeterminant() * volume;
        double deviation = Math.abs(Math.abs(newVolume) - Math.abs(expected))
                / Math.max(1.0e-30, Math.abs(expected));
        if (deviation > 1.0e-9) {
            return failure(label, String.format(Locale.ROOT,
                    "Internal volume cross-check failed: |det(new lattice)| = %.10e vs %d "
                            + "* V = %.10e; refusing the preview.", Math.abs(newVolume),
                    transform.getDeterminant(), volume));
        }
        int[][] m = transform.getMatrix();
        StringBuilder text = new StringBuilder();
        text.append("Transformation matrix (rows multiply the lattice rows; convention:\n");
        text.append("new lattice row i = sum_j M[i][j] * a_j):\n");
        for (int i = 0; i < 3; i++) {
            text.append(String.format(Locale.ROOT, "  %4d %4d %4d%n", m[i][0], m[i][1],
                    m[i][2]));
        }
        text.append(String.format(Locale.ROOT,
                "Exact integer multiplicity det(M) = %d%n", transform.getDeterminant()));
        text.append(String.format(Locale.ROOT,
                "Atoms: %d -> %d (multiplicity x current count)%n", cell.numAtoms(),
                cell.numAtoms() * transform.getDeterminant()));
        text.append(String.format(Locale.ROOT,
                "Cell volume: %.8f -> %.8f Ang^3%n", volume, expected));
        StringBuilder block = new StringBuilder("CELL_PARAMETERS angstrom\n");
        text.append("\nNew lattice rows (Angstrom):\n");
        for (int i = 0; i < 3; i++) {
            String row = String.format(Locale.ROOT, "  %14.8f %14.8f %14.8f",
                    newLattice[i][0], newLattice[i][1], newLattice[i][2]);
            text.append(row).append('\n');
            block.append(row.trim()).append('\n');
        }
        text.append(String.format(Locale.ROOT,
                "Volume cross-check |det(new)| = %.8f; deviation %.1e%n",
                Math.abs(newVolume), deviation));
        List<String> csv = new ArrayList<>();
        csv.add("field,value");
        csv.add("multiplicity," + transform.getDeterminant());
        csv.add("atoms_before," + cell.numAtoms());
        csv.add("atoms_after," + cell.numAtoms() * transform.getDeterminant());
        csv.add(String.format(Locale.ROOT, "volume_before_ang3,%.10f", volume));
        csv.add(String.format(Locale.ROOT, "volume_after_ang3,%.10f", expected));
        for (int i = 0; i < 3; i++) {
            csv.add(String.format(Locale.ROOT, "new_lattice_row_%d,%.10f %.10f %.10f",
                    i + 1, newLattice[i][0], newLattice[i][1], newLattice[i][2]));
        }
        text.append("\nHonesty boundary: PREVIEW ONLY - no atoms are enumerated, mapped or "
                + "written; the CELL_PARAMETERS block leaves only through the explicit save "
                + "action. An integer matrix with det >= 1 preserves periodicity by "
                + "construction; handedness flips (det < 0) were refused upstream. Atom "
                + "site mapping, image merging and the #37 k-mesh re-derivation (a "
                + "supercell needs a rescaled grid!) are YOUR next steps. Entries are "
                + "bounded to |m| <= " + SupercellMatrixValidator.MAX_ENTRY
                + " and multiplicity to " + SupercellMatrixValidator.MAX_DETERMINANT
                + "; beyond that use the full supercell builder.");
        return new AnalysisReport(label, true, text.toString(), csv, block.toString());
    }

    /**
     * Roadmap #63 seam: hp.x &INPUTHP draft derived from the project's existing
     * Hubbard context (never a fabricated placeholder), with the q-grid
     * convergence and restart prerequisites stated on every draft.
     */
    private static AnalysisReport analyzeHubbardHpDraft(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.HUBBARD_HP_DRAFT.getLabel();
        QEContext context = requireInputAndCell(project, label);
        if (context.failure != null) {
            return context.failure;
        }
        OperationResult<QEHubbardPlanner.HubbardContext> extracted =
                QEHubbardPlanner.extractContext(context.input);
        if (!extracted.isSuccess() || extracted.getValue().isEmpty()) {
            return failure(label, "Derivation refused: " + extracted.getMessage());
        }
        QEHubbardPlanner.HubbardContext hubbard = extracted.getValue().get();
        OperationResult<String> drafted = QEHubbardPlanner.draft(hubbard,
                params.getExxNq1(), params.getExxNq2(), params.getExxNq3());
        if (!drafted.isSuccess() || drafted.getValue().isEmpty()) {
            return failure(label, "Draft refused: " + drafted.getMessage());
        }
        StringBuilder text = new StringBuilder();
        text.append(extracted.getMessage()).append('\n');
        text.append(String.format(Locale.ROOT,
                "prefix='%s'; outdir=%s; q grid %d x %d x %d%n%n",
                hubbard.getPrefix(), hubbard.getOutdir(), params.getExxNq1(),
                params.getExxNq2(), params.getExxNq3()));
        List<String> csv = new ArrayList<>();
        csv.add("field,value");
        csv.add("lda_plus_u," + hubbard.isLdaPlusU());
        csv.add("hubbard_u_entries," + hubbard.getHubbardUEntries());
        csv.add("nq1," + params.getExxNq1());
        csv.add("nq2," + params.getExxNq2());
        csv.add("nq3," + params.getExxNq3());
        text.append("The drafted &INPUTHP namelist below leaves only through the explicit "
                + "save action; the REVIEW comments inside it are part of the draft.\n");
        return new AnalysisReport(label, true, text.toString(), csv,
                drafted.getValue().get());
    }

    /**
     * Roadmap #57: difference of two CUBE grids through the tested compatibility
     * gate - the picked file is the SYSTEM, exactly one other .cube in the
     * project directory is the component; incompatible grids are never
     * subtracted, and the subtraction order is stated explicitly.
     */
    private static AnalysisReport analyzeDensityDifference(Project project, File file) {
        String label = AnalysisKind.DENSITY_DIFFERENCE.getLabel();
        if (file == null) {
            return failure(label, "Select the SYSTEM cube file explicitly; the component "
                    + "cube must be the one other .cube in the project directory.");
        }
        File projectDir = project.getDirectory();
        String sourceCanonical;
        try {
            sourceCanonical = file.getCanonicalPath();
        } catch (IOException ex) {
            return failure(label, "Resolving the selected file failed: " + ex.getMessage());
        }
        File[] others = projectDir == null ? new File[0]
                : projectDir.listFiles(candidate -> {
                    if (!candidate.isFile()
                            || !candidate.getName().toLowerCase(Locale.ROOT)
                                    .endsWith(".cube")) {
                        return false;
                    }
                    try {
                        return !candidate.getCanonicalPath().equals(sourceCanonical);
                    } catch (IOException ex) {
                        return false;
                    }
                });
        if (others == null || others.length == 0) {
            return failure(label, "No component cube found: place exactly one other .cube "
                    + "file in the project directory as the reference to subtract.");
        }
        if (others.length > 1) {
            StringBuilder names = new StringBuilder();
            for (File other : others) {
                names.append(' ').append(other.getName()).append(';');
            }
            return failure(label, "Ambiguous component choice - " + others.length
                    + " candidate cube(s) besides the selected system:" + names
                    + " Leave exactly one reference, or rename the others; no silent "
                    + "choice is made.");
        }
        File componentFile = others[0];
        OperationResult<QEGridDensityDifference.Grid3D> systemRead =
                CubeGridReader.read(file.toPath());
        if (!systemRead.isSuccess() || systemRead.getValue().isEmpty()) {
            return failure(label, "System cube refused: " + systemRead.getMessage());
        }
        OperationResult<QEGridDensityDifference.Grid3D> componentRead =
                CubeGridReader.read(componentFile.toPath());
        if (!componentRead.isSuccess() || componentRead.getValue().isEmpty()) {
            return failure(label, "Component cube refused: " + componentRead.getMessage());
        }
        QEGridDensityDifference.Grid3D system = systemRead.getValue().orElseThrow();
        QEGridDensityDifference.Grid3D component = componentRead.getValue().orElseThrow();
        double tolerance = 1.0e-4;
        QEGridDensityDifference.DiffResult difference =
                QEGridDensityDifference.computeDifference(system, List.of(component),
                        tolerance);
        if (!difference.isCompatible() || difference.getDifferenceGrid() == null) {
            return failure(label, "The grids are NOT compatible - no subtraction is "
                    + "performed (that is the whole point of Roadmap #57). Reason: "
                    + difference.getDiagnosticMessage());
        }
        QEGridDensityDifference.Grid3D grid = difference.getDifferenceGrid();
        double[][][] values = grid.getValues();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sumAbs = 0.0;
        long negative = 0L;
        long count = 0L;
        for (double[][] plane : values) {
            for (double[] row : plane) {
                for (double value : row) {
                    if (!Double.isFinite(value)) {
                        return failure(label, "The difference grid contains a non-finite "
                                + "value; refusing to summarize it.");
                    }
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    sumAbs += Math.abs(value);
                    if (value < 0.0) {
                        negative++;
                    }
                    count++;
                }
            }
        }
        double crossCheck = grid.integrate();
        StringBuilder text = new StringBuilder();
        text.append("Subtraction order (stated exactly once and applied literally):\n");
        text.append("  delta = SYSTEM - COMPONENT\n");
        text.append("  SYSTEM    = ").append(file.getName()).append('\n');
        text.append("  COMPONENT = ").append(componentFile.getName()).append("\n\n");
        text.append(String.format(Locale.ROOT,
                "Grid: %d x %d x %d voxels; cell volume %.6f Ang^3 (lattices matched within "
                        + "%.1e)%n", grid.getNx(), grid.getNy(), grid.getNz(),
                grid.getVolume(), tolerance));
        text.append(String.format(Locale.ROOT,
                "Difference voxels: min %.8g, max %.8g, mean|delta| %.8g; negative fraction "
                        + "%.6f%n", min, max, sumAbs / count,
                count == 0L ? 0.0 : (double) negative / (double) count));
        text.append(String.format(Locale.ROOT,
                "Integrated difference: %.8g (computeDifference) vs %.8g "
                        + "(difference-grid integral; cross-check)%n",
                difference.getIntegratedChargeDifference(), crossCheck));
        List<String> csv = new ArrayList<>();
        csv.add("metric,value");
        csv.add(String.format(Locale.ROOT, "min,%.8g", min));
        csv.add(String.format(Locale.ROOT, "max,%.8g", max));
        csv.add(String.format(Locale.ROOT, "mean_abs,%.8g", sumAbs / count));
        csv.add(String.format(Locale.ROOT, "negative_fraction,%.6f",
                count == 0L ? 0.0 : (double) negative / (double) count));
        csv.add(String.format(Locale.ROOT, "integrated_delta,%.8g",
                difference.getIntegratedChargeDifference()));
        text.append("\nHonesty boundary: NO alignment, resampling or unit conversion was "
                + "applied - the grids only pass because dimensions and lattices already "
                + "agree; had they not, this report would fail closed instead of "
                + "subtracting anyway. The subtraction order above is stated because "
                + "sign flips are the classic #57 error. Voxel units inherit the CUBE "
                + "payload convention (typically bohr^-3 for charge density, Hartree for "
                + "potential); the integral uses the parsed voxel volume. A non-charge "
                + "payload would subtract identically - the interpretation is yours. "
                + "Rendering the difference is the #56 volumetric work.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #43 timing/resource report: the PWSCF total (required) plus the
     * top routines by wall time, with the double-counting caveat stated.
     */
    private static AnalysisReport analyzeTimingResource(File source) {
        String label = AnalysisKind.TIMING_RESOURCE.getLabel();
        OperationResult<QETimingParser.TimingSummary> parsed =
                QETimingParser.parse(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "Timing parse refused: " + parsed.getMessage());
        }
        QETimingParser.TimingSummary summary = parsed.getValue().get();
        List<QETimingParser.RoutineTime> routines = new ArrayList<>(
                summary.getRoutines());
        routines.sort((a, b) -> Double.compare(b.getWallSeconds(), a.getWallSeconds()));
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "pw.x total: CPU %.2f s, WALL %.2f s (CPU/WALL ratio %.3f)%n",
                summary.getTotalCpuSeconds(), summary.getTotalWallSeconds(),
                summary.getTotalWallSeconds() > 0.0
                        ? summary.getTotalCpuSeconds() / summary.getTotalWallSeconds()
                        : Double.NaN));
        double routineWallSum = 0.0;
        for (QETimingParser.RoutineTime routine : routines) {
            routineWallSum += routine.getWallSeconds();
        }
        text.append(String.format(Locale.ROOT,
                "Parsed routine rows: %d (their WALL sum %.2f s does NOT need to equal "
                        + "the total - QE timers nest and overlap)%n%n",
                routines.size(), routineWallSum));
        int shown = Math.min(5, routines.size());
        text.append(String.format(Locale.ROOT, " %-16s %12s %12s %12s %10s%n", "routine",
                "cpu (s)", "wall (s)", "% of total", "calls"));
        for (int i = 0; i < shown; i++) {
            QETimingParser.RoutineTime routine = routines.get(i);
            text.append(String.format(Locale.ROOT, " %-16s %12.2f %12.2f %12.2f %10s%n",
                    routine.getName(), routine.getCpuSeconds(), routine.getWallSeconds(),
                    summary.getTotalWallSeconds() > 0.0
                            ? 100.0 * routine.getWallSeconds()
                                    / summary.getTotalWallSeconds()
                            : Double.NaN,
                    routine.getCalls() < 0L ? "?" : Long.toString(routine.getCalls())));
        }
        if (routines.size() > shown) {
            text.append(String.format(Locale.ROOT,
                    " ... plus %d more routine(s), all in the CSV.%n",
                    routines.size() - shown));
        }
        List<String> csv = new ArrayList<>();
        csv.add("routine,cpu_s,wall_s,calls");
        for (QETimingParser.RoutineTime routine : routines) {
            if (csv.size() - 1 >= 20_000) {
                csv.add("# further routines omitted: CSV capped at 20000 rows");
                break;
            }
            csv.add(String.format(Locale.ROOT, "%s,%.2f,%.2f,%d",
                    csvCell(routine.getName()), routine.getCpuSeconds(),
                    routine.getWallSeconds(), routine.getCalls()));
        }
        text.append("\nHonesty boundary: values are EXACTLY as printed by the QE timer "
                + "(compact h/m/s durations decoded token-wise - nothing estimated). "
                + "Percentages are against the printed PWSCF wall total. QE versions vary "
                + "this format; an unrecognized duration fails closed rather than being "
                + "misread, and a log without the PWSCF total is treated as unfinished. "
                + "Scaling/extrapolation advice (#101) is NOT derived from these "
                + "measurements here.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #132 light seam: one-level, name-based project-dir catalogue via
     * the tested scanner - heuristic markers are named on every row.
     */
    private static AnalysisReport analyzeWorkspaceSearch(Project project) {
        String label = AnalysisKind.WORKSPACE_SEARCH.getLabel();
        OperationResult<WorkspaceLightIndex.WorkspaceScan> scanned =
                WorkspaceLightIndex.scan(project.getDirectory().toPath());
        if (!scanned.isSuccess() || scanned.getValue().isEmpty()) {
            return failure(label, "Scan refused: " + scanned.getMessage());
        }
        WorkspaceLightIndex.WorkspaceScan scan = scanned.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Project directory: ").append(project.getDirectory().getName())
                .append('\n');
        text.append(scanned.getMessage()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Skipped: %d non-artifact file(s) (untouched), %d oversized (parse bound "
                        + "8 MiB), %d parse error(s)%n%n", scan.getOtherFiles(),
                scan.getOversizedFiles(), scan.getParseErrors()));
        text.append(String.format(Locale.ROOT, " %-24s %-6s %-12s %-6s %-8s %s%n",
                "file", "kind", "composition", "atoms", "calc", "status/heuristics"));
        List<String> csv = new ArrayList<>();
        csv.add("file,kind,composition,atoms,calculation,status");
        for (WorkspaceLightIndex.WorkspaceEntry entry : scan.getEntries()) {
            text.append(String.format(Locale.ROOT, " %-24s %-6s %-12s %-6d %-8s %s%n",
                    entry.getFileName(), entry.getKind(), entry.getComposition(),
                    entry.getAtomCount(), entry.getCalculation(), entry.getStatus()));
            csv.add(String.format(Locale.ROOT, "%s,%s,%s,%d,%s,%s",
                    csvCell(entry.getFileName()), entry.getKind(),
                    csvCell(entry.getComposition()), entry.getAtomCount(),
                    csvCell(entry.getCalculation()), csvCell(entry.getStatus())));
        }
        text.append("\nHonesty boundary: ONE directory level of THIS project, matched by "
                + "file name; statuses come from the exact markers \"JOB DONE.\", \"Error "
                + "in routine\" and \"convergence NOT achieved\" and are named as "
                + "heuristics - a log without a marker is \"unknown\", not failed. This is "
                + "not the indexed, tag/provenance-aware multi-project search Roadmap #132 "
                + "targets (that needs the database queue #105); nothing outside the "
                + "project directory was read.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #133: curated workflow templates with prerequisites and pitfalls;
     * blank name lists, unknown names fail closed. Starting points only.
     */
    private static AnalysisReport analyzeTemplateLibrary(AnalysisParameters params) {
        String label = AnalysisKind.TEMPLATE_LIBRARY.getLabel();
        List<QEWorkflowTemplateLibrary.WorkflowTemplate> templates =
                QEWorkflowTemplateLibrary.templates();
        String requested = params.getSeriesKeyword() == null
                ? "" : params.getSeriesKeyword().trim();
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        csv.add("name,workflow,purpose");
        for (QEWorkflowTemplateLibrary.WorkflowTemplate template : templates) {
            csv.add(String.format(Locale.ROOT, "%s,%s,%s", csvCell(template.getName()),
                    csvCell(template.getWorkflow()), csvCell(template.getPurpose())));
        }
        if (requested.isEmpty()) {
            text.append(String.format(Locale.ROOT,
                    "Curated templates (%d) - ask for one by name for its full guidance:%n%n",
                    templates.size()));
            for (QEWorkflowTemplateLibrary.WorkflowTemplate template : templates) {
                text.append(String.format(Locale.ROOT, " %-18s %s%n", template.getName(),
                        template.getWorkflow()));
                text.append("   ").append(template.getPurpose()).append('\n');
            }
        } else {
            Optional<QEWorkflowTemplateLibrary.WorkflowTemplate> found =
                    QEWorkflowTemplateLibrary.find(requested);
            if (found.isEmpty()) {
                return failure(label, "\"" + requested + "\" is not one of the curated "
                        + "templates; nothing is improvised. Curated set: "
                        + templates.stream().map(QEWorkflowTemplateLibrary.WorkflowTemplate
                                ::getName).reduce((a, b) -> a + ", " + b).orElse(""));
            }
            QEWorkflowTemplateLibrary.WorkflowTemplate template = found.get();
            text.append(String.format(Locale.ROOT, "Template: %s  (%s)%n%n",
                    template.getName(), template.getWorkflow()));
            text.append("Purpose: ").append(template.getPurpose()).append("\n\n");
            text.append("PREREQUISITES (do these first):\n")
                    .append(template.getPrerequisites()).append("\n\n");
            text.append("Known pitfalls:\n").append(template.getPitfalls()).append('\n');
        }
        text.append("\nHonesty boundary: templates are REVIEWED STARTING POINTS, not "
                + "convergence-complete defaults - Roadmap #133 explicitly forbids "
                + "universal defaults. ecutwfc/ecutrho/k-mesh/smearing choices must come "
                + "from your convergence workflows (#36/#37/#38) for the specific "
                + "material; a template cannot certify a result.");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #113: fail-closed LAMMPS data review. The atom_style is an
     * explicit USER parameter - the file never records it - and the review
     * invents no elements, bonds, or coefficients.
     */
    private static AnalysisReport analyzeLammpsDataReview(File source,
            AnalysisParameters params) {
        String label = AnalysisKind.LAMMPS_DATA_REVIEW.getLabel();
        String styleName = (params.getSeriesKeyword() == null
                ? "" : params.getSeriesKeyword()).trim().toLowerCase(Locale.ROOT);
        if (styleName.isEmpty()) {
            styleName = "atomic";
        }
        LammpsDataReader.AtomStyle style;
        try {
            style = LammpsDataReader.AtomStyle.valueOf(styleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return failure(label, "Unsupported atom_style '" + styleName
                    + "'. The review covers exactly ATOMIC (5 cols), CHARGE (6), "
                    + "MOLECULAR (6) and FULL (7), each with optional 3 image flags. "
                    + "Other styles are not parsed rather than misread - the data file "
                    + "never records its style, so guessing is not an option.");
        }
        OperationResult<LammpsDataReader.LammpsData> parsed =
                LammpsDataReader.parse(source.toPath(), style);
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "LAMMPS data review refused the file " + source.getName()
                    + ":\n[" + parsed.getCode() + "] " + parsed.getMessage());
        }
        LammpsDataReader.LammpsData data = parsed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName());
        if (!data.getTitle().isEmpty()) {
            text.append("  (title: '").append(data.getTitle()).append("')");
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT,
                "atom_style used: %s (YOUR explicit choice - a LAMMPS data file never "
                        + "records its style)%n", data.getStyle()));
        text.append(String.format(Locale.ROOT,
                "Atoms: %d (matches the declared header count - a mismatch would fail "
                        + "the parse); atom types: %d%n", data.getAtomCount(),
                data.getTypeCount()));
        double[] lengths = data.getBoxLengths();
        text.append(String.format(Locale.ROOT,
                "Box: lx=%.6f ly=%.6f lz=%.6f%s%n", lengths[0], lengths[1], lengths[2],
                data.isTilted() ? " (TILTED triclinic via xy/xz/yz)" : ""));
        Double[] masses = data.getMasses();
        if (masses == null) {
            text.append("Masses: ABSENT (set in the input script - verbatim review of "
                    + "what is here only)\n");
        } else {
            StringBuilder massLine = new StringBuilder();
            for (int i = 0; i < masses.length; i++) {
                if (i > 0) {
                    massLine.append(", ");
                }
                massLine.append(i + 1).append('=').append(String.format(Locale.ROOT,
                        "%.8g", masses[i].doubleValue()));
            }
            text.append(String.format(Locale.ROOT,
                    "Masses (VERBATIM, per type; NO element inference): %s%n", massLine));
        }
        if (data.getStyle() == LammpsDataReader.AtomStyle.CHARGE
                || data.getStyle() == LammpsDataReader.AtomStyle.FULL) {
            double charge = 0.0;
            for (LammpsDataReader.LammpsAtom atom : data.getAtoms()) {
                charge += atom.getCharge();
            }
            text.append(String.format(Locale.ROOT,
                    "Total charge (summed from the rows): %.8g e%n", charge));
        }
        text.append(String.format(Locale.ROOT,
                "Atoms outside the box: %d (reported, NEVER wrapped)%n",
                data.getOutsideBoxCount()));
        text.append(String.format(Locale.ROOT,
                "Sections skipped by name (counted, not interpreted): %s%n",
                data.getSkippedSections().isEmpty() ? "none"
                        : data.getSkippedSections().toString()));
        text.append("\nHonesty boundary: REVIEW only - nothing is applied to the live "
                + "project, no QE input is derived, and no simulation is prepared. Type "
                + "numbers stay numbers (element mapping is YOUR input-script truth, not "
                + "guessable); pair/bond/angle coefficients and velocities are skipped "
                + "verbatim by section name; image flags, when present, are carried but "
                + "not unwrapped. The full LAMMPS plugin (unit-aware model, potential "
                + "hashes, runner, thermo) is the remaining #113 work - thermo log "
                + "review is the separate LAMMPS_THERMO kind.");
        List<String> csv = new ArrayList<>();
        csv.add("id,molecule,type,q,x,y,z");
        int limit = Math.min(data.getAtoms().size(), 20000);
        for (int i = 0; i < limit; i++) {
            LammpsDataReader.LammpsAtom atom = data.getAtoms().get(i);
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%s,%.6f,%.6f,%.6f",
                    atom.getId(), atom.getMolecule(), atom.getType(),
                    Double.isNaN(atom.getCharge()) ? ""
                            : String.format(Locale.ROOT, "%.6g", atom.getCharge()),
                    atom.getX(), atom.getY(), atom.getZ()));
        }
        if (limit < data.getAtoms().size()) {
            csv.add("# truncated at 20000 atom rows (cap)");
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #78: fail-closed PDB review. Nothing is applied to the project;
     * the element column is never guessed, and unresolved disorder is counted.
     */
    private static AnalysisReport analyzePdbReview(File source) {
        String label = AnalysisKind.PDB_REVIEW.getLabel();
        OperationResult<PdbStructureReader.PdbStructure> parsed =
                PdbStructureReader.parse(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "PDB review refused the file " + source.getName()
                    + ":\n[" + parsed.getCode() + "] " + parsed.getMessage());
        }
        PdbStructureReader.PdbStructure structure = parsed.getValue().get();
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY};
        double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY};
        int hetatm = 0;
        for (PdbStructureReader.PdbAtom atom : structure.getAtoms()) {
            double[] pos = {atom.getX(), atom.getY(), atom.getZ()};
            for (int axis = 0; axis < 3; axis++) {
                lo[axis] = Math.min(lo[axis], pos[axis]);
                hi[axis] = Math.max(hi[axis], pos[axis]);
            }
            if ("HETATM".equals(atom.getRecord())) {
                hetatm += 1;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Records: %d atoms (%d ATOM + %d HETATM); %d non-coordinate line(s) "
                        + "counted and ignored (HEADER/REMARK/END/CONECT...).%n",
                structure.getAtoms().size(), structure.getAtoms().size() - hetatm, hetatm,
                structure.getIgnoredLines()));
        StringBuilder composition = new StringBuilder();
        for (Map.Entry<String, Integer> entry : structure.elementCounts().entrySet()) {
            if (composition.length() > 0) {
                composition.append(',');
            }
            composition.append(entry.getKey()).append('=').append(entry.getValue());
        }
        text.append(String.format(Locale.ROOT,
                "Composition (from the element column ONLY): %s%n",
                composition.length() == 0 ? "(no element columns present)"
                        : composition.toString()));
        text.append(String.format(Locale.ROOT,
                "Atoms WITHOUT an element column: %d (left anonymous - guessing CA vs Ca "
                        + "is the classic PDB failure)%n", structure.getMissingElementCount()));
        text.append(String.format(Locale.ROOT,
                "Partial-occupancy atoms (0 < occ < 1): %d (unresolved disorder - reported, "
                        + "never resolved here)%n", structure.getPartialOccupancyCount()));
        if (structure.hasCryst()) {
            double[] cryst = structure.getCryst();
            text.append(String.format(Locale.ROOT,
                    "CRYST1: a=%.4f b=%.4f c=%.4f alpha=%.3f beta=%.3f gamma=%.3f deg; "
                            + "cell volume %.4f Ang^3%n",
                    cryst[0], cryst[1], cryst[2], cryst[3], cryst[4], cryst[5],
                    structure.cellVolume()));
        } else {
            text.append("CRYST1: absent (a non-crystallographic file - no periodic box "
                    + "is defined).\n");
        }
        text.append(String.format(Locale.ROOT,
                "Bounding box (Angstrom):\n  min %12.4f %12.4f %12.4f%n  max %12.4f "
                        + "%12.4f %12.4f%n",
                lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
        text.append("\nHonesty boundary: REVIEW only - the structure was NOT applied to "
                + "the live project and nothing was written. PDB coordinates are Angstrom "
                + "as printed; the CRYST1 record is a crystallographic box (MD packages "
                + "routinely store unrelated or placeholder boxes - it is shown, not "
                + "trusted as a DFT cell). CONECT bond records are counted and ignored "
                + "(no bond perception); alternate locations and partial occupancies are "
                + "reported, never resolved; the 11/12-token grammar is deliberately "
                + "stricter than permissive PDB parsers - a record outside it fails the "
                + "whole file instead of being misread.");
        List<String> csv = new ArrayList<>();
        csv.add("index,record,name,residue,element,x_ang,y_ang,z_ang,occupancy");
        int limit = Math.min(structure.getAtoms().size(), 20000);
        for (int i = 0; i < limit; i++) {
            PdbStructureReader.PdbAtom atom = structure.getAtoms().get(i);
            csv.add(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%.4f,%.4f,%.4f,%.2f",
                    i + 1, atom.getRecord(), csvCell(atom.getName()),
                    csvCell(atom.getResidue()), csvCell(atom.getElement()),
                    atom.getX(), atom.getY(), atom.getZ(), atom.getOccupancy()));
        }
        if (limit < structure.getAtoms().size()) {
            csv.add("# truncated at 20000 atom rows (cap)");
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #69: Wannier90 .win draft. The uniform mesh and nbnd are echoed
     * verbatim; every physics choice stays REQUIRED-EDIT; Gamma/explicit-list
     * inputs are refused because they cannot feed pw2wannier90.x.
     */
    private static AnalysisReport analyzeW90WinDraft(Project project) {
        String label = AnalysisKind.W90_WIN_DRAFT.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        OperationResult<Wannier90WinPlanner.WinDraft> planned =
                Wannier90WinPlanner.plan(input);
        if (!planned.isSuccess() || planned.getValue().isEmpty()) {
            return failure(label, "Wannier90 draft refused: [" + planned.getCode() + "] "
                    + planned.getMessage());
        }
        Wannier90WinPlanner.WinDraft draft = planned.getValue().get();
        int[] grid = draft.getGrid();
        int[] offset = draft.getOffset();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Mesh echoed verbatim: automatic %d x %d x %d, offset %d %d %d%n",
                grid[0], grid[1], grid[2], offset[0], offset[1], offset[2]));
        text.append(String.format(Locale.ROOT,
                "nbnd: %s%n", draft.getNbnd() == null
                        ? "not set in the live input - REQUIRED-EDIT in the draft"
                        : "echoed as num_bands = " + draft.getNbnd()));
        text.append(String.format(Locale.ROOT,
                "kpoints block: %s%n", draft.isKpointsGenerated()
                        ? "GENERATED exactly from the grid (Monkhorst-Pack; precision "
                                + "stated in the draft)"
                        : "NOT generated (grid count beyond the " + 4096
                                + " bound) - REQUIRED-EDIT"));
        text.append(String.format(Locale.ROOT,
                "REQUIRED-EDIT placeholders kept: %d (num_wann, projections, "
                        + "disentanglement windows, num_bands when unset)%n",
                draft.countRequiredEdits()));
        text.append("\nPrerequisites stated by #69: run the nscf on THIS SAME mesh with "
                + "nosym=.true./noinv (pw2wannier90.x rejects symmetry-reduced sets), "
                + "choose projections from valence character, and - for entangled bands - "
                + "set the disentanglement windows from a completed band-structure review "
                + "(BANDS_DATA kind), never from defaults. The draft is written ONLY via "
                + "the explicit save action. An honest .win schema round-trip, projection "
                + "templates per chemistry, disentanglement auto-derivation from finished "
                + "bands, and Wannier-vs-DFT band RMS comparison remain the remaining #69 "
                + "work; the WANNIER90_SPREAD kind already audits the spread convergence "
                + "after a run.");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "mp_grid,%dx%dx%d,verbatim", grid[0], grid[1],
                grid[2]));
        csv.add(String.format(Locale.ROOT, "offset,%d%d%d,verbatim", offset[0], offset[1],
                offset[2]));
        csv.add(String.format(Locale.ROOT, "num_bands,%s,echo-or-required",
                draft.getNbnd() == null ? "REQUIRED-EDIT" : draft.getNbnd().toString()));
        csv.add(String.format(Locale.ROOT, "kpoints_generated,%s,draft-status",
                draft.isKpointsGenerated()));
        csv.add(String.format(Locale.ROOT, "required_edits,%d,draft-status",
                draft.countRequiredEdits()));
        return new AnalysisReport(label, true, text.toString(), csv, draft.getDraft());
    }

    /**
     * Roadmap #86: grain-boundary CSL rotation preview. Pure Ranganathan
     * mathematics - no cell is read and nothing is constructed; the report
     * keeps the cosine exact and states the simple-cubic law's scope.
     */
    private static AnalysisReport analyzeGbCslPreview(AnalysisParameters params) {
        String label = AnalysisKind.GB_CSL_PREVIEW.getLabel();
        int[] axis = params.getCslAxis();
        OperationResult<CslSigmaMath.CslRotation> computed = CslSigmaMath.compute(
                axis[0], axis[1], axis[2], params.getMoireM(), params.getMoireN());
        if (!computed.isSuccess() || computed.getValue().isEmpty()) {
            return failure(label, "CSL preview refused: [" + computed.getCode() + "] "
                    + computed.getMessage());
        }
        CslSigmaMath.CslRotation rotation = computed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Rotation axis [%d %d %d]", rotation.getU(),
                rotation.getV(), rotation.getW()));
        if (rotation.getAxisCommonFactor() > 1) {
            text.append(String.format(Locale.ROOT,
                    "  (normalized: a common factor %d was removed from the supplied axis)",
                    rotation.getAxisCommonFactor()));
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT, "Commensurate pair (m, n) = (%d, %d)",
                rotation.getM(), rotation.getN()));
        if (rotation.getPairCommonFactor() > 1) {
            text.append(String.format(Locale.ROOT,
                    "  (normalized: a common factor %d was removed; Sigma is unchanged)",
                    rotation.getPairCommonFactor()));
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT,
                "Axis norm N = u^2+v^2+w^2 = %d;  tan(theta/2) = (n/m)*sqrt(N)"
                        + " = (%d/%d)*sqrt(%d)%n",
                rotation.getAxisNormSquared(), rotation.getN(), rotation.getM(),
                rotation.getAxisNormSquared()));
        text.append(String.format(Locale.ROOT,
                "Exact cosine fraction: cos(theta) = %d/%d%n",
                rotation.getCosNumerator(), rotation.getCosDenominator()));
        text.append(String.format(Locale.ROOT, "Rotation angle: %.6f deg%n",
                rotation.getAngleDeg()));
        text.append(String.format(Locale.ROOT,
                "CSL Sigma = %d  (odd part of m^2 + N n^2 = %d; divided by 2 %s)%n",
                rotation.getSigma(), rotation.getSigmaRaw(),
                rotation.getHalvings() == 0 ? "0 times (already odd)"
                        : rotation.getHalvings() + (rotation.getHalvings() == 1
                                ? " time" : " times")));
        if (rotation.isLatticeSymmetry()) {
            text.append("Sigma = 1: this rotation is a LATTICE SYMMETRY operation of the "
                    + "cubic lattice - both sides coincide identically and NO distinct "
                    + "boundary exists; stated, not hidden.\n");
        }
        text.append(String.format(Locale.ROOT,
                "sin(theta) = 2 m n sqrt(N)/(m^2+N n^2): reported numerically"
                        + " (sqrt(%d) is %s), the cosine above is exact.%n",
                rotation.getAxisNormSquared(),
                isPerfectSquare(rotation.getAxisNormSquared())
                        ? "an integer, so the sine is rational here too"
                        : "irrational for this axis"));
        text.append("\nHonesty boundary: Ranganathan's law generates coincidence rotations "
                + "of the SIMPLE CUBIC lattice rigorously; for face-/body-centred Bravais "
                + "materials additional systematic extinctions can lower the physical "
                + "Sigma - cross-check against the referenced CSL tables before building. "
                + "This preview is geometry ONLY: a real grain boundary additionally needs "
                + "the boundary plane (hkl), the rigid-body translation state, atom "
                + "construction and relaxation - none of that happened here; that builder "
                + "work is the remaining #86 scope.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "axis,[%d %d %d],normalized", rotation.getU(),
                rotation.getV(), rotation.getW()));
        csv.add(String.format(Locale.ROOT, "axis_common_factor,%d,provenance",
                rotation.getAxisCommonFactor()));
        csv.add(String.format(Locale.ROOT, "pair_m,%d,normalized", rotation.getM()));
        csv.add(String.format(Locale.ROOT, "pair_n,%d,normalized", rotation.getN()));
        csv.add(String.format(Locale.ROOT, "pair_common_factor,%d,provenance",
                rotation.getPairCommonFactor()));
        csv.add(String.format(Locale.ROOT, "axis_norm_squared,%d,exact",
                rotation.getAxisNormSquared()));
        csv.add(String.format(Locale.ROOT, "sigma_raw,%d,exact", rotation.getSigmaRaw()));
        csv.add(String.format(Locale.ROOT, "sigma,%d,exact", rotation.getSigma()));
        csv.add(String.format(Locale.ROOT, "halvings,%d,exact", rotation.getHalvings()));
        csv.add(String.format(Locale.ROOT, "cos_theta,%d/%d,exact-reduced",
                rotation.getCosNumerator(), rotation.getCosDenominator()));
        csv.add(String.format(Locale.ROOT, "angle_deg,%.6f,computed-from-exact",
                rotation.getAngleDeg()));
        csv.add(String.format(Locale.ROOT, "lattice_symmetry,%s,sigma==1",
                rotation.isLatticeSymmetry()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    private static boolean isPerfectSquare(long value) {
        long root = (long) Math.floor(Math.sqrt((double) value));
        while (root * root > value) {
            root -= 1;
        }
        while ((root + 1) * (root + 1) <= value) {
            root += 1;
        }
        return root * root == value;
    }

    /**
     * Roadmap #22: version-aware keyword audit. The curated 7.2-7.5 snapshot
     * stays as the baseline (absence there is NOT-IN-CURATED, never judged
     * invalid); batch 150 appends the full machine-mined schema audit
     * (QE 7.2-7.6) that completes the #22 completeness work.
     */
    private static AnalysisReport analyzeQeVersionCheck(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.QE_VERSION_CHECK.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        if (input == null) {
            return failure(label, "[VERSION_INPUT] The project has no resolvable current "
                    + "input to audit.");
        }
        String version = params.getSeriesKeyword() == null
                ? "" : params.getSeriesKeyword().trim();
        // Batch 150 deepened: the machine-mined schema window is 7.2-7.6; the
        // curated snapshot audit below stays frozen as the batch-#22 baseline.
        if (!version.isEmpty() && QENamelistSchema.indexOfVersion(version) < 0) {
            return failure(label, "[VERSION_UNSUPPORTED] \"" + version + "\" is outside "
                    + "the mined schema window " + QENamelistSchema.VERSIONS
                    + "; other minor versions are not judged.");
        }
        String schemaVersion = version.isEmpty()
                ? QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1) : version;
        StringBuilder text = new StringBuilder();
        text.append("Requested version filter: ").append(version.isEmpty()
                ? "(none - the curated snapshot audits the uniform 7.2-7.5 window; "
                        + "the mined schema audits " + schemaVersion + ", its newest)" : version)
                .append('\n');
        text.append("Scope: ").append(QEVersionRuleCatalog.WINDOW_NOTE)
                .append("; docs: ").append(QEKeywordHelp.INPUT_PW_URL).append("\n\n");
        int ok = 0;
        int warnings = 0;
        int removed = 0;
        int notCurated = 0;
        int total = 0;
        List<String> csv = new ArrayList<>();
        csv.add("namelist,keyword,value,verdict,note");
        for (String namelistName : new String[] {QEInput.NAMELIST_CONTROL,
                QEInput.NAMELIST_SYSTEM}) {
            QENamelist namelist = input.getNamelist(namelistName);
            if (namelist == null || namelist.numValues() == 0) {
                continue;
            }
            QEValue[] values = namelist.listQEValues();
            for (QEValue value : values) {
                QEVersionRuleCatalog.AuditEntry entry =
                        QEVersionRuleCatalog.audit(namelistName, value);
                total += 1;
                switch (entry.getVerdict()) {
                    case OK:
                        ok += 1;
                        break;
                    case VALUE_WARNING:
                        warnings += 1;
                        break;
                    case REMOVED_KEYWORD:
                        removed += 1;
                        break;
                    default:
                        notCurated += 1;
                        break;
                }
                text.append(String.format(Locale.ROOT, "  %-20s %-16s = %-14s %s%n",
                        entry.getNamelist() + ".", entry.getKeyword(),
                        entry.getValueEcho(), entry.getVerdict()));
                text.append(String.format(Locale.ROOT, "      -> %s%n", entry.getNote()));
                csv.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s",
                        csvCell(entry.getNamelist()), csvCell(entry.getKeyword()),
                        csvCell(entry.getValueEcho()), entry.getVerdict(),
                        csvCell(entry.getNote())));
            }
        }
        if (total == 0) {
            return failure(label, "[VERSION_EMPTY] The live input's &CONTROL and &SYSTEM "
                    + "namelists hold no keywords to audit.");
        }
        text.append(String.format(Locale.ROOT,
                "%nAudited %d keywords: %d OK, %d value-warning, %d REMOVED, "
                        + "%d not-in-curated.%n",
                total, ok, warnings, removed, notCurated));
        // Batch 150: full machine-mined schema audit (the completed #22 work) -
        // every namelist of the input, typed against QE <schemaVersion>.
        text.append(String.format(Locale.ROOT,
                "%n== Mined schema audit (QE %s grammar, window %s) ==%n",
                schemaVersion, QENamelistSchema.VERSIONS));
        List<ValidationIssue> schemaIssues =
                new QESchemaValidator().validate(input, schemaVersion);
        csv.add("");
        csv.add("mined-schema-audit,severity,code,message");
        int schemaErrors = 0;
        int schemaWarnings = 0;
        for (ValidationIssue issue : schemaIssues) {
            if (issue.getSeverity() == ValidationSeverity.ERROR) {
                schemaErrors += 1;
            } else {
                schemaWarnings += 1;
            }
            text.append("  ").append(issue.getSeverity())
                    .append(" [").append(issue.getCode()).append("] ")
                    .append(issue.getMessage()).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("      docs: ").append(issue.getDocumentationUrl()).append('\n');
            }
            csv.add(String.format(Locale.ROOT, "mined-schema-audit,%s,%s,%s",
                    issue.getSeverity(), csvCell(issue.getCode()), csvCell(issue.getMessage())));
        }
        if (schemaIssues.isEmpty()) {
            text.append(String.format(Locale.ROOT,
                    "  No schema findings: every namelist keyword of this input is part of the "
                            + "QE %s mined grammar, placed in its own namelist, typed like the "
                            + "Fortran reader expects, and inside every mined value set its "
                            + "keyword carries.%n", schemaVersion));
        }
        text.append(String.format(Locale.ROOT,
                "%nMined schema audit: %d ERROR (the binary aborts at namelist read or "
                        + "validation) and %d WARNING (reported-and-never-judged, or silently "
                        + "remapped - check intent).%n",
                schemaErrors, schemaWarnings));
        text.append("\nHonesty boundary: above, the curated slice ("
                + QEVersionRuleCatalog.listRules().size()
                + " keywords) stays as the batch-#22 baseline; NOT-IN-CURATED means outside "
                + "the snapshot, NOT invalid. The mined schema audit is the completed #22 "
                + "work: machine-mined from QE tags qe-7.2..qe-7.6 (459 pw.x + 80 ph.x + 33 "
                + "hp.x namelist keywords, 572 total; cards, runtime-conditional rules, and anything "
                + "the mined sources omit stay out of scope, said in the generated data "
                + "header).\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #102: exact uniform-mesh pool-divisor audit. The mesh N is read
     * verbatim from the live input (symmetry NEITHER applied nor guessed);
     * the divisor window is rigorous, recommendations stay inside it, and the
     * IBZ caveat is stated rather than hidden. -nb/-nt/-nd are deliberately
     * NOT computed - they belong to measured-timing advice.
     */
    private static AnalysisReport analyzeMpiPoolsAdvisor(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.MPI_POOLS_ADVISOR.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        if (input == null) {
            return failure(label, "[POOL_INPUT] The project has no resolvable current "
                    + "input to audit.");
        }
        QEKPoints card = input.getCard(QEKPoints.class);
        if (card == null) {
            return failure(label, "[POOL_MESH] The live input has no K_POINTS card - "
                    + "there is no mesh to audit pool divisibility against.");
        }
        if (card.isGamma()) {
            return failure(label, "[POOL_MESH] Gamma-only is a single k point: any -nk "
                    + "beyond 1 is nonsensical and this audit's mesh arithmetic does not "
                    + "apply; run a uniform mesh first if k-parallel scaling matters.");
        }
        if (!card.isAutomatic()) {
            return failure(label, "[POOL_MESH] The live input uses an explicit/path "
                    + "K_POINTS list, not a uniform automatic mesh - pool arithmetic here "
                    + "is exact-mesh only; count the list explicitly if you must.");
        }
        OperationResult<PoolDivisorMath.PoolAudit> audited = PoolDivisorMath.audit(
                params.getTotalRanks(), card.getKGrid(), params.getCurrentPools());
        if (!audited.isSuccess() || audited.getValue().isEmpty()) {
            return failure(label, "Pool audit refused: [" + audited.getCode() + "] "
                    + audited.getMessage());
        }
        PoolDivisorMath.PoolAudit audit = audited.getValue().get();
        int[] grid = card.getKGrid();
        int[] offset = card.getKOffset();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Exact uniform mesh: %d x %d x %d = %d k points (offset %d %d %d) - the "
                        + "FULL mesh; symmetry is NEITHER applied nor guessed%n",
                grid[0], grid[1], grid[2], audit.getMeshPoints(), offset[0], offset[1],
                offset[2]));
        text.append(String.format(Locale.ROOT, "Total MPI ranks R = %d%n",
                audit.getTotalRanks()));
        text.append(String.format(Locale.ROOT, "Pool divisors of N (the rigorous "
                + "window): %s%n", joinLongs(audit.getMeshDivisors())));
        text.append(String.format(Locale.ROOT,
                "Admissible pools (divide N AND R, p <= R): %s%n",
                joinLongs(audit.getAdmissiblePools())));
        if (audit.getRecommended() != null) {
            text.append(String.format(Locale.ROOT,
                    "Recommendation: -nk %d  (%d pools of %d rank(s) each)%n",
                    audit.getRecommended(), audit.getRecommended(),
                    audit.getRanksPerPool()));
            if (audit.getRecommended().longValue() == 1L
                    && audit.getTotalRanks() > 1L) {
                text.append("Only 1 pool is admissible for this mesh/rank pair - the "
                        + "remaining ranks stand idle in k-parallel; plan -nb/-nt via "
                        + "measured-timing advice or choose a mesh/rank pair with common "
                        + "divisors (stated, not hidden).\n");
            }
        }
        if (audit.hasCurrentPools()) {
            text.append(String.format(Locale.ROOT, "Audit of the supplied -nk %d: %s%n",
                    audit.getCurrentPools(),
                    audit.isCurrentPoolsValid()
                            ? "VERIFIED VALID - divides the full mesh AND the rank count"
                            : "INVALID for this exact mesh/rank pair - it fails the "
                                    + "divisor rules above; change it"));
        }
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "mesh,%dx%dx%d,verbatim-automatic", grid[0],
                grid[1], grid[2]));
        csv.add(String.format(Locale.ROOT, "mesh_points,%d,exact", audit.getMeshPoints()));
        csv.add(String.format(Locale.ROOT, "total_ranks,%d,param", audit.getTotalRanks()));
        csv.add(String.format(Locale.ROOT, "divisors,%s,of-N",
                joinLongs(audit.getMeshDivisors(), ";")));
        csv.add(String.format(Locale.ROOT, "admissible,%s,divide-N-and-R",
                joinLongs(audit.getAdmissiblePools(), ";")));
        csv.add(String.format(Locale.ROOT, "recommended,%s,largest-admissible",
                audit.getRecommended() == null ? "NONE" : audit.getRecommended()));
        csv.add(String.format(Locale.ROOT, "ranks_per_pool,%s,exact",
                audit.getRanksPerPool() == null ? "NONE" : audit.getRanksPerPool()));
        if (audit.hasCurrentPools()) {
            csv.add(String.format(Locale.ROOT, "current_pools,%d,param",
                    audit.getCurrentPools()));
            csv.add(String.format(Locale.ROOT, "current_valid,%s,audit",
                    audit.isCurrentPoolsValid()));
        }
        text.append(String.format(Locale.ROOT,
                "%n-nb/-nt/-nd are deliberately NOT computed here: their optima depend on "
                        + "measured SCALAPACK/FFT timings of YOUR machine; the "
                        + "RESOURCE_ESTIMATE kind offers a full-flag heuristic estimate "
                        + "on a crude N/2 IBZ GUESS - where that estimate and this exact "
                        + "audit disagree, this audit's arithmetic is the one tied to the "
                        + "real input mesh (both are advisors: validate at runtime).%n"));
        text.append("Honesty boundary: pw.x distributes the IRREDUCIBLE k points k <= N, "
                + "and symmetry can reduce k arbitrarily - a p dividing N can still be "
                + "rejected at runtime when k % p != 0 (pw.x refuses at startup with its "
                + "own pool diagnostic). Always dry-run a short job before consuming the "
                + "allocation; nothing measured here, the arithmetic is exact.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    private static String joinLongs(List<Long> values) {
        return joinLongs(values, ", ");
    }

    private static String joinLongs(List<Long> values, String separator) {
        StringBuilder joined = new StringBuilder();
        for (Long value : values) {
            if (joined.length() > 0) {
                joined.append(separator);
            }
            joined.append(value);
        }
        return joined.toString();
    }

    /**
     * Roadmap #26: curated unit conversion. Constants carry their provenance;
     * unknown tokens and incompatible domains fail closed.
     */
    private static AnalysisReport analyzeUnitConvert(AnalysisParameters params) {
        String label = AnalysisKind.UNIT_CONVERT.getLabel();
        OperationResult<QEUnits.Conversion> converted = QEUnits.convert(
                params.getQuantityValue(), params.getUnitFrom(), params.getUnitTo());
        if (!converted.isSuccess() || converted.getValue().isEmpty()) {
            return failure(label, "Unit conversion refused: [" + converted.getCode() + "] "
                    + converted.getMessage());
        }
        QEUnits.Conversion result = converted.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "%s %s = %s %s%n",
                Double.toString(result.getValueFrom()), result.getFromUnit(),
                Double.toString(result.getValueTo()), result.getToUnit()));
        QEUnits.Unit from = QEUnits.findUnit(params.getUnitFrom()).orElseThrow();
        QEUnits.Unit to = QEUnits.findUnit(params.getUnitTo()).orElseThrow();
        text.append(String.format(Locale.ROOT,
                "Factors pinned (canonical base per domain: eV / Angstrom / GPa): %s = %s, "
                        + "%s = %s%n",
                from.getCanonicalName(), Double.toString(from.getFactorToCanonical()),
                to.getCanonicalName(), Double.toString(to.getFactorToCanonical())));
        if (result.isSpectroscopicBridge()) {
            text.append("Spectroscopic bridge crossed: E = h*c*(wavenumber) and E = h*f "
                    + "via the SI-EXACT constants (h, c, e are exact SI definitions since "
                    + "2019) - stated, not assumed silently.\n");
        }
        text.append(String.format(Locale.ROOT,
                "%nConstants provenance: SI-exact relations (2019 SI: e, N_A, h, c) for "
                        + "eV<->kJ/mol and the spectroscopic bridge; CODATA-2018 measured "
                        + "values for Ha (%s eV), Ry (pinned half-scale %s eV) and bohr "
                        + "(%s Ang, same digits as the cube reader) - reported at these "
                        + "digits, never as exact.%n",
                Double.toString(QEUnits.EV_PER_HA), Double.toString(QEUnits.EV_PER_RY),
                Double.toString(QEUnits.ANG_PER_BOHR)));
        text.append("Round-trip rule: A->B->A must reproduce A at machine precision (the "
                + "backend test pins this for six pairs).\n");
        text.append("Curated registry (unknown tokens fail UNIT_UNKNOWN; incompatible "
                + "domains fail UNIT_DOMAIN): ").append(QEUnits.listUnitTokens())
                .append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "value_from,%s,verbatim",
                Double.toString(result.getValueFrom())));
        csv.add(String.format(Locale.ROOT, "unit_from,%s,canonical",
                from.getCanonicalName()));
        csv.add(String.format(Locale.ROOT, "value_to,%s,converted",
                Double.toString(result.getValueTo())));
        csv.add(String.format(Locale.ROOT, "unit_to,%s,canonical", to.getCanonicalName()));
        csv.add(String.format(Locale.ROOT, "spectroscopic_bridge,%s,SI-exact-hc",
                result.isSpectroscopicBridge()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #31: curated error-KB scan of a QE log. Matches print the
     * verbatim line; an empty result is NOT a health certificate.
     */
    private static AnalysisReport analyzeLogErrorDiagnosis(File source) {
        String label = AnalysisKind.LOG_ERROR_DIAGNOSIS.getLabel();
        OperationResult<QEErrorSignatureCatalog.ScanResult> scanned =
                QEErrorSignatureCatalog.scanPath(source.toPath());
        if (!scanned.isSuccess() || scanned.getValue().isEmpty()) {
            return failure(label, "Log scan refused: [" + scanned.getCode() + "] "
                    + scanned.getMessage());
        }
        QEErrorSignatureCatalog.ScanResult result = scanned.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Scanned %d lines; curated KB holds %d signatures (QE 7.x window, "
                        + "deterministic substring matching, quotes kept verbatim)%n",
                result.getLineCount(), QEErrorSignatureCatalog.listSignatures().size()));
        List<String> csv = new ArrayList<>();
        csv.add("signature,severity,line,cause");
        if (result.isEmpty()) {
            text.append("\nNo curated signature matched. This is NOT proof the run is "
                    + "healthy: the KB covers only its curated slice - read the end of "
                    + "the log and the CRASH file before rerun.\n");
        } else {
            text.append(String.format(Locale.ROOT, "%nMatched signatures (%d distinct):%n",
                    result.distinctSignatures()));
            for (String id : result.matchedSignatureIds()) {
                long total = result.totalMatches(id);
                long kept = Math.min(total, QEErrorSignatureCatalog.MAX_QUOTES_PER_SIGNATURE);
                text.append(String.format(Locale.ROOT,
                        "  %s: %d match(es), %d verbatim quote(s) kept%s%n", id, total,
                        kept, total > kept
                                ? " (" + (total - kept) + " repeats suppressed - counted, "
                                        + "not hidden)"
                                : ""));
            }
            for (QEErrorSignatureCatalog.Hit hit : result.getHits()) {
                QEErrorSignatureCatalog.Signature signature = hit.getSignature();
                text.append(String.format(Locale.ROOT, "%n[%s] %s - line %d%n"
                        + "  verbatim: \"%s\"%n  cause: %s%n  checks: %s%n  docs: %s%n",
                        signature.getId(), signature.getSeverity(), hit.getLineNumber(),
                        hit.getQuotedLine(), signature.getCause(), signature.getChecks(),
                        signature.getDocsUrl()));
                csv.add(String.format(Locale.ROOT, "%s,%s,%d,%s", signature.getId(),
                        signature.getSeverity(), hit.getLineNumber(),
                        csvCell(signature.getCause())));
            }
        }
        text.append("\nHonesty boundary: recommendations are deterministic heuristics "
                + "over a small curated corpus (Roadmap #31 first slice) - not an "
                + "exhaustive error oracle, and never a substitute for reading the log; "
                + "the corpus grows only from real, cited failure reports.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #65: xspectra.x XANES draft from the live save context. The
     * draft is deliberately non-runnable; only the prefix/outdir save context
     * is echoed verbatim and the core-hole prerequisite is stated, never
     * claimed verified.
     */
    private static AnalysisReport analyzeXspectraInputDraft(Project project) {
        String label = AnalysisKind.XSPECTRA_INPUT_DRAFT.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        OperationResult<XspectraInputPlanner.XspectraContext> extracted =
                XspectraInputPlanner.extractContext(input);
        if (!extracted.isSuccess() || extracted.getValue().isEmpty()) {
            return failure(label, "xspectra.x draft refused: [" + extracted.getCode()
                    + "] " + extracted.getMessage());
        }
        XspectraInputPlanner.XspectraContext context = extracted.getValue().get();
        String draft = XspectraInputPlanner.draft(context);
        int requiredEdits = XspectraInputPlanner.countRequiredEdits(draft);
        StringBuilder text = new StringBuilder();
        text.append("Save context echoed from the live input:\n");
        text.append(String.format(Locale.ROOT, "  prefix = '%s'; outdir = '%s'%n",
                context.getPrefix(), context.getOutdir()));
        text.append(String.format(Locale.ROOT,
                "%nThe draft is DELIBERATELY NOT RUNNABLE: %d REQUIRED-EDIT placeholders "
                        + "stand exactly where the experimental choices belong "
                        + "(dipole-vs-quadrupole calculation, absorbing edge, energy "
                        + "window, core-hole pseudo filecore, occupation smoothing).%n",
                requiredEdits));
        text.append("\nPrerequisites named by #65 before any XANES run: a CONVERGED SCF "
                + "of the core-excited system (often a supercell) with a GIPAW "
                + "core-hole pseudopotential for the absorbing atom - stated, not "
                + "checkable offline; xnepoint=200 is a typical initial grid only "
                + "(spectrum convergence is on you); consult the version-matched "
                + "INPUT_XSPECTRA documentation. The draft carries the REQUIRED-EDIT "
                + "guard header and is written ONLY via the explicit save action. "
                + "The spectrum-parsing side of #65 (sigma/omega columns) and the "
                + "core-hole supercell helper remain open work.");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "prefix,%s,verbatim",
                csvCell(context.getPrefix())));
        csv.add(String.format(Locale.ROOT, "outdir,%s,verbatim",
                csvCell(context.getOutdir())));
        csv.add(String.format(Locale.ROOT, "required_edit_placeholders,%d,draft-guard",
                requiredEdits));
        csv.add(String.format(Locale.ROOT, "calculation_options,%s,curated",
                csvCell(XspectraInputPlanner.CALCULATION_OPTIONS)));
        return new AnalysisReport(label, true, text.toString(), csv, draft);
    }

    /**
     * Roadmap #66: gipaw.x NMR/EFG draft from the live save context. Only the
     * documented defaults are pre-filled; q_gipaw convergence and the
     * sigma-vs-shift reference are stated guards, never fabricated.
     */
    private static AnalysisReport analyzeGipawInputDraft(Project project) {
        String label = AnalysisKind.GIPAW_INPUT_DRAFT.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        OperationResult<GipawInputPlanner.GipawContext> extracted =
                GipawInputPlanner.extractContext(input);
        if (!extracted.isSuccess() || extracted.getValue().isEmpty()) {
            return failure(label, "gipaw.x draft refused: [" + extracted.getCode()
                    + "] " + extracted.getMessage());
        }
        GipawInputPlanner.GipawContext context = extracted.getValue().get();
        String draft = GipawInputPlanner.draft(context);
        int requiredEdits = GipawInputPlanner.countRequiredEdits(draft);
        StringBuilder text = new StringBuilder();
        text.append("Save context echoed from the live input:\n");
        text.append(String.format(Locale.ROOT,
                "  prefix = '%s'; tmp_dir (outdir) = '%s'%n", context.getPrefix(),
                context.getOutdir()));
        text.append(String.format(Locale.ROOT,
                "%nThe draft is MINIMAL: job='gipaw' and verbosity defaults pre-filled, "
                        + "%d REQUIRED-EDIT marker(s) guarding the physics (q_gipaw "
                        + "Green-function smearing convergence, and the sigma_ref "
                        + "reference needed to turn a SHIELDING into a CHEMICAL "
                        + "SHIFT).%n",
                requiredEdits));
        text.append("\nPrerequisites named by #66: GIPAW-capable pseudopotentials for "
                + "EVERY species (reconstructed projectors in the UPF) - stated, not "
                + "checkable offline; gipaw.x itself rejects incapable pseudos at "
                + "runtime; the matching SCF must exist in the echoed save context; "
                + "consult the version-matched INPUT_GIPAW documentation. The draft is "
                + "written ONLY via the explicit save action. The shielding/EFG tensor "
                + "parser and the reference-shift workflow remain the #66 depth.");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "prefix,%s,verbatim",
                csvCell(context.getPrefix())));
        csv.add(String.format(Locale.ROOT, "outdir,%s,verbatim",
                csvCell(context.getOutdir())));
        csv.add(String.format(Locale.ROOT, "required_edit_placeholders,%d,draft-guard",
                requiredEdits));
        csv.add("job_default,'gipaw',pre-filled");
        return new AnalysisReport(label, true, text.toString(), csv, draft);
    }

    /**
     * Roadmap #64 (first slice): turbo_lanczos.x LR-TDDFT draft from the live
     * input context. The draft is deliberately NON-RUNNABLE (num_init, num_eign,
     * ipol and charge_response are REQUIRED-EDIT) and is explicitly
     * linear-response - never labelled RT-TDDFT.
     */
    private static AnalysisReport analyzeTddfptInputDraft(Project project) {
        String label = AnalysisKind.TDDFPT_INPUT_DRAFT.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        OperationResult<TurboLanczosInputPlanner.LrContext> extracted =
                TurboLanczosInputPlanner.extractContext(input);
        if (!extracted.isSuccess() || extracted.getValue().isEmpty()) {
            return failure(label, "turbo_lanczos.x draft refused: ["
                    + extracted.getCode() + "] " + extracted.getMessage());
        }
        TurboLanczosInputPlanner.LrContext context = extracted.getValue().get();
        String draft = TurboLanczosInputPlanner.draft(context);
        int requiredEdits = TurboLanczosInputPlanner.countRequiredEdits(draft);
        StringBuilder text = new StringBuilder();
        text.append("Save context echoed from the live input:\n");
        text.append(String.format(Locale.ROOT,
                "  prefix = '%s'; outdir = '%s'%n", context.getPrefix(),
                context.getOutdir()));
        text.append(String.format(Locale.ROOT,
                "%nThe draft is a NON-RUNNABLE two-namelist skeleton (&lr_input + "
                        + "&lr_control): the save context pre-filled verbatim, %d "
                        + "REQUIRED-EDIT marker(s) guarding the physics that defines "
                        + "the experiment (num_init Lanczos steps, num_eign T-matrix "
                        + "eigenvalues, ipol polarization - 1/2/3 one axis or 4 = "
                        + "three components, charge_response 0 transition vs 1 "
                        + "charge).%n",
                requiredEdits));
        text.append("\nPrerequisites named by #64: this is LINEAR-RESPONSE TDDFT "
                + "of the charge susceptibility - never labelled RT-TDDFT; a "
                + "CONVERGED pw.x SCF save must exist in the echoed prefix/outdir "
                + "(stated, not checkable offline); iteration/restart convergence "
                + "is documented-experiment territory via the version-matched "
                + "INPUT_turbo_lanczos documentation. The draft is written ONLY "
                + "via the explicit save action; the post-run turbo_spectrum.x "
                + "output is already parsed by the TDDFT_SPECTRUM kind. The "
                + "explicit turbo_lanczos/turbo_spectrum adapters and "
                + "version-matched schemas remain the #64 depth.");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "prefix,%s,verbatim",
                csvCell(context.getPrefix())));
        csv.add(String.format(Locale.ROOT, "outdir,%s,verbatim",
                csvCell(context.getOutdir())));
        csv.add(String.format(Locale.ROOT, "required_edit_placeholders,%d,draft-guard",
                requiredEdits));
        csv.add("namelists,lr_input+lr_control,skeleton");
        return new AnalysisReport(label, true, text.toString(), csv, draft);
    }

    /**
     * Roadmap #82: surface Miller-plane geometry from the live cell. Exact
     * reciprocal metric on the lattice rows; the ESM z-gate mirrors the ESM
     * auditor. Plane geometry ONLY - no atoms are built.
     */
    private static AnalysisReport analyzeSlabMillerPreview(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.SLAB_MILLER_PREVIEW.getLabel();
        Cell cell;
        try {
            cell = project.getCell();
        } catch (RuntimeException ex) {
            return failure(label, "Reading the project cell failed: " + ex.getMessage());
        }
        if (cell == null) {
            return failure(label, "[SLAB_CELL] The project has no cell to metric against.");
        }
        int[] indices = params.getMillerIndices();
        double[][] lattice = cell.copyLattice();
        OperationResult<SlabMillerMath.MillerPlane> computed = SlabMillerMath.compute(
                lattice, indices[0], indices[1], indices[2]);
        if (!computed.isSuccess() || computed.getValue().isEmpty()) {
            return failure(label, "Slab preview refused: [" + computed.getCode() + "] "
                    + computed.getMessage());
        }
        SlabMillerMath.MillerPlane plane = computed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Requested plane: (%d %d %d)", indices[0],
                indices[1], indices[2]));
        if (plane.getCommonFactor() > 1) {
            text.append(String.format(Locale.ROOT,
                    "  (normalized: common factor %d removed; the SAME family is (%d %d %d))",
                    plane.getCommonFactor(), plane.getH(), plane.getK(), plane.getL()));
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT, "Cell volume |det(lattice rows)| = %.6f "
                + "Ang^3%n", plane.getVolumeAng3()));
        text.append(String.format(Locale.ROOT,
                "|G(%d %d %d)| = %.6f 1/Ang;  d-spacing = %.6f Ang%n", plane.getH(),
                plane.getK(), plane.getL(), plane.getRecipNormInvAng(),
                plane.getDSpacingAng()));
        text.append(String.format(Locale.ROOT,
                "Surface normal (Cartesian): (%.6f, %.6f, %.6f)%n", plane.getNx(),
                plane.getNy(), plane.getNz()));
        if (plane.isEsmAligned()) {
            text.append("ESM z-gate: ALIGNED - the surface normal is along +z within "
                    + "1e-8; a slab built this way can host assume_isolated='esm' "
                    + "(final audit after building stays with ESM_SLAB_CHECK).\n");
        } else {
            text.append("ESM z-gate: NOT along +z - a slab destined for "
                    + "assume_isolated='esm' must be ROTATED so this normal becomes +z "
                    + "before/at building; QE's ESM requires it.\n");
        }
        text.append(String.format(Locale.ROOT,
                "%nHonesty boundary: exact plane geometry from the live cell (reciprocal "
                        + "metric, 2*pi convention; the normal follows the lattice-row "
                        + "handedness). NO termination enumeration, stoichiometry or "
                        + "polarity audit, no atom construction, no vacuum sizing - that "
                        + "is the remaining #82 builder depth. Do not confuse d-spacing "
                        + "with a slab thickness or a vacuum gap.%n"));
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "plane,(%d %d %d),normalized", plane.getH(),
                plane.getK(), plane.getL()));
        csv.add(String.format(Locale.ROOT, "common_factor,%d,provenance",
                plane.getCommonFactor()));
        csv.add(String.format(Locale.ROOT, "volume_ang3,%.6f,abs-det",
                plane.getVolumeAng3()));
        csv.add(String.format(Locale.ROOT, "recip_norm_inv_ang,%.6f,exact-metric",
                plane.getRecipNormInvAng()));
        csv.add(String.format(Locale.ROOT, "d_spacing_ang,%.6f,computed",
                plane.getDSpacingAng()));
        csv.add(String.format(Locale.ROOT, "normal,[%.6f %.6f %.6f],unit-cartesian",
                plane.getNx(), plane.getNy(), plane.getNz()));
        csv.add(String.format(Locale.ROOT, "esm_aligned,%s,z-gate-1e-8",
                plane.isEsmAligned()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #75: CIF structure review over the fail-closed subset reader.
     * REVIEW only - no symmetry expansion, no element guessing, nothing is
     * applied to the project.
     */
    private static AnalysisReport analyzeCifReview(File source) {
        String label = AnalysisKind.CIF_REVIEW.getLabel();
        OperationResult<CifStructureReader.CifStructure> parsed =
                CifStructureReader.parse(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "CIF review refused the file " + source.getName()
                    + ":\n[" + parsed.getCode() + "] " + parsed.getMessage());
        }
        CifStructureReader.CifStructure structure = parsed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "data_ block: %s;  atoms reviewed: %d%n", structure.getBlockName(),
                structure.getAtoms().size()));
        text.append(String.format(Locale.ROOT,
                "Formula (verbatim, NOT trusted as stoichiometry proof): %s;  space "
                        + "group (verbatim): %s%n",
                structure.getChemicalFormula() == null ? "(absent)"
                        : structure.getChemicalFormula(),
                structure.getSpaceGroupName() == null ? "(absent)"
                        : structure.getSpaceGroupName()));
        if (structure.hasCell()) {
            double[] cell = structure.getCell();
            text.append(String.format(Locale.ROOT,
                    "Cell: a=%.6f b=%.6f c=%.6f Ang, alpha=%.3f beta=%.3f gamma=%.3f deg;"
                            + " volume = %.4f Ang^3%n",
                    cell[0], cell[1], cell[2], cell[3], cell[4], cell[5],
                    structure.cellVolume()));
        } else {
            text.append("Cell: absent (no cell tags - the fractional coordinates have no "
                    + "metric here).\n");
        }
        StringBuilder composition = new StringBuilder();
        for (Map.Entry<String, Integer> entry : structure.elementCounts().entrySet()) {
            if (composition.length() > 0) {
                composition.append(',');
            }
            composition.append(entry.getKey()).append('=').append(entry.getValue());
        }
        text.append(String.format(Locale.ROOT,
                "Composition (from the type_symbol column ONLY): %s%n",
                composition.length() == 0 ? "(no type_symbol column present)"
                        : composition.toString()));
        text.append(String.format(Locale.ROOT,
                "Atoms anonymous (no type symbol; labels NEVER guessed): %d%n"
                        + "Partial-occupancy atoms (0 < occ < 1; unresolved disorder - "
                        + "reported, not resolved): %d%n"
                        + "Uncertainty-stripped atoms/cell-tags (x(n) parsed as x; "
                        + "counted, never propagated): %d%n"
                        + "Out-of-unit-cell fractional rows (reported, never wrapped): "
                        + "%d%n",
                structure.getAnonymousCount(), structure.getPartialOccupancyCount(),
                structure.getUncertaintyStripCount(), structure.getOutOfCellCount()));
        text.append(String.format(Locale.ROOT,
                "Symmetry-operation loops: %d row(s) - positions are ASYMMETRIC ONLY; "
                        + "NO symmetry expansion was applied. Non-coordinate loops "
                        + "skipped: %d.%n",
                structure.getSymmetryOpRows(), structure.getSkippedLoops()));
        text.append("\nREVIEW only: nothing is applied to the project; full CIF 2.0 "
                + "namespaces, dictionary validation, and symmetry expansion remain the "
                + "#75 depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("label,element,frac_x,frac_y,frac_z,occupancy,uncertainty_stripped");
        int cap = 20000;
        int shown = 0;
        for (CifStructureReader.CifAtom atom : structure.getAtoms()) {
            if (shown >= cap) {
                break;
            }
            csv.add(String.format(Locale.ROOT, "%s,%s,%.6f,%.6f,%.6f,%.4f,%s",
                    csvCell(atom.getLabel()),
                    csvCell(atom.getElement() == null ? "" : atom.getElement()),
                    atom.getFx(), atom.getFy(), atom.getFz(), atom.getOccupancy(),
                    atom.isUncertaintyStripped()));
            shown += 1;
        }
        if (structure.getAtoms().size() > cap) {
            csv.add("# truncated at 20000 atom rows (cap)");
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #78 (partial): MOL/SDF V2000 single-record review. Nothing is
     * imported into the project; pseudo atoms are never element-guessed and no
     * aromaticity/stereo chemistry is perceived.
     */
    private static AnalysisReport analyzeMolSdfReview(File source) {
        String label = AnalysisKind.MOL_SDF_REVIEW.getLabel();
        OperationResult<SdfStructureReader.SdfStructure> parsed =
                SdfStructureReader.parse(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "MOL/SDF review refused the file " + source.getName()
                    + ":\n[" + parsed.getCode() + "] " + parsed.getMessage());
        }
        SdfStructureReader.SdfStructure structure = parsed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Record title (verbatim line 1): %s%n",
                structure.getTitle().isEmpty() ? "(blank)" : structure.getTitle()));
        text.append(String.format(Locale.ROOT,
                "Counts: %d atom(s), %d bond(s);  version marker 'V2000' %s%n",
                structure.getAtoms().size(), structure.getBonds().size(),
                structure.hasVersionMarker() ? "present"
                        : "ABSENT - parsed by the fixed-width/token layout only"));
        StringBuilder composition = new StringBuilder();
        for (Map.Entry<String, Integer> entry : structure.elementCounts().entrySet()) {
            if (composition.length() > 0) {
                composition.append(',');
            }
            composition.append(entry.getKey()).append('=').append(entry.getValue());
        }
        text.append(String.format(Locale.ROOT,
                "Composition (from the atom-block element column ONLY): %s%n",
                composition.length() == 0 ? "(no true-element atoms present)"
                        : composition.toString()));
        text.append(String.format(Locale.ROOT,
                "Pseudo/query atoms (A, Q, L, LP, *, R, R#; NOT elements, NEVER "
                        + "guessed): %d%n",
                structure.getPseudoAtomCount()));
        StringBuilder bonds = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : structure.bondTypeCounts().entrySet()) {
            if (bonds.length() > 0) {
                bonds.append(',');
            }
            bonds.append("type ").append(entry.getKey()).append('=')
                    .append(entry.getValue());
        }
        text.append(String.format(Locale.ROOT,
                "Bond types ECHOED (1/2/3/4 as written; NO aromaticity, stereo or "
                        + "valence chemistry perceived): %s%n",
                bonds.length() == 0 ? "(no bonds)" : bonds.toString()));
        text.append(String.format(Locale.ROOT,
                "M  CHG declared charges: %d atom(s), sum = %+d (declared, NOT "
                        + "validated against any chemistry model)%n",
                structure.getChargedAtoms(), structure.getChargeSum()));
        text.append(String.format(Locale.ROOT,
                "Property-block lines: %d; SDF data fields after M  END: %d%n",
                structure.getPropertyLines(), structure.getDataFieldCount()));
        text.append("\nREVIEW only: nothing is imported into the project; V3000, "
                + "multi-record bundles, aromaticity/stereo perception and an "
                + "independent-parser corpus comparison remain the #78 depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("index,element,x,y,z,pseudo_atom");
        int cap = 20000;
        int shown = 0;
        for (SdfStructureReader.SdfAtom atom : structure.getAtoms()) {
            if (shown >= cap) {
                break;
            }
            csv.add(String.format(Locale.ROOT, "%d,%s,%.4f,%.4f,%.4f,%s",
                    atom.getIndex(),
                    csvCell(atom.getElement() == null ? "" : atom.getElement()),
                    atom.getX(), atom.getY(), atom.getZ(), atom.isPseudoAtom()));
            shown += 1;
        }
        if (structure.getAtoms().size() > cap) {
            csv.add("# truncated at 20000 atom rows (cap)");
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #147 (first slice): compositional baseline screen of an extXYZ
     * training dataset. The linear E = intercept + sum c_s n_s least-squares
     * fit is the isolated-atom/composition reference family; large residuals
     * are review flags, never accusations; unlabeled frames are excluded,
     * never guessed.
     */
    private static AnalysisReport analyzeMlDatasetBaseline(File source) {
        String label = AnalysisKind.ML_DATASET_BASELINE.getLabel();
        OperationResult<CompositionalBaselineMath.BaselineReport> fitted =
                CompositionalBaselineMath.evaluate(source.toPath());
        if (!fitted.isSuccess() || fitted.getValue().isEmpty()) {
            return failure(label, "The compositional baseline refused the file "
                    + source.getName() + ":\n[" + fitted.getCode() + "] "
                    + fitted.getMessage());
        }
        CompositionalBaselineMath.BaselineReport report = fitted.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Linear compositional baseline E = intercept + sum_species c_s "
                        + "n_s (least squares over energy-labeled frames):%n"));
        text.append(String.format(Locale.ROOT,
                "Frames: %d used (energy-labeled); %d EXCLUDED for a missing or "
                        + "unparseable energy/free_energy label (counted, NEVER "
                        + "guessed)%n",
                report.getFramesUsed(), report.getFramesSkippedNoEnergy()));
        text.append(String.format(Locale.ROOT, "intercept = %.9f eV%n",
                report.getInterceptEv()));
        StringBuilder coefficients = new StringBuilder();
        for (int idx = 0; idx < report.getSpecies().size(); idx += 1) {
            if (coefficients.length() > 0) {
                coefficients.append(";  ");
            }
            coefficients.append(String.format(Locale.ROOT, "c[%s] = %.9f eV/atom",
                    report.getSpecies().get(idx),
                    report.getCoefficientsEv().get(idx)));
        }
        text.append(coefficients).append('\n');
        text.append(String.format(Locale.ROOT,
                "residual RMS = %.9f eV;  mean |res| = %.9f eV;  max |res| = "
                        + "%.9f eV%n",
                report.getRmsEv(), report.getMeanAbsEv(), report.getMaxAbsEv()));
        text.append("Largest residuals (review flags, NOT accusations):\n");
        for (CompositionalBaselineMath.ResidualOutlier outlier : report.getOutliers()) {
            text.append(String.format(Locale.ROOT,
                    "  frame %d: residual %+.6f eV (label %.6f eV vs fit %.6f eV)%n",
                    outlier.getFrame(), outlier.getResidualEv(),
                    outlier.getActualEv(), outlier.getFittedEv()));
        }
        text.append("\nSCREEN only: the coefficients are regression outputs of "
                + "THIS dataset (not atomization/formation energies); a small "
                + "residual does NOT prove a label right, a large residual asks "
                + "for human review. Comparing trained-MODEL predictions against "
                + "this baseline (not just the labels) remains the #147 depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "intercept_ev,%.9f,least-squares constant",
                report.getInterceptEv()));
        for (int idx = 0; idx < report.getSpecies().size(); idx += 1) {
            csv.add(String.format(Locale.ROOT, "%s,%.9f,eV per atom (this-dataset fit)",
                    csvCell(report.getSpecies().get(idx)),
                    report.getCoefficientsEv().get(idx)));
        }
        csv.add(String.format(Locale.ROOT, "frames_used,%d,energy-labeled",
                report.getFramesUsed()));
        csv.add(String.format(Locale.ROOT, "frames_excluded_no_label,%d,never guessed",
                report.getFramesSkippedNoEnergy()));
        csv.add(String.format(Locale.ROOT, "rms_ev,%.9f,screening not validation",
                report.getRmsEv()));
        csv.add(String.format(Locale.ROOT, "max_abs_residual_ev,%.9f,",
                report.getMaxAbsEv()));
        if (!report.getOutliers().isEmpty()) {
            CompositionalBaselineMath.ResidualOutlier top = report.getOutliers().get(0);
            csv.add(String.format(Locale.ROOT, "top_outlier_frame,%d,%+.6f eV residual",
                    top.getFrame(), top.getResidualEv()));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #124 (alignment slice): explicit Fermi/VBM/vacuum/user reference
     * alignment of a two-series comparison. Both references are REQUIRED
     * analyst input; the shift is printed and embedded in every CSV header,
     * never hidden.
     */
    private static AnalysisReport analyzeSeriesRefAlign(File source,
            AnalysisParameters params) {
        String label = AnalysisKind.SERIES_REF_ALIGN.getLabel();
        OperationResult<SeriesAlignmentMath.AlignedComparison> aligned =
                SeriesAlignmentMath.align(source.toPath(), params.getAlignMode(),
                        params.getAlignReferenceEv1(), params.getAlignReferenceEv2(),
                        params.getAlignTargetEv());
        if (!aligned.isSuccess() || aligned.getValue().isEmpty()) {
            return failure(label, "The reference alignment refused the file "
                    + source.getName() + ":\n[" + aligned.getCode() + "] "
                    + aligned.getMessage());
        }
        SeriesAlignmentMath.AlignedComparison result = aligned.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "EXPLICIT %s-reference alignment (analyst-supplied; NOTHING was "
                        + "inferred from the data):%n", result.getMode()));
        text.append(String.format(Locale.ROOT,
                "  series 1 '%s' reference = %.9f eV;  series 2 '%s' reference = "
                        + "%.9f eV%n",
                result.getFirstSeriesLabel(), result.getReferenceEv1(),
                result.getSecondSeriesLabel(), result.getReferenceEv2()));
        text.append(String.format(Locale.ROOT,
                "  applied shift (E - reference, landing at %s eV);  reference "
                        + "difference ref2 - ref1 = %+.9f eV%s%n",
                "USER".equals(result.getMode())
                        ? String.format(Locale.ROOT, "%.9f", result.getTargetEv())
                        : "0.000000000",
                result.getReferenceShiftEv(),
                result.isLoudShift()
                        ? "  ** LOUD FLAG: |shift| exceeds 1 eV - probable units "
                                + "slip or an incomparable reference; flagged, "
                                + "never hidden **"
                        : ""));
        text.append(String.format(Locale.ROOT,
                "Aligned deltas (series 2 - series 1): RMS = %.9f eV;  mean = "
                        + "%+.9f eV;  max |delta| = %.9f eV at %s = %.9f%n",
                result.getRmsEv(), result.getMeanEv(), result.getMaxAbsEv(),
                result.getParameterLabel(), result.getMaxAbsAtParameter()));
        text.append(String.format(Locale.ROOT,
                "Rows: %d aligned (%d rejected by the scanner, counted);  the "
                        + "aligned DELTAS are identical in every reference mode "
                        + "(the landing point cancels).%n",
                result.getRowCount(), result.getRejectedRows()));
        text.append("\nYou are ASSERTING the two references share a comparable "
                + "physical origin (e.g. vacuum levels of the same slab "
                + "termination). Grid agreement holds by construction of this CSV; "
                + "cutoff/k-mesh sampling comparability is NOT visible here and "
                + "stays your burden. Multi-project overlays beyond the two-series "
                + "CSV remain the #124 depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add(String.format(Locale.ROOT,
                "# explicit %s alignment: ref1=%.9f eV; ref2=%.9f eV; shift ref2-ref1 "
                        + "= %+.9f eV%s",
                result.getMode(), result.getReferenceEv1(), result.getReferenceEv2(),
                result.getReferenceShiftEv(),
                result.isLoudShift() ? "; LOUD FLAG |shift| > 1 eV" : ""));
        csv.add("parameter,e1_aligned_ev,e2_aligned_ev,delta_aligned_ev");
        int cap = 20000;
        int shown = 0;
        for (double[] row : result.getRows()) {
            if (shown >= cap) {
                break;
            }
            csv.add(String.format(Locale.ROOT, "%.9f,%.9f,%.9f,%+.9f",
                    row[0], row[1], row[2], row[3]));
            shown += 1;
        }
        if (result.getRows().size() > cap) {
            csv.add("# truncated at 20000 rows (cap)");
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #46 (Fermi-reference slice): E - E_F review of a .dat.gnu band
     * file with a REQUIRED explicit Fermi value. Crossing stats use an
     * exactly-zero tolerance and are labelled point-sampled - not the full
     * #47 classification.
     */
    private static AnalysisReport analyzeBandsFermiReview(File source,
            AnalysisParameters params) {
        String label = AnalysisKind.BANDS_FERMI_REVIEW.getLabel();
        OperationResult<BandsFermiReviewMath.BandsReview> reviewed =
                BandsFermiReviewMath.review(source.toPath(), params.getFermiEv());
        if (!reviewed.isSuccess() || reviewed.getValue().isEmpty()) {
            return failure(label, "The band review refused the file " + source.getName()
                    + ":\n[" + reviewed.getCode() + "] " + reviewed.getMessage());
        }
        BandsFermiReviewMath.BandsReview review = reviewed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Explicit Fermi reference (analyst-supplied, never inferred): E_F = "
                        + "%.6f eV; all energies below are E - E_F.%n",
                review.getFermiEv()));
        text.append(String.format(Locale.ROOT,
                "Bands: %d curve(s), %d point(s) total;  k-span %.6f .. %.6f.%n",
                review.getBandCount(), review.getTotalPoints(), review.getKMin(),
                review.getKMax()));
        text.append(String.format(Locale.ROOT,
                "Bands straddling E_F (min < 0 < max, EXACTLY-zero tolerance on the "
                        + "sampled points): %d - a point-sampled metallicity "
                        + "INDICATOR, not the #47 occupation/gap classification.%n",
                review.getCrossingCount()));
        if (!Double.isNaN(review.getVbmSideEv()) && !Double.isNaN(review.getCbmSideEv())) {
            text.append(String.format(Locale.ROOT,
                    "Point-sampled occupied-side max = %+.6f eV; empty-side min = "
                            + "%+.6f eV; naive span = %.6f eV (sampling only - the "
                            + "valence-count detector with a metallicity tolerance "
                            + "is the #47 slice).%n",
                    review.getVbmSideEv(), review.getCbmSideEv(),
                    review.getNaiveGapEv()));
        } else {
            text.append("No finite occupied/empty pair exists - every sampled point "
                    + "lies on one side of E_F; no span is quoted.\n");
        }
        int cap = 48;
        text.append(String.format(Locale.ROOT,
                "Per-band shifted ranges (first %d band(s), the rest counted above):%n",
                Math.min(cap, review.getBandCount())));
        for (BandsFermiReviewMath.BandStats stats : review.getPerBand()) {
            if (stats.getIndex() > cap) {
                break;
            }
            text.append(String.format(Locale.ROOT,
                    "  band %3d: %d point(s);  min %+.6f eV, max %+.6f eV%s%n",
                    stats.getIndex(), stats.getPoints(), stats.getMinShiftedEv(),
                    stats.getMaxShiftedEv(),
                    stats.crossesZero() ? "  (straddles E_F)" : ""));
        }
        if (review.getDiagnosticsTotal() > 0) {
            text.append(String.format(Locale.ROOT,
                    "Parser diagnostics: %d total (first %d shown; sampling hides "
                            + "nothing):%n",
                    review.getDiagnosticsTotal(), review.getDiagnostics().size()));
            for (String diagnostic : review.getDiagnostics()) {
                text.append("  - ").append(diagnostic).append('\n');
            }
        }
        text.append("\nHonesty notes: spin channels travel as SEPARATE files in "
                + "QE - this review covers only " + source.getName() + "; energies "
                + "are in the SAME unit as the supplied reference (QE plotband "
                + "convention: eV; nothing was re-scaled); nothing was written "
                + "back to the project.\n");
        List<String> csv = new ArrayList<>();
        csv.add("band,points,min_e_minus_ef_ev,max_e_minus_ef_ev,straddles_ef");
        for (BandsFermiReviewMath.BandStats stats : review.getPerBand()) {
            csv.add(String.format(Locale.ROOT, "%d,%d,%+.9f,%+.9f,%s",
                    stats.getIndex(), stats.getPoints(), stats.getMinShiftedEv(),
                    stats.getMaxShiftedEv(), stats.crossesZero()));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #47 (curve slice): band-gap classification from band curves via
     * an explicit valence count and metallicity tolerance. METALLIC_OVERLAP
     * names both readings (true metal or wrong valence count for a
     * partial-occupation system); unequal k-grids are refused, not smoothed.
     */
    private static AnalysisReport analyzeBandGapBands(File source,
            AnalysisParameters params) {
        String label = AnalysisKind.BAND_GAP_BANDS.getLabel();
        OperationResult<BandGapBandMath.GapClassification> classified =
                BandGapBandMath.classify(source.toPath(), params.getGapValenceBands(),
                        params.getGapToleranceEv(), params.getGapKTolerance());
        if (!classified.isSuccess() || classified.getValue().isEmpty()) {
            return failure(label, "The band-gap classification refused the file "
                    + source.getName() + ":\n[" + classified.getCode() + "] "
                    + classified.getMessage());
        }
        BandGapBandMath.GapClassification gap = classified.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Inputs (ALL analyst-supplied): valence bands nV = %d of %d "
                        + "curve(s); metallicity tolerance = %.6f eV; directness "
                        + "k tolerance = %.3e.%n",
                gap.getValenceBands(), gap.getBandCount(), gap.getToleranceEv(),
                gap.getKTolerance()));
        text.append(String.format(Locale.ROOT,
                "VBM = %+.6f eV at sampled k = %.6f;  CBM = %+.6f eV at sampled "
                        + "k = %.6f;  gap = %.6f eV (curve nV vs nV+1 over the "
                        + "SAMPLED k grid - a finer mesh can move both).%n",
                gap.getVbmEv(), gap.getVbmAtK(), gap.getCbmEv(), gap.getCbmAtK(),
                gap.getGapEv()));
        switch (gap.getVerdict()) {
        case GAP_DIRECT:
            text.append(String.format(Locale.ROOT,
                    "Verdict: GAP, DIRECT (|k(VBM) - k(CBM)| = %.6f <= k "
                            + "tolerance).%n",
                    Math.abs(gap.getVbmAtK() - gap.getCbmAtK())));
            break;
        case GAP_INDIRECT:
            text.append(String.format(Locale.ROOT,
                    "Verdict: GAP, INDIRECT (|k(VBM) - k(CBM)| = %.6f > k "
                            + "tolerance).%n",
                    Math.abs(gap.getVbmAtK() - gap.getCbmAtK())));
            break;
        case METALLIC_OVERLAP:
            text.append("Verdict: METALLIC_OVERLAP (CBM below VBM beyond "
                    + "tolerance): the bands genuinely cross, OR the valence "
                    + "count is wrong for a partial-occupation (alkali-like) "
                    + "system - BOTH readings are named, neither is resolved "
                    + "silently.\n");
            break;
        default:
            text.append(String.format(Locale.ROOT,
                    "Verdict: DEGENERATE_WITHIN_TOLERANCE (|gap| = %.6f eV <= "
                            + "%.6f eV): at this tolerance the data CANNOT "
                            + "decide metal vs gap, and it says so instead of "
                            + "rounding to a comfortable answer.%n",
                    Math.abs(gap.getGapEv()), gap.getToleranceEv()));
            break;
        }
        text.append("\nHonesty notes: the classification is Fermi-free (curve "
                + "index by valence count); spin channels arrive as SEPARATE "
                + "files (this verdict covers only " + source.getName() + "); "
                + "unequal k-grids would have been refused, not interpolated; "
                + "energies are in the file's own unit (QE plotband convention "
                + "eV); the occupation-analysis half of #47 (from the pw.x "
                + "log) remains open depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "valence_bands,%d,analyst-supplied",
                gap.getValenceBands()));
        csv.add(String.format(Locale.ROOT, "band_curves,%d,", gap.getBandCount()));
        csv.add(String.format(Locale.ROOT, "vbm_ev,%+.6f,at k %.6f (sampled)",
                gap.getVbmEv(), gap.getVbmAtK()));
        csv.add(String.format(Locale.ROOT, "cbm_ev,%+.6f,at k %.6f (sampled)",
                gap.getCbmEv(), gap.getCbmAtK()));
        csv.add(String.format(Locale.ROOT, "gap_ev,%.6f,cbm - vbm", gap.getGapEv()));
        csv.add(String.format(Locale.ROOT, "tolerance_ev,%.6f,analyst-supplied",
                gap.getToleranceEv()));
        csv.add(String.format(Locale.ROOT, "verdict,%s,", gap.getVerdict()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #90 (format + integrity slice): verifies a TransformJournal
     * chain (sequence, parent linkage, SHA-256 per entry) and reviews the
     * recorded provenance. Replay wiring into the builder paths remains the
     * #90 depth; this slice guarantees the journal itself cannot be tampered
     * with silently.
     */
    private static AnalysisReport analyzeProvenanceJournalReview(File source) {
        String label = AnalysisKind.PROVENANCE_JOURNAL_REVIEW.getLabel();
        OperationResult<TransformJournal.JournalSummary> verified =
                TransformJournal.verify(source.toPath());
        if (!verified.isSuccess() || verified.getValue().isEmpty()) {
            return failure(label, "The provenance journal refused the file "
                    + source.getName() + ":\n[" + verified.getCode() + "] "
                    + verified.getMessage());
        }
        TransformJournal.JournalSummary summary = verified.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Chain VERIFIED: %d entry(ies), 1-based sequence intact, parent "
                        + "linkage intact, every SHA-256 entry hash recomputed "
                        + "and equal - a tampered or reordered journal would "
                        + "have been refused, not repaired.%n",
                summary.getEntryCount()));
        text.append(String.format(Locale.ROOT,
                "Entries carrying a 3x3 transform matrix: %d (the rest are "
                        + "parameter-only operations).%n",
                summary.getMatrixCount()));
        TransformJournal.JournalEntry first = summary.getEntries().get(0);
        TransformJournal.JournalEntry last = summary.getEntries()
                .get(summary.getEntries().size() - 1);
        text.append(String.format(Locale.ROOT,
                "First: seq %d '%s' from source '%s'%nLast:  seq %d '%s' (hash "
                        + "%.16s...)%n",
                first.getSeq(), first.getOperation(), first.getSourceId(),
                last.getSeq(), last.getOperation(), last.getEntryHash()));
        text.append("\nReplay note: every entry records source id, operation, "
                + "the exact row-major matrix (or '-') and ordered k=v "
                + "parameters - the data model exact reconstruction needs - but "
                + "the REPLAY wiring into each builder path is the remaining "
                + "#90 depth; this review verifies integrity only and applies "
                + "nothing to the project.\n");
        OperationResult<JournalReplayMath.ReplaySummary> replay = JournalReplayMath
                .combine(summary);
        if (replay.isSuccess() && replay.getValue().isPresent()) {
            JournalReplayMath.ReplaySummary folded = replay.getValue().get();
            double[][] combined = folded.getCombined();
            text.append(String.format(Locale.ROOT,
                    "%nReplay arithmetic: folded %d matrix entr(ies) in journal "
                            + "order (first entry applied FIRST); parameter-only "
                            + "entr(ies) skipped and listed by seq: %s%n",
                    folded.getMatrixEntries(),
                    folded.getSkippedSequences().isEmpty() ? "none"
                            : folded.getSkippedSequences().toString()));
            for (int row = 0; row < 3; row += 1) {
                text.append(String.format(Locale.ROOT,
                        "  [% .6f % .6f % .6f ]%n", combined[row][0],
                        combined[row][1], combined[row][2]));
            }
            text.append(String.format(Locale.ROOT,
                    "  det = %.6f (%s; handedness %s)%s%n", folded.getCombinedDet(),
                    folded.isSingular()
                            ? "SINGULAR - no inverse replay exists, stated not "
                                    + "perturbed"
                            : "invertible",
                    folded.preservedHandedness() ? "preserved" : "INVERTED",
                    folded.getSkippedSequences().isEmpty() ? ""
                            : "; matrix-only replay reproduces the CELL metric "
                                    + "chain - the skipped steps need their "
                                    + "parameters, which is the #90 depth"));
        } else {
            text.append(String.format(Locale.ROOT,
                    "%nReplay arithmetic: no matrix chain to fold ([%s] %s)%n",
                    replay.getCode(), replay.getMessage()));
        }
        List<String> csv = new ArrayList<>();
        csv.add("seq,source,operation,has_matrix,parameters,entry_hash");
        for (TransformJournal.JournalEntry entry : summary.getEntries()) {
            csv.add(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%s",
                    entry.getSeq(), csvCell(entry.getSourceId()),
                    csvCell(entry.getOperation()), entry.getMatrix() != null,
                    csvCell(String.join(";", entry.getParameters())),
                    entry.getEntryHash()));
        }
        if (replay.isSuccess() && replay.getValue().isPresent()) {
            JournalReplayMath.ReplaySummary folded = replay.getValue().get();
            csv.add(String.format(Locale.ROOT,
                    "replay_combined_det,%.6f,%s", folded.getCombinedDet(),
                    folded.isSingular() ? "singular" : "invertible"));
            csv.add(String.format(Locale.ROOT, "replay_matrix_entries,%d,skipped_seqs=%s",
                    folded.getMatrixEntries(),
                    csvCell(folded.getSkippedSequences().isEmpty() ? "none"
                            : folded.getSkippedSequences().toString())));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #105 (schema slice): renders the curated SQLite WAL target
     * schema, the open pragmas and the exact 0 -> current migration chain.
     * The durable JSONL store stays ACTIVE until the sqlite-jdbc driver and
     * integration CI land; nothing here executes SQL.
     */
    private static AnalysisReport analyzeJobDbSchemaPlan() {
        String label = AnalysisKind.JOB_DB_SCHEMA_PLAN.getLabel();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "SQLite WAL target schema at version %d (%d one-step "
                        + "migration(s)):%n",
                JobDbSchema.currentVersion(), JobDbSchema.migrations().size()));
        text.append("Open pragmas applied before anything else:\n");
        for (String pragma : JobDbSchema.OPEN_PRAGMAS) {
            text.append("  ").append(pragma).append('\n');
        }
        OperationResult<List<JobDbSchema.Migration>> plan = JobDbSchema
                .migrationPlan(0, JobDbSchema.currentVersion());
        List<JobDbSchema.Migration> steps = plan.getValue().orElse(List.of());
        text.append(String.format(Locale.ROOT,
                "%nFull fresh-install plan (v0 -> v%d, %d statement(s)):%n",
                JobDbSchema.currentVersion(), JobDbSchema.statementCount(steps)));
        for (JobDbSchema.Migration step : steps) {
            text.append(String.format(Locale.ROOT, "%nv%d - %s%n",
                    step.getToVersion(), step.getName()));
            for (String statement : step.getStatements()) {
                text.append("  ").append(statement).append('\n');
            }
        }
        text.append("\nHonesty block: this build intentionally ships NO "
                + "sqlite-jdbc driver - nothing above is executed here; the "
                + "durable JSONL JobQueueStore remains the ACTIVE queue store "
                + "until the driver and integration CI land (the roadmap's "
                + "'thousands of jobs survive restart' gate). Migrations run "
                + "once inside a transaction under the qf_meta.schema_version "
                + "guard; CREATE statements carry IF NOT EXISTS; the v3 ALTER "
                + "COLUMN steps rely on the version guard (SQLite has no ADD "
                + "COLUMN IF NOT EXISTS); downgrades are REFUSED (no tested "
                + "rollback path); lease expiry/claim logic and per-job-lock "
                + "transactions are runtime depth for #95-#97.\n");
        List<String> csv = new ArrayList<>();
        csv.add("step,to_version,statements,name");
        for (JobDbSchema.Migration step : steps) {
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%s", steps.indexOf(step) + 1,
                    step.getToVersion(), step.getStatements().size(),
                    csvCell(step.getName())));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #117 (query-builder slice): validates an OPTIMADE v1 base URL,
     * composes the bounded element-driven filter, and returns the exact
     * percent-encoded GET URL for review. NOTHING is fetched; the draft
     * writes a file only via the explicit save action.
     */
    private static AnalysisReport analyzeOptimadeQueryDraft(AnalysisParameters params) {
        String label = AnalysisKind.OPTIMADE_QUERY_DRAFT.getLabel();
        OperationResult<OptimadeQueryBuilder.OptimadeQuery> built =
                OptimadeQueryBuilder.build(params.getOptimadeBase(),
                        params.getOptimadeElements(), params.getOptimadeNeMax(),
                        params.getOptimadeNsMax(), params.getOptimadePageLimit());
        if (!built.isSuccess() || built.getValue().isEmpty()) {
            return failure(label, "The OPTIMADE query draft was refused:\n["
                    + built.getCode() + "] " + built.getMessage());
        }
        OptimadeQueryBuilder.OptimadeQuery query = built.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Provider base (validated, normalized): %s%n", query.getNormalizedBase()));
        text.append(String.format(Locale.ROOT,
                "Filter (%d clause(s), owned element-driven subset):%n  %s%n",
                query.getClauses().size(), query.getFilter()));
        for (String clause : query.getClauses()) {
            text.append("  - ").append(clause).append('\n');
        }
        text.append(String.format(Locale.ROOT,
                "%nPage limit: %d (page_offset 0)%n", query.getPageLimit()));
        text.append("\nGET URL (percent-encoded; NOT fetched - review before use):\n")
                .append(query.getUrl()).append('\n');
        text.append("\nHonesty block: this slice builds and validates ONLY - no "
                + "network fetch, no TLS, no JSON:API parsing, and no "
                + "provider-capability negotiation (e.g. whether band_gap is "
                + "queryable varies per provider). Credentials in URLs are "
                + "refused; free-form filter grammar is intentionally not "
                + "parsed (building the owned subset beats parsing for "
                + "injection safety). The draft saves/exports only via the "
                + "explicit action; the fetch + response import remain the "
                + "#117 depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "base,%s,validated /v1 root",
                csvCell(query.getNormalizedBase())));
        csv.add(String.format(Locale.ROOT, "filter_clauses,%d,owned subset",
                query.getClauses().size()));
        csv.add(String.format(Locale.ROOT, "page_limit,%d,offset 0", query.getPageLimit()));
        StringBuilder draft = new StringBuilder();
        draft.append("# OPTIMADE query draft (QuantumForge, unfetched - review "
                + "before use)\n");
        draft.append("# base:   ").append(query.getNormalizedBase()).append('\n');
        draft.append("# filter: ").append(query.getFilter()).append('\n');
        draft.append(query.getUrl()).append('\n');
        return new AnalysisReport(label, true, text.toString(), csv, draft.toString());
    }

    /**
     * Roadmap #116 (builder slice): validates a Materials Project mp-api v2
     * base + task ids and composes a GET /materials/summary/ URL for review.
     * The API key is never attached to the URL and never echoed back; the
     * draft writes a file only via the explicit save action.
     */
    private static AnalysisReport analyzeMpQueryDraft(AnalysisParameters params) {
        String label = AnalysisKind.MP_QUERY_DRAFT.getLabel();
        OperationResult<MpApiQueryBuilder.MpQuery> built = MpApiQueryBuilder.build(
                params.getMpBase(), params.getMpMaterialIds(), params.getMpApiKey());
        if (!built.isSuccess() || built.getValue().isEmpty()) {
            return failure(label, "The Materials Project query draft was refused:\n["
                    + built.getCode() + "] " + built.getMessage());
        }
        MpApiQueryBuilder.MpQuery query = built.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "API base (https-only, normalized): %s%n", query.getNormalizedBase()));
        text.append(String.format(Locale.ROOT,
                "Material ids (%d, analyst order preserved exactly): %s%n",
                query.getMaterialIds().size(),
                String.join(", ", query.getMaterialIds())));
        text.append(String.format(Locale.ROOT,
                "API key: %s%n",
                query.isApiKeyProvided()
                        ? "provided (" + params.getMpApiKey().trim().length()
                                + " chars) - travels ONLY in the X-API-KEY "
                                + "header, never in the URL or this report"
                        : "NOT provided - the draft still builds, but the runtime "
                                + "fetch requires an X-API-KEY header"));
        text.append("\nGET URL (not fetched - review before use):\n")
                .append(query.getUrl()).append('\n');
        text.append("\nHonesty block: like the OPTIMADE draft this slice builds "
                + "and validates ONLY - no fetch, no TLS policy, no rate-limit "
                + "handling, no JSON parsing, no _fields projection and no "
                + "POST-based search; route semantics follow the mp-api v2 "
                + "documentation as curated here. The draft saves/exports only "
                + "via the explicit action; the fetch + response import remain "
                + "the #116 depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "base,%s,https validated",
                csvCell(query.getNormalizedBase())));
        csv.add(String.format(Locale.ROOT, "material_ids,%d,order preserved",
                query.getMaterialIds().size()));
        csv.add(String.format(Locale.ROOT, "api_key,%s,header-only",
                query.isApiKeyProvided() ? "provided" : "missing"));
        StringBuilder draft = new StringBuilder();
        draft.append("# Materials Project query draft (QuantumForge, unfetched - "
                + "review before use)\n");
        draft.append("# base: ").append(query.getNormalizedBase()).append('\n');
        draft.append("# ids:  ").append(String.join(",", query.getMaterialIds()))
                .append('\n');
        draft.append("# key:  X-API-KEY header ")
                .append(query.isApiKeyProvided() ? "(provided)" : "(REQUIRED at fetch)")
                .append(" - never in the URL\n");
        draft.append(query.getUrl()).append('\n');
        return new AnalysisReport(label, true, text.toString(), csv, draft.toString());
    }

    /**
     * Roadmap #91 (config-draft slice): validates an SSH target and renders
     * a hardened ssh_config stanza. Password auth is structurally absent
     * (no password field exists); the draft writes a file only via the
     * explicit save action; no connection is attempted.
     */
    private static AnalysisReport analyzeSshConfigDraft(AnalysisParameters params) {
        String label = AnalysisKind.SSH_CONFIG_DRAFT.getLabel();
        OperationResult<SshTargetSpec.SshTarget> validated = SshTargetSpec.validate(
                params.getSshAlias(), params.getSshHost(), params.getSshUser(),
                params.getSshPort(), params.getSshIdentityFile());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The SSH target draft was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        SshTargetSpec.SshTarget target = validated.getValue().get();
        String stanza = target.stanza();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Validated target: %s@%s:%d (alias '%s')%n", target.getUser(),
                target.getHostName(), target.getPort(), target.getAlias()));
        text.append(String.format(Locale.ROOT,
                "Identity file: %s%n",
                target.getIdentityFile().isEmpty()
                        ? "unset - the stanza says so and offers the agent keys "
                                + "honestly"
                        : target.getIdentityFile() + " (quoted fields never "
                                + "needed: unsafe characters were refused)"));
        text.append("\nRendered stanza (also in the draft channel):\n\n").append(stanza);
        // Batch-130 bridge: the draft compiles to the SAME typed config the
        // runtime transport honors - one payload from review to runtime.
        String knownHosts = params.getSshKnownHosts().trim();
        text.append('\n');
        if (knownHosts.isEmpty()) {
            text.append("Runtime bridge: NOT exercised (blank known_hosts input). "
                    + "Feasibility -> identity file: "
                    + (target.getIdentityFile().isEmpty()
                            ? "MISSING (a compile would refuse SSH_IDENTITY_MISSING - "
                                    + "this build's transport has no agent support)"
                            : "present")
                    + "; any compile forces acceptNewHostKeys=false and refuses a blank "
                    + "known_hosts path by design.\n");
        } else {
            quantumforge.operation.OperationResult<quantumforge.ssh.SshConnectionConfig>
                    bridge = target.toConnectionConfig(knownHosts, null);
            if (bridge.isSuccess()) {
                text.append("Runtime bridge: compiled [SSH_BRIDGE_OK] - "
                        + bridge.getMessage()).append('\n');
            } else {
                text.append("Runtime bridge: refused [" + bridge.getCode() + "] "
                        + bridge.getMessage() + "\n");
            }
        }
        text.append("\nHonesty block: password auth is STRUCTURALLY ABSENT - "
                + "no password field exists, and the stanza pins "
                + "PasswordAuthentication no + BatchMode yes. Key-agent setup "
                + "and bastion/proxy chains remain the #91 runtime depth; the "
                + "session transport itself exists (JschSshTransport, bridged "
                + "above); nothing connects from this build, and the stanza "
                + "saves only via the explicit save action.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "alias,%s,validated", csvCell(target.getAlias())));
        csv.add(String.format(Locale.ROOT, "hostname,%s,validated",
                csvCell(target.getHostName())));
        csv.add(String.format(Locale.ROOT, "user,%s,posix logname", csvCell(target.getUser())));
        csv.add(String.format(Locale.ROOT, "port,%d,1..65535", target.getPort()));
        csv.add(String.format(Locale.ROOT, "identity_file,%s,%s",
                csvCell(target.getIdentityFile().isEmpty() ? "(unset)"
                        : target.getIdentityFile()),
                target.getIdentityFile().isEmpty() ? "agent-key honesty"
                        : "expansion-guard passed"));
        csv.add("password_field,absent,by design");
        return new AnalysisReport(label, true, text.toString(), csv, stanza);
    }

    /**
     * Roadmap #92 (plan slice): one reviewed SFTP staging step, fail-closed.
     * The local file must live inside THIS project (never absolute, never
     * climbing), and its SHA-256 + byte size are pinned at draft time so the
     * post-transfer verify has a checksum target that cannot drift. The
     * remote side is a literal absolute POSIX FILE path - no shell, no
     * expansion, no directory markers, no '..' climbs. Overwrite posture is
     * explicit (REFUSE-IF-EXISTS unless the analyst deliberately flips it).
     * Nothing transfers; the plan renders through the draft channel and saves
     * only via the explicit save action.
     */
    private static AnalysisReport analyzeSftpTransferPlan(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.SFTP_TRANSFER_PLAN.getLabel();
        File projectDir = project.getDirectory();
        if (projectDir == null || !projectDir.isDirectory()) {
            return failure(label, "The project directory is unavailable - a "
                    + "staging plan is only ever drafted against a real, "
                    + "on-disk project (nothing is staged from memory).");
        }
        OperationResult<SftpTransferPlan.TransferStep> prepared =
                SftpTransferPlan.prepare(projectDir.toPath(), params.getSftpLocalName(),
                        params.getSftpRemotePath(), params.isSftpOverwriteAllowed());
        if (!prepared.isSuccess() || prepared.getValue().isEmpty()) {
            return failure(label, "The staging plan was refused:\n["
                    + prepared.getCode() + "] " + prepared.getMessage());
        }
        SftpTransferPlan.TransferStep step = prepared.getValue().get();
        String plan = step.render();
        StringBuilder text = new StringBuilder();
        text.append("Project: ").append(projectDir.getAbsolutePath()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Local file: %s (%d bytes), sha256 pinned at draft time:%n  %s%n",
                step.getLocalName(), step.getLocalBytes(), step.getLocalSha256()));
        text.append("Remote file: ").append(step.getRemotePath()).append('\n');
        text.append("Overwrite posture: ").append(step.isOverwriteAllowed()
                ? "ALLOWED (explicit analyst choice, printed in uppercase)"
                : "REFUSE-IF-EXISTS (default - a remote hit aborts the step)")
                .append('\n');
        text.append("\nPlan draft (also in the draft channel):\n\n").append(plan);
        // Batch-133 bridge: the drafted absolute path compiles to the
        // staging-root-relative path the runtime transfer API consumes - by
        // CONFINEMENT, never re-rooting - and the verified-upload semantics
        // (temp-upload -> sha256 verify -> atomic rename) now exist.
        String root = params.getSftpStagingRoot().trim();
        text.append('\n');
        if (root.isEmpty()) {
            text.append("Runtime bridge: NOT exercised (blank staging-root input). "
                    + "Rule: the drafted absolute remote path must sit strictly under "
                    + "your staging root; the bridge compiles it to the "
                    + "staging-relative form CONFINEMENT-or-refuse - it never silently "
                    + "re-roots a reviewed path.\n");
        } else {
            OperationResult<String> bridge = step.relativizeUnder(root);
            if (bridge.isSuccess() && bridge.getValue().isPresent()) {
                text.append(String.format(Locale.ROOT,
                        "Runtime bridge: [SFTP_BRIDGE_OK] %s%n"
                                + "Verified-upload semantics that consume it"
                                + " (SSHFileTransfer.uploadVerifiedResult): temp-upload to"
                                + " <path>.qftmp, mandatory sha256 verify (mismatch removes"
                                + " ONLY our temp - nothing renames into place), atomic"
                                + " rename; overwrite posture is pre-checked"
                                + " (REFUSE-IF-EXISTS aborts before any byte moves).",
                        bridge.getMessage()));
            } else {
                text.append(String.format(Locale.ROOT,
                        "Runtime bridge: refused [%s] %s (a FINDING on the staging-root"
                                + " mapping; the step itself validated fine).%n",
                        bridge.getCode(), bridge.getMessage()));
            }
        }
        text.append("Boundary: bulk download and remote deletion stay disabled by design "
                + "(SSH_BULK_DOWNLOAD_UNAVAILABLE / SSH_DELETE_UNAVAILABLE). A single-file"
                + " VERIFIED download now exists as a connection-gated runtime"
                + " (SSHFileTransfer.downloadVerifiedResult: remote sha256 source"
                + " pre-check refuses a wrong/missing/absent-hash source BEFORE any byte"
                + " moves, bytes sink only to <local>.qftmp, local sha256 post-verify"
                + " catches corruption in flight - mismatch removes only our temp, then"
                + " an atomic-or-stated-plain rename), but NOTHING transfers from this"
                + " plan channel.\n");
        text.append("\nHonesty block: NOTHING transfers from this build. The "
                + "plan pins the integrity target (sha256 + size) NOW, and the "
                + "declared verify-after-transfer step is MANDATORY for any "
                + "future runtime. Execution must ride the host-key-checked "
                + "SSH channel from the SSH_CONFIG_DRAFT; SFTP session "
                + "plumbing and resumable transfers are the remaining #92 "
                + "runtime depth (the verified download direction landed as a "
                + "runtime in the same batch series as this plan kind). The "
                + "draft saves only via the explicit save action.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "local_file,%s,project-relative",
                csvCell(step.getLocalName())));
        csv.add(String.format(Locale.ROOT, "local_bytes,%d,pinned at draft time",
                step.getLocalBytes()));
        csv.add(String.format(Locale.ROOT, "local_sha256,%s,draft-time integrity target",
                step.getLocalSha256()));
        csv.add(String.format(Locale.ROOT, "remote_path,%s,literal absolute POSIX",
                csvCell(step.getRemotePath())));
        csv.add(String.format(Locale.ROOT, "overwrite,%s,explicit",
                step.isOverwriteAllowed() ? "ALLOWED" : "REFUSE-IF-EXISTS"));
        csv.add("verify_after,sha256-mandatory,no silent acceptance");
        return new AnalysisReport(label, true, text.toString(), csv, plan);
    }

    /**
     * Roadmap #93 (draft slice): typed SLURM submit-script drafting. Core
     * directives are OWNED and validated (never free-form concatenation); the
     * single payload line is verbatim analyst content, commented as such and
     * guarded against directive smuggling and silent multi-line join. Nothing
     * is submitted - the script renders through the draft channel and saves
     * only via the explicit save action.
     */
    private static AnalysisReport analyzeSlurmScriptDraft(AnalysisParameters params) {
        String label = AnalysisKind.SLURM_SCRIPT_DRAFT.getLabel();
        OperationResult<SlurmScriptBuilder.SlurmDraft> validated =
                SlurmScriptBuilder.validate(params.getSlurmJobName(),
                        params.getSlurmPartition(), params.getSlurmNodes(),
                        params.getSlurmNtasks(), params.getSlurmWalltime(),
                        params.getSlurmModules(), params.getSlurmCommand());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The SLURM script draft was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        SlurmScriptBuilder.SlurmDraft draft = validated.getValue().get();
        String script = draft.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Job '%s': %d node(s) x %d task(s), walltime %s, partition %s, "
                        + "modules %d, payload 1 reviewed line.%n",
                draft.getJobName(), draft.getNodes(), draft.getNtasks(),
                draft.getWalltime(),
                draft.getPartition().isEmpty() ? "(omitted - cluster default applies)"
                        : "'" + draft.getPartition() + "'",
                draft.getModules().size()));
        text.append("\nRendered script (also in the draft channel):\n\n").append(script);
        text.append("\nHonesty block: NOTHING is submitted by this build. Every directive "
                + "was validated against an owned grammar/range (job-name, partition, "
                + "nodes 1..1024, ntasks 1..65536, strict HH:MM:SS with a 7-day cap, "
                + "module tokens without whitespace/shell characters); the payload is "
                + "exactly your reviewed line - guarded but NOT interpreted or "
                + "constructed. Job-ID parse-back, the scheduler state machine (#95), "
                + "site profiles (#94), PBS/PJM/SGE adapters and multi-line payloads "
                + "remain the #93 runtime depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "job_name,%s,owned grammar",
                csvCell(draft.getJobName())));
        csv.add(String.format(Locale.ROOT, "partition,%s,%s",
                csvCell(draft.getPartition().isEmpty() ? "(omitted)" : draft.getPartition()),
                draft.getPartition().isEmpty() ? "honest omission" : "owned grammar"));
        csv.add(String.format(Locale.ROOT, "nodes,%d,1..1024", draft.getNodes()));
        csv.add(String.format(Locale.ROOT, "ntasks,%d,1..65536", draft.getNtasks()));
        csv.add(String.format(Locale.ROOT, "walltime,%s,strict HH:MM:SS + 7d cap",
                draft.getWalltime()));
        csv.add(String.format(Locale.ROOT, "modules,%d,%s", draft.getModules().size(),
                draft.getModules().isEmpty() ? "none declared - not assumed"
                        : "each token grammar-checked"));
        csv.add(String.format(Locale.ROOT, "payload_lines,1,verbatim analyst content"));
        return new AnalysisReport(label, true, text.toString(), csv, script);
    }

    /**
     * Roadmap #37 (plan slice): schedules a k-mesh CONVERGENCE LADDER for the
     * live project cell. Rungs are validated (2..8, never coarsening, order
     * preserved, explicit 0/1 shift - never defaulted); every rung is priced
     * with the already-tested QEKpointMeshAdvisor (2pi convention, Angstrom^-1),
     * and the refinement factor vs the previous rung is reported. The honesty
     * boundary is hard: energies/forces are NOT evaluated, the delta-E
     * stopping criterion is user-set runtime work, and this plan NEVER
     * declares convergence.
     */
    private static AnalysisReport analyzeKmeshConvergencePlan(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.KMESH_CONVERGENCE_PLAN.getLabel();
        Cell cell = project.getCell();
        double[][] lattice = cell == null ? null : cell.copyLattice();
        if (lattice == null) {
            return failure(label, "The project has no atomic cell - k-mesh spacing "
                    + "arithmetic needs the live lattice and is not estimated from a "
                    + "placeholder geometry.");
        }
        OperationResult<KMeshConvergenceLadder.Ladder> parsed =
                KMeshConvergenceLadder.parse(params.getKmeshLadder(), params.getKmeshOffset());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "The k-mesh ladder was refused:\n[" + parsed.getCode()
                    + "] " + parsed.getMessage());
        }
        KMeshConvergenceLadder.Ladder ladder = parsed.getValue().get();
        int[] offset = ladder.getOffset();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Shift %d %d %d (prompted explicitly - 1 = half-step shift, 0 = "
                        + "Gamma-inclusive per direction; semantics are never "
                        + "defaulted).%n%n",
                offset[0], offset[1], offset[2]));
        text.append(String.format(Locale.ROOT,
                "%-4s %-12s %-18s %-13s %-16s%n", "#", "mesh n1 n2 n3",
                "worst spacing (A^-1)", "grid points", "refinement vs prev"));
        List<String> csv = new ArrayList<>();
        csv.add("rung,n1,n2,n3,worst_spacing_inv_ang,total_grid_points,"
                + "refinement_factor_vs_prev");
        double previousWorst = Double.NaN;
        int rungIndex = 0;
        for (int[] rung : ladder.getRungs()) {
            rungIndex += 1;
            OperationResult<QEKpointMeshAdvisor.MeshReport> assessed =
                    QEKpointMeshAdvisor.assess(lattice, rung, offset);
            if (!assessed.isSuccess() || assessed.getValue().isEmpty()) {
                return failure(label, "Rung " + rungIndex + " mesh assessment failed "
                        + "closed: [" + assessed.getCode() + "] " + assessed.getMessage());
            }
            QEKpointMeshAdvisor.MeshReport report = assessed.getValue().get();
            double worst = 0.0;
            for (QEKpointMeshAdvisor.DirectionReport direction : report.getDirections()) {
                worst = Math.max(worst, direction.getSpacingInvAng());
            }
            int points = report.getTotalGridPoints();
            boolean hasPrev = rungIndex > 1;
            double refinement = hasPrev ? previousWorst / worst : Double.NaN;
            text.append(String.format(Locale.ROOT,
                    "%-4d %3d %3d %3d     %-18.6f %-13d %-16s%n", rungIndex,
                    rung[0], rung[1], rung[2], worst, points,
                    hasPrev ? String.format(Locale.ROOT, "x%.6f", refinement) : "-"));
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%.6f,%d,%s", rungIndex,
                    rung[0], rung[1], rung[2], worst, points,
                    hasPrev ? String.format(Locale.ROOT, "%.6f", refinement) : ""));
            previousWorst = worst;
        }
        text.append("\nHonesty block: this plan schedules rungs and prices their "
                + "sampling - it NEVER declares convergence. Total energies, forces and "
                + "the delta-E/delta-F stopping threshold are runtime results (run every "
                + "rung, then compare consecutive values against YOUR criterion; the "
                + "workflow stops honestly ONLY when the observed change meets it). "
                + "Spacings use the 2pi convention in Angstrom^-1 (the same arithmetic "
                + "the KMESH_QUALITY advisor cross-checks against the BZ facet "
                + "distance); rung order is exactly as prompted - nothing is re-sorted, "
                + "and a coarsening ladder refuses rather than inverting the study.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #94 (draft slice): a site-profile draft with typed
     * scheduler/launcher enums and owned value grammars. The render is an
     * OWNED qf-site-profile v1 key=value block, explicitly labeled NOT-YAML;
     * unset optional keys render as honest omission comments rather than
     * invented defaults. No submit path consumes profiles from this build.
     */
    private static AnalysisReport analyzeSiteProfileDraft(AnalysisParameters params) {
        String label = AnalysisKind.SITE_PROFILE_DRAFT.getLabel();
        OperationResult<SiteProfileSpec.SiteProfile> validated =
                SiteProfileSpec.validate(params.getSiteCluster(), params.getSiteScheduler(),
                        params.getSiteLauncher(), params.getSitePartition(),
                        params.getSiteAccount(), params.getSiteScratchDir(),
                        params.getSiteMaxNodes(), params.getSiteModules());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The site-profile draft was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        SiteProfileSpec.SiteProfile profile = validated.getValue().get();
        String block = profile.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Cluster '%s': scheduler=%s, launcher=%s, max_nodes=%d, modules=%d, "
                        + "partition %s, account %s.%n",
                profile.getCluster(), profile.getScheduler(), profile.getLauncher(),
                profile.getMaxNodes(), profile.getModules().size(),
                profile.getDefaultPartition().isEmpty() ? "(omitted - honest comment)"
                        : "'" + profile.getDefaultPartition() + "'",
                profile.getAccount().isEmpty() ? "(omitted - honest comment)"
                        : "'" + profile.getAccount() + "'"));
        text.append("Scratch root: ").append(profile.getScratchDir()).append('\n');
        text.append("\nRendered profile block (also in the draft channel):\n\n").append(block);
        text.append("\nHonesty block: this draft changes NOTHING about how jobs run "
                + "today - no submit path reads profiles from this build. Scheduler and "
                + "launcher are TYPED enums (free-form strings refuse); the scratch root "
                + "is required and literal (no expansion characters); unset optionals "
                + "render as omission comments, not defaults. The #94 runtime depth: "
                + "site-admin YAML with schema validation, one portable project "
                + "targeting multiple clusters, and policy limits. The object bridge "
                + "to the loader-domain profile lands below as the runtime bridge trail.\n");
        OperationResult<quantumforge.hpc.SiteProfile> bridged = profile.toHpcProfile();
        text.append("\nRuntime bridge (draft -> hpc.SiteProfile object, headless compile):\n");
        if (bridged.isSuccess() && bridged.getValue().isPresent()) {
            quantumforge.hpc.SiteProfile hpc = bridged.getValue().get();
            text.append(String.format(Locale.ROOT,
                    "  [%s] id='%s', scheduler=%s (canonical via the single alias owner), "
                            + "mpi_launcher=%s, scratch_root=%s, modules=%d.%n",
                    bridged.getCode(), hpc.getId(), hpc.getScheduler(), hpc.getMpiLauncher(),
                    hpc.getScratchRoot(), hpc.getModules().size()));
            text.append("  Adapter resolution proof: the bridged profile resolves to the '"
                    + hpc.schedulerAdapter().name()
                    + "' typed adapter through the SchedulerAdapters registry - including pjm, "
                    + "first-class since the scheduler-identity fix.\n");
            text.append("  Not bridged, deliberately: staging_root stays BLANK (the draft "
                    + "has none - staged uploads still refuse until the site's profile "
                    + "supplies one; scratch is not staging), and max_nodes="
                    + profile.getMaxNodes()
                    + " stays an advisory ceiling, NOT a default-nodes allocation "
                    + "(a ceiling is not an allocation).\n");
        } else {
            text.append("  Bridging was refused as a FINDING (the validated draft above "
                    + "still stands):\n  [" + bridged.getCode() + "] "
                    + bridged.getMessage() + "\n");
        }
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "cluster,%s,owned grammar",
                csvCell(profile.getCluster())));
        csv.add(String.format(Locale.ROOT, "scheduler,%s,typed enum",
                profile.getScheduler()));
        csv.add(String.format(Locale.ROOT, "launcher,%s,typed enum - pairing not judged",
                profile.getLauncher()));
        csv.add(String.format(Locale.ROOT, "default_partition,%s,%s",
                csvCell(profile.getDefaultPartition().isEmpty() ? "(omitted)"
                        : profile.getDefaultPartition()),
                profile.getDefaultPartition().isEmpty() ? "honest omission"
                        : "owned grammar"));
        csv.add(String.format(Locale.ROOT, "account,%s,%s",
                csvCell(profile.getAccount().isEmpty() ? "(omitted)" : profile.getAccount()),
                profile.getAccount().isEmpty() ? "honest omission" : "owned grammar"));
        csv.add(String.format(Locale.ROOT, "scratch_dir,%s,literal absolute POSIX%s",
                csvCell(profile.getScratchDir()),
                profile.isScratchTrimmed() ? " (trailing slash normalized)" : ""));
        csv.add(String.format(Locale.ROOT, "max_nodes,%d,ceiling recorded - enforced by "
                + "the submit path", profile.getMaxNodes()));
        csv.add(String.format(Locale.ROOT, "modules,%d,%s", profile.getModules().size(),
                profile.getModules().isEmpty() ? "none declared - not assumed"
                        : "tokens grammar-checked"));
        csv.add(String.format(Locale.ROOT, "bridge,%s,%s", csvCell(bridged.getCode()),
                bridged.isSuccess()
                        ? "hpc-profile compiled - staging blank; ceiling not allocated"
                        : "refusal recorded as finding - draft stands"));
        return new AnalysisReport(label, true, text.toString(), csv, block);
    }

    /**
     * Roadmap #50 (draft slice): typed neb.x &PATH namelist draft. Only the
     * namelist arithmetic is owned here - intermediate image interpolation,
     * engine image partitioning, the stage parser and the movie remain the
     * #50 editor/runtime depth. no-CI/highest/spin are the usable CI schemes;
     * 'manual' refuses ACTIONABLY (blind indexing would be ceremonial), and
     * any CI needs >= 3 images (climbing needs an interior image). The
     * render carries an explicit numbered-image checklist so images can never
     * reorder silently.
     */
    private static AnalysisReport analyzeNebInputDraft(AnalysisParameters params) {
        String label = AnalysisKind.NEB_INPUT_DRAFT.getLabel();
        OperationResult<NebInputPlanner.NebDraft> validated =
                NebInputPlanner.validate(params.getNebNumImages(), params.getNebNstepPath(),
                        params.getNebOptScheme(), params.getNebCiScheme(),
                        params.getNebKMin(), params.getNebKMax(), params.getNebDs(),
                        params.getNebPathThr());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The neb.x &PATH draft was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        NebInputPlanner.NebDraft draft = validated.getValue().get();
        String namelist = draft.draft();
        String checklist = draft.checklist();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Path: %d image(s) (end points included), %d path steps, "
                        + "opt_scheme='%s', CI_scheme='%s'.%n",
                draft.getNumOfImages(), draft.getNstepPath(), draft.getOptScheme(),
                draft.getCiScheme()));
        text.append(String.format(Locale.ROOT,
                "Springs [k_min=%.6f .. k_max=%.6f] a.u., ds=%.6f a.u., "
                        + "path_thr=%.6f a.u.%n",
                draft.getKMin(), draft.getKMax(), draft.getDs(), draft.getPathThr()));
        text.append("\n&PATH draft (also in the draft channel):\n\n").append(namelist);
        text.append('\n').append(checklist);
        text.append("\nHonesty block: this slice owns the &PATH namelist ONLY. "
                + "Intermediate images are NOT interpolated here (the #50 editor "
                + "generates first/intermediate/last ATOMIC_POSITIONS blocks); the "
                + "-nimage engine partitioning MUST equal num_of_images; the stage "
                + "parser, path-movie and image-energy view are the remaining "
                + "depth. No keyword carried an invented default: blank numeric "
                + "fields refused at the prompt. The draft saves only via the "
                + "explicit save action.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "num_of_images,%d,end-points included - must "
                + "match -nimage", draft.getNumOfImages()));
        csv.add(String.format(Locale.ROOT, "nstep_path,%d,path-optimization steps",
                draft.getNstepPath()));
        csv.add(String.format(Locale.ROOT, "opt_scheme,%s,typed enum",
                draft.getOptScheme()));
        csv.add(String.format(Locale.ROOT, "CI_scheme,%s,%s", draft.getCiScheme(),
                draft.getCiScheme().equals("no-ci") ? "no climbing image"
                        : "interior image required (images>=3)"));
        csv.add(String.format(Locale.ROOT, "k_min,%.6f,a.u. - k_min<=k_max enforced",
                draft.getKMin()));
        csv.add(String.format(Locale.ROOT, "k_max,%.6f,a.u.", draft.getKMax()));
        csv.add(String.format(Locale.ROOT, "ds,%.6f,a.u.", draft.getDs()));
        csv.add(String.format(Locale.ROOT, "path_thr,%.6f,a.u.", draft.getPathThr()));
        csv.add("intermediate_images,not-generated,editor-slice depth");
        return new AnalysisReport(label, true, text.toString(), csv,
                namelist + "\n# " + checklist.replace("\n", "\n# "));
    }

    /**
     * Roadmap #97 (plan slice): review plan for cancelling ONE scheduler job.
     * The scheduler is typed, the job id follows an owned per-scheduler
     * grammar (array syntax SLURM-only), and the confirmation must retype the
     * id EXACTLY - compared untrimmed, because silently forgiving whitespace
     * inside a destructive action is worse than refusing. The render pins
     * the ONLY success signal (post-cancel scheduler query shows the job
     * gone) and states the forbidden alternatives (kill-by-name, directory
     * deletion). NOTHING is cancelled from this build.
     */
    private static AnalysisReport analyzeJobCancelPlan(AnalysisParameters params) {
        String label = AnalysisKind.JOB_CANCEL_PLAN.getLabel();
        OperationResult<JobCancelPlan.CancelPlan> validated = JobCancelPlan.validate(
                params.getCancelScheduler(), params.getCancelJobId(),
                params.getCancelConfirm());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The cancellation plan was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        JobCancelPlan.CancelPlan plan = validated.getValue().get();
        String block = plan.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Cancelling (review-only) job %s on %s via '%s'.%n",
                plan.getJobId(), plan.getScheduler(), plan.getCommand()));
        text.append("\nReview block (also in the draft channel):\n\n").append(block);
        text.append("\nHonesty block: NOTHING was cancelled - there is no SSH channel "
                + "from this build. The command above is a review line; execution "
                + "requires the runtime channel AND your re-review. The classic cancel "
                + "errors are stated as forbidden lines (kill by process name; delete "
                + "the job's directory 'to clean up'). After any execution, the "
                + "scheduler query is the ONLY success signal, and the #95 state "
                + "machine must then record CANCELLED - never carpet-FAILED - citing "
                + "the verifying query.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "scheduler,%s,typed enum", plan.getScheduler()));
        csv.add(String.format(Locale.ROOT, "job_id,%s,owned per-scheduler grammar",
                csvCell(plan.getJobId())));
        csv.add(String.format(Locale.ROOT, "cancel_command,%s,review line only",
                csvCell(plan.getCommand())));
        csv.add("confirmation,retyped-exactly,compared untrimmed");
        csv.add("success_signal,scheduler-query-shows-absent,only signal accepted");
        return new AnalysisReport(label, true, text.toString(), csv, block);
    }

    /**
     * Roadmap #97 (runtime slice): census + per-id grammar audit of the typed
     * scheduler-adapter registry. The registry is the ONLY name resolution
     * path (blank/unknown refuse - there is deliberately no default adapter),
     * and a per-id verdict is produced by the ADAPTER itself with its refusal
     * quoted verbatim, so the review channel can never drift from the runtime
     * channel. Every command rendered is a REVIEW line: nothing is submitted,
     * cancelled or queried from this build.
     */
    private static AnalysisReport analyzeSchedulerAdapterAudit(AnalysisParameters params) {
        String label = AnalysisKind.SCHEDULER_ADAPTER_AUDIT.getLabel();
        String requested = params.getSchedulerAuditName().trim();
        String jobId = params.getSchedulerAuditJobId().trim();
        SchedulerAdapter focus = null;
        if (!requested.isEmpty()) {
            Optional<SchedulerAdapter> resolved = SchedulerAdapters.forName(requested);
            if (resolved.isEmpty()) {
                return failure(label, "scheduler '" + requested
                        + "' is not in the typed registry ("
                        + SchedulerAdapters.supportedNames()
                        + ") - there is deliberately no default adapter."
                        + "\n[SCHEDULER_NAME]");
            }
            focus = resolved.get();
        }
        StringBuilder text = new StringBuilder();
        text.append("Scheduler-adapter registry census (blank/unknown scheduler names"
                + " refuse - NO default):\n\n");
        List<String> csv = new ArrayList<>();
        csv.add("section,scheduler,verdict,detail");
        for (SchedulerAdapter adapter : SchedulerAdapters.all()) {
            // "0" is decimal and passes every owned grammar; only command HEADS
            // are rendered in the census so no fake full command exists.
            String submitHead = adapter.submitCommand("<remote-script>")[0];
            String cancelHead = adapter.cancelCommand("0")[0];
            String statusHead = adapter.statusCommand("0")[0];
            text.append(String.format(Locale.ROOT,
                    "  %-6s submit=%s  cancel=%s  status=%s%n",
                    adapter.name(), submitHead, cancelHead, statusHead));
            text.append(String.format(Locale.ROOT,
                    "         grammar owner: the %s adapter itself (shared by the cancel"
                            + " plan)%n", adapter.name()));
            csv.add(String.format(Locale.ROOT, "census,%s,registered,submit=%s|cancel=%s|status=%s",
                    adapter.name(), submitHead, cancelHead, statusHead));
        }
        if (focus != null) {
            text.append(String.format(Locale.ROOT, "%nFocus scheduler: %s%n", focus.name()));
            if (jobId.isEmpty()) {
                text.append("  job id = BLANK (explicit) -> census only; no per-id"
                        + " verdict requested\n");
                csv.add(String.format(Locale.ROOT, "focus,%s,census-only,blank job id is explicit, no verdict",
                        focus.name()));
            } else {
                try {
                    String cancel = String.join(" ", focus.cancelCommand(jobId));
                    String status = String.join(" ", focus.statusCommand(jobId));
                    text.append(String.format(Locale.ROOT,
                            "  verdict for job id '%s': GRAMMAR-OK - verdict owned by the"
                                    + " %s adapter%n", jobId, focus.name()));
                    text.append("  cancel (review only): ").append(cancel).append('\n');
                    text.append("  status (review only): ").append(status).append('\n');
                    csv.add(String.format(Locale.ROOT, "focus,%s,GRAMMAR-OK,%s",
                            focus.name(), csvCell(jobId)));
                } catch (IllegalArgumentException refusal) {
                    text.append(String.format(Locale.ROOT,
                            "  verdict for job id '%s': REFUSED by the %s adapter - %s%n",
                            jobId, focus.name(), refusal.getMessage()));
                    csv.add(String.format(Locale.ROOT, "focus,%s,REFUSED,%s",
                            focus.name(), csvCell(jobId + " -> " + refusal.getMessage())));
                }
            }
        }
        text.append("\nHonesty block: every command above is a REVIEW line - nothing is"
                + " submitted,\ncancelled or queried from this build. Execution requires the"
                + " runtime SSH\nchannel AND your re-review, and a cancellation is only ever"
                + " proven by the\nstatus query showing the job gone (never by a clean exit code"
                + " alone).\n");
        text.append("\nProvenance (batch-126 correction): the Fujitsu PJM cancel command is"
                + " 'pjdel'\nand the per-job status query is 'pjstat -S' - an earlier cancel-plan"
                + " draft\nrendered 'pdel' and named 'pjobs'. The plan now takes both command"
                + " lines\nfrom the adapter itself (see JOB_CANCEL_PLAN), so one owner holds the"
                + " grammar.\n");
        List<String> provenance = new ArrayList<>();
        provenance.add("Fujitsu PJM command grammar (pjsub / pjdel / pjstat -S): FUJITSU"
                + " Software Technical Computing Suite manual J2UL-2544 and the Kyushu"
                + " University Genkai 'Job Usage' documentation.");
        provenance.add("Grammar ownership: quantumforge.hpc.SchedulerAdapters registry plus"
                + " each adapter's owned job-id grammar; remote.JobCancelPlan holds no regex"
                + " copy (no-drift architecture, batch 126).");
        provenance.add("Nothing in this audit contacted any scheduler; all verdicts are local"
                + " grammar checks.");
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #96 (runtime slice): states BEFORE any channel exists exactly
     * what the remote monitor WILL do - the backoff arithmetic, the verdict
     * boundary (transport failure is never a status verdict), the
     * adapter-owned absence needles, and the guard-owned signal mapping. All
     * census rows are generated by PROBING the owning classes (guard table,
     * adapter needles, monitor constants), so this audit can never drift from
     * the runtime it describes. Nothing contacts any scheduler.
     */
    private static AnalysisReport analyzeJobMonitorAudit(AnalysisParameters params) {
        String label = AnalysisKind.JOB_MONITOR_AUDIT.getLabel();
        String requested = params.getMonitorScheduler().trim();
        String jobId = params.getMonitorJobId().trim();
        SchedulerAdapter focus = null;
        if (!requested.isEmpty()) {
            Optional<SchedulerAdapter> resolved = SchedulerAdapters.forName(requested);
            if (resolved.isEmpty()) {
                return failure(label, "scheduler '" + requested
                        + "' is not in the typed registry ("
                        + SchedulerAdapters.supportedNames()
                        + ") - there is deliberately no default adapter."
                        + "\n[MONITOR_SCHEDULER]");
            }
            focus = resolved.get();
        }
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        csv.add("section,scheduler,item,verdict");
        text.append("Remote monitor runtime audit - the contract the runtime enforces,"
                + " stated\nwith zero scheduler contact:\n\n");
        text.append(String.format(Locale.ROOT,
                "Poll policy (owned by RemoteJobMonitor): first interval %.3f s and cap"
                        + " %.3f s by default;%n"
                        + "  every interval is floored at %d ms; UNCHANGED polls grow"
                        + " linearly by +initial;%n"
                        + "  a poll that maps a NEW state resets the interval to initial;"
                        + " transport-error polls%n"
                        + "  back off x2; both growth paths are capped. A poll mapping"
                        + " COMPLETED/FAILED/CANCELLED%n"
                        + "  stops the loop. UNKNOWN is deliberately NOT terminal for"
                        + " monitoring (a reconciliation%n"
                        + "  keeps polling) - the #95 state machine's own rule counts"
                        + " UNKNOWN as terminal; the%n"
                        + "  difference is intentional and stated.%n%n",
                RemoteJobMonitor.DEFAULT_INITIAL.toMillis() / 1000.0,
                RemoteJobMonitor.DEFAULT_MAX_DELAY.toMillis() / 1000.0,
                RemoteJobMonitor.MIN_POLL_MS));
        csv.add(String.format(Locale.ROOT, "policy,all,defaults,initial=%.3fs|cap=%.3fs|floor=%dms",
                RemoteJobMonitor.DEFAULT_INITIAL.toMillis() / 1000.0,
                RemoteJobMonitor.DEFAULT_MAX_DELAY.toMillis() / 1000.0,
                RemoteJobMonitor.MIN_POLL_MS));

        text.append("Verdict boundary (owned by the transport contract + adapter"
                + " needles):\n");
        text.append("  SSH_EXEC_FAILED + empty stdout + the scheduler's DOCUMENTED stderr"
                + " needle\n    -> MONITOR_GONE (terminal; the record reconciles to"
                + " UNKNOWN with the needle cited)\n");
        text.append("  SSH_EXEC_FAILED without a pinned needle -> MONITOR_QUERY_UNREADABLE"
                + "\n    (no transition; polling continues; never declared gone)\n");
        text.append("  SSH_NOT_CONNECTED / SSH_EXEC_ERROR -> MONITOR_ERROR\n"
                + "    (a TRANSPORT failure is never a status verdict and never a"
                + " transition)\n");
        text.append("  The cancel runtime mirrors this: JobCancellation declares CANCELLED"
                + " ONLY on\n    needle-verified absence or the guard-owned"
                + " cancel-in-progress signal; every other\n    verification shape is"
                + " CANCEL_UNVERIFIED and the job is NOT declared cancelled.\n\n");
        csv.add("boundary,all,SSH_EXEC_FAILED+needle,MONITOR_GONE");
        csv.add("boundary,all,SSH_EXEC_FAILED-no-needle,MONITOR_QUERY_UNREADABLE");
        csv.add("boundary,all,transport-failure,MONITOR_ERROR never-a-verdict");
        csv.add("boundary,all,cancel-verification,CANCELLED-only-on-verified-absence");

        // The signal census is PROBED from the owning guard table, never
        // re-typed here; likewise the needle census is probed from the
        // adapters. Zero duplicated semantics - zero drift surface.
        List<String> battery = List.of("PD", "R", "CF", "CG", "CD", "F", "NF", "TO",
                "BF", "DL", "OOM", "CA", "PR", "Q", "H", "W", "S", "T", "E", "X",
                "ACC", "QUE", "HLD", "RUN", "EXT", "CCL", "QW", "HQW", "DR", "DT",
                "EQW", "ZZ");
        List<String> needleProbes = List.of(
                "slurm_load_jobs error: Invalid job id specified",
                "qstat: Unknown Job Id 1",
                "Following jobs do not exist: 1",
                "connection reset by peer");
        List<SchedulerAdapter> census = focus == null
                ? SchedulerAdapters.all() : List.of(focus);
        text.append("Signal-mapping census (probed from the owning JobStateGuard table;"
                + "\nthe monitor delegates single-token outputs there; multi-token dumps"
                + " fall back to\nthe LABELED legacy substring pass):\n");
        for (SchedulerAdapter adapter : census) {
            StringBuilder row = new StringBuilder();
            int recognized = 0;
            for (String code : battery) {
                OperationResult<JobStateGuard.State> mapped =
                        JobStateGuard.mapSignal(adapter.name(), code);
                if (mapped.isSuccess() && mapped.getValue().isPresent()
                        && !"JOBSTATE_UNKNOWN_SIGNAL".equals(mapped.getCode())) {
                    if (row.length() > 0) {
                        row.append(", ");
                    }
                    row.append(code).append('=').append(mapped.getValue().get().name());
                    recognized++;
                    csv.add(String.format(Locale.ROOT, "signal,%s,%s,%s",
                            adapter.name(), code, mapped.getValue().get().name()));
                }
            }
            text.append(String.format(Locale.ROOT, "  %-5s %s%n", adapter.name(),
                    recognized == 0 ? "(no single-token signals recognized)"
                            : row.toString()));
            text.append(String.format(Locale.ROOT,
                    "       %d code(s) recognized; every other token maps to UNKNOWN"
                            + " honestly, never guessed%n", recognized));
        }
        text.append('\n');
        text.append("Absence-needle census (probed from the adapters themselves):\n");
        for (SchedulerAdapter adapter : census) {
            StringBuilder accepted = new StringBuilder();
            for (String probe : needleProbes) {
                if (adapter.isJobAbsent(probe)) {
                    if (accepted.length() > 0) {
                        accepted.append(" | ");
                    }
                    accepted.append('\'').append(probe).append('\'');
                }
            }
            if (accepted.length() == 0) {
                text.append(String.format(Locale.ROOT,
                        "  %-5s NO needle pinned - declared fail-closed: every status"
                                + " failure stays%n       MONITOR_QUERY_UNREADABLE,"
                                + " never gone%n", adapter.name()));
                csv.add(String.format(Locale.ROOT, "needle,%s,none,fail-closed",
                        adapter.name()));
            } else {
                text.append(String.format(Locale.ROOT, "  %-5s accepts ONLY stderr"
                        + " carrying: %s%n", adapter.name(), accepted));
                csv.add(String.format(Locale.ROOT, "needle,%s,%s,pinned",
                        adapter.name(), csvCell(accepted.toString())));
            }
            if (adapter.isJobAbsent("connection reset by peer")) {
                // Self-audit tripwire: a transport complaint must NEVER be an
                // absence verdict for any adapter. Reported, not hidden.
                text.append("       TRIPWIRE: the transport-complaint probe was accepted"
                        + " - this adapter's needle is too broad!\n");
                csv.add(String.format(Locale.ROOT, "needle,%s,TRIPWIRE,accepted transport complaint",
                        adapter.name()));
            }
        }
        if (focus != null && !jobId.isEmpty()) {
            text.append('\n');
            try {
                String status = String.join(" ", focus.statusCommand(jobId));
                text.append(String.format(Locale.ROOT,
                        "Status command for job id '%s' (review line only - not run):"
                                + "%n  %s%n", jobId, status));
                csv.add(String.format(Locale.ROOT, "focus,%s,status-command,%s",
                        focus.name(), csvCell(status + " <- id " + jobId)));
            } catch (IllegalArgumentException refusal) {
                text.append(String.format(Locale.ROOT,
                        "Job id '%s' was REFUSED by the %s adapter - %s%n",
                        jobId, focus.name(), refusal.getMessage()));
                csv.add(String.format(Locale.ROOT, "focus,%s,REFUSED,%s",
                        focus.name(), csvCell(jobId + " -> " + refusal.getMessage())));
            }
        }
        text.append("\nHonesty block: NOTHING contacted any scheduler - there is no SSH"
                + " channel from\nthis build. Every line above is the stated contract the"
                + " runtime enforces, probed\nfrom the owning classes.\n");
        List<String> provenance = new ArrayList<>();
        provenance.add("Transport verdict boundary: JschSshTransport.exec fails as"
                + " SSH_EXEC_FAILED only when the REMOTE command ran and exited non-zero;"
                + " SSH_NOT_CONNECTED / SSH_EXEC_ERROR are transport-shaped and never a"
                + " status verdict.");
        provenance.add("Signal ownership: remote.JobStateGuard.mapSignal is the single"
                + " truth table; RemoteJobMonitor.mapStatus delegates single-token"
                + " outputs there (legacy substring pass remains only for multi-token"
                + " dumps, labeled).");
        provenance.add("Absence needles are documented per scheduler: squeue 'Invalid job"
                + " id specified' (SLURM), qstat 'Unknown Job Id' (PBS/Torque), qstat -j"
                + " 'Following jobs do not exist' (Grid Engine). PJM ships no pinned"
                + " needle because none is confidently documented - fail-closed.");
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #98 (runtime slice): states the selective result-sync contract
     * with the per-workflow file manifests PROBED from their single owner
     * ({@code ResultSyncManifest.forWorkflow}), never re-typed here. The audit
     * names the verdict boundary (a partial sync always ATTACHES its report;
     * security-grade path failures are named in the message; a mid-walk
     * transport death stops as SYNC_TRANSPORT and never fabricates 'missing';
     * the checksum cache is an optimization that degrades to warnings). No
     * file is transferred, uploaded, or deleted from this build.
     */
    private static AnalysisReport analyzeSyncRuntimeAudit(AnalysisParameters params) {
        String label = AnalysisKind.SYNC_RUNTIME_AUDIT.getLabel();
        String requested = params.getSyncWorkflow().trim();
        String prefix = params.getSyncPrefix().trim().isEmpty()
                ? "espresso" : params.getSyncPrefix().trim();
        List<RunningType> census = new ArrayList<>();
        if (requested.isEmpty()) {
            census.addAll(List.of(RunningType.SCF, RunningType.OPTIMIZ, RunningType.MD,
                    RunningType.DOS, RunningType.BAND, RunningType.NEB,
                    RunningType.PHONON));
        } else {
            RunningType parsed = null;
            for (RunningType type : List.of(RunningType.SCF, RunningType.OPTIMIZ,
                    RunningType.MD, RunningType.DOS, RunningType.BAND, RunningType.NEB,
                    RunningType.PHONON)) {
                if (type.name().equalsIgnoreCase(requested)) {
                    parsed = type;
                    break;
                }
            }
            if (parsed == null) {
                return failure(label, "workflow is TYPED: scf | optimiz | md | dos | band"
                        + " | neb | phonon (got '" + requested + "') - a free-form"
                        + " workflow name never reaches a transfer plan."
                        + "\n[SYNC_WORKFLOW]");
            }
            census.add(parsed);
        }
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        csv.add("workflow,priority,relative_path");
        text.append("Result-sync runtime audit - the contract the sync enforces, stated"
                + "\nwith ZERO transfers (no file is downloaded, uploaded or deleted from"
                + "\nthis build; manifest rows are probed from ResultSyncManifest.forWorkflow,"
                + "\nthe single owner of the per-workflow file lists):\n\n");
        text.append(String.format(Locale.ROOT,
                "Prefix = '%s' %s; LARGE_OPTIONAL payloads are %s.%n%n",
                prefix,
                params.getSyncPrefix().trim().isEmpty()
                        ? "(blank input -> the QE default, stated)" : "(explicit)",
                params.isSyncIncludeLarge()
                        ? "INCLUDED (explicit opt-in)" : "skipped but NAMED in the report"));
        for (RunningType type : census) {
            ResultSyncManifest manifest = ResultSyncManifest.forWorkflow(type, prefix);
            List<String> required = new ArrayList<>();
            List<String> optional = new ArrayList<>();
            List<String> large = new ArrayList<>();
            for (ResultSyncManifest.Entry entry : manifest.getEntries()) {
                String path = entry.getRelativePath();
                (entry.getPriority() == ResultSyncManifest.Priority.REQUIRED ? required
                        : entry.getPriority() == ResultSyncManifest.Priority.LARGE_OPTIONAL
                            ? large : optional).add(path);
                csv.add(String.format(Locale.ROOT, "%s,%s,%s", type.name(),
                        entry.getPriority().name(), csvCell(path)));
            }
            text.append(String.format(Locale.ROOT, "  %-7s REQUIRED(%d): %s%n",
                    type.name(), required.size(), String.join(", ", required)));
            text.append(String.format(Locale.ROOT, "          OPTIONAL(%d): %s%n",
                    optional.size(), optional.isEmpty() ? "(none)" : String.join(", ", optional)));
            if (!large.isEmpty()) {
                text.append(String.format(Locale.ROOT,
                        "          LARGE_OPTIONAL(%d): %s %s%n", large.size(),
                        String.join(", ", large),
                        params.isSyncIncludeLarge() ? "(will be fetched - explicit opt-in)"
                                : "(skipped; each named in the report's skippedLarge list)"));
            }
        }
        text.append('\n');
        text.append("Verdict boundary (owned by SelectiveResultSync):\n");
        text.append("  entry gates: SSH_NOT_CONNECTED (unsupported; nothing transferred),"
                + " MANIFEST_EMPTY,\n    LOCAL_DIR_MISSING - all refuse before any"
                + " transfer exists\n");
        text.append("  path safety: manifest entries refuse absolute paths and '..'"
                + " segments at\n    construction; the sync ALSO rejects '..'-shaped"
                + " remote paths and local escapes\n    - both are SECURITY events"
                + " recorded in 'failed' and NAMED in the verdict message\n");
        text.append("  SYNC_OK / SYNC_INCOMPLETE: a partial sync ATTACHES its report;"
                + " the message\n    names every required miss AND every security-grade"
                + " failure (never a bare count)\n");
        text.append("  SYNC_TRANSPORT: if the channel dies mid-walk the sync STOPS at the"
                + " named\n    file and attaches the partial report - remaining entries"
                + " are NOT probed and\n    NOT declared missing (a dead channel"
                + " fabricates no absence evidence)\n");
        text.append("  checksum cache: an OPTIMIZATION only - load/probe/record/save"
                + " failures\n    degrade to warnings, never to a failed verdict;"
                + " cache hits count as kept\n");
        text.append("  integrity (batch 146): downloads run UNVERIFIED unless the caller"
                + " supplies\n    per-path pins - pinned entries route through"
                + " SSHFileTransfer.downloadVerifiedResult\n    (two-sided sha256), and"
                + " a verification refusal lands in 'failed' with its typed code\n"
                + "    named - a security finding, never a quiet 'missing'; the verdict"
                + " message\n    states the posture either way\n");
        text.append("  scope: manifest entries are DOWNLOADED only - nothing is uploaded,"
                + " nothing\n    is deleted remotely, and bulk 'download everything'"
                + " stays forbidden by design\n");
        text.append("\nHonesty block: NOTHING was transferred - there is no SFTP channel"
                + " from this\nbuild. The census above is probed from the owning classes.\n");
        List<String> provenance = new ArrayList<>();
        provenance.add("Manifest ownership: hpc.ResultSyncManifest.forWorkflow is the"
                + " single owner of the per-workflow required/optional/large file lists;"
                + " this audit adds no rows of its own.");
        provenance.add("Path safety: entry-level refusal of absolute/'..' paths plus"
                + " RemotePathGuard staging confinement; local-escape re-check happens"
                + " per file before any write.");
        provenance.add("Batch-146 integrity: pinned-entry downloads delegate to"
                + " SSHFileTransfer.downloadVerifiedResult; chunked/resumable upload lives"
                + " in TransferChunkPlan + SSHFileTransfer.uploadChunkedVerifiedResult"
                + " (per-chunk pins, resume at chunk granularity); this audit still"
                + " transfers nothing.");
        provenance.add("Nothing in this audit contacted any scheduler or storage; all"
                + " verdicts are the stated runtime contract.");
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #96 (plan slice): a bounded remote-monitoring poll policy with
     * its backoff arithmetic computed BEFORE any thread exists. The preview
     * rows pin min(max, initial * factor^(k-1)) with the cap visibly engaged;
     * factor == 1.0 renders as an honest CONSTANT declaration; jitter's
     * absence and the offline-vs-job-state distinction are stated, not
     * implied. No thread starts from this build.
     */
    private static AnalysisReport analyzeMonitorPollPlan(AnalysisParameters params) {
        String label = AnalysisKind.MONITOR_POLL_PLAN.getLabel();
        OperationResult<MonitorPollPlan.PollPlan> validated =
                MonitorPollPlan.validate(params.getMonitorInitialSec(),
                        params.getMonitorMaxSec(), params.getMonitorFactor(),
                        params.getMonitorMaxPolls());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The poll plan was refused:\n[" + validated.getCode()
                    + "] " + validated.getMessage());
        }
        MonitorPollPlan.PollPlan plan = validated.getValue().get();
        String block = plan.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Policy: start %.3f s, cap %.3f s, factor %.3f, at most %d polls "
                        + "(horizon %.3f s; %d poll(s) ride at the cap).%n%n",
                plan.getInitialSec(), plan.getMaxSec(), plan.getFactor(),
                plan.getMaxPolls(), plan.getHorizonSec(), plan.getCappedPolls()));
        text.append(String.format(Locale.ROOT, "%-5s %-14s %-16s%n", "poll",
                "interval (s)", "cumulative (s)"));
        for (double[] row : plan.getPreview()) {
            text.append(String.format(Locale.ROOT, "%-5d %-14.3f %-16.3f%n",
                    (int) row[0], row[1], row[2]));
        }
        if (plan.getMaxPolls() > plan.getPreview().size()) {
            text.append(String.format(Locale.ROOT,
                    "... %d further poll(s) follow at the capped interval (the full "
                            + "horizon above counts them all).%n",
                    plan.getMaxPolls() - plan.getPreview().size()));
        }
        text.append("\nPlan block (also in the draft channel):\n\n").append(block);
        text.append("\nHonesty block: NO thread starts from this build - the plan is "
                + "data for the runtime loop. Single-flight is a violation line, "
                + "jitter is declared NOT IMPLEMENTED (no fake anti-thundering-herd "
                + "claim), and repeated transport failure renders the channel "
                + "UNKNOWN - never 'job finished'. Reconnect RESUMES this same plan "
                + "and its counters; nothing silently restarts.\n");
        List<String> csv = new ArrayList<>();
        csv.add("poll,interval_s,cumulative_s");
        for (double[] row : plan.getPreview()) {
            csv.add(String.format(Locale.ROOT, "%d,%.3f,%.3f", (int) row[0], row[1],
                    row[2]));
        }
        csv.add(String.format(Locale.ROOT, "policy,horizon_s=%.3f,max_polls=%d,capped=%d,"
                + "factor=%.3f", plan.getHorizonSec(), plan.getMaxPolls(),
                plan.getCappedPolls(), plan.getFactor()));
        return new AnalysisReport(label, true, text.toString(), csv, block);
    }

    /**
     * Roadmap #98 (draft slice): selective result-sync manifest. Each entry
     * carries EXACTLY ONE role (required/optional/large-on-demand/excluded) -
     * cross-role duplicates refuse because 'required' vs 'excluded' for the
     * same name would resolve silently. '*' is meaningful ONLY in the owned
     * '*.ext' leading shape; a trailing star reads as a literal that never
     * exists and refuses. Sizes/checksums are declared UNKNOWN until fetch:
     * the manifest records INTENT, not facts.
     */
    private static AnalysisReport analyzeSyncManifestDraft(AnalysisParameters params) {
        String label = AnalysisKind.SYNC_MANIFEST_DRAFT.getLabel();
        OperationResult<SyncManifestBuilder.SyncManifest> validated =
                SyncManifestBuilder.validate(params.getSyncRequired(),
                        params.getSyncOptional(), params.getSyncLarge(),
                        params.getSyncExcluded());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The sync manifest draft was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        SyncManifestBuilder.SyncManifest manifest = validated.getValue().get();
        String block = manifest.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Manifest: %d required, %d optional, %d large-on-demand, %d excluded "
                        + "entr(y/ies), one role per name.%n",
                manifest.getRequired().size(), manifest.getOptional().size(),
                manifest.getLargeOnDemand().size(), manifest.getExcluded().size()));
        text.append("\nManifest block (also in the draft channel):\n\n").append(block);
        // Batch-129 bridge: the draft now compiles to the SAME typed manifest
        // the runtime sync walks - the review channel and the runtime channel
        // share one payload. A wildcard entry stays draft INTENT and refuses
        // honestly; that refusal is a FINDING about the draft, not its failure.
        OperationResult<ResultSyncManifest> bridge = manifest.toRuntimeManifest();
        text.append('\n');
        if (bridge.isSuccess() && bridge.getValue().isPresent()) {
            ResultSyncManifest runtime = bridge.getValue().get();
            text.append(String.format(Locale.ROOT,
                    "Runtime bridge: this draft COMPILES to the typed runtime manifest "
                            + "(%d entries, %d REQUIRED) [SYNC_BRIDGE_OK] - the names "
                            + "above are exactly what a future sync walks; the excluded "
                            + "names never appear there.",
                    runtime.getEntries().size(), runtime.requiredPaths().size())).append('\n');
        } else {
            text.append(String.format(Locale.ROOT,
                    "Runtime bridge: the draft does NOT compile to a fetchable manifest "
                            + "- [%s] %s (a FINDING about the draft, not its failure - "
                            + "the intent above is still recorded).%n",
                    bridge.getCode(), bridge.getMessage()));
        }
        text.append("\nHonesty block: NOTHING downloads from this build. The manifest "
                + "records INTENT for the #98 runtime: bandwidth is saved by role, "
                + "sizes and checksums are UNKNOWN until the first fetch (never "
                + "fabricated), the checksum cache fills at sync time, and parser "
                + "prerequisites verify BEFORE a result pane claims data. A name "
                + "listed twice - inside one role or across roles - refused at "
                + "validation, so the sync can never resolve ambiguity silently.\n");
        List<String> csv = new ArrayList<>();
        csv.add("role,entries,count");
        csv.add(String.format(Locale.ROOT, "required,%s,%d",
                csvCell(String.join(" ", manifest.getRequired())),
                manifest.getRequired().size()));
        csv.add(String.format(Locale.ROOT, "optional,%s,%d",
                csvCell(String.join(" ", manifest.getOptional())),
                manifest.getOptional().size()));
        csv.add(String.format(Locale.ROOT, "large_on_demand,%s,%d",
                csvCell(String.join(" ", manifest.getLargeOnDemand())),
                manifest.getLargeOnDemand().size()));
        csv.add(String.format(Locale.ROOT, "excluded,%s,%d",
                csvCell(String.join(" ", manifest.getExcluded())),
                manifest.getExcluded().size()));
        csv.add(String.format(Locale.ROOT, "runtime_bridge,%s,%s",
                bridge.isSuccess() ? "SYNC_BRIDGE_OK" : "refused-as-intent",
                bridge.getCode()));
        return new AnalysisReport(label, true, text.toString(), csv, block);
    }

    /**
     * Roadmap #38 (plan slice): the smearing DOWN-ladder. Where the k-mesh
     * ladder refines, this ladder DAMPENS toward zero broadening - widening
     * refuses (it inverts the physical question, and nothing re-sorts user
     * input). eV equivalents come from the shared QEUnits.EV_PER_RY constant.
     * The plan NEVER declares convergence: the entropy (-T*S) term and the
     * delta-E/delta-F comparison are runtime results, stated plainly.
     */
    private static AnalysisReport analyzeSmearingLadderPlan(AnalysisParameters params) {
        String label = AnalysisKind.SMEARING_LADDER_PLAN.getLabel();
        OperationResult<SmearingLadderPlan.Ladder> validated =
                SmearingLadderPlan.validate(params.getSmearScheme(), params.getSmearLadder());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The smearing ladder was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        SmearingLadderPlan.Ladder ladder = validated.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Scheme: %s (typed; occupations='smearing' applies to all rungs - one "
                        + "scheme per study so the comparison is scheme-pure).%n%n",
                ladder.getScheme()));
        text.append(String.format(Locale.ROOT, "%-4s %-14s %-14s %-18s%n", "#",
                "degauss (Ry)", "degauss (eV)", "reduction vs prev"));
        List<String> csv = new ArrayList<>();
        csv.add("rung,degauss_ry,degauss_ev,reduction_factor_vs_prev");
        for (SmearingLadderPlan.Rung rung : ladder.getRungs()) {
            text.append(String.format(Locale.ROOT, "%-4d %-14.6f %-14.6f %-18s%n",
                    rung.getIndex(), rung.getDegaussRy(), rung.getDegaussEv(),
                    rung.getReductionVsPrev() == null ? "-"
                            : String.format(Locale.ROOT, "x%.6f",
                                    rung.getReductionVsPrev())));
            csv.add(String.format(Locale.ROOT, "%d,%.6f,%.6f,%s", rung.getIndex(),
                    rung.getDegaussRy(), rung.getDegaussEv(),
                    rung.getReductionVsPrev() == null ? ""
                            : String.format(Locale.ROOT, "%.6f",
                                    rung.getReductionVsPrev())));
        }
        text.append("\nHonesty block: this plan schedules the broadening ladder ONLY - "
                + "it NEVER declares convergence. In the actual runs you MUST monitor "
                + "the entropy (-T*S) contribution and the total energy/force change "
                + "between consecutive rungs; a wide-band system that LOOKS converged "
                + "at large degauss can still be metallic-biased. Schemes are not "
                + "interchangeable: mv (cold) and mp bias forces differently, and fd "
                + "carries a physical-temperature reading - the study compares rungs "
                + "within ONE scheme. degauss = 0 is refused as a rung: extrapolation "
                + "to zero broadening is an analysis you do from the results, not a "
                + "claim the plan makes by omission.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #36 (plan slice): the ecutwfc convergence ladder. Ascending
     * only (coarsening refuses, never re-sorted); ecutrho is IMPLIED from the
     * prompted deck ratio so plan and deck cannot disagree silently; eV via
     * the shared QEUnits constant; the (ratio)^1.5 cost column is exact
     * arithmetic of a LABELED rule-of-thumb. Convergence is never declared -
     * it is a delta-E/force comparison after the runs.
     */
    private static AnalysisReport analyzeCutoffLadderPlan(AnalysisParameters params) {
        String label = AnalysisKind.CUTOFF_LADDER_PLAN.getLabel();
        OperationResult<CutoffLadderPlan.Ladder> validated =
                CutoffLadderPlan.validate(params.getCutoffLadder(),
                        params.getCutoffRhoRatio());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The cutoff ladder was refused:\n[" + validated.getCode()
                    + "] " + validated.getMessage());
        }
        CutoffLadderPlan.Ladder ladder = validated.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Deck rho ratio ecutrho/ecutwfc = %.6f (YOUR setting - ecutrho below is "
                        + "IMPLIED every rung so the plan cannot drift from the deck).%n%n",
                ladder.getRhoRatio()));
        text.append(String.format(Locale.ROOT, "%-4s %-13s %-14s %-16s %-20s%n", "#",
                "ecutwfc (Ry)", "ecutwfc (eV)", "ecutrho (Ry)",
                "cost vs prev (x^1.5*)"));
        List<String> csv = new ArrayList<>();
        csv.add("rung,ecutwfc_ry,ecutwfc_ev,ecutrho_ry_implied,cost_factor_vs_prev_heuristic");
        for (CutoffLadderPlan.Rung rung : ladder.getRungs()) {
            text.append(String.format(Locale.ROOT, "%-4d %-13.6f %-14.6f %-16.6f %-20s%n",
                    rung.getIndex(), rung.getWfcRy(), rung.getWfcEv(),
                    rung.getImpliedRhoRy(),
                    rung.getCostFactorVsPrev() == null ? "-"
                            : String.format(Locale.ROOT, "x%.6f",
                                    rung.getCostFactorVsPrev())));
            csv.add(String.format(Locale.ROOT, "%d,%.6f,%.6f,%.6f,%s", rung.getIndex(),
                    rung.getWfcRy(), rung.getWfcEv(), rung.getImpliedRhoRy(),
                    rung.getCostFactorVsPrev() == null ? ""
                            : String.format(Locale.ROOT, "%.6f",
                                    rung.getCostFactorVsPrev())));
        }
        text.append("\n* The 1.5 exponent in the cost column is a RULE-OF-THUMB for "
                + "PW basis cost - exact arithmetic over the ladder ratios, but never a "
                + "measurement of your machine's timings.\n");
        text.append("\nHonesty block: this plan schedules the cutoff ladder ONLY - it "
                + "NEVER declares convergence. After the runs, compare total energy (and "
                + "forces/stress where they matter) between consecutive rungs against "
                + "YOUR target tolerance. The 5..500 Ry band is OURS (QE imposes no "
                + "limit) and is stated as such; the rho ratio carries no invented "
                + "default - ranges like 4 (NC) or 8-12 (US/PAW) are literature "
                + "hearsay, echoed as hearsay.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #100 (plan slice): typed scheduler array-job plan. The mapping
     * is pinned 1-BASED like --array=1-N (task i -> value_i -> dir
     * <base>/task_<i>); sweep tokens echo VERBATIM (never re-rounded) while
     * duplicate detection is NUMERIC ('30' vs '30.0' refuses - two tasks
     * computing the same point is the silent-redundancy this manifest exists
     * to prevent). The #SBATCH --array=1-N line is an opt-IN review line;
     * pbs/pjm/sge syntax is stated depth, never guessed. Nothing is
     * submitted or templated from this build.
     */
    private static AnalysisReport analyzeArrayJobPlan(AnalysisParameters params) {
        String label = AnalysisKind.ARRAY_JOB_PLAN.getLabel();
        OperationResult<ArrayJobPlan.Plan> validated = ArrayJobPlan.validate(
                params.getArrayBase(), params.getArrayValues(),
                params.isArraySlurmLine());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The array-job plan was refused:\n[" + validated.getCode()
                    + "] " + validated.getMessage());
        }
        ArrayJobPlan.Plan plan = validated.getValue().get();
        String block = plan.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Array '%s': %d task(s), 1-based mapping (task i -> dir %s/task_<i>), "
                        + "SLURM review line %s.%n",
                plan.getBaseName(), plan.getTaskCount(), plan.getBaseName(),
                plan.hasSlurmArrayLine() ? "incorporated" : "NOT incorporated"));
        text.append("\nPlan block (also in the draft channel):\n\n").append(block);
        text.append("\nHonesty block: NOTHING is submitted, and no deck is templated "
                + "from this build. Sweep tokens echo VERBATIM (your '30.00' stays "
                + "'30.00' - float mangling would silently change what runs) while "
                + "duplicate detection is numeric (the same point twice refuses). "
                + "Per-task deck substitution is #100 runtime depth and is never "
                + "silently composed here; pbs/pjm/sge array syntax is depth too.\n");
        List<String> csv = new ArrayList<>();
        csv.add("task,value_verbatim,directory");
        for (int i = 1; i <= plan.getTaskCount(); i++) {
            csv.add(String.format(Locale.ROOT, "%d,%s,%s", i, csvCell(plan.taskValue(i)),
                    csvCell(plan.taskDirectory(i))));
        }
        return new AnalysisReport(label, true, text.toString(), csv, block);
    }

    /**
     * Roadmap #100 (audit slice): renders BOTH array products side by side so
     * their divergence can never silently confuse a study. Everything is
     * PROBED from the owning classes - the planner is exercised with a canned
     * sweep, the plan with the planner's own verbatim values - and grammar
     * findings land where the two products genuinely disagree (name grammar,
     * count bounds, directory mapping). Pick ONE per study; mixing artifacts
     * of both breaks the per-task index mapping.
     */
    private static AnalysisReport analyzeArrayJobAudit(AnalysisParameters params) {
        String label = AnalysisKind.ARRAY_JOB_AUDIT.getLabel();
        String base = params.getArrayAuditBase().trim();
        if (base.isEmpty()) {
            base = "sweep";
        }
        int count = params.getArrayAuditCount();
        if (count < 1 || count > 50) {
            return failure(label, "the display task count must be 1..50 for this audit"
                    + " (got " + count + ") - the audit renders mappings, it does not"
                    + " file a study.\n[ARRAY_AUDIT_COUNT]");
        }
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        csv.add("surface,attribute,value");
        text.append("Array-products audit (Roadmap #100) - TWO typed products exist; both"
                + "\nmappings are rendered from the owning classes with a canned probe"
                + "\n(keyword 'ecutwfc', start 30.0, step 5.0). NOTHING is submitted and no"
                + "\ndirectory is created.\n\n");
        text.append(String.format(Locale.ROOT, "Probe: base '%s', %d display task(s).%n%n",
                base, count));

        // Product 1: the numeric-generated JSONL manifest.
        OperationResult<ArraySweepPlanner.SweepPlan> planner =
                ArraySweepPlanner.plan("ecutwfc", 30.0, 5.0, count, base);
        ArraySweepPlanner.SweepPlan sweep = null;
        if (planner.isSuccess() && planner.getValue().isPresent()) {
            sweep = planner.getValue().get();
            text.append(String.format(Locale.ROOT,
                    "  product 1 = hpc.ArraySweepPlanner (numeric-generated values,"
                            + " JSONL manifest)%n"));
            csv.add("planner,verdict,OK");
        } else {
            text.append(String.format(Locale.ROOT,
                    "  product 1 = hpc.ArraySweepPlanner REFUSES this probe: [%s] %s%n",
                    planner.getCode(), planner.getMessage()));
            csv.add(String.format(Locale.ROOT, "planner,verdict,%s",
                    csvCell(planner.getCode())));
        }

        // Product 2: the verbatim-token review plan, fed with the planner's
        // own lossless tokens when one exists (value-truthful comparison),
        // otherwise a canned token list sized to the display count.
        String planTokens = sweep == null
                ? joinDoubles(List.of(30.0, 35.0, 40.0), count)
                : joinDoubles(sweep.getValues(), count);
        OperationResult<ArrayJobPlan.Plan> jobPlan = ArrayJobPlan.validate(
                base, planTokens, false);
        ArrayJobPlan.Plan plan = null;
        if (jobPlan.isSuccess() && jobPlan.getValue().isPresent()) {
            plan = jobPlan.getValue().get();
            text.append("  product 2 = remote.ArrayJobPlan (verbatim tokens, review"
                    + " line)\n");
            csv.add("arrayjobplan,verdict,OK");
        } else {
            text.append(String.format(Locale.ROOT,
                    "  product 2 = remote.ArrayJobPlan REFUSES this probe: [%s] %s%n",
                    jobPlan.getCode(), jobPlan.getMessage()));
            csv.add(String.format(Locale.ROOT, "arrayjobplan,verdict,%s",
                    csvCell(jobPlan.getCode())));
        }
        text.append('\n');

        int shown = Math.min(count, 5);
        text.append(String.format(Locale.ROOT, "%-5s %-22s %-22s%n", "task",
                "planner directory", "plan directory"));
        boolean mismatch = false;
        for (int i = 1; i <= shown; i++) {
            String plannerDir = sweep == null ? "(refused)" : sweep.taskDirectory(i);
            String planDir;
            try {
                planDir = plan == null ? "(refused)" : plan.taskDirectory(i);
            } catch (IllegalArgumentException outOfRange) {
                planDir = "(outside this plan's task range)";
            }
            if (sweep != null && plan != null && !planDir.startsWith("(")
                    && !plannerDir.equals(planDir)) {
                mismatch = true;
            }
            text.append(String.format(Locale.ROOT, "%-5d %-22s %-22s%n", i,
                    plannerDir, planDir));
            csv.add(String.format(Locale.ROOT, "mapping,task-%d,%s | %s", i,
                    csvCell(plannerDir), csvCell(planDir)));
            if (sweep != null) {
                csv.add(String.format(Locale.ROOT, "planner-value,task-%d,%s", i,
                        Double.toString(sweep.getValues().get(i - 1))));
            }
        }
        if (count > shown) {
            text.append(String.format(Locale.ROOT,
                    "  ... (%d more task(s) elided - display only)%n", count - shown));
        }
        if (mismatch) {
            text.append("\nMAPPING MISMATCH IS PROVEN ABOVE: the two products map the same"
                    + " task index to DIFFERENT directories by design. Do NOT mix artifacts"
                    + " of both in one study.\n");
        }
        text.append('\n');
        text.append(String.format(Locale.ROOT,
                "Grammar census (probed): planner name = 1..32 chars of [A-Za-z0-9._-]"
                        + " (leading digit ALLOWED), tasks %d..%d; plan name = leading"
                        + " letter + up to 64 chars of [A-Za-z0-9._-], tasks 1..%d.%n",
                ArraySweepPlanner.MIN_TASKS, ArraySweepPlanner.MAX_TASKS,
                ArrayJobPlan.MAX_TASKS));
        text.append("Shared invariants (both products, probed): the mapping is 1-BASED like"
                + " SLURM --array=1-N; duplicate tasks refuse on both sides"
                + " (arithmetic guard vs numeric-token equality); every SLURM line is a"
                + " REVIEW line on both sides (REQUIRED-EDIT exit-2 guard vs opt-in review"
                + " line with site-profile edit points).\n");
        text.append("Arithmetic truth (batch-131 correction stated): planner task values are"
                + " start + i*step - ONE rounding per value, error never ACCUMULATES (the"
                + " 10th task of a 0.1-step sweep from 0 is exactly 1.0, not the"
                + " accumulated 0.9999999999999999); overflow to non-finite and sub-ulp"
                + " steps refuse.\n");
        text.append("\nHonesty block: no submission, no directory creation, no deck"
                + " templating - this audit renders mappings and grammar verdicts only,"
                + " probed from the owning classes.\n");
        List<String> provenance = new ArrayList<>();
        provenance.add("Product ownership: hpc.ArraySweepPlanner (numeric JSONL manifest,"
                + " directories <base>-NNN) and remote.ArrayJobPlan (verbatim review plan,"
                + " directories <base>/task_<i>) are two products under Roadmap #100, not"
                + " one - their mapping difference is pinned by cross-test.");
        provenance.add("Float arithmetic: values are start + i*step (single rounding per"
                + " value). Reference divergence example (0.1-step): the accumulated sum"
                + " 0.1+...+0.1 (10 terms) is 0.9999999999999999 while 10*0.1 is exactly"
                + " 1.0 - multiplication is the honest arithmetic for named sweep points.");
        provenance.add("Nothing in this audit contacted any scheduler or filesystem -"
                + " every row is local logic from the owning classes.");
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    private static String joinDoubles(List<Double> values, int limit) {
        StringBuilder tokens = new StringBuilder();
        int n = Math.min(limit, values.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                tokens.append(',');
            }
            tokens.append(Double.toString(values.get(i)));
        }
        return tokens.toString();
    }

    /**
     * Roadmap #103 (draft slice): Apptainer/Singularity profile. The
     * reproducibility rule is STRUCTURAL: the image reference must pin
     * sha256:<64 hex> - floating tags refuse as moving targets, truncated
     * digests refuse as not-close-enough. MPI compatibility must be DECLARED
     * (exactly 'yes' or 'no'; neutrality refuses), and the render labels the
     * declaration analyst-owned, never verified by this build. Nothing
     * launches from this build.
     */
    private static AnalysisReport analyzeContainerProfileDraft(AnalysisParameters params) {
        String label = AnalysisKind.CONTAINER_PROFILE_DRAFT.getLabel();
        OperationResult<ContainerProfileSpec.ContainerProfile> validated =
                ContainerProfileSpec.validate(params.getContainerRuntime(),
                        params.getContainerImageRef(), params.getContainerBinds(),
                        params.getContainerMpiAnswer());
        if (!validated.isSuccess() || validated.getValue().isEmpty()) {
            return failure(label, "The container profile draft was refused:\n["
                    + validated.getCode() + "] " + validated.getMessage());
        }
        ContainerProfileSpec.ContainerProfile profile = validated.getValue().get();
        String block = profile.render();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "runtime=%s, image=%s@%s, binds=%d, MPI=%s.%n",
                profile.getRuntime(), profile.getImageName(), profile.getDigest(),
                profile.getBinds().size(),
                profile.isHostMpiCompatible() ? "host-compatible (declared)"
                        : "container-internal (declared)"));
        text.append("\nProfile block (also in the draft channel):\n\n").append(block);
        // Batch-132 exec-shape slice: the typed runtimes' shared exec shape as
        // one REVIEW line. The command tokens come from the user (blank = the
        // canned 'pw.x -i pw.in' example, stated); a token-grammar refusal is
        // a FINDING on the tokens, the profile stays validated.
        String execTokens = params.getContainerExecCommand().trim();
        List<String> tokens = new ArrayList<>();
        boolean canned = execTokens.isEmpty();
        for (String token : (canned ? "pw.x,-i,pw.in" : execTokens).split(",", -1)) {
            if (!token.trim().isEmpty()) {
                tokens.add(token.trim());
            }
        }
        OperationResult<String> preview = profile.execPreview(tokens);
        text.append('\n');
        if (preview.isSuccess() && preview.getValue().isPresent()) {
            text.append(String.format(Locale.ROOT,
                    "Exec-shape preview (%s tokens; REVIEW lines only - not launched):%n%n%s",
                    canned ? "canned 'pw.x -i pw.in'" : "your", preview.getValue().get()));
        } else {
            text.append(String.format(Locale.ROOT,
                    "Exec-shape preview: tokens REFUSED - [%s] %s (a FINDING on the"
                            + " tokens; the profile itself validated fine).%n",
                    preview.getCode(), preview.getMessage()));
        }
        // Batch-147 launch-bridge slice: compose the VALIDATED profile with a
        // loaded site profile so the batch-132 <mpirun/srun + counts> anchor
        // gets its values from the owner (never transcribed). Blank path =
        // stated not_exercised; any load/bridge refusal is a FINDING - the
        // validated profile and the preview stand either way.
        String bridgeCsvRow;
        String sitePath = params.getContainerSiteProfile().trim();
        if (sitePath.isEmpty()) {
            text.append("\nLaunch bridge: not exercised (no site profile path given - "
                    + "the <mpirun/srun + counts> anchor above stays an edit point).\n");
            bridgeCsvRow = "launch_bridge,not_exercised,no site profile path given";
        } else {
            SiteProfile site = null;
            String loadError = null;
            try {
                site = SiteProfile.load(java.nio.file.Path.of(sitePath));
            } catch (Exception loadFail) {
                loadError = String.valueOf(loadFail.getMessage());
            }
            if (site == null) {
                text.append("\nLaunch bridge: site profile REFUSED - " + loadError
                        + " (a FINDING on the path; the container profile and the"
                        + " preview stand).\n");
                bridgeCsvRow = "launch_bridge,refused,site load refused";
            } else {
                OperationResult<ContainerLaunchBridge.LaunchBridge> bridged =
                        ContainerLaunchBridge.bridge(profile, site, tokens);
                if (bridged.isSuccess() && bridged.getValue().isPresent()) {
                    ContainerLaunchBridge.LaunchBridge launchBridge =
                            bridged.getValue().get();
                    text.append("\nLaunch bridge (site '" + launchBridge.getSiteId()
                            + "'; REVIEW only - not launched):\n\n"
                            + launchBridge.renderBlock());
                } else {
                    text.append("\nLaunch bridge: REFUSED - [" + bridged.getCode()
                            + "] " + bridged.getMessage() + " (a FINDING on the bridge"
                            + " inputs; the container profile and the preview stand).\n");
                }
                bridgeCsvRow = String.format(Locale.ROOT, "launch_bridge,%s,%s",
                        bridged.isSuccess() ? "resolved" : "refused",
                        csvCell(bridged.getCode()));
            }
        }
        text.append("\nHonesty block: NOTHING launches from this build. The digest pin "
                + "is structural - a floating tag is a moving target and refuses; the "
                + "bind list is literal (no expansion/whitespace/separators); and the "
                + "MPI line is a DECLARATION by you, labeled as such - a wrong "
                + "declaration breaks multi-node jobs, and this build does not verify "
                + "ABIs. Image pull/verify, bind enforcement and launch are the #103 "
                + "runtime depth.\n");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "runtime,%s,typed enum", profile.getRuntime()));
        csv.add(String.format(Locale.ROOT, "image,%s,floating tags refused",
                csvCell(profile.getImageName())));
        csv.add(String.format(Locale.ROOT, "digest,%s,sha256 64-hex required",
                profile.getDigest()));
        csv.add(String.format(Locale.ROOT, "binds,%d,literal absolute POSIX",
                profile.getBinds().size()));
        csv.add(String.format(Locale.ROOT, "mpi_compatibility,%s,analyst declaration - "
                + "not verified",
                profile.isHostMpiCompatible() ? "host-compatible" : "container-internal"));
        csv.add(String.format(Locale.ROOT, "exec_preview,%s,%s",
                preview.isSuccess() ? "rendered" : "refused", preview.getCode()));
        csv.add(bridgeCsvRow);
        return new AnalysisReport(label, true, text.toString(), csv, block);
    }

    /**
     * Roadmap #95 (guard slice): the legal transition table plus scheduler
     * signal mapping as a pure truth function. Terminals never leave;
     * backward edges refuse (they rewrite history); the only sideways edge
     * is -> UNKNOWN, always labelled reconciliation-not-progress; CANCELLED
     * requires a live job (batch 112's verification shows it gone); and an
     * unrecognized scheduler signal SUCCEEDS carrying UNKNOWN - unknown is an
     * honest result, guessing is the error.
     */
    private static AnalysisReport analyzeJobStateGuard(AnalysisParameters params) {
        String label = AnalysisKind.JOB_STATE_GUARD.getLabel();
        String mode = params.getJobStateMode() == null ? "" : params.getJobStateMode()
                .trim().toLowerCase(Locale.ROOT);
        StringBuilder text = new StringBuilder();
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        if (mode.equals("transition")) {
            OperationResult<JobStateGuard.Verdict> verdict = JobStateGuard.transition(
                    params.getJobStateFrom(), params.getJobStateTo());
            if (!verdict.isSuccess() || verdict.getValue().isEmpty()) {
                return failure(label, "The transition was refused:\n[" + verdict.getCode()
                        + "] " + verdict.getMessage());
            }
            JobStateGuard.Verdict v = verdict.getValue().get();
            text.append(String.format(Locale.ROOT,
                    "transition %s -> %s : LEGAL%s%n", v.getFrom(), v.getTo(),
                    v.isReconciliation() ? " (sideways -> UNKNOWN: RECONCILIATION, "
                            + "not progress)" : " (forward edge)"));
            csv.add(String.format(Locale.ROOT, "transition,%s->%s,%s", v.getFrom(),
                    v.getTo(), v.isReconciliation() ? "reconciliation" : "legal-forward"));
        } else if (mode.equals("signal")) {
            OperationResult<JobStateGuard.State> mapped = JobStateGuard.mapSignal(
                    params.getJobStateScheduler(), params.getJobStateSignal());
            if (!mapped.isSuccess() || mapped.getValue().isEmpty()) {
                return failure(label, "The signal mapping was refused:\n["
                        + mapped.getCode() + "] " + mapped.getMessage());
            }
            JobStateGuard.State state = mapped.getValue().get();
            text.append(String.format(Locale.ROOT,
                    "signal '%s' on %s -> %s%n", params.getJobStateSignal(),
                    params.getJobStateScheduler(), state));
            if (state == JobStateGuard.State.UNKNOWN) {
                text.append("(An unrecognized signal is an HONEST UNKNOWN result - the "
                        + "mapping never guesses.)\n");
            }
            csv.add(String.format(Locale.ROOT, "signal,%s,%s",
                    csvCell(params.getJobStateSignal()), state));
        } else {
            return failure(label, "mode must be typed 'transition' or 'signal' (got '"
                    + params.getJobStateMode() + "') - the guard judges exactly one "
                    + "truth per invocation.");
        }
        text.append("\nThe full legal table (the contract for the #95 runtime state "
                + "machine):\n"
                + "  staged     -> submitted\n"
                + "  submitted  -> pending | failed | cancelled\n"
                + "  pending    -> running | cancelled | failed\n"
                + "  running    -> completed | failed | cancelled\n"
                + "  any live   -> unknown   (sideways: RECONCILIATION, not progress)\n"
                + "  terminals (completed/failed/cancelled/unknown): never leave\n"
                + "Honesty block: no jobs exist in this build - this guard is the "
                + "contract. CANCELLED additionally requires the batch-112 scheduler "
                + "query to have shown the job gone; alternate GUI restart paths must "
                + "reconstruct accurately FROM the scheduler and manifests, never from "
                + "wishful last state.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #51 (plan slice): the phonon q-grid ladder with per-rung k/q
     * COMMENSURABILITY verdicts against the live deck k-grid. QE permits
     * non-commensurate q (fresh SCFs follow), so non-commensurate rungs are
     * NAMED per direction rather than refused - but they are never silently
     * passed. An absent/non-automatic deck k-grid makes EVERY verdict an
     * explicit UNVERIFIABLE banner, in caps - not a quiet green.
     */
    private static AnalysisReport analyzePhononGridPlan(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.PHONON_GRID_PLAN.getLabel();
        OperationResult<List<int[]>> parsed =
                PhononGridLadderPlan.parse(params.getPhononLadder());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "The q-grid ladder was refused:\n[" + parsed.getCode()
                    + "] " + parsed.getMessage());
        }
        List<int[]> rungs = parsed.getValue().get();
        int[] kGrid = null;
        String kNote;
        try {
            project.resolveQEInputs();
            QEInput input = project.getQEInputCurrent();
            QEKPoints kpoints = input == null ? null
                    : input.getCard(QEKPoints.class);
            if (kpoints != null && kpoints.isAutomatic() && kpoints.getKGrid() != null
                    && kpoints.getKGrid().length == 3) {
                kGrid = kpoints.getKGrid();
                kNote = String.format(Locale.ROOT,
                        "Deck K_POINTS automatic k-grid: %d %d %d (divisibility below is "
                                + "k_i %% q_i == 0 arithmetic).",
                        kGrid[0], kGrid[1], kGrid[2]);
            } else {
                kNote = "Deck k-grid is absent, gamma or explicit - commensurability is "
                        + "UNVERIFIABLE for every rung (stated in caps, not passed).";
            }
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        StringBuilder text = new StringBuilder();
        text.append(kNote).append("\n\n");
        text.append(String.format(Locale.ROOT, "%-4s %-12s %-44s%n", "#", "q-grid",
                "commensurability verdict"));
        List<String> csv = new ArrayList<>();
        csv.add("rung,q1,q2,q3,verdict,bad_directions");
        int rungIndex = 0;
        for (int[] rung : rungs) {
            rungIndex += 1;
            String verdictText;
            String csvVerdict;
            String badDirs;
            if (kGrid == null) {
                verdictText = "UNVERIFIABLE - no automatic deck k-grid (never a "
                        + "silent pass)";
                csvVerdict = "UNVERIFIABLE";
                badDirs = "";
            } else {
                List<Integer> bad = PhononGridLadderPlan.nonCommensurateDirections(
                        kGrid, rung);
                if (bad.isEmpty()) {
                    verdictText = "COMMENSURATE on the deck k-grid";
                    csvVerdict = "COMMENSURATE";
                    badDirs = "";
                } else {
                    StringBuilder dirs = new StringBuilder();
                    for (int d = 0; d < bad.size(); d++) {
                        if (d > 0) {
                            dirs.append(' ');
                        }
                        dirs.append(bad.get(d));
                    }
                    verdictText = "NOT-COMMENSURATE at direction(s) " + dirs
                            + " (QE permits it - fresh SCFs follow - but this plan "
                            + "never hides it)";
                    csvVerdict = "NOT_COMMENSURATE";
                    badDirs = dirs.toString();
                }
            }
            text.append(String.format(Locale.ROOT, "%-4d %2d %2d %2d   %s%n", rungIndex,
                    rung[0], rung[1], rung[2], verdictText));
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%s,%s", rungIndex, rung[0],
                    rung[1], rung[2], csvVerdict, badDirs));
        }
        text.append("\nHonesty block: this slice schedules q-grids ONLY. Why "
                + "commensurability matters: a q point outside the SCF k-grid set costs "
                + "its own SCF - the classic wasted-queue mistake - so the verdict is "
                + "arithmetic you can audit, with failing directions NAMED. Downstream "
                + "#51 depth (not judged here): ph.x run itself, q2r/matdyn conversion, "
                + "the acoustic sum rule diagnostics, non-analytic corrections for "
                + "polars, and honest imaginary-mode reporting.\n");
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #99 (surface slice): checkpoint-aware resubmission advice for the
     * open project - READ-ONLY: no plan file, script, or job mutation (see
     * CheckpointResubmit.plan / exportScript for the explicit-write sibling).
     *
     * <p>Writes NOTHING: the advice composes two tested truths - WHY the run
     * stopped (bounded log-signature scan, "unknown" admitted) and whether the
     * restart artifacts are complete (never invented) - into a typed
     * recommendation. The user still resubmits explicitly, elsewhere.</p>
     *
     * <p>The stop reason comes from the tested {@link CheckpointResubmit} log
     * signature scan; restart artifact completeness from the tested
     * {@link RestartManager}. Neither is invented.</p>
     */
    private static AnalysisReport analyzeCheckpointResubmit(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.CHECKPOINT_RESUBMIT_PLAN.getLabel();
        File directory = project.getDirectory();
        if (directory == null) {
            return failure(label, "The project has no on-disk directory to inspect.");
        }
        String prefix = params.getCheckpointPrefixOverride().isBlank()
                ? project.getPrefixName() : params.getCheckpointPrefixOverride();
        OperationResult<ResubmitAdvice.Advice> advised =
                ResubmitAdvice.advise(directory.toPath(), prefix);
        if (!advised.isSuccess() || advised.getValue().isEmpty()) {
            return failure(label, "The resubmission advice refused:\n[" + advised.getCode()
                    + "] " + advised.getMessage());
        }
        ResubmitAdvice.Advice advice = advised.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Prefix: ").append(prefix)
                .append(params.getCheckpointPrefixOverride().isBlank()
                        ? " (project prefix)\n" : " (user override)\n");
        text.append("Save directory: ").append(advice.getSaveDirectory()).append('\n');
        text.append("Stop-reason signature: ").append(advice.getStopReason())
                .append("  (bounded scan of small .log/.err/.out files; 'unknown' is an ")
                .append("honest answer, not a gap to paper over)\n");
        text.append("Restart artifacts safe: ").append(advice.isRestartSafe()).append('\n');
        text.append("Recommended restart_mode: ").append(advice.getRestartMode()).append("\n\n");
        text.append("RECOMMENDATION: ").append(advice.getRecommendation()).append('\n');
        text.append(advice.getRationale()).append("\n\n");
        text.append("CONTROL snippet for a resubmission input (REVIEW before use):\n  ")
                .append(advice.controlSnippet()).append("\n\n");
        text.append("Diagnostics:\n");
        for (String diagnostic : advice.getDiagnostics()) {
            text.append(" - ").append(diagnostic).append('\n');
        }
        text.append("\nNo files were written: no plan file, no script, no job record was "
                + "created or mutated, and NOTHING was submitted - resubmission is always "
                + "your explicit act. The explicit-write planner (plan file / script) "
                + "remains a separate deliberate tool.");
        List<String> csv = new ArrayList<>();
        csv.add("prefix,stop_reason,restart_safe,restart_mode,recommendation");
        csv.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s",
                csvCell(prefix), advice.getStopReason(), advice.isRestartSafe(),
                advice.getRestartMode(), advice.getRecommendation()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #105 (review slice): READ-ONLY audit of a durable job-queue JSONL
     * artifact. The JobQueueStore loader is intentionally FORGIVING (malformed
     * lines dropped, duplicate ids last-wins, unknown states mapped to UNKNOWN);
     * this audit re-reads the RAW text and names what the loader would tolerate:
     * malformed lines by number, duplicate jobIds, typed-chain history verdicts
     * (batch 119), backward timestamps, final-state/history contradictions, and
     * a final-state histogram. Nothing is repaired, migrated, or rewritten.
     */
    private static AnalysisReport analyzeJobQueueAudit(File source) {
        String label = AnalysisKind.JOB_QUEUE_AUDIT.getLabel();
        OperationResult<JobQueueAudit.Audit> audited = JobQueueAudit.audit(source.toPath());
        if (!audited.isSuccess() || audited.getValue().isEmpty()) {
            return failure(label, "The queue audit refused " + source.getName() + ":\n["
                    + audited.getCode() + "] " + audited.getMessage());
        }
        JobQueueAudit.Audit audit = audited.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Queue artifact: ").append(audit.getFile()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Lines: %d total, %d blank/comment (skipped by the loader, counted here), "
                        + "%d malformed (RAW - the loader would silently drop these)%n",
                audit.getTotalLines(), audit.getSkippedLines(), audit.getMalformedCount()));
        if (audit.getMalformedCount() > 0) {
            text.append("Malformed line numbers (first ").append(JobQueueAudit.MAX_LISTED)
                    .append("): ");
            int shown = 0;
            for (Integer lineNo : audit.getMalformedLines()) {
                if (shown++ >= JobQueueAudit.MAX_LISTED) {
                    text.append("... +").append(audit.getMalformedCount() - JobQueueAudit.MAX_LISTED)
                            .append(" more");
                    break;
                }
                if (shown > 1) {
                    text.append(' ');
                }
                text.append(lineNo);
            }
            text.append('\n');
        }
        if (audit.getDuplicates().isEmpty()) {
            text.append("Duplicate jobIds: none\n");
        } else {
            text.append("Duplicate jobIds (loader semantics are LAST-WINS):\n");
            for (String id : audit.getDuplicates()) {
                text.append(" - ").append(id).append(" x")
                        .append(audit.getOccurrences().get(id)).append('\n');
            }
        }
        text.append(String.format(Locale.ROOT, "%nRecords: %d, clean: %d, with problems: "
                        + "%d; per-edge reconciliation (-> UNKNOWN, honest) edges tallied "
                        + "below.%n",
                audit.getRecords().size(), audit.getCleanCount(),
                audit.getRecords().size() - audit.getCleanCount()));
        int reconciliationTotal = 0;
        for (JobQueueAudit.RecordVerdict record : audit.getRecords()) {
            reconciliationTotal += record.getReconciliationEdges();
            text.append(String.format(Locale.ROOT, "\n- %s [%s] final=%s history=%d edge(s)%n",
                    record.getJobId(), record.getScheduler(), record.getFinalState(),
                    record.getHistoryLength()));
            if (record.getReconciliationEdges() > 0) {
                text.append("    reconciliation edges (-> UNKNOWN): ")
                        .append(record.getReconciliationEdges()).append('\n');
            }
            int shown = 0;
            for (String problem : record.getProblems()) {
                if (shown++ >= JobQueueAudit.MAX_LISTED) {
                    text.append("    ... +")
                            .append(record.getProblems().size() - JobQueueAudit.MAX_LISTED)
                            .append(" more problem(s) (counted)\n");
                    break;
                }
                text.append("    ! ").append(problem).append('\n');
            }
        }
        text.append("\nFinal-state histogram (last occurrence per jobId):\n");
        for (JobState state : JobState.values()) {
            Integer count = audit.getHistogram().get(state);
            if (count != null) {
                text.append("  ").append(state).append(": ").append(count).append('\n');
            }
        }
        text.append("\nReview-only boundary: this audit READ the artifact and VERDICTED "
                + "only - no line was repaired, no record migrated, no store rewritten, "
                + "and no scheduler was contacted. Loader-tolerated defects are YOUR call.");
        List<String> csv = new ArrayList<>();
        csv.add("job_id,scheduler,final_state,history_edges,problems,reconciliation_edges");
        for (JobQueueAudit.RecordVerdict record : audit.getRecords()) {
            csv.add(String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%d",
                    csvCell(record.getJobId()), csvCell(record.getScheduler()),
                    record.getFinalState(), record.getHistoryLength(),
                    record.getProblems().size(), record.getReconciliationEdges()));
        }
        List<String> provenance = new ArrayList<>();
        provenance.add("queue-audit: total_problem_count=" + audit.getProblemTotal());
        provenance.add("queue-audit: reconciliation_edges=" + reconciliationTotal);
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #104 (audit slice): READ-ONLY structural audit of the project's
     * exported workflow script (.quantumforge.workflow.sh) against the CURRENT
     * stage list. The artifact's promise is GUI-independence; this kind states
     * its stage census, which required stages would abort unconfigured
     * ("No command recorded" -> exit 2), its safety markers, and an
     * order-sensitive SYNC verdict naming differing stage ids. The refresh
     * path is the deliberate rerun/export - nothing is rewritten here.
     */
    private static AnalysisReport analyzeWorkflowExportAudit(Project project) {
        String label = AnalysisKind.WORKFLOW_EXPORT_AUDIT.getLabel();
        File directory = project.getDirectory();
        if (directory == null) {
            return failure(label, "The project has no on-disk directory to inspect.");
        }
        File script = new File(directory, ".quantumforge.workflow.sh");
        OperationResult<WorkflowAudit.Audit> audited = WorkflowAudit.audit(script.toPath());
        if (!audited.isSuccess() || audited.getValue().isEmpty()) {
            return failure(label, "The workflow audit refused:\\n[" + audited.getCode()
                    + "] " + audited.getMessage()
                    + "\\n\\n(No artifact at the standard path either - exports happen "
                    + "per run or via the deliberate Export workflow script action.)");
        }
        WorkflowAudit.Audit audit = audited.getValue().get();

        RunningType type = RunningType.getRunningType(project);
        List<String> expectedIds = null;
        String expectedNote;
        try {
            if (type == null) {
                expectedNote = "current workflow type is unset - DAG not rebuildable";
            } else {
                QECommandDag dag = QECommandDag.build(project, type, 1);
                expectedIds = new ArrayList<>();
                for (QECommandStage stage : dag.getStages()) {
                    expectedIds.add(stage.getId());
                }
                expectedNote = "current " + type.name() + " DAG ("
                        + expectedIds.size() + " stage(s)); numProc/input-name never "
                        + "affect stage ids - only command tokens";
            }
        } catch (UnsupportedOperationException ex) {
            expectedNote = "current " + type.name() + " DAG is NOT honestly rebuildable ("
                    + ex.getMessage() + " in one line: a sweep DAG needs a configured "
                    + "parameter set) - comparison refused, never faked";
        } catch (RuntimeException ex) {
            expectedNote = "rebuilding the current DAG failed closed: " + ex.getMessage();
        }
        WorkflowAudit.SyncVerdict verdict = WorkflowAudit.sync(audit.stageIds(), expectedIds);
        List<String> missing = WorkflowAudit.missingFromArtifact(audit.stageIds(), expectedIds);
        List<String> extra = WorkflowAudit.extraInArtifact(audit.stageIds(), expectedIds);

        StringBuilder text = new StringBuilder();
        text.append("Artifact: ").append(audit.getFile()).append('\n');
        text.append("Recognizable export markers: shebang=").append(audit.hasShebang())
                .append(", set -euo pipefail=").append(audit.hasSetOptions())
                .append(", SLURM block=").append(audit.hasSlurmBlock()).append('\n');
        if (!audit.getGeneratorLine().isBlank()) {
            text.append("Generator: # Generated by ").append(audit.getGeneratorLine())
                    .append("  (a CLAIM made by the file, not a verified fact)\n");
        }
        if (!audit.getWorkflowType().isBlank()) {
            text.append("Workflow type stamped in artifact: ")
                    .append(audit.getWorkflowType());
            if (type != null && !audit.getWorkflowType().equals(type.name())) {
                text.append("  <-- MISMATCH with the current type ").append(type.name())
                        .append(": the artifact predates a workflow-type change");
            }
            text.append('\n');
        }
        text.append(String.format(Locale.ROOT, "%nExported stages (%d):%n",
                audit.getStages().size()));
        for (WorkflowAudit.StageVerdict stage : audit.getStages()) {
            text.append(String.format(Locale.ROOT, "  #%-3d %-14s %-9s %-9s %s%n",
                    stage.getIndex(), stage.getId(),
                    stage.isOptional() ? "optional" : "required",
                    stage.hasCommand() ? "has-cmd" : "NO-COMMAND",
                    stage.isAbortsWhenEmpty() ? "WOULD EXIT 2 at runtime" : ""));
        }
        if (!audit.abortStageIds().isEmpty()) {
            text.append("\nWARNING: required stage(s) ").append(audit.abortStageIds())
                    .append(" carry no command - running the artifact would abort there. "
                            + "Configure QE executable paths and re-export.\n");
        }
        text.append("\nSync vs current configuration (").append(expectedNote)
                .append("): ").append(verdict).append('\n');
        if (verdict == WorkflowAudit.SyncVerdict.BEHIND_CONFIG
                || verdict == WorkflowAudit.SyncVerdict.DIVERGED) {
            text.append("  stage(s) in the CURRENT config but NOT in the artifact: ")
                    .append(missing).append('\n');
        }
        if (verdict == WorkflowAudit.SyncVerdict.AHEAD_OF_CONFIG
                || verdict == WorkflowAudit.SyncVerdict.DIVERGED) {
            text.append("  stage(s) in the artifact but NOT in the current config: ")
                    .append(extra).append('\n');
        }
        text.append("\nRead-only boundary: the artifact was READ and judged; nothing was "
                + "rewritten, refreshed, or re-exported, and no stage was executed. A stale "
                + "artifact is refreshed by your deliberate rerun or Export action, matching "
                + "the artifact's own purpose: staying usable when QuantumForge is absent.");
        List<String> csv = new ArrayList<>();
        csv.add("stage_index,stage_id,optional,has_command,aborts_when_empty");
        for (WorkflowAudit.StageVerdict stage : audit.getStages()) {
            csv.add(String.format(Locale.ROOT, "%d,%s,%s,%s,%s", stage.getIndex(),
                    csvCell(stage.getId()), stage.isOptional(), stage.hasCommand(),
                    stage.isAbortsWhenEmpty()));
        }
        List<String> provenance = new ArrayList<>();
        provenance.add("workflow-audit: sync_verdict=" + verdict);
        provenance.add("workflow-audit: stages=" + audit.getStages().size());
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #50 (review slice): READ-ONLY geometric audit of a multi-frame XYZ
     * NEB ladder. Verdicts, not edits: per-pair RMSD/max-displacement exact
     * arithmetic, NAMED duplicated-image pairs, coincident-endpoint reporting
     * (legal - ring paths exist), and an owned spacing-ratio RULE-OF-THUMB
     * (labeled ours). The cell-aware minimum-image / spring / climbing-image
     * depth stays explicitly out of scope. Nothing is rewritten or re-shaped.
     */
    private static AnalysisReport analyzeNebPathAudit(File source) {
        String label = AnalysisKind.NEB_PATH_AUDIT.getLabel();
        OperationResult<NebPathAudit.Audit> audited = NebPathAudit.audit(source.toPath());
        if (!audited.isSuccess() || audited.getValue().isEmpty()) {
            return failure(label, "The NEB path audit refused " + source.getName() + ":\n["
                    + audited.getCode() + "] " + audited.getMessage());
        }
        NebPathAudit.Audit audit = audited.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Path artifact: ").append(audit.getFile()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Frames: %d, atoms/frame: %d, coordinates: Cartesian Angstrom (stated "
                        + "convention - no cell, no minimum-image wrap)%n",
                audit.getFrames(), audit.getAtomsPerFrame()));
        text.append(String.format(Locale.ROOT, "%n%-12s %-14s %-14s%n", "image pair",
                "RMS disp (A)", "max atom (A)"));
        List<String> csv = new ArrayList<>();
        csv.add("pair_from,pair_to,rmsd_angstrom,max_disp_angstrom");
        for (NebPathAudit.PairMetrics pair : audit.getPairs()) {
            text.append(String.format(Locale.ROOT, " %2d -> %-6d %-14s %-14s%n",
                    pair.getFrom() + 1, pair.getFrom() + 2,
                    NebPathAudit.fmt(pair.getRmsd()), NebPathAudit.fmt(pair.getMaxDisp())));
            csv.add(String.format(Locale.ROOT, "%d,%d,%s,%s", pair.getFrom() + 1,
                    pair.getFrom() + 2, NebPathAudit.fmt(pair.getRmsd()),
                    NebPathAudit.fmt(pair.getMaxDisp())));
        }
        text.append(String.format(Locale.ROOT,
                "%nPath length (sum of pair RMSDs): %s A%n", NebPathAudit.fmt(audit.getTotalLength())));
        text.append(String.format(Locale.ROOT,
                "Spacing ratio max/min RMSD: %s  (owned RULE-OF-THUMB bound is %.2f - ours, "
                        + "never a QE rule)%n",
                Double.isInfinite(audit.spacingRatio()) ? "INFINITE (duplicated image present)"
                        : NebPathAudit.fmt(audit.spacingRatio()),
                NebPathAudit.SPACING_RATIO_RULE));
        if (!Double.isInfinite(audit.spacingRatio())
                && audit.spacingRatio() > NebPathAudit.SPACING_RATIO_RULE) {
            text.append("  above the owned bound: widest pair is ")
                    .append(audit.getWorstPairFrom() + 1).append(" -> ")
                    .append(audit.getWorstPairFrom() + 2).append(", narrowest is ")
                    .append(audit.getBestPairFrom() + 1).append(" -> ")
                    .append(audit.getBestPairFrom() + 2)
                    .append(" (consider re-interpolating BETWEEN the wide pair)\n");
        }
        if (audit.getDuplicatePairs().isEmpty()) {
            text.append("Duplicated-image pairs: none\n");
        } else {
            text.append("DUPLICATED-IMAGE pair(s) NAMED (zero spring force, silently breaks "
                    + "the path parameterization): ");
            for (int i = 0; i < audit.getDuplicatePairs().size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                int from = audit.getDuplicatePairs().get(i);
                text.append(from + 1).append(" -> ").append(from + 2);
            }
            text.append('\n');
        }
        text.append("Endpoints: ").append(audit.hasCoincidentEndpoints()
                ? "COINCIDENT (max displacement " + NebPathAudit.fmt(audit.getEndpointMaxDisp())
                        + " A) - REPORTED as an observation; ring/closed paths are legitimate"
                : "distinct (first->last max displacement "
                        + NebPathAudit.fmt(audit.getEndpointMaxDisp()) + " A)")
                .append('\n');
        text.append("\nScope boundary (stated, never hidden): this is a GEOMETRY audit of "
                + "the ladder as written. NO minimum-image convention (periodic paths can "
                + "be misjudged), no spring-constant energy model, no force projections, no "
                + "climbing-image verdict, and NO editing - image interpolation / insertion "
                + "is the #50 path-editor depth, done deliberately elsewhere.");
        List<String> provenance = new ArrayList<>();
        provenance.add("neb-audit: frames=" + audit.getFrames());
        provenance.add("neb-audit: spacing_ratio=" + (Double.isInfinite(audit.spacingRatio())
                ? "infinite" : NebPathAudit.fmt(audit.spacingRatio())));
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #40 (transaction): commits the previewed converged geometry into
     * every resolved mode deck through FinalGeometryTransaction - staged on
     * copies first, audit artifacts + SHA-256 marker pinned under
     * .quantumforge/ BEFORE any live mutation, then verified write-through
     * with in-memory rollback on failure. This kind IS the explicit user
     * action that justifies the write; the READ-ONLY preview remains the
     * separate "Preview final geometry" action.
     */
    private static AnalysisReport analyzeFinalGeometryApply(Project project) {
        String label = AnalysisKind.FINAL_GEOMETRY_APPLY.getLabel();
        OperationResult<FinalGeometryTransaction.Plan> applied =
                FinalGeometryTransaction.apply(project);
        if (!applied.isSuccess() || applied.getValue().isEmpty()) {
            return failure(label, "The transactional apply refused:\n[" + applied.getCode()
                    + "] " + applied.getMessage());
        }
        FinalGeometryTransaction.Plan plan = applied.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Committed converged geometry (opt step %d, %d atoms) to %d resolved "
                        + "mode(s).%n",
                plan.getStepIndex() + 1, plan.getAtomCount(),
                plan.getCommittedModes().size()));
        text.append("Cell: ").append(plan.isCellWritten()
                ? "CELL_PARAMETERS rewritten bohr for modes carrying the card (relaxed-cell "
                        + "trail)"
                : "not written - geometry row had no cell; deck cell kept as-is")
                .append('\n');
        text.append("Unit: positions rewritten explicitly BOHR (parser provenance of the "
                + "snapshot; unit matched by declaration, never by assumption).\n\n");
        text.append("Per-mode trail:\n");
        for (FinalGeometryTransaction.TrailEntry entry : plan.getTrail()) {
            text.append("  - ").append(String.format(Locale.ROOT, "%-9s %-9s %s%n",
                    entry.getMode(), entry.getState(), entry.getReason()));
        }
        List<String> csv = new ArrayList<>();
        csv.add("mode,state,pre_sha256,staged_sha256,reason");
        for (FinalGeometryTransaction.TrailEntry entry : plan.getTrail()) {
            csv.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s", entry.getMode(),
                    entry.getState(), entry.getPreHash(), entry.getStagedHash(),
                    csvCell(entry.getReason())));
        }
        text.append("\nAudit artifacts (recovery path - deleting them forfeits it):\n");
        text.append("  .quantumforge/<mode>.pre-final-geometry  - original deck text "
                + "BEFORE the apply\n");
        text.append("  .quantumforge/<mode>.staged-geometry     - the exact text committed\n");
        text.append("  .quantumforge/final-geometry.audit.txt   - SHA-256 hashes + mode map\n");
        text.append("\nRollback/recovery: a mid-write failure restores the captured "
                + "in-memory cards and re-saves, then VERIFIES; the report above says "
                + "RESTORE-UNVERIFIABLE when even that failed (replay the pre-* artifacts "
                + "by hand, then RELOAD the project). On success, reload the project "
                + "before running: the deck text on disk now carries the converged "
                + "geometry.");
        List<String> provenance = new ArrayList<>();
        provenance.add("final-geometry-apply: modes=" + plan.getCommittedModes());
        provenance.add("final-geometry-apply: cell_written=" + plan.isCellWritten());
        return new AnalysisReport(label, true, text.toString(), csv, null, provenance);
    }

    /**
     * Roadmap #117 (parse slice): reads a LOCAL, already-saved OPTIMADE
     * JSON:API /structures response. Nothing is fetched here - the fetch,
     * pagination, provider-filter and cache work remains #117 runtime depth.
     * The report says plainly that the artifact is the only provenance, that
     * absent optional fields are "(not supplied)" rather than invented, that
     * OPTIMADE lattice vectors carry nm units by specification, and that
     * meta.* values are claims made by the file, not verified facts.
     */
    private static AnalysisReport analyzeOptimadeResponseParse(File source) {
        String label = AnalysisKind.OPTIMADE_RESPONSE_PARSE.getLabel();
        OperationResult<OptimadeStructuresParser.OptimadeResponse> parsed =
                OptimadeStructuresParser.parseFile(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "The OPTIMADE response parse refused "
                    + source.getName() + ":\n[" + parsed.getCode() + "] "
                    + parsed.getMessage());
        }
        OptimadeStructuresParser.OptimadeResponse response = parsed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Artifact: ").append(source.getName())
                .append("  (LOCAL unfetched file - it is the only provenance "
                        + "available; nothing was downloaded or verified against a "
                        + "database.)\n");
        text.append(String.format(Locale.ROOT, "data_returned claim by the file: %s%n",
                response.getDataReturnedClaim() == null ? "(not supplied)"
                        : response.getDataReturnedClaim()));
        text.append("provider.name claim by the file: ").append(
                response.getProviderNameClaim().isEmpty() ? "(not supplied)"
                        : response.getProviderNameClaim()).append('\n');
        text.append(String.format(Locale.ROOT, "Structures parsed exactly as given: %d%n%n",
                response.getStructures().size()));
        int cap = 40;
        int shown = 0;
        for (OptimadeStructuresParser.Structure structure : response.getStructures()) {
            shown += 1;
            if (shown > cap) {
                text.append(String.format(Locale.ROOT,
                        "... %d further structure(s) follow (ALL parsed; display capped at "
                                + "%d, nothing dropped from the CSV).%n",
                        response.getStructures().size() - cap, cap));
                break;
            }
            text.append(String.format(Locale.ROOT,
                    "  id=%s  formula=%s  nsites=%s  elements=%s  lattice=%s%n",
                    structure.getId(), structure.getFormula(),
                    structure.getNsites() == null ? "(not supplied)"
                            : structure.getNsites().toString(),
                    structure.getElements().isEmpty() ? "(not supplied)"
                            : String.join(" ", structure.getElements()),
                    structure.hasLattice() ? "present (OPTIMADE units: nm - not "
                            + "re-based here)" : "absent"));
        }
        text.append("\nHonesty block: ids are REQUIRED and never invented; optional "
                + "fields absent from the file print as '(not supplied)', never as "
                + "defaults. Retrieval + caching + provider filtering remain the #117 "
                + "runtime work; pairing this parse with the OPTIMADE_QUERY_DRAFT is "
                + "the intended review workflow.\n");
        List<String> csv = new ArrayList<>();
        csv.add("id,formula_reduced,nsites,elements,lattice,nm_units_stated");
        for (OptimadeStructuresParser.Structure structure : response.getStructures()) {
            csv.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%s",
                    csvCell(structure.getId()), csvCell(structure.getFormula()),
                    structure.getNsites() == null ? "" : structure.getNsites().toString(),
                    csvCell(structure.getElements().isEmpty() ? ""
                            : String.join(" ", structure.getElements())),
                    structure.hasLattice() ? "present" : "absent",
                    structure.hasLattice() ? "nm" : ""));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #116 (parse slice): reads a LOCAL, already-saved mp-api v2
     * /materials/summary response body. Nothing is fetched or keyed here -
     * retrieval (via the MP_QUERY_DRAFT builder) remains #116 runtime depth.
     * Band gaps print in eV and hull energies in eV/atom every time; absent
     * optional fields print the explicit "(not supplied)" sentinel - a
     * missing band gap is NOT a zero gap and is never rendered as 0.0.
     */
    private static AnalysisReport analyzeMpSummaryParse(File source) {
        String label = AnalysisKind.MP_SUMMARY_PARSE.getLabel();
        OperationResult<List<MpSummaryParser.SummaryDoc>> parsed =
                MpSummaryParser.parseFile(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "The Materials Project summary parse refused "
                    + source.getName() + ":\n[" + parsed.getCode() + "] "
                    + parsed.getMessage());
        }
        List<MpSummaryParser.SummaryDoc> docs = parsed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Artifact: ").append(source.getName())
                .append("  (LOCAL unfetched file - it is the only provenance "
                        + "available; nothing was downloaded and no API key was "
                        + "used.)\n");
        text.append(String.format(Locale.ROOT,
                "Summary documents parsed exactly as given: %d%n%n", docs.size()));
        int cap = 40;
        int shown = 0;
        for (MpSummaryParser.SummaryDoc doc : docs) {
            shown += 1;
            if (shown > cap) {
                text.append(String.format(Locale.ROOT,
                        "... %d further document(s) follow (ALL parsed; display capped "
                                + "at %d, nothing dropped from the CSV).%n",
                        docs.size() - cap, cap));
                break;
            }
            text.append(String.format(Locale.ROOT,
                    "  id=%s  formula=%s  nsites=%s  band_gap=%s  E_above_hull=%s  "
                            + "stable=%s%n",
                    doc.getMaterialId(), doc.getFormulaPretty(),
                    doc.getNsites() == null ? MpSummaryParser.NOT_SUPPLIED
                            : doc.getNsites().toString(),
                    doc.getBandGapEv() == null ? MpSummaryParser.NOT_SUPPLIED
                            : String.format(Locale.ROOT, "%.6f eV", doc.getBandGapEv()),
                    doc.getEnergyAboveHullEvPerAtom() == null ? MpSummaryParser.NOT_SUPPLIED
                            : String.format(Locale.ROOT, "%.6f eV/atom",
                                    doc.getEnergyAboveHullEvPerAtom()),
                    doc.getIsStable() == null ? MpSummaryParser.NOT_SUPPLIED
                            : doc.getIsStable().toString()));
        }
        text.append("\nHonesty block: ids are required and never invented; optional "
                + "fields absent from the file print '(not supplied)' - a missing band "
                + "gap is NOT a zero gap. Units follow the mp-api summary schema "
                + "(band_gap in eV; energy_above_hull in eV/atom) and are stated every "
                + "time. Retrieval (key-safe), pagination and caching remain the #116 "
                + "runtime work; pair with the MP_QUERY_DRAFT for the intended review "
                + "workflow.\n");
        List<String> csv = new ArrayList<>();
        csv.add("material_id,formula_pretty,nsites,band_gap_ev,energy_above_hull_ev_per_atom,"
                + "is_stable");
        for (MpSummaryParser.SummaryDoc doc : docs) {
            csv.add(String.format(Locale.ROOT, "%s,%s,%s,%s,%s,%s",
                    csvCell(doc.getMaterialId()), csvCell(doc.getFormulaPretty()),
                    doc.getNsites() == null ? "" : doc.getNsites().toString(),
                    doc.getBandGapEv() == null ? ""
                            : String.format(Locale.ROOT, "%.6f", doc.getBandGapEv()),
                    doc.getEnergyAboveHullEvPerAtom() == null ? ""
                            : String.format(Locale.ROOT, "%.6f",
                                    doc.getEnergyAboveHullEvPerAtom()),
                    doc.getIsStable() == null ? "" : doc.getIsStable().toString()));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #47 (occupation half): line-provenanced HOMO/LUMO review of a
     * pw log. Repeating pairs across SCF steps are ALL shown (no silent
     * 'last wins'); single-value lines mean the gap is undefined; spin
     * attribution is never guessed; an empty result is a needle statement,
     * not a health certificate.
     */
    private static AnalysisReport analyzeOccupationLevelsReview(File source) {
        String label = AnalysisKind.OCCUPATION_LEVELS_REVIEW.getLabel();
        OperationResult<OccupationLevelsParser.OccupationReview> reviewed =
                OccupationLevelsParser.review(source.toPath());
        if (!reviewed.isSuccess() || reviewed.getValue().isEmpty()) {
            return failure(label, "The occupation-level review refused "
                    + source.getName() + ":\n[" + reviewed.getCode() + "] "
                    + reviewed.getMessage());
        }
        OccupationLevelsParser.OccupationReview review = reviewed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Scanned %d line(s); occupation-level statements found: %d "
                        + "(HOMO-LUMO pairs: %d; single HOMO-only lines: %d).%n",
                review.getLinesScanned(), review.getOccurrences().size(),
                review.getPairCount(), review.getSingleCount()));
        if (review.getOccurrences().isEmpty()) {
            text.append("\nNo occupation-level line was found. This is a needle "
                    + "statement ONLY - it says the pw.x HOMO/LUMO print is "
                    + "absent (early run, non-pw program, or log level), and it "
                    + "is NEITHER a convergence nor a run-quality certificate.\n");
        } else {
            int cap = 40;
            text.append(String.format(Locale.ROOT,
                    "Occurrences (line-provenanced; ALL shown up to %d, nothing "
                            + "silently 'last-wins'):%n",
                    cap));
            int shown = 0;
            for (OccupationLevelsParser.OccupationLine line : review.getOccurrences()) {
                if (shown >= cap) {
                    break;
                }
                if (Double.isNaN(line.getLumoEv())) {
                    text.append(String.format(Locale.ROOT,
                            "  line %d: HOMO-only = %+.6f eV  (metallic/smearing "
                                    + "occupations; gap UNDEFINED - no LUMO was "
                                    + "printed and none is invented)%n",
                            line.getLineNumber(), line.getHomoEv()));
                } else {
                    text.append(String.format(Locale.ROOT,
                            "  line %d: HOMO = %+.6f eV, LUMO = %+.6f eV, "
                                    + "line-gap = %.6f eV%n",
                            line.getLineNumber(), line.getHomoEv(), line.getLumoEv(),
                            line.getGapEv()));
                }
                shown += 1;
            }
            if (review.getOccurrences().size() > cap) {
                text.append(String.format(Locale.ROOT,
                        "  ... %d more occurrence(s) not displayed (counted "
                                + "above).%n",
                        review.getOccurrences().size() - cap));
            }
        }
        text.append("\nHonesty block: a later pair is the converged one ONLY if "
                + "the run converged - this parser does not establish "
                + "convergence; these lines carry no spin-channel label, so "
                + "per-channel attribution is never guessed; the line-gap is "
                + "the single-cell HOMO-LUMO statement and complements - never "
                + "replaces - the k-resolved VBM/CBM/directness verdict of the "
                + "BAND_GAP_BANDS kind (#47 curve half).\n");
        List<String> csv = new ArrayList<>();
        csv.add("line,homo_ev,lumo_ev,line_gap_ev,kind");
        for (OccupationLevelsParser.OccupationLine line : review.getOccurrences()) {
            csv.add(String.format(Locale.ROOT, "%d,%+.6f,%s,%s,%s",
                    line.getLineNumber(), line.getHomoEv(),
                    Double.isNaN(line.getLumoEv()) ? ""
                            : String.format(Locale.ROOT, "%+.6f", line.getLumoEv()),
                    Double.isNaN(line.getGapEv()) ? "undefined"
                            : String.format(Locale.ROOT, "%.6f", line.getGapEv()),
                    Double.isNaN(line.getLumoEv()) ? "homo_only" : "pair"));
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #67: cp.x draft from the live input context. The draft is
     * deliberately NON-RUNNABLE (every CP-physics choice is REQUIRED-EDIT)
     * and it substitutes for the invalid pw.x calculation='cp' pattern.
     */
    private static AnalysisReport analyzeCpInputDraft(Project project) {
        String label = AnalysisKind.CP_INPUT_DRAFT.getLabel();
        QEInput input;
        try {
            project.resolveQEInputs();
            input = project.getQEInputCurrent();
        } catch (RuntimeException ex) {
            return failure(label, "Resolving the current QE input failed: "
                    + ex.getMessage());
        }
        OperationResult<CpInputPlanner.CpContext> extracted =
                CpInputPlanner.extractContext(input);
        if (!extracted.isSuccess() || extracted.getValue().isEmpty()) {
            return failure(label, "cp.x draft refused: [" + extracted.getCode() + "] "
                    + extracted.getMessage());
        }
        CpInputPlanner.CpContext context = extracted.getValue().get();
        String draft = CpInputPlanner.draft(context);
        int requiredEdits = 0;
        for (int idx = draft.indexOf("REQUIRED-EDIT"); idx >= 0;
                idx = draft.indexOf("REQUIRED-EDIT", idx + 1)) {
            requiredEdits += 1;
        }
        StringBuilder text = new StringBuilder();
        text.append("Context detected in the live input:\n");
        text.append(String.format(Locale.ROOT, "  prefix = '%s'; outdir = '%s'%n",
                context.getPrefix(), context.getOutdir()));
        text.append(String.format(Locale.ROOT, "  calculation = %s%n",
                context.getCalculation() == null ? "(unset)"
                        : "'" + context.getCalculation() + "'"));
        if (context.usesInvalidPwCalculation()) {
            text.append("\nWARNING: calculation='cp' is NOT a valid pw.x calculation - "
                    + "pw.x rejects it (Roadmap #67 exists to replace this pattern). "
                    + "The dedicated cp.x executable with a dedicated input is the "
                    + "tool; the draft below is its starting skeleton.\n");
        }
        text.append(String.format(Locale.ROOT,
                "%nThe draft is DELIBERATELY NOT RUNNABLE: %d REQUIRED-EDIT placeholders "
                        + "stand exactly where Car-Parrinello physics decisions belong "
                        + "(fictitious mass emass / emass_cutoff, timestep dt, ionic "
                        + "temperature control), and structural cards are NOT copied.%n",
                requiredEdits));
        text.append("\nPrerequisites named by #67 before any production CP run: a "
                + "converged pw.x scf/relax at production cutoff, a dt/emass ADIABATICITY "
                + "check per system (electron/ion energy drift on a short test), and the "
                + "version-matched INPUT_CP documentation. The draft carries the "
                + "REQUIRED-EDIT guard header; it is written ONLY via the explicit save "
                + "action. Post-run energy/drift review is the existing CP_TRAJECTORY "
                + "kind (its parser handles the cp.x log shape).");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "prefix,%s,verbatim", csvCell(context.getPrefix())));
        csv.add(String.format(Locale.ROOT, "outdir,%s,verbatim", csvCell(context.getOutdir())));
        csv.add(String.format(Locale.ROOT, "calculation,%s,verbatim",
                context.getCalculation() == null ? "unset" : context.getCalculation()));
        csv.add(String.format(Locale.ROOT, "invalid_pw_cp,%s,audit",
                context.usesInvalidPwCalculation()));
        csv.add(String.format(Locale.ROOT, "required_edit_placeholders,%d,draft-guard",
                requiredEdits));
        return new AnalysisReport(label, true, text.toString(), csv, draft);
    }

    /**
     * Roadmap #85: parametric commensurate-twist preview with EXACT rational
     * geometry. No structure is built; the live cell, when present, only
     * scales the moire length to Angstrom for context.
     */
    private static AnalysisReport analyzeMoireTwistPreview(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.MOIRE_TWIST_PREVIEW.getLabel();
        OperationResult<MoireTwistMath.MoireTwist> computed = MoireTwistMath.compute(
                params.getMoireM(), params.getMoireN(), params.getLatticeRatio());
        if (!computed.isSuccess() || computed.getValue().isEmpty()) {
            return failure(label, "Twist preview refused: [" + computed.getCode() + "] "
                    + computed.getMessage());
        }
        MoireTwistMath.MoireTwist twist = computed.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Commensurate pair: (m, n) = (%d, %d)%s%n", twist.getM(), twist.getN(),
                twist.getCommonFactor() > 1
                        ? " (your input had common factor " + twist.getCommonFactor()
                                + "; this is the SAME family)"
                        : ""));
        text.append(String.format(Locale.ROOT,
                "CSL index: Sigma = %d (raw m^2+mn+n^2 = %d%s)%n", twist.getSigma(),
                twist.getSigmaRaw(),
                twist.getSigma() != twist.getSigmaRaw() ? ", /3 because 3 | (m-n)" : ""));
        text.append(String.format(Locale.ROOT,
                "cos(theta) = %d/%d EXACTLY -> theta = %.6f degrees%n",
                twist.getCosNumerator(), twist.getCosDenominator(), twist.getThetaDeg()));
        text.append(String.format(Locale.ROOT,
                "Lattice ratio a2/a1 = %.6f; required coherent strain of layer 2 = %.4f%% "
                        + "%s%n",
                twist.getLatticeRatio(), 100.0 * twist.getRequiredStrainLayer2(),
                twist.getRequiredStrainLayer2() == 0.0
                        ? "(identical lattices - truly commensurate)"
                        : "(WITHOUT it the pair is only quasi-commensurate)"));
        text.append(String.format(Locale.ROOT,
                "Moire period: L = %.6f * a1 (exact-theta mismatch formula)%n",
                twist.getMoireLength()));
        text.append(String.format(Locale.ROOT,
                "Atoms in the coincidence cell: Sigma x (layer-1 primitive atoms) + Sigma x "
                        + "(layer-2 primitive atoms); a honeycomb bilayer: 4 x %d = %d "
                        + "atoms.%n",
                twist.getSigma(), 4L * twist.getSigma()));
        Cell cell = project == null ? null : project.getCell();
        if (cell != null) {
            double[][] lattice = cell.copyLattice();
            double a1 = Math.hypot(lattice[0][0], lattice[0][1]);
            if (a1 > 0.0) {
                text.append(String.format(Locale.ROOT,
                        "Live-cell context: in-plane |a1| = %.6f Ang -> L ~= %.4f Ang "
                                + "(context only; the cell's own orientation is not "
                                + "checked for hexagonality here).%n",
                        a1, a1 * twist.getMoireLength()));
            }
        }
        text.append("\nHonesty boundary: NOTHING is constructed - no rotated cell, no "
                + "atom mapping, no relaxation. The exact (m,n) orientation is a lattice "
                + "geometry fact; the atom-level moire cell, interlayer registry (AA/AB "
                + "stacking), and any built structure are the remaining #85 builder work. "
                + "For a2 != a1 the exact incommensurability is stated via the required "
                + "strain rather than silently straining or ignoring it.");
        List<String> csv = new ArrayList<>();
        csv.add("quantity,value,unit");
        csv.add(String.format(Locale.ROOT, "m,%d,int", twist.getM()));
        csv.add(String.format(Locale.ROOT, "n,%d,int", twist.getN()));
        csv.add(String.format(Locale.ROOT, "sigma,%d,int", twist.getSigma()));
        csv.add(String.format(Locale.ROOT, "theta_deg,%.8f,deg", twist.getThetaDeg()));
        csv.add(String.format(Locale.ROOT, "lattice_ratio,%.8f,1",
                twist.getLatticeRatio()));
        csv.add(String.format(Locale.ROOT, "moire_period_over_a1,%.8f,1",
                twist.getMoireLength()));
        csv.add(String.format(Locale.ROOT, "required_strain_layer2,%.8f,1",
                twist.getRequiredStrainLayer2()));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #54: static ESM/slab readiness audit over the live input and
     * cell. Verbatim keyword reporting; the geometry gate enforces the QE z
     * orientation (a1_z = a2_z = 0) and reports an honest vacuum gap.
     */
    private static AnalysisReport analyzeEsmSlabCheck(Project project) {
        String label = AnalysisKind.ESM_SLAB_CHECK.getLabel();
        QEContext context = requireInputAndCell(project, label);
        if (context.failure != null) {
            return context.failure;
        }
        OperationResult<QEEsmAuditor.EsmAudit> audited =
                QEEsmAuditor.audit(context.input, context.cell);
        if (!audited.isSuccess() || audited.getValue().isEmpty()) {
            return failure(label, "ESM audit failed closed: [" + audited.getCode() + "] "
                    + audited.getMessage());
        }
        QEEsmAuditor.EsmAudit audit = audited.getValue().get();
        StringBuilder text = new StringBuilder();
        text.append("Keyword audit (&SYSTEM, values verbatim, quotes stripped):\n");
        text.append(String.format(Locale.ROOT,
                "  assume_isolated = %s%n", audit.getAssumeIsolated() == null
                        ? "(unset - QE default 'none')" : "'" + audit.getAssumeIsolated() + "'"));
        text.append(String.format(Locale.ROOT,
                "  esm_bc          = %s%n", audit.getEsmBc() == null
                        ? "(unset - QE default 'pbc')" : "'" + audit.getEsmBc() + "'"));
        text.append(String.format(Locale.ROOT,
                "  esm_w           = %s%n", audit.getEsmW() == null
                        ? "(unset - QE default -0.2 a.u.)" : audit.getEsmW()));
        text.append(String.format(Locale.ROOT,
                "  esm_efield      = %s%n%n", audit.getEsmEfield() == null
                        ? "(unset - no field)" : audit.getEsmEfield()));
        switch (audit.getVerdict()) {
        case READY:
            text.append("Keyword verdict: READY - assume_isolated='esm' with esm_bc='")
                    .append(audit.getEsmBc()).append("' (open circuit; bc1=bare/BC1, "
                            + "bc2=BC2, bc3=BC3 boundary variants - see the ESM paper "
                            + "Otani-Sugino PRB 73 115407).\n");
            break;
        case ACTIVE_BUT_PERIODIC:
            text.append("Keyword verdict: ESM ACTIVE BUT PERIODIC - esm_bc is 'pbc' "
                    + "(or unset, which is the QE default): no potential offset, so NO "
                    + "work function can be extracted. Use bc1/bc2/bc3 for an open "
                    + "circuit.\n");
            break;
        default:
            text.append("Keyword verdict: NOT ESM - assume_isolated is ")
                    .append(audit.getAssumeIsolated() == null ? "unset (default 'none')"
                            : "'" + audit.getAssumeIsolated() + "'")
                    .append("; this is not an Effective Screening Medium calculation.\n");
            break;
        }
        text.append(String.format(Locale.ROOT,
                "%nGeometry gate: %s (QE ESM REQUIRES the surface normal along z: "
                        + "a1_z = 0 and a2_z = 0)%n",
                audit.isZPerpendicular() ? "PASS" : "FAIL"));
        text.append(String.format(Locale.ROOT,
                "  |c| = %.6f Ang; slab z extent = %.6f Ang; vacuum gap = %.6f Ang%s%n",
                audit.getCLengthAng(), audit.getSlabExtentAng(), audit.getVacuumGapAng(),
                audit.getVacuumGapAng() < QEEsmAuditor.RECOMMENDED_VACUUM_ANG
                        ? " (below the " + (int) QEEsmAuditor.RECOMMENDED_VACUUM_ANG
                                + " Ang advisory floor - screening is questionable)"
                        : ""));
        boolean overall = audit.getVerdict() == QEEsmAuditor.Verdict.READY
                && audit.isZPerpendicular();
        text.append(String.format(Locale.ROOT, "%nOverall ESM readiness: %s%n",
                overall ? "YES (keywords READY + geometry PASS)"
                        : "NO - fix the flagged gate(s) above"));
        text.append("\nHonesty boundary: this is a STATIC audit of the live input and "
                + "cell only - nothing was executed, parsed from outputs, or modified. "
                + "It does NOT verify a finished ESM run, the pp.x tavg planar-average "
                + "convergence, or the macroscopic plateau; work-function extraction "
                + "from a completed run is the WORK_FUNCTION kind (#54 output side). "
                + "fcp/dipfield couplings and other assume_isolated schemes are named "
                + "but not audited here.");
        List<String> csv = new ArrayList<>();
        csv.add("item,value,note");
        csv.add(String.format(Locale.ROOT, "assume_isolated,%s,verbatim",
                audit.getAssumeIsolated() == null ? "unset" : audit.getAssumeIsolated()));
        csv.add(String.format(Locale.ROOT, "esm_bc,%s,verbatim",
                audit.getEsmBc() == null ? "unset(default-pbc)" : audit.getEsmBc()));
        csv.add(String.format(Locale.ROOT, "esm_w,%s,verbatim",
                audit.getEsmW() == null ? "unset" : audit.getEsmW()));
        csv.add(String.format(Locale.ROOT, "esm_efield,%s,verbatim",
                audit.getEsmEfield() == null ? "unset" : audit.getEsmEfield()));
        csv.add(String.format(Locale.ROOT, "z_perpendicular,%s,geometry-gate",
                audit.isZPerpendicular()));
        csv.add(String.format(Locale.ROOT, "c_length_ang,%.8f,geometry",
                audit.getCLengthAng()));
        csv.add(String.format(Locale.ROOT, "slab_extent_ang,%.8f,geometry",
                audit.getSlabExtentAng()));
        csv.add(String.format(Locale.ROOT, "vacuum_gap_ang,%.8f,geometry",
                audit.getVacuumGapAng()));
        csv.add(String.format(Locale.ROOT, "verdict,%s,keyword-audit",
                audit.getVerdict()));
        csv.add(String.format(Locale.ROOT, "overall_ready,%s,keywords+geometry", overall));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #59: collinear spin magnetization from a paired up/down CUBE set.
     * The user-selected file is the MAJORITY (up) density; the one other .cube
     * is the minority (down). m(r) = rho_up - rho_down integrates to the spin
     * excess = total moment in Bohr magnetons per cell (electron counting
     * identity, g ~= 2). No vector/non-collinear handling.
     */
    private static AnalysisReport analyzeSpinCubeMagnetization(Project project, File file) {
        String label = AnalysisKind.SPIN_CUBE_MAGNETIZATION.getLabel();
        if (file == null) {
            return failure(label, "Select the MAJORITY (up-spin) cube explicitly; the "
                    + "minority (down-spin) cube must be the one other .cube in the "
                    + "project directory.");
        }
        File projectDir = project.getDirectory();
        String sourceCanonical;
        try {
            sourceCanonical = file.getCanonicalPath();
        } catch (IOException ex) {
            return failure(label, "Resolving the selected file failed: " + ex.getMessage());
        }
        File[] others = projectDir == null ? new File[0]
                : projectDir.listFiles(candidate -> {
                    if (!candidate.isFile()
                            || !candidate.getName().toLowerCase(Locale.ROOT)
                                    .endsWith(".cube")) {
                        return false;
                    }
                    try {
                        return !candidate.getCanonicalPath().equals(sourceCanonical);
                    } catch (IOException ex) {
                        return false;
                    }
                });
        if (others == null || others.length == 0) {
            return failure(label, "No minority cube found: place exactly one other .cube "
                    + "(the down-spin density) in the project directory.");
        }
        if (others.length > 1) {
            StringBuilder names = new StringBuilder();
            for (File other : others) {
                names.append(' ').append(other.getName()).append(';');
            }
            return failure(label, "Ambiguous minority choice - " + others.length
                    + " candidate cube(s) besides the selected majority:" + names
                    + " Leave exactly one, or rename the others; no silent choice is "
                    + "made (a wrong pairing fabricates a moment).");
        }
        File minorityFile = others[0];
        OperationResult<QEGridDensityDifference.Grid3D> upRead =
                CubeGridReader.read(file.toPath());
        if (!upRead.isSuccess() || upRead.getValue().isEmpty()) {
            return failure(label, "Majority cube refused: " + upRead.getMessage());
        }
        OperationResult<QEGridDensityDifference.Grid3D> downRead =
                CubeGridReader.read(minorityFile.toPath());
        if (!downRead.isSuccess() || downRead.getValue().isEmpty()) {
            return failure(label, "Minority cube refused: " + downRead.getMessage());
        }
        QEGridDensityDifference.Grid3D up = upRead.getValue().orElseThrow();
        QEGridDensityDifference.Grid3D down = downRead.getValue().orElseThrow();
        double tolerance = 1.0e-4;
        QEGridDensityDifference.DiffResult magnetization =
                QEGridDensityDifference.computeDifference(up, List.of(down), tolerance);
        if (!magnetization.isCompatible() || magnetization.getDifferenceGrid() == null) {
            return failure(label, "The up/down grids are NOT compatible - no moment is "
                    + "computed from misaligned data. Reason: "
                    + magnetization.getDiagnosticMessage());
        }
        QEGridDensityDifference.Grid3D mGrid = magnetization.getDifferenceGrid();
        double nUp = up.integrate();
        double nDown = down.integrate();
        double excessFromSums = nUp - nDown;
        double excessFromDiff = magnetization.getIntegratedChargeDifference();
        double excessFromGrid = mGrid.integrate();
        double deviation = Math.max(Math.abs(excessFromSums - excessFromDiff),
                Math.abs(excessFromSums - excessFromGrid));
        double scale = Math.max(1.0, Math.abs(excessFromSums));
        if (deviation > 1.0e-6 * scale) {
            return failure(label, "The three magnetization integrals disagree by "
                    + String.format(Locale.ROOT, "%.3e", deviation)
                    + " (> 1e-6 relative); refusing to pick one silently.");
        }
        double[][][] values = mGrid.getValues();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        long negative = 0L;
        long count = 0L;
        for (double[][] plane : values) {
            for (double[] row : plane) {
                for (double value : row) {
                    if (!Double.isFinite(value)) {
                        return failure(label, "The magnetization grid contains a "
                                + "non-finite value; refusing to summarize it.");
                    }
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    if (value < 0.0) {
                        negative++;
                    }
                    count++;
                }
            }
        }
        double totalCharge = nUp + nDown;
        double polarization = totalCharge != 0.0 ? excessFromSums / totalCharge : 0.0;
        StringBuilder text = new StringBuilder();
        text.append("Pairing (stated exactly once and applied literally):\n");
        text.append("  m(r) = MAJORITY - MINORITY\n");
        text.append("  MAJORITY (up)   = ").append(file.getName()).append('\n');
        text.append("  MINORITY (down) = ").append(minorityFile.getName())
                .append("\n\n");
        text.append(String.format(Locale.ROOT,
                "Grid: %d x %d x %d voxels; cell volume %.6f Ang^3 (lattices matched "
                        + "within %.1e)%n",
                mGrid.getNx(), mGrid.getNy(), mGrid.getNz(), mGrid.getVolume(), tolerance));
        text.append(String.format(Locale.ROOT,
                "Integrated electrons: N_up = %.6f, N_down = %.6f, total = %.6f "
                        + "(cube payload units; QE pp.x prints e/bohr^3)%n",
                nUp, nDown, totalCharge));
        text.append(String.format(Locale.ROOT,
                "Spin excess (N_up - N_down) = %.6f electrons%n", excessFromSums));
        text.append(String.format(Locale.ROOT,
                "  cross-checks: difference-grid integral %.6f, component-difference "
                        + "integral %.6f (max deviation %.3e)%n",
                excessFromGrid, excessFromDiff, deviation));
        text.append(String.format(Locale.ROOT,
                "TOTAL MAGNETIZATION = %.6f mu_B per cell (spin-1/2 counting identity, "
                        + "g ~= 2; sign follows the up/down choice above)%n",
                excessFromSums));
        text.append(String.format(Locale.ROOT,
                "Spin polarization of the charge = %.4f%n", polarization));
        text.append(String.format(Locale.ROOT,
                "m(r) extrema: min %.6f, max %.6f; minority-sign voxels: %d of %d "
                        + "(%.2f%% - spatial moment reversal is physical information, "
                        + "not an error)%n",
                min, max, negative, count, 100.0 * negative / (double) Math.max(1, count)));
        text.append("\nHonesty boundary: this is the COLLINEAR two-file protocol only; "
                + "non-collinear vector magnetization needs the four mx/my/mz/charge "
                + "grids (pp.x plot_num variants) and remains the remaining #59 work. "
                + "The mu_B figure is a counting identity from the grid sums - no "
                + "orbital/relativistic contribution is included, and the cube payload "
                + "convention is the file's own (a non-charge payload integrates to a "
                + "meaningless number; interpret accordingly). Rendering m(r) as an "
                + "isosurface is the #56 volumetric work.");
        List<String> csv = new ArrayList<>();
        csv.add("quantity,value,unit");
        csv.add(String.format(Locale.ROOT, "n_up,%.8f,electrons", nUp));
        csv.add(String.format(Locale.ROOT, "n_down,%.8f,electrons", nDown));
        csv.add(String.format(Locale.ROOT, "total_charge,%.8f,electrons", totalCharge));
        csv.add(String.format(Locale.ROOT, "spin_excess,%.8f,electrons", excessFromSums));
        csv.add(String.format(Locale.ROOT, "magnetization_per_cell,%.8f,mu_B",
                excessFromSums));
        csv.add(String.format(Locale.ROOT, "spin_polarization,%.8f,1", polarization));
        csv.add(String.format(Locale.ROOT, "m_min,%.8e,payload_unit", min));
        csv.add(String.format(Locale.ROOT, "m_max,%.8e,payload_unit", max));
        csv.add(String.format(Locale.ROOT, "minority_voxel_fraction,%.8f,1",
                negative / (double) Math.max(1, count)));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Roadmap #119: ELATE-convention tensor draft. The tensor is parsed through
     * the STRICT reader (no zero padding), the asymmetry audit is printed, and
     * Born mechanical stability gates the artifact: an unstable tensor yields a
     * failed report and NO draft. The draft itself applies no unit conversion.
     */
    private static AnalysisReport analyzeElasticElateDraft(File source) {
        String label = AnalysisKind.ELASTIC_ELATE_DRAFT.getLabel();
        OperationResult<ELateTensorDraft.TensorBlock> read =
                ELateTensorDraft.readTensor(source.toPath());
        if (!read.isSuccess() || read.getValue().isEmpty()) {
            return failure(label, "ELATE draft refused the file " + source.getName()
                    + ":\n[" + read.getCode() + "] " + read.getMessage());
        }
        ELateTensorDraft.TensorBlock block = read.getValue().get();
        QEElasticStabilityValidator.StabilityResult stability =
                QEElasticStabilityValidator.validateStability(block.getCij());
        StringBuilder diagnostics = new StringBuilder();
        for (String diagnostic : stability.getDiagnostics()) {
            if (diagnostics.length() > 0) {
                diagnostics.append("; ");
            }
            diagnostics.append(diagnostic);
        }
        String summary = diagnostics.length() == 0 ? "no diagnostics" : diagnostics.toString();
        if (!stability.isMechanicallyStable()) {
            return failure(label, "The tensor of " + source.getName()
                    + " parsed but FAILED Born mechanical stability (Sylvester "
                    + "leading-minors): " + summary + "\nELATE directional properties of a "
                    + "mechanically unstable phase are not physical equilibrium properties, "
                    + "so NO draft artifact is emitted - review the tensor with the "
                    + "ELASTIC_STABILITY kind first.");
        }
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Symmetry audit: max |C_ij - C_ji| = %.6g (within the %.0e relative "
                        + "print tolerance vs max |C| = %.6g); tensor used as "
                        + "(C + C^T)/2.%n",
                block.getMaxAsymmetry(), ELateTensorDraft.SYMMETRY_REL_TOLERANCE,
                block.getMaxAbs()));
        text.append("Born mechanical stability (Sylvester leading-minors): STABLE ("
                + summary + ").\n");
        text.append("Convention: Voigt 1=xx 2=yy 3=zz 4=yz 5=xz 6=xy, rows exactly as "
                + "parsed - NO convention transform and NO unit conversion was applied.\n");
        text.append(String.format(Locale.ROOT,
                "max |C_ij| = %.6g (tensor units as printed; thermo_pw prints kbar, ELATE "
                        + "expects GPa - the draft's REQUIRED-EDIT flag does the /10).%n",
                block.getMaxAbs()));
        text.append("\nThe draft below is written ONLY via the explicit 'Save input "
                + "file...' action; QuantumForge never sends the tensor anywhere. "
                + "Reference: Gaillac, Coudert, J. Phys.: Condens. Matter 28 (2016) "
                + "275201. Rendering the anisotropic surfaces inside the GUI remains "
                + "the viewer-level #119/#125 work.");
        String draft = ELateTensorDraft.draft(block, source.getName(), true, summary);
        List<String> csv = new ArrayList<>();
        csv.add("voigt_row,c1,c2,c3,c4,c5,c6");
        double[][] cij = block.getCij();
        for (int i = 0; i < 6; i++) {
            csv.add(String.format(Locale.ROOT, "%d,%.10g,%.10g,%.10g,%.10g,%.10g,%.10g",
                    i + 1, cij[i][0], cij[i][1], cij[i][2], cij[i][3], cij[i][4], cij[i][5]));
        }
        return new AnalysisReport(label, true, text.toString(), csv, draft);
    }

    /**
     * Roadmap #76: fail-closed POSCAR/CONTCAR review. Nothing is imported into
     * the project and nothing is written; the parsed geometry is presented for
     * cross-checking only.
     */
    private static AnalysisReport analyzePoscarReview(File source) {
        String label = AnalysisKind.POSCAR_REVIEW.getLabel();
        OperationResult<PoscarStructureReader.PoscarStructure> parsed =
                PoscarStructureReader.parse(source.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "POSCAR review refused the file " + source.getName()
                    + ":\\n[" + parsed.getCode() + "] " + parsed.getMessage());
        }
        PoscarStructureReader.PoscarStructure structure = parsed.getValue().get();
        double[][] lattice = structure.getLattice();
        double[][] positions = structure.getPositionsAng();
        double volume = Math.abs(
                  lattice[0][0] * (lattice[1][1] * lattice[2][2] - lattice[1][2] * lattice[2][1])
                - lattice[0][1] * (lattice[1][0] * lattice[2][2] - lattice[1][2] * lattice[2][0])
                + lattice[0][2] * (lattice[1][0] * lattice[2][1] - lattice[1][1] * lattice[2][0]));
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY};
        double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY};
        for (double[] pos : positions) {
            for (int axis = 0; axis < 3; axis++) {
                lo[axis] = Math.min(lo[axis], pos[axis]);
                hi[axis] = Math.max(hi[axis], pos[axis]);
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(source.getName()).append('\n');
        text.append("Comment line: ").append(structure.getComment()).append("\n\n");
        if (structure.isVolumeScaled()) {
            text.append(String.format(Locale.ROOT,
                    "Scale factor: %s written -> VOLUME-scaled: linear factor (|s|/V_raw)^(1/3)"
                            + " = %.10f applied to lattice and Cartesian coordinates.%n",
                    Double.toString(structure.getScaleWritten()), structure.getScaleApplied()));
        } else {
            text.append(String.format(Locale.ROOT,
                    "Scale factor: %s (linear, applied to lattice and Cartesian coordinates).%n",
                    Double.toString(structure.getScaleWritten())));
        }
        text.append(String.format(Locale.ROOT, "%nLattice rows (Angstrom, scale applied):%n"));
        for (int i = 0; i < 3; i++) {
            text.append(String.format(Locale.ROOT, "  %18.10f %18.10f %18.10f%n",
                    lattice[i][0], lattice[i][1], lattice[i][2]));
        }
        text.append(String.format(Locale.ROOT, "Cell volume: %.6f Angstrom^3%n%n", volume));
        text.append(String.format(Locale.ROOT,
                "Species: %s%n", structure.getSpecies().isEmpty()
                        ? "ABSENT (VASP 4 layout - the POTCAR order applies and is NOT "
                                + "knowable from this file)"
                        : structure.getSpecies().toString()));
        text.append(String.format(Locale.ROOT,
                "Counts (in order): %d species, %d atoms total; composition %s%n",
                structure.getCounts().length, structure.getTotalAtoms(),
                structure.composition()));
        text.append(String.format(Locale.ROOT, "Coordinate frame: %s%s%n",
                structure.isDirectFrame() ? "Direct (fractional)" : "Cartesian (Angstrom)",
                structure.isSelectiveDynamics() ? ", Selective dynamics ON" : ""));
        if (structure.getOutOfCellCount() > 0) {
            text.append(String.format(Locale.ROOT,
                    "WARNING: %d atom(s) carry DIRECT coordinates outside [0,1) - reported "
                            + "verbatim, never wrapped.%n",
                    structure.getOutOfCellCount()));
        }
        if (structure.isSelectiveDynamics()) {
            int fixed = 0;
            for (boolean flag : structure.getFullyFixed()) {
                if (flag) {
                    fixed += 1;
                }
            }
            text.append(String.format(Locale.ROOT,
                    "Fully fixed atoms (F F F): %d of %d%n", fixed, structure.getTotalAtoms()));
        }
        text.append(String.format(Locale.ROOT,
                "%nCartesian positions (Angstrom) bounding box:%n  min %10.4f %10.4f %10.4f%n"
                        + "  max %10.4f %10.4f %10.4f%n",
                lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
        if (structure.getTrailingNote() != null) {
            text.append('\n').append(structure.getTrailingNote()).append('\n');
        }
        text.append("\nHonesty boundary: this is a REVIEW only - the structure was NOT "
                + "applied to the live project cell, and nothing was written anywhere. "
                + "Species/group ordering is exactly as written (VASP 4 names stay "
                + "anonymous); disordered/partial-occupancy sites have no POSCAR encoding "
                + "and are out of scope; velocities and predictor-corrector blocks are "
                + "reported but not parsed. Cross-check against ASE/pymatgen "
                + "(Roadmap #76 acceptance) must precede any production import path.");
        List<String> csv = new ArrayList<>();
        csv.add("index,species,x_ang,y_ang,z_ang,fully_fixed");
        boolean[] fixedFlags = structure.getFullyFixed();
        int rowLimit = 20000;
        int atom = 0;
        int[] counts = structure.getCounts();
        List<String> species = structure.getSpecies();
        outer: for (int s = 0; s < counts.length; s++) {
            String name = s < species.size() ? species.get(s) : ("#" + (s + 1));
            for (int k = 0; k < counts[s]; k++) {
                if (atom >= rowLimit) {
                    csv.add("# truncated at " + rowLimit + " atom rows (cap)");
                    break outer;
                }
                double[] pos = positions[atom];
                csv.add(String.format(Locale.ROOT, "%d,%s,%.10f,%.10f,%.10f,%s",
                        atom + 1, csvCell(name), pos[0], pos[1], pos[2],
                        structure.isSelectiveDynamics()
                                ? (fixedFlags[atom] ? "F" : "T") : ""));
                atom += 1;
            }
        }
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /** Static site-profile validation (Roadmap #94/#103); connects/probes nothing. */
    private static AnalysisReport analyzeSiteProfile(File source) {
        String label = AnalysisKind.SITE_PROFILE_CHECK.getLabel();
        SiteProfileValidator.SiteProfileReport check = SiteProfileValidator.validate(
                source.toPath());
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(source.getName()).append('\n');
        if (check.getProfile() != null) {
            SiteProfile profile = check.getProfile();
            text.append("Site id: ").append(profile.getId()).append('\n');
            text.append("Scheduler: ").append(profile.getScheduler()).append("; launcher: ")
                    .append(profile.getMpiLauncher()).append('\n');
            text.append("Staging root: ").append(profile.getStagingRoot().isEmpty()
                    ? "(not set)" : profile.getStagingRoot()).append('\n');
            text.append("Scratch root: ").append(profile.getScratchRoot().isEmpty()
                    ? "(not set)" : profile.getScratchRoot()).append('\n');
            text.append("Default partition: ").append(
                    profile.getDefaultPartition().isEmpty()
                            ? "(not set)" : profile.getDefaultPartition());
            text.append("; modules: ").append(profile.getModules().size()).append('\n');
        }
        text.append('\n');
        long errors = check.errorCount();
        text.append("Blocking errors: ").append(errors).append("; findings total: ")
                .append(check.getIssues().size()).append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("severity,code,message,documentation");
        for (ValidationIssue issue : check.getIssues()) {
            text.append(issue).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("  ").append(issue.getDocumentationUrl()).append('\n');
            }
            csv.add(csvCell(issue.getSeverity().name()) + "," + csvCell(issue.getCode())
                    + "," + csvCell(issue.getMessage())
                    + "," + csvCell(issue.getDocumentationUrl()));
        }
        if (!check.containerValues("image").isEmpty()) {
            text.append("\nContainer image declared: ")
                    .append(check.containerValues("image").get(0)).append('\n');
        }
        text.append("\nValidation is static: no SSH connection, scheduler probe, or module "
                + "check was attempted; approved here means only internally consistent.");
        return new AnalysisReport(label, errors == 0L && check.getProfile() != null,
                text.toString(), csv, null);
    }

    /** Quotes a CSV cell that may contain separators. */
    static String csvCell(String value) {
        String cell = value == null ? "" : value;
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }

    /**
     * ML model-manifest validation (Roadmap #139) plus the element-level
     * domain-of-applicability gate against the live cell (Roadmap #140, partial).
     * This analysis runs no Python and no inference; it can only state whether a
     * manifest is internally provenance-complete.
     */
    private static AnalysisReport analyzeMlModel(Project project, File file) {
        String label = AnalysisKind.ML_MODEL_CHECK.getLabel();
        if (file == null || !file.isFile()) {
            return failure(label, "Select a model manifest file (key: value text; fields: "
                    + "name, version, license, citation, cutoff_angstrom, species, sha256). "
                    + "No manifest was provided.");
        }
        MlModelManifest.ManifestReport check = MlModelManifest.parse(file.toPath());
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(file.getName()).append('\n');
        MlModelManifest manifest = check.getManifest();
        if (manifest != null) {
            text.append("Model: ").append(manifest.getName().isBlank() ? "(unnamed)"
                    : manifest.getName());
            text.append("  version: ").append(manifest.getVersion().isBlank() ? "(missing)"
                    : manifest.getVersion()).append('\n');
            text.append("License: ").append(manifest.getLicense().isBlank() ? "(missing)"
                    : manifest.getLicense()).append('\n');
            if (!manifest.getCitation().isBlank()) {
                text.append("Citation: ").append(manifest.getCitation()).append('\n');
            }
            if (Double.isFinite(manifest.getCutoffAngstrom())) {
                text.append(String.format(Locale.ROOT, "Cutoff: %.4f Ang%n",
                        manifest.getCutoffAngstrom()));
            }
            text.append("Species declared: ").append(manifest.getSpecies().isEmpty()
                    ? "(none parsed)" : String.join(", ", manifest.getSpecies())).append('\n');
            text.append("Model-file sha256: ").append(manifest.getSha256().isBlank()
                    ? "(missing)" : manifest.getSha256()).append('\n');
        }
        List<String> csv = new ArrayList<>();
        csv.add("severity,code,message,documentation");
        for (ValidationIssue issue : check.getIssues()) {
            text.append(issue).append('\n');
            csv.add(csvCell(issue.getSeverity().name()) + "," + csvCell(issue.getCode())
                    + "," + csvCell(issue.getMessage())
                    + "," + csvCell(issue.getDocumentationUrl()));
        }
        if (!check.getUnparsedLines().isEmpty()) {
            text.append("Unparsed lines (ignored, never guessed): ")
                    .append(check.getUnparsedLines().size()).append('\n');
        }

        boolean domainChecked = false;
        boolean domainOk = true;
        if (manifest != null && !manifest.getSpecies().isEmpty()
                && project.getCell() != null) {
            Set<String> projectElements = new LinkedHashSet<>();
            Atom[] atoms = project.getCell().listAtoms(false);
            if (atoms != null) {
                for (Atom atom : atoms) {
                    if (atom != null) {
                        projectElements.add(atom.getElementName());
                    }
                }
            }
            List<String> missing = manifest.elementsOutsideDomain(projectElements);
            if (!projectElements.isEmpty()) {
                domainChecked = true;
                domainOk = missing.isEmpty();
                text.append('\n');
                text.append("Project elements: ").append(String.join(", ", projectElements))
                        .append('\n');
                if (missing.isEmpty()) {
                    text.append("Domain check (elements): all project elements are covered by "
                            + "the manifest species list.\n");
                } else {
                    text.append("Domain check (elements): OUT OF DOMAIN - not covered: ")
                            .append(String.join(", ", missing)).append('\n');
                    csv.add(csvCell("WARNING") + "," + csvCell("ML_OUT_OF_DOMAIN")
                            + "," + csvCell("Project elements outside manifest species: "
                                    + String.join(", ", missing))
                            + "," + csvCell(MlModelManifest.DOCS));
                }
            }
        }
        text.append('\n');
        if (!domainChecked) {
            text.append("Domain check (elements): not performed (no manifest species or no "
                    + "project cell); this does not make the model applicable.\n");
        }
        text.append("Element coverage is necessary, not sufficient: coordination, density and "
                + "descriptor-distance gates (Roadmap #140) and backend conformance tests "
                + "(Roadmap #138) are separate evidence. No Python was started and no "
                + "inference was run.");
        boolean success = check.isUsable() && domainOk;
        return new AnalysisReport(label, success, text.toString(), csv, null);
    }

    /**
     * EXX/hybrid guidance (Roadmap #70): validates the user-proposed Fock q grid
     * against the current input's automatic k mesh. Physics namelist choices are
     * never guessed; the only quantitative output is the pre-symmetry pair count.
     */
    private static AnalysisReport analyzeExxGuidance(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.EXX_GUIDANCE.getLabel();
        QEContext context = requireInputAndCell(project, label);
        if (context.failure != null) {
            return context.failure;
        }
        QEKPoints kpoints = context.input.getCard(QEKPoints.class);
        if (kpoints == null) {
            return failure(label, "The current input has no K_POINTS card to check against.");
        }
        if (kpoints.isGamma() || !kpoints.isAutomatic()) {
            return failure(label, "EXX q-grid guidance requires an automatic k mesh; the "
                    + "input is " + (kpoints.isGamma() ? "Gamma-only" : "an explicit k-point "
                            + "list") + ", for which no grid statement can be made.");
        }
        int[] kGrid = kpoints.getKGrid();
        int nq1 = params.getExxNq1();
        int nq2 = params.getExxNq2();
        int nq3 = params.getExxNq3();

        QEExxPlanner.ExxGuidance guidance = QEExxPlanner.plan(
                kGrid[0], kGrid[1], kGrid[2], nq1, nq2, nq3);
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Current automatic k mesh: %d %d %d  (offset %d %d %d)%n",
                kGrid[0], kGrid[1], kGrid[2], kpoints.getKOffset()[0],
                kpoints.getKOffset()[1], kpoints.getKOffset()[2]));
        text.append(String.format(Locale.ROOT, "Proposed Fock q mesh: %d %d %d%n%n",
                nq1, nq2, nq3));
        List<String> csv = new ArrayList<>();
        csv.add("axis,n_k,n_q,divides");
        for (int axis = 0; axis < 3; axis++) {
            int nqAxis = axis == 0 ? nq1 : axis == 1 ? nq2 : nq3;
            boolean divides = nqAxis >= 1 && nqAxis <= kGrid[axis]
                    && kGrid[axis] % nqAxis == 0;
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%s", axis + 1, kGrid[axis],
                    nqAxis, divides));
        }
        if (guidance.getKqPairCount() > 0L) {
            text.append(String.format(Locale.ROOT,
                    "k/q pair count before symmetry reduction: %d%n%n",
                    guidance.getKqPairCount()));
        }
        for (ValidationIssue issue : guidance.getIssues()) {
            text.append(issue).append('\n');
            if (!issue.getDocumentationUrl().isEmpty()) {
                text.append("  ").append(issue.getDocumentationUrl()).append('\n');
            }
        }
        text.append("\nThis guidance validates grid compatibility only. It does not start a "
                + "hybrid calculation, does not estimate wall time from a universal factor, "
                + "and leaves input_dft/ecutfock/exxdiv_treatment as explicit physics choices.");
        return new AnalysisReport(label, guidance.isUsable(), text.toString(), csv, null);
    }

    /**
     * Ionic constraint preview (Roadmap #80, data layer): validates a compact
     * constraint specification through the tested {@link QEConstraintSpec},
     * then renders the ATOMIC_POSITIONS block the tested
     * {@link QEIonicConstraintManager} would emit. The text is a generated
     * preview only - the GUI saves it only on explicit user action and the cell
     * and the input are never modified.
     */
    private static AnalysisReport analyzeConstraintsPreview(Project project,
            AnalysisParameters params) {
        String label = AnalysisKind.CONSTRAINTS_PREVIEW.getLabel();
        Cell cell = project.getCell();
        if (cell == null || cell.numAtoms() <= 0) {
            return failure(label, "The project has no atoms to constrain.");
        }
        int natoms = cell.numAtoms();
        String mode = params.getConstraintMode() == null
                ? "relax" : params.getConstraintMode().trim().toLowerCase(Locale.ROOT);
        if (!"relax".equals(mode) && !"vc-relax".equals(mode) && !"md".equals(mode)) {
            return failure(label, "If_pos flags are only interpreted in relax, vc-relax and md "
                    + "calculations; the requested mode \"" + mode + "\" would silently drop "
                    + "them. Choose one of the three modes.");
        }
        OperationResult<QEConstraintSpec> parsed =
                QEConstraintSpec.parse(params.getConstraintSpec(), natoms);
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "Constraint specification refused: " + parsed.getMessage());
        }
        QEConstraintSpec spec = parsed.getValue().orElseThrow();
        QEIonicConstraintManager manager = new QEIonicConstraintManager();
        for (QEConstraintSpec.Entry entry : spec.getEntries()) {
            manager.setConstraint(entry.getAtomIndex0(), entry.getIfX(), entry.getIfY(),
                    entry.getIfZ());
        }
        StringBuilder block = new StringBuilder();
        block.append("ATOMIC_POSITIONS angstrom\n");
        List<String> csv = new ArrayList<>();
        csv.add("atom_index,element,if_pos_x,if_pos_y,if_pos_z,frozen");
        Atom[] atoms = cell.listAtoms();
        for (int i = 0; i < atoms.length; i++) {
            block.append(manager.formatAtomPositionLine(atoms[i], i, mode)).append('\n');
            QEIonicConstraintManager.ConstraintRecord record = manager.getConstraint(i);
            csv.add(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%s", i + 1,
                    csvCell(atoms[i].getName()), record.getIfX(), record.getIfY(),
                    record.getIfZ(), record.getIfX() == 0 || record.getIfY() == 0
                            || record.getIfZ() == 0));
        }
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Mode (flags appended only in relax/vc-relax/md): %s%n", mode));
        text.append(String.format(Locale.ROOT,
                "Atoms: %d; explicitly constrained: %d; with any frozen axis: %d%n%n",
                natoms, spec.getEntries().size(), spec.frozenCount()));
        text.append("Preview block (angstrom coordinates from the live cell model):\n");
        text.append(block);
        text.append("\nHonesty boundary: this is a PREVIEW FOR REVIEW ONLY. Nothing was "
                + "applied to the cell or input; explicit save is the only way out. "
                + "Coordinates are printed in angstrom exactly as the live cell model holds "
                + "them - if your input uses a different ATOMIC_POSITIONS option (bohr, "
                + "crystal, alat), re-express them there. Unlisted atoms render fully free "
                + "(1 1 1), matching QE's default. if_pos semantics: " + QEConstraintSpec.DOCS);
        return new AnalysisReport(label, true, text.toString(), csv, block.toString());
    }

    /**
     * phonopy FORCE_SETS review (Roadmap #107 data layer): a fail-closed
     * structural read through the tested {@link PhonopyForceSetsReader} with
     * displacement/force statistics, an explicit unitless-format caveat, and an
     * informational atom-count cross-check against the live cell (a mismatch is
     * the normal supercell case, not an error). Nothing is converted or run.
     */
    private static AnalysisReport analyzeForceSetsReview(Project project, File file) {
        String label = AnalysisKind.PHONOPY_DATA_REVIEW.getLabel();
        if (file == null || !file.isFile()) {
            return failure(label, "Select a phonopy FORCE_SETS file (legacy "
                    + "finite-displacement format); none was provided.");
        }
        OperationResult<PhonopyForceSetsReader.ForceSets> parsed =
                PhonopyForceSetsReader.parse(file.toPath());
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            return failure(label, "FORCE_SETS refused: " + parsed.getMessage());
        }
        PhonopyForceSetsReader.ForceSets sets = parsed.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("Source: ").append(file.getName()).append('\n');
        text.append(String.format(Locale.ROOT,
                "Atoms per set: %d; displacement sets: %d; distinct displaced atoms: %d%n",
                sets.getNumAtoms(), sets.getSets().size(), sets.countDistinctDisplacedAtoms()));
        if (sets.getTrailingLines() > 0) {
            text.append(String.format(Locale.ROOT,
                    "Trailing non-empty lines after the declared sets (ignored): %d%n",
                    sets.getTrailingLines()));
        }
        List<String> csv = new ArrayList<>();
        csv.add("set_index,displaced_atom_1based,disp_x,disp_y,disp_z,disp_norm,"
                + "max_force_norm,mean_force_norm");
        double globalMax = 0.0;
        double globalSum = 0.0;
        long rows = 0;
        int index = 0;
        int csvCap = 20000;
        for (PhonopyForceSetsReader.DisplacementSet set : sets.getSets()) {
            index++;
            globalMax = Math.max(globalMax, set.maxForceNorm());
            if (index <= csvCap) {
                double[] d = set.getDisplacement();
                csv.add(String.format(Locale.ROOT, "%d,%d,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f",
                        index, set.getAtomIndex(), d[0], d[1], d[2], set.displacementNorm(),
                        set.maxForceNorm(), set.meanForceNorm()));
            }
            for (double[] row : set.getForces()) {
                globalSum += Math.sqrt(row[0] * row[0] + row[1] * row[1] + row[2] * row[2]);
                rows++;
            }
        }
        if (sets.getSets().size() > csvCap) {
            text.append(String.format(Locale.ROOT,
                    "CSV truncated at %d set rows (%d sets in the file).%n", csvCap,
                    sets.getSets().size()));
        }
        text.append(String.format(Locale.ROOT,
                "Global max |F| = %.10f; global mean |F| = %.10f (over %d force rows)%n",
                globalMax, rows == 0L ? 0.0 : globalSum / rows, rows));
        text.append(String.format(Locale.ROOT,
                "Smallest displacement norm: %.10f%n", sets.getSets().stream()
                        .mapToDouble(PhonopyForceSetsReader.DisplacementSet::displacementNorm)
                        .min().orElse(0.0)));
        Cell cell = project.getCell();
        if (cell == null) {
            text.append("\nProject cell cross-check: absent - no atom-count cross-check "
                    + "is possible.");
        } else if (cell.numAtoms() == sets.getNumAtoms()) {
            text.append(String.format(Locale.ROOT,
                    "%nProject cell cross-check: atom counts numerically match (%d); the "
                            + "origin of the file is not verified by this.", sets.getNumAtoms()));
        } else {
            text.append(String.format(Locale.ROOT,
                    "%nProject cell cross-check: the file has %d atoms per set but the live "
                            + "cell has %d - EXPECTED when FORCE_SETS belongs to a supercell; "
                            + "the primitive/supercell mapping is not verified by this review.",
                    sets.getNumAtoms(), cell.numAtoms()));
        }
        text.append(String.format(Locale.ROOT,
                "%n%nHonesty/units boundary: FORCE_SETS is unitless. The phonopy/QE "
                        + "convention writes displacements in Angstrom and forces in "
                        + "eV/Angstrom. If forces were copied raw from pw.x (Ry/bohr) "
                        + "without conversion, every value is wrong by the factor "
                        + "Ry/bohr -> eV/Ang = %.6f. This review displays the numbers as "
                        + "written and cannot detect the unit; "
                        + "QEPhonopyForceSetsWriter.addRecordFromQeRyPerBohr is the tested "
                        + "conversion path. No phonopy run, symmetry treatment, "
                        + "force-constant or band-structure result is implied.",
                QEPhonopyForceSetsWriter.RY_PER_BOHR_TO_EV_PER_ANGSTROM));
        return new AnalysisReport(label, true, text.toString(), csv, null);
    }

    /**
     * Reciprocal Wigner-Seitz zone polyhedron from the live cell lattice
     * (Roadmap #126, geometry layer). The result is accepted only when the volume
     * identity and the Euler characteristic both hold; symmetry labels belong to
     * the SeeK-path analysis, not to this geometric construction.
     */
    private static AnalysisReport analyzeBzGeometry(Project project) {
        String label = AnalysisKind.BZ_GEOMETRY.getLabel();
        Cell cell = project.getCell();
        if (cell == null) {
            return failure(label, "The project has no cell; a lattice is required.");
        }
        OperationResult<QEBrillouinZoneGeometry.BzReport> computed =
                QEBrillouinZoneGeometry.compute(cell.copyLattice());
        if (!computed.isSuccess() || computed.getValue().isEmpty()) {
            return failure(label, "Zone construction failed closed: " + computed.getMessage());
        }
        QEBrillouinZoneGeometry.BzReport zone = computed.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "Reciprocal lattice rows (Ang^-1, 2 pi included):%n"));
        for (double[] row : zone.getReciprocalRows()) {
            text.append(String.format(Locale.ROOT, "  % .8f % .8f % .8f%n",
                    row[0], row[1], row[2]));
        }
        text.append(String.format(Locale.ROOT,
                "%nVertices: %d%nEdges: %d%nFaces: %d%n",
                zone.getVertexCount(), zone.getEdgeCount(), zone.getFaceCount()));
        text.append(String.format(Locale.ROOT,
                "Zone volume: %.8f Ang^-3; expected (2 pi)^3/V_cell: %.8f Ang^-3%n",
                zone.getVolumeInvAng3(), zone.getExpectedVolumeInvAng3()));
        for (String note : zone.getNotes()) {
            text.append(" - ").append(note).append('\n');
        }
        text.append("Shell level used for the half-space enumeration: ")
                .append(zone.getShellsUsed()).append('\n');
        List<String> csv = new ArrayList<>();
        csv.add("vertex_index,x_inv_ang,y_inv_ang,z_inv_ang");
        int index = 0;
        for (double[] vertex : zone.getVertices()) {
            csv.add(String.format(Locale.ROOT, "%d,%.10f,%.10f,%.10f",
                    ++index, vertex[0], vertex[1], vertex[2]));
        }
        text.append("\nVertices are Cartesian coordinates in Ang^-1 (CSV export contains all ")
                .append(zone.getVertexCount()).append(").\n");
        text.append("\nThis is bare lattice geometry: no point-group symmetry was applied and "
                + "no high-symmetry point names are attached - use the spglib/SeeK-path "
                + "analysis for labelled paths. The polyhedron was accepted only because the "
                + "volume identity and the Euler characteristic both hold.");
        return new AnalysisReport(label, zone.isConsistent(), text.toString(), csv, null);
    }

    /**
     * Methods-section draft (Roadmap #123, partial): transcribes parsed input and
     * cell facts into reviewable Markdown. Values are never fabricated; missing
     * physics items are listed explicitly for manual completion.
     */
    private static AnalysisReport analyzeMethodsText(Project project) {
        String label = AnalysisKind.METHODS_TEXT.getLabel();
        QEInput input = project.getQEInputCurrent();
        Cell cell = project.getCell();
        CitationContext context = citationContextFor(project);
        String bibtex = context.manager.compileBibTex();
        MethodsTextBuilder.MethodsDraft draft = MethodsTextBuilder.build(
                input, cell, context.manager.getActiveCitationKeys(), bibtex);
        StringBuilder text = new StringBuilder();
        text.append("Generated from the open input and cell. Items NOT parsed are listed in "
                + "the draft's 'Not recorded' section - ").append(draft.getMissing().size())
                .append(" item(s).\n\n");
        if (input == null) {
            text.append("NOTE: the project has no current input, so every input-dependent "
                    + "field is in the missing list. The draft is still reviewable but "
                    + "contains no transcribed input values.\n\n");
        }
        text.append("The draft below is also what 'Save input file ...' would write; review "
                + "and complete it before use.\n\n");
        text.append(draft.getText());
        return new AnalysisReport(label, input != null, text.toString(), List.of(),
                draft.getText());
    }

    /**
     * RO-Crate metadata draft (Roadmap #135, partial): hashes the project's own
     * artifacts (input copy, log, run manifest) under the 64 MiB bound and
     * composes a deterministic JSON-LD skeleton. Payload packaging is explicit
     * future work; saving the draft is an explicit user action.
     */
    /**
     * The single owner of the RO_CRATE artifact set (Roadmap #135): the
     * project's own input copy, its log, and the run-manifest file, resolved
     * against the on-disk project directory. The metadata-draft analysis and
     * the pack-RO-Crate dialog BOTH build their drafts from this list, so the
     * checksums a user reviews and the bytes the packer pins can never diverge.
     * Returns an empty list when no artifacts exist (callers fail closed).
     */
    public static List<Path> collectRoCrateCandidates(Project project) {
        List<Path> candidates = new ArrayList<>();
        if (project == null || project.getDirectory() == null) {
            return candidates;
        }
        String[] names = {project.getInpFileName(null), project.getLogFileName(null),
                RunManifest.FILE_NAME};
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Path candidate = project.getDirectory().toPath().resolve(name);
            if (Files.isRegularFile(candidate)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private static AnalysisReport analyzeRoCrate(Project project) {
        String label = AnalysisKind.RO_CRATE.getLabel();
        File directory = project.getDirectory();
        if (directory == null) {
            return failure(label, "The project has no on-disk directory to describe.");
        }
        List<Path> candidates = collectRoCrateCandidates(project);
        if (candidates.isEmpty()) {
            return failure(label, "No project artifacts were found to describe (looked for "
                    + "the input file, the log file, and " + RunManifest.FILE_NAME + ").");
        }
        RoCrateExporter.CrateDraft crate = RoCrateExporter.build(
                project.getPrefixName(), directory.toPath(), candidates);
        StringBuilder text = new StringBuilder();
        text.append("Artifacts scanned in: ").append(directory.getAbsolutePath()).append('\n');
        text.append("Included entries: ").append(crate.getEntries().size())
                .append("; skipped: ").append(crate.getSkipped().size()).append("\n\n");
        for (RoCrateExporter.CrateEntry entry : crate.getEntries()) {
            text.append(String.format(Locale.ROOT, "  %-40s %10d bytes  sha256=%s%n",
                    entry.getRelativePath(), entry.getBytes(), entry.getSha256()));
        }
        for (String skipped : crate.getSkipped()) {
            text.append("  SKIPPED: ").append(skipped).append('\n');
        }
        text.append("\nThe JSON-LD draft below (explicitly savable) carries checksums of the "
                + "artifacts as they exist now; re-running a calculation invalidates them. "
                + "Payload packaging is now the viewer action \"Pack RO-Crate folder ...\": it "
                + "materializes exactly these files into a new crate folder, re-hashing every "
                + "byte AFTER copying against the checksums above (any drift aborts before "
                + "anything is activated - the draft would be invalid), and licence/author "
                + "metadata is added only when you type it explicitly; it invents none.");
        return new AnalysisReport(label, !crate.getEntries().isEmpty(), text.toString(),
                List.of(), crate.getJson());
    }

    /**
     * Defect formation energy from fully explicit terms (Roadmap #152, formulation
     * layer). The Fermi level is supplied relative to the host VBM. Charged states
     * require both terms; neutral states ignore them.
     */
    private static AnalysisReport analyzeDefectFormation(AnalysisParameters params) {
        String label = AnalysisKind.DEFECT_FORMATION.getLabel();
        int charge = params.getDefectCharge();
        double vbm = params.getVbmEv();
        double fermiShift = params.getFermiEv(); // Documented as dE_F above the host VBM here.
        if (charge != 0 && (!Double.isFinite(vbm) || !Double.isFinite(fermiShift))) {
            return failure(label, "A charged defect (q != 0) requires the host VBM energy "
                    + "and the Fermi shift dE_F above it (both in eV, explicit).");
        }
        if (charge == 0) {
            vbm = 0.0;
            fermiShift = 0.0;
        }
        OperationResult<Double> result = QEThermochemistryMath.defectFormationEnergy(
                params.getDefectEnergyEv(), params.getHostEnergyEv(), params.getChemPotSumEv(),
                charge, vbm, fermiShift, params.getCorrectionsEv());
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            return failure(label, "Formation energy refused: " + result.getMessage());
        }
        double value = result.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("E_form = E_defect - E_host - sum(n_i*mu_i) + q*(E_VBM + dE_F) "
                + "+ E_corr\n\n");
        text.append(String.format(Locale.ROOT,
                "  E_defect   = %.6f eV%n  E_host     = %.6f eV%n  sum(n_i*mu_i) = %.6f eV%n",
                params.getDefectEnergyEv(), params.getHostEnergyEv(), params.getChemPotSumEv()));
        text.append(String.format(Locale.ROOT,
                "  q          = %d (E_VBM %.6f + dE_F %.6f)%n  E_corr     = %.6f eV%n%n",
                charge, vbm, fermiShift, params.getCorrectionsEv()));
        text.append(String.format(Locale.ROOT, "E_form = %.6f eV%n", value));
        text.append(String.format(Locale.ROOT, "       = %.6e J per defect%n",
                value * 1.602176634e-19));
        text.append("\nAssumptions (not validated numerically here): the chemical-potential "
                + "sum uses one consistent reference set; finite-size (FNV/LK) and potential-"
                + "alignment corrections are included ONLY inside E_corr; vibrational "
                + "contributions are neglected unless included in E_corr. A formation energy "
                + "is not a defect concentration (that needs the full equilibrium equation "
                + "and charge-state surface).");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /**
     * Adsorption energy from fully explicit terms (Roadmap #153, formulation
     * layer): E_ads = E_total - E_slab - E_molecule + E_corr where E_corr may
     * carry the caller's (dZPE - T*dS) term.
     */
    private static AnalysisReport analyzeAdsorptionEnergy(AnalysisParameters params) {
        String label = AnalysisKind.ADSORPTION_ENERGY.getLabel();
        OperationResult<Double> result = QEThermochemistryMath.adsorptionEnergy(
                params.getDefectEnergyEv(), params.getHostEnergyEv(),
                params.getMoleculeEnergyEv(), params.getCorrectionsEv());
        if (!result.isSuccess() || result.getValue().isEmpty()) {
            return failure(label, "Adsorption energy refused: " + result.getMessage());
        }
        double value = result.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("E_ads = E_total - E_slab - E_molecule + E_corr\n\n");
        text.append(String.format(Locale.ROOT,
                "  E_total    = %.6f eV%n  E_slab     = %.6f eV%n  E_molecule = %.6f eV%n"
                        + "  E_corr     = %.6f eV%n%n",
                params.getDefectEnergyEv(), params.getHostEnergyEv(),
                params.getMoleculeEnergyEv(), params.getCorrectionsEv()));
        text.append(String.format(Locale.ROOT, "E_ads = %.6f eV  (negative = binding)%n",
                value));
        text.append(String.format(Locale.ROOT, "       = %.6f kJ/mol%n",
                value * 96.48533212331002));
        text.append("\nAssumptions (not validated numerically here): all three total energies "
                + "come from the same cell, cutoff, and smearing; gas-phase reference quality "
                + "and entropy are the caller's responsibility unless included in E_corr; "
                + "coverage effects require additional terms beyond this three-energy form.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }

    /**
     * Arrhenius single-barrier diffusivity (Roadmap #157): D = D0*exp(-Ea/kBT),
     * D0 = a^2*nu/(2d), with an explicit non-conductivity caveat in every report.
     */
    private static AnalysisReport analyzeBarrierDiffusion(AnalysisParameters params) {
        String label = AnalysisKind.BARRIER_DIFFUSION.getLabel();
        OperationResult<Double> d0 = QEDiffusionBarrierLink.preFactorCm2PerS(
                params.getHopAngstrom(), params.getAttemptThz(), params.getHopDimension());
        if (!d0.isSuccess() || d0.getValue().isEmpty()) {
            return failure(label, "Hop parameters refused: " + d0.getMessage());
        }
        OperationResult<Double> diffusivity = QEDiffusionBarrierLink.estimateDiffusivityCm2PerS(
                params.getBarrierEv(), params.getTemperatureK(), params.getHopAngstrom(),
                params.getAttemptThz(), params.getHopDimension());
        if (!diffusivity.isSuccess() || diffusivity.getValue().isEmpty()) {
            return failure(label, "Arrhenius estimate refused: " + diffusivity.getMessage());
        }
        double d0Value = d0.getValue().orElseThrow();
        StringBuilder text = new StringBuilder();
        text.append("D(T) = D0 * exp(-Ea / kB T);   D0 = a^2 * nu / (2 d)\n\n");
        text.append(String.format(Locale.ROOT,
                "  a (hop length)   = %.6f Ang%n  nu (attempt)     = %.6f THz%n"
                        + "  d (dimension)    = %d%n",
                params.getHopAngstrom(), params.getAttemptThz(), params.getHopDimension()));
        text.append(String.format(Locale.ROOT,
                "  Ea               = %.6f eV%n  T                = %.2f K%n"
                        + "  kB               = %.9e eV/K (CODATA 2018)%n%n",
                params.getBarrierEv(), params.getTemperatureK(),
                QEDiffusionBarrierLink.KB_EV_PER_K));
        text.append(String.format(Locale.ROOT, "D0 = %.6e cm^2/s%n", d0Value));
        text.append(String.format(Locale.ROOT, "D(%.1f K) = %.6e cm^2/s%n",
                params.getTemperatureK(), diffusivity.getValue().orElseThrow()));
        text.append(String.format(Locale.ROOT,
                "Activation factor exp(-Ea/kBT) = %.6e%n",
                diffusivity.getValue().orElseThrow() / d0Value));
        text.append("\nThis is the simplest uncorrelated-hopping form: correlation factors, "
                + "pathway networks, site availability and defect/carrier concentrations are "
                + "NOT included. A single barrier estimate MUST NOT be presented as bulk ionic "
                + "conductivity; kinetic Monte Carlo or MD sampling is required for that.");
        return new AnalysisReport(label, true, text.toString(), List.of(), null);
    }
}
