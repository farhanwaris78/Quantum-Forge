# NanoLabo vs QuantumForge (updated BURAI) — Complete Feature Analysis

## Source: NanoLabo Official Documentation (https://nanolabo-doc.readthedocs.io/en/latest/)
## NanoLabo Version: 3.1.2 (Sept 2025) — proprietary, based on BURAI
## QuantumForge Version: 2.0.0 — our updated BURAI

---

## FEATURE COMPARISON TABLE

### 1. CALCULATION ENGINES

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| Quantum ESPRESSO pw.x | ✅ Up to QE 7.5 | ✅ QE 7.5 configured | ✓ MATCH |
| Quantum ESPRESSO ph.x | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO neb.x | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO pp.x (PostProcessing) | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO dos.x | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO projwfc.x | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO bands.x | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO pw2ebands.x | ✅ | ✅ | ✓ MATCH |
| Quantum ESPRESSO wannier90.x | ✅ | ✅ | ✓ MATCH |
| **LAMMPS (Classical MD)** | **✅** | ❌ | **MISSING** |
| **ThreeBodyTB (Tight-Binding)** | **✅** | ❌ | **MISSING** |
| **Advance/PHASE (proprietary DFT)** | **✅** | ❌ | **MISSING** |
| **GNN Force Fields (M3GNet, CHGNet, etc.)** | **✅** | ❌ (stub only) | **MISSING** |

### 2. CALCULATION TYPES

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| SCF calculation | ✅ | ✅ | ✓ MATCH |
| Structure optimization (relax) | ✅ | ✅ | ✓ MATCH |
| Variable-cell relaxation (vc-relax) | ✅ | ✅ | ✓ MATCH |
| Molecular dynamics (QE) | ✅ | ✅ | ✓ MATCH |
| Band structure | ✅ | ✅ | ✓ MATCH |
| Density of States (DOS) | ✅ | ✅ | ✓ MATCH |
| Projected DOS (PDOS) | ✅ | ✅ (projwfc.x) | ✓ MATCH |
| NEB method | ✅ | ✅ | ✓ MATCH |
| Phonon (DFPT) | ✅ | ✅ (ph.x) | ✓ MATCH |
| **Phonon Dispersion (bands)** | **✅** | **⚠️ (basic)** | PARTIAL |
| TD-DFT | ✅ | ✅ | ✓ MATCH |
| **XAFS/EELS** | **✅** | ❌ | **MISSING** |
| **ESM method (work function)** | **✅** | ❌ | **MISSING** |
| **CPMD (Car-Parrinello MD)** | **✅** | ❌ | **MISSING** |
| **NMR spectrum** | **✅** | ❌ | **MISSING** |
| **Hybrid functionals** | ✅ | ✅ | ✓ MATCH |
| **vdW correction** | ✅ | ✅ | ✓ MATCH |
| DFT+U | ✅ | ✅ | ✓ MATCH |
| **DFT+U+V (Hubbard V)** | **✅** (QE 7.5) | **⚠️ (partial)** | PARTIAL |

### 3. MODELING & STRUCTURE BUILDING

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| **Supercell** | ✅ | **⚠️ (basic)** | PARTIAL |
| **Non-diagonal supercell** | **✅** | ❌ | **MISSING** |
| Lattice vector editing | ✅ | ✅ | ✓ MATCH |
| Element substitution | ✅ | ✅ | ✓ MATCH |
| Point defects (vacancy) | ✅ | ✅ | ✓ MATCH |
| **Slab model (any orientation)** | **✅** | **⚠️ (basic)** | PARTIAL |
| **Molecule adsorption on surface** | **✅** | ❌ (stub only) | **MISSING** |
| **Interface model (mismatched)** | **✅ (Pro)** | ❌ | **MISSING** |
| **Drawing molecule (JSME editor)** | **✅** | ❌ | **MISSING** |
| **Packing molecules** | **✅** | ❌ | **MISSING** |
| **Polymer modeler** | **✅ (Pro)** | ❌ (stub only) | **MISSING** |
| **Solvent filling** | **✅** | ❌ (stub only) | **MISSING** |
| Crystal lattice conversion (primitive/standard) | ✅ | ❌ | MISSING |
| **Autopilot (ChatGPT model generation)** | **✅** | ❌ | **MISSING** |
| Translation of cell | ✅ | ✅ | ✓ MATCH |

