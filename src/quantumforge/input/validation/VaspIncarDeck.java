/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import quantumforge.operation.OperationResult;

/**
 * Batch 173 (roadmap #111, VASP input side): a total parser for the INCAR
 * statement grammar, pinned character-for-character from the vasp.at wiki
 * 'INCAR' page (fetched 2026-07-22):
 *
 * <ul>
 *   <li>each statement is {@code tag = values}; several statements may share
 *       one line when separated by a semicolon {@code ;};</li>
 *   <li>any text after a hashtag {@code #} or exclamation mark {@code !} is
 *       a comment VASP ignores (verbatim), unless inside a quoted value;</li>
 *   <li>a trailing backslash {@code \} joins lines - the wiki warns
 *       'Avoid blanks after the backslash because some versions of VASP
 *       cannot parse those' (quoted, surfaced as a syntax note);</li>
 *   <li>a value may be wrapped in double quotes {@code ".."}; line breaks
 *       inside the quotes are part of the value (WANNIER90_WIN example);</li>
 *   <li>nested tags exist in the literal form
 *       {@code KERNEL_TRUNCATION/LTRUNCATE = T} and in curly-group form
 *       {@code PLUGINS { STRUCTURE = T }} - the group prefixes each inner
 *       tag with {@code PLUGINS/};</li>
 *   <li>text that does not fit the statement format is ignored by VASP
 *       (quoted from the page); it is COUNTED here, never silently
 *       reinterpreted;</li>
 *   <li>logical values are T/F/.TRUE./.FALSE.-family spellings; arrays allow
 *       the {@code N*x} repetition syntax (MAGMOM wiki example
 *       {@code 2*5.0 2*-5.0}).</li>
 * </ul>
 *
 * <p>Honesty: the deck never normalizes, reorders or rewrites - every
 * statement keeps its VERBATIM value text and its original 1-based line
 * number; duplicate tags are reported as a census (the wiki page does not
 * pin duplicate-tag semantics, so the deck collects them and the AUDIT
 * surfaces the review burden - nothing is declared "last wins" here).
 * Bounded reads: 1 MiB / 16384 statements live in the refusal
 * codes.</p>
 */
public final class VaspIncarDeck {

    /** One parsed statement (verbatim value, original line). */
    public static final class Statement {
        private final String tag;
        private final String rawValue;
        private final int line;

        Statement(String tag, String rawValue, int line) {
            this.tag = tag;
            this.rawValue = rawValue;
            this.line = line;
        }

        /** Tag, uppercase-normalized; nested tags keep their PREFIX/. */
        public String getTag() { return this.tag; }
        /** Value text exactly as written (comments stripped, quotes kept out). */
        public String getRawValue() { return this.rawValue; }
        /** 1-based line where the tag appeared. */
        public int getLine() { return this.line; }

        /** Pinned logical spellings (T/F/.TRUE./.FALSE. family), else empty. */
        public Optional<Boolean> logicalValue() {
            String v = this.rawValue.trim().toUpperCase(Locale.ROOT);
            switch (v) {
                case "T", ".T.", "TRUE", ".TRUE." -> {
                    return Optional.of(Boolean.TRUE);
                }
                case "F", ".F.", "FALSE", ".FALSE." -> {
                    return Optional.of(Boolean.FALSE);
                }
                default -> {
                }
            }
            return Optional.empty();
        }

        /** Strict integer (optional sign + digits), else empty. */
        public Optional<Integer> intValue() {
            String v = this.rawValue.trim();
            if (!v.matches("[-+]?\\d+")) {
                return Optional.empty();
            }
            try {
                return Optional.of(Integer.valueOf(v));
            } catch (NumberFormatException problem) {
                return Optional.empty();
            }
        }

        /**
         * Plain real (VASP is Fortran: E and D exponents both accepted;
         * a leading-dot form like '.5' is legal per the wiki examples),
         * or empty when the value is not a single number.
         */
        public Optional<Double> realValue() {
            String v = this.rawValue.trim();
            if (v.isEmpty() || v.contains(" ") || v.contains("*")) {
                return Optional.empty();
            }
            try {
                return Optional.of(Double.valueOf(v.replace('D', 'E').replace('d', 'e')));
            } catch (NumberFormatException problem) {
                return Optional.empty();
            }
        }

        /**
         * Expanded real array honoring the wiki 'N*x' repetition syntax
         * (empty when ANY token is malformed - the audit reports that,
         * nothing is partially expanded).
         */
        public Optional<List<Double>> realArrayValue() {
            return expandArray(this.rawValue, false);
        }

        /** Expanded integer array (same repetition rule), else empty. */
        public Optional<List<Integer>> intArrayValue() {
            Optional<List<Double>> reals = expandArray(this.rawValue, true);
            if (reals.isEmpty()) {
                return Optional.empty();
            }
            List<Integer> out = new ArrayList<>(reals.get().size());
            for (double value : reals.get()) {
                out.add((int) Math.round(value));
            }
            return Optional.of(out);
        }
    }

