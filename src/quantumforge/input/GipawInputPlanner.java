/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * gipaw.x (GIPAW NMR/EFG) input DRAFT planner (Roadmap #66 first slice):
 * echoes the prefix/outdir save context of the live pw.x input (gipaw.x
 * reads the SCF wavefunction through the same prefix/tmp_dir) and emits a
 * deliberately MINIMAL draft in which the physics convergence choice
 * (q_gipaw, the Green-function smearing) is a REQUIRED-EDIT placeholder and
 * only the documented safe defaults (job='gipaw', verbosity=1) are
 * pre-filled. The load-bearing honesty points:
 *
 * <ul>
 *   <li>NMR shielding REQUIRES GIPAW-capable pseudopotentials for every
 *       species (the UPF must carry the reconstructed GIPAW projectors);
 *       whether your pseudo directory satisfies this is stated, not
 *       checked offline - gipaw.x itself complains clearly at runtime;</li>
 *   <li>a shielding sigma is NOT a chemical shift: the shift needs an
 *       experimental or computed REFERENCE (sigma_ref) - that reference
 *       workflow stays REQUIRED-EDIT and is named openly as #66 depth;</li>
 *   <li>hyperfine/EFG specifics and the tensor parser (shielding/EFG
 *       conventions) remain the remaining #66 completeness work.</li>
 * </ul>
 */
public final class GipawInputPlanner {

    /** Immutable context: what the live input honestly tells us, verbatim. */
    public static final class GipawContext {
        private final String prefix;
        private final String outdir;

        GipawContext(String prefix, String outdir) {
            this.prefix = prefix;
            this.outdir = outdir;
        }

        public String getPrefix() { return this.prefix; }
        public String getOutdir() { return this.outdir; }
    }

    private GipawInputPlanner() { }

    /**
     * Extracts the save context. Code: GIPAW_INPUT (no resolvable input).
     */
    public static OperationResult<GipawContext> extractContext(QEInput input) {
        if (input == null) {
            return OperationResult.failed("GIPAW_INPUT",
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
        return OperationResult.success("GIPAW_CONTEXT_OK", "Context extracted.",
                new GipawContext(prefix, outdir));
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
     * Renders the minimal draft: documented defaults pre-filled, the single
     * convergence-relevant keyword REQUIRED-EDIT, reference/pseudo caveats
     * printed as comments.
     */
    public static String draft(GipawContext context) {
        StringBuilder text = new StringBuilder();
        text.append("! gipaw.x NMR/EFG input DRAFT - QuantumForge Roadmap #66\n");
        text.append("! REQUIRED-EDIT review guard: resolve EVERY 'REQUIRED-EDIT' line\n");
        text.append("! before running gipaw.x.\n");
        text.append("&inputgipaw\n");
        text.append("   job          = 'gipaw',  ! all-quantities driver (shields, EFG,\n");
        text.append("!                          hyperfine); the documented default job\n");
        text.append("   prefix       = '").append(context.getPrefix())
                .append("',  ! save context echoed verbatim\n");
        text.append("   tmp_dir      = '").append(context.getOutdir())
                .append("',  ! save scratch echoed verbatim (must match the SCF outdir)\n");
        text.append("   verbosity    = 1,\n");
        text.append("!  q_gipaw      = ...   ! REQUIRED-EDIT: Green-function smearing (Ry).\n");
        text.append("!                      A default exists (0.77), but for production\n");
        text.append("!                      shielding you CONVERGE it - not guessed here.\n");
        text.append("/\n");
        text.append("! NMR shielding REQUIRES GIPAW-capable pseudopotentials for EVERY\n");
        text.append("! species (reconstructed projectors inside the UPF). Stated, not\n");
        text.append("! checked offline - gipaw.x itself rejects incapable pseudos at\n");
        text.append("! runtime. The matching SCF must exist in the echoed save context.\n");
        text.append("! A shielding sigma is NOT a chemical shift: converting needs an\n");
        text.append("! explicit sigma_ref reference (experimental standard or computed\n");
        text.append("! reference compound) - REQUIRED-EDIT downstream of this draft; the\n");
        text.append("! shielding/EFG tensor parser side of #66 remains open work.\n");
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
