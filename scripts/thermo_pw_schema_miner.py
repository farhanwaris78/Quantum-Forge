#!/usr/bin/env python3
"""Mine the thermo_pw &INPUT_THERMO grammar into generated Java.

Ground truth (mined verbatim, never paraphrased):
  * <repo>/src/thermo_readin.f90
      - the NAMELIST / input_thermo / declaration: the keyword set plus the
        code's OWN group-header comments ("!  mur_lc", "!  scf_disp", ...)
        carried as raw grouping labels (bookkeeping comments, not a spec);
      - the procedural default assignments between the anchors
        "Default values of the input variables" and "IF (meta_ionode) READ(";
      - the post-READ consistency `CALL errore('thermo_readin', ...)` lines,
        kept verbatim as fact lines;
      - the READ(iun_thermo, input_thermo ...) -> errore(...) line proving an
        unknown keyword is FATAL (HARD).
  * <repo>/src/initialize_thermo_work.f90
      - the part-1 SELECT CASE (TRIM(what)) arms with the CASE DEFAULT
        errore (HARD accepted set for `what`).

Output: src/quantumforge/input/schema/QEThermoPwSchemaData.java (generated).

Usage: python3 scripts/thermo_pw_schema_miner.py /path/to/thermo_pw
"""
import hashlib
import re
import subprocess
import sys
from pathlib import Path

NAMELIST_END = re.compile(r"^\s*IF \(meta_ionode\) READ", re.MULTILINE)
DEFAULTS_ANCHOR = "Default values of the input variables"


