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
 * ph.x input DRAFT planner (QE-integration roadmap R3): drafts an
 * {@code &INPUTPH} namelist from the live pw.x input (prefix/outdir are
 * echoed verbatim so ph.x finds the completed SCF run), typed against the
 * mined QE 7.2-7.6 grammar windows via {@link QEDeckKeywordCatalog}.
 *
 * <p>What "typed" means here, stated precisely:</p>
 * <ul>
 *   <li>every keyword the planner emits is probed against the mined ph.x
 *       grammar for the CALLER-PINNED version - requesting a version-gated
 *       keyword outside its window is a typed refusal
 *       ({@code PH_KEYWORD_WINDOW}) that names the mined window
 *       ("lmultipole" exists 7.5-7.6, "skip_upperfan" 7.2-7.4), because an
 *       unknown namelist keyword makes ph.x abort while reading &INPUTPH -
 *       a draft must never hand the user an aborting deck;</li>
 *   <li>an unsupported version label is a typed refusal ({@code
 *       PH_VERSION}); blank means the newest mined window and the header
 *       NAMES the resolved version either way;</li>
 *   <li>the q-grid bounds (1..32) are a PLANNER review bound, not a QE
 *       limit - QE accepts larger integers; the planner refuses the extremes
 *       so stopping there is a deliberate user decision, not a default;</li>
 *   <li>declared defaults shown in comments are the mined grammar texts,
 *       never rephrased prose.</li>
 * </ul>
 *
 * <p>Scope honesty: single-q runs (ldisp=.false.) are NOT drafted - the q
 * point then arrives through ph.x's own input covenant and guessing it here
 * would fabricate the very choice the calculation is about. Grid mode
 * (ldisp=.true.) is the one path this planner takes a position on, and it
 * says so in the draft.</p>
 */
public final class PhInputPlanner {

    /** Planner-side review bound for the q grid - NOT a QE limit. */
    public static final int MIN_Q = 1;

    /** Planner-side review bound for the q grid - NOT a QE limit. */
    public static final int MAX_Q = 32;

    /** Keyword the planner emits without any user toggle, in emit order. */
    private static final List<String> STATIC_KEYWORDS = List.of(
            "prefix", "outdir", "tr2_ph", "ldisp", "nq1", "nq2", "nq3");

    /** Extracted pw.x context the ph.x draft is anchored to. */
    public static final class PhContext {
        private final String prefix;
        private final String outdir; // "(QE default...)" marker when unset verbatim

        PhContext(String prefix, String outdir) {
            this.prefix = prefix;
            this.outdir = outdir;
        }

        public String getPrefix() { return this.prefix; }
        public String getOutdir() { return this.outdir; }

        /** True when outdir stays at the QE default (emitted as a comment). */
        public boolean usesDefaultOutdir() { return this.outdir.startsWith("("); }
    }

    /**
     * Immutable user toggles for the version-gated / default-off keywords.
     * Every wither returns a new instance; the base is all-off, matching the
     * mined .false. defaults so an untouched option changes nothing.
     */
    public static final class PhOptions {
        private final boolean epsil;
        private final boolean lmultipole;
        private final boolean skipUpperfan;

        private PhOptions(boolean epsil, boolean lmultipole, boolean skipUpperfan) {
            this.epsil = epsil;
            this.lmultipole = lmultipole;
            this.skipUpperfan = skipUpperfan;
        }

        /** All toggles off - the mined defaults stand everywhere. */
        public static PhOptions base() {
            return new PhOptions(false, false, false);
        }

        /** Dielectric tensor at q=0 (mined default .false.; all versions). */
        public PhOptions withEpsil(boolean on) {
            return new PhOptions(on, this.lmultipole, this.skipUpperfan);
        }

        /** Multipole terms for finite systems (mined window 7.5-7.6 ONLY). */
        public PhOptions withLmultipole(boolean on) {
            return new PhOptions(this.epsil, on, this.skipUpperfan);
        }

        /** skip_upperfan (mined window 7.2-7.4 ONLY - removed from 7.5 on). */
        public PhOptions withSkipUpperfan(boolean on) {
            return new PhOptions(this.epsil, this.lmultipole, on);
        }

        public boolean isEpsil() { return this.epsil; }
        public boolean isLmultipole() { return this.lmultipole; }
        public boolean isSkipUpperfan() { return this.skipUpperfan; }
    }

    private PhInputPlanner() { }