    private final List<Statement> statements;
    private final Map<String, List<Statement>> byTag;
    private final List<String> syntaxNotes;
    private final int ignoredLineCount;
    private final int totalLines;

    private VaspIncarDeck(List<Statement> statements, List<String> syntaxNotes,
            int ignoredLineCount, int totalLines) {
        this.statements = List.copyOf(statements);
        this.byTag = new LinkedHashMap<>();
        for (Statement statement : this.statements) {
            this.byTag.computeIfAbsent(statement.getTag(), t -> new ArrayList<>())
                    .add(statement);
        }
        this.syntaxNotes = List.copyOf(syntaxNotes);
        this.ignoredLineCount = ignoredLineCount;
        this.totalLines = totalLines;
    }

    /** All statements in file order. */
    public List<Statement> getStatements() { return this.statements; }
    /** Every occurrence of one (normalized) tag, in file order. */
    public List<Statement> occurrencesOf(String tag) {
        return this.byTag.getOrDefault(
                tag.trim().toUpperCase(Locale.ROOT), List.of());
    }
    /** First occurrence or empty (VASP reads the file top to bottom). */
    public Optional<Statement> first(String tag) {
        List<Statement> found = occurrencesOf(tag);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
    /** Distinct tags in first-appearance order. */
    public List<String> distinctTags() { return new ArrayList<>(this.byTag.keySet()); }
    /** True when a tag appears at least twice (the audit owns the verdict). */
    public boolean hasDuplicates(String tag) {
        return occurrencesOf(tag).size() > 1;
    }
    /** Syntax-level notes recorded during parse (never silent). */
    public List<String> getSyntaxNotes() { return this.syntaxNotes; }
    /** Non-empty, non-comment lines that carried NO statement (VASP ignores them). */
    public int getIgnoredLineCount() { return this.ignoredLineCount; }
    /** Physical line count of the source. */
    public int getTotalLines() { return this.totalLines; }

    /** Bounded total parse; codes VASP_INCAR_INPUT / VASP_INCAR_EMPTY / OK. */
    public static OperationResult<VaspIncarDeck> parse(String text) {
        if (text == null) {
            return OperationResult.failed("VASP_INCAR_INPUT",
                    "No INCAR text given.", null);
        }
        if (text.length() > 1024 * 1024) {
            return OperationResult.failed("VASP_INCAR_INPUT",
                    "INCAR text exceeds the 1 MiB audit bound (" + text.length()
                            + " chars) - bounded-reads doctrine, refusing.",
                    null);
        }
        if (text.trim().isEmpty()) {
            return OperationResult.failed("VASP_INCAR_EMPTY",
                    "The INCAR text is empty (or comments-only equivalent is"
                            + " irrelevant here: there are zero lines to read).",
                    null);
        }
        List<Statement> statements = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> prefixStack = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        int pendingLine = 0;
        int ignored = 0;
        boolean inQuote = false;
        int quoteLine = 0;
        String[] lines = text.split("\n", -1);
        outer:
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            int i = 0;
            boolean continued = false;
            // a quoted literal may swallow the whole line
            while (i < line.length()) {
                char c = line.charAt(i);
                if (inQuote) {
                    if (c == '"') {
                        inQuote = false;
                    } else if (c != '\r') {
                        pending.append(c);
                    }
                    i++;
                    continue;
                }
                if (c == '"') {
                    inQuote = true;
                    quoteLine = li + 1;
                    i++;
                    continue;
                }
                if (c == '#' || c == '!') {
                    break; // comment: rest of the line is ignored (wiki verbatim)
                }
                if (c == ';') {
                    if (!emit(pending, pendingLine, li + 1, statements, notes,
                            prefixStack)
                            && !pending.toString().trim().isEmpty()) {
                        ignored++;
                    }
                    pending.setLength(0);
                    pendingLine = 0;
                    // drop leading blanks after the ';' so the NEXT statement
                    // on this line still earns its pendingLine stamp (a
                    // whitespace residue would otherwise re-stamp the
                    // following line with this one)
                    while (i + 1 < line.length()
                            && Character.isWhitespace(line.charAt(i + 1))) {
                        i++;
                    }
                    i++;
                    continue;
                }
                if (c == '{') {
                    String name = pending.toString().trim();
                    pending.setLength(0);
                    pendingLine = 0;
                    if (name.matches("[A-Za-z0-9_]+")) {
                        prefixStack.add(name.toUpperCase(Locale.ROOT));
                    } else {
                        notes.add("line " + (li + 1) + ": '{' without a group"
                                + " name before it - not a wiki-pinned curly"
                                + " group; the brace is dropped");
                    }
                    i++;
                    continue;
                }
                if (c == '}') {
                    emit(pending, pendingLine, li + 1, statements, notes,
                            prefixStack);
                    pending.setLength(0);
                    pendingLine = 0;
                    if (prefixStack.isEmpty()) {
                        notes.add("line " + (li + 1) + ": '}' with no open"
                                + " curly group - dropped");
                    } else {
                        prefixStack.remove(prefixStack.size() - 1);
                    }
                    i++;
                    continue;
                }
                if (c == '\\') {
                    // continuation: legal only as the last meaningful char
                    String rest = line.substring(i + 1);
                    if (rest.trim().isEmpty()) {
                        if (!rest.isEmpty()) {
                            notes.add("line " + (li + 1) + ": blank(s) after"
                                    + " the continuation backslash - the wiki"
                                    + " warns 'Avoid blanks after the"
                                    + " backslash because some versions of"
                                    + " VASP cannot parse those'");
                        }
                        continued = true;
                        i = line.length();
                        continue; // join with the next physical line
                    }
                }
                if (pending.toString().trim().isEmpty()) {
                    if (Character.isWhitespace(c)) {
                        i++;
                        continue;
                    }
                    pendingLine = li + 1;
                }
                pending.append(c);
                i++;
            }
            if (!inQuote && !continued) {
                String tail = pending.toString();
                if (tail.trim().isEmpty()) {
                    pending.setLength(0);
                    pendingLine = 0;
                    continue;
                }
                boolean had = emit(pending, pendingLine, li + 1, statements,
                        notes, prefixStack);
                if (!had) {
                    ignored++;
                }
                pending.setLength(0);
                pendingLine = 0;
            } else if (inQuote) {
                pending.append('\n');
            }
        }
        if (inQuote) {
            notes.add("unterminated quoted value starting at line " + quoteLine
                    + " - VASP would read the rest of the file as the value");
            emit(pending, pendingLine, lines.length, statements, notes,
                    prefixStack);
        }
        if (!prefixStack.isEmpty()) {
            notes.add("curly group " + prefixStack + " never closed"
                    + " - inner tags kept with their PREFIX/ form");
        }
        if (statements.size() > 16384) {
            return OperationResult.failed("VASP_INCAR_INPUT",
                    "more than 16384 INCAR statements - the audit bound is"
                            + " deliberate; refusing instead of reading on",
                    null);
        }
        // a trailing '\n' produces a phantom empty split() element - physical
        // lines stop at the last non-blank one (a blank TAIL is not a line
        // the reader ever sees)
        int total = lines.length;
        while (total > 0 && lines[total - 1].isBlank()) {
            total--;
        }
        return OperationResult.success("VASP_INCAR_OK",
                statements.size() + " statement(s), " + ignored
                        + " ignored bare-text line(s)",
                new VaspIncarDeck(statements, notes, ignored, total));
    }

