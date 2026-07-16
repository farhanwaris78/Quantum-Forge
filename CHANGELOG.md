# Changelog

## 2.0.0 — 2026-07-16

### Release engineering

- Added reproducible Maven/JDK 17/OpenJFX build, modern dependency declarations, JUnit tests, and CycloneDX SBOM.
- Added `quantumforge` launcher with `--version`, `--doctor`, remote-X11 software-rendering fallback, file arguments, and clear Java errors.
- Added platform portable archives, jpackage native packaging, Arch PKGBUILD, safe per-user install/update/uninstall, desktop/PATH integration, and checksum verification.
- Added ready-to-activate CI/release workflow templates for Ubuntu 20.04 baseline, Arch, Windows, and Intel/Apple-Silicon macOS.
- Added comprehensive installation, external-engine, release-security, code-audit, and 170-item roadmap documentation.

### Correctness and safety

- Added one authoritative capability registry exposed by the GUI and `quantumforge --capabilities`; executable detection no longer implies integration support.
- Added deterministic QE preflight for atom/species counts, pseudopotential selection, lattice volume, cutoffs, smearing, SOC/noncollinear consistency, and k-point completeness; invalid jobs are blocked before execution.
- Replaced ambiguous no-op booleans with typed operation results for disabled SSH/SFTP and local scheduler paths while retaining deprecated compatibility wrappers.
- Rebuilt band-gap analysis with rectangular/finite validation, explicit state degeneracy, occupation-aware metallic detection, direct/indirect k-point evidence, and DOS threshold-crossing diagnostics.
- Corrected PDOS integration to use trapezoids on nonuniform energy grids and defensive copies.
- Replaced endpoint diffusivity with least-squares fitting, R², fit window, and standard error; strengthened formation-energy, exciton, CHE, piezoelectric-ratio, and McMillan helper contracts.
- Removed fabricated random/zero/mock outputs from work-function maps, hyperfine, SOC, orbital magnetization, Weyl, STH, volumetric differences, and phonon thermodynamics; those paths now fail explicitly.
- Fixed three `QEInputBinder` bounds checks that used OR instead of AND, a secondary-input type guard, a public `SocksProxyConfig` filename/class mismatch that prevented Java compilation, malformed Modeler FXML nesting, and a Γ-centering path that expected a nonexistent six-element grid.
- Hardened UPF XML parsing against DTD/external-entity access, imposed a 128 MiB limit, and fixed UTF-8 handling.
- Stopped serializing SSH passwords, disabled plaintext proxy-password persistence, and migrates a legacy Materials Project API key to session-only memory.
- Fixed startup failure caused by parsing semantic version `2.0.0` as a Java double.
- Fixed upgrades replacing all user QE paths/settings; migration now preserves user values.
- Stopped automatically persisting nonexistent legacy bundled-QE paths.
- Fixed Γ-centred automatic k-grid offsets.
- Stopped generating invalid pseudo-CPMD, NMR/GIPAW, and XAFS/XSpectra `pw.x` inputs; those incomplete workflows now fail explicitly.
- Stopped fabricating space and magnetic-space groups from lattice metrics.
- Corrected CIF loop/labels, POSCAR species ordering, locale-independent numeric export, element masses, and unsafe guessed QE pseudopotential names/cutoffs.
- Replaced misleading “complete VASP/CASTEP GUI” messages with accurate implementation status.
- Replaced abandoned JSch 0.1.54 with maintained `com.github.mwiede:jsch` and upgraded other declared dependencies.
- Normalized fourteen non-UTF-8 source files and corrected broken/obsolete URLs.

### Known limitations

- SSH/SFTP/scheduler submission contains unresolved TODO/no-op methods and is not production-ready.
- VASP is limited to POSCAR I/O and a disconnected INCAR prototype; CASTEP is not implemented.
- thermo_pw, phonopy, BoltzTraP2 and XCrySDen are not complete end-to-end integrations.
- Advanced physics/catalysis/battery/topology/ML modules include unwired or simplified prototypes and are not validated research features.
- Native Windows/macOS code signing requires owner-provided certificates and protected CI secrets; unsigned artifacts must be labeled accordingly.
- The initial test suite is not yet the required QE multi-version golden-output corpus.
