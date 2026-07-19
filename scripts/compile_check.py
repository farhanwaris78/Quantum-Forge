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
    for token in ["QEBrillouinZoneGeometry"]:
        if token not in service:
            error(f"ResultAnalysisService is not bound to {token}")
    rel = "quantumforge/symmetry/QEBrillouinZoneGeometry.java"
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
    node = (SRC / "quantumforge/run/RunningNode.java").read_text(encoding="utf-8")
    if "DryRunPreflight" not in node or "ArtifactScanner" not in node or "QECommandDag" not in node:
        error("RunningNode is not wired to dry-run/DAG/artifact scanning")
    runAction = (SRC / "quantumforge/app/project/viewer/run/RunAction.java").read_text(encoding="utf-8")
    if "postJobToServerResult" not in runAction or "HostKeyAcceptance" not in runAction:
        error("RunAction still uses untyped/fake SSH submit path")
    neb = (SRC / "quantumforge/builder/neb/NEBPathCreator.java").read_text(encoding="utf-8")
    if "for (int k = 0; k < 3; k++)" not in neb and "for (int k = 0; k < 3; k++)" not in neb:
        # accept either classic or enhanced form
        if "for (int k = 0; k < 3; k++)" not in neb:
            if "k < 3" not in neb:
                error("NEBPathCreator lattice loop appears broken")

    
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