    /**
     * Extracts prefix/outdir from the live input (&CONTROL). Null input fails
     * with PH_INPUT; unset keywords fall back to the ph.x own defaults, and
     * the draft says so instead of inventing a location.
     */
    public static OperationResult<PhContext> extractContext(QEInput input) {
        if (input == null) {
            return OperationResult.failed("PH_INPUT",
                    "No QE input was supplied; ph.x drafts are derived from an existing "
                            + "pw.x input, never invented.", null);
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
        return OperationResult.success("PH_CONTEXT",
                "ph.x context: prefix='" + prefix + "'"
                        + (outdir.startsWith("(") ? " (outdir left to the QE default)"
                                : ", outdir='" + outdir + "'"),
                new PhContext(prefix, outdir));
    }

    /**
     * Drafts the &INPUTPH namelist for a ldisp q grid typed against the mined
     * grammar of the requested version. Codes: PH_INPUT (no context),
     * PH_VERSION (unsupported label), PH_QGRID (outside review bounds),
     * PH_KEYWORD_WINDOW (version-gated option outside its mined window).
     */
    public static OperationResult<String> draft(PhContext context, String version,
            int nq1, int nq2, int nq3, PhOptions options) {
        if (context == null) {
            return OperationResult.failed("PH_INPUT", "No ph.x context supplied.", null);
        }
        PhOptions toggles = options == null ? PhOptions.base() : options;
        OperationResult<String> resolved = QEDeckKeywordCatalog.resolveVersion(version);
        if (!resolved.isSuccess()) {
            return OperationResult.failed("PH_VERSION", resolved.getMessage(), null);
        }
        String pinnedVersion = resolved.getValue().orElseThrow();
        QEDeckKeywordCatalog catalog = QEDeckKeywordCatalog
                .forVersion(QENamelistSchema.Kind.PH, pinnedVersion).getValue().orElseThrow();
        if (nq1 < MIN_Q || nq1 > MAX_Q || nq2 < MIN_Q || nq2 > MAX_Q
                || nq3 < MIN_Q || nq3 > MAX_Q) {
            return OperationResult.failed("PH_QGRID",
                    String.format(Locale.ROOT,
                            "Each q-mesh entry must sit inside the planner review bound "
                                    + "%d..%d; got %d %d %d. QE itself accepts larger meshes - "
                                    + "outside the bound the grid is a deliberate hand edit, "
                                    + "never a drafted default.",
                            MIN_Q, MAX_Q, nq1, nq2, nq3), null);
        }
        String windowFailure = gatedWindowFailure(catalog, toggles, pinnedVersion);
        if (windowFailure != null) {
            return OperationResult.failed("PH_KEYWORD_WINDOW", windowFailure, null);
        }

        StringBuilder draft = new StringBuilder();
        draft.append("! ph.x phonon input DRAFT - QuantumForge QE integration (roadmap R3)\n");
        draft.append("! Target QE grammar: ").append(pinnedVersion);
        if (version == null || version.trim().isEmpty()) {
            draft.append(" (newest mined window; the caller did not pin a version)");
        } else {
            draft.append(" (caller-pinned; mined window)");
        }
        draft.append('\n');
        draft.append("! Reference grammar: ").append(QENamelistSchema.INPUT_PH_URL).append('\n');
        draft.append("&INPUTPH\n");
        draft.append(String.format(Locale.ROOT,
                "   prefix  = '%s',   ! echoed from the live pw.x input%n", context.getPrefix()));
        if (context.usesDefaultOutdir()) {
            draft.append("   ! outdir intentionally left to the QE default; ph.x must find\n");
            draft.append("   ! the completed scf run: ").append(context.getOutdir()).append('\n');
        } else {
            draft.append(String.format(Locale.ROOT,
                    "   outdir  = '%s',    ! echoed - ph.x needs the scf data here%n",
                    context.getOutdir()));
        }
        draft.append("   tr2_ph  = 1e-12,       ! mined QE default across 7.2-7.6; tighten for\n");
        draft.append("   !                        production, loosening is a DELIBERATE trade\n");
        draft.append("   ldisp   = .true.,      ! mined default is .false.; q-grid mode is the\n");
        draft.append("   !                        deliberate choice of this draft\n");
        draft.append(String.format(Locale.ROOT, "   nq1 = %d,%n   nq2 = %d,%n   nq3 = %d,%n",
                nq1, nq2, nq3));
        if (toggles.isEpsil()) {
            emit(draft, catalog, "epsil", ".true.",
                    "requested; mined default .false. - dielectric/Born at q=0");
        }
        if (toggles.isLmultipole()) {
            emit(draft, catalog, "lmultipole", ".true.",
                    "requested; mined window " + catalog.windowText("lmultipole"));
        }
        if (toggles.isSkipUpperfan()) {
            emit(draft, catalog, "skip_upperfan", ".true.",
                    "requested; mined window " + catalog.windowText("skip_upperfan"));
        }
        draft.append("/\n");
        draft.append("! REVIEW before running ph.x:\n");
        draft.append("! 1. This draft assumes a COMPLETED scf run at the same prefix/outdir;\n");
        draft.append("!    ph.x stops when the wavefunction/charge files are not there.\n");
        draft.append("! 2. The printed q grid is NOT converged by default - converge phonon\n");
        draft.append("!    properties against nq progressively (see PhononGridLadderPlan).\n");
        draft.append("! 3. Single-q runs (ldisp=.false.) are deliberately NOT drafted: the q\n");
        draft.append("!    point then arrives through ph.x's own input covenant and belongs\n");
        draft.append("!    to the user's hand, not to a template.\n");
        if ("7.6".equals(pinnedVersion)) {
            draft.append("! 4. QE 7.6 drift, mined: 'last_q' is internal-only (default -1000),\n");
            draft.append("!    so restart planning that relied on last_q<=nqs in 7.2-7.5 must be\n");
            draft.append("!    re-read against the QE 7.6 INPUT_PH page before trusting it.\n");
        }
        return OperationResult.success("PH_DRAFT",
                "Drafted &INPUTPH for q grid " + nq1 + " " + nq2 + " " + nq3
                        + " against the QE " + pinnedVersion + " grammar.",
                draft.toString());
    }

    /**
     * Typed self-audit of every emission decision against the mined grammar:
     * every STATIC keyword must be promptable in ALL mined versions, and every
     * user-gated keyword must be gated for real (absent from at least one
     * version - otherwise its refusal path would be dead code). An empty
     * result means the planner and the grammar agree; violations are named.
     */
    public static List<String> auditStaticEmissions() {
        List<String> violations = new ArrayList<>();
        for (QENamelistSchema.Kind kind : new QENamelistSchema.Kind[] {
                QENamelistSchema.Kind.PH }) {
            for (String keyword : STATIC_KEYWORDS) {
                for (String version : QENamelistSchema.VERSIONS) {
                    QEDeckKeywordCatalog catalog = QEDeckKeywordCatalog
                            .forVersion(kind, version).getValue().orElseThrow();
                    if (!catalog.prompts(keyword)) {
                        violations.add(kind.getExecutable() + " keyword " + keyword
                                + " is emitted unconditionally but absent from QE " + version);
                    }
                }
            }
        }
        for (String gated : List.of("epsil", "lmultipole", "skip_upperfan")) {
            int carries = 0;
            for (String version : QENamelistSchema.VERSIONS) {
                if (QEDeckKeywordCatalog.forVersion(QENamelistSchema.Kind.PH, version)
                        .getValue().orElseThrow().prompts(gated)) {
                    carries++;
                }
            }
            if ("epsil".equals(gated)) {
                if (carries != QENamelistSchema.VERSIONS.size()) {
                    violations.add("epsil is documented as version-independent (mined 0x1F)"
                            + " but is missing from " + (QENamelistSchema.VERSIONS.size() - carries)
                            + " versions");
                }
            } else if (carries == QENamelistSchema.VERSIONS.size()) {
                violations.add(gated + " is treated as version-gated but exists in every "
                        + "mined version - the refusal path is dead code");
            }
        }
        return List.copyOf(violations);
    }

    /** Emits one gated keyword line after asserting the catalog prompts it. */
    private static void emit(StringBuilder draft, QEDeckKeywordCatalog catalog,
            String keyword, String valueText, String comment) {
        if (!catalog.prompts(keyword)) {
            throw new IllegalStateException("planner defect: emitting " + keyword
                    + " outside its mined window for QE " + catalog.getVersion()
                    + " - gatedWindowFailure should have stopped this");
        }
        draft.append(String.format(Locale.ROOT, "   %-8s = %s,   ! %s%n",
                keyword, valueText, comment));
    }

    /** The typed refusal text for a requested option outside its window. */
    private static String gatedWindowFailure(QEDeckKeywordCatalog catalog,
            PhOptions toggles, String version) {
        if (toggles.isLmultipole() && !catalog.prompts("lmultipole")) {
            return "'lmultipole' exists only in QE " + catalog.windowText("lmultipole")
                    + " (mined grammar); the pinned target " + version
                    + " has no such keyword, and ph.x aborts on an unknown &INPUTPH "
                    + "keyword - refusal is the honest answer.";
        }
        if (toggles.isSkipUpperfan() && !catalog.prompts("skip_upperfan")) {
            return "'skip_upperfan' exists only in QE " + catalog.windowText("skip_upperfan")
                    + " (mined grammar); the pinned target " + version
                    + " has no such keyword, and ph.x aborts on an unknown &INPUTPH "
                    + "keyword - refusal is the honest answer.";
        }
        return null;
    }

    /** QE store keeps raw quotes on character values; deck drafts must not. */
    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                        || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
