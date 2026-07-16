# Scientific engines and auxiliary software

QuantumForge is an orchestration/editor layer, not a density-functional-theory engine. Install and validate each external program independently before connecting it to the GUI. Paths and examples below are conservative templates; always follow the exact upstream release documentation for the versions selected.

## 1. Quantum ESPRESSO (primary engine)

QuantumForge's current input compatibility target is QE 7.5. The authoritative QE installation guide covers prerequisites, CMake/make builds, libraries, platforms, and tests: <https://www.quantum-espresso.org/Doc/user_guide/node7.html>. The authoritative `pw.x` input reference is <https://www.quantum-espresso.org/Doc/INPUT_PW.html>.

### Fast distribution package (functional test, often not latest)

```bash
# Ubuntu
sudo apt update
sudo apt install quantum-espresso
pw.x -v

# Arch (inspect AUR PKGBUILD and sources before building)
yay -S quantum-espresso
pw.x -v
```

Repository/AUR versions and build options vary. Do not report reproducibility as “QE 7.5” unless `pw.x -v` confirms it.

### Reproducible source build on Ubuntu

```bash
sudo apt update
sudo apt install build-essential gfortran cmake git \
  libopenmpi-dev openmpi-bin libfftw3-dev libblas-dev liblapack-dev \
  libscalapack-openmpi-dev

git clone --branch qe-7.5 --depth 1 https://gitlab.com/QEF/q-e.git qe-7.5
cd qe-7.5
cmake -S . -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$HOME/.local/qe-7.5"
cmake --build build --parallel "$(nproc)"
cmake --install build
export PATH="$HOME/.local/qe-7.5/bin:$PATH"
pw.x -v
```

If the tag/build commands differ, use the release tarball and instructions linked by QE. Preserve `CMakeCache.txt`, compiler/MPI versions, and the git commit in research metadata.

### Verify engine and MPI

```bash
command -v pw.x ph.x dos.x projwfc.x bands.x neb.x pp.x
pw.x -v
mpirun --version
ldd "$(command -v pw.x)" | grep -Ei 'mpi|fftw|blas|lapack'
```

Run QE's upstream test suite. Then run a small serial SCF and the same input with two MPI ranks; compare total energies within output precision. A GUI launch is not an engine validation.

### Pseudopotentials

Use a curated family such as SSSP from Materials Cloud: <https://www.materialscloud.org/discover/sssp/table>. Keep the complete family/version, UPF files, checksums, suggested wavefunction/charge-density cutoffs, and XC functional consistent. Do not mix PBE, PBEsol, LDA, scalar-relativistic, and fully relativistic potentials casually. SOC needs fully relativistic potentials and matching noncollinear settings.

## 2. `thermo_pw`

`thermo_pw` is tightly coupled to a compatible QE source tree; it is not an independent executable that can safely be dropped beside arbitrary QE binaries. The upstream repository documents this sequence: <https://github.com/dalcorso/thermo_pw>.

```bash
# Start from a clean, compatible QE source tree.
cd /path/to/q-e
# Obtain the thermo_pw release explicitly declared compatible with that QE release.
git clone --branch VERSION https://github.com/dalcorso/thermo_pw.git thermo_pw
cd thermo_pw
make join_qe
cd ..
./configure                    # or the documented CMake route
make -j"$(nproc)" thermo_pw
./bin/thermo_pw.x --help 2>&1 | head
```

Important upstream behavior: `make join_qe` replaces/integrates QE build files. To uninstall it cleanly, run `make leave_qe` inside `thermo_pw` **before** deleting the directory. Keep a clean QE checkout or use a separate worktree/container.

QuantumForge 2.0.0 only detects `thermo_pw.x` and contains a narrow elastic-matrix parser. It does not yet generate/validate the complete `thermo_control`/`ph_control` workflow. Treat manual `thermo_pw` execution as the production route.

Scientific validation should cover:

- elastic tensors against symmetry constraints and positive-definiteness/Born criteria;
- strain amplitude and SCF/k-point/cutoff convergence;
- phonon q meshes and acoustic sum rule;
- QHA volume grid, equation of state, and temperature range;
- units (Ry/bohr³, kbar, GPa) and Voigt-index convention;
- comparison to an upstream example before a new material.

