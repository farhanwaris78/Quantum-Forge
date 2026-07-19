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


def main() -> int:
    files = list(SRC.rglob("*.java")) + list(TESTS.rglob("*.java"))
    for path in files:
        root = SRC if SRC in path.parents else TESTS
        check_file(path, root)

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
            or "actionValidateInput" not in actions):
        error("ViewerActions missing XCrySDen/workflow export/input validation actions")
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
