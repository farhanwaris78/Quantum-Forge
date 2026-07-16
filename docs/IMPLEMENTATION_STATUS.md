# QuantumForge implementation status (roadmap inventory)

Last updated: 2026-07-16 (branch batches 1–8).

Status key:

- **Done** — usable end-to-end in-tree with tests/harnesses (may still need real-engine/cluster validation)
- **Partial** — real code path exists, but incomplete vs roadmap acceptance criteria
- **Fail-closed** — intentionally refuses rather than inventing results
- **Not started** — no meaningful implementation yet

## Phase 0 — trust, safety, scope

| # | Item | Status | Notes |
|---:|---|---|---|
| 1 | Quarantine experimental modules | Partial | Many advanced prototypes fail-closed; not fully moved to experimental source set |
| 2 | Capability registry | Done | CLI/GUI capability matrix |
| 3 | Typed operation results | Done | `OperationResult` widely used in new paths |
| 4 | Structured logging | Partial | `AppLog` rotating logs + redaction (not full SLF4J stack) |
| 5 | Crash reporter | Partial | Local redacted crash bundles |
| 6 | Project schema versioning | Partial | schema v1 metadata on status |
| 7 | Atomic project writes | Done | `AtomicFileWriter` + `.bak` |
| 8 | Autosave and recovery | Done | export-safe autosave + recovery GUI |
| 9 | Secret storage | Partial | `SecretStore` memory default; OS keyring interface only |
| 10 | SSH fail-closed | Done/Partial | No false success; real transport exists when configured |
| 11 | Asset/license manifest | Not started | |
| 12 | Upstream provenance map | Not started | |
| 13 | Signed release pipeline | Partial | SBOM/checksums/docs; signing secrets external |
| 14 | Dependency policy | Partial | Maven deps + SBOM; no Renovate gate in-tree |
| 15 | Reproducible builds | Partial | portable archive normalization scripts |
| 16 | UTF-8/style/static gates | Partial | static_checks/compile_check harnesses |
| 17 | Test coverage floor | Partial | many unit tests; no JaCoCo gate enforced here |
| 18 | Scientific review template | Partial | docs/checklists exist |
| 19 | Remove benchmark-copy development | Partial | policy in roadmap/docs |
| 20 | Stable/experimental channels | Not started | |

## Phase 1 — QE foundation

| # | Item | Status | Notes |
|---:|---|---|---|
| 21 | QE executable profile | Partial | probe/version/doctor |
| 22 | QE version-aware schema | Not started | |
| 23 | Lossless input AST | Not started | |
| 24 | Formal lexer/parser | Partial | existing namelist model + tests; not full grammar rewrite |
| 25 | Semantic validator | Partial | deterministic preflight rules |
| 26 | Units library | Done | `PhysicalQuantity`/`Unit` |
| 27 | QE command DAG | Done | typed stages + runner wiring + skip completed |
| 28 | Run manifest | Done | JSONL provenance |
| 29 | Process-tree cancellation | Done | EXIT file + descendants |
| 30 | Live output tailer | Partial | `LiveFileTailer` + parser polling |
| 31 | QE error knowledge base | Partial | signature table + failed-job diagnose |
| 32 | SCF convergence dashboard | Partial | analyzer + log summary (GUI chart not fully rewired) |
| 33 | Restart manager | Partial | `.save` completeness assessment |
| 34 | Scratch storage policy | Not started | |
| 35 | Pseudopotential family manager | Not started | |
| 36–38 | Convergence workflows | Fail-closed/Partial | mock five-job loop removed/unsupported |
| 39 | Geometry convergence validator | Partial | marker/threshold evidence |
| 40 | Transactional final-geometry update | Partial | preview only; apply fail-closed |
| 41 | SCF reference suite | Partial | golden log fixtures (not full engine corpus) |
| 42 | XML-first parsing | Not started | |
| 43 | Timing/resource parser | Not started | |
| 44 | Input diff/preview | Not started | |
| 45 | Dry-run/preflight | Done | binaries/disk/MPI/input/DAG/restart notes |

## Phase 2 — core QE workflows

| # | Item | Status | Notes |
|---:|---|---|---|
| 46 | Bands workflow | Partial | existing command chain + fixtures |
| 47 | Band-gap detector | Partial | occupation-aware analyzer + tests |
| 48 | DOS/PDOS workflow | Partial | existing chain + fixtures |
| 49 | Correct DOS integration | Partial | trapezoidal nonuniform integration |
| 50–70 | NEB/phonon/ESM/pp/Berry/EPW/etc. | Mostly fail-closed / not started | advanced prototypes disabled or incomplete |

## Phase 3 — structures / symmetry

| # | Item | Status | Notes |
|---:|---|---|---|
| 71 | spglib service | Partial | isolated JSON sidecar protocol |
| 72 | Primitive/conventional conversion | Fail-closed | refuses silent identity transform |
| 73 | seekpath | Not started | |
| 74 | Magnetic symmetry | Fail-closed | undetermined without magnetic spglib |
| 75–90 | Formats/builders | Partial/varies | CIF/POSCAR/etc. exist; many builders experimental |

## Phase 4 — remote HPC

| # | Item | Status | Notes |
|---:|---|---|---|
| 91 | Real SSH layer | Partial | JSch + strict known_hosts + host-key UI helper |
| 92 | Safe SFTP staging | Partial | path guards, unique dirs, temp rename |
| 93 | Scheduler abstraction | Partial | SLURM + PBS + SGE adapters |
| 94 | Site profiles | Partial | YAML-like profiles + examples |
| 95 | Job state machine | Partial | JobRecord transitions + queue store |
| 96 | Remote monitoring | Not started | |
| 97 | Safe cancellation | Partial | cancel-by-id + status verify |
| 98 | Selective result sync | Partial | manifest + checksum cache |
| 99 | Checkpoint-aware resubmit | Not started | |
| 100–103 | Arrays/estimator/MPI/containers | Not started | |
| 104 | Workflow export | Done | bash/SLURM export + GUI |
| 105 | Database-backed queue | Partial | durable JSONL queue (not SQLite yet) |

## Phase 5 — auxiliary engines

| # | Item | Status | Notes |
|---:|---|---|---|
| 106 | thermo_pw | Experimental/detection | not full workflow |
| 107 | phonopy | Unavailable | detection only |
| 108 | phono3py/ShengBTE | Not started | |
| 109 | BoltzTraP2 | Unavailable | detection only |
| 110 | XCrySDen | Partial | XSF export + launch |
| 111 | VASP | Experimental | POSCAR/INCAR prototype only |
| 112 | CASTEP | Unavailable | |
| 113–120 | LAMMPS/ASE/MP/OPTIMADE/etc. | Mostly unavailable/experimental | |

## Phases 6–8 (UX/ML/advanced science)

Mostly **not started** or **fail-closed prototypes**. Advanced science menus that previously fabricated numbers now throw unsupported errors.

## Packaging / release engineering

| Area | Status |
|---|---|
| Portable install/update/uninstall | Done |
| `quantumforge` CLI launcher | Done |
| Multi-OS docs/tutorial | Done |
| CI/release workflow templates | Done (under `packaging/github-workflows/`) |
| Native signed installers | Partial/external secrets required |
| First-release maintainer guide | Done (`docs/FIRST_RELEASE.md`) |

## How to verify this tree

```bash
python3 scripts/static_checks.py
python3 scripts/compile_check.py
python3 scripts/fixture_harness.py
mvn -B -ntp clean verify   # on a JDK 17+ machine
```