    /** Emit one pending statement text; true when it yielded a statement. */
    private static boolean emit(StringBuilder pending, int firstLine, int line,
            List<Statement> statements, List<String> notes,
            List<String> prefixStack) {
        String body = pending.toString().trim();
        if (body.isEmpty()) {
            return false;
        }
        int eq = body.indexOf('=');
        if (eq < 0) {
            return false; // bare text - VASP ignores it (page, verbatim)
        }
        String tag = body.substring(0, eq).trim().toUpperCase(Locale.ROOT);
        String value = body.substring(eq + 1).trim();
        if (!tag.matches("[A-Z0-9_/]+")) {
            notes.add("line " + (firstLine == 0 ? line : firstLine)
                    + ": tag '" + tag + "' is not an alphanumeric INCAR name"
                    + " - line kept out of the statement list");
            return false;
        }
        StringBuilder full = new StringBuilder();
        for (String prefix : prefixStack) {
            full.append(prefix).append('/');
        }
        full.append(tag);
        statements.add(new Statement(full.toString(), value,
                firstLine == 0 ? line : firstLine));
        return true;
    }

    /**
     * Expand the wiki array grammar: whitespace-separated tokens, with the
     * repetition form {@code count*value} (MAGMOM example '2*5.0 2*-5.0').
     * Empty when the text has no tokens; empty when ANY token is malformed
     * (fail-closed: a partially expanded array would be a fabrication).
     * EXPANDED VALUES ARE ALWAYS DOUBLES - with {@code integersOnly} the
     * method only VERIFIES integrality; the typed {@code List<Integer>}
     * arrives through {@link Statement#intArrayValue()}.</p>
     */
    public static Optional<List<Double>> expandArray(String raw, boolean integersOnly) {
        List<Double> out = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int star = token.indexOf('*');
            double value;
            int count = 1;
            try {
                if (star >= 0) {
                    String countText = token.substring(0, star);
                    String valueText = token.substring(star + 1);
                    if (!countText.matches("[-+]?\\d+")
                            || Integer.parseInt(countText) <= 0) {
                        return Optional.empty();
                    }
                    count = Integer.parseInt(countText);
                    value = Double.parseDouble(
                            valueText.replace('D', 'E').replace('d', 'e'));
                } else {
                    value = Double.parseDouble(
                            token.replace('D', 'E').replace('d', 'e'));
                }
            } catch (NumberFormatException problem) {
                return Optional.empty();
            }
            if (integersOnly && value != Math.floor(value)) {
                return Optional.empty();
            }
            for (int k = 0; k < count; k++) {
                out.add(value);
                if (out.size() > 100000) {
                    return Optional.empty(); // bounded expansion
                }
            }
        }
        if (out.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(out);
    }
}
