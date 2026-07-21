/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

import quantumforge.input.QEInputReader;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.schema.QEThermoPwSchema;

/**
 * Mined-grammar audit of a thermo_pw {@code thermo_control} file
 * (QE-integration roadmap R6): pairs read through the production
 * {@link QEInputReader}/{@link QENamelist} namelist grammar and adjudicated
 * against {@link QEThermoPwSchema}.
 *
 * <p>Severities mirror what the binary does, mined line by line:</p>
 * <ul>
 *   <li>{@link #CODE_UNKNOWN} = ERROR: READ(iun_thermo, input_thermo) routes
 *       ANY read error to errore('reading input_thermo namelist') - an
 *       unknown namelist keyword is fatal;</li>
 *   <li>{@link #CODE_WHAT_MISSING} = ERROR: thermo_readin errors with
 *       "'what' must be initialized" when what stays blank;</li>
 *   <li>{@link #CODE_WHAT_REJECTED} = ERROR: the part-1 dispatch ends in
 *       errore('what not recognized'); the comparison is Fortran string
 *       equality - case-significant, like the QE SELECT CASE conventions;</li>
 *   <li>{@link #CODE_AGGREGATE_RULE} = ERROR: all_geometries_together is
 *       honoured ONLY for what='mur_lc_t' or what='elastic_constants_geo'
 *       (verbatim mined consistency rule);</li>
 *   <li>{@link #CODE_FLEXT_REMAPPED} = WARNING: flext other than '.pdf' is
 *       silently forced to '.ps' - the binary tolerates and remaps, the audit
 *       says so;</li>
 *   <li>{@link #CODE_TYPE_MISMATCH} = WARNING: shape mismatch against the
 *       type INFERRED from the procedural default literal (advisory only -
 *       inference is stated, never a Fortran declaration).</li>
 * </ul>
 *
 * <p>Scope honesty: group labels are the thermo_pw code's own bookkeeping
 * comments exposed for navigation; they are NOT used to refuse anything.
 * Multi-assignment lines follow the production reader semantics (one
 * assignment per line is the documented convention of every shipped
 * thermo_control example).</p>
 */
public final class QEThermoPwDeckAudit {

    /** ERROR: unknown namelist keyword (fatal read error in thermo_readin). */
    public static final String CODE_UNKNOWN = "THERMO_UNKNOWN_KEYWORD";
    /** ERROR: what must be initialized (verbatim mined message). */
    public static final String CODE_WHAT_MISSING = "THERMO_WHAT_MISSING";
    /** ERROR: what rejected by the part-1 dispatch (CASE DEFAULT errore). */
    public static final String CODE_WHAT_REJECTED = "THERMO_WHAT_REJECTED";
    /** ERROR: all_geometries_together with an unsupported what value. */
    public static final String CODE_AGGREGATE_RULE = "THERMO_AGGREGATE_RULE";
    /** WARNING: flext != '.pdf' is silently forced to '.ps' by the code. */
    public static final String CODE_FLEXT_REMAPPED = "THERMO_VALUE_REMAPPED";
    /** WARNING: value shape mismatches the inferred-from-default type. */
    public static final String CODE_TYPE_MISMATCH = "THERMO_TYPE_MISMATCH";

    private static final Pattern INTEGER_LITERAL = Pattern.compile("[+-]?\\d+");
    private static final Pattern REAL_LITERAL = Pattern.compile(
            "[+-]?(\\d+\\.?\\d*|\\.\\d+)([eEdD][+-]?\\d+)?");
    private static final Pattern LOGICAL_LITERAL = Pattern.compile(
            "\\.(true|false|t|f)\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_THERMO_MARKER = Pattern.compile(
            "&\\s*INPUT_THERMO\\b", Pattern.CASE_INSENSITIVE);

    /** Bounded text surface, same doctrine as the pw adapter. */
    private static final int MAX_DECK_TEXT_CHARS = 1_000_000;

