/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * cp.x (Car-Parrinello) input DRAFT planner (Roadmap #67): echoes the
 * prefix/outdir and the pw.x calculation keyword from the live input, flags
 * the classic mistake (calculation='cp' is NOT a valid pw.x calculation -
 * QE's pw.x engine rejects it, the dedicated cp.x executable is the tool), and
 * emits a deliberately NON-RUNNABLE skeleton in which every Car-Parrinello
 * physics choice (fictitious electron mass emass, emass_cutoff, timestep dt,
 * ionic temperature control) is a REQUIRED-EDIT placeholder. Fabricating a
 * "default" CP setup is exactly the failure this roadmap item exists to kill:
 * emass/dt are system- and cutoff-dependent and must be checked for adiabatic
 * drift, not copied from anywhere.
 */
public final class CpInputPlanner {

    /** Immutable context: what the live input already tells us, verbatim. */
    public static final class CpContext {
        private final String prefix;
        private final String outdir;
        private final String calculation; // verbatim or null

        CpContext(String prefix, String outdir, String calculation) {
            this.prefix = prefix;
            this.outdir = outdir;
            this.calculation = calculation;
        }

        public String getPrefix() { return this.prefix; }
        public String getOutdir() { return this.outdir; }
        public String getCalculation() { return this.calculation; }

        /** True when the live input uses the invalid pw.x calculation='cp'. */
        public boolean usesInvalidPwCalculation() {
            return this.calculation != null && this.calculation.equalsIgnoreCase("cp");
        }
    }

    private CpInputPlanner() { }

    /**
     * Extracts the context from the live input. Code: CP_INPUT (no input).
     */
    public static OperationResult<CpContext> extractContext(QEInput input) {
        if (input == null) {
            return OperationResult.failed("CP_INPUT",
                    "The project has no resolvable current input to draft from.", null);
        }
        String prefix = "pwscf";
        String outdir = "./";
        String calculation = null;
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
                } else if ("calculation".equalsIgnoreCase(name)) {
                    calculation = verbatim(value);
                }
            }
        }
        return OperationResult.success("CP_CONTEXT_OK", "Context extracted.",
                new CpContext(prefix, outdir, calculation));
    }

    /**
     * Renders the deliberately NON-RUNNABLE draft: every CP-physics choice is
     * a REQUIRED-EDIT; structural cards are explicitly NOT generated.
     */
    public static String draft(CpContext context) {
        StringBuilder text = new StringBuilder();
        text.append("! cp.x Car-Parrinello input DRAFT - QuantumForge Roadmap #67\n");
        text.append("! REQUIRED-EDIT review guard: resolve EVERY 'REQUIRED-EDIT' line\n");
        text.append("! before running cp.x. As drafted this input is NOT runnable and\n");
        text.append("! that is deliberate: no Car-Parrinello physics is fabricated.\n");
        text.append("&CONTROL\n");
        text.append("   calculation    = 'cp'        ! the cp.x driver keyword\n");
        text.append("   prefix         = '").append(context.getPrefix())
                .append("',  ! echoed from the live input\n");
        text.append("   outdir         = '").append(context.getOutdir())
                .append("',  ! echoed from the live input\n");
        text.append("!  pseudo_dir    = ...          ! REQUIRED-EDIT: your pseudopotential path\n");
        text.append("!  ndr / ndw / isave            ! OPTIONAL: restart write/read controls\n");
        text.append("/\n");
        text.append("&SYSTEM\n");
        text.append("!  REQUIRED-EDIT: copy ibrav+celldm (or CELL_PARAMETERS), nat, ntyp,\n");
        text.append("!  ecutwfc from your CONVERGED pw.x scf/relax setup. This draft does\n");
        text.append("!  not copy physics: the same converged cutoff must be re-validated\n");
        text.append("!  for the CP dynamics you intend (dt/emass tradeoff).\n");
        text.append("/\n");
        text.append("&ELECTRONS\n");
        text.append("   electron_dynamics = 'verlet',\n");
        text.append("!  emass       = ...  ! REQUIRED-EDIT: fictitious mass in a.u.\n");
        text.append("!                     (typical 100-1000; MUST be checked against the\n");
        text.append("!                      band gap / cutoff for adiabaticity - no default)\n");
        text.append("!  emass_cutoff= ...  ! REQUIRED-EDIT: accompanying emass cutoff\n");
        text.append("!  ortho_max                              ! OPTIONAL orthogonality steps\n");
        text.append("/\n");
        text.append("&IONS\n");
        text.append("   ion_dynamics  = 'verlet',\n");
        text.append("!  ion_temperature = 'not_controlled'  ! REQUIRED-EDIT for production:\n");
        text.append("!                     pick tempw or a thermostat (nose/berendsen) and\n");
        text.append("!                     document the drift diagnostic.\n");
        text.append("/\n");
        text.append("&CELL\n");
        text.append("!  only for cell dynamics; delete otherwise\n");
        text.append("/\n");
        text.append("! REQUIRED-EDIT: append ATOMIC_SPECIES / ATOMIC_POSITIONS / K_POINTS\n");
        text.append("! cards from the live input - this planner deliberately does not copy\n");
        text.append("! cards (a stale card is worse than none). Consult INPUT_CP matching\n");
        text.append("! your QE version; trajectory review after the run: CP_TRAJECTORY kind.\n");
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
