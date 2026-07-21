/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import quantumforge.input.QEInputReader;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.schema.QEAuxSchema;
import quantumforge.input.schema.QENamelistSchema;

/**
 * Mined-grammar audit of an auxiliary-QE-program input deck (batch 159):
 * the 24 programs of {@link QEAuxSchema} adjudicated from the deck text
 * through the production {@link QEInputReader}/{@link QENamelist} namelist
 * grammar - never a parallel mini-parser.
 *
 * <p>Severities mirror what each binary does:</p>
 * <ul>
 *   <li>{@link #CODE_UNKNOWN} = ERROR: a keyword the program's grammar does
 *       not declare aborts the Fortran namelist READ in EVERY one of these
 *       programs (language-level fact, same doctrine as pw/ph/thermo);</li>
 *   <li>{@link #CODE_WRONG_NAMELIST} = ERROR: a keyword placed under the wrong
 *       namelist of the SAME program - the READ under that namelist fails
 *       exactly like an unknown keyword;</li>
 *   <li>{@link #CODE_ABSENT_AT_VERSION} = ERROR: keyword real but absent at
 *       the pinned QE version (masks mined per tag);</li>
 *   <li>{@link #CODE_STOP_SET} = ERROR: spectra_correction's option guard -
 *       out-of-set or missing option makes the program WRITE 'Option not
 *       recognized' and STOP (verbatim mined, not an errore);</li>
 *   <li>{@link #CODE_REQUIRED} = WARNING: a def-REQUIRED keyword without a
 *       declared default is missing from the deck;</li>
 *   <li>{@link #CODE_TYPE_SHAPE} = WARNING: advisory shape check against the
 *       DECLARED type (def -type / source decl; UNKNOWN types never judge);</li>
 *   <li>{@link #CODE_OPTION_UNDOCUMENTED} = WARNING: a CHARACTER value outside
 *       the documented literals - the doc layer is a SOFT hint, honestly
 *       framed as such, never a refusal;</li>
 *   <li>plus the batch-158 {@link QEDeckDialect} XML boundary, a bounded text
 *       refusal, and {@link #CODE_NO_NAMELIST} when no namelist marker
 *       exists at all (structural ownership stays elsewhere).</li>
 * </ul>
 *
 * <p>{@link #detectProgram(String)} is conservative by design: it answers
 * only unambiguous namelist signatures (&INPUTCOND -&gt; pwcond alone);
 * ambiguous families (&INPUT_MANIP -&gt; the two spectra tools, &input-style
 * collisions) are listed by {@link #candidatePrograms(String)} so the caller
 * can say "audit as X (alternatives: Y)" instead of guessing.</p>
 */
public final class QEAuxDeckAudit {

    /** ERROR: keyword unknown to the program's mined grammar (fatal READ). */
    public static final String CODE_UNKNOWN = "AUX_UNKNOWN_KEYWORD";
    /** ERROR: keyword belongs to a different namelist of the same program. */
    public static final String CODE_WRONG_NAMELIST = "AUX_WRONG_NAMELIST";
    /** ERROR: keyword absent at the pinned QE version (mined masks). */
    public static final String CODE_ABSENT_AT_VERSION = "AUX_ABSENT_AT_VERSION";
    /** ERROR: spectra_correction option missing/out-of-set (verbatim STOP). */
    public static final String CODE_STOP_SET = "AUX_OPTION_STOP_SET";
    /** WARNING: def-REQUIRED keyword (no declared default) not provided. */
    public static final String CODE_REQUIRED = "AUX_REQUIRED_MISSING";
    /** WARNING: value shape mismatches the declared type (advisory). */
    public static final String CODE_TYPE_SHAPE = "AUX_TYPE_MISMATCH";
    /** WARNING: value outside the documented option literals (SOFT doc layer). */
    public static final String CODE_OPTION_UNDOCUMENTED = "AUX_OPTION_UNDOCUMENTED";
    /** WARNING: text surface exceeded the bounded audit (refusal, no truncation). */
    public static final String CODE_TEXT_LIMIT = "AUX_TEXT_LIMIT";
    /** WARNING: no namelist marker in the deck text at all. */
    public static final String CODE_NO_NAMELIST = "AUX_NO_NAMELIST";

    /** Bounded text surface, same doctrine as the other audits. */
    private static final int MAX_DECK_TEXT_CHARS = 1_000_000;

