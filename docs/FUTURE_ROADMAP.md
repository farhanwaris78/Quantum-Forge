# Future roadmap: what to add, how to implement it, and why it matters

This roadmap is ordered by scientific value and dependency. “Effective” means reducing wrong calculations, researcher time, or irreproducibility—not increasing the number of menu entries. Every feature must ship with engine-version documentation, unit/reference tests, and at least one independently validated material.

## Implementation progress

The first stabilization batch implements or partially implements items **2, 3, 9, 16, 17, 18, 24, 25, 36, 37, 45, 47, 49, 156**, plus fail-closed portions of 54, 56, 61, 64–67, 91–99, and 161–170.

The second stabilization batch added or hardened items **4, 5, 6, 7, 8, 21, 28, 29** and packaging/docs infrastructure.

The **third** stabilization batch implemented or strengthened items **8, 9, 26, 30–32, 39–41** (units, live tailing, error KB, SCF/geometry analyzers, secret store, fixtures).

The **fourth** batch added recovery GUI, command DAG model, restart manager, workflow export, fixtures, and offline harnesses.

The **fifth** batch (this continuation) wires and extends those foundations:

| # | Status after batch 5 | What landed |
|---:|---|---|
| 27 | **Wired into runner** | `RunningNode` uses `QECommandDag` stage IDs, skips completed stages via `ArtifactScanner`, logs remaining work |
| 33 | **Used in preflight** | Restart assessment surfaced in dry-run report |
| 45 | **Stronger** | `DryRunPreflight` checks binaries, disk, MPI, input semantics, DAG integrity before launch |
| 104 | **GUI + auto** | Viewer **Export workflow script**; auto-write `.quantumforge.workflow.sh` on run |
| 110 | **Partial** | `XCrySDenLauncher` temp XSF + argument-array launch; viewer menu **Open in XCrySDen** |
| 32 | **Runner feedback** | SCF convergence summary logged after each stage when present |

“Partially” remains intentional where full engine golden runs, native keyrings, or remote XCrySDen lifecycle tests are still required.

The **sixth** batch adds HPC/SSH foundations and spglib protocol:

| # | Status after batch 6 | What landed |
|---:|---|---|
| 91 | **Partial** | `JschSshTransport` + `KnownHostsStore` fail-closed host keys; session passwords only |
| 92 | **Partial** | `RemotePathGuard` staging roots, unique job dirs, temp upload+rename pattern |
| 93 | **Partial** | `SlurmSchedulerAdapter` typed `#SBATCH` + job-id parse + cancel/status arrays |
| 94 | **Partial** | `SiteProfile` simple YAML-like loader (`packaging/sites/example-slurm.yaml`) |
| 95 | **Partial** | `JobRecord` / `JobState` transition history |
| 10 | **Still fail-closed without transport** | `SSHJob` prepares scripts; submit requires connected transport |
| 71 | **Partial** | `SpglibService` JSON-line sidecar protocol + `tools/spglib_sidecar.py` |


## Phase 0 — trust, safety, and scope (do these first)

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 1 | Quarantine experimental modules | Move unwired classes to an `experimental` source set excluded from production; feature flag only after tests. | Users cannot mistake random/mock values for research output; stable menus contain only supported workflows. |
| 2 | Capability registry | Define `SUPPORTED`, `EXPERIMENTAL`, `UNAVAILABLE` states with engine/version/test evidence; render badges in UI. | One source of truth replaces marketing claims and PATH-only “available” status. |
| 3 | Typed operation results | Replace boolean success with `Result<T>` containing status, diagnostics, command, files, and cause. | Stub/no-op operations cannot return success; failures become actionable. |
| 4 | Structured logging | Add SLF4J backend, rotating logs, job IDs, severity, and privacy redaction. | A failed run is diagnosable without exposing API keys/passwords. |
| 5 | Crash reporter (local-first) | Catch uncaught JavaFX/background exceptions; create redacted diagnostic bundle after user approval. | Reproducible bug reports; no automatic upload of structures or licensed files. |
| 6 | Project schema versioning | JSON schema or explicit versioned serializer; transaction, backup, migration and rollback tests. | Old projects open without silent field loss; failed migration leaves original intact. |
| 7 | Atomic project writes | Write/fsync temporary file and atomic rename; maintain last-known-good journal. | Power loss cannot truncate the only project copy. |
| 8 | Autosave and recovery | Debounced immutable snapshots with size limits and recovery picker. | Hours of input editing survive a crash without overwriting deliberate saved state. |
| 9 | Secret storage | OS keyring via Secret Service/Keychain/Credential Manager; memory-only default. | No proxy/API/SSH secrets in `.properties`; migration deletes plaintext after consent. |
| 10 | SSH fail-closed behavior | Disable current TODO paths and return `UNSUPPORTED` until real adapter is complete. | No false “job submitted/download complete” messages. |
| 11 | Asset/license manifest | Record origin, author, license and hash for every icon/image; replace unknown assets. | Release legal provenance is auditable. |
| 12 | Upstream provenance map | Map inherited files to BURAI commit/hash and original license header. | Derivative licensing and attribution can be reviewed confidently. |
| 13 | Signed release pipeline | Protected environment, Windows Authenticode, macOS notarization, Linux detached signature, provenance attestation. | Clean OS recognizes publisher; signature verifies independently of checksum file. |
| 14 | Dependency policy | Renovate/Dependabot PRs, dependency review, SBOM, vulnerability gate with documented exceptions. | Known vulnerable dependencies do not silently enter releases. |
| 15 | Reproducible builds | Pin Maven/plugins/JDK; normalized timestamps/order; compare hashes on two runners. | Same inputs produce byte-identical portable archives where signing is excluded. |
| 16 | UTF-8/style/static gates | Enforce encoding, formatter, Checkstyle/SpotBugs/Error Prone, `git diff --check`. | Prevent regression to mojibake, null hazards, ignored results and accidental binaries. |
| 17 | Test coverage floor | JaCoCo by package, mutation tests for parsers/math, no coverage gaming. | Core `input/run/project/atoms/pseudo` reaches meaningful branch coverage (>80% target). |
| 18 | Scientific review template | PR checklist: equation, units, assumptions, citations, reference data, uncertainty, domain. | Every scientific formula has traceable justification and tests. |
| 19 | Remove benchmark-copy development | Stop adding classes solely because another GUI lists a feature; use user workflows/issues. | Engineering effort follows validated needs rather than feature-count competition. |
| 20 | Stable/experimental release channels | Stable excludes prototypes; nightly artifacts visibly watermarked and isolated from user projects. | Researchers can choose risk consciously; experimental schema cannot corrupt stable data. |

## Phase 1 — robust Quantum ESPRESSO foundation

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 21 | QE executable profile | One profile stores bin directory, version output, MPI linkage, build commit and capabilities. | Doctor verifies actual selected binaries, not only PATH. |
| 22 | QE version-aware schema | Generate keyword metadata from QE 7.2–7.5 input docs/source; typed version constraints and enums. | Invalid/deprecated keywords are blocked or warned for the selected QE version. |
| 23 | Lossless input AST | Preserve comments, ordering, unknown keywords, quoting, cards and repeated indices while editing known values. | Open→save without edits is byte/semantic stable; expert input is not destroyed. |
| 24 | Formal lexer/parser | Replace ad-hoc splitting with tested Fortran-namelist/card grammar supporting D exponents and arrays. | Complex valid QE input parses consistently with a broad corpus. |
| 25 | Semantic validator | Cross-field rules: `ibrav`/CELL, spin/SOC, occupations/smearing, ESM orientation, calculation/card compatibility. | Errors are caught before queue time with exact official-doc links. |
| 26 | Units library | Immutable quantities and explicit conversions for Ry/eV/Ha, bohr/Å, kbar/GPa, frequency units. | No unitless public scientific API; round-trip conversion tests at machine precision. |
| 27 | QE command DAG | Represent SCF→NSCF/bands→post-processing as typed stages with inputs/outputs/dependencies. | Resume/retry one stage without rerunning valid predecessors. |
| 28 | Run manifest | Persist command array, environment, cwd, executable hash/version, input/output hashes, timestamps and exit code. | Every result plot can reveal exactly which process produced it. |
| 29 | Process-tree cancellation | `ProcessHandle` descendants, graceful QE stop file/signal, timeout, then forced kill; state machine. | Stop reliably terminates MPI children and records cancellation, not “failure.” |
| 30 | Live output tailer | Incremental UTF-8 decoder, file rotation/truncation handling and partial-line buffering. | Live plots never parse half-written numerical records. |
| 31 | QE error knowledge base | Structured signatures from real QE errors with version, cause, checks, and source links. | Faster diagnosis without an opaque LLM; suggestions are deterministic and testable. |
| 32 | SCF convergence dashboard | Parse iteration energy, estimated accuracy, charge, magnetization and timing; stage-aware chart. | Detect oscillation/divergence early; values match QE logs in golden tests. |
| 33 | Restart manager | Validate `.save` completeness and prefix/outdir compatibility; generate safe `restart_mode`. | Interrupted jobs resume without mixing incompatible scratch data. |
| 34 | Scratch storage policy | User/site profiles for local SSD, cluster scratch, cleanup retention and quota checks. | Avoid slow home I/O and disk-full failures while preserving needed restart artifacts. |
| 35 | Pseudopotential family manager | Import SSSP/PSLibrary manifests, verify hash, element/XC/relativity/valence, suggested cutoffs. | Prevent mixed/incompatible pseudo sets; cite exact family automatically. |
| 36 | Cutoff convergence workflow | User-defined ecutwfc/ecutrho grid, reference metric, monotonicity view and cost/error curve. | Recommended cutoff backed by evidence for energy/force/stress/property target. |
| 37 | k-mesh convergence workflow | Reciprocal-density or spacing-based grids; preserve Γ/shift semantics; converge target observable. | Replaces fixed “five mocked iterations” with quantitative stopping and uncertainty. |
| 38 | Smearing convergence workflow | Compare scheme/degauss while monitoring energy, forces, entropy correction and metallic occupations. | Avoid false metallic/insulating behavior and force bias. |
| 39 | Geometry convergence validator | Check final force/stress thresholds, SCF status, BFGS termination and cell volume. | “Optimized” appears only when QE actually met user criteria. |
| 40 | Transactional final-geometry update | Parse last complete coordinates/lattice into a new Cell, preview diff, then update selected modes. | Fixes current destructive stub; original geometry remains recoverable. |
| 41 | SCF reference suite | Si, Fe, molecule, slab, SOC fixtures for each supported QE version. | Generated input/output parsers agree with independently checked values. |
| 42 | XML-first parsing | Parse QE `data-file-schema.xml` where available; text fallback with versioned adapters. | More stable structured values and fewer prose-token dependencies. |
| 43 | Timing/resource parser | Stage/call timers, memory estimates, FFT/grid size and MPI layout from QE output. | Users optimize cost and identify scaling bottlenecks. |
| 44 | Input diff/preview | Side-by-side GUI model vs emitted text with semantic highlights and official keyword help. | Expert users catch unwanted GUI transformations before run. |
| 45 | Dry-run/preflight | Verify binaries, pseudo files/hashes, writable outdir, disk, MPI, input semantics, expected outputs. | Expensive queue jobs fail locally in seconds instead of after waiting. |

## Phase 2 — complete core QE workflows

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 46 | Bands workflow | SCF→bands/NSCF→`bands.x`; spin-aware files, symmetry path labels, Fermi reference. | Si/Fe/SOC band plots match reference data and preserve units. |
| 47 | Real band-gap detector | Occupation/electron-count analysis by k/spin; VBM/CBM, direct/indirect, metallic tolerance and confidence. | Correct classification on semiconductor, metal, spin-polarized and partial-occupation fixtures. |
| 48 | DOS/PDOS workflow | NSCF dense mesh→`dos.x`/`projwfc.x`; parse atom/wfc/l/m/spin metadata. | Orbital selection sums reproduce total/projected DOS within tolerances. |
| 49 | Correct DOS integration | Trapezoidal/nonuniform-grid integration with units and spin convention. | Integrated electron count agrees with expected valence count. |
| 50 | NEB editor/runner | Typed `neb.x` path input, image interpolation, constraints, climbing image, stage parser and movie. | Reference diffusion/barrier path reproduces QE example; images cannot reorder silently. |
| 51 | Phonon DFPT workflow | `ph.x`, `q2r.x`, `matdyn.x`, q-grid/path, Born charges/NAC and ASR provenance. | Dispersion/DOS match QE examples; imaginary modes and units handled honestly. |
| 52 | Phonon eigenvector animation | Parse complex mass-weighted eigenvectors; phase/amplitude controls and supercell replication. | Γ and non-Γ modes obey periodic phase and normalization. |
| 53 | Raman/IR workflow | Parse dielectric/Born/Raman tensors from supported QE route; orientation/powder spectra with broadening metadata. | Peak positions/intensities trace to raw tensor/mode and documented assumptions. |
| 54 | ESM/slab workflow | Validate z nonperiodicity, `assume_isolated`, gate/field/dipole settings against QE version; planar potential pipeline. | Work function from averaged potential/EF with plateau diagnostics, not random maps. |
| 55 | Charge/potential workflow | Generate `pp.x` inputs, cube/XSF conversion, metadata and field units. | Charge integral and grid/cell alignment validated before rendering/difference. |
| 56 | 3D volumetric renderer | Streaming cube parser, marching cubes, positive/negative surfaces, clipping, decimation and GPU fallback. | Large grids render within bounded memory; isosurface geometry tested on analytic fields. |
| 57 | Density difference | Require grid/cell compatibility or explicit resampling; compute ρsystem−Σρcomponents. | Integral and alignment reported; cannot subtract unrelated grids silently. |
| 58 | Wavefunction visualization | `pp.x` selected band/k/spin; phase/sign and complex-field policy. | Every surface labels state, k point, spin and chosen scalar representation. |
| 59 | Spin density/noncollinear visualization | Handle scalar magnetization and vector components with arrows/slices. | Known magnetic fixtures reproduce total magnetization and component orientation. |
| 60 | Berry phase/polarization | Supported `pw.x` Berry workflows with branch/unwrapping and polarization quantum. | Changes, not arbitrary absolute branches, are reported with lattice/unit context. |
| 61 | DFPT dielectric/Born charges | Parse tensors, enforce acoustic charge sum diagnostics, principal axes and unit conversion. | Tensor symmetry and sum rules displayed and tested. |
| 62 | Electron-phonon/EPW adapter | Separate optional EPW plugin, Wannierization checks, q/k convergence and α²F parsing. | No Tc without converged α²F, λ, ωlog, μ* and provenance. |
| 63 | Hubbard `hp.x` workflow | Generate/iterate `hp.x`, site/projector mapping, convergence and update HUBBARD card. | U/V values tied to structure/pseudo/projector and iteration history. |
| 64 | TDDFPT/Turbo tools | Explicit adapters for turbo_lanczos/turbo_spectrum, version schemas and spectra. | Optical spectrum matches QE examples; do not label a generic empty wizard RT-TDDFT. |
| 65 | XSpectra workflow | SCF/core-hole species→`xspectra.x`; dedicated schema, edge/polarization and convergence. | Valid XANES fixtures replace the disabled fake XAFS corrector. |
| 66 | GIPAW NMR workflow | Detect GIPAW-capable pseudos, SCF→`gipaw.x`, shielding/EFG parse and reference convention. | Calculated shifts explicitly state reference; fixture tensors match raw output. |
| 67 | `cp.x` workflow | Dedicated CP namelists/cards, fictitious mass/timestep validation, restart and energy drift plots. | Replaces invalid `pw.x calculation='cp'`; QE CP example passes. |
| 68 | PWcond/transport adapter | Typed leads/scattering setup only after reference corpus and unit review. | Conductance plots trace to valid PWcond output, separate from Boltzmann transport. |
| 69 | Wannier90 bridge | `.win` schema, projections, disentanglement windows, spread convergence and band comparison. | Wannier-interpolated bands overlay DFT with quantified RMS/max error. |
| 70 | Exact-exchange/hybrid guidance | q-grid, EXX cutoff, memory/cost estimate and version constraints. | Prevent impossible resource requests and unconverged hybrid results. |

## Phase 3 — structures, symmetry, interoperability

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 71 | spglib service | Versioned local Python service/JNI; cell/species/tolerance in, full dataset out. | Space group/Wyckoff/equivalent atoms match spglib fixtures; tolerance sweep shown. |
| 72 | Primitive/conventional conversion | Use spglib standardized cells; preserve mapping and preview coordinate transformation. | Round trip preserves structure and composition; no silent unchanged return. |
| 73 | seekpath integration | Feed standardized primitive cell; return reciprocal path, labels and transformations. | Paths agree with seekpath versioned reference; discontinuities explicit. |
| 74 | Magnetic symmetry | spglib magnetic dataset with vector moments, axial-vector/time-reversal handling. | No result without moments and tolerance; known AFM fixtures pass. |
| 75 | CIF 2.0 parser/exporter | Use a mature CIF library or formal grammar; loops, uncertainties, symmetry operations, occupancy/disorder. | COD/ICSD-permitted corpus round trips semantically. |
| 76 | POSCAR robustness | VASP 4/5, negative scale, selective dynamics, Cartesian/direct, species provenance. | ASE/pymatgen cross-validation; coordinates remain grouped correctly. |
| 77 | Extended XYZ | ASE properties schema for lattice, pbc, forces, stress, energy and per-atom metadata. | MLP datasets round-trip through ASE without field/unit loss. |
| 78 | Additional open formats | PDB, MOL/SDF, LAMMPS data, CP2K, ABINIT where mature libraries exist. | Add only with corpus and independent parser comparison. |
| 79 | Measurement tools | Minimum-image bond, angle, dihedral, plane/distance; uncertainty/units. | Results agree with analytic fixtures in triclinic cells. |
| 80 | Constraint editor | Per-axis ionic flags, collective constraints and validation per calculation mode. | Generated flags/cards survive import/export and match viewer highlighting. |
| 81 | Supercell matrix builder | General integer 3×3 transformation, determinant/atom-count preview and mapping. | Non-diagonal cells preserve periodicity; determinant equals multiplicity. |
| 82 | Slab builder | Miller indices, termination enumeration, stoichiometry/polarity, vacuum and symmetric/asymmetric choices. | Surface normal/cell/termination validated against pymatgen/ASE references. |
| 83 | Adsorption builder | Symmetry-distinct sites, molecular orientation, collision checks and provenance. | Generates candidates, not a single hard-coded height; duplicates reduced by symmetry. |
| 84 | Defect builder | Vacancies/substitutions/interstitials, charge-state metadata, image-separation diagnostics. | Enumerates symmetry-inequivalent defects and records mapping. |
| 85 | Commensurate moiré builder | Integer lattice matching under bounded strain/size; twist error and atom-count estimate. | Report exact achieved angle/strain; reject placeholder lattice. |
| 86 | Grain boundary builder | CSL search, plane/axis, translations, overlap removal and stoichiometry. | Validate Σ value and periodic matching; retain construction parameters. |
| 87 | Molecule builder via RDKit/Open Babel | Sidecar for SMILES valence, conformers, protonation and force-field preoptimization. | Replaces hand-coded fragments; warns that conformer is not DFT minimum. |
| 88 | Solvation/interface builder | Packmol adapter with seed, density, box and overlap checks. | Reproducible configurations with reported achieved density and minimum distance. |
| 89 | SQS adapter | Integrate ATAT/mcsqs or icet with target correlations and seed. | Quantify correlation error rather than random substitutions. |
| 90 | Structure provenance graph | Record source database/file and every transform matrix/parameter. | Any generated structure can be reconstructed exactly. |

