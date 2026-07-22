/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import quantumforge.input.schema.QENamelistSchema;
import quantumforge.input.schema.QENamelistSchema.Entry;
import quantumforge.input.schema.QENamelistSchema.Kind;

/**
 * Version-aware schema audit of QE namelist keywords against the mined
 * {@link QENamelistSchema} (Roadmap #22 completeness work; the machine-checked
 * continuation of the curated batch catalog). This is the dependency-free core,
 * operating on raw (namelist -> keyword -> verbatim value) pairs so every input
 * producer (pw/ph/hp planners, tests, GUI panels) can feed it without owning a
 * QEInput model; {@link QESchemaValidator} is the thin QEInput adapter.
 * Supplements - never replaces - the structural QEInputValidator.
 *
 * <p>Severity doctrine, mirroring what the QE binaries actually do:</p>
 * <ul>
 *   <li>ERROR: findings that make the binary STOP at namelist read or at its
 *       {@code errore()} validation - wrong namelist for the keyword, keyword
 *       not present in the requested version's grammar, HARD value outside the
 *       mined accepted set, typed literal the Fortran reader cannot parse;</li>
 *   <li>WARNING: honest uncertainty - keyword absent from the whole mined
 *       schema (reported, never judged invalid), SOFT value outside the
 *       documented set (the binary silently remaps it), REQUIRED keywords the
 *       input never sets (the REQUIRED card deck lives outside the
 *       namelists, so this layer advises only);</li>
 *   <li>messages always name version window, the accepted/documented values
 *       when mined, and the upstream documentation URL.</li>
 * </ul>
 */
public final class QESchemaAudit {

    public static final String CODE_UNKNOWN = "SCHEMA_UNKNOWN_KEYWORD";
    public static final String CODE_WRONG_NAMELIST = "SCHEMA_WRONG_NAMELIST";
    public static final String CODE_NOT_IN_VERSION = "SCHEMA_NOT_IN_VERSION";
    public static final String CODE_VALUE_REJECTED = "SCHEMA_VALUE_REJECTED";
    public static final String CODE_VALUE_UNDOCUMENTED = "SCHEMA_VALUE_UNDOCUMENTED";
    public static final String CODE_TYPE_MISMATCH = "SCHEMA_TYPE_MISMATCH";
    public static final String CODE_REQUIRED_MISSING = "SCHEMA_REQUIRED_MISSING";

    private static final Pattern INTEGER_LITERAL = Pattern.compile("[+-]?\\d+");
    private static final Pattern REAL_LITERAL = Pattern.compile(
            "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eEdDqQ][+-]?\\d+)?");
    private static final Pattern LOGICAL_LITERAL = Pattern.compile(
            "(?i)\\s*(?:\\.true\\.|\\.false\\.|true|false|t|f)\\s*\\.?");
    private static final Pattern INDEX_SUFFIX = Pattern.compile("\\s*\\(.*\\)\\s*$");

    /**
     * Core audit on raw (namelist -> keyword -> verbatim value) pairs; the seam
     * every input producer (pw/ph/hp planners, tests, GUI panels) can feed
     * without owning a {@link QEInput} model. Nulls NPE toward the empty form.
     */
    public List<ValidationIssue> validatePairs(Kind kind, String version,
            Map<String, ? extends Map<String, String>> pairs) {
        if (kind == null || version == null || pairs == null) {
            throw new NullPointerException("kind, version and pairs are required");
        }
        if (QENamelistSchema.indexOfVersion(version) < 0) {
            throw new IllegalArgumentException("version " + version
                    + " is outside the mined window " + QENamelistSchema.VERSIONS);
        }
        List<ValidationIssue> issues = new java.util.ArrayList<>();
        for (Map.Entry<String, ? extends Map<String, String>> namelist : pairs.entrySet()) {
            String namelistName = namelist.getKey();
            if (namelistName == null || namelist.getValue() == null) {
                continue;
            }
            for (Map.Entry<String, String> kv : namelist.getValue().entrySet()) {
                auditKeyword(kind, version, namelistName, kv.getKey(), kv.getValue(), issues);
            }
        }
        auditRequired(kind, version, pairs, issues);
        return List.copyOf(issues);
    }

