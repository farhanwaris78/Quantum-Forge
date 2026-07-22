/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.input.schema.QECardSchema;
import quantumforge.input.schema.QENamelistSchema;

/**
 * Mined-grammar audit of the CARD section of a pw.x input deck
 * (QE-integration roadmap R4): every card-shaped line adjudicated against
 * {@link QECardSchema}, whose rows come verbatim from
 * Modules/read_cards.f90 at tags qe-7.2 .. qe-7.6.
 *
 * <p>Severities mirror what pw.x itself does, mined line by line:</p>
 * <ul>
 *   <li>{@link #CODE_CARD_REMOVED} = ERROR: DIPOLE/ESR arms call errore
 *       'no longer existing' - the binary aborts;</li>
 *   <li>{@link #CODE_TRAP} = ERROR: HUBBARD sanity arms abort with
 *       'Wrong name of the Hubbard projectors' (chain-order, substring
 *       matches() semantics - so PSEUDO-ATOMIC traps exactly like pw.x);</li>
 *   <li>{@link #CODE_UNKNOWN_OPTION} = ERROR only when the card's mined
 *       ELSE arm calls errore (ATOMIC_POSITIONS, HUBBARD, SOLVENTS); when
 *       the arm TOLERATES with a default (K_POINTS silent tpiba,
 *       CELL_PARAMETERS DEPRECATED 'none', REF_CELL_PARAMETERS silent
 *       'alat') it is a WARNING that names the remedial default;</li>
 *   <li>{@link #CODE_BARE_CARD} = informative WARNING for HUBBARD (bare
 *       name also aborts - promoted to ERROR there) and DEPRECATED-default
 *       notes for bare ATOMIC_POSITIONS / SOLVENTS;</li>
 *   <li>{@link #CODE_MESH} = ERROR: K_POINTS { automatic } grids violating
 *       the mined constraints (nk_i &gt; 0; offsets in {0,1}) abort in
 *       card_kpoints;</li>
 *   <li>{@link #CODE_UNKNOWN_CARD} = WARNING: pw.x's dispatch fallback is a
 *       bare 'Warning: card ... ignored' WRITE - never a read error - so
 *       the audit warns (the card AND its intended content are IGNORED by
 *       the binary; a typo here silently loses physics);</li>
 *   <li>{@link #CODE_GATE} = WARNING: prog-gated cards outside their
 *       program context (SOLVENTS without trism, WANNIER_AC outside 'WA')
 *       fall through to the same ignored-warning.</li>
 * </ul>
 *
 * <p>Scope honesty: this is a LINE-SHAPED audit - a line only counts as a
 * candidate card when it starts with an alphabetic word optionally followed
 * by a { ... } / ( ... ) option, after stripping trailing ! comments and
 * skipping namelist bodies and blank/comment lines. Content lines of known
 * cards can therefore never be mistaken for cards EXCEPT for the detached,
 * brace-carrying pseudo-headers which pw.x itself would also dispatch on.
 * Content rules beyond the mined grammar stay with QEInputValidator.</p>
 */
public final class QECardAudit {

    /** ERROR: card no longer existing (DIPOLE/ESR dispatch arms errore). */
    public static final String CODE_CARD_REMOVED = "CARD_REMOVED_FATAL";
    /** ERROR: a mined sanity-trap arm matched (HUBBARD projector names). */
    public static final String CODE_TRAP = "CARD_OPTION_TRAP";
    /** ERROR/WARNING: unrecognised option (severity = mined disposition). */
    public static final String CODE_UNKNOWN_OPTION = "CARD_OPTION_UNRECOGNISED";
    /** ERROR/WARNING: bare card name where the mined bare-branch acts. */
    public static final String CODE_BARE_CARD = "CARD_BARE_NAME";
    /** ERROR: K_POINTS { automatic } mesh violates the mined constraints. */
    public static final String CODE_MESH = "KP_AUTOMATIC_MESH";
    /** WARNING: unknown card name - pw.x writes a warning and IGNORES it. */
    public static final String CODE_UNKNOWN_CARD = "CARD_UNKNOWN_IGNORED";
    /** WARNING: prog-gated card outside its program context (ignored). */
    public static final String CODE_GATE = "CARD_PROG_GATE";
    /** WARNING: text surface exceeded the bounded audit (refusal, no truncation). */
    public static final String CODE_TEXT_LIMIT = "CARD_TEXT_LIMIT";