def sha256_text(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_namelist(readin: str):
    """(ordered unique keywords with group labels), (duplicate names)."""
    start = readin.index("NAMELIST / input_thermo /")
    block_end = readin.index("parse_unit_save", start)
    block = readin[start:block_end]
    group = "declared before any group header"
    lines = block.splitlines()
    lines[0] = lines[0].split("NAMELIST / input_thermo /", 1)[1]
    entries = []  # (name, [groups...])
    for raw in lines:
        comment = re.match(r"^\s*!\s*(.+?)\s*$", raw)
        if comment:
            text = comment.group(1)
            if text:
                group = text
            continue
        code = raw.split("!")[0]
        for name in re.findall(r"[A-Za-z][A-Za-z0-9_]*", code):
            if name in ("NAMELIST",):
                continue
            if not entries or entries[-1][0] != name:
                for existing, groups in entries:
                    if existing == name:
                        if group not in groups:
                            groups.append(group)
                        break
                else:
                    entries.append((name, [group]))
    seen = set()
    unique = []
    duplicates = set()
    for name, _groups in entries:
        if name in seen:
            duplicates.add(name)
        seen.add(name)
    for name, groups in entries:
        if not any(n == name for n, _ in unique):
            unique.append((name, groups))
    return unique, duplicates


def parse_defaults(readin: str):
    start = readin.index(DEFAULTS_ANCHOR)
    end = re.search(NAMELIST_END, readin[start:]).start() + start
    section = readin[start:end]
    defaults = {}
    order = 0
    for raw in section.splitlines():
        code = raw.split("!")[0].strip()
        m = re.match(r"^([A-Za-z][A-Za-z0-9_]*(?:\([^)]*\))?)\s*=\s*(.+?)\s*$", code)
        if not m:
            continue
        name, rhs = m.group(1), m.group(2).rstrip(",")
        # element-wise array assignments are recorded as their base name:
        # the miner does not invent a dimension grammar, the RHS text stays.
        base = name.split("(")[0]
        order += 1
        defaults[base] = (rhs, order)  # last assignment wins (execution order)
    return defaults


def infer_type(rhs: str) -> str:
    rhs = rhs.strip()
    if re.fullmatch(r"\.TRUE\.|\.FALSE\.", rhs.upper()):
        return "LOGICAL"
    if rhs.startswith("'") and rhs.endswith("'"):
        return "CHARACTER"
    if re.fullmatch(r"[+-]?\d+", rhs):
        return "INTEGER"
    core = re.sub(r"_(DP|dp|QP|qp|SP|sp)$", "", rhs)  # kind suffix is part of the literal
    if re.fullmatch(r"[+-]?(\d+\.\d*|\.\d+)([eEdD][+-]?\d+)?", core) \
            or re.fullmatch(r"[+-]?\d+[eEdD][+-]?\d+", core):
        return "REAL"
    return "UNKNOWN"


def parse_what_values(initwork: str) -> list:
    start = initwork.index("SELECT CASE (TRIM(what))")
    end = initwork.index("END SELECT", start)
    block = initwork[start:end]
    if "what not recognized" not in block:
        raise SystemExit("hard-set proof missing: CASE DEFAULT errore not found")
    values = []
    for case in re.findall(r"CASE\s*\(([^)]*)\)", block):
        for literal in re.findall(r"'([^']*)'", case):
            if literal.strip() and literal not in values:
                values.append(literal)
    return values


def parse_consistency_facts(readin: str) -> list:
    start = re.search(NAMELIST_END, readin).start()
    lines = readin[start:].splitlines()
    facts = []
    i = 0
    while i < len(lines) and len(facts) < 24:
        line = lines[i]
        if "END SUBROUTINE thermo_readin" in line:
            break
        if "errore('thermo_readin'" in line:
            stmt = line.strip()
            extra = 0
            # Fortran continuation: trailing '&' on this line, optional leading
            # '&' on the next - join to one statement.
            while stmt.rstrip().endswith("&") and i + extra + 1 < len(lines):
                stmt = stmt.rstrip()[:-1].rstrip()
                nxt = lines[i + extra + 1].strip()
                if nxt.startswith("&"):
                    nxt = nxt[1:].lstrip()
                stmt = stmt + " " + nxt
                extra += 1
            stmt = " ".join(stmt.split())
            if stmt.count("(") == stmt.count(")") and "(" in stmt:
                facts.append(stmt)
            i += extra
        i += 1
    return facts


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    repo = Path(sys.argv[1])
    readin_path = repo / "src/thermo_readin.f90"
    initwork_path = repo / "src/initialize_thermo_work.f90"
    for path in (readin_path, initwork_path):
        if not path.is_file():
            raise SystemExit(f"missing ground truth: {path}")
    readin = readin_path.read_text(encoding="utf-8", errors="replace")
    initwork = initwork_path.read_text(encoding="utf-8", errors="replace")
    commit = subprocess.run(["git", "-C", str(repo), "rev-parse", "HEAD"],
                            capture_output=True, text=True).stdout.strip()

    entries, duplicates = parse_namelist(readin)
    defaults = parse_defaults(readin)
    what_values = parse_what_values(initwork)
    facts = parse_consistency_facts(readin)

    out = Path("src/quantumforge/input/schema/QEThermoPwSchemaData.java")
    lines = []
    lines.append("/* GENERATED by scripts/thermo_pw_schema_miner.py - do not hand-edit.")
    lines.append(" * Mined from the thermo_pw project (github.com/dalcorso/thermo_pw),")
    lines.append(f" * commit {commit} (fetched 2026-07-21),")
    lines.append(f" * src/thermo_readin.f90 sha256={sha256_text(readin_path)},")
    lines.append(f" * src/initialize_thermo_work.f90 sha256={sha256_text(initwork_path)}.")
    lines.append(" * Provenance rules: keyword set + group labels + procedural defaults come")
    lines.append(" * from the NAMELIST declaration and the defaults section; the HARD accepted")
    lines.append(" * values of `what` come from the part-1 SELECT CASE (TRIM(what)) whose")
    lines.append(" * CASE DEFAULT calls errore('what not recognized'); the read path")
    lines.append(" * (READ(iun_thermo, input_thermo...) -> errore) makes an unknown keyword")
    lines.append(" * FATAL, mirrored as ERROR severity. Types are INFERRED from the default")
    lines.append(" * literal when one is assigned (stated per entry), UNKNOWN otherwise. */")
    lines.append("package quantumforge.input.schema;")
    lines.append("")
    lines.append("import java.util.ArrayList;")
    lines.append("import java.util.List;")
    lines.append("")
    lines.append("/** Generated mined thermo_pw &INPUT_THERMO grammar table. */")
    lines.append("final class QEThermoPwSchemaData {")
    lines.append("")
    lines.append("    static List<QEThermoPwSchema.Entry> buildEntries() {")
    lines.append("        List<QEThermoPwSchema.Entry> entries = new ArrayList<>();")
    for name, groups in entries:
        default = defaults.get(name)
        default_text = "null" if default is None else f'"{default[0].replace(chr(92), chr(92)*2)}"'
        type_name = "UNKNOWN" if default is None else infer_type(default[0])
        group_csv = ",".join(groups)
        lines.append(f'        entries.add(QEThermoPwSchema.entry("{name}",'
                     f' QEThermoPwSchema.Type.{type_name}, {default_text}, "{group_csv}"));')
    lines.append("        return entries;")
    lines.append("    }")
    lines.append("")
    lines.append("    /** HARD accepted literals of `what` (CASE DEFAULT -> errore). */")
    lines.append("    static List<String> buildWhatAcceptedValues() {")
    parts = ", ".join(f'"{value}"' for value in what_values)
    lines.append(f"        return List.of({parts});")
    lines.append("    }")
    lines.append("")
    lines.append("    /** Verbatim consistency-check fact lines from thermo_readin. */")
    lines.append("    static List<String> buildConsistencyFacts() {")
    lines.append("        return List.of(")
    for index, fact in enumerate(facts):
        escaped = fact.replace("\\", "\\\\").replace('"', '\\"')
        comma = "," if index < len(facts) - 1 else ""
        lines.append(f'            "{escaped}"{comma}')
    lines.append("        );")
    lines.append("    }")
    lines.append("}")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="")
    print(f"mined {len(entries)} keywords ({len(duplicates)} duplicated in declaration:"
          f" {sorted(duplicates)}), {len(what_values)} what values,"
          f" {len(facts)} consistency facts -> {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
