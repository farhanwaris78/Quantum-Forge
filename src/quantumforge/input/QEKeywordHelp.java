/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Offline, version-pinned pw.x keyword reference subset (Roadmap #129): a curated
 * table of the most load-bearing namelist keywords with vetted one-line summaries
 * that carry units, plus the upstream documentation URL. This is explicitly NOT
 * the full generated schema - Roadmap #22 generates complete, version-aware
 * keyword metadata from the QE 7.2-7.5 input docs; until then, lookups outside
 * this curated set fail closed and point upstream instead of improvising.
 */
public final class QEKeywordHelp {

    public static final String INPUT_PW_URL =
            "https://www.quantum-espresso.org/Doc/INPUT_PW.html";

    /** One curated keyword entry: namelist section plus a vetted summary with units. */
    public static final class KeywordEntry {
        private final String name;
        private final String section;
        private final String summary;

        KeywordEntry(String name, String section, String summary) {
            this.name = name;
            this.section = section;
            this.summary = summary;
        }

        public String getName() { return this.name; }
        public String getSection() { return this.section; }
        public String getSummary() { return this.summary; }
    }

    private static final Map<String, KeywordEntry> TABLE = new HashMap<>();

    private static void put(String name, String section, String summary) {
        TABLE.put(name, new KeywordEntry(name, section, summary));
    }

    static {
        put("calculation", QEInput.NAMELIST_CONTROL,
                "Calculation type: scf, nscf, bands, relax, md, vc-relax or vc-md; "
                        + "every later keyword's meaning depends on this.");
        put("restart_mode", QEInput.NAMELIST_CONTROL,
                "'from_scratch' or 'restart'; restart reuses outdir data and must not "
                        + "be mixed with an incompatible prefix/outdir.");
        put("prefix", QEInput.NAMELIST_CONTROL,
                "String prepended to files written in outdir; must be identical across "
                        + "linked steps (scf -> ph.x/pp.x) or the pipeline breaks.");
        put("outdir", QEInput.NAMELIST_CONTROL,
                "Directory for temporary and restart files; defaults to "
                        + "ESPRESSO_TMPDIR or the current directory.");
        put("pseudo_dir", QEInput.NAMELIST_CONTROL,
                "Directory holding the pseudopotential files; defaults to "
                        + "ESPRESSO_PSEUDO or the home directory.");
        put("wf_collect", QEInput.NAMELIST_CONTROL,
                ".true. collects the Kohn-Sham states into outdir at run end; required "
                        + "for portable restarts across machines.");
        put("nstep", QEInput.NAMELIST_CONTROL,
                "Maximum ionic steps for relax/md runs (default 1 for scf, 50 for "
                        + "relax/md).");
        put("tprnfor", QEInput.NAMELIST_CONTROL,
                ".true. prints forces after each SCF step; cheap and almost always "
                        + "wanted.");
        put("tstress", QEInput.NAMELIST_CONTROL,
                ".true. computes the stress tensor each step; needed for cell "
                        + "relaxation and elastic work.");
        put("max_seconds", QEInput.NAMELIST_CONTROL,
                "Wall-clock budget in seconds; pw.x stops cleanly and writes restart "
                        + "data when reached.");
        put("etot_conv_thr", QEInput.NAMELIST_CONTROL,
                "Total-energy convergence threshold between ionic steps, in Ry; the "
                        + "ions stop when the energy change falls below it.");
        put("forc_conv_thr", QEInput.NAMELIST_CONTROL,
                "Force convergence threshold in Ry/bohr; ions stop when every force "
                        + "component is below it.");
        put("ibrav", QEInput.NAMELIST_SYSTEM,
                "Bravais-lattice index selecting the cell shape; ibrav=0 means free "
                        + "lattice via the CELL_PARAMETERS card.");
        put("nat", QEInput.NAMELIST_SYSTEM, "Number of atoms in the unit cell.");
        put("ntyp", QEInput.NAMELIST_SYSTEM, "Number of distinct atomic types.");
        put("ecutwfc", QEInput.NAMELIST_SYSTEM,
                "Kinetic-energy cutoff for wavefunctions, in Ry; convergence with "
                        + "respect to it is the user's responsibility.");
        put("ecutrho", QEInput.NAMELIST_SYSTEM,
                "Charge-density and potential cutoff, in Ry; default 4*ecutwfc but "
                        + "norm-conserving/US/PAW pseudopotentials typically need 8-12x.");
        put("occupations", QEInput.NAMELIST_SYSTEM,
                "'fixed', 'smearing', 'tetrahedra' or 'from_input'; smearing is for "
                        + "metals, tetrahedra for BZ-integrated properties of "
                        + "insulators.");
        put("smearing", QEInput.NAMELIST_SYSTEM,
                "Smearing function ('gaussian', 'mp', 'mv', 'fd') used when "
                        + "occupations='smearing'.");
        put("degauss", QEInput.NAMELIST_SYSTEM,
                "Smearing width in Ry; the energy must be reconverged against it "
                        + "(the reported energy includes an entropy correction).");
        put("nspin", QEInput.NAMELIST_SYSTEM,
                "1 = unpolarized, 2 = collinear spin-polarized, 4 = non-collinear "
                        + "with spin-orbit coupling work.");
        put("nbnd", QEInput.NAMELIST_SYSTEM,
                "Number of computed bands; default adds 20% empty bands for "
                        + "insulators - metals need more for occupations.");
        put("starting_magnetization", QEInput.NAMELIST_SYSTEM,
                "Starting spin polarization per atomic type, -1..1; needed to find "
                        + "antiferromagnetic or symmetry-broken solutions.");
        put("tot_magnetization", QEInput.NAMELIST_SYSTEM,
                "Constraint on total magnetization in Bohr magnetons per cell; a "
                        + "constraint, not an initial guess.");
        put("lda_plus_u", QEInput.NAMELIST_SYSTEM,
                ".true. enables DFT+U; Hubbard_U must then be set per atom type "
                        + "(see hp.x for first-principles values).");
        put("assume_isolated", QEInput.NAMELIST_SYSTEM,
                "Effective-screening-medium correction for aperiodic directions: "
                        + "'2D' slabs, '1D' wires, '0D' molecules, 'mt' "
                        + "Makov-Payne.");
        put("conv_thr", QEInput.NAMELIST_ELECTRONS,
                "SCF self-consistency threshold on the total energy, in Ry; typical "
                        + "production values 1e-8 to 1e-10, tighter for phonons.");
        put("mixing_beta", QEInput.NAMELIST_ELECTRONS,
                "Charge-density mixing factor 0 < beta <= 1; lowering it helps "
                        + "difficult (magnetic, metallic, large-cell) convergence.");
        put("diagonalization", QEInput.NAMELIST_ELECTRONS,
                "Eigensolver: 'david' (default), 'cg', 'ppcg', 'paro'; cg is slower "
                        + "but more robust for exotic cases.");
        put("electron_maxstep", QEInput.NAMELIST_ELECTRONS,
                "Maximum SCF iterations before pw.x gives up; raising it hides "
                        + "convergence problems rather than fixing them.");
        put("ion_dynamics", QEInput.NAMELIST_IONS,
                "Ionic optimizer for relax/md: 'bfgs' (default relax), 'damp', "
                        + "'verlet', 'beeman' for md.");
        put("cell_dynamics", QEInput.NAMELIST_CELL,
                "Cell optimizer for vc-relax ('bfgs', 'sd', 'damp-pr'); only "
                        + "meaningful with calculation='vc-relax'.");
        put("press_conv_thr", QEInput.NAMELIST_CELL,
                "Pressure convergence threshold in kbar for vc-relax cell moves.");
    }

    private QEKeywordHelp() { }

    /** Case-insensitive lookup; empty when the keyword is outside the curated set. */
    public static Optional<KeywordEntry> lookup(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        KeywordEntry entry = TABLE.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry);
    }

    /** All curated entries sorted by keyword name. */
    public static List<KeywordEntry> entries() {
        List<KeywordEntry> all = new ArrayList<>(TABLE.values());
        all.sort((a, b) -> a.getName().compareTo(b.getName()));
        return List.copyOf(all);
    }

    /** Names of the curated keywords, sorted - used in fail-closed messages. */
    public static List<String> coveredNames() {
        List<String> names = new ArrayList<>(TABLE.keySet());
        Collections.sort(names);
        return List.copyOf(names);
    }
}
