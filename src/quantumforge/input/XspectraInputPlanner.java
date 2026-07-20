/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * xspectra.x (core-edge XANES) input DRAFT planner (Roadmap #65 first
 * slice): echoes the prefix/outdir save-context of the live pw.x input (the
 * xspectra.x executable finds the SCF wavefunction through the same
 * outdir/prefix) and emits a deliberately NON-RUNNABLE draft in which every
 * experimental-physics choice - dipole-vs-quadrupole calculation, absorbing
 * edge, energy window, core-hole pseudopotential, occupation smoothing - is
 * a REQUIRED-EDIT placeholder. The load-bearing honesty points:
 *
 * <ul>
 *   <li>a valid XANES run needs a CONVERGED SCF of the (often supercell)
 *       core-excited system with a GIPAW core-hole pseudopotential for the
 *       absorbing atom - that cannot be verified offline and is stated, not
 *       checked;</li>
 *   <li>dipole/quadrupole and the edge are EXPERIMENTAL choices; no default
 *       is fabricated;</li>
 *   <li>xnepoint is a typical initial grid whose adequacy is on the user -
 *       convergence of the spectrum is the remaining #65 depth together with
 *       the spectrum parser and version-matched INPUT_XSPECTRA schema.</li>
 * </ul>
 */
public final class XspectraInputPlanner {

    /** Curated calculation options named in the draft. */
    public static final String CALCULATION_OPTIONS = "'xanes_dipole' or 'xanes_quadrupole'";
    /** Curated absorbing-edge examples named in the draft. */
    public static final String EDGE_EXAMPLES = "'K', 'L1', 'L2', 'L3', 'L23' (see INPUT_XSPECTRA)";

    /** Immutable context: what the live input honestly tells us, verbatim. */
    public static final class XspectraContext {
        private final String prefix;
        private final String outdir;

        XspectraContext(String prefix, String outdir) {
            this.prefix = prefix;
            this.outdir = outdir;
        }

        public String getPrefix() { return this.prefix; }
        public String getOutdir() { return this.outdir; }
    }

    private XspectraInputPlanner() { }

    /**
     * Extracts the save context. Code: XSPEC_INPUT (no resolvable input).
     */
    public static OperationResult<XspectraContext> extractContext(QEInput input) {
        if (input == null) {
            return OperationResult.failed("XSPEC_INPUT",
                    "The project has no resolvable current input to draft from.", null);
        }
        String prefix = "pwscf";
        String outdir = "./";
        QENamelist control = input.getNamelist(QEInput.NAMELIST_CONTROL);
        if (control != null) {
            QEValue[] values = control.listQEValues();
            for (QEValue value : values) {
                if (value == null || value.getName() == null) {
                    continue;
                }
                String name = value.getName();
                if ("prefix".equalsIgnoreCase(name)) {
                    String text = verbatim(value);
                    if (text != null && !text.isBlank()) {
                        prefix = text.trim();
                    }
                } else if ("outdir".equalsIgnoreCase(name)) {
                    String text = verbatim(value);
                    if (text != null && !text.isBlank()) {
                        outdir = text.trim();
                    }
                }
            }
        }
        return OperationResult.success("XSPEC_CONTEXT_OK", "Context extracted.",
                new XspectraContext(prefix, outdir));
    }

    /** Number of REQUIRED-EDIT placeholders in a draft. */
    public static int countRequiredEdits(String draft) {
        int count = 0;
        int idx = draft == null ? -1 : draft.indexOf("REQUIRED-EDIT");
        while (idx >= 0) {
            count += 1;
            idx = draft.indexOf("REQUIRED-EDIT", idx + 1);
        }
        return count;
    }

    /**
     * Renders the deliberately NON-RUNNABLE draft: the experimental choices
     * are REQUIRED-EDIT; only the safe grid default and a verbosity default
     * are pre-filled (both labelled typical/default, not recommendations).
     */
    public static String draft(XspectraContext context) {
        StringBuilder text = new StringBuilder();
        text.append("! xspectra.x core-edge XANES input DRAFT - QuantumForge Roadmap #65\n");
        text.append("! REQUIRED-EDIT review guard: resolve EVERY 'REQUIRED-EDIT' line\n");
        text.append("! before running xspectra.x. As drafted this input is NOT runnable\n");
        text.append("! and that is deliberate: no experimental physics is fabricated.\n");
        text.append("&input_xspectra\n");
        text.append("!  calculation = ...   ! REQUIRED-EDIT: ").append(CALCULATION_OPTIONS)
                .append("\n!                      - matches your experimental geometry\n");
        text.append("!  edge        = ...   ! REQUIRED-EDIT: absorbing edge, e.g. ")
                .append(EDGE_EXAMPLES).append('\n');
        text.append("   verbosity    = 0,   ! xspectra.x default echoed (0 = silent)\n");
        text.append("/\n");
        text.append("&plot\n");
        text.append("   xnepoint     = 200,  ! typical INITIAL grid only; converging the\n");
        text.append("!                      spectrum against xnepoint is on you\n");
        text.append("!  xemin        = ...  ! REQUIRED-EDIT: energy window lower bound (eV,\n");
        text.append("!                      relative to the edge reference)\n");
        text.append("!  xemax        = ...  ! REQUIRED-EDIT: energy window upper bound (eV)\n");
        text.append("/\n");
        text.append("&pseudos\n");
        text.append("!  filecore     = ...  ! REQUIRED-EDIT: the core-wavefunction file from\n");
        text.append("!                      the GIPAW core-hole pseudopotential of the ABSORBING\n");
        text.append("!                      atom (one absorbing species per run)\n");
        text.append("/\n");
        text.append("&cut_occ\n");
        text.append("!  cut_desmooth = ...  ! REQUIRED-EDIT: occupation smoothing near the Fermi\n");
        text.append("!                      level for the pseudo core-hole run (typical 0.1-0.5)\n");
        text.append("/\n");
        text.append("! Save context echoed from the live input (xspectra.x locates the SCF\n");
        text.append("! wavefunction through the same outdir/prefix):\n");
        text.append("!   prefix = '").append(context.getPrefix()).append("'\n");
        text.append("!   outdir = '").append(context.getOutdir()).append("'\n");
        text.append("! Prerequisite stated, not checked offline: a CONVERGED SCF of the\n");
        text.append("! core-excited system (often a supercell) with a GIPAW core-hole\n");
        text.append("! pseudopotential for the absorbing atom; consult INPUT_XSPECTRA\n");
        text.append("! matching your QE version; the spectrum-reviewer side of #65\n");
        text.append("! (parse sigma/omega columns) remains open work.\n");
        return text.toString();
    }

    private static String verbatim(QEValue value) {
        String raw;
        try {
            raw = value.getCharacterValue();
        } catch (RuntimeException ex) {
            raw = null;
        }
        if (raw == null || raw.isBlank()) {
            raw = value.toString();
        }
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                        || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