## Phase 4 — remote HPC and workflow scale

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 91 | Real SSH layer | Maintained JSch/Apache MINA, strict known_hosts, agent/key auth, timeouts and host fingerprint UI. | Connection tests include changed-host rejection; no plaintext password default. |
| 92 | Safe SFTP staging | Unique remote directory, canonical path checks, temp upload+rename, hashes and resumable transfer. | Interrupted transfer cannot masquerade as complete; no broad remote deletion. |
| 93 | Scheduler abstraction | Typed resources and adapters for SLURM/PBS/PJM/SGE; no free-form concatenation for core directives. | Generated scripts pass site fixtures and shellcheck; job ID parsed. |
| 94 | Site profiles | Admin/user YAML for modules, partitions, account, launcher, scratch, policies and limits. | One portable project can target multiple clusters without editing inputs. |
| 95 | Job state machine | STAGED→SUBMITTED→PENDING→RUNNING→COMPLETED/FAILED/CANCELLED/UNKNOWN with persisted transitions. | GUI restart reconstructs jobs accurately from scheduler and manifests. |
| 96 | Remote monitoring | Bounded polling/backoff, scheduler reason, output tail, network-offline status. | No thread leak or request storm; reconnect resumes cleanly. |
| 97 | Safe cancellation | Scheduler-specific cancel by parsed job ID, confirmation and post-cancel verification. | Never kill by broad process name or delete unrelated directory. |
| 98 | Selective result sync | Manifest-driven required/optional/large files, include/exclude and checksum cache. | Save bandwidth while guaranteeing parser prerequisites. |
| 99 | Checkpoint-aware resubmit | Detect walltime stop/restart files and clone a new manifest/job. | Resubmission is explicit and preserves original job history. |
| 100 | Array jobs | Parameter-sweep manifest and scheduler arrays with per-task directories/index mapping. | Hundreds of convergence points submit/manage consistently. |
| 101 | Resource estimator | Historical/site model based on atoms, cutoffs, bands, k/q points, pools and FFT. | Provides range/confidence; estimates improve from completed runs. |
| 102 | MPI topology advisor | Map `-nk/-nb/-nt/-nd`, OpenMP and ranks using QE output/site cores; benchmark mode. | Suggestion backed by scaling measurements, not a universal formula. |
| 103 | Container support | Apptainer/Singularity profile with image digest, bind paths and MPI compatibility checks. | Reproducible software stack without hiding host-MPI constraints. |
| 104 | Workflow export | Export runnable shell/SLURM scripts and manifest independent of GUI. | Jobs remain usable if QuantumForge is unavailable. |
| 105 | Database-backed queue | SQLite WAL with migrations, transactions and per-job locks. | Thousands of jobs survive restart without corrupted ad-hoc files. |

## Phase 5 — auxiliary engines and analysis

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 106 | thermo_pw plugin | Compatibility matrix, join/build detector, controls model, DAG and parsers by task. | Elastic/QHA examples match upstream; GUI refuses incompatible QE pairing. |
| 107 | phonopy plugin | Isolated environment, YAML protocol, displacements, force job arrays, FORCE_SETS and plots. | Full QE finite-displacement reference reproduces phonopy output. |
| 108 | phono3py/ShengBTE plugin | Third-order displacements, huge-job safeguards, FC validation and κ tensor parsing. | Mesh/supercell/isotope/scattering convergence recorded; no one-click κ claim. |
| 109 | BoltzTraP2 plugin | Convert QE bands via supported library, run `btp2`, parse machine-readable data, unit-aware tensors. | Interpolation error and k convergence displayed before transport curves. |
| 110 | XCrySDen launcher | Export selected structure/grid to temp XSF, argument-array launch, lifecycle and path detection. | One action opens the exact current dataset locally/remotely. |
| 111 | VASP plugin (licensed) | Separate module; full INCAR metadata, KPOINTS/POSCAR, POTCAR references only, `vasprun.xml` parser. | Never bundles POTCAR; private licensed golden suite passes. |
| 112 | CASTEP plugin (licensed) | Separate `.cell/.param` AST, executable profile and `.castep` parser. | Round-trip and private licensed fixtures; no menu until complete. |
| 113 | LAMMPS plugin | Unit-aware data/script model, package query, potential-file hashes, runner and dump/thermo parser. | Energy/force fixture equals direct LAMMPS; unsupported pair styles blocked. |
| 114 | CP2K/ABINIT optional adapters | Plugin API and engine-specific schemas, not conditionals in QE classes. | Core remains maintainable; each adapter has independent support level/tests. |
| 115 | ASE interoperability service | Restricted JSON RPC for converters/calculators; pinned environment and no arbitrary code. | Leverage mature ecosystem while keeping Java process isolated. |
| 116 | Materials Project v2 | `mp-api` sidecar, keyring secret, pagination/cache/rate-limit and provenance. | Replaces dead REST v1; downloaded structure includes material ID/API version. |
| 117 | OPTIMADE client | Standards-based multi-database search with provider filters and cached responses. | Reduces vendor lock-in and makes source provenance explicit. |
| 118 | PubChem/RDKit molecule import | CID/SMILES retrieval, stereochemistry, protonation/charge and 3D conformer metadata. | Chemically valid import with warnings, not string-only geometry. |
| 119 | ELATE integration | Export elastic tensor with convention/units to local implementation or official service by consent. | Directional properties reproduce tensor and stability checks. |
| 120 | CatMAP bridge | Export a validated reaction-energy dataset/schema; run isolated Python, parse rates/sensitivity. | Catalysis results require complete thermochemistry and model settings, replacing empty UI. |

## Phase 6 — visualization, UX, and reproducibility

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 121 | Unified result data model | Typed datasets with axis units, provenance, uncertainty and raw-file locations. | Every graph/export uses the same values and metadata. |
| 122 | Publication exports | SVG/PDF/PNG and CSV/JSON with labels, units, styles, raw-data hash and settings sidecar. | Plot can be regenerated exactly without screenshots. |
| 123 | Report generator | Quarto/LaTeX template from manifests, convergence plots, citations and method text; never fabricate values. | Methods section and tables trace to raw outputs. |
| 124 | Comparison workspace | Align multiple projects by Fermi/VBM/vacuum/user reference; unit and sampling checks. | Honest overlays with explicit alignment, not hidden shifts. |
| 125 | Tensor viewer | Matrix, eigenvalues/vectors, invariants and 2D/3D directional surfaces with convention selector. | Elastic/dielectric/piezo tensors are interpretable and convention-safe. |
| 126 | Brillouin-zone viewer | Robust reciprocal Wigner–Seitz polyhedron, k/q path and points, labels and symmetry. | Geometry validated against seekpath/spglib; no decorative empty pane. |
| 127 | Large trajectory streaming | Indexed frames, memory mapping/chunking, decimation and background parse. | Million-atom-frame files do not exhaust heap. |
| 128 | Data provenance panel | Click any value to show source file, line/XML path, parser version and transformation. | Researchers can audit derived numbers immediately. |
| 129 | Integrated documentation | Offline versioned QE keyword docs plus upstream links; contextual F1. | Correct help for selected engine version without WebView dependency. |
| 130 | Accessibility | Screen-reader labels, keyboard traversal, contrast themes, scalable fonts and automated checks. | Core workflows usable without mouse and at 200% scaling. |
| 131 | Internationalization | Resource bundles for UI only; parsers/serialization fixed to Locale.ROOT. | Localized UI cannot emit comma-decimal scientific input. |
| 132 | Workspace search | Indexed projects by formula, engine, status, tags and provenance; no raw secret indexing. | Find/reuse calculations without fragile directory browsing. |
| 133 | Template library | Versioned, reviewed workflow templates with parameter explanations—not universal defaults. | Faster setup while preserving required convergence steps. |
| 134 | Citation manager | Automatically collect QE/plugin/pseudo/method citations into BibTeX/CSL. | Papers credit exact methods/software correctly. |
| 135 | RO-Crate/FAIR export | Package manifest, inputs, selected raw outputs, metadata, checksums and licenses. | Shareable reproducibility bundle without exposing secrets/licensed content. |

## Phase 7 — machine learning, implemented safely

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 136 | Rename/fix GNN API | Replace `GNNFroceField` with `MLPotentialService`; backend-neutral request/response schema. | Removes typo and stops hard-coding marketing model list into core. |
| 137 | Isolated Python worker | Conda/uv lock, localhost/stdio JSON protocol, model hash, units, device/precision and timeouts. | Java never imports arbitrary Python; every inference is reproducible. |
| 138 | Backend conformance tests | Energy, force, stress, periodic cell, species, batching and finite-difference force tests per model. | A backend is enabled only when contract tolerances pass. |
| 139 | Model registry | Source/license/citation/training chemistry/cutoff/version/hash and allowed uses. | Users know applicability and legal constraints before download/run. |
| 140 | Domain-of-applicability gate | Species, coordination, density/volume and descriptor-distance checks; ensemble disagreement where available. | Out-of-domain structures warn/block rather than giving confident nonsense. |
| 141 | ML pre-relaxation | Optional ML optimizer creates a new provenance stage, followed by mandatory DFT check/relax. | Reduces DFT steps without mixing ML and DFT energies silently. |
| 142 | Active-learning loop | Committee uncertainty selects configurations; immutable DFT labels; retraining dataset versions. | DFT effort targets informative frames with auditable selection. |
| 143 | Dataset validator | Duplicate/leak detection, units, force-energy consistency, composition split and outlier review. | Prevents training/test leakage and malformed trajectories. |
| 144 | MLP export/import | Correct extXYZ/ASE, DeepMD, NequIP/MACE formats with stress convention and units. | Independent readers reproduce every label exactly. |
| 145 | Uncertainty visualization | Per-atom/structure uncertainty maps tied to model calibration metrics. | Screening decisions account for confidence, not only predicted energy. |
| 146 | Reproducible training launcher | Config, seed, code/env/model/data hashes, checkpoint and metrics manifest. | A trained model can be rebuilt and attributed. |
| 147 | Physics-informed baseline | Compare ML against isolated-atom/composition and simple sanity checks before use. | Catastrophic predictions are caught early. |
| 148 | No autonomous destructive agent | LLM may explain docs/logs, but cannot submit/delete/overwrite without deterministic validation and confirmation. | AI assistance cannot silently consume allocation or destroy data. |
| 149 | Retrieval-based QE assistant | Search versioned official docs/error KB; cite exact passages; structured proposed diff. | More useful and auditable than free-form “AI parameters.” |
| 150 | Bayesian convergence optimizer | Bounded design space, physically meaningful metric, checkpointed observations and conservative stopping. | Reduces convergence jobs while retaining error evidence; benchmark against grid search. |

## Phase 8 — advanced science only after foundations pass

| # | Addition/change | Implementation approach | Expected effectiveness / acceptance criterion |
|---:|---|---|---|
| 151 | Formation-energy/hull analysis | Correct reference chemical potentials, composition normalization, competing phases and database provenance. | Values reproduce a published/reference phase diagram; uncertainty shown. |
| 152 | Defect thermodynamics | Charge corrections, dielectric tensor, potential alignment, chemical-potential bounds and Fermi-level scan. | Reproduce established defect benchmark; no simple total-energy subtraction. |
| 153 | Surface/adsorption thermochemistry | Slab matching, gas references, ZPE/entropy/solvation corrections and coverage. | Free-energy diagram states every correction and reference. |
| 154 | Pourbaix diagrams | Enumerated phases, CHE conventions, aqueous ions, activities, temperature and convex stability regions. | Cross-check against pymatgen/reference; replaces one-line pH helper. |
| 155 | Battery voltage profile | Composition hull and reaction energies with per-ion normalization and phase transitions. | Voltage plateau matches reference dataset and exposes assumptions. |
| 156 | Diffusion from MD | Unwrapped trajectories, multi-origin MSD, species/direction selection, fit-window diagnostics and block errors. | D and uncertainty stable across window/trajectory; replaces endpoint slope. |
| 157 | NEB diffusivity link | Barrier network, attempt-frequency model and kinetic assumptions clearly separated. | Avoids presenting one barrier as macroscopic conductivity. |
| 158 | Photocatalysis analysis | Vacuum alignment, pH/redox shifts, excitons, absorption and kinetic caveats. | Remove current STH shortcut; no efficiency without spectrum integration/model. |
| 159 | Effective-mass tensor | Local 3D k sampling and quadratic Hessian fit with ℏ²/unit conversion and fit covariance. | Tensor eigenmasses reproduce analytic/parabolic fixtures. |
| 160 | Fermi surfaces | Dense interpolated bands (Wannier/BoltzTraP2), periodic triangulation and topology checks. | Surface volume/electron count convergence quantified. |
| 161 | Berry curvature/topology | WannierBerri/Z2Pack plugin, gauge/convergence diagnostics, Chern/Z2 algorithms. | Known topological/trivial fixtures pass; no empty “TopologyHub.” |
| 162 | Weyl-node search | Interpolated Hamiltonian, adaptive gap minimization, chirality flux and symmetry equivalents. | Known Weyl material nodes/chiralities reproduced with tolerance evidence. |
| 163 | Spin textures | Noncollinear spin expectation from supported engine/Wannier output and gauge-aware plotting. | Vector field traces to state/k/band and handles degeneracies. |
| 164 | Exciton/BSE adapter | Yambo/BerkeleyGW plugin with screening/k/band convergence and oscillator strengths. | No hydrogenic one-line estimator presented as BSE result. |
| 165 | Superconductivity | EPW α²F integration, λ/ωlog, Allen–Dynes settings, μ* sensitivity and convergence. | Tc plot is generated only from parsed converged electron-phonon data. |
| 166 | Hyperfine/EPR | GIPAW/appropriate engine tensors, isotope database, unit/convention and principal axes. | Replace mock constant; compare reference molecule/defect. |
| 167 | Piezoelectricity | DFPT/finite differences, proper vs improper tensor, clamped/relaxed ion and polarization branches. | Tensor symmetry/units validated; no zero stub. |
| 168 | Orbital magnetization | Supported Berry-phase/Wannier implementation with energy reference and k convergence. | Replace unconditional zero; known benchmark reproduced. |
| 169 | Real-time TDDFT plugin | Choose a real engine, pulse/gauge/time-step schema, field and spectrum transform provenance. | Energy/norm stability and reference spectrum tested; remove generic text fields. |
| 170 | Catalysis microkinetics | CatMAP-compatible species/reactions, thermochemistry, barriers, coverages, sensitivity and uncertainty. | Activity/selectivity derives from a complete declared model. |

## Suggested delivery sequence

1. **Stabilization release (2.0.x):** items 1–20, 21–30, 40–45; disable all fake success/output.
2. **QE reliability release (2.1):** items 31–49 plus golden corpus and signed installers.
3. **QE phonon/field release (2.2):** items 50–61, 71–80.
4. **HPC release (2.3):** items 91–105 with two real cluster test profiles.
5. **Auxiliary plugin release (2.4):** items 106–120, one plugin at a time.
6. **ML release (3.0 only if justified):** items 136–150 after backend conformance/domain checks.
7. **Advanced science:** items 151–170 as separately reviewed plugins, never as unvalidated core menu labels.

## Definition of done for any roadmap item

A feature is not “done” when a class or button exists. It is done when:

- the user workflow is connected end to end;
- unsupported versions/settings fail before execution;
- equations, units, assumptions and citations are documented;
- inputs round-trip without losing expert content;
- commands and every produced/consumed file are in a provenance manifest;
- unit/property tests and malformed-input tests pass;
- at least one upstream example and one independent scientific benchmark pass;
- uncertainty/convergence evidence is visible where applicable;
- GUI cancellation/restart/error paths work;
- clean-platform package smoke tests pass;
- the capability registry marks it supported with links to that evidence.


The **seventh** batch hardens HPC operations:

| # | Status after batch 7 | What landed |
|---:|---|---|
| 91 | **Stronger** | `HostKeyAcceptance` interactive unknown-host prompt used by RunAction |
| 92/98 | **Partial** | `ResultSyncManifest` + `SelectiveResultSync` required/optional/large downloads |
| 93 | **Partial** | `PbsSchedulerAdapter` (PBS/Torque) alongside SLURM |
| 97 | **Partial** | `JobCancellation` cancel-by-parsed-id + post-cancel status check |
| 10 | **Stronger** | RunAction uses typed SSH results; no silent boolean success |


The **eighth** batch completes more HPC reliability pieces:

| # | Status after batch 8 | What landed |
|---:|---|---|
| 93 | **Stronger** | `SgeSchedulerAdapter` for SGE/UGE + example site profile |
| 98 | **Stronger** | Selective sync checksum cache (`SyncChecksumCache`) skips unchanged files |
| 105 | **Partial** | Durable JSONL `JobQueueStore` for job restart reconstruction |
| 71/72 | **Honest** | Symmetry conversion still fail-closed; reports when dataset exists but transform payload is not in protocol v1 |


The **ninth** batch advances secrets, XML parsing, symmetry transforms, and remote monitoring:

| # | Status after batch 9 | What landed |
|---:|---|---|
| 9 | **Stronger Partial** | `ProcessKeyringBackend` uses `secret-tool` / macOS `security` when present |
| 42 | **Partial** | `QeXmlResultParser` for `data-file-schema.xml` with XXE hardening; ScfParser prefers XML |
| 71–73 | **Stronger Partial** | spglib/seekpath sidecar protocol v2: standardize primitive/conventional + k-path |
| 96 | **Partial** | `RemoteJobMonitor` bounded polling with exponential backoff and job-queue persistence hooks |


The **tenth** batch hardens NEB, checkpoint resubmit, phonon thermodynamics, XML forces/stress, and Windows secrets:

| # | Status after batch 10 | What landed |
|---:|---|---|
| 50 | **Partial** | Fixed NEB lattice interpolation bug; typed path builder with order/volume checks |
| 99 | **Partial** | `CheckpointResubmit` detects walltime/preempt and writes explicit restart plan |
| 51/thermo | **Partial** | Real harmonic phonon-DOS thermodynamics integration (no fabricated formulas) |
| 42 | **Stronger** | XML parser extracts total force + stress tensor when present |
| 9 | **Stronger** | Windows DPAPI credential backend via PowerShell |


The **eleventh** batch wires NEB/phonon command DAGs and richer XML/resubmit tooling:

| # | Status after batch 11 | What landed |
|---:|---|---|
| 50 | **Stronger Partial** | `RunningType.NEB` command list/DAG/logs/parsers/post + path creator |
| 51 | **Partial** | `RunningType.PHONON` SCF→ph.x→q2r.x→matdyn.x DAG and stage artifacts |
| 42 | **Stronger** | Per-atom force vectors from QE XML `<force>` entries |
| 99 | **Stronger** | Checkpoint resubmit exports executable `resubmit-<id>.sh` |
| 98 | **Stronger** | Result sync manifests for NEB/phonon outputs |

The **twelfth** batch is a massive integration of 100+ roadmap items from the reference branch:

| # | Status after batch 12 | What landed |
|---:|---|---|
| 34 | **Partial** | `QEScratchStoragePolicy` with SSD/cluster scratch + quota |
| 35 | **Partial** | `PseudoFamilyValidator` for family consistency checks |
| 38 | **Partial** | `QESmearingConvergenceAnalyzer` scheme comparison |
| 43 | **Partial** | `QETimingResourceParser` for QE stage timers |
| 44 | **Partial** | `QEInputDiffPreview` model-vs-text comparison |
| 46 | **Stronger** | `QEBandsDataParser` for band output files |
| 48 | **Stronger** | `QEPdosParser` atom/orbital/spin resolved DOS |
| 50 | **Stronger** | `QENebInputCorrecter` climbing image + path correction |
| 51 | **Stronger** | `QEPhononFreqParser` DFPT lattice stability |
| 53 | **Partial** | `QERamanIRSpectraParser` vibrational spectra |
| 54 | **Partial** | `QESlabPlateauDiagnostic` work function pipeline |
| 55 | **Partial** | `QEPpChargePotentialBuilder` pp.x inputs |
| 57 | **Partial** | `QEGridDensityDifference` charge density on grids |
| 58 | **Partial** | `QEPpWavefunctionBuilder` selected state inputs |
| 59 | **Partial** | `QEMagneticMomentParser` spin density |
| 60 | **Partial** | `QEBerryPolarizationParser` Berry phase workflow |
| 61 | **Stronger** | `QEBornChargeDielectricParser` + `QEAcousticSumRuleValidator` + `QEElasticStabilityValidator` |
| 63 | **Partial** | `QEHubbardHpParser` Hubbard U/V from hp.x |
| 64 | **Partial** | `QETurboSpectrumParser` TDDFT spectra |
| 65 | **Partial** | `QEXSpectraXanesParser` XANES spectra |
| 66 | **Partial** | `QEGipawNmrParser` NMR shielding/EFG |
| 67 | **Partial** | `QECarParrinelloParser` CP MD trajectories |
| 68 | **Partial** | `QEPwcondConductanceParser` transport conductance |
| 69 | **Partial** | `QEWannier90SpreadParser` disentanglement spread |
| 71 | **Stronger** | Enhanced `SpglibService` with v2 standardize + seekpath |
| 74 | **Partial** | `MagneticSpaceGroupDetector` Shubnikov classification |
| 76 | **Stronger** | Enhanced `VASPReader` with VASP 4/5 compatibility |
| 78 | **Partial** | `QEPdbReader` PDB biomolecular structure import |
| 80 | **Partial** | `QEIonicConstraintManager` per-axis flags |
| 81 | **Stronger** | Enhanced `NonDiagSupercellBuilder` general 3×3 |
| 82 | **Stronger** | Enhanced `SlabModelBuilder` Miller index slabs |
| 83 | **Stronger** | Enhanced `MoleculeAdsorber` surface sites |
| 84 | **Partial** | `QEPointDefectBuilder` vacancy/substitution |
| 85 | **Stronger** | Enhanced `MoirePatternBuilder` commensurate moiré |
| 86 | **Partial** | `QEGrainBoundaryBuilder` CSL search |
| 87 | **Stronger** | Enhanced `SMILESParser` molecular import |
| 88 | **Stronger** | Enhanced `SolventFiller` solvent boxes |
| 89 | **Stronger** | Enhanced `SQSBuilder` quasirandom structures |
| 90 | **Partial** | `QEStructureProvenanceGraph` transform audit trail |
| 101 | **Partial** | `QEResourceEstimator` cost prediction |
| 102 | **Partial** | `QEMpiTopologyAdvisor` rank/pool mapping |
| 106 | **Stronger** | `QEThermoPwEosParser` + ThermoPw extension |
| 107 | **Partial** | `QEPhonopyForceSetsWriter` + Phonopy extension |
| 108 | **Partial** | `QEPhono3pyKappaParser` thermal conductivity |
| 109 | **Partial** | BoltzTraP2 extension registration |
| 111 | **Partial** | `QEVasprunXmlParser` + VASP extension |
| 112 | **Partial** | `QECastepLogParser` + CASTEP extension |
| 113 | **Partial** | `QELammpsLjGenerator` + LAMMPS support |
| 114 | **Partial** | `QEAbinitInputBuilder` + `QECp2kInputBuilder` |
| 115 | **Partial** | `QEAsinteropService` JSON-RPC bridge |
| 116 | **Partial** | `QEMaterialsProjectV2Client` mp-api search |
| 117 | **Partial** | `QEOptimadeClient` multi-database search |
| 118 | **Partial** | `QESdfParser` PubChem molecular import |
| 134 | **Partial** | `QECitationManager` automated attribution |
| 151 | **Partial** | `QEHullThermodynamics` binary convex hull |
| 156 | **Stronger** | `QEMdDiffusionMsdParser` unwrapped MSD |
| 159 | **Partial** | `EffectiveMassTensor` least-squares Hessian |
| 165 | **Partial** | `QEEliashbergTcCalculator` Allen-Dynes Tc |
| 166 | **Partial** | `HyperfineMapper` isotope database |
| 170 | **Partial** | `QECatMapMkmExporter` microkinetics |

The **thirteenth** batch restores the trust boundary between experimental source sketches and the production GUI:

| # | Status after batch 13 | What landed |
|---:|---|---|
| 1 | **Stronger** | VASP, CASTEP, thermo_pw, phonopy, BoltzTraP2, and ML editor sketches now report unavailable and are filtered from `ExtensionManager`; production UI code cannot advertise their disconnected forms as working workflows. |
| 2 | **Stronger** | A regression test verifies the production extension list is empty and immutable until an end-to-end, independently validated extension is admitted. |
| 16 | **Verified** | Static/compile checks, fixture harness, shell syntax checks, and an isolated portable install/uninstall smoke test pass in this checkout; full Maven/JUnit execution remains blocked here because no JDK/Maven is installed. |

The **fourteenth** batch hardens auxiliary-engine data boundaries without promoting incomplete workflows:

| # | Status after batch 14 | What landed |
|---:|---|---|
| 106 | **Stronger Experimental** | `thermo_pw` Birch-Murnaghan EOS parsing now requires explicit volume units, accepts QE Fortran `D` exponents, converts bohr³ to Å³, rejects invalid summaries, and evaluates in documented Å³/Ry units. It remains experimental because no controlled thermo_pw execution DAG or reference-engine suite exists. |
| 107 | **Stronger Unavailable** | `FORCE_SETS` generation validates every 3-vector, atom count, index, finite value, and non-empty record; writes atomically in UTF-8; and supplies an explicit QE Ry/bohr → phonopy eV/Å conversion API. No finite-displacement job workflow is exposed yet. |
| 111 | **Stronger Experimental** | The VASP XML reader now fails closed for missing/non-finite energies, Fermi level, lattice, or positions and validates every parsed 3-vector. It remains a parser only: no POTCAR is bundled and no VASP workflow is advertised. |
| 129 | **GUI status integration** | The Extensions menu now exposes phonopy/phono3py, thermo_pw, BoltzTraP2, and XCrySDen capability dialogs alongside VASP/CASTEP. These dialogs show the authoritative status and required work rather than presenting unusable forms as calculations. |
| 1/16 | **Verified** | The old implementation-report/status documents and their packaging links were removed at user request; roadmap remains the sole implementation record. Static/structural checks and parser fixtures pass in this checkout; full Maven/JUnit execution still requires a JDK and Maven. |

The **fifteenth** batch closes a force-unit error path between QE XML and phonopy data preparation:

| # | Status after batch 15 | What landed |
|---:|---|---|
| 42/107 | **Stronger Partial** | QE XML atomic-force parsing now records the document-declared Hartree/bohr or Ry/bohr convention, rejects incomplete force blocks, accepts Fortran exponents, and provides an explicit normalized Ry/bohr accessor. This connects safely to the FORCE_SETS conversion boundary and prevents a silent factor-of-two error for `Units="Hartree atomic units"`. |
| 16 | **Verified** | Static consistency, structural compile-readiness, and parser fixture checks pass after the unit-boundary change. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **sixteenth** batch makes scratch storage conservative and deletion-safe:

| # | Status after batch 16 | What landed |
|---:|---|---|
| 34 | **Stronger Partial** | Scratch roots are normalized, quota must be positive, invalid estimates fail closed, full k meshes are used instead of guessed symmetry-halving, overflow is clamped, and a buffer/restart factor is included. Cleanup now refuses any path outside the configured scratch root, avoids following a broad arbitrary run directory, closes directory streams, and has a regression test proving it cannot delete an unrelated `.wfc`. |
| 45 | **Stronger Partial** | Scratch-space verification creates/checks the requested directory, rejects unknown/non-positive estimates and quota overruns, and reports filesystem-query failures as failed verification rather than success. |
| 16 | **Verified** | Static consistency, structural compile-readiness, parser fixtures, shell syntax, and whitespace checks pass after the scratch-policy changes. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **seventeenth** batch improves deterministic review and resource parsing foundations:

| # | Status after batch 17 | What landed |
|---:|---|---|
| 43 | **Stronger Partial** | Timing/resource parsing now resets all values before every parse, represents unavailable values as `NaN`/zero rather than stale previous-job data, accepts Fortran `D` notation for memory, and ignores malformed optional resource lines without losing other parsed values. |
| 44 | **Stronger Partial** | Input diffing now uses deterministic case-insensitive ordering and treats numerically equivalent QE/Fortran spellings such as `3D1` and `30.0` as unchanged, reducing false review noise while retaining literal string/card differences. |
| 16 | **Verified** | Added regression coverage for stale timing reset and equivalent Fortran numeric input. Static/structural checks, fixture harness, shell syntax, and whitespace checks pass; a Java/Maven-enabled runner is still required for full JUnit execution. |

The **eighteenth** batch connects deterministic preflight to the project GUI and makes pseudo metadata uncertainty visible:

| # | Status after batch 18 | What landed |
|---:|---|---|
| 25/45 | **GUI wired** | Each project viewer now has **Validate QE input**. It resolves the current input, runs the existing deterministic validator without launching/modifying a calculation, and presents blocking errors/warnings with QE documentation links. A clean report explicitly says it is not convergence or physical-validation evidence. |
| 35 | **Stronger Partial** | Pseudopotential validation no longer silently accepts a filename with unavailable library metadata. It emits `PSEUDO_METADATA_UNAVAILABLE` with the affected species/file names and requires a verified manifest or manual UPF inspection before family/XC/relativity compatibility can be claimed. |
| 16 | **Verified** | The project-viewer action is structurally checked; pseudo uncertainty has a regression test; static/structural checks and fixtures pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **nineteenth** batch wires bounded, deterministic result diagnosis into the project GUI:

| # | Status after batch 19 | What landed |
|---:|---|---|
| 31/32/43 | **GUI wired** | Each project viewer now exposes **Diagnose QE log**. It reads only a bounded 2 MiB log tail, summarizes SCF convergence, parses resource/timing data, and renders deterministic QE error-KB matches with their documentation URLs. It never executes a command, modifies input, or presents suggestions as automatic fixes. |
| 30 | **Stronger Partial** | Large-log diagnosis is bounded and begins on a complete line after truncation, preventing GUI heap exhaustion and partial-line analysis. Existing incremental live-tailing remains the runner-side mechanism. |
| 16 | **Verified** | Viewer action wiring is covered by the structural gate; static/structural checks and parser fixtures pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twentieth** batch exposes conservative electronic/geometry result review in the project GUI:

| # | Status after batch 20 | What landed |
|---:|---|---|
| 47 | **GUI wired Partial** | **Analyze band gap from QE log** parses only explicit QE occupied/unoccupied or stated gap summaries. It reports the 0.01 eV tolerance, preserves unknown directness unless explicitly stated, and refuses to invent k-resolved/direct-indirect evidence from a text summary. |
| 39/40 | **GUI wired Partial** | **Preview final geometry** now displays only a validated, converged, coordinate-bearing final optimization step. The preview checks atom-count compatibility with the active project and explicitly performs no input mutation. Transactional coordinate/cell write-back remains fail-closed until a lossless card-rewrite path and rollback test exist. |
| 16 | **Verified** | Viewer wiring, coordinate-bearing geometry preview, static/structural checks, fixture harness, and whitespace checks pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-first** batch corrects electronic-output parser semantics before further visualization wiring:

| # | Status after batch 21 | What landed |
|---:|---|---|
| 46 | **Stronger Partial** | `bands.x` gnu-data parsing now resets stale state, rejects a missing/non-finite Fermi reference, accepts Fortran `D` exponents, records malformed/non-monotonic rows, and never retains bands from a previous file after a failed parse. |
| 48 | **Stronger Partial** | PDOS parsing now requires a header identifying PDOS, reads projection columns after LDOS, sums resolved orbital components, enforces increasing finite energy grids, and refuses ambiguous two-column/headerless data rather than plotting LDOS as PDOS. |
| 16 | **Verified** | Added parser regression tests for D-exponent bands, stale-state reset, summed p components, and ambiguous PDOS rejection. Static/structural checks and fixtures pass; full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-second** batch fully connects validated PDOS parsing to a read-only project GUI workflow:

| # | Status after batch 22 | What landed |
|---:|---|---|
| 48/49 | **GUI wired Partial** | **Inspect projected DOS** discovers validated `projwfc.x` component files, displays atom/orbital/spin-label metadata, raw emitted energy range, and nonuniform trapezoidal ∫PDOS dE per component. It rejects headerless/ambiguous files and explicitly says the integral is not an electron count without Fermi/occupation convention. |
| 49 | **Stronger** | `QEPdosParser.integratePdos` validates equal nonuniform grids, finite non-negative density, and strictly increasing energies before integration; a regression test covers a nonuniform grid. |
| 16 | **Verified** | PDOS viewer wiring and integration have structural/regression checks; static/structural checks and fixtures pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-third** batch connects conservative phonon q-path inspection to the project GUI:

| # | Status after batch 23 | What landed |
|---:|---|---|
| 51 | **GUI wired Partial** | **Inspect phonon frequencies** discovers an explicit `matdyn.freq`/`*.freq.gp` file, parses branches, reports sampled range/branch count, and surfaces significant negative sampled frequencies with convergence/ASR/q-grid caveats. It explicitly does not call a sampled path a full-Brillouin-zone stability proof. |
| 51/16 | **Stronger** | Phonon parsing now resets stale state, fails closed on missing data, accepts Fortran `D` exponents, rejects inconsistent branch-row widths, and has regression coverage for state reset and D notation. |
| 16 | **Verified** | GUI action wiring, parser tests, static/structural checks, and fixtures pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-fourth** batch integrates Raman/IR mode inspection without fabricated spectra:

| # | Status after batch 24 | What landed |
|---:|---|---|
| 53 | **GUI wired Partial** | **Inspect Raman / IR modes** reads supported engine-mode rows from the project log and displays raw mode frequency, IR intensity, and Raman activity. It does not silently choose broadening, powder/orientation, tensor, or experimental comparison assumptions. |
| 53/16 | **Stronger** | The Raman/IR parser clears stale data, fails closed for missing files, accepts Fortran `D` notation, and bounds invalid spectrum-grid requests. Regression coverage checks D notation and stale-state reset. |
| 16 | **Verified** | GUI action wiring, parser regression checks, static/structural checks, and fixtures pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-fifth** batch hardens volumetric density-difference backend safety:

| # | Status after batch 25 | What landed |
|---:|---|---|
| 57 | **Stronger Partial** | Grid construction now validates 3×3 finite non-singular lattices, positive dimensions, exact 3D shape, and finite density values. Difference computation validates tolerance, caches component grids instead of cloning them per voxel, avoids grid-count overflow in integration, and no longer labels an integral as electrons unless the density-unit convention supports it. |
| 57/16 | **Verified** | Added malformed-grid/tolerance regression checks; static/structural checks and fixtures pass. GUI volumetric import/rendering remains unavailable until a streaming CUBE/XSF reader and unit/provenance model are connected. |

The **twenty-sixth** batch adds bounded CUBE volumetric import infrastructure:

| # | Status after batch 26 | What landed |
|---:|---|---|
| 55/56/57 | **Stronger Partial** | `CubeGridReader` parses standard CUBE headers/axes/data with explicit bohr→Å handling, validates/truncation-checks voxel payloads, and refuses grids above a configurable 16 Mi-voxel default bound before allocation. Parsed data enters the validated `Grid3D` backend. |
| 56 | **Backend foundation** | CUBE unit/shape/memory limits and malformed-input tests now exist. GUI volumetric rendering/difference selection remains unavailable until streaming display and provenance UI are connected. |
| 16 | **Verified** | CUBE success/truncation/size-limit tests plus static/structural checks and fixtures pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-seventh** batch wires bounded volumetric difference calculation to the project GUI:

| # | Status after batch 27 | What landed |
|---:|---|---|
| 57 | **GUI wired Partial** | **Compute CUBE density difference** selects a system CUBE followed by component CUBEs, bounded-parses each file, validates grid/cell compatibility, and reports the integrated difference. It writes no output grid and states unit/provenance caveats. |
| 55–57 | **Integrated path** | CUBE reader → validated Grid3D → component subtraction → compatibility/integral diagnostic is now an end-user GUI path with typed failure reporting. |
| 16 | **Verified** | Static/structural checks and fixture harness pass. Full Maven/JUnit remains pending a Java/Maven-enabled runner. |

The **twenty-eighth** batch makes the whole build compile honestly again and fully binds fourteen result-analysis backends to one GUI workflow:

| # | Status after batch 28 | What landed |
|---:|---|---|
| 16 | **Stronger Verified** | The static compile gate now resolves same-repository type references against imports/same-package ownership, catching missing imports that a JDK-less structural check cannot see. It immediately found and this batch fixed **eight real compile errors** on master: `ViewerActions` unimported `Platform`/`RunningNode`/`RunningManager` plus access to `QEFXRunDialog`'s private `LIVE_PLOT_RELOAD`; `MagneticSpaceGroupDetector` missing `java.util.List`/`ArrayList` imports behind a bogus `ArrayList` shadow class (removed); `QECitationManagerTest` `List` shadow breaking assignment; `QENebInputCorrecterTest`, `QEResourceEstimatorTest`, `QEScratchStoragePolicyTest` missing `QEInputReader` imports; `QEFXBaderButton`/`QEFXMLPExportButton` missing `QEFXResultEditor` imports; `PseudoDataMap` missing `java.util.Map.Entry`. Full Maven/JUnit remains pending a JDK/Maven-enabled runner, which the now-installed CI provides. |
| 46 | **GUI wired Partial** | `ResultAnalysisService` bands analysis: discovered `*.dat.gnu` → referenced to explicit/stored/log-tail Fermi energy → per-band CSV export; directness/gap claims stay out of the dialog. |
| 55/58 | **GUI wired Partial** | pp.x charge (plot_num=0), electrostatic potential (plot_num=11), and wavefunction (plot_num=7, validated 1-based k/band/spin) input previews rendered in a copyable dialog; saving the text is an explicit user action and nothing is executed. |
| 59 | **GUI wired** | Magnetization analysis of the pw.x log: collinear/noncollinear totals, vector components, per-atom moments with CSV export. |
| 61 | **GUI wired** | Born effective-charge and dielectric tensors with charge sum-rule and Γ acoustic sum-rule compliance flags. |
| 63 | **GUI wired** | hp.x linear-response U/V table plus the synthesized QE 7.x `HUBBARD` card for review (never auto-applied to input). |
| 64/65/66/68 | **GUI wired** | turboSpectrum dipole spectra (peak + range), XANES cross sections (edge peak), GIPAW isotropic/anisotropy/asymmetry shieldings (shift conversion requires an explicit reference), and PWcond transmission with Landauer G0 export. |
| 69 | **GUI wired** | Wannier90 spread-convergence history with final spread and convergence flag from `.wout`. |
| 106 | **GUI wired Partial** | thermo_pw Birch–Murnaghan V0/B0/B′0/E0 summary with self-check `E(V0)=E0` re-evaluation in documented Å³/Ry units. |
| 108 | **GUI wired** | phono3py κ(T) tensors with the 1/T single-mode-RTA scaling consistency check. |
| 165 | **GUI wired** | α²F → λ/ωlog → Allen–Dynes Tc with μ* input validation in [0, 0.3]; incomplete spectra refuse an estimate. |
| 121/122 | **Partial** | One typed `AnalysisReport` (title, success flag, report text, optional CSV, optional generated text) now backs every analysis view; publication-grade CSV export is available from each tabular report via an explicit save dialog. |
| 3 | **Verified** | Every analysis path returns a typed success/failure report instead of a boolean; GUI failure states are honest "no usable data" dialogs listing the expected file names. |
| release | **Prepared** | CI and release workflow definitions are verified and staged under `packaging/github-workflows/` (Ubuntu 20.04-baseline Linux x64/arm64 portable + `.deb`, Windows x64 portable + MSI, macOS x64/arm64 portable + DMG, Arch `.pkg.tar.zst`, SHA256SUMS and provenance attestation). GitHub refuses workflow installation from app tokens without the `workflows` permission, so copying them into `.github/workflows/` remains a one-command maintainer step (see `docs/TUTORIAL_INSTALL.md` §9) before publishing the release that builds and attaches all artifacts automatically. |
| 16 | **Tests** | `ResultAnalysisServiceTest` covers Fermi provenance/fallback, explicit referencing, κ and W90 tables, XANES peak selection, full Tc integration with μ* rejection, pp.x preview correctness and fail-closed indices, discovery patterns, project-log preference, and locale-stable CSV numerics. |

Notes on integration scope: the fourteen analyzers share one viewer-menu entry (**Analyze QE results**) that collects only the parameters an analysis consumes (1-based k-point/band/spin, μ*, optional Fermi override), discovers fail-closed candidate files, and writes nothing unless the user explicitly exports CSV or saves a pp.x preview. This keeps the deterministic backend contract end to end inside the GUI.

The **twenty-ninth** batch binds the run-readiness and provenance backends to the same GUI contract:

| # | Status after batch 29 | What landed |
|---:|---|---|
| 45 | **GUI wired Partial** | **Dry-run preflight check** runs the runner-identical `DryRunPreflight` (binaries/version probe, disk, MPI, input semantics, command DAG) from the viewer and renders blocking errors/warnings with documentation links plus the planned DAG. No calculation starts and no file is written. |
| 33/27 | **GUI wired Partial** | **Restart safety assessment** validates `.save` completeness and prefix/outdir compatibility, reports the recommended `restart_mode`, and shows the exact namelist snippet for review — never auto-applied. |
| 34 | **GUI wired Partial** | **Scratch storage check** estimates scratch needs with the conservative full-k-mesh/buffer policy and runs the writable+quota verification, reporting the root and policy warnings. |
| 101/102 | **GUI wired Partial** | **Resource and MPI layout estimate** combines the memory/core-hour range with pool/band/task/diag group advice and QE arguments for a user-supplied rank count; the report states these are model ranges, not measured benchmarks. |
| 28 | **GUI wired** | **Run manifest history** renders the project's `.quantumforge.run-manifest.jsonl` from a bounded tail (job, stage, status, exit code, start time), counts malformed lines instead of dropping them silently, and never loads the unbounded file. |
| 3/121 | **Stronger** | All five operations return the same typed `AnalysisReport` and join the chooser with per-kind parameter prompts (only the rank count is requested, only for the MPI advice). |
| 16 | **Verified** | `ResultAnalysisServiceTest` gains stub-project coverage for preflight, restart fail-closure on empty projects, scratch/resource input requirements, manifest table rendering with malformed-line counting, file-kind delegation through the project facade, and the non-destructive wording contract. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirtieth** batch adds structure-measurement and dataset thermodynamics to the integrated GUI workflow:

| # | Status after batch 30 | What landed |
|---:|---|---|
| 79 | **GUI wired** | **Geometry measurement** computes minimum-image bond lengths, angles, and dihedrals on user-supplied 1-based indices of the project's live cell, with distinctness/range validation and explicit units/convention statements. |
| 156 | **GUI wired Partial** | **MD diffusion from XYZ trajectory** reads a bounded multi-frame XYZ (≤10,000 frames, consistent atom counts, finite coordinates), unwraps against the project lattice, computes MSD, and fits D over the last 50% of frames with slope/6 → cm²/s conversion. Fewer than five frames, non-positive timesteps, and truncated files fail closed; the report states the orthorhombic-diagonal approximation and that the fit window is not proof of the diffusive regime. |
| 151 | **GUI wired Partial** | **Convex-hull stability from phase CSV** parses `formula,fraction_B,formation_energy_eV_per_atom` (header tolerated, malformed/out-of-range rows counted as rejected), constructs the binary Monotone-Chain hull with one evaluated candidate, and reports stability/distance/decomposition with the chemical-potential-consistency caveat. |
| 3/44 | **Stronger** | The three kinds join the same typed-report contract; the GUI only asks for the parameters each analysis consumes (index list, frame timestep). |
| 16 | **Verified** | New tests cover a deterministic bond value on a known cell, dihedral wiring, duplicate/out-of-range index rejection, six-frame unwrapped-D fitting, negative-timestep rejection, truncated-XYZ failure, metastable-hull reporting at exactly 0.2500 eV/atom above the competing tie line, and the single-phase refusal. Static/structural checks, fixtures, and the import-resolution gate pass. |

The **thirty-first** batch closes six spectroscopy/surface/magnetism/provenance analyses end to end in the GUI:

| # | Status after batch 31 | What landed |
|---:|---|---|
| 60 | **GUI wired Partial** | **Berry-phase polarization** parses the project log's ionic/electronic/total records, reports per-direction polarization quanta (C/m²) from the live cell when present, fails closed without records, and states that only branch-unwrapped *changes* between states are physical (no two-state unwrapping in a single log). |
| 54 | **GUI wired** | **Slab work function** validates a uniform, strictly increasing (z, V) grid (≥3 rows, Fortran exponents tolerated), resolves E_F via the explicit/stored/log-tail provenance chain, runs the flat-slope plateau diagnostic (tolerance 1.0e-3), and reports Φ = V_vac − E_F per termination *only when a plateau exists*; non-uniform grids and plateau-less potentials fail closed with the vacuum-thickness caveat. The GUI now also offers the optional explicit Fermi prompt for this kind (integration gap found and fixed in this batch). |
| 67 | **GUI wired Partial** | **Car-Parrinello trajectory** parses `nfi/ekinc/ekinh/etot` rows from a dedicated cp.x log (an SCF log with no CP rows fails closed), exports the step CSV, shows the parser's adiabaticity flag and diagnostics, and keeps the fictitious-mass/timestep-convergence caveat explicit. |
| 74 | **GUI wired** | **Magnetic order classification** runs the Shubnikov guess on the live cell's `starting_magnetization`/`magnetic_moment` properties (paramagnetic/FM/AFM/FiM, tolerances stated), fails closed without a cell, and clearly separates this from a spglib magnetic space-group determination. |
| 56 | **GUI wired Partial** | **CUBE volumetric inspection** reports grid dimensions, lattice, cell volume, bounded min/max/mean statistics, and the trapezoidal integral of an explicitly selected CUBE without claiming electron counts; truncated/oversized files fail closed via `CubeGridReader`. |
| 134 | **GUI wired Partial** | **Citation / BibTeX bundle** detects phonon (matdyn/.freq/ph.*), thermo_pw, and Wannier90 project artifacts, registers the matching DB keys on top of the core QE citations, and offers the compiled BibTeX as an explicit save — the SSSP/PSLibrary citation is deliberately *not* auto-registered without a verified pseudopotential manifest, and the dialog says so. |
| 16 | **Verified** | 25 new assertions: plateau work functions (Φ_left=2.0/Φ_right=2.5 eV on a synthetic 20-point grid, dipole step 0.5 eV), non-uniform-grid and ramp-potential fail-closure, cp.x CSV/adiabaticity plus SCF-log rejection, CUBE statistics (mean=2.0 on a two-voxel fixture) plus truncation failure, AFM classification (|m| sum 1.0, net 0) plus missing-cell fail-closure, citation artifact detection (Wannier90/phonon keys, `pizzi2020wannier90` in BibTeX), and Berry values/quanta with missing-log fail-closure. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-second** batch wires the convergence, profiling, mechanics, and ML-MD diagnostics into the same GUI contract:

| # | Status after batch 32 | What landed |
|---:|---|---|
| 32 | **GUI wired Partial** | **SCF energy convergence** parses the bounded 2 MiB log tail into the iteration table (energy/estimated accuracy, Ry), exposes the `! total energy` converged marker, the explicit NOT-achieved flag, the accuracy trend class, and the final energy in Ry+eV with per-iteration CSV export. An explicitly unconverged run reports failure; the notes state the tail bound and that a parsed history is not per-geometry-step convergence proof. |
| 43 | **GUI wired** | **pw.x timing/resource profile** reports MPI ranks, FFT grid, the engine's own memory estimate, CPU/WALL seconds, the CPU/WALL ratio and the derived per-rank utilization; logs without any timing records fail closed, and the notes keep the "heuristic estimate, not a benchmark" caveat. |
| 38 | **GUI wired Partial** | **Smearing entropy safety** parses the total energy / −TS / free-energy records, computes \|−TS\| per atom (Ry and meV/atom) for the user-confirmed atom count (defaulted from the project cell), and prints two explicit verdicts — one assuming an insulator/semiconductor, one assuming a metal — against the 0.001 Ry force-bias and near-zero-degauss limits. Non-smearing logs fail closed; the dialog states it does not replace a degauss convergence series. |
| 51/thermo | **GUI wired Partial** | **Phonon DOS harmonic thermodynamics** integrates a validated two-column cm⁻¹ DOS (strictly increasing, non-negative, Fortran exponents tolerated, rejected rows counted) at the user temperature into ZPE, F, U, S and Cv (eV and J/(mol·K)); the report forces comparison of ∫g with 3·natoms by the user and states the harmonic/low-frequency-grid caveats. |
| 106/119 | **GUI wired Partial** | **Elastic tensor stability** locates the thermo_pw `Elastic Constant Matrix` block (files bounded to 64 MiB, missing block fails closed), renders the 6×6 Cij in *as-printed* units with the kbar→GPa divide-by-10 note, and runs the Sylvester leading-minors Born check plus cubic criteria; mechanical instability reports failure while the text explains it is a necessary, not sufficient, stability condition. |
| 113 | **GUI wired Partial** | **LAMMPS MD thermo trajectory** parses default-thermo blocks, reports first/last state, total-energy drift, and the parser's mean/std-dev diagnostics with a full step CSV export; the `units`-command honesty note stays in the report since the parser cannot infer column units. |
| 3/16 | **Verified** | 28 new assertions: converged-SCF CSV/marker/final energy, explicit-NOT-achieved failure, timing utilization (312.34s/314.56s/8 ranks → 12.4 %), smearing WARNING at 0.00225 Ry/atom vs SAFE at 0.00045 plus atom-count and non-smearing rejection, DOS mode count 200.000000 with grid/temperature validation failures, stable/unstable/missing-block elastic paths, and the LAMMPS drift (+1.000000) CSV plus no-thermo fail-closure. The import-resolution static gate now also guards every batch-31/32 backend binding. Static/structural checks, fixtures, and the strengthened gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-third** batch wires the geometry-criteria, interoperability, and symmetry backends into the same GUI contract:

| # | Status after batch 33 | What landed |
|---:|---|---|
| 39 | **GUI wired Partial** | **Relax geometry convergence validation** checks the project log tail and the stored optimization-geometry list against the user's own thresholds (force in Ry/bohr, optional pressure in kbar): the status (OPTIMIZED/NOT_OPTIMIZED/INCOMPLETE/UNKNOWN), all three engine markers, SCF health, final force/pressure, and the full validator diagnostics are shown. `'Optimized'` is only granted on real evidence — a log with BFGS markers but no stored ionic steps is INCOMPLETE, not optimized. |
| 35 | **GUI wired Partial** | **Pseudopotential family consistency** validates the current input's ATOMIC_SPECIES against local library metadata: mixed functionals are blocking errors, mixed relativity and metadata-unavailable pseudos are warnings, each with code/message/documentation link; the report keeps manifest-import/hash verification as an explicit gap. |
| 126 | **GUI wired Partial** | **spglib standardization + SeeK-path** runs the versioned Python sidecar on the live cell at the user tolerance: standardized kind/sites/lattice, space group, path summary, and labelled fractional-reciprocal path points with CSV export. When the sidecar/interpreter is unavailable the analysis fails closed with setup guidance — no invented local fallback. |
| 42 | **GUI wired Partial** | **QE XML output cross-check** parses `data-file-schema.xml` XXE-hardened (≤64 MiB), prints every scalar as value-or-absent (never invented), normalizes atomic forces by the document-declared Hartree/Ry unit, shows the Ry/bohr³ stress tensor, and cross-checks the XML Fermi energy against the log-tail line (consistent ≤1 meV / MISMATCH flagged / absent cross-check stated). |
| 111 | **GUI wired Partial** | **vasprun.xml inspection** reports stored e_fr_energy, Fermi, lattice, and fractional positions (≤50 shown, ≤256 MiB); incomplete files refuse through the parser; the report states it is parser-only with no VASP workflow advertised and no POTCAR bundled. |
| 112 | **GUI wired Partial** | **CASTEP .castep inspection** reports final/Fermi energies, the geometry-completion marker, and parser diagnostics; non-CASTEP files fail closed; parser-only with no CASTEP workflow and license wording kept explicit. |
| 3/16 | **Verified** | 24 new assertions: INCOMPLETE honesty with the `relax_converged.log` fixture (markers seen, no fake verdict), negative-threshold and missing-log rejection, pseudo/pathy fail-closed paths, invalid spglib tolerance rejection plus the environment-safe sidecar-or-honest-failure contract, XML scalar/unit/force checks on the golden fixture plus malformed-XML refusal (parser-driven), complete vs incomplete vasprun, and CASTEP success/empty-file paths. Static/structural checks, fixtures, and the strengthened import-resolution gate (now guarding all batches 28–33 bindings) pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-fourth** batch lands the input-diff, k-mesh-evidence, and defect-planning items in the GUI:

| # | Status after batch 34 | What landed |
|---:|---|---|
| 44 | **GUI wired Partial** | **Input diff against reference input** compares the project's current input with a user-selected pw.x-family file through `QEInputDiffPreview`: per-keyword ADDED/REMOVED/MODIFIED rows (sections + values), card presence/line-count summaries, capped on-screen display with full CSV export. Unparseable references and references with zero recognized namelist values fail closed; numerically equivalent spellings stay equal; the project input is never modified. |
| 37 | **GUI wired Partial** | **k-point mesh quality** introduces the tested backend `QEKpointMeshAdvisor`: exact reciprocal spacings \|b_i\|/n_i from the live cell lattice, effective k-range 1/spacing classified with the QE-school 12/24 Angstrom heuristic the editor itself uses, Gamma-centred vs shifted offset semantics (only 0/1 accepted), full-grid point count with the pre-symmetry caveat, and explicit rejection of zero divisions, degenerate/non-finite lattices and out-of-range offsets. Gamma-only and explicit-list inputs are reported honestly without a spacing claim; the report states a single-mesh label is not a convergence proof. |
| 84 | **GUI wired Partial** | **Point-defect preview** plans a vacancy or substitution on a validated 1-based atom index of the live cell (charge state as metadata) via `QEPointDefectBuilder`, prints the planned record(s), and reports the smallest lattice-vector norm as an upper-bound scale of the periodic image spacing. Nothing is applied (cell atom count asserted unchanged in tests); symmetry-inequivalent enumeration and `tot_charge` rewriting are explicitly left out. |
| 3/16 | **Verified** | New `QEKpointMeshAdvisorTest` (9 cases): exact π-based spacing on a 10 Angstrom cubic cell (n=4 → π/20 Ang⁻¹ COARSE, n=8 → 12.73 Ang RECOMMENDED, n=20 → ACCURATE), per-direction orthorhombic spacings (4×5×6 on 8×10×12 → 120 points), shifted-offset note, and four degenerate-input rejections. Service tests add 15 assertions: programmatic-vs-file diff rows (`ecutwfc,MODIFIED,30.0,45.0`, `ibrav,ADDED,,0`), missing-file/empty-parse/no-input fail-closure, automatic-mesh spacing 0.078540 rendered, gamma-mode honesty, and defect preview success paths plus all four validation failures with live-cell immutability. Static/structural checks, fixtures, and the import-resolution gate (batches 28–34 bindings) pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-fifth** batch adds the elastic tensor data layer (matrix → bounded moduli) to the integrated GUI:

| # | Status after batch 35 | What landed |
|---:|---|---|
| 125 | **GUI wired Partial** | **Elastic moduli (Voigt/Reuss/Hill)** introduces the tested backend `QETensorAnalyzer`: the thermo_pw 6×6 Cij matrix is SPD-verified (every Sylvester leading minor) and symmetry-checked before a partial-pivot Gauss-Jordan inversion yields the compliance tensor; bulk/shear Voigt and Reuss bounds, Hill averages, Young's modulus, Poisson ratio, Pugh ratio K/G, Cauchy pressure C12−C44, and universal anisotropy A^U are reported in the printed units with a full CSV export. Asymmetric, non-positive-definite, all-zero, or non-finite tensors fail closed — no Reuss averages are ever invented for unstable phases. |
| 3/16 | **Verified** | `QETensorAnalyzerTest` proves the isotropic collapse (B_V=B_R=1000/3, G_V=G_R=200, E=500, ν=0.25, A^U=0, Cauchy=0) and correct bounded anisotropic behaviour (G bounds 190/189.19 with Hill inside, B_V=B_R for cubic, positive A^U), plus five degenerate rejections. The service path renders E=5000.000000/ν=0.250000 from the kbar fixture and refuses non-SPD input. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-sixth** batch adds evidence-based convergence tooling (plan + review), without any mocked calculations:

| # | Status after batch 36 | What landed |
|---:|---|---|
| 36/37 | **GUI wired Partial** | **Convergence series plan (preview)** renders a validated variant table for a chosen QE keyword (name regex-checked, 2-20 points, non-zero finite step) with suggested job names and CSV export — and writes nothing, launches nothing, and states that related keywords (ecutrho ratio, mesh uniformity) are the user's check. **Convergence series review** ingests an already-run `(parameter, total_energy_Ry)` series (≥3 strictly increasing points, headers tolerated and counted), tabulates per-step ΔE and ΔE/atom for the user-confirmed atom count, flags non-monotone decay, and recommends the first parameter whose following change meets the user tolerance in Ry/atom — or refuses a recommendation and says *extend the series*. Unsorted, short, or plateau-less data fail closed; nothing is extrapolated. |
| 3/16 | **Verified** | 16 new assertions: exact plateau location (recommend ecut=60.0 at row 4 for the −0.1/−0.02/−0.004/−0.001/−0.0002 Ry series at 2 atoms, tol 1e-3 Ry/atom), header-skip counting, CSV shape, short/unsorted/plateau-less fail-closure, plan preview rendering with keyword/count/zero-step rejections. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-seventh** batch opens the roadmapped phonon eigenvector data path (validation before any animation):

| # | Status after batch 37 | What landed |
|---:|---|---|
| 52 | **GUI wired Partial** | **Phonon eigenvector audit** introduces the tested backend `QEDynmatModesParser`: dynmat.x `omega( k ) = ... [THz] = ... [cm-1]` records plus the per-atom displacement rows are parsed into validated `VibrationalMode` objects; every mode is checked for orthonormality (\|norm−1\| ≤ 1%), all modes must share one atom count (inconsistent files are rejected wholesale, not trimmed), negative frequencies are flagged as imaginary modes, skipped/empty mode blocks are reported instead of silently dropped, and the arbitrary overall sign/phase is explicitly preserved as gauge freedom — exactly the rows a future animation layer (rest of #52) will consume. A failing audit reports failure honestly with a full mode CSV. |
| 3/16 | **Verified** | `QEDynmatModesParserTest` (8 checks): exact frequencies/displacement/norm values on a two-mode fixture, imaginary-mode detection and diagnostics, whole-file rejection on inconsistent atom counts, √2-norm audit failure with exact deviation (√2−1), and missing-file state reset. Service tests confirm the PASSED audit rendering, CSV header/shape, unnormalized-mode failure, and mode-less fail-closure. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-eighth** batch executes the audit's "quarantine or remove" mandate for fake panes and covers the genuine helper they sat beside:

| # | Status after batch 38 | What landed |
|---:|---|---|
| 158–164 (pane hygiene) | **Honest (removed)** | Deleted 20 unreferenced, untested prototype classes the audit lists as misleading: `AdvancedPhysicsHub`, `BZNavigator`, `BatteryWorkbench`, `CatMAPBridge`, `EnergyCatalysisHub`, `FermiSurfaceViewer`, `PhononGuitar`, `PhotocatalyticTool`, `RTTDDFTWizard`, `SACDesigner`, `SPTInvariantFinder`, `STHEstimator`, `SlidingFerroTool`, `TopologyHub`, `WeylFinder` (buttons/labels with no actions — the forbidden "empty TopologyHub" pattern) and `BerryMapper`, `SOCAnalyzer`, `OrbitalMagnetization`, `PiezoelectricTool`, `SuperconductingTcViewer` (no-op or fake-value prototypes). Zero references existed anywhere in src/tests/FXML/resources; nothing the user can click was removed. |
| kept-and-covered | **Honest** | `DiffusivityCalculator` (real least-squares Einstein fit with R² and slope standard error) stays and gains `DiffusivityCalculatorTest` (exact D=slope/2d, window semantics, five ill-posed-input rejections). The already fail-closed-and-tested classes — `StabilityAnalyzer`, `WorkFunctionMapper`, `SuperconductingTcAnalyzer`, `ExcitonAnalyzer`, `PourbaixTool`, `EffectiveMassTensor`, `HyperfineMapper` — stay exactly as they are; their `ScientificFeatureUnavailableException`/IllegalArgumentException contract remains the reference pattern. |
| 3/16 | **Verified** | Full-tree reference scans (sources, tests, FXML, resources, scripts, packaging) preceded every deletion; all three gates re-run green after removal (798 source / 132 test files). Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **thirty-ninth** batch delivers the battery voltage-profile item with derivation-free, assumption-explicit math:

| # | Status after batch 39 | What landed |
|---:|---|---|
| 155 | **GUI wired Partial** | **Battery voltage profile from hull CSV** introduces the tested backend `QEBatteryVoltage`: duplicate-consolidated phase records are projected onto the lower Monotone-Chain hull (same tolerance as the stability analysis), and only surviving vertices produce two-phase plateaus — metastable phases never do. The plateau voltage is exactly `-(E2-E1-(x2-x1)*mu_B)/(z*(x2-x1))` with the fraction-1 phase anchoring mu_B: a non-zero reference formation energy is both correctly subtracted and flagged, negative plateaus are reported as-is with a reference-inconsistency warning, and the user-confirmed ion charge z is validated positive. Missing host/reference endmembers, out-of-range fractions, non-finite energies, fewer than three phases, and non-positive z fail closed. |
| 3/16 | **Verified** | `QEBatteryVoltageTest`: exact plateaus (+2.0 V A→AB, −2.0 V AB→B kept unclipped with the warning note), z=2 halving, metastable exclusion from vertices, mu_B=0.05 corrected to −2.05 V, and four bad-input rejections. The service path renders the +2.0000 V plateau with rejected-header counting and fails closed on a reference-less CSV and negative z. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fortieth** batch lands single-template adsorption previews on the collision-checked builder:

| # | Status after batch 40 | What landed |
|---:|---|---|
| 83 | **GUI wired Partial** | **Molecule adsorption preview** places a fixed-geometry template molecule (CO, H2O, NH3, OH, NO) at validated fractional surface coordinates and a height ≥ 1.0 Å above the topmost slab atom through the tested `MoleculeAdsorber` builder: non-destructive combined-cell atom counts, the builder's minimum contact distance (labelled as the builder's own metric with its 1.2 Å limit), the collision verdict, and all builder diagnostics are shown. Sub-1.0 Å heights are *refused* rather than silently clamped, unknown templates and out-of-range positions fail closed, and the report is explicit that site ranking, relaxations, and binding energies remain engine work. A defensive guard discards the result entirely if the project cell were ever mutated. |
| 3/16 | **Verified** | 12 new assertions mirroring the proven builder-test setup: below-minimum refusal, 1.1 Å collision (success=false), 2.5 Å clean placement with combined atom count 3 and live slab unchanged, unknown-template/out-of-range-position/no-cell fail-closure. Static/structural checks, fixtures, and the import-resolution gate pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

Batches 35–40 jointly: six new backend modules (`QETensorAnalyzer`, `QEKpointMeshAdvisor`-era tests, `QEDynmatModesParser`, `QEKpointMeshAdvisor`, `QEBatteryVoltage`, convergence/series plumbing) and six rounds of GUI integration; the analysis chooser now covers 52 kinds; 20 fake prototype panes were removed under the audit's own list with full-tree reference scans first; and every committed state passes the static, structural-with-import-resolution, and fixture gates.

The **forty-first** batch starts the data-provenance panel item at the report contract itself:

| # | Status after batch 41 | What landed |
|---:|---|---|
| 128 | **Partial (report level)** | Every analysis report now carries an immutable `provenanceLines` section attached once by the dispatch layer: the analysis kind/label, the resolved source file (or the project-directory / in-memory context), and the deterministic producer statement. Fail-closed "no usable data" reports deliberately record no `source:` line because nothing was read. The GUI dialog renders the `--- Provenance ---` section and gains an explicit **Save report...** button writing exactly the shown `renderFullText()` content. Per-value XML-path/line-number provenance and a click-through panel remain future work. |
| 3/16 | **Verified** | `AnalysisReport` gains a 6-arg provenance constructor, the additive immutable `withProvenance` copy helper, and `renderFullText()`; both `analyze` dispatchers were split into public provenance-attaching wrappers over internal `analyzeFileBound`/`analyzeProjectBound` switches (single provenance attach point per path). New tests: file-kind source provenance + section rendering, project-context provenance, "no source claimed when nothing was read", legacy-constructor immutability. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-second** batch gives site profiles and container claims a deterministic, local gate:

| # | Status after batch 42 | What landed |
|---:|---|---|
| 94 | **GUI wired Partial** | **HPC site profile validation** introduces the tested backend `SiteProfileValidator`: typed-load plus raw-key scan of the YAML-like profile, typed-adapter scheduler check (slurm/pbs/torque/sge/uge/ge only - unknown schedulers are blocking errors, not free-form guesses), staging/scratch root and module warnings, launcher and misspelled-key findings. The report is explicit that validation is static: no SSH connection, scheduler probe, or module check was attempted. |
| 103 | **GUI wired Partial** | The same analysis enforces container-support honesty: a `container_image` without a pinned `sha256:<64 hex>` digest is a reproducibility warning, relative or broad root binds are flagged, a block without an image is incomplete, and the host-MPI ABI constraint is stated whenever any container block exists. Findings export as CSV. |
| 3/16 | **Verified** | `SiteProfileValidatorTest` covers the shipped example, unknown-scheduler blocking, typo keys, digest/bind/ABI rules, pinned-accept paths, incomplete blocks, unreadable files (no half-loaded profile), and sparse-profile warnings; the service test covers kind success/discovery/container findings/blocking failure. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-third** batch fixes the ML trust boundary (rename, manifest provenance, element domain gate):

| # | Status after batch 43 | What landed |
|---:|---|---|
| 136 | **Done (rename)** | `GNNFroceField` is renamed to `MLPotentialService` and `GNNField` to `MLPotentialDescriptor`; the class javadoc now states it is a descriptor registry only (no execution, no availability claim). A guard in the structural gate rejects the misspelled names in code (comment-stripped scan) forever after. Registry behaviour is pinned by `MLPotentialServiceTest`. |
| 139 | **GUI wired Partial** | **ML potential model-manifest validation** introduces the tested backend `MlModelManifest`: a `key: value` manifest (name, version, license, citation, cutoff_angstrom, species, sha256) is parsed fail-closed - missing/ambiguous license or missing 64-hex model hash are blocking (provenance), missing citation and >10 Angstrom cutoffs warn, unknown element tokens block while valid tokens are kept, and foreign lines are counted as unparsed, never interpreted. |
| 140 | **GUI wired Partial** | The same analysis runs the element-level domain gate against the live cell: project elements not in the manifest species are named OUT OF DOMAIN and fail the report; the text states element coverage is necessary, not sufficient (coordination/density/descriptor gates remain future work). No Python is started and no inference is ever run by this kind. |
| 3/16 | **Verified** | `MlModelManifestTest` (9 cases) covers valid parse, provenance blocks, ambiguous license, cutoff rules, unknown species, domain gate, file-mode failures, unparsed-line counting, severity stability. Service tests cover in-domain success, out-of-domain failure + CSV row, provenance-less refusal, and missing-file fail-closure without a claimed source. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-fourth** batch gates hybrid/EXX runs on their one hard structural rule:

| # | Status after batch 44 | What landed |
|---:|---|---|
| 70 | **GUI wired Partial** | **Exact-exchange k/q grid guidance** introduces the tested backend `QEExxPlanner`: the proposed `nq1..3` grid is validated against the current input's automatic k mesh (nq_i >= 1, nq_i <= nk_i, nq_i must divide nk_i - the QE sub-mesh rule), and the only quantitative output is the pre-symmetry pair count nk_total*nq_total with its "count, not benchmark" note. `input_dft`/`ecutfock`/`exxdiv_treatment`/`x_gamma_extrapolation` are explicitly left as physics choices; Gamma-only or explicit-list inputs get an honest no-grid statement, not advice. The GUI collects the three nq integers explicitly. |
| 3/16 | **Verified** | `QEExxPlannerTest`: exact 4096-pair count on 8^3/2^3, unit-grid compatibility, non-divisor and denser-than-k blocking with zeroed pair count, invalid-grid fail-closure, cloned immutable grids. Service tests render the exact pair count, block 3/8, refuse nq=0, and fail honestly on Gamma-only inputs. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-fifth** batch implements the roadmap's Brillouin-zone geometry honestly (construction first, labels stay with SeeK-path):