    private static final Pattern NAMELIST_OPEN = Pattern.compile(
            "^\\s*&\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*$|^\\s*&\\s*([A-Za-z_][A-Za-z0-9_]*)\\b.*");
    private static final Pattern INTEGER_LITERAL = Pattern.compile("[+-]?\\d+");
    private static final Pattern REAL_LITERAL = Pattern.compile(
            "[+-]?(\\d+\\.?\\d*|\\.\\d+)([eEdDqQ][+-]?\\d+)?");
    private static final Pattern LOGICAL_LITERAL = Pattern.compile(
            "\\.(true|false|t|f)\\.", Pattern.CASE_INSENSITIVE);

    /**
     * Conservative program detection: the single program owning EVERY
     * namelist named in the deck, when exactly one program qualifies and the
     * leading namelist is program-distinctive. Empty for ambiguous decks -
     * {@link #candidatePrograms(String)} then lists the honest alternatives.
     */
    public Optional<String> detectProgram(String deckText) {
        List<String> namelists = deckNamelists(deckText);
        if (namelists.isEmpty()) {
            return Optional.empty();
        }
        List<String> firstOwners = QEAuxSchema.programsForNamelist(namelists.get(0));
        if (firstOwners.size() != 1) {
            return Optional.empty();
        }
        String program = firstOwners.get(0);
        for (int i = 1; i < namelists.size(); i++) {
            if (!QEAuxSchema.programsForNamelist(namelists.get(i))
                    .contains(program)) {
                return Optional.empty();
            }
        }
        return Optional.of(program);
    }

    /** Every program owning at least one namelist named in the deck. */
    public List<String> candidatePrograms(String deckText) {
        Set<String> out = new LinkedHashSet<>();
        for (String namelist : deckNamelists(deckText)) {
            out.addAll(QEAuxSchema.programsForNamelist(namelist));
        }
        return List.copyOf(out);
    }