## 3. phonopy, phono3py, spglib, and seekpath

Use an isolated environment rather than `sudo pip`. Current phonopy documentation recommends conda-forge/miniforge or a controlled pip build: <https://phonopy.github.io/phonopy/install.html>.

```bash
conda create -n qf-phonons -c conda-forge \
  python phonopy phono3py spglib seekpath numpy scipy h5py pyyaml matplotlib
conda activate qf-phonons
phonopy --version
python -c "import phonopy, spglib, seekpath; print(phonopy.__version__, spglib.__version__)"
```

For QE finite displacements, the robust workflow is:

1. fully relax the primitive structure to tighter force/stress thresholds than the target phonon accuracy;
2. standardize/check symmetry with spglib and retain the transformation matrix;
3. converge supercell dimensions so force constants decay before periodic images;
4. generate displaced supercells with phonopy's QE interface;
5. run identical, tightly converged `pw.x` force calculations for every displacement;
6. collect forces and inspect translational/rotational invariance;
7. apply an explicitly documented acoustic sum rule and non-analytical correction where appropriate;
8. converge q-path/q-mesh, displacement amplitude, k mesh, cutoff, and SCF force noise;
9. retain `phonopy.yaml`, force sets/constants, Born charges/dielectric tensor, and exact tool versions.

An imaginary mode is not automatically “a plotting error”: test convergence, supercell, electronic occupations, symmetry constraints, and displaced relaxation. A tiny negative acoustic Γ frequency can be numerical; a robust branch instability away from Γ may represent real structural instability.

QuantumForge currently has an internal phonon result prototype but no end-to-end phonopy execution adapter. Implementing one is a high-priority roadmap item.

## 4. BoltzTraP2

BoltzTraP2 performs band interpolation and semiclassical transport (usually constant relaxation-time/rigid-band approximations). Upstream usage and dependencies are described at <https://gitlab.com/sousaw/BoltzTraP2> and its documentation.

```bash
conda create -n qf-transport -c conda-forge python boltztrap2 ase spglib numpy scipy matplotlib netcdf4
conda activate qf-transport
btp2 --help
```

Transport demands a much denser and symmetry-consistent electronic k mesh than a band-path plot. Validate:

- converged NSCF eigenvalues over the full Brillouin zone;
- sufficient unoccupied bands over the intended temperature/chemical-potential window;
- correct cell, symmetry, spin degeneracy, electron count, and energy units;
- interpolation quality by comparing reconstructed and DFT eigenvalues;
- k-mesh convergence of Seebeck and conductivity tensors;
- clear reporting of `σ/τ`, `κₑ/τ`, Hall quantities, and assumed/externally calculated relaxation time;
- no claim of total `ZT` without a defensible lattice thermal conductivity and scattering model.

QuantumForge currently only detects `btp2`; it does not convert QE data or parse BoltzTraP2 outputs.

## 5. XCrySDen (spelling)

The software is **XCrySDen**, not “xcrygen.” It is useful for XSF structures, QE input previews, charge-density/isosurface inspection, k paths, and Fermi surfaces. Obtain it from <https://www.xcrysden.org/> or a trusted distribution package.

```bash
# Ubuntu availability depends on release/repository
sudo apt install xcrysden
xcrysden --version || xcrysden

# Arch
sudo pacman -S xcrysden   # if available in current repositories; otherwise inspect AUR
```

For remote OpenGL/X11, start with software rendering and a tiny file. Large isosurfaces are better rendered on the machine holding the data or transferred locally. QuantumForge detects `xcrysden` but has not yet wired a reliable external-launch/export action.

## 6. VASP

VASP is separately licensed software. Obtain binaries, pseudopotentials, and documentation only through authorized institutional/licensed channels: <https://www.vasp.at/>. Never package VASP or POTCAR content in a QuantumForge release, public repository, bug report, or test fixture.

QuantumForge currently provides:

- a POSCAR/CONTCAR reader;
- POSCAR export;
- a small disconnected INCAR-form prototype;
- executable detection for `vasp_std`.