### 4. ATOMIC STRUCTURE VIEWER

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| 3D rotation/zoom/pan | ✅ | ✅ | ✓ MATCH |
| Viewpoint manipulation | ✅ | ✅ | ✓ MATCH |
| Display design customization | ✅ | ✅ (Designer) | ✓ MATCH |
| Change element | ✅ | ✅ | ✓ MATCH |
| Delete atom | ✅ | ✅ | ✓ MATCH |
| Move atom | ✅ | ✅ | ✓ MATCH |
| Put (add) atom | ✅ | ✅ | ✓ MATCH |
| Cut/copy/paste atoms | ✅ | ✅ | ✓ MATCH |
| Multiple atom selection | ✅ | ✅ | ✓ MATCH |
| Rotate atoms | ✅ | ✅ | ✓ MATCH |
| **Detect space group** | **✅ (right-click)** | **⚠️ (basic ibrav)** | **PARTIAL** |
| **Geometric information (bond length/angle)** | **✅** | ❌ | **MISSING** |
| **Export to CIF/XYZ/POSCAR** | **✅** | ❌ (CIF read only) | **MISSING** |
| Undo/Redo | ✅ | ✅ | ✓ MATCH |

### 5. INPUT FILE EDITOR

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| GUI-based parameter editing | ✅ | ✅ | ✓ MATCH |
| Default value buttons | ✅ | ❌ | MISSING |
| Advanced Settings dialog | ✅ | ❌ | MISSING |
| Collapsible sections | ✅ | ❌ | MISSING |
| Visual validation (yellow/red fields) | ✅ | ❌ | MISSING |
| Right-click context menu on fields | ✅ | ❌ | MISSING |
| **Extended slider domain** | **✅** | ❌ | **MISSING** |
| **Built-in text editor** | ✅ | ✅ | ✓ MATCH |
| **Let atoms fixed/mobile** | **✅ (per direction XYZ)** | **⚠️ (basic)** | **PARTIAL** |
| **Pseudopotential selector** | ✅ | ✅ | ✓ MATCH |
| **Band path configuration** | **✅ (visual)** | **⚠️ (list)** | PARTIAL |
| **NEB image management** | **✅ (visual)** | **⚠️ (basic)** | **PARTIAL** |

### 6. QUANTUM ESPRESSO SPECIFIC SETTINGS

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| Pseudopotential settings | ✅ | ✅ | ✓ MATCH |
| Cutoff energy auto-suggest | ✅ | ✅ | ✓ MATCH |
| **Band path through B.Z.** | **✅ (interactive)** | **⚠️ (auto only)** | PARTIAL |
| **ESM method** | **✅** | ❌ | **MISSING** |
| **NEB method (visual image mgmt)** | **✅** | ❌ | **MISSING** |
| **XAFS (core-hole)** | **✅** | ❌ | **MISSING** |
| **CPMD (Car-Parrinello)** | **✅** | ❌ | **MISSING** |
| **NMR spectrum** | **✅** | ❌ | **MISSING** |
| **Phonon Dispersion** | **✅** | ❌ | **MISSING** |
| **Γ-Trick option** | **✅ (v3.1)** | ❌ | **MISSING** |
| **Automatic band gap calculation** | **✅ (v3.1)** | ❌ | **MISSING** |
| 3D charge density visualization | ✅ | ❌ | MISSING |
| 3D potential visualization | ✅ | ❌ | MISSING |
| 3D spin polarization | ✅ | ❌ | MISSING |
| **3D wave function visualization** | **✅ (v3.0)** | ❌ | **MISSING** |

