#!/usr/bin/env python3
"""Structural compile-readiness checks when javac is unavailable.

Validates packages, public type names, balanced braces (string/comment-aware),
required abstract method implementations, and recovery menu wiring.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
TESTS = ROOT / "tests" / "java"
ERRORS: list[str] = []


def error(msg: str) -> None:
    ERRORS.append(msg)


def strip_strings_and_comments(text: str) -> str:
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        ch = text[i]
        if ch == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            out.append('""')
            continue
        if ch == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            out.append("''")
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def check_file(path: Path, source_root: Path) -> None:
    raw = path.read_text(encoding="utf-8", errors="replace")
    text = strip_strings_and_comments(raw)
    if text.count("{") != text.count("}"):
        error(
            f"brace mismatch {path.relative_to(ROOT)} "
            f"{{ {text.count('{')} }} {text.count('}')}"
        )

    package = re.search(r"(?m)^package\s+([\w.]+);", raw)
    if not package:
        error(f"missing package {path.relative_to(ROOT)}")
        return
    expected_dir = source_root.joinpath(*package.group(1).split("."))
    if path.parent != expected_dir:
        error(f"package path mismatch {path.relative_to(ROOT)} vs {package.group(1)}")

    public = re.search(
        r"(?m)^public\s+(?:final\s+|abstract\s+)?(?:class|interface|enum)\s+(\w+)",
        raw,
    )
    if public and public.group(1) != path.stem:
        error(f"public type {public.group(1)} != file {path.name}")


def build_type_index(files):
    """Maps repo simple type names (top-level and nested) to (package, owner) tuples.

    Nested types are registered both on their own name and are considered
    accessible when their top-level owner is accessible (Outer.Inner usage).
    """
    index: dict[str, list[tuple[str, str | None]]] = {}
    for path in files:
        root = SRC if SRC in path.parents else TESTS
        rel = path.relative_to(root).with_suffix("")
        package = ".".join(rel.parent.parts)
        raw = path.read_text(encoding="utf-8", errors="replace")
        text = strip_strings_and_comments(raw)
        top = None
        for match in re.finditer(r"(?:^|[}\s])(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)", text):
            name = match.group(1)
            if top is None:
                top = name
                owner = None
            else:
                owner = top
            index.setdefault(name, []).append((package, owner))
        if top is None:
            index.setdefault(path.stem, []).append((package, None))
    return index


def check_repo_type_imports(files, index):
    """Flags references to same-repository types that are not imported or otherwise
    accessible. External (JDK/JavaFX/third-party) types cannot be resolved without
    javac and are intentionally skipped.

    A name is accessible when any of these holds:
      * the file itself declares it (top-level or nested);
      * it lives in the same package;
      * it is imported exactly, or via a wildcard import of its package;
      * it is a nested type whose top-level owner is accessible by these rules;
      * an explicit non-repo import shadows the simple name.
    """
    java_lang = {
        "Boolean", "Byte", "Character", "Class", "Double", "Enum", "Exception",
        "Float", "IllegalArgumentException", "IllegalStateException", "Integer",
        "InterruptedException", "Long", "Math", "NullPointerException", "Number",
        "NumberFormatException", "Object", "Override", "RuntimeException",
        "Short", "String", "StringBuilder", "SuppressWarnings", "System",
        "Thread", "Throwable", "Void",
    }
    for path in files:
        root = SRC if SRC in path.parents else TESTS
        package = ".".join(path.relative_to(root).parent.parts)
        raw = path.read_text(encoding="utf-8", errors="replace")
        text = strip_strings_and_comments(raw)

        own_names = set(re.findall(r"(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)", text))
        exact_imports: set[str] = set()
        wildcard_packages: set[str] = set()
        for imp in re.findall(r"(?m)^import\s+(?:static\s+)?([\w.]+?)(\.\*)?;", text):
            target, star = imp
            if star:
                wildcard_packages.add(target)
            else:
                exact_imports.add(target.rsplit(".", 1)[-1])

        # Inline fully-qualified uses (parseTree.Java types embedded in code)
        # need no import; remove them before token scanning.
        code = re.sub(r"\bquantumforge(?:\.[A-Za-z_]\w*)+", " ", text)
        code = re.sub(r"(?m)^\s*(?:import|package)\s+[\w.]+\s*;.*$", " ", code)

        declared = own_names | {path.stem}

        def accessible(name: str, depth: int = 0) -> bool:
            if name in declared or name in exact_imports or name in java_lang:
                return True
            entries = index.get(name)
            if not entries:
                return True  # external type: cannot verify here
            for pkg, owner in entries:
                if pkg == package or pkg in wildcard_packages:
                    return True
            if depth < 1:
                for pkg, owner in entries:
                    if owner and accessible(owner, depth + 1):
                        return True
            return False

        for token in set(re.findall(r"\b([A-Z][A-Za-z0-9_]{2,})\b", code)):
            entries = index.get(token)
            if not entries:
                continue  # not a repo type; JDK/JavaFX names are unverifiable here
            # Dot-qualified member uses (Outer.Inner, Map.Entry, FQN chains) are
            # resolved through their owner; only standalone uses need an import.
            if all(m.start() > 0 and code[m.start() - 1] == "."
                   for m in re.finditer(rf"(?<![\w]){re.escape(token)}\b", code)):
                continue
            if not accessible(token):
                locations = ", ".join(sorted({pkg for pkg, _ in entries}))
                error(
                    f"{path.relative_to(ROOT)} references repository type '{token}' "
                    f"(declared in {locations}) without an import or same-package access"
                )


def main() -> int:
    files = list(SRC.rglob("*.java")) + list(TESTS.rglob("*.java"))
    type_index = build_type_index(files)
    for path in files:
        root = SRC if SRC in path.parents else TESTS
        check_file(path, root)
    check_repo_type_imports(files, type_index)

    project = (SRC / "quantumforge/project/Project.java").read_text(encoding="utf-8")
    if "exportQEInputsTo" not in project:
        error("Project missing exportQEInputsTo")
    for rel in [
        "quantumforge/project/ProjectBody.java",
        "quantumforge/project/ProjectProxy.java",
    ]:
        text = (SRC / rel).read_text(encoding="utf-8")
        if "exportQEInputsTo" not in text:
            error(f"{rel} missing exportQEInputsTo implementation")

    item_set = (SRC / "quantumforge/app/project/viewer/ViewerItemSet.java").read_text(
        encoding="utf-8"
    )
    actions = (SRC / "quantumforge/app/project/viewer/ViewerActions.java").read_text(
        encoding="utf-8"
    )
    if "recoverItem" not in item_set or "getRecoverItem" not in item_set:
        error("ViewerItemSet missing recover item")
    if "actionRecover" not in actions or "RecoveryAction" not in actions:
        error("ViewerActions missing recovery wiring")

    for rel in [
        "quantumforge/run/QECommandDag.java",
        "quantumforge/run/RestartManager.java",
        "quantumforge/run/WorkflowExporter.java",
        "quantumforge/run/ArtifactScanner.java",
        "quantumforge/run/DryRunPreflight.java",
        "quantumforge/tools/XCrySDenLauncher.java",
        "quantumforge/app/project/viewer/recovery/RecoveryAction.java",
        "quantumforge/ssh/KnownHostsStore.java",
        "quantumforge/ssh/JschSshTransport.java",
        "quantumforge/hpc/SlurmSchedulerAdapter.java",
        "quantumforge/hpc/SiteProfile.java",
        "quantumforge/symmetry/SpglibService.java",
        "quantumforge/app/ssh/HostKeyAcceptance.java",
        "quantumforge/ssh/SyncChecksumCache.java",
        "quantumforge/symmetry/SeekPathResult.java",
        "quantumforge/symmetry/StandardizedCell.java",
        "quantumforge/hpc/RemoteJobMonitor.java",
        "quantumforge/com/secrets/WindowsCredentialBackend.java",
        "quantumforge/run/parser/PhononDosThermodynamics.java",
        "quantumforge/run/CheckpointResubmit.java",
        "quantumforge/builder/neb/NEBPathCreator.java",
        "quantumforge/run/parser/QeXmlResultParser.java",
        "quantumforge/com/secrets/ProcessKeyringBackend.java",
        "quantumforge/hpc/JobQueueStore.java",
        "quantumforge/hpc/SgeSchedulerAdapter.java",
        "quantumforge/ssh/JobCancellation.java",
        "quantumforge/ssh/SelectiveResultSync.java",
        "quantumforge/hpc/ResultSyncManifest.java",
        "quantumforge/hpc/PbsSchedulerAdapter.java",
    ]:
        text = (SRC / rel).read_text(encoding="utf-8")
        if "class " not in text and "enum " not in text:
            error(f"{rel} does not declare a type")

    actions = (SRC / "quantumforge/app/project/viewer/ViewerActions.java").read_text(encoding="utf-8")
    if ("actionXcrysden" not in actions or "actionExportWorkflow" not in actions
            or "actionValidateInput" not in actions or "actionDiagnoseLog" not in actions
            or "actionAnalyzeBandGap" not in actions or "actionPreviewFinalGeometry" not in actions
            or "actionInspectPdos" not in actions or "actionInspectPhonons" not in actions
            or "actionInspectSpectra" not in actions or "actionDensityDifference" not in actions
            or "actionAnalyzeResults" not in actions):
        error("ViewerActions missing required export, validation, diagnosis, band-gap, geometry-preview, PDOS, phonon, spectra, density, or result-analysis actions")
    for rel in [
        "quantumforge/run/ResultAnalysisService.java",
        "quantumforge/app/project/viewer/analysis/AnalysisAction.java",
    ]:
        text = (SRC / rel).read_text(encoding="utf-8")
        if "class " not in text and "enum " not in text:
            error(f"{rel} does not declare a type")
    service = (SRC / "quantumforge/run/ResultAnalysisService.java").read_text(encoding="utf-8")
    for token in ["QEBandsDataParser", "QEMagneticMomentParser", "QEBornChargeDielectricParser",
                  "QEHubbardHpParser", "QETurboSpectrumParser", "QEXSpectraXanesParser",
                  "QEGipawNmrParser", "QEPwcondConductanceParser", "QEWannier90SpreadParser",
                  "QEThermoPwEosParser", "QEPhono3pyKappaParser", "QEEliashbergTcCalculator",
                  "QEPpChargePotentialBuilder", "QEPpWavefunctionBuilder",
                  "QEAcousticSumRuleValidator"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["DryRunPreflight", "RestartManager", "QEScratchStoragePolicy",
                  "QEResourceEstimator", "QEMpiTopologyAdvisor", "RunManifest",
                  "GeometryMeasurer", "QEMdDiffusionMsdParser", "QEHullThermodynamics"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["QEBerryPolarizationParser", "QESlabPlateauDiagnostic",
                  "QECarParrinelloParser", "MagneticSpaceGroupDetector",
                  "CubeGridReader", "QECitationManager",
                  "ScfConvergenceAnalyzer", "QETimingResourceParser",
                  "QESmearingConvergenceAnalyzer", "PhononDosThermodynamics",
                  "ElasticParser", "QEElasticStabilityValidator", "QELammpsThermoParser"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["GeometryConvergenceValidator", "PseudoFamilyValidator",
                  "SpglibService", "QeXmlResultParser",
                  "QEVasprunXmlParser", "QECastepLogParser"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["QEInputDiffPreview", "QEKpointMeshAdvisor", "QEPointDefectBuilder"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["QETensorAnalyzer", "QEDynmatModesParser", "QEBatteryVoltage",
                  "MoleculeAdsorber"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["SiteProfileValidator", "SiteProfile"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    text = (SRC / "quantumforge/hpc/SiteProfileValidator.java").read_text(encoding="utf-8")
    if "class " not in text:
        error("quantumforge/hpc/SiteProfileValidator.java does not declare a type")
    for token in ["MlModelManifest"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for token in ["QEExxPlanner"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    rel = "quantumforge/input/QEExxPlanner.java"
    text = (SRC / rel).read_text(encoding="utf-8")
    if "class " not in text:
        error(f"{rel} does not declare a type")
    for token in ["QEBrillouinZoneGeometry", "BandGapParser", "QEPdosParser"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    rel = "quantumforge/symmetry/QEBrillouinZoneGeometry.java"
    text = (SRC / rel).read_text(encoding="utf-8")
    if "class " not in text:
        error(f"{rel} does not declare a type")
    rel = "quantumforge/export/SvgSeriesPlotter.java"
    text = (SRC / rel).read_text(encoding="utf-8")
    if "class " not in text:
        error(f"{rel} does not declare a type")
    if "SvgSeriesPlotter" not in (SRC / "quantumforge/app/project/viewer/analysis/AnalysisAction.java").read_text(encoding="utf-8"):
        error("AnalysisAction is not wired to SvgSeriesPlotter SVG export")
    for token in ["MethodsTextBuilder", "RoCrateExporter", "QEThermochemistryMath",
                  "QEDiffusionBarrierLink", "EffectiveMassTensor", "SymmetricEigen3",
                  "QEConstraintSpec", "QEIonicConstraintManager",
                  "PhonopyForceSetsReader", "QEPhonopyForceSetsWriter",
                  "TrajectoryIndexReader", "ExtXyzDatasetValidator",
                  "EnergySeriesComparer", "TENSOR_EIGEN",
                  "PhononFrameSynthesis", "PHONON_MODE_FRAMES",
                  "HyperfineMapper", "HYPERFINE_LOOKUP",
                  "QEKeywordHelp", "KEYWORD_HELP",
                  "ArraySweepPlanner", "ARRAY_SWEEP_PLAN", "ArrayDeckTemplate",
                  "ArrayTaskIntent", "ArraySubmitPlan",
                  "ExtXyzCellExporter", "CELL_EXTXYZ_EXPORT",
                  "QERamanIRSpectraParser", "RAMAN_IR_SPECTRUM",
                  "TrajectoryWindowReader", "TRAJECTORY_WINDOW_SCAN",
                  "TENSOR_DIRECTIONAL", "QEGridDensityDifference",
                  "DENSITY_DIFFERENCE", "SupercellMatrixValidator",
                  "SUPERCELL_PREVIEW", "QEHubbardPlanner",
                  "HUBBARD_HP_DRAFT", "QETimingParser", "TIMING_RESOURCE",
                  "WorkspaceLightIndex", "WORKSPACE_SEARCH",
                  "QEWorkflowTemplateLibrary", "TEMPLATE_LIBRARY",
                  "PoscarStructureReader", "POSCAR_REVIEW",
                  "ELateTensorDraft", "ELASTIC_ELATE_DRAFT",
                  "SPIN_CUBE_MAGNETIZATION",
                  "QEEsmAuditor", "ESM_SLAB_CHECK",
                  "MoireTwistMath", "MOIRE_TWIST_PREVIEW",
                  "PdbStructureReader", "PDB_REVIEW",
                  "LammpsDataReader", "LAMMPS_DATA_REVIEW",
                  "CpInputPlanner", "CP_INPUT_DRAFT",
                  "Wannier90WinPlanner", "W90_WIN_DRAFT",
                  "CslSigmaMath", "GB_CSL_PREVIEW",
                  "QEVersionRuleCatalog", "QE_VERSION_CHECK",
                  "PoolDivisorMath", "MPI_POOLS_ADVISOR",
                  "QEUnits", "UNIT_CONVERT",
                  "QEErrorSignatureCatalog", "LOG_ERROR_DIAGNOSIS",
                  "XspectraInputPlanner", "XSPECTRA_INPUT_DRAFT",
                  "GipawInputPlanner", "GIPAW_INPUT_DRAFT",
                  "SlabMillerMath", "SLAB_MILLER_PREVIEW",
                  "CifStructureReader", "CIF_REVIEW",
                  "SdfStructureReader", "MOL_SDF_REVIEW",
                  "TurboLanczosInputPlanner", "TDDFPT_INPUT_DRAFT",
                  "CompositionalBaselineMath", "ML_DATASET_BASELINE",
                  "SeriesAlignmentMath", "SERIES_REF_ALIGN",
                  "BandsFermiReviewMath", "BANDS_FERMI_REVIEW",
                  "BandGapBandMath", "BAND_GAP_BANDS",
                  "TransformJournal", "PROVENANCE_JOURNAL_REVIEW",
                  "JobDbSchema", "JOB_DB_SCHEMA_PLAN",
                  "OptimadeQueryBuilder", "OPTIMADE_QUERY_DRAFT",
                  "OccupationLevelsParser", "OCCUPATION_LEVELS_REVIEW",
                  "MpApiQueryBuilder", "MP_QUERY_DRAFT",
                  "SshTargetSpec", "SSH_CONFIG_DRAFT",
                  "SftpTransferPlan", "SFTP_TRANSFER_PLAN",
                  "OptimadeStructuresParser", "OPTIMADE_RESPONSE_PARSE",
                  "MpSummaryParser", "MP_SUMMARY_PARSE",
                  "SlurmScriptBuilder", "SLURM_SCRIPT_DRAFT",
                  "KMeshConvergenceLadder", "KMESH_CONVERGENCE_PLAN",
                  "SiteProfileSpec", "SITE_PROFILE_DRAFT", "ContainerLaunchBridge",
                  "NebInputPlanner", "NEB_INPUT_DRAFT",
                  "JobCancelPlan", "JOB_CANCEL_PLAN",
                  "MonitorPollPlan", "MONITOR_POLL_PLAN",
                  "SyncManifestBuilder", "SYNC_MANIFEST_DRAFT",
                  "SmearingLadderPlan", "SMEARING_LADDER_PLAN",
                  "CutoffLadderPlan", "CUTOFF_LADDER_PLAN",
                  "ArrayJobPlan", "ARRAY_JOB_PLAN",
                  "ContainerProfileSpec", "CONTAINER_PROFILE_DRAFT",
                  "JobStateGuard", "JOB_STATE_GUARD",
                  "PhononGridLadderPlan", "PHONON_GRID_PLAN",
                  "ResubmitAdvice", "CHECKPOINT_RESUBMIT_PLAN",
                  "JobQueueAudit", "JOB_QUEUE_AUDIT",
                  "WorkflowAudit", "WORKFLOW_EXPORT_AUDIT",
                  "NebPathAudit", "NEB_PATH_AUDIT",
                  "FinalGeometryTransaction", "FINAL_GEOMETRY_APPLY",
                  "SchedulerAdapters", "SCHEDULER_ADAPTER_AUDIT",
                  "RemoteJobMonitor", "JOB_MONITOR_AUDIT",
                  "SelectiveResultSync", "SYNC_RUNTIME_AUDIT",
                  "ArraySweepPlanner", "ARRAY_JOB_AUDIT",
                  "JournalReplayMath", "replay_combined_det"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    for rel in ["quantumforge/com/math/SymmetricEigen3.java",
                "quantumforge/builder/QEConstraintSpec.java",
                "quantumforge/run/parser/PhonopyForceSetsReader.java",
                "quantumforge/run/parser/TrajectoryIndexReader.java",
                "quantumforge/neural/ExtXyzDatasetValidator.java",
                "quantumforge/run/parser/EnergySeriesComparer.java",
                "quantumforge/run/parser/PhononFrameSynthesis.java",
                "quantumforge/input/QEKeywordHelp.java",
                "quantumforge/hpc/ArraySweepPlanner.java",
                "quantumforge/hpc/ArrayDeckTemplate.java",
                "quantumforge/hpc/ArrayTaskIntent.java",
                "quantumforge/hpc/ArraySubmitPlan.java",
                "quantumforge/hpc/ArraySubmitSpec.java",
                "quantumforge/ssh/ArraySubmitExecutor.java",
                "quantumforge/ssh/ArrayLoopSubmitExecutor.java",
                "quantumforge/ssh/SSHServerScheduler.java",
                "quantumforge/ssh/SSHConnectRetry.java",
                "quantumforge/ssh/TransferChunkPlan.java",
                "quantumforge/app/project/viewer/run/ArraySubmitAction.java",
                "quantumforge/builder/ExtXyzCellExporter.java",
                "quantumforge/run/parser/TrajectoryWindowReader.java",
                "quantumforge/builder/SupercellMatrixValidator.java",
                "quantumforge/input/QEHubbardPlanner.java",
                "quantumforge/run/parser/QETimingParser.java",
                "quantumforge/project/WorkspaceLightIndex.java",
                "quantumforge/input/QEWorkflowTemplateLibrary.java",
                "quantumforge/builder/PoscarStructureReader.java",
                "quantumforge/export/ELateTensorDraft.java",
                "quantumforge/input/QEEsmAuditor.java",
                "quantumforge/builder/MoireTwistMath.java",
                "quantumforge/builder/PdbStructureReader.java",
                "quantumforge/builder/LammpsDataReader.java",
                "quantumforge/input/CpInputPlanner.java",
                "quantumforge/input/Wannier90WinPlanner.java",
                "quantumforge/builder/CslSigmaMath.java",
                "quantumforge/input/QEVersionRuleCatalog.java",
                "quantumforge/hpc/PoolDivisorMath.java",
                "quantumforge/com/math/QEUnits.java",
                "quantumforge/run/QEErrorSignatureCatalog.java",
                "quantumforge/input/XspectraInputPlanner.java",
                "quantumforge/input/GipawInputPlanner.java",
                "quantumforge/builder/SlabMillerMath.java",
                "quantumforge/builder/CifStructureReader.java",
                "quantumforge/builder/SdfStructureReader.java",
                "quantumforge/input/TurboLanczosInputPlanner.java",
                "quantumforge/neural/CompositionalBaselineMath.java",
                "quantumforge/run/parser/SeriesAlignmentMath.java",
                "quantumforge/run/parser/BandsFermiReviewMath.java",
                "quantumforge/run/parser/BandGapBandMath.java",
                "quantumforge/builder/TransformJournal.java",
                "quantumforge/hpc/JobDbSchema.java",
                "quantumforge/remote/OptimadeQueryBuilder.java",
                "quantumforge/run/parser/OccupationLevelsParser.java",
                "quantumforge/remote/MpApiQueryBuilder.java",
                "quantumforge/remote/SshTargetSpec.java",
                "quantumforge/remote/SftpTransferPlan.java",
                "quantumforge/remote/OptimadeStructuresParser.java",
                "quantumforge/remote/MpSummaryParser.java",
                "quantumforge/remote/SlurmScriptBuilder.java",
                "quantumforge/run/KMeshConvergenceLadder.java",
                "quantumforge/remote/SiteProfileSpec.java",
                "quantumforge/input/NebInputPlanner.java",
                "quantumforge/remote/JobCancelPlan.java",
                "quantumforge/remote/MonitorPollPlan.java",
                "quantumforge/remote/SyncManifestBuilder.java",
                "quantumforge/run/SmearingLadderPlan.java",
                "quantumforge/run/CutoffLadderPlan.java",
                "quantumforge/remote/ArrayJobPlan.java",
                "quantumforge/remote/ContainerProfileSpec.java",
                "quantumforge/remote/ContainerLaunchBridge.java",
                "quantumforge/remote/ContainerImageAttestor.java",
                "quantumforge/remote/JobStateGuard.java",
                "quantumforge/run/PhononGridLadderPlan.java",
                "quantumforge/run/ResubmitAdvice.java",
                "quantumforge/hpc/JobQueueAudit.java",
                "quantumforge/run/WorkflowAudit.java",
                "quantumforge/input/NebPathAudit.java",
                "quantumforge/run/parser/FinalGeometryTransaction.java",
                "quantumforge/hpc/PjmSchedulerAdapter.java",
                "quantumforge/hpc/SchedulerAdapters.java",
                "quantumforge/builder/JournalReplayMath.java"]:
        text = (SRC / rel).read_text(encoding="utf-8")
        if "class " not in text:
            error(f"{rel} does not declare a type")
    rel = "quantumforge/export/MethodsTextBuilder.java"
    text = (SRC / rel).read_text(encoding="utf-8")
    if "class " not in text:
        error(f"{rel} does not declare a type")
    for rel in ["quantumforge/neural/MlModelManifest.java",
                "quantumforge/neural/MLPotentialService.java"]:
        text = (SRC / rel).read_text(encoding="utf-8")
        if "class " not in text:
            error(f"{rel} does not declare a type")
    for stale in ["GNNFroceField", "GNNField"]:
        # Roadmap #136: the misspelled names must not reappear in code (comments
        # and strings are stripped so the historical note in the javadoc stays).
        for path in list(SRC.rglob("*.java")) + list(TESTS.rglob("*.java")):
            raw = path.read_text(encoding="utf-8", errors="replace")
            if stale in strip_strings_and_comments(raw):
                error(f"stale pre-rename name {stale} remains in {path.relative_to(ROOT)}")
                break
    # Maintainer requirement: the name of the premium commercial reference product
    # must never appear in code, tests, docs, or packaging. The software competes on
    # capability and scientific honesty, never on another product's brand.
    forbidden_brand = "Nano" + "Labo"
    brand_paths = [p for p in list(SRC.rglob("*.java")) + list(TESTS.rglob("*.java"))
                   + list(ROOT.glob("*.md")) + list((ROOT / "docs").rglob("*.md"))
                   + list((ROOT / "packaging").rglob("*")) + list((ROOT / "scripts").rglob("*.py"))]
    for path in brand_paths:
        if path.is_file() and path.name != "compile_check.py":
            try:
                raw = path.read_text(encoding="utf-8", errors="replace")
            except (UnicodeDecodeError, OSError):
                continue
            if forbidden_brand.lower() in raw.lower():
                error(f"reference-product brand name remains in {path.relative_to(ROOT)}")
                break
    node = (SRC / "quantumforge/run/RunningNode.java").read_text(encoding="utf-8")
    if "DryRunPreflight" not in node or "ArtifactScanner" not in node or "QECommandDag" not in node:
        error("RunningNode is not wired to dry-run/DAG/artifact scanning")
    runAction = (SRC / "quantumforge/app/project/viewer/run/RunAction.java").read_text(encoding="utf-8")
    if ("postJobToServerResult" not in runAction and "RemoteSubmitChain" not in runAction) \
            or "HostKeyAcceptance" not in runAction:
        error("RunAction still uses untyped/fake SSH submit path")
    if "offerMonitoring" not in runAction or "QEFXJobMonitorDialog" not in runAction:
        error("RunAction lost the job-monitor offer wiring (batch 138)")
    if "transportHandedOff" not in runAction:
        error("RunAction lost exactly-once transport ownership tracking (batch 138)")
    if "qf-ssh-submit" not in runAction or "RemoteSubmitChain" not in runAction:
        error("RunAction lost the background submit worker / headless chain (batch 139)")
    if "SSHConnectRetry" not in runAction or "provenanceLine" not in runAction:
        error("RunAction lost the bounded connect retry with provenance (batch 145)")
    retry = SRC / "quantumforge/ssh/SSHConnectRetry.java"
    if not retry.is_file() or "isRetriable" not in retry.read_text(encoding="utf-8"):
        error("SSHConnectRetry (batch-145 retry combinator) missing its retriable owner")
    transfer = (SRC / "quantumforge/ssh/SSHFileTransfer.java").read_text(encoding="utf-8")
    if "uploadChunkedVerifiedResult" not in transfer or "TRANSFER_CHUNK_STALE" not in transfer:
        error("SSHFileTransfer lost the batch-146 chunked/resumable verified upload")
    syncRuns = (SRC / "quantumforge/ssh/SelectiveResultSync.java").read_text(encoding="utf-8")
    if "downloadVerifiedResult" not in syncRuns or "pin-verified" not in syncRuns:
        error("SelectiveResultSync lost the batch-146 hash-pinned download path")
    if "NOT hash-verified (no pins supplied)" not in syncRuns:
        error("SelectiveResultSync lost the unpinned-posture honesty sentence (batch 146)")
    svcText = (SRC / "quantumforge/run/ResultAnalysisService.java").read_text(encoding="utf-8")
    if "uploadChunkedVerifiedResult" not in svcText or "TransferChunkPlan" not in svcText:
        error("ResultAnalysisService lost the batch-146 integrity boundary/provenance")
    if "waitDialog" not in runAction or "Platform.runLater" not in runAction.replace(
            "javafx.application.Platform.runLater", "Platform.runLater"):
        error("RunAction lost its FX-marshalling/lifecycle discipline (batch 139)")
    hka = (SRC / "quantumforge/app/ssh/HostKeyAcceptance.java").read_text(encoding="utf-8")
    if "isFxApplicationThread" not in hka or "CountDownLatch" not in hka:
        error("HostKeyAcceptance lost its cross-thread audited prompt (batch 139)")
    chain = SRC / "quantumforge/ssh/RemoteSubmitChain.java"
    if not chain.is_file() or "wasTransportClosedByChain" not in chain.read_text(
            encoding="utf-8"):
        error("RemoteSubmitChain (batch 139 headless submit chain) missing ownership flags")
    monitorGui = SRC / "quantumforge/app/ssh/QEFXJobMonitorDialog.java"
    monitorModel = SRC / "quantumforge/app/ssh/MonitorLogModel.java"
    if not monitorGui.is_file() or not monitorModel.is_file():
        error("batch-138 monitor GUI host classes missing")
    else:
        monitorRaw = monitorGui.read_text(encoding="utf-8")
        if "Platform.runLater" not in monitorRaw or "shutdownNow" not in monitorRaw:
            error("QEFXJobMonitorDialog lost its FX-thread/lifecycle discipline")
        if "MAX_LINES" not in monitorModel.read_text(encoding="utf-8"):
            error("MonitorLogModel lost its owned ring bound")
    actionFx = (SRC / "quantumforge/app/project/viewer/analysis/AnalysisAction.java").read_text(
        encoding="utf-8")
    if "withContainerSiteProfile" not in actionFx:
        error("AnalysisAction lost the batch-147 launch-bridge prompt wiring")
    if "launch_bridge" not in svcText:
        error("ResultAnalysisService lost the batch-147 launch-bridge render/csv trail")
    sshJob = (SRC / "quantumforge/ssh/SSHJob.java").read_text(encoding="utf-8")
    if "SSHServerScheduler" not in sshJob:
        error("SSHJob lost the batch-144 single-owner scheduler resolution fix")
    resolver = SRC / "quantumforge/ssh/SSHServerScheduler.java"
    if not resolver.is_file() or "SSH_SCHEDULER_UNSET" not in resolver.read_text(
            encoding="utf-8"):
        error("SSHServerScheduler (batch-144 resolver) missing its typed refusals")
    loopExec = SRC / "quantumforge/ssh/ArrayLoopSubmitExecutor.java"
    if not loopExec.is_file() or "SUBMIT_LOOP_WRONG_SHAPE" not in loopExec.read_text(
            encoding="utf-8"):
        error("ArrayLoopSubmitExecutor (batch 143) missing its loop-only shape guard")
    arrayGui = SRC / "quantumforge/app/project/viewer/run/ArraySubmitAction.java"
    if not arrayGui.is_file():
        error("ArraySubmitAction (batch-144 GUI array-submit dialogue) missing")
    else:
        arrayRaw = arrayGui.read_text(encoding="utf-8")
        if "qf-array-submit" not in arrayRaw or "SSHServerScheduler" not in arrayRaw:
            error("ArraySubmitAction lost its daemon/resolver wiring (batch 144)")
        if "ArrayLoopSubmitExecutor" not in arrayRaw or "ArraySubmitExecutor" not in arrayRaw:
            error("ArraySubmitAction lost the shape-split executor wiring (batch 144)")
        if "QEFXJobMonitorDialog" not in arrayRaw or "transport.close()" not in arrayRaw:
            error("ArraySubmitAction lost the monitor offer / exactly-once closes (batch 144)")
        if "SSHConnectRetry" not in arrayRaw:
            error("ArraySubmitAction lost the bounded connect retry (batch 145)")
    viewerItems = (SRC / "quantumforge/app/project/viewer/ViewerItemSet.java").read_text(
        encoding="utf-8")
    viewerActs = (SRC / "quantumforge/app/project/viewer/ViewerActions.java").read_text(
        encoding="utf-8")
    if "Submit array sweep" not in viewerItems or "ArraySubmitAction" not in viewerActs:
        error("viewer menu lost the batch-144 array-sweep submission entry")
    sshDialog = (SRC / "quantumforge/app/ssh/QEFXSSHDialog.java").read_text(encoding="utf-8")
    sshFxml = (SRC / "quantumforge/app/ssh/QEFXSSHDialog.fxml").read_text(encoding="utf-8")
    if "schedulerCombo" not in sshDialog or "schedulerCombo" not in sshFxml:
        error("SSH dialog lost the batch-144 Job Scheduler chooser")
    neb = (SRC / "quantumforge/builder/neb/NEBPathCreator.java").read_text(encoding="utf-8")
    if "for (int k = 0; k < 3; k++)" not in neb and "for (int k = 0; k < 3; k++)" not in neb:
        # accept either classic or enhanced form
        if "for (int k = 0; k < 3; k++)" not in neb:
            if "k < 3" not in neb:
                error("NEBPathCreator lattice loop appears broken")

    
    # Batch 150: machine-mined QE namelist grammar (7.2-7.6) + audits.
    schemaDir = SRC / "quantumforge/input/schema"
    if not (schemaDir / "QENamelistSchema.java").is_file() \
            or not (schemaDir / "QESchemaData.java").is_file():
        error("batch-150 mined schema classes missing")
    else:
        schemaText = (schemaDir / "QENamelistSchema.java").read_text(encoding="utf-8")
        dataText = (schemaDir / "QESchemaData.java").read_text(encoding="utf-8")
        if "getAcceptedValuesIn" not in schemaText or "getToleratedValueDetails" not in schemaText:
            error("QENamelistSchema lost the hard/soft version-masked accessors (batch 150)")
        if "diago_ppcg_maxiter" not in dataText or "sha256" not in dataText:
            error("QESchemaData lost mined drift entries or sha256 provenance (batch 150)")
    audit = SRC / "quantumforge/input/validation/QESchemaAudit.java"
    validator = SRC / "quantumforge/input/validation/QESchemaValidator.java"
    if not audit.is_file() or "SCHEMA_VALUE_REJECTED" not in audit.read_text(encoding="utf-8"):
        error("QESchemaAudit (batch-150 pair-level core) missing its rejection codes")
    if not validator.is_file() or "validatePairs" not in validator.read_text(encoding="utf-8"):
        error("QESchemaValidator (batch-150 QEInput adapter) missing its core delegation")
    if "Mined schema audit" not in svcText:
        error("ResultAnalysisService lost the batch-150 mined-schema report section")

    # Batch 151: chunked verified download with per-chunk pins.
    transferRaw = (SRC / "quantumforge/ssh/SSHFileTransfer.java").read_text(encoding="utf-8")
    if "downloadChunkedVerifiedResult" not in transferRaw:
        error("SSHFileTransfer lost the batch-151 chunked verified download")
    if "TRANSFER_PART_MISMATCH" not in transferRaw:
        error("SSHFileTransfer lost the batch-151 per-chunk pin mismatch verdict")

    # Batch 152: BoltzTraP2 transport tables (.trace/.condtens), Fortran grammar.
    boltz = SRC / "quantumforge/run/parser/BoltzTrap2TraceParser.java"
    if not boltz.is_file():
        error("BoltzTrap2TraceParser (batch 152) missing")
    else:
        boltzRaw = boltz.read_text(encoding="utf-8")
        if "TENSOR_OTHER" not in boltzRaw or "Fortran" not in boltzRaw:
            error("BoltzTrap2TraceParser lost the tensor-family refusal / Fortran grammar (batch 152)")
    if "BOLTZTRAP2_TRANSPORT" not in svcText or "analyzeBoltzTrap2Transport" not in svcText:
        error("ResultAnalysisService lost the batch-152 BoltzTraP2 transport wiring")

    # Batch 153: eigen solver + latent master compile defects resurrected green.
    eigen = (SRC / "quantumforge/com/math/SymmetricEigen3.java").read_text(encoding="utf-8")
    if "double[] rotation = rotate(a, p, q);" not in eigen:
        error("SymmetricEigen3 lost the batch-153 rotation/accumulate call fix")
    bz = (SRC / "quantumforge/symmetry/QEBrillouinZoneGeometry.java").read_text(encoding="utf-8")
    if "faceCentroid" not in bz:
        error("QEBrillouinZoneGeometry lost the batch-153 index-order fix (sort before faces)")
    prop = (SRC / "quantumforge/project/property/ProjectProperty.java").read_text(encoding="utf-8")
    if "public ProjectProperty()" not in prop:
        error("ProjectProperty lost the batch-153 explicit blank-context ctor")
    dynmat = (SRC / "quantumforge/run/parser/QEDynmatModesParser.java").read_text(encoding="utf-8")
    if "\\\\*+" not in dynmat and '"\\\\*+"' not in dynmat:
        error("QEDynmatModesParser lost the batch-153 decoration-line tolerance")
    wa = (SRC / "quantumforge/run/WorkflowAudit.java").read_text(encoding="utf-8")
    if "private final boolean setOptions;" not in wa:
        error("WorkflowAudit lost the batch-153 setOptions field declaration")

    # Batch 154 (QE-roadmap R3): version-windowed keyword catalog + typed
    # ph.x/hp.x deck planners + phonon-dialog GUI window list.
    catalog = SRC / "quantumforge/input/QEDeckKeywordCatalog.java"
    if not catalog.is_file():
        error("QEDeckKeywordCatalog (batch 154, R3) missing")
    else:
        catalogText = catalog.read_text(encoding="utf-8")
        if "resolveVersion" not in catalogText or "QES_VERSION" not in catalogText \
                or "promptLabel" not in catalogText:
            error("QEDeckKeywordCatalog lost the fail-closed version resolution / prompt rows (batch 154)")
    ph = SRC / "quantumforge/input/PhInputPlanner.java"
    if not ph.is_file():
        error("PhInputPlanner (batch 154, R3) missing")
    else:
        phText = ph.read_text(encoding="utf-8")
        if "PH_KEYWORD_WINDOW" not in phText or "PH_VERSION" not in phText \
                or "auditStaticEmissions" not in phText:
            error("PhInputPlanner lost the version-window refusals or the typed self-audit (batch 154)")
    hp = (SRC / "quantumforge/input/QEHubbardPlanner.java").read_text(encoding="utf-8")
    if "HP_KEYWORD_WINDOW" not in hp or "no_metq0" not in hp or "auditStaticEmissions" not in hp:
        error("QEHubbardPlanner lost the batch-154 version-typed overload / no_metq0 gating")
    phononFx = (SRC / "quantumforge/app/project/editor/input/phonon/QEFXPhononController.java").read_text(encoding="utf-8")
    if "QEDeckKeywordCatalog" not in phononFx or "refreshKeywordWindow" not in phononFx:
        error("QEFXPhononController lost the batch-154 typed grammar window (GUI slice of R3)")
    phononFxml = (SRC / "quantumforge/app/project/editor/input/phonon/QEFXPhonon.fxml").read_text(encoding="utf-8")
    if "qeVersionCombo" not in phononFxml or "keywordWindowList" not in phononFxml:
        error("QEFXPhonon.fxml lost the batch-154 version picker / keyword window list")

    # Batch 155 (QE-roadmap R1+R2): extension-deck accessor + deck-text audit +
    # user-pinned validation version picker.
    qeinp = (SRC / "quantumforge/input/QEInput.java").read_text(encoding="utf-8")
    if "listExtraNamelistKeys" not in qeinp or "NAMELIST_PRESS_AI" not in qeinp \
            or "listAllNamelistKeys" not in qeinp:
        error("QEInput lost the batch-155 R1 extension-deck accessor (FCP/RISM/WANNIER.../PRESS_AI)")
    schemaVal = (SRC / "quantumforge/input/validation/QESchemaValidator.java").read_text(encoding="utf-8")
    if "validateDeckText" not in schemaVal or "CODE_TEXT_LIMIT" not in schemaVal:
        error("QESchemaValidator lost the batch-155 deck-text extension audit or its bounded read")
    vact = (SRC / "quantumforge/app/project/viewer/ViewerActions.java").read_text(encoding="utf-8")
    if "versionDialog" not in vact or "ChoiceDialog<String>" not in vact:
        error("ViewerActions lost the batch-155 R2 user-pinned audit-version picker")
    if "audit the input against which qe grammar window" not in vact.lower():
        error("ViewerActions lost the batch-155 R2 picker text naming the grammar window")

    cap = (SRC / "quantumforge/capability/CapabilityRegistry.java").read_text(encoding="utf-8")
    if "Strict known_hosts" not in cap:
        error("CapabilityRegistry SSH status not updated")
    if "spglib/seekpath sidecar protocol v2" not in cap and "spglib JSON sidecar" not in cap:
        error("CapabilityRegistry symmetry status not updated")

    if ERRORS:
        print(f"Compile-check FAILED ({len(ERRORS)}):")
        for e in ERRORS:
            print(" -", e)
        return 1
    print(f"Compile-check passed for {len(files)} Java files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
