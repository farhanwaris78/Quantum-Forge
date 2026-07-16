/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative capability matrix.
 *
 * <p>Executable detection is deliberately not used as evidence of integration:
 * finding {@code phonopy} or {@code vasp_std} on PATH does not mean that input,
 * execution, parsing, and scientific regression tests exist.</p>
 */
public final class CapabilityRegistry {
    public static final String STRUCTURE_IO = "structure-io";
    public static final String QE_INPUT = "qe-input";
    public static final String QE_LOCAL = "qe-local";
    public static final String SSH_HPC = "ssh-hpc";
    public static final String THERMO_PW = "thermo-pw";
    public static final String PHONOPY = "phonopy";
    public static final String BOLTZTRAP2 = "boltztrap2";
    public static final String XCRYSDEN = "xcrysden";
    public static final String VASP = "vasp";
    public static final String CASTEP = "castep";
    public static final String SYMMETRY = "symmetry";
    public static final String LAMMPS = "lammps";
    public static final String ML_POTENTIALS = "ml-potentials";
    public static final String ADVANCED_SCIENCE = "advanced-science";
    public static final String PACKAGING = "packaging";

    private static final Map<String, Capability> CAPABILITIES = createCapabilities();

    private CapabilityRegistry() { }

    private static Map<String, Capability> createCapabilities() {
        Map<String, Capability> values = new LinkedHashMap<>();
        register(values, STRUCTURE_IO, "Structure I/O", CapabilityStatus.SUPPORTED,
                "CIF/XYZ/XSF/CUBE/POSCAR readers and reviewed CIF/XYZ/POSCAR export are present.",
                "Expand independent ASE/pymatgen round-trip fixtures.");
        register(values, QE_INPUT, "Quantum ESPRESSO input editor", CapabilityStatus.PARTIAL,
                "Core pw.x SCF, relaxation, MD, bands, and DOS models/editors are present.",
                "Complete version-aware schema and multi-version QE golden tests.");
        register(values, QE_LOCAL, "Local Quantum ESPRESSO execution", CapabilityStatus.PARTIAL,
                "ProcessBuilder is driven with dry-run preflight, typed command DAG stage skipping, XML-first data-file-schema parsing when present, run manifests, restart assessment, workflow export, process-tree cancellation, live log tailing, and SCF/geometry analysis.",
                "Add real multi-version QE golden integration tests against installed engines.");
        register(values, SSH_HPC, "SSH and HPC schedulers", CapabilityStatus.PARTIAL,
                "Strict known_hosts store, JSch transport, host-key acceptance UI helper, selective result sync with checksum cache, remote job monitor with backoff, safe cancel-by-id, SLURM/PBS/SGE adapters, site profiles, durable job-queue store, and job-state records exist; live multi-cluster validation is still required.",
                "Validate against two real clusters and polish fingerprint dialog UX.");
        register(values, THERMO_PW, "thermo_pw", CapabilityStatus.EXPERIMENTAL,
                "Executable detection and a narrow elastic-matrix parser exist.",
                "Add QE compatibility checks, controls, execution DAG, units, and reference tests.");
        register(values, PHONOPY, "phonopy/phono3py", CapabilityStatus.UNAVAILABLE,
                "Only detection/data sketches exist; there is no force-displacement workflow.",
                "Implement isolated Python protocol, displacement jobs, force collection, and YAML parsing.");
        register(values, BOLTZTRAP2, "BoltzTraP2", CapabilityStatus.UNAVAILABLE,
                "The btp2 command can be detected but no conversion, execution, or parser exists.",
                "Implement dense-band conversion, interpolation diagnostics, tensors, and unit tests.");
        register(values, XCRYSDEN, "XCrySDen", CapabilityStatus.PARTIAL,
                "Safe temp XSF export and argument-array launch are implemented; requires a local xcrysden binary and display.",
                "Add remote/X11 lifecycle tests and density/grid XSF export.");
        register(values, VASP, "VASP", CapabilityStatus.EXPERIMENTAL,
                "POSCAR I/O and a disconnected INCAR form exist; no complete licensed workflow exists.",
                "Add private licensed schema/execution/vasprun.xml tests without distributing POTCAR data.");
        register(values, CASTEP, "CASTEP", CapabilityStatus.UNAVAILABLE,
                "No .cell/.param model, execution adapter, or results parser exists.",
                "Build a separately licensed plugin and private reference suite.");
        register(values, SYMMETRY, "Symmetry", CapabilityStatus.PARTIAL,
                "Bravais helpers remain; spglib/seekpath sidecar protocol v2 supports dataset, primitive/conventional standardization, and k-path when python packages are installed; otherwise results stay undetermined/fail-closed.",
                "Ship a locked sidecar environment and COD/ICSD-permitted round-trip fixtures.");
        register(values, LAMMPS, "LAMMPS", CapabilityStatus.EXPERIMENTAL,
                "A small script skeleton exists without a data writer, runner, or parser.",
                "Implement a unit-aware plugin and force/energy conformance fixtures.");
        register(values, ML_POTENTIALS, "Machine-learning potentials", CapabilityStatus.EXPERIMENTAL,
                "Python package probes exist without model inference, units, uncertainty, or domain checks.",
                "Build an isolated, locked, backend-neutral worker protocol and conformance suite.");
        register(values, ADVANCED_SCIENCE, "Advanced science prototypes", CapabilityStatus.UNAVAILABLE,
                "Topology, catalysis, battery, spectroscopy, and superconductivity sketches are not validated workflows.",
                "Implement as reviewed plugins backed by real engines and benchmark data.");
        register(values, PACKAGING, "Cross-platform packaging", CapabilityStatus.PARTIAL,
                "Versioned portable/native installers, update/uninstall, SBOM, Ubuntu 20.04 baseline, Arch PKGBUILD, Windows/macOS scripts, and the quantumforge CLI exist.",
                "Activate authorized cross-platform CI runners and configure Windows/macOS code signing.");
        return Collections.unmodifiableMap(values);
    }

    private static void register(Map<String, Capability> values, String id, String name,
                                 CapabilityStatus status, String summary, String required) {
        Capability capability = new Capability(id, name, status, summary, required);
        if (values.put(id, capability) != null) {
            throw new IllegalStateException("Duplicate capability id: " + id);
        }
    }

    public static Capability get(String id) {
        return CAPABILITIES.get(id);
    }

    public static List<Capability> list() {
        return Collections.unmodifiableList(new ArrayList<>(CAPABILITIES.values()));
    }

    public static String createReport() {
        StringBuilder report = new StringBuilder("QuantumForge capability report\n");
        report.append("==============================\n");
        for (Capability capability : CAPABILITIES.values()) {
            report.append(capability.toDisplayString()).append('\n');
        }
        report.append("\nSUPPORTED means an end-user path exists; it does not replace convergence or independent validation.\n");
        return report.toString();
    }
}
