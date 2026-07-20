#!/usr/bin/env python3
"""Lightweight offline validation of golden QE fixtures and analyzer contracts.

Used when a full JDK/Maven toolchain is unavailable. This is not a substitute
for `mvn verify`, but it catches fixture/regex regressions immediately.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / "tests" / "fixtures" / "qe"
ERRORS: list[str] = []


def error(msg: str) -> None:
    ERRORS.append(msg)


def read(name: str) -> str:
    path = FIX / name
    if not path.is_file():
        error(f"missing fixture {name}")
        return ""
    return path.read_text(encoding="utf-8")


TOTAL_BANG = re.compile(
    r"^\s*!\s*total energy\s*=\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[EeDd][+-]?\d+)?)\s*Ry",
    re.I | re.M,
)
TOTAL_PLAIN = re.compile(
    r"^\s*total energy\s*=\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[EeDd][+-]?\d+)?)\s*Ry",
    re.I | re.M,
)
NOT_CONV = re.compile(r"convergence NOT achieved", re.I)
BFGS = re.compile(r"bfgs converged in\s+\d+\s+scf cycles|End final coordinates", re.I)
MISSING_PSEUDO = re.compile(
    r"Error in routine read_pseudopot|file .*\.UPF not found", re.I
)
HIGH_SYM = re.compile(r"high-symmetry point:")

# Occupation-level needles, mirrored VERBATIM from the owning class
# src/quantumforge/run/parser/OccupationLevelsParser.java (PAIR / SINGLE).
# The Java parser is the single owner; if its grammar changes this mirror
# must change in the same commit or the class-matrix pins below drift.
OCC_PAIR = re.compile(
    r"highest occupied, lowest unoccupied level \(ev\):\s*(\S+)\s+(\S+)"
)
OCC_SINGLE = re.compile(r"highest occupied level \(ev\):\s*(\S+)")


def scf_iterations(text: str) -> list[tuple[float, bool]]:
    rows: list[tuple[float, bool]] = []
    for line in text.splitlines():
        m = TOTAL_BANG.match(line)
        if m:
            rows.append((float(m.group(1).replace("D", "E").replace("d", "e")), True))
            continue
        m = TOTAL_PLAIN.match(line)
        if m:
            rows.append((float(m.group(1).replace("D", "E").replace("d", "e")), False))
    return rows


def main() -> int:
    si = read("scf_si_converged.log")
    rows = scf_iterations(si)
    if len(rows) < 4:
        error(f"si fixture expected >=4 energy lines, got {len(rows)}")
    if not any(converged for _, converged in rows):
        error("si fixture missing ! converged total energy")
    if NOT_CONV.search(si):
        error("si fixture unexpectedly not-converged")
    if OCC_PAIR.findall(si) or OCC_SINGLE.findall(si):
        error("si fixture unexpectedly carries occupation needles")

    bad = read("scf_not_converged.log")
    if not NOT_CONV.search(bad):
        error("non-converged fixture missing marker")
    if not scf_iterations(bad):
        error("non-converged fixture has no energy lines")

    fe = read("scf_fe_spin.log")
    if "nspin" not in fe.lower() and "magnetization" not in fe.lower():
        error("fe spin fixture missing spin/magnetization markers")
    if not any(c for _, c in scf_iterations(fe)):
        error("fe spin fixture missing converged energy")
    if OCC_PAIR.findall(fe) or OCC_SINGLE.findall(fe):
        error("fe metal fixture unexpectedly carries occupation needles")

    # #47 acceptance-class matrix (batch 148): spin-polarized insulator with
    # FIXED occupations prints one PAIR line per SCF step (all kept - no
    # last-wins); an alkali-like partial-occupation metal with SMEARING
    # prints SINGLE lines, and its gap must stay UNDEFINED.
    paired = read("scf_spin_paired.log")
    pair_hits = OCC_PAIR.findall(paired)
    if len(pair_hits) != 4:
        error(f"spin-paired fixture expected 4 pair lines, got {len(pair_hits)}")
    if OCC_SINGLE.findall(paired):
        error("spin-paired fixture unexpectedly carries single lines")
    if "nspin" not in paired.lower():
        error("spin-paired fixture missing nspin marker")
    if paired.lower().count("total magnetization") < 5:
        error("spin-paired fixture expected per-step magnetization lines")
    if "fermi energy" in paired.lower():
        error("spin-paired insulator fixture must NOT print a Fermi energy")
    if not any(c for _, c in scf_iterations(paired)):
        error("spin-paired fixture missing converged energy")
    if NOT_CONV.search(paired):
        error("spin-paired fixture unexpectedly not-converged")

    partial = read("scf_partial_occupation.log")
    single_hits = OCC_SINGLE.findall(partial)
    if len(single_hits) != 2:
        error(f"partial-occupation fixture expected 2 single lines, "
              f"got {len(single_hits)}")
    if OCC_PAIR.findall(partial):
        error("partial-occupation fixture unexpectedly carries pair lines")
    if "fermi energy" not in partial.lower():
        error("partial-occupation fixture missing Fermi-energy line")
    if "smearing" not in partial.lower():
        error("partial-occupation fixture missing smearing marker")
    if "magnetization" in partial.lower():
        error("partial-occupation fixture must NOT claim magnetization")
    if not any(c for _, c in scf_iterations(partial)):
        error("partial-occupation fixture missing converged energy")
    if NOT_CONV.search(partial):
        error("partial-occupation fixture unexpectedly not-converged")

    relax = read("relax_converged.log")
    if not BFGS.search(relax):
        error("relax fixture missing BFGS/end marker")

    if not MISSING_PSEUDO.search(read("error_missing_pseudo.log")):
        error("missing-pseudo fixture not matched")

    bands = read("bands_path.log")
    if len(HIGH_SYM.findall(bands)) < 3:
        error("bands fixture needs >=3 high-symmetry points")

    dos = read("dos_header.log")
    if "E (eV)" not in dos or "EFermi" not in dos:
        error("dos fixture missing header markers")

    # Source presence checks for batch-4 modules
    for rel in [
        "src/quantumforge/run/QECommandDag.java",
        "src/quantumforge/run/QECommandStage.java",
        "src/quantumforge/run/RestartManager.java",
        "src/quantumforge/run/WorkflowExporter.java",
        "src/quantumforge/run/ArtifactScanner.java",
        "src/quantumforge/run/DryRunPreflight.java",
        "src/quantumforge/tools/XCrySDenLauncher.java",
        "src/quantumforge/app/project/viewer/recovery/RecoveryAction.java",
        "src/quantumforge/ssh/KnownHostsStore.java",
        "src/quantumforge/ssh/JschSshTransport.java",
        "src/quantumforge/hpc/SlurmSchedulerAdapter.java",
        "src/quantumforge/symmetry/SpglibService.java",
        "src/quantumforge/app/ssh/HostKeyAcceptance.java",
        "src/quantumforge/ssh/SyncChecksumCache.java",
        "tests/fixtures/qe/data-file-schema.xml",
        "src/quantumforge/hpc/RemoteJobMonitor.java",
        "src/quantumforge/com/secrets/WindowsCredentialBackend.java",
        "src/quantumforge/run/parser/PhononDosThermodynamics.java",
        "src/quantumforge/run/CheckpointResubmit.java",
        "src/quantumforge/builder/neb/NEBPathCreator.java",
        "src/quantumforge/run/parser/QeXmlResultParser.java",
        "src/quantumforge/com/secrets/ProcessKeyringBackend.java",
        "src/quantumforge/hpc/JobQueueStore.java",
        "src/quantumforge/hpc/SgeSchedulerAdapter.java",
        "src/quantumforge/ssh/SelectiveResultSync.java",
        "src/quantumforge/hpc/PbsSchedulerAdapter.java",
        "tools/spglib_sidecar.py",
    ]:
        if not (ROOT / rel).is_file():
            error(f"missing source {rel}")

    if ERRORS:
        print("Fixture harness FAILED:")
        for e in ERRORS:
            print(" -", e)
        return 1
    xml = FIX / "data-file-schema.xml"
    if not xml.is_file():
        error("missing data-file-schema.xml fixture")
    else:
        text = xml.read_text(encoding="utf-8")
        if "fermi_energy" not in text or "etot" not in text:
            error("QE XML fixture missing fermi/etot fields")

    print("Fixture harness passed:", len(list(FIX.glob('*.log'))), "log fixtures")
    return 0


if __name__ == "__main__":
    sys.exit(main())
