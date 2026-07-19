/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * hp.x input draft planner (Roadmap #63 seam): builds the &INPUTHP namelist
 * ONLY from an existing Hubbard context (lda_plus_u enabled and/or hubbard_u
 * entries in &SYSTEM). Without that context the planner fails closed - a
 * placeholder U setup fabricated here would masquerade as physics. The draft
 * keeps prefix/outdir verbatim so hp.x finds the completed scf restart data;
 * a REVIEW comment trail (Fortran '!' comments) is inserted instead of any
 * hidden convenience.
 */
public final class QEHubbardPlanner {

    public static final int MIN_Q = 1;
    public static final int MAX_Q = 16;

    /** Extracted Hubbard context plus the drafted namelist. */
    public static final class HubbardContext {
        private final boolean ldaPlusU;
        private final int hubbardUEntries;
        private final String prefix;
        private final String outdir;
        private final List<String> uValueNames;

        HubbardContext(boolean ldaPlusU, List<String> uValueNames, String prefix,
                String outdir) {
            this.ldaPlusU = ldaPlusU;
            this.uValueNames = List.copyOf(uValueNames);
            this.hubbardUEntries = uValueNames.size();
            this.prefix = prefix;
            this.outdir = outdir;
        }

        public boolean isLdaPlusU() { return this.ldaPlusU; }
        public int getHubbardUEntries() { return this.hubbardUEntries; }
        public List<String> getUValueNames() { return this.uValueNames; }
        public String getPrefix() { return this.prefix; }
        public String getOutdir() { return this.outdir; }
    }

    /** QE store keeps raw quotes on character values; namelist drafts must not. */
    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                        || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private QEHubbardPlanner() { }

    /**
     * Extracts the hubbard context; null input or missing &SYSTEM fails with
     * HP_INPUT, a non-Hubbard input with HP_NO_HUBBARD.
     */
    public static OperationResult<HubbardContext> extractContext(QEInput input) {
        if (input == null) {
            return OperationResult.failed("HP_INPUT",
                    "No QE input was supplied; hp.x drafts are derived from an existing "
                            + "pw.x input, never invented.", null);
        }
        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        if (system == null) {
            return OperationResult.failed("HP_INPUT",
                    "The input has no &SYSTEM namelist to detect a Hubbard setup in.",
                    null);
        }
        boolean ldaPlusU = false;
        List<String> uNames = new ArrayList<>();
        for (QEValue value : system.listQEValues()) {
            String name = value.getName() == null ? ""
                    : value.getName().toLowerCase(Locale.ROOT);
            if (name.equals("lda_plus_u") && value.getLogicalValue()) {
                ldaPlusU = true;
            }
            if (name.startsWith("hubbard_u")) {
                uNames.add(value.getName());
            }
        }
        if (!ldaPlusU && uNames.isEmpty()) {
            return OperationResult.failed("HP_NO_HUBBARD",
                    "The input shows neither lda_plus_u=.true. nor hubbard_u entries; "
                            + "hp.x computes U for an EXISTING Hubbard setup, so no draft "
                            + "is generated - inventing one would fabricate projector "
                            + "choices. Set up DFT+U in pw.x first.", null);
        }
        String prefix = "pwscf";
        String outdir = "(QE default: outdir or ESPRESSO_TMPDIR)";
        QENamelist control = input.getNamelist(QEInput.NAMELIST_CONTROL);
        if (control != null) {
            QEValue prefixValue = control.getValue("prefix");
            if (prefixValue != null && prefixValue.getCharacterValue() != null
                    && !prefixValue.getCharacterValue().isBlank()) {
                prefix = stripQuotes(prefixValue.getCharacterValue().trim());
            }
            QEValue outdirValue = control.getValue("outdir");
            if (outdirValue != null && outdirValue.getCharacterValue() != null
                    && !outdirValue.getCharacterValue().isBlank()) {
                outdir = stripQuotes(outdirValue.getCharacterValue().trim());
            }
        }
        return OperationResult.success("HP_CONTEXT",
                "Hubbard context detected: lda_plus_u=" + ldaPlusU
                        + ", hubbard_u entries=" + uNames.size() + ".",
                new HubbardContext(ldaPlusU, uNames, prefix, outdir));
    }

    /**
     * Drafts the &INPUTHP namelist for a q mesh; every q within
     * [{@value #MIN_Q}, {@value #MAX_Q}]. Codes: HP_QGRID.
     */
    public static OperationResult<String> draft(HubbardContext context, int nq1,
            int nq2, int nq3) {
        if (context == null) {
            return OperationResult.failed("HP_INPUT", "No Hubbard context supplied.", null);
        }
        if (nq1 < MIN_Q || nq1 > MAX_Q || nq2 < MIN_Q || nq2 > MAX_Q
                || nq3 < MIN_Q || nq3 > MAX_Q) {
            return OperationResult.failed("HP_QGRID",
                    String.format(Locale.ROOT,
                            "Each q-mesh entry must be within %d..%d; got %d %d %d.",
                            MIN_Q, MAX_Q, nq1, nq2, nq3), null);
        }
        StringBuilder draft = new StringBuilder();
        draft.append("&INPUTHP\n");
        draft.append(String.format(Locale.ROOT, "   prefix = '%s',%n", context.getPrefix()));
        if (context.getOutdir().startsWith("(")) {
            draft.append("   ! outdir intentionally left to the QE default; hp.x must find\n");
            draft.append("   ! the completed scf run: ").append(context.getOutdir())
                    .append('\n');
        } else {
            draft.append(String.format(Locale.ROOT, "   outdir = '%s',%n",
                    context.getOutdir()));
        }
        draft.append(String.format(Locale.ROOT,
                "   nq1 = %d,%n   nq2 = %d,%n   nq3 = %d,%n", nq1, nq2, nq3));
        draft.append("/\n");
        draft.append("! REVIEW before running hp.x:\n");
        draft.append("! 1. This draft assumes a COMPLETED scf run at the same prefix/outdir"
                + "\n!    with lda_plus_u=.true.; restart data missing there makes hp.x "
                + "fail.\n");
        draft.append("! 2. The printed q grid is NOT converged by default - converge U "
                + "against\n!    nq progressively (Roadmap #63 acceptance criterion).\n");
        draft.append("! 3. U from hp.x is a linear-response property of THIS structure/"
                + "pseudopotential;\n!    for HUBBARD card projector syntax see the "
                + "version-matched INPUT_HP docs\n!    (https://www.quantum-espresso.org/Doc/INPUT_HP.html).\n");
        return OperationResult.success("HP_DRAFT",
                "Drafted &INPUTHP for q grid " + nq1 + " " + nq2 + " " + nq3 + ".",
                draft.toString());
    }
}