    private void auditKeyword(Kind kind, String version, String namelistName,
            String keyword, String rawValue, List<ValidationIssue> issues) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        String base = INDEX_SUFFIX.matcher(keyword.trim()).replaceFirst("")
                .toLowerCase(Locale.ROOT);
        Optional<Entry> found = QENamelistSchema.lookup(kind, base);
        String url = switch (kind) {
            case PW -> QENamelistSchema.INPUT_PW_URL;
            case PH -> QENamelistSchema.INPUT_PH_URL;
            case HP -> QENamelistSchema.INPUT_HP_URL;
        };
        if (found.isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_UNKNOWN,
                    namelistName + "." + keyword + " is not present in the mined QE "
                            + QENamelistSchema.VERSIONS.get(0) + "-"
                            + QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1)
                            + " " + kind + " schema; it is reported, not judged - verify it against "
                            + url + " (it may belong to a card option, a newer release, or the "
                            + "schema's stated gaps).",
                    url));
            return;
        }
        Entry entry = found.get();
        // Namelist placement: the binary aborts reading a namelist that names a
        // variable it does not declare - a placement mismatch is a true stop.
        if (!entry.getNamelist().equalsIgnoreCase(namelistName)) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_WRONG_NAMELIST,
                    "&" + namelistName.toUpperCase(Locale.ROOT) + "." + keyword + ": the mined "
                            + kind + " schema places '" + base + "' in &" + entry.getNamelist()
                            + " (" + kind + " reads unknown namelist members as a fatal read error).",
                    url));
        }
        // Version validity: a keyword the requested version dropped stops its binary.
        if (!entry.presentIn(version)) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_NOT_IN_VERSION,
                    "&" + namelistName.toUpperCase(Locale.ROOT) + "." + keyword + ": '" + base
                            + "' is not part of the QE " + version + " " + kind + " grammar (mined "
                            + "window: " + entry.versionRange() + "); that binary aborts reading it.",
                    url));
        }
        if (rawValue == null) {
            return;
        }
        auditLiteralType(entry, namelistName, keyword, rawValue, issues, url);
        // Hard sets are version-exact: a literal that joined the switch later
        // (e.g. diagonalization='direct' at 7.6) is REJECTED for the earlier
        // version whose binary truly lacks the arm; a version with no mined
        // hard literals at all cannot reject (empty set = no ground truth).
        List<String> acceptedInVersion = entry.getAcceptedValuesIn(version);
        if (!acceptedInVersion.isEmpty() && !entry.acceptsHardValueIn(rawValue, version)) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_VALUE_REJECTED,
                    "&" + namelistName.toUpperCase(Locale.ROOT) + "." + keyword + " = "
                            + rawValue.trim() + ": outside the values the QE " + version
                            + " runtime validation accepts for '" + base + "' (it aborts "
                            + "otherwise). Accepted (QE " + version + "): "
                            + String.join(", ", acceptedInVersion) + " (case-exactly as "
                            + "the Fortran SELECT CASE spells them).",
                    url));
        } else if (entry.getAcceptedValues().isEmpty()
                && !entry.getDocumentedValues().isEmpty()
                && !entry.inDocumentedValues(rawValue)) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_VALUE_UNDOCUMENTED,
                    "&" + namelistName.toUpperCase(Locale.ROOT) + "." + keyword + " = "
                            + rawValue.trim() + ": outside the values the QE runtime documents for '"
                            + base + "'; this program SILENTLY remaps out-of-set values to a "
                            + "fallback instead of failing - check you meant one of: "
                            + String.join(", ", entry.getDocumentedValues()) + ".",
                    url));
        }
    }

    private void auditLiteralType(Entry entry, String namelistName, String keyword,
            String rawValue, List<ValidationIssue> issues, String url) {
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return;
        }
        switch (entry.getType()) {
            case INTEGER -> {
                if (!INTEGER_LITERAL.matcher(value).matches()) {
                    typeMismatch(entry, namelistName, keyword, rawValue, issues, url, "an integer literal");
                }
            }
            case REAL -> {
                if (!REAL_LITERAL.matcher(value).matches()) {
                    typeMismatch(entry, namelistName, keyword, rawValue, issues, url,
                            "a real literal (Fortran d/q exponents accepted)");
                }
            }
            case LOGICAL -> {
                if (!LOGICAL_LITERAL.matcher(value).matches()) {
                    typeMismatch(entry, namelistName, keyword, rawValue, issues, url,
                            "a Fortran logical literal (.true./.false.)");
                }
            }
            case CHARACTER -> { /* every literal parses - nothing to prove */ }
        }
    }

    private void typeMismatch(Entry entry, String namelistName, String keyword, String rawValue,
            List<ValidationIssue> issues, String url, String expectation) {
        issues.add(new ValidationIssue(ValidationSeverity.ERROR, CODE_TYPE_MISMATCH,
                "&" + namelistName.toUpperCase(Locale.ROOT) + "." + keyword + " = "
                        + rawValue.trim() + ": the mined schema declares '" + entry.getName()
                        + "' as " + entry.getType() + ", so the Fortran namelist reader expects "
                        + expectation + " and aborts on this value.",
                url));
    }

    private void auditRequired(Kind kind, String version,
            Map<String, ? extends Map<String, String>> pairs, List<ValidationIssue> issues) {
        for (Entry entry : QENamelistSchema.entries(kind, version)) {
            if (!entry.isRequired()) {
                continue;
            }
            Map<String, String> namelist = findNamelist(pairs, entry.getNamelist());
            if (namelist == null) {
                // REQUIRED binds only when its namelist is in use: a deck without
                // &FCP owes no fcp_mu (the whole namelist is conditional). Whether
                // &SYSTEM may be omitted at all is a structural question left to
                // the deck's producer - never fabricated here.
                continue;
            }
            boolean set = namelist.keySet().stream()
                    .anyMatch(k -> INDEX_SUFFIX.matcher(k.trim()).replaceFirst("")
                            .equalsIgnoreCase(entry.getName()));
            if (!set) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, CODE_REQUIRED_MISSING,
                        "&" + entry.getNamelist() + "." + entry.getName()
                                + " is REQUIRED by the mined " + version + " " + kind + " schema "
                                + "but is not set in this input (mandatory parameters with"
                                + " no usable default).",
                        entry.getDocsUrl()));
            }
        }
    }

    private Map<String, String> findNamelist(Map<String, ? extends Map<String, String>> pairs,
            String namelistName) {
        for (Map.Entry<String, ? extends Map<String, String>> e : pairs.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(namelistName)) {
                return e.getValue();
            }
        }
        return null;
    }
}