    /**
     * Audits a thermo_control text; null/blank audits empty (a MISSING
     * thermo_control is a structural condition owned elsewhere). Texts beyond
     * the bound refuse with one CODE_TEXT_LIMIT-style warning — never a
     * silent truncation.
     */
    public List<ValidationIssue> auditDeckText(String deckText) {
        if (deckText == null || deckText.isBlank()) {
            return List.of();
        }
        if (deckText.length() > MAX_DECK_TEXT_CHARS) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    "THERMO_TEXT_LIMIT",
                    "thermo_control text of " + deckText.length() + " characters exceeds"
                            + " the audit bound of " + MAX_DECK_TEXT_CHARS
                            + "; it was NOT audited - split or trim instead of trusting"
                            + " a silent truncation.",
                    QEThermoPwSchema.DOCS_URL));
        }
        QEInputReader reader = new QEInputReader();
        reader.appendInputData(deckText);
        QENamelist namelist = new QENamelist(QEThermoPwSchema.NAMELIST_NAME);
        try {
            reader.readNamelist(namelist);
        } catch (java.io.IOException | RuntimeException problem) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    "THERMO_PARSE",
                    "the deck text could not be read as an &INPUT_THERMO namelist"
                            + " through the production namelist grammar; the structural"
                            + " review owns the deck, this layer only audits the mined grammar.",
                    QEThermoPwSchema.DOCS_URL));
        }
        Map<String, String> pairs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (QEValue value : namelist.listQEValues()) {
            if (value != null && value.getName() != null) {
                pairs.put(value.getName(), value.getCharacterValue());
            }
        }
        if (pairs.isEmpty()) {
            if (INPUT_THERMO_MARKER.matcher(deckText).find()) {
                // The namelist exists but parses to NOTHING (e.g. a lone
                // what = ' '): what keeps its ' ' default and the binary
                // aborts - a silent "no issues" here would be a fabricated pass.
                return List.of(new ValidationIssue(ValidationSeverity.ERROR,
                        CODE_WHAT_MISSING,
                        "the &INPUT_THERMO namelist carries no usable assignments; 'what'"
                                + " stays at its ' ' default and thermo_readin aborts with"
                                + " \"'what' must be initialized\".",
                        QEThermoPwSchema.DOCS_URL));
            }
            return List.of(new ValidationIssue(ValidationSeverity.WARNING, "THERMO_PARSE",
                    "no &INPUT_THERMO namelist was found in the supplied text; this audit"
                            + " covers the mined grammar of a thermo_control file.",
                    QEThermoPwSchema.DOCS_URL));
        }
        return auditPairs(pairs);
    }

    /**
     * Core audit on (keyword -> verbatim value) pairs; null pairs NPE toward
     * an explicit empty deck rather than a fabricated verdict.
     */
    public List<ValidationIssue> auditPairs(Map<String, String> pairs) {
        Objects.requireNonNull(pairs, "pairs");
        java.util.List<ValidationIssue> issues = new java.util.ArrayList<>();
        String what = null;
        boolean aggregateTogether = false;
        for (Map.Entry<String, String> kv : pairs.entrySet()) {
            String keyword = kv.getKey();
            String rawValue = kv.getValue() == null ? "" : kv.getValue().trim();
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            var found = QEThermoPwSchema.lookup(keyword);
            if (found.isEmpty()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_UNKNOWN,
                        "&INPUT_THERMO." + keyword + " is not in the mined thermo_pw grammar"
                                + " (" + QEThermoPwSchema.entryCount()
                                + " keywords, commit-provenance in QEThermoPwSchemaData):"
                                + " thermo_pw aborts reading an unknown namelist keyword"
                                + " (READ -> errore) - remove or correct it.",
                        QEThermoPwSchema.DOCS_URL));
                continue;
            }
            QEThermoPwSchema.Entry entry = found.get();
            // Trim like the binary's TRIM(): what=' ' is the uninitialized
            // default and hits 'what must be initialized', not the reject set.
            String value = stripQuotes(rawValue).trim();
            if ("what".equalsIgnoreCase(entry.getName())) {
                what = value;
                if (value.isEmpty()) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_WHAT_MISSING,
                            "what = ' ' reaches thermo_readin, which aborts with"
                                    + " \"'what' must be initialized\" - pick one of the "
                                    + QEThermoPwSchema.whatAcceptedValues().size()
                                    + " mined values.",
                            QEThermoPwSchema.DOCS_URL));
                } else if (!QEThermoPwSchema.whatAcceptedValues().contains(value)) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_WHAT_REJECTED,
                            "what = '" + value + "' is not one of the mined accepted values "
                                    + QEThermoPwSchema.whatAcceptedValues()
                                    + ": the part-1 dispatch ends in errore('what not"
                                    + " recognized'), and the comparison is case-significant"
                                    + " Fortran string equality.",
                            QEThermoPwSchema.DOCS_URL));
                }
            } else if ("all_geometries_together".equalsIgnoreCase(entry.getName())) {
                String normalized = value.toLowerCase(Locale.ROOT);
                // Fortran truth forms only: ".false." contains a "t" too.
                aggregateTogether = ".true.".equals(normalized) || ".t.".equals(normalized);
            } else if ("flext".equalsIgnoreCase(entry.getName())
                    && !"'.pdf'".equalsIgnoreCase(rawValue) && !".pdf".equalsIgnoreCase(value)) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_FLEXT_REMAPPED,
                        "flext = " + rawValue + " is silently forced to '.ps' by thermo_readin"
                                + " (flext/='.pdf' -> '.ps') - say '.pdf' explicitly if you want"
                                + " pdf figures, '.ps' if you want postscript.",
                        QEThermoPwSchema.DOCS_URL));
            }
            auditTypeShape(entry, value, issues);
        }
        if (aggregateTogether && what != null
                && !"mur_lc_t".equals(what) && !"elastic_constants_geo".equals(what)) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_AGGREGATE_RULE,
                    "all_geometries_together=.true. requires what='mur_lc_t' or"
                            + " what='elastic_constants_geo' (verbatim mined consistency rule);"
                            + " with what='" + what + "' thermo_readin aborts.",
                    QEThermoPwSchema.DOCS_URL));
        }
        if (what == null && !pairs.isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_WHAT_MISSING,
                    "no 'what' assignment was read; thermo_readin aborts with \"'what' must"
                            + " be initialized\" when it stays blank.",
                    QEThermoPwSchema.DOCS_URL));
        }
        return List.copyOf(issues);
    }

    private void auditTypeShape(QEThermoPwSchema.Entry entry, String value,
            List<ValidationIssue> issues) {
        boolean ok = switch (entry.getType()) {
            case LOGICAL -> LOGICAL_LITERAL.matcher(value).matches();
            case INTEGER -> INTEGER_LITERAL.matcher(value).matches();
            case REAL -> REAL_LITERAL.matcher(value).matches();
            case CHARACTER, UNKNOWN -> true; // accept any text; inference admits no law
        };
        if (!ok) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_TYPE_MISMATCH,
                    "&INPUT_THERMO." + entry.getName() + " = '" + value + "' does not look "
                            + "like a " + entry.getType() + " literal; the type is INFERRED"
                            + " from the mined default " + entry.getDefaultText()
                            + " (advisory, not a Fortran declaration) - the real read failure"
                            + " would surface as a fatal namelist read error.",
                    QEThermoPwSchema.DOCS_URL));
        }
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
}
