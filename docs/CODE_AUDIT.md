# QuantumForge 2.0.0 deep code and scientific audit

**Audit date:** 2026-07-16  
**Scope:** all tracked source/build/documentation paths in the repository snapshot (approximately 692 Java classes, 54 FXML files, 13 CSS files, and 103,000 Java/FXML/CSS lines).  
**Method:** package/file inventory, import/reference/dead-code analysis, placeholder/TODO scan, command/network/credential scan, source-encoding check, scientific formula/input review, build-system reconstruction, and initial unit/integration test creation.

This is a code audit, not formal verification or certification. No finite review can prove every calculation path correct. Scientific acceptance requires reference-output regression tests with the actual external engines.

## 1. Executive assessment

The strongest part of the repository is an inherited JavaFX QE desktop core: structure models/readers/viewers, QE namelist/card models, SCF/optimization/MD/band/DOS editors, local process execution, and established output plots. The weakest part is a later layer of broadly named “advanced” classes that are mostly disconnected, hard-coded, or placeholders. Documentation previously advertised the latter as complete.

The release should therefore be positioned as a **QE workflow editor/viewer with experimental modules**, not a complete multi-engine, AI, topological, catalysis, battery, and spectroscopy suite.

### Source distribution by top-level package

| Package | Java files | Approx. lines | Assessment |
|---|---:|---:|---|
| `app` | 337 | 49,300 | Main JavaFX UI; mature core mixed with many unwired prototypes |
| `atoms` | 117 | 14,300 | Core structure model, readers, rendering; high value |
| `input` | 56 | 10,300 | Core QE model/correctors; requires QE-version golden tests |
| `com` | 86 | 6,800 | Utilities/environment/math; legacy concurrency and I/O need hardening |
| `project` | 22 | 4,100 | Core project persistence/state; migration tests needed |
| `run` | 21 | 4,000 | Local QE pipeline; some new modes are mocked |
| `pseudo` | 4 | 1,900 | UPF metadata/library; parser corpus/security limits needed |
| `builder` | 19 | 1,600 | Mostly unwired experimental structure builders |
| `matapi` | 11 | 1,460 | Legacy Materials Project v1 API plus PubChem additions |
| `ssh` | 4 | 1,125 | Incomplete remote execution |
| `export` | 3 | 400 | Useful but young; format compliance tests required |
| `symmetry` | 3 | 280 | Bravais metrics only; spglib work not implemented |
| `plugins`, `neural`, `lammps`, `jupyter` | 5 | ~500 | Detection/skeletons, not integrations |

## 2. Scope classification

### A. Core, directly related to Quantum ESPRESSO — retain and strengthen

- `quantumforge.input`: QE namelists/cards, binders, correctors, input generation.
- `quantumforge.run` core: `RunningNode`, `RunningType`, parsers for SCF/geometry/Fermi/bands/DOS.
- `quantumforge.project` core: calculation modes and project state.
- `quantumforge.pseudo`: UPF parsing and pseudopotential selection.
- `quantumforge.atoms`: structure representation, CIF/XYZ/XSF/CUBE/POSCAR readers and JavaFX viewer.
- `quantumforge.app.project.editor.input`: primary QE GUI.
- `quantumforge.app.project.viewer.result`: established logs, geometry, bands, DOS, convergence, movies.
- `quantumforge.com.math/consts/path/env`: support code, after modernization.

### B. Appropriate auxiliary scope, but incomplete — keep behind “experimental” gates

- `thermo_pw`, phonopy, BoltzTraP2, XCrySDen adapters.
- spglib/seekpath symmetry and path generation.
- SSH/SFTP and SLURM/PBS/PJM/SGE execution.
- `AtomicExporter`, chart/CSV export, geometry measurement.
- Materials Project/PubChem retrieval.
- phonon and volumetric-data viewers.
- LAMMPS and ML-potential adapters if implemented as isolated plugins.
- VASP/CASTEP adapters if licensed independently and given engine-specific schemas/tests.
- Jupyter as an optional local analysis plugin.

### C. Scientifically related but currently misleading prototypes — quarantine or remove from production

The following classes have no reference outside their own file (or only a prototype parent) and are not wired into a validated result pipeline:

- `AdvancedPhysicsHub`, `TopologyHub`, `SPTInvariantFinder`, `WeylFinder`;
- `BatteryWorkbench`, `PhotocatalyticTool`, `EnergyCatalysisHub`, `CatMAPBridge`, `SACDesigner`, `STHEstimator`;
- `RTTDDFTWizard`, `FermiSurfaceViewer`, `SlidingFerroTool`, `PhononGuitar`;
- `BerryMapper`, `SOCAnalyzer`, `OrbitalMagnetization`, `HyperfineMapper`;
- `EffectiveMassTensor`, `ExcitonAnalyzer`, `PiezoelectricTool`, `PourbaixTool`;
- `SuperconductingTcAnalyzer`, `SuperconductingTcViewer`, `WorkFunctionMapper`, `StabilityAnalyzer`.

