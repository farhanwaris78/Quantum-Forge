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