### 7. MATERIALS DATABASE

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| Materials Project search | ✅ | ✅ | ✓ MATCH |
| **PubChem search** | **✅** | **✅ (new)** | ✓ MATCH (just added) |
| **SMILES search** | **✅** | ❌ | **MISSING** |
| **Substance name search** | **✅** | ❌ | **MISSING** |
| **Primitive/Conventional cell toggle** | **✅** | ❌ | **MISSING** |
| **API Key configuration** | **✅ (v3.1)** | ❌ | **MISSING** |
| **Molecule builder (JSME integration)** | **✅** | ❌ (stub only) | **MISSING** |

### 8. JOB EXECUTION & SCHEDULING

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| Local execution | ✅ | ✅ | ✓ MATCH |
| SSH remote execution | ✅ | ✅ | ✓ MATCH |
| PBS/Torque support | ✅ | ✅ (new) | ✓ MATCH (just added) |
| **SLURM support** | **✅** | **✅ (new)** | ✓ MATCH (just added) |
| **PJM (Fugaku) support** | **✅** | **✅ (new)** | ✓ MATCH (just added) |
| SGE support | ✅ | ✅ (new) | ✓ MATCH (just added) |
| **Job Manager (local, raw/PBS/SLURM/PJM)** | **✅** | ❌ | **MISSING** |
| **GPU configuration** | **✅** | ❌ | **MISSING** |
| **Auto-transfer executable to server** | **✅** | ❌ | **MISSING** |
| **Queue selection** | **✅** | ❌ (default queue) | **MISSING** |
| Concurrent job management | ✅ | ✅ | ✓ MATCH |
| Job status monitoring | ✅ | ✅ | ✓ MATCH |

### 9. RESULTS VISUALIZATION

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| 2D plots (energy, forces) | ✅ | ✅ | ✓ MATCH |
| Band structure plot | ✅ | ✅ | ✓ MATCH |
| DOS/PDOS plot | ✅ | ✅ | ✓ MATCH |
| **PDOS Calculator** | **✅** | ❌ | **MISSING** |
| **3D charge density (isosurface)** | **✅** | ❌ | **MISSING** |
| **3D potential (isosurface)** | **✅** | ❌ | **MISSING** |
| **3D spin polarization (isosurface)** | **✅** | ❌ | **MISSING** |
| **3D wave function (isosurface)** | **✅** | ❌ | **MISSING** |
| **Difference density (current - reference)** | **✅** | ❌ | **MISSING** |
| **Cloud rendering** | **✅** | ❌ | **MISSING** |
| Movie of MD trajectory | ✅ | ✅ | ✓ MATCH |
| **Phonon modes (animated arrows)** | **✅** | ❌ | **MISSING** |
| **Automatic band gap display** | **✅ (v3.1)** | ❌ | **MISSING** |
| **Screen-shot export** | **✅** | ❌ | **MISSING** |
| **CSV data export** | **✅** | ❌ | **MISSING** |
| Text log viewer | ✅ | ✅ | ✓ MATCH |
| **Remote file continuous fetching** | **✅** | ❌ | **MISSING** |
| **Download All Files from Server** | **✅** | ❌ | **MISSING** |
| **Delete All Files on Server** | **✅** | ❌ | **MISSING** |
| **Update atomic config after optimization** | **✅** | ❌ | **MISSING** |

### 10. THERMO_PW / THERMODYNAMICS

| Feature | NanoLabo | QuantumForge (Plugin) | Status |
|---------|----------|----------------------|--------|
| **thermo_pw integration** | **✅ (plugin-like)** | **✅ (stub)** | PARTIAL |
| **Elastic constants** | **✅** | ❌ | **MISSING** |
| **Grüneisen parameters** | **✅** | ❌ | **MISSING** |
| **Thermal expansion** | **✅** | ❌ | **MISSING** |
| **Heat capacity** | **✅** | ❌ | **MISSING** |
| **BoltzTraP2 (transport)** | **✅ (external)** | **✅ (stub)** | PARTIAL |