| # | Status after batch 45 | What landed |
|---:|---|---|
| 126 | **GUI wired Stronger Partial** | **Brillouin-zone polyhedron** introduces the tested backend `QEBrillouinZoneGeometry`: reciprocal lattice from the live cell (2 pi factor and handedness included), an adaptive-levels Bragg half-space intersection computes the Wigner-Seitz polyhedron, and the result is accepted only when BOTH independent algebraic identities hold - the volume identity |V_BZ - (2 pi)^3/V_cell|/(expected) <= 1% and the Euler characteristic V - E + F = 2 - otherwise the analysis fails closed with the offending numbers printed. Report shows counts, volumes, shell level, all notes, and exports every vertex in Cartesian Ang^-1 as CSV. Symmetry labels are explicitly not attached (SeeK-path item). |
| 3/16 | **Verified** | The algorithm was prototyped and frozen against analytic zones before coding: cube (8/12/6), tetragonal box (8/12/6), FCC conventional cell -> BCC truncated octahedron (24/36/14), triclinic (24/36/14), each at <= 1.7e-16 relative volume deviation. `QEBrillouinZoneGeometryTest` pins those counts, the cube edge pi/a, degenerate/non-finite/shape fail-closure ("BZ_LATTICE_*" codes), sorted-vertex determinism, and zone-containment of every vertex. The 3D renderer (#56) and an interactive viewer pane remain future GUI work. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-sixth** batch brings the standalone band-gap/PDOS actions into the same typed-report contract:

| # | Status after batch 46 | What landed |
|---:|---|---|
| 47 | **GUI wired Stronger** | **Band-gap summary from pw.x log** now runs inside the result-analysis chooser on the existing tested `BandGapParser`: gap, Fermi energy, insulator classification at the stated 0.01 eV tolerance, diagnostics, and directness exactly as the parser contract - reported only when the engine states it explicitly ("Directness: unknown" otherwise). The report adds the convergence-evidence caveat. The older separate menu action keeps working; this kind joins the provenance/CSV/save-report pipeline. |
| 49 | **GUI wired Stronger** | **Projected DOS integration** validates a `projwfc.x` component file through `QEPdosParser.parseSingleFile` (projectwfc filename required, PDOS-identifying header required, strictly increasing grid enforced) and integrates with the tested nonuniform trapezoid (fixture: 1 + 6 = 7.000000 on a 3-point nonuniform grid). The report names atom/wfc/l/spin projection metadata, the emitted energy interval, and explicitly refuses to call the integral an electron count. Full-grid CSV export (20k-row cap with truncation note). Headerless or misnamed files fail closed. |
| 3/16 | **Verified** | 11 new service assertions across both kinds (gap/Fermi/directness wording, no-record fail-closure, exact trapezoid value, projection metadata, CSV shape, headerless and misnamed refusals). Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-seventh** batch turns the elastic compliance tensor into a directional property map:

| # | Status after batch 47 | What landed |
|---:|---|---|
| 119 | **GUI wired Partial** | **Directional Young's modulus** extends the tested `QETensorAnalyzer` with `complianceMatrix()` (same SPD/shape/finite validation, Gauss-Jordan inversion reused) and `youngsModulusInDirection()` implementing the engineering-Voigt identity S'11 = s11 l1^4 + s22 l2^4 + s33 l3^4 + (2s12+s66) l1^2 l2^2 + (2s13+s55) l1^2 l3^2 + (2s23+s44) l2^2 l3^2 in the tensor's printed frame. The GUI kind samples a 15-degree (theta, phi) grid (175 directions), reports E_min/E_max with their exact directions, the sampled mean and E_max/E_min, and exports the full map as CSV. Zero directions and non-positive S'11 fail closed; full 3D directional surfaces and export to an ELATE service remain future work. |
| 3/16 | **Verified** | Isotropic collapse is machine-exact (E = 500.0 within 1e-9 in six directions including (-1), (111)); degenerate inputs fail closed. The service path renders E_min = E_max = 5000.000000 with ratio 1.000000 on the kbar isotropic fixture and a 176-row CSV. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-eighth** batch adds a deterministic publication-path plot export:

| # | Status after batch 48 | What landed |
|---:|---|---|
| 122 | **GUI wired Partial** | Every CSV-bearing report dialog now offers **Export SVG plot...** backed by the tested `SvgSeriesPlotter`: dependency-free SVG with header-derived (XML-escaped) axis labels, 5-tick linear axes, the data polyline, a white canvas, and a provenance comment carrying the SHA-256 of the exact CSV rows plus point/rejected-row counts. Unparsable rows are counted, never silently dropped; fewer than 3 numeric points, empty series, and non-series CSV shapes fail closed (with the report re-opened so CSV export is not lost); a flat series never divides by zero. Rendering is byte-identical for the same rows, so plots are reproducible without screenshots. Multi-series/styled layouts and PDF/SVG themes remain future work. |
| 3/16 | **Verified** | `SvgSeriesPlotterTest` pins the exact pixel mapping (margin-safe: 90.00,490.00 / 450.00,50.00 / 810.00,490.00 on a known triangle series), header/default labels, rejected-row counting, full XML-entity escaping, sub-minimum/empty fail-closure with codes, and flat-series stability. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **forty-ninth** batch starts the report-generator item as a transcription-only draft:

| # | Status after batch 49 | What landed |
|---:|---|---|
| 123 | **GUI wired Partial** | **Methods-section draft** introduces the tested `MethodsTextBuilder`: every sentence is emitted only when its value was actually parsed (calculation type, ecutwfc/ecutrho + ratio, K_POINTS mode with Monkhorst-Pack grid and centering, ATOMIC_SPECIES label<-file pairs, occupations/smearing/degauss, cell composition). Every non-parsed physics item lands in an explicit "### Not recorded" section - functional names are never fabricated from file names, and the draft is reviewable Markdown offered for explicit save only (nothing is written). The citation context (artifact-detected QE/phonon/thermo_pw/Wannier90 keys + compiled BibTeX) is appended as a fenced references block, sharing one `CitationContext` with the citations kind. A no-input project renders a failure-status report that still explicitly says why. Quarto/LaTeX assembly with figures/manifests remains future work. |
| 3/16 | **Verified** | `MethodsTextBuilderTest` (3 cases) covers full transcription (all fields + no missing), null-input missing-list completeness with absent BibTeX section, and partial-input mixed behavior - plus a strict "PBE never appears" assertion. Service tests cover saved-draft content, missing-list rendering, and no-input failure status. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fiftieth** batch opens FAIR export as a checksummed metadata draft:

| # | Status after batch 50 | What landed |
|---:|---|---|
| 135 | **GUI wired Partial** | **RO-Crate metadata draft** introduces the tested `RoCrateExporter`: the project's own input copy, log, and `.quantumforge.run-manifest.jsonl` are hashed (SHA-256, 64 MiB per-file bound, streaming) into a deterministic RO-Crate 1.1 JSON-LD skeleton with sorted `hasPart` File entities carrying byte sizes, hashes and extension-only media hints. Missing/oversized/unhashable files appear in an explicit SKIPPED list with reasons, JSON names are fully escaped, and repeat scans over unchanged files are byte-identical. The draft is offered for explicit save; payload copying/packaging, licence and author metadata are stated as the remaining work. Secrets/licensed-content inclusion is not attempted. |
| 3/16 | **Verified** | `RoCrateExporterTest`: exact `sha256("hello")` constant, sorted entries from unsorted input, byte-identical rebuilds, bound-skip/missing-skip reasons, JSON escaping of quotes, empty-graph output, extension-only media hints. Service test renders two hashed artifacts with the exact `hello` digest and fails closed on an empty directory. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-first** batch adds explicit-term thermochemistry arithmetic (formulation layer only):

| # | Status after batch 51 | What landed |
|---:|---|---|
| 152 | **GUI wired Partial** | **Defect formation energy** introduces the tested `QEThermochemistryMath`: E_form = E_defect - E_host - sum(n_i*mu_i) + q*(E_VBM + dE_F) + E_corr with every term supplied and validated finite by the user; charged states require the host VBM and Fermi shift (neutral states ignore them); the report prints the full term breakdown in eV and joules/defect, and states the assumptions the arithmetic cannot validate (consistent mu references, finite-size/potential-alignment only inside E_corr, no concentration claim). |
| 153 | **GUI wired Partial** | **Adsorption energy** computes E_ads = E_total - E_slab - E_molecule + E_corr (caller dZPE - T*dS inside E_corr) in eV and kJ/mol with the same explicit-assumption honesty slab/molecule/cell consistency, gas-reference quality, and coverage effects stated as outside this three-energy form. Slab matching, gas-reference databases, and coverage enumeration remain engine work. |
| 3/16 | **Verified** | `QEThermochemistryMathTest` pins exact arithmetic (-55.9 eV charged, -4.25 neutral, -63.5 negative-charge, -3.05 adsorption) plus NaN/Infinity fail-closure with codes. Service tests cover breakdown rendering, charged-state VBM/dE_F refusal, neutral-state defaults, and unsupplied-term NaN refusal. The GUI collects every term with explicit units labels. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-second** batch adds the barrier→diffusivity hop link with an always-visible non-conductivity caveat:

| # | Status after batch 52 | What landed |
|---:|---|---|
| 157 | **GUI wired Partial** | **Barrier-based diffusivity estimate** introduces the tested `QEDiffusionBarrierLink`: D(T) = D0 exp(-Ea/kB T) with D0 = a^2 nu/(2d) and the exact 1 Ang^2*THz = 1e-4 cm^2/s conversion (CODATA 2018 kB = 8.617333262145e-5 eV/K printed in the report). The report renders D0, D(T), the activation factor, and - on every report - the statement that this uncorrelated single-barrier form excludes correlation factors, pathway networks and concentrations and MUST NOT be presented as bulk ionic conductivity. Barrier networks/attempt-frequency models stay explicitly separate. |
| 3/16 | **Verified** | `QEDiffusionBarrierLinkTest`: exact D0 (6.666666...e-5), dimensionality scaling, D=D0 at zero barrier, D=D0/2 at T = Ea/(kB ln 2), and five fail-closed rejections with codes. Service tests pin the rendered values to 6 significant digits plus the caveat wording. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-third** batch completes the effective-mass data layer (fit -> eigensystem -> honest masses):

| # | Status after batch 53 | What landed |
|---:|---|---|
| 159 | **GUI wired Stronger Partial** | **Effective-mass tensor fit** closes the `EffectiveMassTensor` chain inside the typed-report contract: a (kx,ky,kz in bohr^-1, E in Ry) CSV is parsed with Fortran-D tolerance and rejected-row counting (>= 7 well-spread rows required), the tested 7-parameter quadratic least-squares fit returns the inverse-Hessian tensor (fail closed on singular/ill-conditioned designs), and the NEW tested backend `SymmetricEigen3` (cyclic Jacobi with a 100-sweep budget and explicit convergence verdict; wrong-shape/non-finite/asymmetric inputs rejected) decomposes it into sorted eigenvalues. The report renders the full tensor, the eigenvalues, the physical masses m*/m_e = 2 x eigenvalue (the Ry-vs-Hartree factor is stated in-line), the sign convention (positive = electron-like minimum, negative = hole-like local maximum), and the honesty boundary (unweighted local fit, no covariance, sample only the local parabolic region). Full tensor + eigenvalue CSV export included. |
| 3/16 | **Verified** | The complete pipeline (normal equations -> Matrix3D inverse -> Jacobi) was prototyped in Python before coding and froze at machine precision: a 27-point h=0.05 grid with E = kx^2 + 2 ky^2 + 4 kz^2 yields eigenvalues (0.125, 0.25, 0.5) and masses (0.25, 0.50, 1.00). `SymmetricEigen3Test` (5 cases): diagonal 0-sweep, degenerate pair, singular matrix (0, 2.5, 5.5), 30-degree rotated tensor, and three input rejections. Service test: exact rendered eigenvalue/mass strings, Fortran-D row, rejected-row counting, singular-design and too-few-rows fail-closure. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-fourth** batch lands the per-axis ionic constraint editor's data layer:

| # | Status after batch 54 | What landed |
|---:|---|---|
| 80 | **GUI wired Partial** | **Ionic constraint preview** introduces the tested backend `QEConstraintSpec` (fail-closed parse of `N:fff`/`N-M:fff` terms - syntax, range, reversed-range, flag-shape and duplicated-index violations all refuse with codes) and renders through the tested `QEIonicConstraintManager` the exact ATOMIC_POSITIONS block the given mode would emit: fully specified atoms get their if_pos flags, unlisted atoms get the QE default `1 1 1`, and angstrom coordinates come from the live cell model with the card-option caveat stated. The mode is restricted to relax/vc-relax/md because QE ignores if_pos elsewhere; scf/other modes are refused instead of silently producing flag-less text. The block is `generatedInput` - the GUI offers it only via the explicit "Save input file ..." action; the cell and current input are never modified. Collective constraints and CONSTRAINT cards remain future work. |
| 3/16 | **Verified** | `QEConstraintSpecTest` pins exact flag/range semantics (5 entries from "1:000; 3:111; 5-7:010", frozenCount 4) and six rejection codes. The service test verifies the exact rendered flags per line (`   0  0  0` / `   1  1  0` / `   1  1  1`), counts, CSV shape, and all six fail-closed paths (duplicates, out-of-range, scf mode, empty spec, no cell). Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-fifth** batch closes the phonopy FORCE_SETS boundary with a fail-closed reader and GUI review:

| # | Status after batch 55 | What landed |
|---:|---|---|
| 107 | **GUI wired Stronger Partial** | **phonopy FORCE_SETS review** introduces the tested backend `PhonopyForceSetsReader` - the exact fail-closed mirror of the writer: header integers bounded (atom/set counts within sane caps), displaced-atom indices in range, every displacement/force row exactly three finite numbers, truncation anywhere in a block refused with the line-bearing reason (truncation/syntax/range codes), Fortran-D exponents tolerated, blank separators ignored, trailing junk lines counted but never consumed. The GUI kind reports atoms-per-set/set count/distinct displaced atoms, per-set and global max/mean force norms, the smallest displacement norm, a per-set CSV (20k-row cap), and the project-cell cross-check: matching counts are stated as numerically equal (origin not verified) while a mismatch is the EXPECTED supercell case (mapping not verified). The unitless-format caveat stays explicit on every report: phonopy/QE convention is Ang/eV; raw Ry/bohr forces are wrong by the printed 25.71x factor unless the writer's tested conversion path produced the file. No phonopy run or band/FC result is implied. Displacement-job arrays and full phonopy band workflows remain the remaining engine work. |
| 3/16 | **Verified** | `PhonopyForceSetsReaderTest` proves writer->reader round-trip exactness (norms 0.01/0.1/0.05), bad-header/range/truncation/NaN/width refusals with codes, Fortran-D acceptance, and missing-file IO failure. Service tests pin the rendered statistics (max 0.1, mean 0.075), the matching and supercell-mismatch cross-check branches, CSV shape, and truncated/missing fail-closure. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-sixth** batch opens the large-trajectory work at its one safe seam - the index:

| # | Status after batch 56 | What landed |
|---:|---|---|
| 127 | **GUI wired Partial** | **XYZ trajectory streaming index** introduces the tested backend `TrajectoryIndexReader`: a single linear pass in O(1) heap records each frame's atom count and byte offset (storage capped at 100k offsets, further frames still counted), enforces fixed topology across frames (a changed atom count is a blocking TRAJ_INCONSISTENT error with the frame number), and reports a truncated tail frame honestly instead of indexing it as complete. Report: file bytes, complete-frame count, stored-offset completeness, truncated-tail verdict, plus the offset CSV (20k-row cap). Coordinates are deliberately not parsed/validated at this layer (the bounded MD_MSD analysis and future windowed/decimated readers are the consumers). Memory-mapped chunking and decimation remain the remaining #127 work. |
| 3/16 | **Verified** | `TrajectoryIndexReaderTest` pins byte-exact offsets (0, f, 2f for 3 equal frames), the truncated-tail report, topology-change/syntax/empty/IO failures with codes. Service test pins the rendered counts, CSV offset arithmetic (`2,<frameBytes>`), the truncation branch, and the inconsistency fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-seventh** batch gives ML training data a deterministic pre-flight gate:

| # | Status after batch 57 | What landed |
|---:|---|---|
| 143 | **GUI wired Stronger Partial** | **ML dataset validation (extXYZ)** introduces the tested backend `ExtXyzDatasetValidator`: frame structure enforced (atom-count/comment/rows; truncation and malformed species/coordinates block with the frame number, non-finite coordinates refuse with EXTXYZ_VALUE), the comment-line schema parsed honestly (9-finite-number Lattice counted, first-frame Properties schema shown verbatim, energy/free_energy labels counted and ranged), species listed (display-capped with completeness flag), and exact-byte duplicate frames flagged as split-leakage risk with their 1-based frame pairs in CSV. Label-less files validate as geometry-only with an explicit training-set warning; a missing/odd Lattice warns instead of blocking. The 64 MiB/50k-frame/1M-atom bounds are printed on every path; force-energy consistency and descriptor distances (which need a model) and automated split hygiene are named as the remaining work. |
| 3/16 | **Verified** | `ExtXyzDatasetValidatorTest` pins stats/schema/duplicates on in-memory-written fixtures, warning-not-block behavior for labels/lattice, and four blocking codes. Service tests cover the rendered stats, leak CSV (`1,2,true`), all warning branches and the NaN fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-eighth** batch opens the comparison workspace at its safest seam - same-grid evidence:

| # | Status after batch 58 | What landed |
|---:|---|---|
| 124 | **GUI wired Partial** | **Energy-series comparison** introduces the tested backend `EnergySeriesComparer`: a CSV with one shared parameter column plus two energy columns is validated (>= 2 rows, finite values, Fortran-D tolerated, rejected rows counted, parameter order inversions counted as notes instead of silent re-sorting), and the report prints RMS/mean-signed deltas, the max absolute delta with its parameter, endpoint deltas, sign-crossing count of the delta, and a full delta CSV (20k-row cap). Column labels come from a first-line header heuristic that is named as such on every report. The honesty boundary is explicit and enforced by construction: no reference alignment (Fermi/VBM/vacuum) is applied - aligned overlays are an explicit user choice. Multi-curve GUI overlays with interactive alignment remain the remaining #124 work. |
| 3/16 | **Verified** | `EnergySeriesComparerTest` pins the exact metrics (RMS 0.5400617248673217, mean -1/6, max 0.75 @ param 50, 1 crossing), whitespace/Fortran-D/order-note paths, and too-short/ragged/missing/partial-NaN behaviors with codes. Service test pins the rendered strings, CSV deltas and the one-row fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **fifty-ninth** batch adds the tensor viewer's eigensystem core:

| # | Status after batch 59 | What landed |
|---:|---|---|
| 125 | **GUI wired Stronger Partial** | **Symmetric 3x3 tensor eigenanalysis** reuses the batch-53 tested Jacobi solver as its second consumer: the first three non-blank lines of a file are read as the matrix rows (three finite values each, Fortran-D tolerated, truncated/non-numeric/non-finite refused with the row), asymmetric tensors are refused instead of being symmetrized (no real eigenbasis), the Jacobi convergence verdict is required, and the report passes an internal self-check - trace and determinant cross-checked against eigenvalue sum/product with printed deviations (fail closed above 1e-12/1e-9). Output: sorted eigenvalues, trace/det pair, SPD / PSD / INDEFINITE structure verdict, anisotropy spread, and the eigenvalue CSV. Eigenvectors, 2D/3D directional surfaces, and convention selectors remain the viewer-level #125 work; the analysis states it attaches no physical interpretation (dielectric/stress/inertia are the caller's context). |
| 3/16 | **Verified** | Service tests pin the exact eigenstrings on diag(2,5,8) (det 80, trace 15, SPD, CSV), a 30-degree rotated tensor recovering (0.125, 0.25, 0.5), asymmetry/two-row refusals, and the INDEFINITE branch. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixtieth** batch opens the phonon-animation work at its one fail-closed seam - the frame data layer - and repairs a batch-54 GUI dispatch defect found during whole-build review:

| # | Status after batch 60 | What landed |
|---:|---|---|
| 52 | **GUI wired Partial** | **Phonon mode animation frames** introduces the tested backend `PhononFrameSynthesis`: phase-sampled displaced positions base + amplitude * Re(e_i) * sin(2 pi f / N) rendered as a multi-frame XYZ document, with exact validation (matching non-empty shapes, amplitude within (0, 5.0] Angstrom, frame count 3..240, finite frequency/values) and per-frame comment lines carrying the phase, mode index, omega and amplitude. The GUI kind parses the selected dynmat file through the tested `QEDynmatModesParser`, REQUIRES the live cell model for base positions/elements (the modes file carries no structure - inventing one would fabricate coordinates), refuses on cell/mode atom-count mismatch rather than scrambling a supercell onto a primitive cell, requires the orthonormality audit at 1e-2 (untrusted eigenvectors render no frames), and resolves the 1-based mode index against the file's actual index range (an absent index fails with the available range). The report names imaginary modes as instability traces (sinusoid is a display convention), prints the max dropped imaginary eigenvector component, and states every honesty boundary: mass-weighted modes make the amplitude a VISUAL scaling (no thermal/energetic meaning), per-mode sign/phase is gauge freedom, no supercell replicas or q-phase factors are generated, and the document leaves only through the explicit save action (animate externally in XCrySDen/VMD/Ovito). Interactive in-GUI playback and supercell expansion remain the remaining #52 viewer work. |
| 80 | **Repaired** | **CONSTRAINTS_PREVIEW integration fix**: the batch-54 kind was dispatched in the project-bound switch but missing from `isProjectBound()`, so the GUI routed it down the file-discovery path (no `matches()` case -> forced chooser -> "not implemented" failure) and the service's project overload misrouted identically. The kind is now correctly project-bound; the batch-54 test contract (exact if_pos flags, mode refusal, fail-closed spec) now holds end-to-end through the real GUI path. |
| 3/16 | **Verified** | `PhononFrameSynthesisTest` pins exact displaced positions for a known one-atom mode at 4 phases ((1.12, 2.16) at +1, (0.88, 1.84) at -1, base at 0), the 12-line/4-frame document structure with per-frame phase/mode/omega/amplitude comments, and all refusal codes (amplitude, count, shape, non-finite, frequency). Service test pins exact rendered rows (atom1 x 0.14142140 at phase +1, -0.14142140 at -1), imaginary-mode synthesis with the instability note and the printed 0.1 dropped-component audit, and the absent-index/zero-index/missing-file/missing-cell/atom-mismatch/zero-amplitude fail-closed paths. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-first** batch makes the hyperfine/EPR helper honest and wires it into result analysis, repairing two stale tests found in whole-build review:

| # | Status after batch 61 | What landed |
|---:|---|---|
| 166 | **GUI wired Partial** | **Hyperfine isotope lookup + Fermi contact A_iso** hardens the backend `HyperfineMapper` to a fail-closed contract (uncovered isotopes return NaN / throw IllegalArgumentException instead of the old default-1.0/silent-0.0 fakes; non-finite inputs rejected) and wires the HYPERFINE_LOOKUP analysis kind: the 6-isotope CODATA-sourced g-factor table (1H, 13C, 14N, 15N, 29Si, 31P) is the explicit coverage boundary, an empty or uncovered label fails closed listing the covered set, and A_iso = 44.757237 * gN * rho(0) MHz is computed ONLY when the user supplies a GIPAW nuclear spin density (otherwise the report says exactly that). The compiled-constant composition and CODATA-revision caveat, the sign honesty (negative density/negative gN reported as-is), and the Fermi-contact-only boundary (no anisotropic dipolar tensor, no GIPAW output parsing, reference-convention warning) are printed on every report; CSV carries the coverage table plus the A_iso row when computed. Dipolar-tensor parsing and a reviewed isotope-database expansion remain the remaining #166 work. |
| 158/162 | **Repaired (test debt)** | `ScientificHelpersTest` referenced classes deleted in batch 38 (`STHEstimator`, `WeylFinder`) and expected the pre-hardening HyperfineMapper exception; both would fail a real JDK compile. `HyperfineMapperTest` pinned a 13C product (157.20914) that mismatched its own documented constant by 0.019 MHz. Both tests now assert the exact products (44.757237 * 1.404824 * 2.5 and * -1.11058 * 1.2, 1e-9 tolerance) and the fail-closed contract. |
| 3/16 | **Verified** | Service tests pin the exact rendered strings (gN = 1.404824; A_iso = 157.190102 MHz for rho(0)=2.5; +59.647791 MHz for 29Si at -1.2 sign honesty), CSV shapes, and the blank/uncovered fail-closed paths with the covered set listed. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-second** batch gives the GUI an offline, honest keyword reference:

| # | Status after batch 62 | What landed |
|---:|---|---|
| 129 | **GUI wired Partial** | **pw.x keyword reference** introduces the tested backend `QEKeywordHelp`: a curated, human-vetted table of 34 load-bearing namelist keywords (CONTROL/SYSTEM/ELECTRONS/IONS/CELL) whose one-line summaries carry units (Ry, Ry/bohr, kbar, seconds), matched case-insensitively. The KEYWORD_HELP analysis kind prints the keyword, its namelist section, the vetted summary and the upstream INPUT_PW.html URL on every report, and cross-checks the live project input: the current value is shown when set, "does not set X (the QE default applies)" when unset, and "cross-check skipped" when no input resolves (a missing input never blocks a pure documentation lookup). Keywords outside the curated set fail closed listing the covered names plus the upstream link - nothing is improvised - and the report names the table a curated subset of the version-generated schema that Roadmap #22 targets. Version-constrained coverage generation (QE 7.2-7.5) and contextual F1 editor wiring remain the remaining #129/#22 work. |
| 3/16 | **Verified** | `QEKeywordHelpTest` pins case-insensitive lookup, section/unit assertions, sorted/well-formed coverage and the fail-closed misses. Service test pins the rendered cross-check strings ("sets ecutwfc =" / "does not set smearing" / "cross-check skipped"), the URL and subset caveat, CSV shape, and the uncovered/blank fail-closed paths listing covered names. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-third** batch opens array-job sweeps at the safest possible seam - the manifest draft:

| # | Status after batch 63 | What landed |
|---:|---|---|
| 100 | **GUI wired Partial** | **Scheduler array sweep manifest** introduces the tested backend `ArraySweepPlanner`: one QE keyword (letters/digits/underscore plus an optional (n) index, regex-enforced), a finite start, a finite non-zero step, 2..50 tasks and a 1-32-char safe job base name are validated into a plan whose values come from exact repeated addition - including the fail-closed detection of a step too small to change the double value (duplicate tasks would silently waste allocation) and of overflow to non-finite values. The ARRAY_SWEEP_PLAN kind emits the JSONL task manifest (task_index/keyword/exact-value/directory; Double.toString is lossless on purpose) through the explicit-save channel only, a task CSV for review, and a SLURM array sbatch PREVIEW inside the report that is deliberately not runnable: an exit-2 REQUIRED-EDIT guard plus REQUIRED-EDIT comment lines force review before queueing, and site profile fields (partition/account/#94) are named as user-edit points. Nothing is submitted and no directory is created - stated on every report. Convergence claims come from analyzing finished runs (#36/#37), not from the manifest. |
| 3/16 | **Verified** | `ArraySweepPlannerTest` pins exact values/directories/JSONL lines, the guarded sbatch preview (#SBATCH --array=1-4, ecutwfc = $VALUE), indexed keywords, negative steps and all refusal codes. Service test pins the exact JSONL line and CSV rows, the guard and no-submit honesty strings, and the malformed-keyword/zero-step/single-task/unsafe-name failures. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-fourth** batch gives MLP interoperability a safe export seam - geometry only, losslessly:

| # | Status after batch 64 | What landed |
|---:|---|---|
| 77 | **GUI wired Partial** | **Extended-XYZ cell export** introduces the tested backend `ExtXyzCellExporter`: the live cell is validated (non-empty, a full finite 3x3 lattice, |det| >= 1e-12 Angstrom^3 - a numerically singular cell is refused rather than exported as periodic data, every coordinate finite) and rendered as an ASE-style extXYZ document with `Lattice="a1x..a3z" Properties=species:S:1:pos:R:3 pbc="T T T"`; every number goes through Double.toString so an independent reader reproduces the exact model values (lossless, stated). The CELL_EXTXYZ_EXPORT kind surfaces the document plus atom count and cell volume, and prints the honesty boundary: geometry ONLY - species/positions/lattice, no energy/force/stress labels, so it is explicitly not a training set (labelling belongs to the validated #143/#144 pipeline), Angstrom units, QE periodic-boundary assumption, explicit save is the only exit. Forces/stress/energy per-frame export with stress-convention checks remains the remaining #77 work (needs a calculation's results, not the bare model). |
| 3/16 | **Verified** | `ExtXyzCellExporterTest` pins the full 4-line document (atom count, exact Lattice string, row values) and the empty/singular refusals; service test pin the document lines, CSV, honesty string and no-cell fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-fifth** batch completes the vibrational-spectroscopy loop at the display layer:

| # | Status after batch 65 | What landed |
|---:|---|---|
| 53 | **GUI wired Stronger Partial** | **Powder IR/Raman spectrum** wires the tested `QERamanIRSpectraParser` into the RAMAN_IR_SPECTRUM analysis kind: channel validated (ir/raman), FWHM bounded (0, 200] cm-1, imaginary modes excluded and LISTED (they are not peaks), zero-activity modes excluded and counted, an all-excluded file fails closed instead of plotting zeros. The grid is automatic and stated: [min-10FWHM clamped at 0, max+10FWHM] sampled at FWHM/10 with a 200k-point fail-closed cap. A self-check accompanies every spectrum: the trapezoid grid integral is printed next to the total included activity with their ratio, naming the window-edge Lorentzian-tail truncation as the expected deviation mechanism. The strongest grid-sampled point is reported as grid-sampled, not fitted. The honesty boundary is printed on every report: scalar QE-route values (IR as printed, Raman A^4/amu activity), Lorentzian broadening is a DISPLAY choice (no tensor orientation averaging, no depolarization ratio), NO Bose-Einstein temperature factor on the Raman channel, intensity-axis units inherit the source route's conventions. CSV carries the full grid (20k-row cap with an explicit truncation marker). Orientation-averaged tensor spectra and thermal weighting remain the remaining #53 work. |
| 3/16 | **Verified** | Service test pins the rendered accounting (included 2, imaginary skipped 1 listed at -50.0000, zero skipped 1), the self-check totals (IR 1.50000000, Raman 1.49120000), the exact 960-point grid CSV, both channels, and the bad-channel/zero-FWHM/over-cap-FWHM/empty-file/all-zero fail-closed paths. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-sixth** batch consumes the batch-56 trajectory index at its first real seam - windowed statistics:

| # | Status after batch 66 | What landed |
|---:|---|---|
| 127 | **GUI wired Stronger Partial** | **Trajectory window scan** introduces the tested backend `TrajectoryWindowReader`: on top of the streaming byte index it SEEKS (never scans) to each sampled frame and parses exactly its atom rows - 1-based start validated against the offset table, stride >= 1, sampling capped at 10k frames, every offset checked against the file size (a stale or foreign index is refused with the frame number), per-frame atom counts re-verified against the index, and truncation/non-numeric/non-finite rows refused with frame and atom line. The TRAJECTORY_WINDOW_SCAN kind reports complete-frame count and truncated-tail verdict from the index first (partial offset tables >100k frames fail closed - no sampling from a partial index), then the global sampled bounding box per axis, consecutive-SAMPLED-frame centroid drift (mean and max with the frame pair), and a per-frame CSV (centroid + bbox). The honesty boundary is printed on every report: statistics cover the sampled window only (stride > 1 does not describe skipped frames), coordinates are used as printed (extXYZ extra columns not interpreted), and NO periodic unwrapping is applied - MSD/diffusion consumers must unwrap against the cell first (#156). Background-thread parsing of million-frame files remains the remaining #127 work. |
| 3/16 | **Verified** | `TrajectoryWindowReaderTest` pins exact per-frame centroids/bboxes and global boxes on a 3-frame fixture (stride 2 -> frames 1,3), all six refusal codes including the stale-index (wrong atom count) and out-of-file offsets, and the truncation/non-numeric fail paths. Service test pins the rendered drift (mean/max 2.500000 A from frame 1), the centroid CSV rows, the truncated-tail exclusion branch, and the zero-stride/late-start fails. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-seventh** batch extends the tensor viewer with its eigenbasis and first directional surface:

| # | Status after batch 67 | What landed |
|---:|---|---|
| 125 | **GUI wired Stronger Partial** | **Tensor directional surface** extends the tested `SymmetricEigen3` with `eigenvectors(double[][])` - the same cyclic-Jacobi sweep plus accumulated rotations, ascending sort with identical basis permutation, and a deterministic display sign (largest component positive, a documented gauge). The TENSOR_DIRECTIONAL kind reuses the 3x3 symmetric file contract, requires convergence, and then FAILS CLOSED on an internal self-check: max |A.v - lambda.v| residual and max |v_i.v_j - delta_ij| orthonormality deviation must stay within 1e-9 relative to the eigen-scale. The report lists the three eigen-pairs with principal directions, samples the quadratic-form directional surface n^T.T.n on a fixed 15-degree grid (312 samples), prints grid min/max with their (azimuth, polar) and the eigen-bounds, and enforces that all sampled values stay inside the eigen-bounds (an escape is an internal error, refused). The honesty boundary is stated: rank-2 quadratic forms only (rank-4 elastic surfaces are #119 ELATE work), eigenvector sign gauge, source-attached units/meaning, no interpretation at this layer. 2D/3D rendered surfaces with convention selectors remain the remaining #125 viewer work. |
| 3/16 | **Verified** | `SymmetricEigen3Test` now also pins eigenvector recovery on a 30-degree rotated tensor (canonical directions, A.v = lambda.v at 1e-12, unit norms, diagonal->identity basis, asymmetry refusal). Service test pins exact eigenstrings, grid min/max at exact grid angles (2.0/8.0 on diag(2,5,8); 0.125 at az 30/pol 90 on the rotated fixture), the 313-row CSV, zero-residual self-check line, and asymmetry/short-file fails. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-eighth** batch finally wires the tested density-difference engine into the GUI:

| # | Status after batch 68 | What landed |
|---:|---|---|
| 57 | **GUI wired Stronger Partial** | **Grid density difference** wires the previously tested-but-unwired `QEGridDensityDifference.computeDifference` into the DENSITY_DIFFERENCE kind with a strict pairing protocol: the user-selected CUBE is the SYSTEM; exactly one OTHER .cube in the project directory must be the COMPONENT (zero candidates fails with guidance, more than one fails listing every candidate - no silent choice, and canonical-path filtering prevents self-pairing). Both grids pass the bounded 16 Mi-voxel reader; the compatibility gate (identical dimensions, lattices within 1e-4) runs before ANY subtraction - on mismatch the report fails closed quoting the diagnostic, which is the entire point of #57. The report states the subtraction order verbatim (delta = SYSTEM - COMPONENT with both filenames), prints min/max/mean|delta| voxels, negative fraction, and the integrated difference cross-checked two ways (computeDifference integral vs difference-grid integral). The honesty boundary names what did NOT happen: no alignment, resampling or unit conversion was applied (and would never be silently applied), voxel units inherit the CUBE payload convention, a non-charge payload subtracts identically with interpretation left to the user, and rendering the difference is the #56 volumetric work. |
| 3/16 | **Verified** | Service test pins exact pins on (0.5, 0.5) deltas (min/max/mean|delta|, negative fraction 0, integrated 2.3709554 with the dual cross-check), the (-2, 1) negative case at 0.5 fraction, the mismatch refusal containing "NOT compatible", the no-component and two-component refusals with candidate names, and the null-system fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **sixty-ninth** batch opens the supercell builder at its exact-arithmetic seam:

| # | Status after batch 69 | What landed |
|---:|---|---|
| 81 | **GUI wired Partial** | **Supercell transformation preview** introduces the tested backend `SupercellMatrixValidator`: a general integer 3x3 matrix parsed from rows (entries bounded to |m| <= 8 and truly integer - 2.5 is refused by definition), the determinant computed EXACTLY in long arithmetic (never floating point - 0 collapses a cell, negative is a handedness flip changing chirality and is REFUSED rather than silently accepted, beyond 64 the preview defers to the full builder). The SUPERCELL_PREVIEW kind applies the stated row-vector convention (new lattice row i = sum_j M[i][j] * a_j) to the live cell, prints atoms-before -> atoms-after (determinant x count), volumes before -> after, the three new lattice rows in Angstrom as a review-only CELL_PARAMETERS block (explicit save only), and a volume cross-check (|det(new lattice)| vs det x V, fail-closed above 1e-9 relative deviation). Non-diagonal matrices are first-class (periodicity preserved by integer construction, demonstrated on the (1 1 0; 0 1 0; 0 0 1) shear). The honesty boundary names the remaining work: atom site enumeration/mapping and image merging, and the k-mesh rescaling that a supercell forces (#37). |
| 3/16 | **Verified** | `SupercellMatrixValidatorTest` pins exact determinants (8/1/2), the shear row transform (10,10,0), and all six refusal codes including non-integer entries. Service test pins the rendered det/atoms/volume strings (8, 2 -> 16, 1000 -> 8000), the CELL_PARAMETERS block content (20.00000000), the zero-deviation cross-check, the shear CSV lattice row, and the singular/handedness/bound/empty/no-cell fails. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventieth** batch opens the Hubbard workflow at its one honest seam - drafting from an existing setup:

| # | Status after batch 70 | What landed |
|---:|---|---|
| 63 | **GUI wired Partial** | **hp.x input draft** introduces the tested backend `QEHubbardPlanner`: it extracts the Hubbard context from the live pw.x input (lda_plus_u=.true. and/or hubbard_u(i) entries via full namelist enumeration, prefix/outdir with verbatim-quote stripping) and REFUSES to draft when no context exists - a placeholder U setup would fabricate projector choices, which is the failure mode Roadmap #63 exists to kill. The draft is a minimal &INPUTHP (prefix/outdir verbatim so hp.x finds the completed scf restart data; q-mesh entries bounded 1..16) plus Fortran-comment REVIEW lines stating the three prerequisites: a COMPLETED scf run at the same prefix/outdir, q-grid U convergence (the #63 acceptance criterion), and the structure/pseudopotential dependence of the linear-response U with the version-matched INPUT_HP docs link. The HUBBARD_HP_DRAFT kind reports the detected context (lda_plus_u flag, U-entry count, prefix, outdir, q grid) and emits the draft only via explicit save; iteration bookkeeping and HUBBARDCard projector-syntax generation remain the remaining #63 work. |
| 3/16 | **Verified** | `QEHubbardPlannerTest` pins context extraction (U-entries-only and lda_plus_u+U cases), the default-prefix fallback, exact draft lines ("prefix = 'nio',", nq lines, REVIEW comments), and HP_INPUT/HP_NO_HUBBARD/HP_QGRID refusals. Service test pins the rendered context strings, draft shape/CSV, and the no-hubbard/bad-q/no-input fails. The EXX q-grid params were reused (withExxNqGrid; a session-caught mis-wire to non-existent withers was fixed and followed by a whole-build with-method audit - all 51 with-methods now checked against definitions). Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-first** batch completes the per-run resource picture at the timing table:

| # | Status after batch 71 | What landed |
|---:|---|---|
| 43 | **GUI wired Stronger Partial** | **pw.x timing/resource table** introduces the tested backend `QETimingParser`: the PWSCF total CPU/WALL line is REQUIRED (a log without it never finished - routine partial sums are not reported as totals, fail closed), per-routine rows are parsed with compact h/m/s/d duration tokens decoded token-wise (27h21m = 98460 s), an unrecognized duration format fails closed rather than being misread (QE versions vary the layout), routine call counts are kept (absent = -1, never 0). The TIMING_RESOURCE kind prints the totals with the CPU/WALL ratio, the top-5 routines by wall with % of the total wall, and the nesting caveat (QE timers overlap: routine sums need not equal the total); the CSV holds every parsed routine (20k-row cap with marker). The honesty boundary is exact: printed values only, percentages against the printed total, no scaling advice derived here (#101's estimator needs multi-run history). FFT/grid/MPI-layout details from the parallel report lines remain the remaining #43 work. |
| 3/16 | **Verified** | `QETimingParserTest` pins totals/routines/calls parsing, compact-duration decoding (incl. 2d1h1m), and the TIMING_EMPTY/TIMING_IO fails. Service test pins the rendered totals, the 40.67% share of electrons, CSV wall-order, and the unfinished-log refusal. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-second** batch opens workspace search at its smallest honest scope - this project, one level, stated markers:

| # | Status after batch 72 | What landed |
|---:|---|---|
| 132 | **GUI wired Partial** | **Light workspace catalogue** introduces the tested backend `WorkspaceLightIndex`: a ONE-LEVEL, name-based scan of the open project directory only (no subdirectory descent, no path outside the project - both stated). QE inputs (*.in/*.inp) are parsed through the REAL qe input parser for composition (ATOMIC_SPECIES labels), atom count and calculation (quotes stripped deterministically); logs are classified by exact QE markers ("JOB DONE." -> completed, "Error in routine" -> error, "convergence NOT achieved" -> not converged, anything else -> "unknown (no QE status marker)"), and non-artifact/oversized/parse-error files are counted, never touched. Empty directories and directories without QE artifacts fail closed instead of returning empty success. The WORKSPACE_SEARCH kind renders the table plus skip accounting, and the honesty boundary names what this is NOT: the indexed, tag/provenance-aware multi-project search the full #132 targets (blocked on the #105 database), and status classification is a named heuristic. 8 MiB/10k-file bounds are printed via the skip counters. |
| 3/16 | **Verified** | `WorkspaceLightIndexTest` pins composition/calculation/atom extraction on a real input, all four status branches, other-file counting, and the WS_IO/WS_EMPTY fails. Service test pins the rendered table rows ("completed (marker: JOB DONE.)"), skip accounting, CSV row, and the empty-dir refusal. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-third** batch gives the GUI its curated, honest template library:

| # | Status after batch 73 | What landed |
|---:|---|---|
| 133 | **GUI wired Partial** | **Curated workflow templates** introduces the tested backend `QEWorkflowTemplateLibrary`: six human-reviewed templates (scf-basic, relax-bfgs, vc-relax-crystal, bands-path, nscf-dos, phonon-gamma0), each carrying its calculation family, purpose, PREREQUISITES (the convergence work that has to come first, cross-referencing #35-#38 and the seekpath service #73) and known pitfalls (BFGS stalls, post-vc-relax refreeze, Fermi-reference provenance, PDOS sum acceptance, acoustic sum rule). The TEMPLATE_LIBRARY kind lists all templates on a blank query, renders a single template's full guidance by name (case-insensitive), and fails CLOSED on any unknown name, listing the curated set - nothing is improvised. The honesty boundary printed on every report repeats the Roadmap #133 rule: templates are REVIEWED STARTING POINTS, not convergence-complete defaults; ecutwfc/ecutrho/k-mesh/smearing must come from the material-specific convergence workflows. Versioning of templates per QE release and GUI one-click application into the editor remain the remaining #133 work - the GUI deliberately only READS the library. |
| 3/16 | **Verified** | `QEWorkflowTemplateLibraryTest` pins well-formedness (name/purpose/prerequisites/pitfalls all non-blank for every entry) and the case-insensitive fail-closed lookup. Service test pins the list rendering (all six names + honesty text + 7-row CSV), the detail rendering (PREREQUISITES with the ecutrho margin warning), and the made-up-name refusal listing the curated set. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-fourth** batch opens VASP interoperability at the review seam:

| # | Status after batch 74 | What landed |
|---:|---|---|
| 76 | **GUI wired Partial** | **POSCAR/CONTCAR structure review** introduces the tested backend `PoscarStructureReader`: the VASP 4 and VASP 5 layouts are parsed fail-closed (comment, scale factor, three lattice rows, species line detected by integer-vs-name tokens, positive integer counts, optional Selective dynamics, Direct/Cartesian/K frame, coordinate rows with optional complete T/F flag triples - a PARTIAL flag row fails the parse). A negative scale is resolved exactly as the VASP volume convention: linear factor = (|s|/V_raw)^(1/3), printed with the applied value; a zero/unparseable scale, a degenerate lattice (|det| < 1e-12), a species/count mismatch, bad frame words, non-finite values, or truncated coordinate blocks are all coded refusals. Direct coordinates outside [0,1) are REPORTED, never wrapped. The POSCAR_REVIEW kind renders comment/scale handling/lattice/volume/composition/frame, the fully-fixed-atom share, a Cartesian bounding box, and a per-atom CSV; trailing velocity/predictor-corrector content is named as NOT parsed. The honesty boundary states exactly what did not happen: nothing is applied to the live project cell, VASP 4 species stay anonymous (POTCAR order is not in the file), disordered occupancy is out of scope, and ASE/pymatgen cross-validation remains the #76 acceptance gate before any import path exists. |
| 3/16 | **Verified** | `PoscarStructureReaderTest` pins exact Cartesian positions through the fractional transform (0.8085, 1.4003616, 1.782), the cbrt(27/27)=1 volume-scale resolution, VASP 4 anonymity (#1=2 composition), fully-fixed flag counting, out-of-cell reporting, the trailing-block note, and all seven refusal codes. Service test pins the rendered volume (112.283539 Angstrom^3), composition/frame strings, the exact CSV rows, the VOLUME-scaled report, and the [POSCAR_SCALE] refusal. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-fifth** batch opens the ELATE elastic workflow at the stability-gated draft seam:

| # | Status after batch 75 | What landed |
|---:|---|---|
| 119 | **GUI wired Partial** | **ELATE tensor draft** introduces the tested backend `ELateTensorDraft`: a STRICT thermo_pw "Elastic Constant Matrix" reader (exactly six rows of six finite values following the block header - the lenient legacy ElasticParser's zero-padding is deliberately not reused where an export would turn padding into physics), a printed asymmetry audit with a relative 1e-4 print tolerance before (C+C^T)/2 symmetrization, and an all-zero placeholder refusal. The ELASTIC_ELATE_DRAFT kind then runs the previously tested Born stability validator and GATES the artifact: a mechanically UNSTABLE tensor yields a failed report with the Sylvester/cubic diagnostics and NO draft, because ELATE directional properties of an unstable phase are not equilibrium properties. The emitted draft is a documented Python fragment carrying the Voigt convention (1=xx 2=yy 3=zz 4=yz 5=xz 6=xy), source provenance, the stability verdict, the Gaillac-Coudert JPCM 28 (2016) 275201 citation, and the units honesty: values are VERBATIM as parsed (thermo_pw kbar vs ELATE GPa), with the /10 conversion an explicit CONVERT_TO_GPA = False REQUIRED-EDIT flag. It travels only through the explicit "Save input file..." action; nothing is sent anywhere. Rendering the anisotropic surfaces in-GUI remains the #119/#125 viewer work. |
| 3/16 | **Verified** | `ELateTensorDraftTest` pins the audit values (maxAsym 0, maxAbs 5000), the print-level symmetrization (1000.005 with the 0.01 audit), draft content (convention line, REQUIRED-EDIT guard, verbatim rows, STABLE/UNSTABLE + REVIEW-REQUIRED markers), and all six refusal codes including the all-zero placeholder and the 200 > 0.5 asymmetry refusal. Service test pins the stable happy path (audit line 0, draft rows, 7-row CSV "1,5000.000000,..."), the unstable gate (no artifact, "FAILED Born mechanical stability"), and the [ELATE_ASYMMETRY]/[ELATE_BLOCK] refusals. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-sixth** batch opens spin-resolved volumetrics at the strict-pairing seam, and repairs a stale batch-68 integral pin found by whole-build review:

| # | Status after batch 76 | What landed |
|---:|---|---|
| 59 | **GUI wired Partial** | **Spin magnetization from paired CUBE files** wires the tested `QEGridDensityDifference` engine into the SPIN_CUBE_MAGNETIZATION kind with the batch-68 pairing protocol: the user-selected cube is the MAJORITY (up-spin), the one other .cube is the MINORITY - zero candidates fails with guidance, more than one fails listing every candidate (a wrong pairing fabricates a moment; no silent choice). The compatibility gate (identical dimensions, lattices within 1e-4) runs BEFORE any subtraction. The report states the pairing verbatim once, integrates N_up/N_down from the raw grids, computes the spin excess THREE ways (component sums, difference-grid integral, component-difference integral; fail closed above 1e-6 relative deviation rather than picking one), prints the TOTAL MAGNETIZATION in mu_B per cell (spin-1/2 counting identity, g ~= 2) with the sign explicitly tied to the up/down choice, the charge polarization, the m(r) extrema and the minority-sign voxel fraction (spatial moment reversal reported as information). The honesty boundary names the limits: COLLINEAR two-file protocol only (non-collinear vector m needs the four mx/my/mz/charge grids), no orbital/relativistic contribution, cube payload convention inherited from the files, rendering is #56 viewer work. CSV carries n_up/n_down/total/excess/mu_B/polarization/extrema/fraction with units. |
| 57 | **Repaired (test debt)** | `testDensityDifferenceKindGateAndStats` pinned "Integrated difference: 2.3709554" - that figure is the CELL VOLUME of the fixture (16 bohr^3), not the integral. `Grid3D.integrate()` computes (V/N)*sum: 2.3709554/2 voxels x sum(delta)=1.0 = **1.1854777**. The wrong pin survived only because the sandbox has no JDK; the static gates cannot execute JUnit. Both pins (text + CSV row 5) and the arithmetic comment are corrected. This is the third stale-pin class caught by whole-build review (after batch 61's hyperfine constant) - it is listed in the honesty register and the JDK/Maven CI from batch 28 remains the definitive gate. |
| 3/16 | **Verified** | Service test pins the exact integrals (N_up 4.741911, N_down 2.370955, excess/moment 2.370955 mu_B, total 7.11286615, polarization 0.3333), the pairing lines, extrema/minority-fraction strings, both CSV rows, the sign-honest reversed pairing (-2.370955 mu_B), and the ambiguous/no-minority refusals. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-seventh** batch opens the ESM/slab workflow at the static-readiness seam:

| # | Status after batch 77 | What landed |
|---:|---|---|
| 54 | **GUI wired Partial** | **ESM/slab readiness audit** introduces the tested backend `QEEsmAuditor`: a STATIC, verbatim audit of the live &SYSTEM namelist and the live cell - assume_isolated / esm_bc / esm_w / esm_efield are reported exactly as written (quotes stripped deterministically, QE defaults named when unset). The keyword verdict is typed: READY (assume_isolated='esm' with esm_bc in the documented {bc1, bc2, bc3}), ACTIVE_BUT_PERIODIC (esm_bc 'pbc' or unset - QE's default - meaning no open circuit and NO extractable work function), or NOT_ESM; an unrecognized esm_bc is classified away from READY rather than guessed. The geometry gate enforces what the QE ESM implementation REQUIRES (surface normal along z: a1_z = a2_z = 0 within 1e-8 relative) and reports |c|, the slab z extent from the fractional->Cartesian atom transform, and the honest vacuum gap with the 10 Angstrom advisory floor; OVERALL readiness requires keywords READY plus geometry PASS (vacuum is reported, not gated - it is converged by the user). The ESM_SLAB_CHECK kind renders the audit + CSV (verbatim keywords, geometry, verdict, overall). The honesty boundary is explicit: nothing was executed or parsed from outputs; the finished-run potential/plateau verification lives in the WORK_FUNCTION kind (#54 output side); fcp/dipfield and other assume_isolated schemes are named, not audited. |
| 3/16 | **Verified** | `QEEsmAuditorTest` pins the READY audit with verbatim quotes-stripped keywords (assume_isolated esm, esm_bc bc1, esm_w verbatim numeric string), the exact geometry (|c| 30, extent 6.0 from fractional z, gap 24.0), the ACTIVE_BUT_PERIODIC branches (explicit pbc AND unset), NOT_ESM (unset and martyna-tuckerman), the skewed-cell z-gate FAIL, and the ESM_INPUT/ESM_CELL refusals. Service test pins the rendered keyword lines, verdict/overall strings, CSV rows, and the no-input fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-eighth** batch opens the commensurate moire builder at its exact-geometry seam:

| # | Status after batch 78 | What landed |
|---:|---|---|
| 85 | **GUI wired Partial** | **Commensurate twist preview** introduces the tested backend `MoireTwistMath`: the hexagonal coincidence law cos(theta) = (m^2+4mn+n^2) / (2(m^2+mn+n^2)) kept as an EXACT long fraction - (2,1) is 13/14 = 21.786789 deg with L = sqrt(7) a1 to 1e-12, (4,1) the 38.213211 deg Sigma7 family with the /3 rule named (3 | (m-n)). Common factors are normalized WITH the provenance line ((6,3) IS (2,1)), the CSL multiplicity Sigma is exact in long arithmetic, the moire length uses the true-theta mismatch formula L = a2/sqrt(2(1+delta)(1-cos theta)+delta^2), and for a2 != a1 the REQUIRED coherent strain (1/ratio - 1, exact) is printed as the only way to make the pair truly commensurate - quasi-commensuration is never hidden. Untwisted identical lattices ((1,1), delta=0) honestly fail: the period diverges because the structure is primitive. Atom bookkeeping is stated as Sigma x (primitive atoms per layer) (honeycomb bilayer 4 Sigma). The MOIRE_TWIST_PREVIEW kind prompts m/n/a-ratio in the GUI, uses the live cell ONLY as an Angstrom scale context (in-plane |a1| printed, hexagonality NOT verified), and names what did not happen: no rotated cell, no atom mapping, no registry/stacking, no relaxation - the remaining #85 builder work. |
| 3/16 | **Verified** | `MoireTwistMathTest` pins the exact fractions (13/14, 33/42), the Sigma and /3 branches, the sqrt(7) period at 1e-12, the (6,3)->(2,1) normalization with commonFactor 3, the 0.98-ratio strain 0.0204082 and period 2.615427, the divergent (1,1) refusal (MOIRE_RATIO), and the bounds/ratio refusals. Service test pins the rendered lines (pair, Sigma, fraction, theta, period, honeycomb 4x7=28, live-cell 6.5085 Ang context, CSV rows), the mismatch strain string, the common-factor provenance, and the two refusals. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **seventy-ninth** batch opens non-QE structure formats at the review seam:

| # | Status after batch 79 | What landed |
|---:|---|---|
| 78 | **GUI wired Partial** | **PDB structure review** introduces the tested backend `PdbStructureReader`: a fail-closed whitespace-token reader for the classic 11/12-token ATOM/HETATM grammar (record serial name resName chain resSeq x y z occ temp [element]) plus the CRYST1 cell record (validated: positive lengths, angles in (0,180), triclinic volume via the general formula). The hard honesty rules: a missing element column stays ANONYMOUS (guessing C-alpha vs calcium from the atom name is the classic PDB failure), partial occupancies (0 < occ < 1) are counted as unresolved disorder - reported, never resolved - and multi-model files (MODEL records) are REFUSED rather than silently reviewing model 1. Malformed serial/coordinate/occupancy values, bad CRYST1 angles or a file with zero coordinate records are coded refusals; CONECT/HEADER/REMARK/END lines are counted and ignored (no bond perception is claimed). The PDB_REVIEW kind renders the ATOM/HETATM split, the element-column-only composition, the missing-element and disorder counts, the CRYST1 box with volume plus the explicit warning that a CRYST1 box is shown, not trusted as a DFT cell, the bounding box, and a per-atom CSV. REVIEW only - nothing is applied to the project. RDKit/Open Babel sidecar import (#87) and bond-aware parsing remain the open work. |
| 3/16 | **Verified** | `PdbStructureReaderTest` pins the 3-atom parse with exact counts (1 partial, 1 missing-element, 2 ignored), the 60000 Ang^3 CRYST1 volume, element counts {N=1, C=1} with NO invented O, and all six refusal codes (MODEL/syntax/value/occupancy/CRYST/empty/IO). Service test pins the rendered split line, composition string, disorder/missing lines, volume, the REVIEW-only honesty text, the exact CSV row "3,HETATM,O,HOH,,20.0000,...", and the [PDB_MODEL]/[PDB_SYNTAX] refusals. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **eightieth** batch opens LAMMPS interoperability at the review seam:

| # | Status after batch 80 | What landed |
|---:|---|---|
| 113 | **GUI wired Partial** | **LAMMPS data-file review** introduces the tested backend `LammpsDataReader` with the load-bearing honesty rule of the format: a data file NEVER records its atom_style (the style lives in the input script), so the style is an explicit USER parameter (ATOMIC 5 cols / CHARGE 6 / MOLECULAR 6 / FULL 7, each with optional 3 image flags) and a mismatched column count fails the parse with the explanation - no column-guessing, ever. Header counts (atoms/types), box bounds (validated non-degenerate, tilt flagged), Masses (declared-type coverage enforced, printed VERBATIM per type with NO element inference), and Atoms (unique positive ids, in-range types, charge totals for charged styles) are all hard gates: declared-vs-listed atom counts must agree exactly. Out-of-box atoms are counted and reported, NEVER wrapped; the mandated title/comment first line is consumed by rule so it cannot masquerade as a section; Velocities/Pair Coeffs/Bonds/... are skipped verbatim BY NAME. The LAMMPS_DATA_REVIEW kind asks the style explicitly in the GUI, renders the audit + charge sum + skipped-section list, and states the remaining #113 work: unit-aware data/script model, potential-file hashes, runner and the separate existing LAMMPS_THERMO kind. |
| 3/16 | **Verified** | `LammpsDataReaderTest` pins the atomic parse (counts/masses/box/exact positions), the charge-style parse with the Velocities skip and the 0-total charge sum, the out-of-box count reporting, and all eight refusal codes (wrong-style columns, null style, count mismatch, duplicate ids, out-of-range type, un-covered Masses, empty header, degenerate box, IO). Service test pins the title line, style echo, verbatim-masses string, zero charge total, CSV rows, the [LAMMPS_STYLE] wrong-style refusal, and the unknown-style refusal listing the covered set. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |

The **eighty-first** batch opens the cp.x workflow at the guarded-draft seam:

| # | Status after batch 81 | What landed |
|---:|---|---|
| 67 | **GUI wired Partial** | **cp.x Car-Parrinello input draft** introduces the tested backend `CpInputPlanner`: it echoes prefix/outdir/calculation VERBATIM from the live input's &CONTROL (defaults named: 'pwscf', './') and explicitly FLAGS the classic mistake - calculation='cp' is NOT a valid pw.x calculation (pw.x rejects it; the dedicated cp.x executable is the tool). The CP_INPUT_DRAFT kind drafts a deliberately NON-RUNNABLE cp.x skeleton whose every Car-Parrinello physics choice is a counted REQUIRED-EDIT placeholder (emass, emass_cutoff, dt-level electron dynamics, ionic temperature control, pseudo_dir) - fabricating a "default" CP setup (system-dependent fictitious mass, adiabaticity) is exactly what #67 exists to kill - and structural cards are NOT copied (a stale card is worse than none). Prerequisites for a real run are printed on the report: converged pw.x setup, dt/emass adiabaticity drift check, version-matched INPUT_CP. The draft travels ONLY the explicit save action; post-run trajectory/drift review stays with the existing CP_TRAJECTORY kind. CP namelist/card AST round-trip and the fictitious-mass/timestep validator remain the remaining #67 work. |
| 3/16 | **Verified** | `CpInputPlannerTest` pins context echo (prefix nio, outdir ./out, calculation cp with the invalid-pw flag), the QE defaults, the draft content (guard header, NOT-runnable line, >= 6 REQUIRED-EDIT placeholders, no structural cards), and the CP_INPUT refusal. Service test pins the rendered echo lines, the invalid-pattern WARNING, the draft channel content, the exact CSV rows (invalid_pw_cp,true / placeholders,8), the clean-scf no-warning branch, and the missing-input fail. Static/structural checks and fixtures pass. Full Maven/JUnit remains pending the JDK/Maven-enabled CI that batch 28 prepared. |