    /**
     * Audits a deck against the pinned program grammar at the pinned QE
     * version (null version = newest window end, stated in messages).
     * Blank text audits empty (missing files are structural, owned elsewhere).
     */
    public List<ValidationIssue> auditDeckText(String program, String deckText,
                                               String version) {
        Objects.requireNonNull(program, "program");
        String pinned = program.trim().toLowerCase(Locale.ROOT);
        if (QEAuxSchema.entries(pinned).isEmpty()) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    "AUX_PROGRAM_UNKNOWN",
                    "program '" + program + "' is not one of the 24 mined auxiliary"
                            + " programs; the audit ran nothing (refusal, not a pass).",
                    null));
        }
        if (deckText == null || deckText.isBlank()) {
            return List.of();
        }
        if (deckText.length() > MAX_DECK_TEXT_CHARS) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_TEXT_LIMIT,
                    "deck text of " + deckText.length() + " characters exceeds the"
                            + " audit bound of " + MAX_DECK_TEXT_CHARS
                            + "; it was NOT audited - split or trim instead of trusting"
                            + " a silent truncation.",
                    QEAuxSchema.docPage(pinned)));
        }
        String effectiveVersion = effectiveVersion(version);
        if (QEDeckDialect.looksLikeXmlDeck(deckText)) {
            return List.of(QEDeckDialect.boundaryIssue(
                    "auxiliary-program grammar audit (" + pinned + ")",
                    QEAuxSchema.docPage(pinned)));
        }
        Map<String, Map<String, String>> pairs = readPairs(deckText);
        if (pairs.isEmpty()) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_NO_NAMELIST,
                    "no namelist marker (&NAME ... /) was read from this deck; the"
                            + " audit owns the grammar of namelist assignments, the"
                            + " structure of the deck is owned elsewhere.",
                    QEAuxSchema.docPage(pinned)));
        }
        return auditPairs(pinned, pairs, effectiveVersion);
    }

    /** Core audit on already-parsed namelist pairs (null pairs = NPE by design). */
    public List<ValidationIssue> auditPairs(String program,
                                            Map<String, Map<String, String>> pairs,
                                            String version) {
        Objects.requireNonNull(pairs, "pairs");
        String pinned = program.trim().toLowerCase(Locale.ROOT);
        String effectiveVersion = effectiveVersion(version);
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> programNamelists = QEAuxSchema.namelists(pinned);
        boolean stopSetPresent = false;
        Map<String, String> firstFlat = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        pairs.values().forEach(firstFlat::putAll);
        for (Map.Entry<String, Map<String, String>> namelistEntry : pairs.entrySet()) {
            String namelist = namelistEntry.getKey().toUpperCase(Locale.ROOT);
            for (Map.Entry<String, String> kv : namelistEntry.getValue().entrySet()) {
                String keyword = kv.getKey();
                String rawValue = kv.getValue() == null ? "" : kv.getValue().trim();
                if (keyword == null || keyword.trim().isEmpty()) {
                    continue;
                }
                List<QEAuxSchema.Row> rows = QEAuxSchema.lookup(pinned, keyword);
                if (rows.isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_UNKNOWN,
                            "&" + namelist + "." + keyword + " is not in the mined "
                                    + pinned + " grammar (" + QEAuxSchema.entries(pinned).size()
                                    + " keywords, tags 7.2..7.6): the Fortran namelist READ"
                                    + " aborts on unknown keywords in every mined program -"
                                    + " remove or correct it.",
                            QEAuxSchema.docPage(pinned)));
                    continue;
                }
                QEAuxSchema.Row row = null;
                for (QEAuxSchema.Row candidate : rows) {
                    if (candidate.getNamelist().equalsIgnoreCase(namelist)) {
                        row = candidate;
                        break;
                    }
                }
                if (row == null) {
                    // declared in this program but under another namelist
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_WRONG_NAMELIST,
                            "&" + namelist + "." + keyword + " is declared by " + pinned
                                    + " only under &" + rows.get(0).getNamelist()
                                    + ": the READ of &" + namelist + " does not accept it"
                                    + " and aborts exactly like an unknown keyword - move"
                                    + " or remove it.",
                            QEAuxSchema.docPage(pinned)));
                    continue;
                }
                if (!row.presentIn(effectiveVersion)) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            CODE_ABSENT_AT_VERSION,
                            "&" + namelist + "." + keyword + " is not declared by " + pinned
                                    + " at QE " + effectiveVersion + " (it exists from "
                                    + row.firstPresentLabel() + " in the mined window):"
                                    + " at " + effectiveVersion + " the namelist READ"
                                    + " aborts like an unknown keyword.",
                            QEAuxSchema.docPage(pinned)));
                    continue;
                }
                String value = stripQuotes(rawValue).trim();
                if (pinned.equals("spectra_correction")
                        && keyword.equalsIgnoreCase("option")) {
                    stopSetPresent = true;
                    if (!isStopSetValue(value)) {
                        issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                                CODE_STOP_SET,
                                "INPUT_MANIP.option = '" + value + "' is outside the mined"
                                        + " STOP-set (cut_occ_states / add_L2_L3 /"
                                        + " convolution): spectra_correction writes"
                                        + " 'Option not recognized', 'Program is stopped'"
                                        + " and STOPS - a hard abort, not a remap.",
                                QEAuxSchema.docPage(pinned)));
                    }
                }
                auditTypeShape(row, namelist, value, effectiveVersion, issues);
                auditDocumentedOptions(row, namelist, value, effectiveVersion, issues);
            }
        }
        if (pinned.equals("spectra_correction") && !stopSetPresent
                && firstFlat.keySet().stream()
                        .noneMatch(k -> k.equalsIgnoreCase("option"))) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_STOP_SET,
                    "no option= assignment was read for spectra_correction: 'option'"
                            + " stays blank, the guard compares it against all three"
                            + " accepted literals and the program STOPS with 'Option"
                            + " not recognized' / 'Program is stopped' - set one of"
                            + " cut_occ_states / add_L2_L3 / convolution explicitly.",
                    QEAuxSchema.docPage(pinned)));
        }
        for (QEAuxSchema.Row row : QEAuxSchema.entries(pinned)) {
            if (!row.isRequired() || row.getDefaultText() != null
                    || !row.presentIn(effectiveVersion)) {
                continue;
            }
            boolean provided = firstFlat.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase(row.getName()));
            if (!provided) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                        CODE_REQUIRED,
                        pinned + "." + row.getName() + " is marked REQUIRED by the"
                                + " machine grammar and declares NO default; it was not"
                                + " read in the deck - set it explicitly before running.",
                        QEAuxSchema.docPage(pinned)));
            }
        }
        return List.copyOf(issues);
    }

    private void auditTypeShape(QEAuxSchema.Row row, String namelist,
                                String value, String version,
                                List<ValidationIssue> issues) {
        boolean ok = switch (row.getType().toUpperCase(Locale.ROOT)) {
            case "INTEGER", "INT" -> INTEGER_LITERAL.matcher(value).matches();
            case "REAL", "DOUBLE", "FLOAT" -> REAL_LITERAL.matcher(value).matches();
            case "LOGICAL" -> LOGICAL_LITERAL.matcher(value).matches();
            default -> true; // CHARACTER/UNKNOWN: any text passes honestly
        };
        if (!ok) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_TYPE_SHAPE,
                    "&" + namelist + "." + row.getName() + " = '" + value + "' does not"
                            + " look like a " + row.getType() + " literal (declared type,"
                            + " advisory only) - a real failure would surface as the"
                            + " fatal namelist read error.",
                    QEAuxSchema.docPage(row.getProgram())));
        }
    }

    private void auditDocumentedOptions(QEAuxSchema.Row row, String namelist,
                                        String value, String version,
                                        List<ValidationIssue> issues) {
        if (row.getOptions().isEmpty() || value.isEmpty()
                || !"CHARACTER".equalsIgnoreCase(row.getType())) {
            return;
        }
        for (QEAuxSchema.OptionLiteral literal : row.getOptions()) {
            if (literal.getValue().equalsIgnoreCase(value)) {
                return;
            }
        }
        List<String> documented = new ArrayList<>();
        for (QEAuxSchema.OptionLiteral literal : row.getOptions()) {
            documented.add(literal.getValue());
        }
        issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                CODE_OPTION_UNDOCUMENTED,
                "&" + namelist + "." + row.getName() + " = '" + value + "' is not among"
                        + " the documented literals " + documented + " of the machine"
                        + " grammar - a SOFT doc-layer hint: the program's own switch"
                        + " decides whether it remaps or aborts; verify with the docs.",
                QEAuxSchema.docPage(row.getProgram())));
    }

    private static boolean isStopSetValue(String value) {
        return value.equalsIgnoreCase("cut_occ_states")
                || value.equalsIgnoreCase("add_L2_L3")
                || value.equalsIgnoreCase("convolution");
    }

    /** Read all namelist pairs through the production reader grammar. */
    private static Map<String, Map<String, String>> readPairs(String deckText) {
        QEInputReader reader = new QEInputReader();
        reader.appendInputData(deckText);
        Map<String, Map<String, String>> pairs =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String namelistName : deckNamelists(deckText)) {
            QENamelist namelist = new QENamelist(namelistName);
            try {
                reader.readNamelist(namelist);
            } catch (java.io.IOException | RuntimeException problem) {
                // a deck the reader cannot parse is structural; the structural
                // validator owns it - this layer stays an audit.
                continue;
            }
            Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (QEValue value : namelist.listQEValues()) {
                if (value != null && value.getName() != null) {
                    out.put(value.getName(), value.getCharacterValue());
                }
            }
            // an explicit namelist marker registers even with zero assignments:
            // an empty &INPUT_BGW2PW is "prefix (REQUIRED) missing", honestly
            // different from CODE_NO_NAMELIST (no marker in the deck at all).
            pairs.put(namelistName, out);
        }
        return pairs;
    }

    /** Namelist header names in deck order (&NAME lines, comment-stripped). */
    private static List<String> deckNamelists(String deckText) {
        List<String> out = new ArrayList<>();
        if (deckText == null) {
            return out;
        }
        for (String line : deckText.split("\\R")) {
            int bang = line.indexOf('!');
            String clean = bang < 0 ? line : line.substring(0, bang);
            java.util.regex.Matcher matcher = NAMELIST_OPEN.matcher(clean);
            if (matcher.matches()) {
                String name = matcher.group(1) != null ? matcher.group(1)
                        : matcher.group(2);
                out.add(name.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String stripQuotes(String rawValue) {
        String value = rawValue.trim();
        if (value.length() >= 2
                && ((value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')
                || (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String effectiveVersion(String version) {
        if (version != null && QENamelistSchema.indexOfVersion(version) >= 0) {
            return version.trim();
        }
        return QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
    }
}
