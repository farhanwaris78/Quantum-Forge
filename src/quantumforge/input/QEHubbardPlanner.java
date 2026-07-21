/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.schema.QENamelistSchema;
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
     *
     * <p>Version-agnostic legacy entry: identical output to the pre-batch-154
     * planner (no version grammar is named, no version-gated keyword is
     * offered). New callers should use
     * {@link #draft(HubbardContext, int, int, int, String, boolean)} so the
     * deck is typed against the mined grammar window of the QE they run.</p>
     */
    public static OperationResult<String> draft(HubbardContext context, int nq1,
            int nq2, int nq3) {
        return draftCore(context, nq1, nq2, nq3, null, false, false);
    }

    /**
     * Version-typed draft (QE-integration roadmap R3): the deck is probed
     * against the mined QE 7.2-7.6 hp.x grammar of the REQUESTED version
     * (blank resolves to the newest mined window and says so in the deck).
     * Codes: HP_INPUT (no context), HP_VERSION (unsupported label), HP_QGRID
     * (outside bounds), HP_KEYWORD_WINDOW ({@code no_metq0} requested on a
     * version whose grammar has no such keyword - it exists only 7.5-7.6, and
     * hp.x aborts on unknown &INPUTHP keywords, so a refusal is the honest
     * answer).
     */
    public static OperationResult<String> draft(HubbardContext context, int nq1,
            int nq2, int nq3, String version, boolean noMetQ0) {
        return draftCore(context, nq1, nq2, nq3, version, noMetQ0, true);
    }

    private static OperationResult<String> draftCore(HubbardContext context, int nq1,
            int nq2, int nq3, String version, boolean noMetQ0, boolean versionTyped) {
        if (context == null) {
            return OperationResult.failed("HP_INPUT", "No Hubbard context supplied.", null);
        }
        String pinnedVersion = null;
        QEDeckKeywordCatalog catalog = null;
        if (versionTyped) {
            OperationResult<String> resolved = QEDeckKeywordCatalog.resolveVersion(version);
            if (!resolved.isSuccess()) {
                return OperationResult.failed("HP_VERSION", resolved.getMessage(), null);
            }
            pinnedVersion = resolved.getValue().orElseThrow();
            catalog = QEDeckKeywordCatalog.forVersion(QENamelistSchema.Kind.HP, pinnedVersion)
                    .getValue().orElseThrow();
            if (noMetQ0 && !catalog.prompts("no_metq0")) {
                return OperationResult.failed("HP_KEYWORD_WINDOW",
                        "'no_metq0' exists only in QE " + catalog.windowText("no_metq0")
                                + " (mined grammar); the pinned target " + pinnedVersion
                                + " has no such keyword, and hp.x aborts on an unknown "
                                + "&INPUTHP keyword - refusal is the honest answer.", null);
            }
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
        if (noMetQ0 && catalog != null) {
            draft.append(String.format(Locale.ROOT, "   no_metq0 = .true.,%n"));
            draft.append("   ! requested; mined window " + catalog.windowText("no_metq0")
                    + '\n');
        }
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
        if (versionTyped) {
            draft.append("! 4. Target QE grammar: ").append(pinnedVersion);
            if (version == null || version.trim().isEmpty()) {
                draft.append(" (newest mined window; the caller did not pin a version)");
            } else {
                draft.append(" (caller-pinned; mined window)");
            }
            draft.append('\n');
            if ("7.6".equals(pinnedVersion)) {
                draft.append("! 5. QE 7.6 drift, mined: 'last_q' is internal-only (default\n");
                draft.append("!    -1000 instead of 'number of q points') - restart chunks\n");
                draft.append("!    planned from 7.2-7.5 semantics must be re-read first.\n");
            }
        }
        return OperationResult.success("HP_DRAFT",
                "Drafted &INPUTHP for q grid " + nq1 + " " + nq2 + " " + nq3
                        + (versionTyped ? " against the QE " + pinnedVersion + " grammar."
                                : "."),
                draft.toString());
    }

    /**
     * Typed self-audit against the mined grammar (batch-154 guarantee): every
     * keyword this planner emits unconditionally (prefix/outdir/nq1/nq2/nq3)
     * must be promptable in ALL mined versions, and the one version-gated
     * keyword it offers (no_metq0) must be absent from at least one version -
     * otherwise its refusal path would be dead code. Empty = planner and
     * grammar agree.
     */
    public static java.util.List<String> auditStaticEmissions() {
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (String keyword : java.util.List.of("prefix", "outdir", "nq1", "nq2", "nq3")) {
            for (String version : QENamelistSchema.VERSIONS) {
                if (!QEDeckKeywordCatalog.forVersion(QENamelistSchema.Kind.HP, version)
                        .getValue().orElseThrow().prompts(keyword)) {
                    violations.add("hp.x keyword " + keyword
                            + " is emitted unconditionally but absent from QE " + version);
                }
            }
        }
        int carries = 0;
        for (String version : QENamelistSchema.VERSIONS) {
            if (QEDeckKeywordCatalog.forVersion(QENamelistSchema.Kind.HP, version)
                    .getValue().orElseThrow().prompts("no_metq0")) {
                carries++;
            }
        }
        if (carries == QENamelistSchema.VERSIONS.size()) {
            violations.add("no_metq0 is treated as version-gated but exists in every mined "
                    + "version - the HP_KEYWORD_WINDOW refusal path is dead code");
        }
        return java.util.List.copyOf(violations);
    }
}
