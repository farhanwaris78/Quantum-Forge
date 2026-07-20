/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * turbo_lanczos.x (linear-response TDDFT, Lanczos) input DRAFT planner
 * (Roadmap #64 first slice): echoes the prefix/outdir save context of the
 * live pw.x input (turbo_lanczos.x reads the converged ground-state
 * wavefunction through prefix/outdir) and emits a deliberately NON-RUNNABLE
 * draft of the two-namelist layout ({@code &lr_input} / {@code &lr_control})
 * in which every physics choice is a REQUIRED-EDIT placeholder. The
 * load-bearing honesty points:
 *
 * <ul>
 *   <li>this is LINEAR-RESPONSE TDDFT of the charge susceptibility - it must
 *       never be labelled or sold as real-time (RT) TDDFT propagation
 *       (Roadmap #64 explicitly forbids an empty wizard claiming RT);</li>
 *   <li>num_init (Lanczos steps of the initial run), num_eign (eigenvalues
 *       diagonalized in the T-matrix post-processing), ipol (polarization
 *       direction: 1/2/3 select one axis, 4 computes the three spatial
 *       components) and charge_response (0 = transition-based, 1 = charge
 *       response) are EXPERIMENT- and convergence-defining - they stay
 *       REQUIRED-EDIT, no numeric defaults are fabricated;</li>
 *   <li>the draft only names restart/restart_step as the resubmission
 *       mechanism; iteration convergence is documented-experiment
 *       territory, consulted against the version-matched
 *       INPUT_turbo_lanczos documentation;</li>
 *   <li>a CONVERGED SCF save must exist in the echoed prefix/outdir -
 *       stated, not checkable offline; turbo_lanczos.x fails clearly at
 *       runtime when the save is absent; the post-run spectrum still passes
 *       through turbo_spectrum.x whose output the existing TDDFT_SPECTRUM
 *       analysis already parses.</li>
 * </ul>
 */
public final class TurboLanczosInputPlanner {

    /** Immutable context: what the live input honestly tells us, verbatim. */
    public static final class LrContext {
        private final String prefix;
        private final String outdir;

        LrContext(String prefix, String outdir) {
            this.prefix = prefix;
            this.outdir = outdir;
        }

        public String getPrefix() { return this.prefix; }
        public String getOutdir() { return this.outdir; }
    }

    private TurboLanczosInputPlanner() { }

    /**
     * Extracts the save context. Code: TDDFPT_INPUT (no resolvable input).
     */
    public static OperationResult<LrContext> extractContext(QEInput input) {
        if (input == null) {
            return OperationResult.failed("TDDFPT_INPUT",
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
        return OperationResult.success("TDDFPT_CONTEXT_OK", "Context extracted.",
                new LrContext(prefix, outdir));
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
     * Renders the draft: save context pre-filled verbatim, every physics
     * keyword REQUIRED-EDIT, prerequisites documented as comments.
     */
    public static String draft(LrContext context) {
        StringBuilder text = new StringBuilder();
        text.append("! turbo_lanczos.x linear-response TDDFT input DRAFT - QuantumForge\n");
        text.append("! Roadmap #64. This is LINEAR-RESPONSE (charge-susceptibility\n");
        text.append("! Lanczos recursion) TDDFT - NOT real-time propagation; never\n");
        text.append("! label it RT-TDDFT. Resolve EVERY 'REQUIRED-EDIT' line before\n");
        text.append("! running.\n");
        text.append("&lr_input\n");
        text.append("   prefix   = '").append(context.getPrefix())
                .append("',  ! save context echoed verbatim\n");
        text.append("   outdir   = '").append(context.getOutdir())
                .append("',  ! save scratch echoed verbatim (must match the SCF outdir)\n");
        text.append("!  num_init  = ...   ! REQUIRED-EDIT: Lanczos steps in the initial run\n");
        text.append("!                    (spectrum convergence knob - no honest default)\n");
        text.append("!  num_eign  = ...   ! REQUIRED-EDIT: eigenvalues diagonalized when\n");
        text.append("!                    post-processing the Lanczos T-matrix\n");
        text.append("!  restart   = .false.  ! resubmission mechanism for continued\n");
        text.append("!                    iterations pairs with restart_step; documented,\n");
        text.append("!                    left at the QE family default here\n");
        text.append("/\n");
        text.append("&lr_control\n");
        text.append("!  ipol            = ...   ! REQUIRED-EDIT: polarization - 1/2/3 picks\n");
        text.append("!                         one Cartesian axis; 4 computes the three\n");
        text.append("!                         spatial components (three Lanczos runs)\n");
        text.append("!  charge_response  = ...   ! REQUIRED-EDIT: 0 = transition-based\n");
        text.append("!                         response, 1 = charge response; physics choice,\n");
        text.append("!                         not a detail\n");
        text.append("/\n");
        text.append("! Prereqs stated, not checkable offline: a CONVERGED pw.x SCF save at\n");
        text.append("! the echoed prefix/outdir (turbo_lanczos.x reads that wavefunction and\n");
        text.append("! fails clearly when it is absent). Iteration/restart convergence is\n");
        text.append("! documented-experiment territory - consult the version-matched\n");
        text.append("! INPUT_turbo_lanczos documentation. Post-processing passes through\n");
        text.append("! turbo_spectrum.x; its spectrum output is already parsed by the\n");
        text.append("! TDDFT_SPECTRUM analysis kind (#64 spectrum side). The explicit\n");
        text.append("! turbo_lanczos/turbo_spectrum adapters and version-matched schemas\n");
        text.append("! remain the #64 depth.\n");
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
