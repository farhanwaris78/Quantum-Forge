# QuantumForge 2.0.0

QuantumForge is a JavaFX desktop application for preparing, running, and inspecting selected [Quantum ESPRESSO](https://www.quantum-espresso.org/) workflows. It includes crystal/molecular structure readers and viewers, QE namelist/card editors, local process execution, and parsers/plots for several common outputs.

> **Scientific-status warning:** this repository also contains many disconnected experimental prototypes. A class name or menu label is not evidence of a complete or validated scientific workflow. Read the capability matrix and [code audit](docs/CODE_AUDIT.md) before using results in research.

## Current capability matrix

| Area | Status in 2.0.0 |
|---|---|
| QE `pw.x` SCF, optimization and MD input model/editor | Core implementation; verify generated input against the QE 7.5 input reference |
| QE band/DOS post-processing command chains and plots | Core implementation; regression coverage is being expanded |
| CIF, XYZ, XSF, CUBE and POSCAR reading; CIF/XYZ/POSCAR/QE export | Implemented; round-trip coverage is currently limited |
| Local QE execution | Implemented using `ProcessBuilder` with deterministic structural/cutoff/spin/k-point preflight; real-QE golden tests are still required |
| SSH/HPC execution | **Unavailable/fail-closed**: typed results report that secure transfer/submission is not implemented |
| VASP | POSCAR reader/export and a disconnected INCAR-form prototype only; **not a complete VASP GUI** |
| CASTEP | Executable detection/menu placeholder only; **not implemented** |
| `thermo_pw` | Executable registration plus a narrow elastic-matrix parser; no complete workflow |
| phonopy / BoltzTraP2 / XCrySDen | External-tool detection or documentation only; no end-to-end integration |
| LAMMPS / GNN potentials / Jupyter | Disconnected prototypes, not production integrations |
| Advanced physics, battery, catalysis, topology, superconductivity tools | Unavailable prototypes; known fabricated/random/zero-result paths now throw explicit unsupported errors |
| Symmetry | Bravais-metric helper only; space/magnetic groups remain explicitly undetermined until future spglib integration |
| Band gap / DOS helpers | Occupation-aware k-resolved gaps, threshold-qualified DOS estimates, and nonuniform-grid trapezoidal DOS integration; convergence remains the user's responsibility |

QuantumForge does **not** distribute Quantum ESPRESSO, pseudopotentials, VASP, CASTEP, `thermo_pw`, phonopy, BoltzTraP2, or XCrySDen. Their licenses and installation requirements apply separately.

## Install and launch

The cross-platform command that starts the GUI is:

```text
quantumforge
```

Use that same word in a local terminal **or** in MobaXterm/SSH with X11 forwarding.

Useful non-GUI commands:

```text
quantumforge --version
quantumforge --doctor
quantumforge --capabilities
quantumforge --update
quantumforge --uninstall
```

See the complete, checksum-first tutorial covering Ubuntu 20.04→current, Arch,
Windows 10/11, macOS Intel/Apple Silicon, safe update/uninstall, and remote GUI:

- **[Full multi-platform install tutorial](docs/TUTORIAL_INSTALL.md)**
- **[How to publish the first release](docs/FIRST_RELEASE.md)**
- **[Installation, update, uninstall, and MobaXterm/X11 guide](docs/INSTALLATION.md)**
- **[External scientific software setup](docs/SCIENTIFIC_SOFTWARE_GUIDE.md)**
- **[Release integrity and security model](docs/RELEASE_AND_SECURITY.md)**
- **[Deep code/scientific audit](docs/CODE_AUDIT.md)**
- **[Prioritized future implementation roadmap](docs/FUTURE_ROADMAP.md)**

## Build from source

Requirements: a 64-bit JDK 17+, Maven 3.9+, Git, and platform GUI libraries.

```bash
git clone https://github.com/farhanwaris78/Quantum-Forge.git
cd Quantum-Forge
mvn clean verify
mvn javafx:run
```

`mvn verify` compiles approximately 100,000 lines of Java/FXML/CSS, runs the test suite, resolves maintained dependencies, and creates `target/quantumforge-sbom.json`.

Platform artifacts are built with:

```bash
# Linux/macOS portable archive
packaging/build-portable.sh linux x64

# Linux/macOS self-contained app/native package
packaging/build-native.sh app-image   # or deb, rpm, dmg, pkg
```

```powershell
# Windows portable ZIP and self-contained package
.\packaging\build-portable.ps1 -Machine x64
.\packaging\build-native.ps1 -Type msi
```

The obsolete `bin/build.xml` and `bin/launch4j.xml` are retained only as historical files; they contain old absolute developer paths and are not the supported build.

## Research reproducibility rules

1. Record the QuantumForge version, QE version/commit, pseudopotential filenames and checksums, MPI implementation, compiler/math libraries, input files, and convergence evidence.
2. Converge cutoffs, k/q meshes, smearing, supercell dimensions, vacuum, thresholds, and finite-displacement/strain amplitudes for the property—not only total energy.
3. Inspect generated input against the official engine documentation before launching expensive jobs.
4. Validate a workflow against a reference material and, where possible, a second parser/code.
5. Never interpret a GUI default, heuristic, or prototype formula as a universal physical parameter.

## License

The repository-level terms are in [`LICENSE`](LICENSE) and are proprietary. Portions identify an Apache-2.0-licensed upstream heritage, and bundled/runtime dependencies have their own licenses; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). The older plain-text `README` contained an inconsistent Apache statement and is superseded by this file and `LICENSE`.
