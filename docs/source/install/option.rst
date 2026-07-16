Scientific engines (installed separately)
==========================================

Quantum ESPRESSO and all auxiliary engines are external to QuantumForge. Follow
the upstream engine's version-specific installation and test procedure, then set
its executable directory in QuantumForge.

The maintained engine guide covers Quantum ESPRESSO, thermo_pw, phonopy,
BoltzTraP2, XCrySDen, VASP/CASTEP status, LAMMPS, ML potentials, and HPC:
`docs/SCIENTIFIC_SOFTWARE_GUIDE.md <../../SCIENTIFIC_SOFTWARE_GUIDE.md>`_.

Do not assume that executable detection means an end-to-end integration is
implemented. See ``docs/CODE_AUDIT.md`` for the current capability matrix.
