# QuantumForge implementation status (roadmap inventory)

Last updated: 2026-07-17 (branch batches 1–12).

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
| 9 | Secret storage | Partial | `SecretStore` + Linux/macOS process keyring + Windows DPAPI helper |
| 10 | SSH fail-closed | Done/Partial | No false success; real transport exists when configured |
| 11 | Asset/license manifest | Done | `docs/ASSET_PROVENANCE_MANIFEST.md` with SHA-256 hashes for all assets |
| 12 | Upstream provenance map | Done | BURAI heritage mapping in asset provenance manifest |
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
| 34 | Scratch storage policy | Partial | `QEScratchStoragePolicy` SSD/cluster/quota |
| 35 | Pseudopotential family manager | Partial | `PseudoFamilyValidator` consistency checks |
| 36–38 | Convergence workflows | Fail-closed/Partial | smearing analyzer added; mock loop removed |
| 39 | Geometry convergence validator | Partial | marker/threshold evidence |
| 40 | Transactional final-geometry update | Partial | preview only; apply fail-closed |
| 41 | SCF reference suite | Partial | golden log fixtures (not full engine corpus) |
| 42 | XML-first parsing | Partial | energy/Fermi/convergence/total-force/stress/atomic-forces from XML |
| 43 | Timing/resource parser | Partial | `QETimingResourceParser` stage/call timers |
| 44 | Input diff/preview | Partial | `QEInputDiffPreview` model-vs-text comparison |
| 45 | Dry-run/preflight | Done | binaries/disk/MPI/input/DAG/restart notes |

## Phase 2 — core QE workflows

| # | Item | Status | Notes |
|---:|---|---|---|
| 46 | Bands workflow | Partial | `QEBandsDataParser` + command chain + fixtures |
| 47 | Band-gap detector | Partial | occupation-aware analyzer + tests |
| 48 | DOS/PDOS workflow | Partial | `QEPdosParser` atom/orbital/spin + fixtures |
| 49 | Correct DOS integration | Partial | trapezoidal nonuniform integration |
| 50 | NEB | Partial | NEB corrector + path creator + DAG stages |
| 51 | Phonon DFPT | Partial | freq parser + FORCE_SETS writer + SCF→ph→q2r→matdyn DAG |
| 53 | Raman/IR | Partial | `QERamanIRSpectraParser` DFPT tensors |
| 54 | ESM/slab | Partial | `QESlabPlateauDiagnostic` work function |
| 55 | Charge/potential | Partial | `QEPpChargePotentialBuilder` pp.x inputs |
| 57 | Density difference | Partial | `QEGridDensityDifference` on grids |
| 58 | Wavefunction | Partial | `QEPpWavefunctionBuilder` selected state |
| 59 | Spin density | Partial | `QEMagneticMomentParser` |
| 60 | Berry phase | Partial | `QEBerryPolarizationParser` |
| 61 | DFPT dielectric/Born | Partial | Born/dielectric parser + acoustic sum rule + elastic stability |
| 62 | EPW/Tc | Partial | `QEEliashbergTcCalculator` Allen-Dynes |
| 63 | Hubbard hp.x | Partial | `QEHubbardHpParser` |
| 64 | TDDFPT/Turbo | Partial | `QETurboSpectrumParser` |
| 65 | XSpectra | Partial | `QEXSpectraXanesParser` |
| 66 | GIPAW NMR | Partial | `QEGipawNmrParser` |
| 67 | cp.x | Partial | `QECarParrinelloParser` |
| 68 | PWcond | Partial | `QEPwcondConductanceParser` |
| 69 | Wannier90 | Partial | `QEWannier90SpreadParser` |

## Phase 3 — structures / symmetry

| # | Item | Status | Notes |
|---:|---|---|---|
| 71 | spglib service | Partial | protocol v2 dataset/standardize with enhanced service |
| 72 | Primitive/conventional conversion | Partial | sidecar standardize when available; else fail-closed |
| 73 | seekpath | Partial | sidecar seekpath op when package installed |
| 74 | Magnetic symmetry | Partial | `MagneticSpaceGroupDetector` Shubnikov types |
| 75 | CIF 2.0 | Partial | existing reader (formal grammar not yet) |
| 76 | POSCAR robustness | Partial | enhanced VASPReader with VASP 4/5 compat |
| 77 | Extended XYZ | Partial | existing reader |
| 78 | Additional formats | Partial | PDB reader added; SDF parser added |
| 79 | Measurement tools | Partial | `GeometryMeasurer` bonds/angles/distances |
| 80 | Constraint editor | Partial | `QEIonicConstraintManager` per-axis flags |
| 81 | Supercell builder | Partial | enhanced `NonDiagSupercellBuilder` general 3×3 |
| 82 | Slab builder | Partial | enhanced `SlabModelBuilder` |
| 83 | Adsorption builder | Partial | enhanced `MoleculeAdsorber` |
| 84 | Defect builder | Partial | `QEPointDefectBuilder` vacancy/substitution |
| 85 | Moiré builder | Partial | enhanced `MoirePatternBuilder` |
| 86 | Grain boundary | Partial | `QEGrainBoundaryBuilder` CSL search |
| 87 | Molecule builder | Partial | enhanced `SMILESParser` |
| 88 | Solvation builder | Partial | enhanced `SolventFiller` |
| 89 | SQS adapter | Partial | enhanced `SQSBuilder` |
| 90 | Structure provenance | Partial | `QEStructureProvenanceGraph` |