### 11. AI / NEURAL NETWORK FEATURES

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| **ChatGPT Chatbot** | **✅** | ❌ | **MISSING** |
| **Autopilot (AI model building)** | **✅ (v3.0)** | ❌ | **MISSING** |
| **M3GNet GNN force field** | **✅** | ❌ | **MISSING** |
| **CHGNet GNN force field** | **✅** | ❌ | **MISSING** |
| **MACE GNN force field** | **✅ (v3.1)** | ❌ | **MISSING** |
| **SevenNet GNN force field** | **✅ (v2.9.2)** | ❌ | **MISSING** |
| **ORB GNN force field** | **✅ (v3.1)** | ❌ | **MISSING** |
| **MatterSim GNN force field** | **✅ (v3.1)** | ❌ | **MISSING** |
| **FAIR-Chem (eqV2) GNN** | **✅ (v3.1)** | ❌ | **MISSING** |
| **Open Catalyst Project** | **✅ (legacy)** | ❌ | **MISSING** |
| **Advance/NeuralMD (proprietary)** | **✅** | ❌ | **MISSING** |
| **SLHMC (self-learning HMC)** | **✅** | ❌ | **MISSING** |

### 12. INFRASTRUCTURE & PLATFORM

| Feature | NanoLabo | QuantumForge | Status |
|---------|----------|-------------|--------|
| Java Runtime | **Liberica JRE 17** | **Java 8+** | OUTDATED |
| **Java 17+ support** | **✅** | ❌ (Java 8 only) | **MISSING** |
| Built-in web browser | ✅ (JxBrowser 8.5) | ✅ (JavaFX WebView) | ✓ MATCH |
| **Jupyter Interface** | **✅** | ❌ | **MISSING** |
| **Matlantis collaboration** | **✅** | ❌ | **MISSING** |
| **Python API** | **✅** | ❌ | **MISSING** |
| **SOCKS proxy for SSH** | **✅ (v2.9.3)** | ❌ | **MISSING** |
| **PAC file proxy** | **✅ (v2.9.3)** | ❌ | **MISSING** |

---

## SUMMARY

### Features Already in QuantumForge (from our update):
✅ QE 7.5 full support
✅ All QE binaries (pw.x, ph.x, dos.x, bands.x, neb.x, pp.x, etc.)
✅ PBS/SLURM/PJM/SGE job scheduler support
✅ PubChem API integration
✅ Plugin architecture (stubs for thermo_pw, phonopy, BoltzTraP2)
✅ Space group detector (basic ibrav)
✅ Molecule builder (basic stub)
✅ Fixed space group bug (ibrav -3, -13 + two-pass detection)

### TOP MISSING FEATURES (Priority for implementation):

**HIGH PRIORITY:**
1. **ESM method** (work function calculation) — QE 7.5 feature
2. **Export atomic config** (CIF/XYZ/POSCAR export) — essential for interoperability
3. **Update atomic config after optimization** — critical workflow step
4. **Band gap auto-detection from DOS/Band** — v3.1 NanoLabo feature
5. **Phonon Dispersion visualization** — interactive phonon band plots
6. **Phonon modes animation** — animated arrows on atoms
7. **Geometric information** (bond length/angle/dihedral measurement)
8. **3D charge density/potential/wavefunction visualization** (isosurface/cloud)

**MEDIUM PRIORITY:**
9. **Default value buttons** and **visual validation** (yellow/red fields)
10. **Non-diagonal supercell expansion**
11. **Molecule adsorption on surface**
12. **Crystal lattice conversion** (primitive/standard cell)
13. **PDOS Calculator** (interactive orbital selection)
14. **Γ-Trick option** for band calculations
15. **Slab model with any orientation** (full implementation)
16. **CSV/Screenshot export** for plots

**LOWER PRIORITY (larger scope):**
17. **GNN force field interface** (M3GNet, CHGNet, etc.)
18. **LAMMPS integration**
19. **Autopilot/AI features**
20. **Job Manager (local PBS/SLURM)**
21. **Jupyter/Python API integration**
22. **Java 17+ migration**

---

*Analysis based on NanoLabo v3.1.2 documentation (latest) vs QuantumForge v2.0.0*