Examples of unacceptable current behavior include random work-function maps, a mock hyperfine constant, all-zero SOC/orbital magnetization, free-electron fallback effective masses, a one-line STH “efficiency,” and thermodynamics unrelated to the supplied phonon DOS. These must never appear as computed research results.

### D. Out of focus for the next stable release

- blockchain provenance and tokenization;
- holographic/VR viewing;
- generic LLM material recommendation/chat;
- broad “quantum information” claims without a specific supported engine/workflow;
- claims of arbitrary Majorana/axion/Floquet discovery;
- marketing comparisons to commercial NanoLabo features as if they were implemented requirements.

These topics can be research projects, but they distract from making QE input, execution, parsing, and reproducibility excellent. Provenance should use signed manifests, hashes, RO-Crate/PROV, not blockchain.

## 3. Critical findings (P0)

### P0-1: historical build was not reproducible — addressed in this change set

`bin/build.xml` and `launch4j.xml` referenced `D:/git/...` and one developer's Windows home. No standard build or CI existed. A Maven build, platform dependency resolution, tests, SBOM, launchers, and packaging scripts have been added. Historical files should eventually move to `archive/` or be deleted.

### P0-2: semantic version caused class-initialization failure — addressed

`Double.parseDouble("2.0.0")` in `Version.VERSION_NUMBER` throws. A compatibility constant remains, while migration uses the semantic version string.

### P0-3: upgrades destroyed user settings — addressed

`EnvProperties` replaced the complete user properties file whenever versions differed; because semantic parsing failed, this could happen every launch. Migration now overlays user values on new defaults and updates only the version. Add explicit migration tests before changing the format again.

### P0-4: `ConfigUpdater` can erase geometry — disabled; real implementation unresolved

It clears every `QEAtomicPositions` card and never adds parsed positions, reuses the existing lattice, and switches active modes repeatedly. `updateFromOutput()` now fails before that legacy body can execute. Do not enable it until transactional final-geometry parsing, preview, copy, and rollback tests are implemented.

### P0-5: fake success from remote/job code — immediate behavior addressed; implementation still absent

`SSHJob.postJobToServer()`, `SSHFileTransfer.downloadAllFiles()`/`deleteAllOnServer()`, and `LocalJobManager.submitToQueue()` previously returned `true` without doing the operation. They now fail closed (and continuous fetch throws unsupported). Replace booleans with typed results when implementing the real SSH/scheduler adapter; no-op success can lose researcher time/data.

### P0-6: fabricated scientific outputs — partially addressed

- Space-group code assigned high-symmetry groups from Bravais lattice alone; now returns “Undetermined.”
- Magnetic symmetry returned a mock Shubnikov group; now explicit undetermined.
- Primitive/conventional conversion returned the unchanged cell; now throws unsupported.
- CPMD generated invalid `pw.x calculation='cp'`; NMR/XAFS generated partial/invalid PW inputs. They now fail explicitly when enabled.
- Γ-grid helper set half-grid offsets while claiming to include Γ; corrected to zero offsets.

Still unresolved: random/simplified advanced analyzers and phonon thermodynamics. Remove them from menus/releases or make every call throw `UnsupportedOperationException` until validated.

### P0-7: release/license/provenance ambiguity — partially addressed

`README` said Apache-2.0 while `README.md`, file headers, and `LICENSE` said proprietary. Documentation now defers to `LICENSE`, and third-party notices were added. Still required:

- map inherited BURAI files to exact upstream commit/license headers;
- obtain/record ownership authorization for 2025–2026 additions;
- inventory every icon/image and its license;
- have counsel/owner resolve whether repository/public release terms are intended.

### P0-8: no code signing — infrastructure prepared, credentials still required

Hashes and SBOM are implemented. Production release still needs Windows signing and macOS notarization/stapling from owner-controlled identities.

## 4. High-severity findings (P1)