## Phase 4 — remote HPC

| # | Item | Status | Notes |
|---:|---|---|---|
| 91 | Real SSH layer | Partial | JSch + strict known_hosts + host-key UI helper |
| 92 | Safe SFTP staging | Partial | path guards, unique dirs, temp rename |
| 93 | Scheduler abstraction | Partial | SLURM + PBS + SGE adapters |
| 94 | Site profiles | Partial | YAML-like profiles + examples |
| 95 | Job state machine | Partial | JobRecord transitions + queue store |
| 96 | Remote monitoring | Partial | `RemoteJobMonitor` backoff polling |
| 97 | Safe cancellation | Partial | cancel-by-id + status verify |
| 98 | Selective result sync | Partial | manifest + checksum cache |
| 99 | Checkpoint-aware resubmit | Partial | plan + executable resubmit script export |
| 100 | Array jobs | Not started | |
| 101 | Resource estimator | Partial | `QEResourceEstimator` atoms/cutoff cost |
| 102 | MPI topology advisor | Partial | `QEMpiTopologyAdvisor` rank/pool mapping |
| 103 | Container support | Not started | |
| 104 | Workflow export | Done | bash/SLURM export + GUI |
| 105 | Database-backed queue | Partial | durable JSONL queue (not SQLite yet) |

## Phase 5 — auxiliary engines

| # | Item | Status | Notes |
|---:|---|---|---|
| 106 | thermo_pw | Partial | `QEThermoPwEosParser` + ThermoPw extension |
| 107 | phonopy | Partial | `QEPhonopyForceSetsWriter` + Phonopy extension |
| 108 | phono3py/ShengBTE | Partial | `QEPhono3pyKappaParser` κ tensor |
| 109 | BoltzTraP2 | Partial | BoltzTraP2 extension registration |
| 110 | XCrySDen | Partial | XSF export + launch |
| 111 | VASP | Partial | `QEVasprunXmlParser` + VASP POSCAR reader + extension |
| 112 | CASTEP | Partial | `QECastepLogParser` + CASTEP extension |
| 113 | LAMMPS | Partial | `QELammpsLjGenerator` + LJ mixing rules |
| 114 | CP2K/ABINIT | Partial | `QECp2kInputBuilder` + `QEAbinitInputBuilder` |
| 115 | ASE interop | Partial | `QEAsinteropService` JSON-RPC bridge |
| 116 | Materials Project v2 | Partial | `QEMaterialsProjectV2Client` mp-api search |
| 117 | OPTIMADE | Partial | `QEOptimadeClient` multi-database search |
| 118 | PubChem/SMILES | Partial | `QESdfParser` + enhanced `SMILESParser` |
| 119 | ELATE | Not started | |
| 120 | CatMAP | Partial | `QECatMapMkmExporter` vibrational corrections |

## Phase 6 — visualization/UX

| # | Item | Status | Notes |
|---:|---|---|---|
| 121–135 | Mostly not started | Enhanced convergence viewer, result viewer updates from batch 12 |

## Phase 7 — machine learning

| # | Item | Status | Notes |
|---:|---|---|---|
| 136 | GNN rename | Partial | `MLPotentialExtension` registered |
| 137–150 | Not started | Isolated worker, conformance, model registry not yet |

## Phase 8 — advanced science

| # | Item | Status | Notes |
|---:|---|---|---|
| 151 | Formation energy/hull | Partial | `QEHullThermodynamics` binary hull |
| 152–155 | Defects/surfaces/Pourbaix/battery | Not started/Partial | point defect builder exists |
| 156 | Diffusion from MD | Partial | `QEMdDiffusionMsdParser` unwrapped MSD |
| 159 | Effective mass | Partial | `EffectiveMassTensor` Hessian solver |
| 165 | Superconductivity | Partial | `QEEliashbergTcCalculator` Allen-Dynes |
| 166 | Hyperfine/EPR | Partial | `HyperfineMapper` isotope database |
| 170 | Catalysis microkinetics | Partial | `QECatMapMkmExporter` |
| 157–158, 160–164, 167–169 | Not started or fail-closed | Prototypes disabled |

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