It does **not** currently provide validated INCAR/KPOINTS/POTCAR assembly, VASP execution, scheduler integration, OUTCAR/vasprun.xml parsing, restart management, or provenance controls. The UI now says so explicitly.

A real VASP adapter should use `vasprun.xml` as the primary machine-readable result, preserve all INCAR tags, never read/copy POTCAR contents into logs, and test against VASP's documented examples under a license-compliant private CI environment.

## 7. CASTEP

CASTEP is also separately licensed/distributed in many environments. QuantumForge contains only a menu placeholder and executable name. There is no `.cell`/`.param` model, run adapter, or parser. Do not describe current code as CASTEP support.

A production adapter needs schema-aware `.cell`/`.param` round trips, unit handling, pseudopotential/license separation, MPI invocation profiles, `.castep` parsing, checkpoint/restart management, and private licensed reference tests.

## 8. LAMMPS

A Java class generates a small LAMMPS script skeleton, but there is no data-file writer, force-field file manager, units compatibility layer, process runner, or dump/log parser. Real integration should:

- require an explicit LAMMPS unit style and track every conversion;
- validate atom styles, topology, periodicity, pair coefficients and external potential files;
- query `lmp -h` for compiled packages rather than assuming GPU/REAXFF/ML plugins;
- write data via a tested structure topology model;
- parse thermo output and dump trajectories with timestep/box metadata;
- prevent an ML potential trained for one chemistry/cutoff from being applied outside its domain;
- test energy/force agreement against direct LAMMPS runs.

## 9. Deep-learning interatomic potentials

The current `GNNFroceField` (also misspelled) only checks whether Python packages import. It does not load models, transfer structures, run energies/forces, check units, or quantify uncertainty.

A safe architecture is a versioned Python sidecar protocol:

```text
Java GUI -> validated JSON/ASE structure request -> isolated Python worker
         <- model identity, training-domain metadata, energy, forces, stress,
            units, device, precision, warnings, uncertainty, runtime versions
```

Each backend (MACE, CHGNet, MatGL/M3GNet, SevenNet, MatterSim, FAIR-Chem) should implement one common contract and a conformance test. Pin environments with lock files/containers; verify model-file hashes; disallow arbitrary pickle by default; expose CPU/GPU precision; compare forces to finite differences; benchmark against held-out DFT data; and implement an out-of-domain/ensemble uncertainty gate before active learning. Universal potentials are useful pre-relaxers and screeners, not automatic replacements for converged DFT.

## 10. Jupyter

The prototype starts `jupyter-lab --no-browser` but does not capture the token URL, authenticate an API, stop the process, reserve ports safely, or sandbox notebooks. A future integration should use Jupyter Server APIs, random loopback ports, token authentication, explicit environment selection, process lifecycle management, and no network exposure by default.

## 11. Remote HPC and schedulers

Current SSH transfer/submission contains TODOs. Until replaced, submit with your site's documented SSH/SLURM/PBS workflow. A robust implementation must:

- use maintained SSH cryptography and strict known-host verification;
- prefer agent/key authentication; never persist plaintext passwords;
- quote paths/arguments without constructing shell strings;
- upload to a unique remote staging directory;
- template scheduler directives with a typed model;
- parse job IDs and query state (`squeue`/`qstat`) with bounded retries;
- checkpoint downloads atomically and verify size/hash;
- distinguish login and compute nodes;
- never run heavy QE calculations on a login node;
- support cancellation without broad `rm -rf` operations.

## 12. Reproducibility record for every calculation

At minimum archive:

- application, engine, plugin, compiler, MPI, BLAS/LAPACK/FFTW, Python package and OS versions;
- complete input files and job scripts;
- pseudopotential/model filenames, licenses where applicable, and SHA-256;
- structure provenance and all transformations;
- numerical convergence studies and acceptance thresholds;
- stdout/stderr and exit status for every stage;
- raw outputs needed to reproduce plots;
- parser version and plot configuration;
- random seeds and model hashes for stochastic/ML workflows;
- citations required by QE, pseudopotentials, algorithms, and auxiliary tools.