1. **Zero historical tests.** Initial JUnit tests now cover matrix/expression math, QE namelists, band-gap summaries, structure I/O/export, resources, and CLI version. This is far below the required parser corpus.
2. **Mixed encodings.** Fourteen Java files contained non-UTF-8 mojibake comments. They were normalized; CI should enforce UTF-8.
3. **Legacy dependencies.** Bundled Gson 2.6.1, JCodec 0.2.0, exp4j 0.4.6, and JSch 0.1.54 were from 2016. Maven now uses reviewed current versions, including maintained JSch fork. Delete checked-in binaries after confirming no offline-build requirement.
4. **Materials Project v1 API is obsolete.** Migrate to `mp-api` v2 through a Python sidecar or current HTTPS client. Current API key is plaintext.
5. **Plaintext secrets.** Proxy password and Materials Project key can live in `~/.quantumforge/.properties`; SSH password lives in memory/model and serialization behavior needs review. Use OS keyring and default not to save.
6. **SSH cryptographic model absent.** No real connection, known-host verification, host-key UI, agent support, timeout, or safe transfer exists.
7. **Shell command construction.** SSH job scripts concatenate tokens/redirects without robust POSIX quoting. Model arguments as arrays locally and use a quoting library/template remotely.
8. **Network timeouts/limits inconsistent.** API/WebView/download paths need connect/read timeouts, response-size ceilings, content-type checks, cancellation, and TLS-only URLs.
9. **Concurrency is ad hoc.** Many raw threads use `wait(milliseconds)` as sleep, share mutable lists, and do not have lifecycle cancellation. Replace with executors, JavaFX `Task/Service`, scheduled executors, and structured shutdown.
10. **Log redirection hides startup failures.** GUI exceptions go to home log files by default; provide crash dialog/path and rotating logs.
11. **Silent parse errors.** Number format exceptions are often swallowed. Parsers need line/field diagnostics and completeness status.
12. **Parser format fragility.** Many parsers match English prose and token positions. QE-version golden files and tolerant state machines are needed.
13. **Band-gap parser is not a real band-gap algorithm.** It only recognizes an externally produced “band gap” line; QE normally requires eigenvalue/occupation analysis across k points/spins.
14. **Elastic parser assumes a literal header and six raw lines.** Units, row labels, symmetry, malformed matrices, and thermo_pw version are not handled.
15. **Space-group claim remains in class naming/docs.** Only Bravais metric detection exists; rename until spglib is integrated.
16. **QE command chain may overrun incorrect stages.** Spin-up/down and condition lists need golden end-to-end tests for nonmagnetic, collinear and noncollinear workflows.
17. **Convergence mode is mocked at exactly five jobs.** It appends filenames but is not driven by user parameter range or convergence criteria.
18. **Pseudopotential validation is metadata-only.** Add functional/relativity/element/valence checks, hashes, family manifests, and recommended cutoffs.
19. **Unvalidated structure transformations.** Many builder classes are unreferenced and use hard-coded geometry/random positions. Keep out of production.
20. **CIF/POSCAR export defects.** CIF lacked `loop_`; POSCAR did not group coordinates by species; locale-dependent decimals and fake mass/cutoff values existed. Corrected, with missing pseudopotentials now made explicit. Add formal format validators.
21. **Volumetric viewer is a shell.** Cube load returns false; difference returns an empty dataset; no marching cubes.
22. **Phonon thermodynamics ignores input DOS.** Current formulas use only temperature and arbitrary constants—must be removed/disabled.
23. **PDOS integral ignores energy spacing.** It sums DOS values rather than numerical integration over the energy grid.
24. **Effective mass lacks units/tensor fit.** Uses one path coordinate and returns `1/curvature`; cannot claim `m_e`.
25. **Diffusivity uses first-to-last slope.** No unwrapping, origin averaging, diffusive-window fit, uncertainty, or unit conversion.
26. **Pourbaix helper omits reference states and thermochemical corrections.** A pH term alone is not a Pourbaix diagram.
27. **Superconducting helper labels McMillan/Allen–Dynes but uses Debye temperature and a simplified expression.** Real EPW/α²F workflow is absent.
28. **VASP/CASTEP menu claims were false.** Changed to honest prototype/not-implemented messages.
29. **Plugin manager only detects PATH.** Availability is not compatibility, health, version, license, or workflow readiness.
30. **No schema/version migration for projects.** Add transactional backups and forward/backward compatibility tests.

## 5. Medium-severity engineering findings (P2)

- monolithic controllers and 337 UI Java files make changes difficult;
- no dependency injection; global singletons hide state and impede tests;
- no structured logging framework or correlation/job IDs;
- default `Locale` was globally forced to English; separate parser locale from UI localization;
- file writers often use platform default charset and non-atomic replacement;
- no autosave/recovery journal for project edits;
- no large-file/memory strategy for trajectories, bands, DOS, cube grids;
- graph reload polling can race with writers and parse partial lines;
- process cancellation uses `destroy()` only, not descendants or timed `destroyForcibly()`;
- command environment writes `PATH`, `Path`, and `path` simultaneously;
- executable discovery should inspect selected QE directory and versions, not only PATH;
- no semantic validation layer separating UI fields from engine schema;
- no immutable unit-aware quantity type;
- no citation/provenance capture in projects;
- exception messages are often printed rather than shown/actionable;
- accessibility, keyboard navigation, high-DPI, dark mode, and localization lack tests;
- old `.bak`, Eclipse metadata, vendored Javadoc/source ZIPs and generated docs clutter release scope;
- repository history is shallow/grafted in this checkout, limiting provenance/blame analysis;
- no contribution guide, code style check, issue templates, security policy, or deprecation policy.

