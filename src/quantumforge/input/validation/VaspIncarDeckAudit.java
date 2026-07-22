/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import quantumforge.input.schema.VaspIncarSchema;
import quantumforge.input.schema.VaspIncarSchema.Entry;
import quantumforge.input.schema.VaspIncarSchema.TagType;
import quantumforge.operation.OperationResult;

/**
 * Batch 173 (roadmap #111, VASP input side): the INCAR grammar audit over
 * the batch-173 pinned {@link VaspIncarSchema} window (vasp.at wiki, MediaWiki
 * API wikitext fetched 2026-07-22). Severities mirror VASP's OWN documented
 * behavior, never invented severity:
 *
 * <ul>
 *   <li><b>statements VASP cannot interpret</b> are emitted as WARNING
 *       ({@code VASP_UNKNOWN_TAG}) with the load-bearing truth: VASP does
 *       NOT abort on an unknown tag - it silently ignores it, so a typo
 *       passes unnoticed (the wiki's own warning shape for mistyped setup -
 *       see INCAR page 'the settings in the INCAR file are the main source
 *       of errors and false results');</li>
 *   <li><b>names recognized on the pinned tier-2 window only</b> are INFO
 *       ({@code VASP_TIER2_TAG}) - value grammar not audited;</li>
 *   <li><b>tier-1 type / option violations</b> are ERROR
 *       ({@code VASP_TYPE_MISMATCH}, {@code VASP_OPTION_REJECTED}) - VASP
 *       would misparse or misread them at runtime;</li>
 *   <li><b>duplicates</b> are WARNING ({@code VASP_DUPLICATE_TAG}): the wiki
 *       window does NOT pin duplicate-tag semantics, so the audit pins the
 *       review burden, not a 'last wins' claim;</li>
 *   <li><b>mixed-structure*consistency rules</b> mined verbatim from the
 *       wiki pages (LHFCALC+ALGO=Fast 'not properly supported (note: no
 *       warning is printed)', NPAR-precedence over NCORE, LDIPOL requires
 *       IDIPOL, ICHARG&gt;=11 strongly recommends LMAXMIX 2/4/6, LDAU/LMAXMIX
 *       4-6 guidance, IBRION=0 without POTIM 'has to be supplied therefore,
 *       otherwise VASP crashes immediately', IBRION=0 without NSW exits
 *       immediately, HFSCREEN needs GGA=PE/PS/CA, METAGGA strongly recommends
 *       LASPH=.TRUE., tetrahedron ISMEAR wants a Gamma-centered mesh,
 *       ENCUT-manual advice, ISTART auto-restart trap); crash-class facts
 *       are ERROR, advisory-class are WARNING/INFO.</li>
 * </ul>
 *
 * <p>The audit never runs VASP, never touches a POTCAR (licensed file:
 * referenced only), and never edits the deck - it reads text and states
 * findings.</p>
 */
public final class VaspIncarDeckAudit {

    public static final String CODE_UNKNOWN = "VASP_UNKNOWN_TAG";
    public static final String CODE_TIER2 = "VASP_TIER2_TAG";
    public static final String CODE_TYPE = "VASP_TYPE_MISMATCH";
    public static final String CODE_OPTION = "VASP_OPTION_REJECTED";
    public static final String CODE_ARRAY = "VASP_ARRAY_MALFORMED";
    public static final String CODE_DUPLICATE = "VASP_DUPLICATE_TAG";
    public static final String CODE_CONTINUATION = "VASP_BACKSLASH_BLANK";
    public static final String CODE_QUOTES = "VASP_QUOTES_UNTERMINATED";
    public static final String CODE_LHFCALC_FAST = "VASP_LHFCALC_FAST_ALGO";
    public static final String CODE_NPAR_NCORE = "VASP_NPAR_OVERRIDES_NCORE";
    public static final String CODE_LDIPOL_IDIPOL = "VASP_LDIPOL_NEEDS_IDIPOL";
    public static final String CODE_ICHARG_LMAXMIX = "VASP_ICHARG11_LMAXMIX";
    public static final String CODE_LDAU_LMAXMIX = "VASP_LDAU_LMAXMIX";
    public static final String CODE_POTIM_MD = "VASP_POTIM_REQUIRED_FOR_MD";
    public static final String CODE_NSW_MD = "VASP_NSW_REQUIRED_FOR_MD";
    public static final String CODE_HFSCREEN_GGA = "VASP_HFSCREEN_GGA_FAMILY";
    public static final String CODE_META_LASPH = "VASP_METAGGA_LASPH";
    public static final String CODE_TETRA_GAMMA = "VASP_TETRA_GAMMA_MESH";
    public static final String CODE_ENCUT_MANUAL = "VASP_ENCUT_MANUAL_ADVICE";
    public static final String CODE_ISTART_AUTO = "VASP_ISTART_AUTO_TRAP";
    public static final String CODE_NUPDOWN_MAGMOM = "VASP_NUPDOWN_MAGMOM";
    public static final String CODE_SYNTAX_NOTE = "VASP_SYNTAX_NOTE";