    /** Bounded text surface, same doctrine as the other audits. */
    private static final int MAX_DECK_TEXT_CHARS = 1_000_000;

    /** Card-shaped line: WORD, optional {opt}/(opt), comment stripped already. */
    private static final Pattern CARD_LINE = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z_0-9]*)\\s*(?:[{(](.*)[})])?\\s*$");
    private static final Pattern NAMELIST_OPEN = Pattern.compile("^\\s*&\\s*\\S+");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    /**
     * Audits a pw.x input deck text against the mined card grammar for the
     * given QE version (null = newest window end, stated in messages).
     * Null/blank text audits empty - a missing FILENAME-owned deck is a
     * structural condition owned elsewhere.
     */
    public List<ValidationIssue> auditDeckText(String deckText, String version) {
        if (deckText == null || deckText.isBlank()) {
            return List.of();
        }
        if (deckText.length() > MAX_DECK_TEXT_CHARS) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_TEXT_LIMIT,
                    "deck text of " + deckText.length() + " characters exceeds the"
                            + " audit bound of " + MAX_DECK_TEXT_CHARS
                            + "; it was NOT card-audited - split or trim instead of"
                            + " trusting a silent truncation.",
                    QECardSchema.INPUT_CARD_URL));
        }
        String effectiveVersion = effectiveVersion(version);
        if (QEDeckDialect.looksLikeXmlDeck(deckText)) {
            return List.of(QEDeckDialect.boundaryIssue(
                    "pw.x card grammar audit", QECardSchema.INPUT_CARD_URL));
        }
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> lines = deckText.lines().toList();
        boolean inNamelist = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = stripComment(lines.get(i));
            if (line.isBlank()) {
                continue;
            }
            if (!inNamelist && NAMELIST_OPEN.matcher(line).find()) {
                inNamelist = true;
            }
            if (inNamelist) {
                if (line.trim().startsWith("/")) {
                    inNamelist = false;
                }
                continue;
            }
            Matcher matcher = CARD_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String card = matcher.group(1).toUpperCase(Locale.ROOT);
            String bracketed = matcher.group(2);
            // Option text = everything after the card word with optional
            // braces removed (matches() substring semantics, pre-capitalised).
            String optionText = bracketed != null ? bracketed.trim()
                    : line.substring(line.toUpperCase(Locale.ROOT).indexOf(card)
                            + card.length()).replaceAll("[{}()]", " ").trim();
            Optional<QECardSchema.Dispatch> dispatch =
                    QECardSchema.lookupDispatch(card);
            if (dispatch.isEmpty()) {
                // pw.x fallback: 'Warning: card ... ignored' - tolerated.
                issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                        CODE_UNKNOWN_CARD,
                        "'" + card + "' is not a card pw.x " + effectiveVersion
                                + " dispatches on; read_cards writes 'Warning: card "
                                + card.toLowerCase(Locale.ROOT) + " ignored' and moves"
                                + " on WITHOUT reading its content - if this is a typo,"
                                + " the intended physics is silently lost.",
                        QECardSchema.INPUT_CARD_URL));
                continue;
            }
            QECardSchema.Dispatch row = dispatch.get();
            if (!row.presentIn(effectiveVersion)) {
                continue; // outside this version's tag window: honest silence
            }
            if (row.isKilled()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                        CODE_CARD_REMOVED,
                        "card '" + card + "' no longer exists in QE " + effectiveVersion
                                + ": its read_cards arm calls errore('card "
                                + card.toLowerCase(Locale.ROOT)
                                + " no longer existing') and pw.x ABORTS.",
                        QECardSchema.INPUT_CARD_URL));
                continue;
            }
            adjudicateGate(card, row, issues);
            adjudicate(card, optionText, bracketed != null, lines, i,
                    effectiveVersion, issues);
        }
        return issues;
    }

    /**
     * Prog-gate adjudication for a PW run (this audit adjudicates pw.x
     * decks). The mined knowledge, card by card: KSOUT is read and THEN
     * pw.x warns it is ignored; WANNIER_AC is dispatched only when
     * prog == 'WA', so a plain pw.x run falls through to the generic
     * 'Warning: card ... ignored' fallback; SOLVENTS is dispatched only
     * when the rism/trism switch is active (a namelist condition the audit
     * names honestly instead of resolving); K_POINTS warns only under CP,
     * so a pw.x deck earns NO finding here.
     */
    private static final Pattern PROG_GATE = Pattern.compile(
            "prog == '([A-Z0-9]+)'");
    private static final Pattern TRISM_GATE = Pattern.compile(
            "(?i)(?<![A-Za-z_(])trism\\b");

    private void adjudicateGate(String card, QECardSchema.Dispatch row,
                                List<ValidationIssue> issues) {
        String warnProg = row.getWarnProg();
        if ("PW".equalsIgnoreCase(warnProg) || "ANY".equalsIgnoreCase(warnProg)) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_GATE,
                    "card '" + card + "' is read by pw.x but then flagged with"
                            + " 'Warning: card " + card.toLowerCase(Locale.ROOT)
                            + " ignored' under prog='PW' (its effect belongs to"
                            + " another program context).",
                    QECardSchema.INPUT_CARD_URL));
            return;
        }
        java.util.regex.Matcher prog = PROG_GATE.matcher(row.getCondition());
        if (prog.find() && !"PW".equalsIgnoreCase(prog.group(1))) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_GATE,
                    "card '" + card + "' is dispatched only when prog == '"
                            + prog.group(1) + "' (" + row.getCondition()
                            + "); a plain pw.x run falls through to the generic"
                            + " 'Warning: card ... ignored' fallback and the card"
                            + " has NO effect.",
                    QECardSchema.INPUT_CARD_URL));
            return;
        }
        if (TRISM_GATE.matcher(row.getCondition()).find()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_GATE,
                    "card '" + card + "' is dispatched only when the rism/trism"
                            + " switch is active (" + row.getCondition()
                            + "); without it pw.x ignores the card with the same"
                            + " generic warning.",
                    QECardSchema.INPUT_CARD_URL));
        }
        // warnProg "CP" (K_POINTS) and friends: silent in a pw.x deck audit.
    }

    /** Option-switch adjudication for one dispatched card. */
    private void adjudicate(String card, String optionText, boolean hadBrackets,
                            List<String> lines, int lineIndex, String version,
                            List<ValidationIssue> issues) {
        Optional<QECardSchema.CardGrammar> found = QECardSchema.lookupGrammar(card);
        if (found.isEmpty()) {
            return; // dispatched but content-only and unmined: honest silence
        }
        QECardSchema.CardGrammar grammar = found.get();
        if (grammar.getDisposition() == QECardSchema.Disposition.IGNORED) {
            return; // content-only card: option text never even read by pw.x
        }
        if (optionText.isEmpty()) {
            // BARE card name: the mined bare-branch disposition rules.
            switch (grammar.getBareDisposition()) {
                case FATAL -> issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, CODE_BARE_CARD,
                        "bare '" + card + "' with no projector option ABORTS pw.x "
                                + version + " (" + grammar.getNote()
                                + "); choose one of " + optionsList(grammar) + ".",
                        QECardSchema.INPUT_CARD_URL));
                case TOLERATED_DEFAULT -> issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING, CODE_BARE_CARD,
                        "bare '" + card + "' triggers the mined DEPRECATED-default"
                                + " path (" + grammar.getNote() + ").",
                        QECardSchema.INPUT_CARD_URL));
                default -> { } // silent paths need no finding
            }
            return;
        }
        Optional<QECardSchema.ChainEntry> match = grammar.firstChainMatch(optionText);
        if (match.isPresent()) {
            QECardSchema.ChainEntry entry = match.get();
            if (!entry.getLiteral().presentIn(version)) {
                return; // outside window: other layers own drift reporting
            }
            if (entry.isTrap()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_TRAP,
                        "'" + card + "' option text '" + optionText
                                + "' matches the mined sanity-trap literal '"
                                + entry.getLiteral().getValue() + "': pw.x " + version
                                + " aborts with 'Wrong name of the Hubbard projectors'"
                                + ". Valid projectors: " + optionsList(grammar) + ".",
                        QECardSchema.INPUT_CARD_URL));
            }
            if (entry.getLiteral().getValue().equalsIgnoreCase("AUTOMATIC")) {
                auditAutomaticMesh(lines, lineIndex, version, issues);
            }
            return;
        }
        // No chain arm matched: the mined ELSE-arm disposition rules.
        switch (grammar.getDisposition()) {
            case FATAL -> issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, CODE_UNKNOWN_OPTION,
                    "'" + card + "' option '" + optionText + "' matches no mined"
                            + " option; pw.x " + version + " ABORTS ("
                            + grammar.getNote() + "). Mined options: "
                            + optionsList(grammar) + ".",
                    QECardSchema.INPUT_CARD_URL));
            case TOLERATED_DEFAULT, TOLERATED_SILENT_DEFAULT ->
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING, CODE_UNKNOWN_OPTION,
                            "'" + card + "' option '" + optionText
                                    + "' matches no mined option; pw.x " + version
                                    + " TOLERATES it by default (" + grammar.getNote()
                                    + ") - the requested unit may NOT be in effect.",
                            QECardSchema.INPUT_CARD_URL));
            default -> { } // TOLERATED_SILENT / UNKNOWN: no finding to stand on
        }
    }

    /**
     * K_POINTS { automatic }: the next line must hold six integers with
     * nk_i &gt; 0 and offsets in {0,1} - verbatim from card_kpoints
     * ('invalid offsets: must be 0 or 1'; 'invalid values for nk1, nk2, nk3').
     */
    private void auditAutomaticMesh(List<String> lines, int cardLine,
                                    String version, List<ValidationIssue> issues) {
        int target = -1;
        for (int i = cardLine + 1; i < lines.size(); i++) {
            String line = stripComment(lines.get(i));
            if (!line.isBlank()) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_MESH,
                    "K_POINTS { automatic } is not followed by a mesh line;"
                            + " card_kpoints reads nk1 nk2 nk3 k1 k2 k3 from the"
                            + " NEXT line and aborts at end of file.",
                    QECardSchema.INPUT_CARD_URL));
            return;
        }
        String[] tokens = stripComment(lines.get(target)).trim().split("\\s+");
        if (tokens.length < 6) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_MESH,
                    "K_POINTS { automatic } mesh line '" + lines.get(target).trim()
                            + "' holds " + tokens.length + " fields; card_kpoints"
                            + " READs six integers (nk1 nk2 nk3 k1 k2 k3) and a"
                            + " failed READ is an input error in pw.x " + version + ".",
                    QECardSchema.INPUT_CARD_URL));
            return;
        }
        int[] numbers = new int[6];
        for (int i = 0; i < 6; i++) {
            if (!INTEGER.matcher(tokens[i]).matches()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_MESH,
                        "K_POINTS { automatic } mesh field '" + tokens[i]
                                + "' is not an integer; card_kpoints READs six"
                                + " integers and a failed READ is an input error.",
                        QECardSchema.INPUT_CARD_URL));
                return;
            }
            numbers[i] = Integer.parseInt(tokens[i]);
        }
        if (numbers[0] <= 0 || numbers[1] <= 0 || numbers[2] <= 0) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_MESH,
                    "K_POINTS { automatic } mesh " + numbers[0] + " " + numbers[1]
                            + " " + numbers[2] + " violates nk_i > 0: pw.x " + version
                            + " aborts with 'invalid values for nk1, nk2, nk3'.",
                    QECardSchema.INPUT_CARD_URL));
        }
        for (int i = 3; i < 6; i++) {
            if (numbers[i] < 0 || numbers[i] > 1) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_MESH,
                        "K_POINTS { automatic } offset k" + (i - 2) + "=" + numbers[i]
                                + " is outside {0,1}: pw.x " + version
                                + " aborts with 'invalid offsets: must be 0 or 1'.",
                        QECardSchema.INPUT_CARD_URL));
                break;
            }
        }
    }

    private static String optionsList(QECardSchema.CardGrammar grammar) {
        List<String> out = new ArrayList<>();
        for (QECardSchema.PresenceValue value : grammar.getOptions()) {
            out.add(value.getValue());
        }
        return out.isEmpty() ? "(none mined)" : String.join(", ", out);
    }

    private static String stripComment(String line) {
        int bang = line.indexOf('!');
        return bang < 0 ? line : line.substring(0, bang);
    }

    private static String effectiveVersion(String version) {
        if (version != null && QENamelistSchema.indexOfVersion(version) >= 0) {
            return version.trim();
        }
        return QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
    }
}