## 6. Specific “not actually implemented” inventory

| Claimed area | What exists | What is missing |
|---|---|---|
| VASP | POSCAR reader/export, simple form object | KPOINTS/POTCAR policy, complete INCAR schema, execution, XML parser, tests |
| CASTEP | menu and executable string | everything engine-specific |
| thermo_pw | PATH detector, narrow 6×6 parser | input/control generation, compatible QE check, execution DAG, broad parsers |
| phonopy | detector, data containers | displacement generation, force collection, YAML parser, provenance |
| BoltzTraP2 | `btp2` detector | QE conversion, run, JSON/data parser, transport plotting |
| XCrySDen | detector/docs | export-and-launch action and remote behavior |
| SSH/HPC | server form/script templates | SFTP, SSH exec, submit/status/cancel/download |
| LAMMPS | text skeleton | data/topology, force fields, runner, parser |
| GNN | Python import checks | model load/inference, units, uncertainty, worker protocol |
| Jupyter | process start | token URL, lifecycle, API, environment and security |
| symmetry | metric/Bravais heuristics | atomic space group, Wyckoff sites, standardization |
| isosurfaces | data fields | cube parser, difference arithmetic, meshing/rendering |
| phonon | data holders/animation math | QE/phonopy parsers and real thermodynamic integration |
| convergence assistant | fixed five-command loop/UI note | parameter sweep, queue, metric extraction, stopping criterion |
| optimized-geometry update | destructive stub | final-step parser and atomic transactional update |

## 7. Changes made during this audit

- introduced Maven/JDK 17/OpenJFX build and modern dependencies;
- added a stable `quantumforge` CLI (`--version`, `--doctor`, GUI/files);
- added Linux/macOS/Windows portable installers, verified updater, conservative uninstall;
- added jpackage and Arch packaging scripts plus release/SBOM workflow foundation;
- added initial JUnit scientific/parser/resource/CLI tests;
- normalized source encoding;
- fixed semantic-version startup and non-destructive settings migration;
- stopped setting nonexistent bundled QE paths;
- fixed stderr redirection guard;
- fixed Γ-grid offset logic;
- made invalid CPMD/GIPAW/XSpectra and symmetry stubs fail honestly;
- corrected CIF loops, POSCAR species ordering, locale-independent numbers and atomic masses;
- replaced false VASP/CASTEP completion messages;
- updated obsolete/broken documentation URLs and dependency declarations;
- reconciled top-level documentation with repository license and added notices;
- documented honest capability, safe installation, external engines, and scientific validation.

## 8. Required validation matrix before a production claim

### Pure Java tests

- all matrix/lattice transforms, including singular/near-singular and randomized round trips;
- every QE value/card/namelist type and Fortran number/logical/string syntax;
- CIF/XYZ/XSF/CUBE/POSCAR readers using external format corpora;
- format round trips with independent parsers (ASE/pymatgen/spglib);
- project migration, crash recovery and concurrent saves;
- malicious/oversized parser inputs and path traversal fixtures.

### QE golden tests

For each supported QE version and spin regime:

- Si SCF + bands + DOS;
- Fe collinear spin;
- fully relativistic noncollinear/SOC reference;
- molecule in cell;
- slab with dipole/ESM only after keyword validation;
- ionic relax and variable-cell relax;
- MD short trajectory;
- phonon Γ and q-grid;
- NEB minimal reference;
- failure outputs: nonconvergence, missing pseudo, bad XML, MPI abort, disk full.

Compare generated inputs, command DAGs, parsed energies/forces/stresses/Fermi levels, and final structures to independently checked expected values.

### GUI/package tests

- FXML load and control binding under Linux/Windows/macOS;
- create/open/edit/save/run/stop/reopen;
- high-DPI/multiple monitors;
- no-display doctor and MobaXterm X11 smoke;
- install/update/rollback/uninstall without data loss;
- filenames with spaces, Unicode and shell metacharacters;
- clean-VM native/portable launch for every artifact.

## 9. Recommendation

Freeze speculative feature additions for at least one stabilization cycle. Put 80% of effort into QE schema correctness, parser golden tests, project safety, executable/job orchestration, pseudopotential provenance, and signed packaging. A small trustworthy QE application is much more effective scientifically than a large menu of unvalidated formulas.