    private VaspIncarDeckAudit() { }

    /**
     * Audit INCAR text; findings in deterministic order (per-statement rules
     * first - in file order - then the cross-statement consistency rules).
     * A parse-level refusal becomes ONE error finding, never a crash.
     */
    public static List<ValidationIssue> auditDeckText(String text) {
        List<ValidationIssue> issues = new ArrayList<>();
        OperationResult<VaspIncarDeck> parsed = VaspIncarDeck.parse(text);
        if (!parsed.isSuccess() || parsed.getValue().isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                    "VASP_INCAR_PARSE",
                    "deck refused by the INCAR grammar: " + parsed.getCode()
                            + ": " + parsed.getMessage(),
                    "https://www.vasp.at/wiki/index.php/INCAR"));
            return issues;
        }
        VaspIncarDeck deck = parsed.getValue().get();
        auditEntries(deck, issues);
        auditConsistency(deck, issues);
        return issues;
    }

    // --------------------------- per-statement rules -------------------------

    private static void auditEntries(VaspIncarDeck deck,
            List<ValidationIssue> issues) {
        for (VaspIncarDeck.Statement statement : deck.getStatements()) {
            applyEntryRules(statement, issues);
        }
        for (String tag : deck.distinctTags()) {
            if (deck.hasDuplicates(tag)) {
                List<VaspIncarDeck.Statement> all = deck.occurrencesOf(tag);
                StringBuilder lines = new StringBuilder();
                for (VaspIncarDeck.Statement each : all) {
                    lines.append(lines.length() == 0 ? "" : ", ")
                            .append(each.getLine());
                }
                issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                        CODE_DUPLICATE,
                        "tag " + tag + " is written " + all.size()
                                + " times (lines " + lines + "); the pinned wiki"
                                + " window does NOT state duplicate-tag"
                                + " semantics - pin down one occurrence instead"
                                + " of relying on unverified behavior",
                        VaspIncarSchema.wikiUrl(tag)));
            }
        }
        if (deck.getIgnoredLineCount() > 0) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_SYNTAX_NOTE,
                    deck.getIgnoredLineCount() + " non-empty line(s) carry no"
                            + " tag=values statement; the wiki states VASP"
                            + " ignores text that does not fit the statement"
                            + " format - review for comment-style lines missing"
                            + " their '#'/'!'",
                    "https://www.vasp.at/wiki/index.php/INCAR"));
        }
        for (String note : deck.getSyntaxNotes()) {
            String code = CODE_SYNTAX_NOTE;
            ValidationSeverity severity = ValidationSeverity.WARNING;
            if (note.startsWith("unterminated")) {
                code = CODE_QUOTES;
                severity = ValidationSeverity.ERROR;
            } else if (note.contains("continuation backslash")) {
                // dedicated code for the wiki-pinned backslash-blank trap
                code = CODE_CONTINUATION;
            }
            issues.add(new ValidationIssue(severity, code, note,
                    "https://www.vasp.at/wiki/index.php/INCAR"));
        }
    }

    /** Tag-recognition + tier-1 type/option checks for ONE statement. */
    private static void applyEntryRules(VaspIncarDeck.Statement statement,
            List<ValidationIssue> issues) {
        String tag = statement.getTag();
        Optional<Entry> entry = VaspIncarSchema.lookup(tag);
        if (entry.isEmpty()) {
        } else {
            applyTier1Rules(statement, entry.get(), issues);
            return;
        }
        if (tag.contains("/")) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_SYNTAX_NOTE,
                    "nested tag " + tag + " (line " + statement.getLine()
                            + "): the wiki documents nested tags (e.g."
                            + " KERNEL_TRUNCATION/LTRUNCATE, PLUGINS curly"
                            + " groups) but none is in the pinned window -"
                            + " value grammar not audited",
                    "https://www.vasp.at/wiki/index.php/INCAR"));
            return;
        }
        if (VaspIncarSchema.isRecognizedName(tag)) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_TIER2,
                    tag + " (line " + statement.getLine()
                            + ") is a tag of the pinned wiki window (incar"
                            + " index page 1 of 4) whose value grammar this"
                            + " schema does not catalog yet - recognized,"
                            + " not audited",
                    VaspIncarSchema.wikiUrl(tag)));
            return;
        }
        issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                CODE_UNKNOWN,
                tag + " (line " + statement.getLine()
                        + ") is not in the pinned 53-tag tier-1 catalogue and"
                        + " is not recognized on the wiki index window"
                        + " (first 200 of 614 tags): VASP does NOT abort on an"
                        + " unknown tag - it is silently ignored, so a typo"
                        + " passes unnoticed; check the spelling on vasp.at"
                        + " (remaining index pages exist too)",
                "https://www.vasp.at/wiki/index.php/INCAR"));
    }

    /** Type + option checks driven by the pinned Entry. */
    private static void applyTier1Rules(VaspIncarDeck.Statement statement,
            Entry entry, List<ValidationIssue> issues) {
        String tag = entry.getName();
        String url = VaspIncarSchema.wikiUrl(tag);
        String value = statement.getRawValue();
        switch (entry.getType()) {
            case INTEGER -> {
                if (statement.intValue().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_TYPE,
                            tag + " (line " + statement.getLine()
                                    + ") = '" + value + "': an INTEGER tag"
                                    + " (wiki TAGDEF '" + entry.getDefaultText()
                                    + "' default) cannot hold a non-integer"
                                    + " value", url));
                    return;
                }
            }
            case REAL -> {
                if (statement.realValue().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_TYPE,
                            tag + " (line " + statement.getLine()
                                    + ") = '" + value + "': a REAL tag"
                                    + " cannot hold a non-numeric value", url));
                    return;
                }
            }
            case LOGICAL -> {
                if (statement.logicalValue().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_TYPE,
                            tag + " (line " + statement.getLine()
                                    + ") = '" + value + "': a LOGICAL tag"
                                    + " accepts only T/F/.TRUE./.FALSE.-family"
                                    + " spellings", url));
                    return;
                }
            }
            case REAL_ARRAY -> {
                if (statement.realArrayValue().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_ARRAY,
                            tag + " (line " + statement.getLine()
                                    + ") = '" + value + "': expected a real"
                                    + " array (the wiki 'N*x' repetition"
                                    + " syntax is honored); one token is"
                                    + " malformed", url));
                    return;
                }
            }
            case INTEGER_ARRAY -> {
                if (statement.intArrayValue().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_ARRAY,
                            tag + " (line " + statement.getLine()
                                    + ") = '" + value + "': expected an"
                                    + " integer array (the wiki 'N*x'"
                                    + " repetition syntax is honored); one"
                                    + " token is malformed or non-integral",
                            url));
                    return;
                }
            }
            default -> {
            }
        }
        // options set (normalized compare; FLAGS accept the wiki's aliases)
        if (!entry.getOptions().isEmpty()) {
            String given = value.trim();
            String normalizedGiven = normalizeOption(given);
            boolean ok = false;
            for (String option : entry.getOptions()) {
                String norm = normalizeOption(option);
                if (norm.equals(normalizedGiven)
                        || optionAliases(tag, option).contains(normalizedGiven)) {
                    ok = true;
                    break;
                }
            }
            // ISMEAR's '>0' option means any positive integer
            if (!ok && tag.equals("ISMEAR") && entry.getOptions().contains(">0")) {
                Optional<Integer> asInt = statement.intValue();
                ok = asInt.isPresent() && asInt.get() > 0;
            }
            // ICHARG's +10 family (11/12 are pinned options; 10 itself is the
            // 'less convenient value' per the wiki text)
            if (!ok && tag.equals("ICHARG")) {
                Optional<Integer> asInt = statement.intValue();
                ok = asInt.isPresent() && asInt.get() == 10;
            }
            if (!ok) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                        CODE_OPTION,
                        tag + " (line " + statement.getLine() + ") = '" + value
                                + "' is outside the pinned option set ["
                                + String.join(", ", entry.getOptions()) + "]"
                                + (tag.equals("LDAUTYPE")
                                        ? " (the page text additionally"
                                                + " documents 3 = Cococcioni"
                                                + " linear response; the TAGDEF"
                                                + " options list 1|2|4)"
                                        : ""),
                        url));
            }
        }
    }

    /** Option normalization: trim, collapse dots/case/spaces. */
    private static String normalizeOption(String option) {
        return option.replace(".", "").replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }

    /** The wiki's own spelling aliases for FLAGS options (LREAL family). */
    private static List<String> optionAliases(String tag, String option) {
        if (!tag.equals("LREAL")) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        switch (normalizeOption(option)) {
            case "AUTO" -> aliases.addAll(List.of("AUTO", "A"));
            case "ON" -> aliases.addAll(List.of("ON", "O"));
            case "TRUE" -> aliases.addAll(List.of("T", "TRUE", "T."));
            case "FALSE" -> aliases.addAll(List.of("F", "FALSE", "F."));
            default -> {
            }
        }
        return aliases;
    }

    // --------------------------- consistency rules ---------------------------

    private static void auditConsistency(VaspIncarDeck deck,
            List<ValidationIssue> issues) {
        Optional<VaspIncarDeck.Statement> lhfcalc = deck.first("LHFCALC");
        boolean hybrid = lhfcalc.flatMap(VaspIncarDeck.Statement::logicalValue)
                .orElse(false);
        Optional<VaspIncarDeck.Statement> algo = deck.first("ALGO");
        if (hybrid && algo.isPresent()
                && algo.get().getRawValue().trim().equalsIgnoreCase("fast")) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_LHFCALC_FAST,
                    "LHFCALC=.TRUE. with ALGO=Fast (line "
                            + algo.get().getLine() + "): the wiki states"
                            + " ALGO=Fast 'is not properly supported' for"
                            + " hybrid functionals and warns '(note: no"
                            + " warning is printed)' - use ALGO=Damped or"
                            + " All (direct optimization recommended)",
                    VaspIncarSchema.wikiUrl("LHFCALC")));
        }
        if (hybrid && deck.first("ALGO").isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_SYNTAX_NOTE,
                    "LHFCALC=.TRUE. without an ALGO pin: default Normal is"
                            + " legal, but the wiki's own tip recommends"
                            + " ALGO=Damped or All for the most reliable"
                            + " hybrid convergence",
                    VaspIncarSchema.wikiUrl("LHFCALC")));
        }
        if (deck.first("NCORE").isPresent() && deck.first("NPAR").isPresent()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_NPAR_NCORE,
                    "both NCORE (line " + deck.first("NCORE").get().getLine()
                            + ") and NPAR (line "
                            + deck.first("NPAR").get().getLine()
                            + ") are set: 'If both NPAR and NCORE are"
                            + " specified in the INCAR file, NPAR takes"
                            + " precedence' (wiki verbatim) - NCORE is the"
                            + " recommended modern tag; drop NPAR",
                    VaspIncarSchema.wikiUrl("NCORE")));
        }
        boolean ldipol = deck.first("LDIPOL")
                .flatMap(VaspIncarDeck.Statement::logicalValue).orElse(false);
        if (ldipol && deck.first("IDIPOL").isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                    CODE_LDIPOL_IDIPOL,
                    "LDIPOL=.TRUE. without IDIPOL: 'When activating this tag"
                            + " the tag IDIPOL has to be specified' (wiki"
                            + " verbatim)",
                    VaspIncarSchema.wikiUrl("LDIPOL")));
        }
        Optional<Integer> icharg = deck.first("ICHARG")
                .flatMap(VaspIncarDeck.Statement::intValue);
        Optional<Integer> lmaxmix = deck.first("LMAXMIX")
                .flatMap(VaspIncarDeck.Statement::intValue);
        if (icharg.isPresent() && icharg.get() >= 11
                && (lmaxmix.isEmpty() || lmaxmix.get() < 4)) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_ICHARG_LMAXMIX,
                    "ICHARG=" + icharg.get() + " reads and KEEPS the charge"
                            + " density: the wiki says 'it is strongly"
                            + " recommended to set LMAXMIX to twice the"
                            + " maximum l-quantum number in the"
                            + " pseudopotentials' (2 for s/p, 4 for d,"
                            + " 6 for f elements) - current LMAXMIX "
                            + (lmaxmix.isEmpty() ? "is absent (default 2)"
                                    : "= " + lmaxmix.get()),
                    VaspIncarSchema.wikiUrl("LMAXMIX")));
        }
        boolean ldau = deck.first("LDAU")
                .flatMap(VaspIncarDeck.Statement::logicalValue).orElse(false);
        if (ldau && (lmaxmix.isEmpty() || lmaxmix.get() < 4)) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_LDAU_LMAXMIX,
                    "LDAU=.TRUE.: DFT+U calculations 'require, in many cases,"
                            + " an increase of LMAXMIX to 4 for d-electrons"
                            + " (or 6 for f-elements)' (wiki verbatim);"
                            + " current LMAXMIX "
                            + (lmaxmix.isEmpty() ? "is absent (default 2)"
                                    : "= " + lmaxmix.get()),
                    VaspIncarSchema.wikiUrl("LMAXMIX")));
        }
        Optional<Integer> ibrion = deck.first("IBRION")
                .flatMap(VaspIncarDeck.Statement::intValue);
        if (ibrion.isPresent() && ibrion.get() == 0) {
            if (deck.first("POTIM").isEmpty()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                        CODE_POTIM_MD,
                        "IBRION=0 (molecular dynamics) without POTIM: 'it HAS"
                                + " to be supplied therefore, otherwise VASP"
                                + " crashes immediately after having started'"
                                + " (wiki verbatim)",
                        VaspIncarSchema.wikiUrl("POTIM")));
            }
            if (deck.first("NSW").isEmpty()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                        CODE_NSW_MD,
                        "IBRION=0 (molecular dynamics) without NSW: 'NSW gives"
                                + " the number of steps in all molecular"
                                + " dynamics runs. It HAS to be supplied,"
                                + " otherwise VASP exits immediately after"
                                + " having started' (wiki verbatim)",
                        VaspIncarSchema.wikiUrl("NSW")));
            }
        }
        Optional<Double> hfscreen = deck.first("HFSCREEN")
                .flatMap(VaspIncarDeck.Statement::realValue);
        if (hfscreen.isPresent() && hfscreen.get() > 0.0) {
            Optional<VaspIncarDeck.Statement> gga = deck.first("GGA");
            if (gga.isPresent()) {
                String given = gga.get().getRawValue().trim()
                        .toUpperCase(Locale.ROOT);
                if (!List.of("PE", "PS", "CA").contains(given)) {
                    issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                            CODE_HFSCREEN_GGA,
                            "HFSCREEN>0 with GGA=" + given + ": 'HFSCREEN can"
                                    + " be used only when GGA=PE, PS or CA.'"
                                    + " (wiki verbatim)",
                            VaspIncarSchema.wikiUrl("HFSCREEN")));
                }
            }
        }
        if (deck.first("METAGGA").isPresent()) {
            Boolean lasph = deck.first("LASPH")
                    .flatMap(VaspIncarDeck.Statement::logicalValue).orElse(null);
            if (lasph == null || !lasph) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                        CODE_META_LASPH,
                        "METAGGA=" + deck.first("METAGGA").get().getRawValue().trim()
                                + " without LASPH=.TRUE.: 'For accuracy, it is"
                                + " strongly recommended to set LASPH=.TRUE.'"
                                + " for meta-GGA functionals (wiki verbatim)",
                        VaspIncarSchema.wikiUrl("METAGGA")));
            }
        }
        Optional<Integer> ismear = deck.first("ISMEAR")
                .flatMap(VaspIncarDeck.Statement::intValue);
        if (ismear.isPresent()
                && List.of(-15, -14, -5, -4).contains(ismear.get())) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_TETRA_GAMMA,
                    "ISMEAR=" + ismear.get() + " selects a tetrahedron"
                            + " method: the wiki tip says 'Use a"
                            + " Gamma-centered k-mesh for the tetrahedron"
                            + " methods' - check the KPOINTS centering",
                    VaspIncarSchema.wikiUrl("ISMEAR")));
        }
        if (deck.first("ENCUT").isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_ENCUT_MANUAL,
                    "ENCUT is absent: the default is the largest ENMAX in the"
                            + " POTCAR (licensed file - quantumforge reads"
                            + " only the wiki fact). The wiki 'strongly"
                            + " recommend(s) specifying the energy cutoff"
                            + " ENCUT always manually in the INCAR file to"
                            + " ensure the same accuracy between"
                            + " calculations'",
                    VaspIncarSchema.wikiUrl("ENCUT")));
        }
        if (deck.first("ISTART").isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_ISTART_AUTO,
                    "ISTART is absent: VASP auto-detects (reads WAVECAR when"
                            + " present, i.e. default 1). Pin ISTART=0"
                            + " explicitly when a fresh start is intended -"
                            + " the wiki example does exactly that",
                    VaspIncarSchema.wikiUrl("ISTART")));
        }
        if (deck.first("NUPDOWN").isPresent() && deck.first("MAGMOM").isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.INFO,
                    CODE_NUPDOWN_MAGMOM,
                    "NUPDOWN is set without MAGMOM: the wiki warns 'If"
                            + " NUPDOWN is set in the INCAR file the initial"
                            + " moment for the charge density should be the"
                            + " same. Otherwise convergence can slow down'"
                            + " (VASP auto-seeds MAGMOM=NUPDOWN/NIONS only"
                            + " from atomic densities, ICHARG=2)",
                    VaspIncarSchema.wikiUrl("NUPDOWN")));
        }
    }
}
