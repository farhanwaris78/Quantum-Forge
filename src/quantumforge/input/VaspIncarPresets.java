/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input;

import java.util.List;

import quantumforge.input.validation.VaspIncarDeck.Statement;
import quantumforge.input.validation.VaspKpointsDeck;

/**
 * Batch 173 (roadmap #111, VASP input side): INCAR preset bundles for the
 * extension workbench, assembled ONLY from the batch-173 pinned
 * {@code VaspIncarSchema} tier-1 window (vasp.at wiki 6.x tag pages, fetched
 * 2026-07-22). Every emitted VALUE is a pinned option or a documented unit
 * (eV, fs); every recommendation travels as a '#' comment quoting the wiki
 * fact it rests on. Nothing is invented: tags whose pinned default is
 * conditional are pinned down explicitly with the condition quoted, and
 * ENCUT is NEVER generated - the wiki 'strongly recommend(s) specifying the
 * energy cutoff ENCUT always manually' and the default (largest ENMAX of the
 * POTCAR) belongs to VASP's licensed file, referenced only.
 *
 * <p><b>Honest scope:</b> multi-stage recipes (DOS, bands) are emitted as a
 * STAGED TEMPLATE with explicit stage comments - VASP runs ONE INCAR per
 * directory, so the operator splits the stages. quantumforge never executes
 * VASP and never writes these files: the workbench puts the text on the
 * screen for review and copy. The companion meshes come from the batch-173
 * {@link VaspKpointsDeck} factories and render through its canonical
 * writer.</p>
 */
public final class VaspIncarPresets {

    /** Preset keys, workbench order. */
    public static final List<String> KEYS = List.of(
            "SCF", "RELAX", "MD", "DOS_BANDS", "HSE06_BANDS", "DFTPLUSU");

    private VaspIncarPresets() { }

    /** Human-facing label for the workbench combo. */
    public static String labelOf(String key) {
        return switch (key) {
            case "SCF" -> "Self-consistent field (single point)";
            case "RELAX" -> "Ion relaxation (positions only)";
            case "MD" -> "Ab-initio molecular dynamics";
            case "DOS_BANDS" -> "DOS + band structure (3 stages)";
            case "HSE06_BANDS" -> "HSE06 hybrid band structure (2 stages)";
            case "DFTPLUSU" -> "DFT+U (2-species example)";
            default -> throw new IllegalArgumentException(
                    "unknown INCAR preset key: " + key);
        };
    }

    /** Shared prologue every preset carries (the ENCUT burden, verbatim). */
    private static String header(String label) {
        return "# -------------------------------------------------------------\n"
                + "# INCAR preset '" + label + "' - assembled from the vasp.at wiki\n"
                + "# window (VASP 6.x tag pages, fetched 2026-07-22), every value a\n"
                + "# pinned option or documented unit. quantumforge never executes VASP\n"
                + "# and never writes this file for you - review, then copy.\n"
                + "#\n"
                + "# ENCUT is deliberately NOT generated: the default is the largest\n"
                + "# ENMAX of your POTCAR (licensed file), and the wiki 'strongly\n"
                + "# recommend(s) specifying the energy cutoff ENCUT always manually\n"
                + "# in the INCAR file to ensure the same accuracy between\n"
                + "# calculations'. Pin it below before running:\n"
                + "# ENCUT = <your converged cutoff, eV>\n"
                + "# -------------------------------------------------------------\n";
    }

    /** The common electronic-minimization block used by the scf stage. */
    private static String scfCore() {
        return "SYSTEM = preset run\n"
                + "ISTART = 0      # fresh start: pin down explicitly - the wiki's\n"
                + "                # own example does exactly this (WAVECAR auto-detect\n"
                + "                # is the restart trap)\n"
                + "EDIFF  = 1E-6   # eV; the wiki default is 10^{-4} - pinned down for\n"
                + "                # reproducible convergence\n"
                + "NELMIN = 4      # minimum self-consistency steps before the\n"
                + "                # early exit may trigger (wiki default 2)\n"
                + "ISMEAR = 0      # Gaussian smearing (pinned option set\n"
                + "                # -15..0/>0); semi-conductor-friendly\n"
                + "SIGMA  = 0.05   # eV; below the 0.2 eV wiki default, scriptable\n"
                + "PREC   = Accurate\n"
                + "LREAL  = Auto   # the wiki's recommended value for this tag\n";
    }

