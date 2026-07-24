#!/usr/bin/env python3
"""Dependency-free repository consistency checks used before Maven/GUI tests."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def error(message: str) -> None:
    ERRORS.append(message)


def text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except Exception as exc:  # report all files instead of stopping at the first
        error(f"{path.relative_to(ROOT)} is not valid UTF-8: {exc}")
        return ""


source_files = list((ROOT / "src").rglob("*.java"))
test_files = list((ROOT / "tests" / "java").rglob("*.java"))
all_java = source_files + test_files

# XML and text encoding.
for path in [ROOT / "pom.xml", *list((ROOT / "src").rglob("*.fxml"))]:
    try:
        ET.parse(path)
    except Exception as exc:
        error(f"invalid XML {path.relative_to(ROOT)}: {exc}")
for path in [*all_java, *list((ROOT / "src").rglob("*.css")),
             *list((ROOT / "src").rglob("*.prop"))]:
    text(path)

# Public top-level Java type/file and package/path agreement.
for path in all_java:
    content = text(path)
    public_type = re.search(
        r"(?m)^public\s+(?:final\s+|abstract\s+)?(?:class|interface|enum)\s+(\w+)", content
    )
    if public_type and public_type.group(1) != path.stem:
        error(f"{path.relative_to(ROOT)} declares public {public_type.group(1)}")
    package = re.search(r"(?m)^package\s+([\w.]+);", content)
    if package:
        source_root = ROOT / "src" if path in source_files else ROOT / "tests" / "java"
        expected = Path(*package.group(1).split(".")) / path.name
        if path.relative_to(source_root) != expected:
            error(f"{path.relative_to(ROOT)} does not match package {package.group(1)}")

# Internal imports resolve to a source/test type (nested types resolve through their owner).
for path in all_java:
    for imported in re.findall(
        r"(?m)^import\s+(?:static\s+)?(quantumforge(?:\.\w+)+)(?:\.\*)?;", text(path)
    ):
        parts = imported.split(".")
        resolved = False
        for length in range(len(parts), 1, -1):
            candidate = Path(*parts[:length]).with_suffix(".java")
            if (ROOT / "src" / candidate).exists() or (ROOT / "tests" / "java" / candidate).exists():
                resolved = True
                break
        if not resolved:
            error(f"{path.relative_to(ROOT)} has unresolved internal import {imported}")

# Version agreement.
pom = text(ROOT / "pom.xml")
version_source = text(ROOT / "src" / "quantumforge" / "ver" / "Version.java")
pom_version = re.search(r"<version>([^<]+)</version>", pom)
java_version = re.search(r'VERSION\s*=\s*"([^"]+)"', version_source)
versions = [pom_version.group(1) if pom_version else None,
            java_version.group(1) if java_version else None]
for prop in ["Environments.unix.prop", "Environments.win.prop"]:
    match = re.search(r"(?m)^version\s*=\s*(\S+)", text(ROOT / "src" / "quantumforge" / "com" / "env" / prop))
    versions.append(match.group(1) if match else None)
if None in versions or len(set(versions)) != 1:
    error(f"version declarations disagree: {versions}")

# Main FXML is manually controlled; every ID must be represented by the controller.
fxml = text(ROOT / "src" / "quantumforge" / "app" / "QEFXMain.fxml")
controller = text(ROOT / "src" / "quantumforge" / "app" / "QEFXMainController.java")
for identifier in set(re.findall(r'fx:id="([^"]+)"', fxml)):
    if not re.search(r"\b" + re.escape(identifier) + r"\b", controller):
        error(f"QEFXMain.fxml id {identifier} is absent from QEFXMainController")

# Local Markdown links and release documentation manifest.
for path in [ROOT / "README.md", *list((ROOT / "docs").glob("*.md"))]:
    for target in re.findall(r"\[[^]]*\]\(([^)]+)\)", text(path)):
        target = target.split("#", 1)[0]
        if target and "://" not in target and not (path.parent / target).resolve().exists():
            error(f"broken local link in {path.relative_to(ROOT)}: {target}")
required_docs = [
    "INSTALLATION.md", "RELEASE_AND_SECURITY.md", "SCIENTIFIC_SOFTWARE_GUIDE.md",
    "CODE_AUDIT.md", "ROADMAP.md",
]
for builder in [ROOT / "packaging" / "build-portable.sh", ROOT / "packaging" / "build-portable.ps1"]:
    content = text(builder)
    for document in required_docs:
        if document not in content:
            error(f"{builder.relative_to(ROOT)} does not package {document}")

# Scientific/credential regressions that must never return silently.
joined_source = "\n".join(text(path) for path in source_files)
for pattern, description in [
    (r"Mocking 5 iterations", "mock convergence loop"),
    (r"Mock constant for", "mock scientific constant"),
    (r"Simplified Debye model", "fabricated phonon thermodynamics"),
    (r"P4/mmm' \(Mock\)", "mock magnetic space group"),
    (r"private String password;", "serializable plaintext SSH password"),
]:
    if re.search(pattern, joined_source):
        error(f"forbidden regression found: {description}")

if ERRORS:
    for item in ERRORS:
        print(f"ERROR: {item}", file=sys.stderr)
    print(f"Static checks failed with {len(ERRORS)} error(s).", file=sys.stderr)
    raise SystemExit(1)
print(f"Static checks passed: {len(source_files)} source and {len(test_files)} test Java files.")