    /**
     * Assemble the staged INCAR template for one preset key. The text is a
     * REVIEW COPY: stage comments mark where one VASP run ends and the next
     * begins (one INCAR per run directory).
     */
    public static String buildIncar(String key) {
        return switch (key) {
            case "SCF" -> header(labelOf(key)) + "\n" + scfCore();
            case "RELAX" -> header(labelOf(key)) + "\n" + scfCore()
                    + "\n# --- ionic motion (wiki ISIF table row: positions only) ---\n"
                    + "IBRION = 2      # conjugate-gradient ion relaxation\n"
                    + "NSW    = 100    # the run stops after NSW steps even unconverged\n"
                    + "EDIFFG = -0.02  # eV/A: negative => |force| break condition\n"
                    + "ISIF   = 2      # relax IONS only, cell shape/volume fixed\n";
            case "MD" -> header(labelOf(key)) + "\n" + scfCore()
                    + "\n# --- molecular dynamics (IBRION = 0) ---\n"
                    + "# the wiki on POTIM for IBRION=0: 'it HAS to be supplied therefore,\n"
                    + "# otherwise VASP crashes immediately after having started'; and on\n"
                    + "# NSW: 'It HAS to be supplied, otherwise VASP exits immediately\n"
                    + "# after having started'. Both are pinned here.\n"
                    + "IBRION = 0      # molecular dynamics\n"
                    + "POTIM  = 1.0    # fs per MD step (no default exists - required)\n"
                    + "NSW    = 500    # MD steps (required - VASP exits without it)\n"
                    + "# thermostats/ensemble tags (SMASS, ANDERSEN_PROB, ...) are outside\n"
                    + "# the pinned window of this bundle - add them deliberately from the\n"
                    + "# wiki's molecular-dynamics pages.\n";
            case "DOS_BANDS" -> header(labelOf(key)) + "\n"
                    + "# ============ STAGE 1: self-consistent density ============\n"
                    + scfCore()
                    + "\n# ============ STAGE 2: DOS (new directory, keep CHGCAR) ============\n"
                    + "# commented recipe - uncomment into the STAGE-2 directory's INCAR:\n"
                    + "# ISMEAR = -5   # tetrahedron method with Bloechl corrections; the\n"
                    + "                # wiki tip: 'Use a Gamma-centered k-mesh for the\n"
                    + "                # tetrahedron methods'\n"
                    + "# LORBIT = 11   # site-projected DOS, PROCAR/PROOUT written\n"
                    + "# NEDOS  = 2001 # DOS grid points (wiki default 301)\n"
                    + "# EMIN   = -10  # eV window around the eigenvalue range - adapt\n"
                    + "# EMAX   = 10\n"
                    + "\n# ============ STAGE 3: bands (new directory, keep CHGCAR) ============\n"
                    + "# commented recipe - pair with the line-mode KPOINTS companion; the\n"
                    + "# wiki warns the line-mode mesh 'is not suitable for self-consistent\n"
                    + "# calculations' - read the stage-1 CHGCAR and keep it:\n"
                    + "# ICHARG = 11   # read + keep the charge density\n"
                    + "# LMAXMIX = 4   # 'strongly recommended to set LMAXMIX to twice the\n"
                    + "                # maximum l-quantum number' for ICHARG >= 11 (d-electron\n"
                    + "                # example; use 6 for f elements)\n";
            case "HSE06_BANDS" -> header(labelOf(key)) + "\n"
                    + "# ============ STAGE 1: hybrid SCF ============\n"
                    + scfCore()
                    + "LHFCALC = .TRUE.  # hybrid functional\n"
                    + "GGA     = PE      # 'HFSCREEN can be used only when GGA=PE, PS or CA.'\n"
                    + "HFSCREEN = 0.2    # A^-1: this value selects the HSE06 recipe\n"
                    + "AEXX    = 0.25    # exact-exchange fraction (HSE06)\n"
                    + "ALGO    = Damped  # the wiki recommends Damped/All for hybrids and\n"
                    + "                  # warns ALGO=Fast 'is not properly supported (note:\n"
                    + "                  # no warning is printed)'\n"
                    + "\n# ============ STAGE 2: hybrid bands ============\n"
                    + "# uncomment into the STAGE-2 directory's INCAR. The wiki:\n"
                    + "# 'For meta-GGA and hybrid functionals, a regular mesh must always be provided.'\n"
                    + "# Line mode is NOT available here; the hybrid\n"
                    + "# band-structure route is a zero-weight explicit k-point list added\n"
                    + "# to the regular mesh (see the wiki 'Si bandstructure' hybrid\n"
                    + "# example). quantumforge emits no fake list: build the explicit\n"
                    + "# path from YOUR structure's high-symmetry points.\n"
                    + "# ICHARG = 11\n"
                    + "# LMAXMIX = 4\n";
            case "DFTPLUSU" -> header(labelOf(key)) + "\n" + scfCore()
                    + "\n# --- DFT+U block (2-species example: one U-corrected, one not) ---\n"
                    + "LDAU     = .TRUE.\n"
                    + "LDAUTYPE = 2      # the wiki default (Liechtenstein rotationally\n"
                    + "                  # invariant); type 3 = Cococcioni linear response is\n"
                    + "                  # documented in the page text\n"
                    + "LDAUL    = 2 -1   # per species: d-channel U on species 1, none on 2\n"
                    + "LDAUU    = 4.0 0.0  # eV, per species - EXAMPLE VALUE, not a law\n"
                    + "LDAUJ    = 0.5 0.0  # eV, per species - EXAMPLE VALUE, not a law\n"
                    + "LDAUPRINT = 1     # write the onsite occupancy matrices\n"
                    + "# the wiki: DFT+U 'require, in many cases, an increase of LMAXMIX\n"
                    + "# ... to 4 for d-electrons (or 6 for f-elements)':\n"
                    + "LMAXMIX  = 4\n";
            default -> throw new IllegalArgumentException(
                    "unknown INCAR preset key: " + key);
        };
    }

    /**
     * Companion k-point meshes for the preset (one or two decks, in run
     * order). Line mode appears ONLY where the wiki allows it (never for the
     * hybrid preset - a regular mesh is mandatory there and the honest note
     * lives inside {@link #buildIncar(String)}).
     */
    public static List<VaspKpointsDeck> companionMeshes(String key) {
        return switch (key) {
            case "SCF", "RELAX", "MD", "DFTPLUSU" -> List.of(
                    VaspKpointsDeck.gammaMesh(
                            "Gamma-centered 4x4x4 mesh - adapt to YOUR cell",
                            4, 4, 4));
            case "DOS_BANDS" -> List.of(
                    VaspKpointsDeck.gammaMesh(
                            "DOS stage mesh (Gamma-centered for ISMEAR=-5)",
                            8, 8, 8),
                    // fcc G-X-W-G path (the wiki template, ASCII 'G' for
                    // the Gamma label); 40 points per segment
                    VaspKpointsDeck.bandPath(
                            "bands stage: fcc Gamma-X-W-Gamma (wiki template)",
                            40, false,
                            List.of(new VaspKpointsDeck.Vertex(0, 0, 0, "G"),
                                    new VaspKpointsDeck.Vertex(0.5, 0.5, 0, "X"),
                                    new VaspKpointsDeck.Vertex(0.5, 0.5, 0, "X"),
                                    new VaspKpointsDeck.Vertex(0.5, 0.75, 0.25, "W"),
                                    new VaspKpointsDeck.Vertex(0.5, 0.75, 0.25, "W"),
                                    new VaspKpointsDeck.Vertex(0, 0, 0, "G"))));
            case "HSE06_BANDS" -> List.of(
                    VaspKpointsDeck.gammaMesh(
                            "hybrid SCF mesh (regular mesh is mandatory for"
                                    + " hybrids, wiki)",
                            4, 4, 4));
            default -> throw new IllegalArgumentException(
                    "unknown INCAR preset key: " + key);
        };
    }

    /**
     * The companion meshes rendered as canonical KPOINTS text blocks (with a
     * one-line caption each), ready for the workbench's review pane.
     */
    public static String companionText(String key) {
        List<VaspKpointsDeck> meshes = companionMeshes(key);
        StringBuilder out = new StringBuilder();
        int index = 1;
        for (VaspKpointsDeck mesh : meshes) {
            out.append("# --- KPOINTS companion ").append(index++)
                    .append(" (").append(mesh.getMode()).append(") ---\n");
            out.append(mesh.toKpointsText());
            out.append('\n');
        }
        if ("HSE06_BANDS".equals(key)) {
            out.append("# no second mesh: hybrid bands need an explicit\n"
                    + "# zero-weight list appended to the SCF mesh - the wiki's\n"
                    + "# own route; line mode is prohibited for meta-GGA/hybrid\n"
                    + "# functionals ('a regular mesh must always be provided').\n");
        }
        out.append("# KSPACING alternative: if no KPOINTS file is present, VASP\n"
                + "# falls back to KSPACING (wiki default 0.5 A^-1, KGAMMA=T) -\n"
                + "# pin KSPACING in the INCAR only if you mean that trade.\n");
        return out.toString();
    }

    /**
     * Fast self-check used by the extension workbench and the tests: every
     * statement value of the preset's own tier-1 tags must parse - a preset
     * that trips the TYPE layer of the audit is a bug in this class, not in
     * the user's deck.
     */
    public static String selfAuditReport(String key) {
        String text = buildIncar(key);
        quantumforge.input.validation.VaspIncarDeck deck
                = quantumforge.input.validation.VaspIncarDeck.parse(text)
                        .getValue().orElseThrow();
        StringBuilder out = new StringBuilder();
        for (Statement statement : deck.getStatements()) {
            out.append(statement.getTag()).append(" = ")
                    .append(statement.getRawValue()).append('\n');
        }
        return out.toString();
    }
}
