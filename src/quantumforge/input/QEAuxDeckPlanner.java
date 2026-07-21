/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

import quantumforge.input.schema.QEAuxSchema;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.input.validation.QEAuxDeckAudit;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.operation.OperationResult;

/**
 * Version-windowed deck-draft planner for the 24 auxiliary QE programs
 * (batch 160, the frontend-facing half of the batch-159 R7 seam): the typed
 * field list an edit dialog may offer for ONE auxiliary program at ONE
 * pinned QE version, plus the deck renderer and live audit that drive the
 * dialog - built entirely from the mined {@link QEAuxSchema} grammar and
 * adjudicated by the mined {@link QEAuxDeckAudit}, never re-interpreted.
 *
 * <p>Honesty rules carried by every surface:</p>
 * <ul>
 *   <li>an unknown program or version label is a TYPED REFUSAL
 *       ({@link #CODE_PROGRAM}/{@link #CODE_VERSION}) naming the supported
 *       labels - never a silent fallback to a different grammar window;
 *       only an explicitly blank version resolves to the newest mined tag,
 *       and the resolved label is always exposed;</li>
 *   <li>{@link #fields()} lists only keywords PRESENT in the pinned window
 *       (5-bit mined masks): a prompt can never hand the binary a keyword
 *       its own namelist reader will abort on;</li>
 *   <li>{@link #renderDraft(Map)} REFUSES undeclared keywords
 *       ({@link #CODE_KEYWORD}) and keywords absent at the pinned version
 *       ({@link #CODE_KEYWORD_VERSION}, first-present label named) instead
 *       of emitting a deck the program cannot read;</li>
 *   <li>value rendering is mechanical and documented: already-quoted values
 *       pass verbatim (never double-quoted); declared CHARACTER values are
 *       single-quoted with Fortran '' escaping; declared numeric/LOGICAL
 *       values pass verbatim; UNKNOWN-typed values (the def-less XSpectra
 *       family, whose declaration types live in other module files) pass
 *       verbatim when they carry an obvious Fortran literal shape and are
 *       single-quoted otherwise - the audit stays the adjudicator;</li>
 *   <li>{@link #auditDraft(Map)} renders and then hands the deck to
 *       {@link QEAuxDeckAudit} - ONE grammar implementation, so the dialog
 *       can never drift from the audit the results surface runs;</li>
 *   <li>REQUIRED flags, declared defaults and documented option literals
 *       ride verbatim from the mined grammar - never rephrased, never
 *       invented; the literals stay a SOFT layer (see the audit).</li>
 * </ul>
 */
public final class QEAuxDeckPlanner {

    /** Typed refusal: program not among the 24 mined auxiliary programs. */
    public static final String CODE_PROGRAM = "QEAUX_PROGRAM";
    /** Typed refusal: version label outside the mined 7.2..7.6 window. */
    public static final String CODE_VERSION = "QEAUX_VERSION";
    /** Typed refusal: keyword undeclared by the pinned program's grammar. */
    public static final String CODE_KEYWORD = "QEAUX_KEYWORD";
    /** Typed refusal: keyword real, but absent at the pinned version. */
    public static final String CODE_KEYWORD_VERSION = "QEAUX_KEYWORD_VERSION";
    /** Typed status: no assignments, nothing rendered (not an error). */
    public static final String CODE_EMPTY = "QEAUX_EMPTY";

    private static final Pattern FORTRAN_INTEGER = Pattern.compile("[+-]?\\d+");
    private static final Pattern FORTRAN_REAL = Pattern.compile(
            "[+-]?(\\d+\\.?\\d*|\\.\\d+)([eEdDqQ][+-]?\\d+)?");
    private static final Pattern FORTRAN_LOGICAL = Pattern.compile(
            "\\.(true|false|t|f)\\.", Pattern.CASE_INSENSITIVE);

    private final String program;
    private final String version;
    private final List<FieldRow> fields;
    private final List<String> namelists;

    private QEAuxDeckPlanner(String program, String version, List<FieldRow> fields,
            List<String> namelists) {
        this.program = program;
        this.version = version;
        this.fields = fields;
        this.namelists = namelists;
    }

    /**
     * Builds a planner for one program at one pinned version window. Codes:
     * QEAUX_PROGRAM (unknown label), QEAUX_VERSION (outside the window),
     * QEAUX_OK / QEAUX_OK_NEWEST (built; the second names the silently
     * resolved newest when no version was pinned).
     */
    public static OperationResult<QEAuxDeckPlanner> forProgram(String program,
            String version) {
        if (program == null || program.trim().isEmpty()) {
            return OperationResult.failed(CODE_PROGRAM,
                    "No auxiliary program supplied; an empty label is never guessed.",
                    null);
        }
        String pinnedProgram = program.trim().toLowerCase(Locale.ROOT);
        if (QEAuxSchema.entries(pinnedProgram).isEmpty()) {
            return OperationResult.failed(CODE_PROGRAM,
                    "'" + program + "' is not one of the 24 mined auxiliary programs ("
                            + String.join(", ", QEAuxSchema.programs())
                            + ") - refusing instead of drafting against an invented grammar.",
                    null);
        }
        boolean newestResolved = version == null || version.trim().isEmpty();
        String pinnedVersion = newestResolved
                ? QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1)
                : version.trim();
        if (QENamelistSchema.indexOfVersion(pinnedVersion) < 0) {
            return OperationResult.failed(CODE_VERSION,
                    "Unsupported QE version label '" + version + "'; the mined grammar window is "
                            + String.join(", ", QENamelistSchema.VERSIONS)
                            + " - refusing instead of drafting against a different grammar"
                            + " silently.",
                    null);
        }
        List<FieldRow> rows = new ArrayList<>();
        for (QEAuxSchema.Row row : QEAuxSchema.entries(pinnedProgram)) {
            if (row.presentIn(pinnedVersion)) {
                rows.add(new FieldRow(row, pinnedVersion));
            }
        }
        return OperationResult.success(
                newestResolved ? "QEAUX_OK_NEWEST" : "QEAUX_OK",
                rows.size() + " keyword field(s) of " + pinnedProgram
                        + " present in the QE " + pinnedVersion + " grammar window"
                        + (newestResolved ? " (no version pinned; resolved to the newest"
                                + " mined tag)" : "") + ".",
                new QEAuxDeckPlanner(pinnedProgram, pinnedVersion, List.copyOf(rows),
                        QEAuxSchema.namelists(pinnedProgram)));
    }

    /** The pinned auxiliary program label. */
    public String getProgram() { return this.program; }

    /** The pinned QE version label (never null - refusals precede this). */
    public String getVersion() { return this.version; }

    /** Fields present in the pinned window, in mined order. */
    public List<FieldRow> fields() { return this.fields; }

    /** Fields present in the pinned window under one namelist. */
    public List<FieldRow> fields(String namelistName) {
        List<FieldRow> out = new ArrayList<>();
        for (FieldRow row : this.fields) {
            if (row.getNamelist().equalsIgnoreCase(namelistName)) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    /** The program's namelists in mined order (schema-level membership). */
    public List<String> namelists() { return this.namelists; }

    /** The QE doc page of record for this program. */
    public String docPage() { return QEAuxSchema.docPage(this.program); }

    /**
     * Renders a draft deck from keyword assignments (blank values mean
     * "not assigned" and are skipped). Output is deterministic: namelists
     * in mined order, keywords in mined field order inside each namelist.
     * Undeclared or version-absent keywords REFUSE the whole render; a
     * refusal never emits a partially rendered deck.
     */
    public OperationResult<String> renderDraft(Map<String, String> assignments) {
        Objects.requireNonNull(assignments, "assignments");
        Map<String, String> given = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            given.put(entry.getKey().trim(), value.trim());
        }
        given.remove("");
        Map<String, FieldRow> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FieldRow row : this.fields) {
            byName.put(row.getName(), row);
        }
        for (String keyword : given.keySet()) {
            if (given.get(keyword).isEmpty()) {
                continue;
            }
            if (!byName.containsKey(keyword)) {
                List<QEAuxSchema.Row> anywhere = QEAuxSchema.lookup(this.program, keyword);
                if (!anywhere.isEmpty()) {
                    return OperationResult.failed(CODE_KEYWORD_VERSION,
                            keyword + " is declared by " + this.program + " only from QE "
                                    + anywhere.get(0).firstPresentLabel()
                                    + " in the mined window; the pinned " + this.version
                                    + " grammar does not read it - refusing to emit a deck"
                                    + " the " + this.version + " binary would abort on.",
                            null);
                }
                return OperationResult.failed(CODE_KEYWORD,
                        keyword + " is not declared by the mined " + this.program
                                + " grammar at any window tag (" + QEAuxSchema
                                .entries(this.program).size()
                                + " keywords) - never pass an undeclared keyword through:",
                        null);
            }
        }
        StringBuilder text = new StringBuilder();
        int emitted = 0;
        for (String namelist : this.namelists) {
            StringBuilder body = new StringBuilder();
            for (FieldRow row : fields(namelist)) {
                String value = given.get(row.getName());
                if (value == null || value.isEmpty()) {
                    continue;
                }
                body.append("   ").append(row.getName()).append(" = ")
                        .append(formatValue(row, value)).append('\n');
                emitted += 1;
            }
            if (body.length() > 0) {
                text.append('&').append(namelist).append('\n').append(body).append('/')
                        .append('\n');
            }
        }
        if (emitted == 0) {
            return OperationResult.success(CODE_EMPTY,
                    "No assignments; nothing was rendered (an empty deck is a typed"
                            + " status, not an error).",
                    "");
        }
        return OperationResult.success("QEAUX_OK", "Rendered " + emitted + " assignment(s).",
                text.toString());
    }

    /**
     * Renders the draft and adjudicates it through the mined
     * {@link QEAuxDeckAudit} at the pinned version - the ONLY grammar
     * implementation, so the preview verdict can never drift from the
     * results-surface audit. A render refusal propagates as the same code.
     */
    public OperationResult<List<ValidationIssue>> auditDraft(
            Map<String, String> assignments) {
        OperationResult<String> render = renderDraft(assignments);
        if (!render.isSuccess()) {
            return OperationResult.failed(render.getCode(), render.getMessage(), null);
        }
        List<ValidationIssue> issues = new QEAuxDeckAudit()
                .auditDeckText(this.program, render.getValue().orElse(""), this.version);
        return OperationResult.success("QEAUX_OK",
                issues.size() + " audit finding(s) at QE " + this.version + ".", issues);
    }

    /**
     * Mechanical value rendering - the whole quoting rule lives here.
     * Already-quoted values pass verbatim (never double-quoted); declared
     * CHARACTER values are single-quoted with Fortran '' escaping; declared
     * numeric/LOGICAL values pass verbatim; UNKNOWN-typed values (def-less
     * XSpectra family) pass verbatim when they carry an obvious Fortran
     * literal shape, otherwise single-quoted. The audit adjudicates the
     * result - this layer never decides validity.
     */
    public static String formatValue(FieldRow row, String rawValue) {
        String value = rawValue.trim();
        if (value.length() >= 2
                && ((value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')
                || (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'))) {
            return value; // the user quoted it; keep exactly what was quoted
        }
        String type = row.getType().toUpperCase(Locale.ROOT);
        if ("CHARACTER".equals(type)) {
            return "'" + value.replace("'", "''") + "'";
        }
        switch (type) {
            case "INTEGER": case "INT": case "REAL": case "DOUBLE": case "FLOAT":
            case "LOGICAL":
                return value;
            default: // UNKNOWN (def-less family): shape-based, documented
                if (FORTRAN_INTEGER.matcher(value).matches()
                        || FORTRAN_REAL.matcher(value).matches()
                        || FORTRAN_LOGICAL.matcher(value).matches()) {
                    return value;
                }
                return "'" + value.replace("'", "''") + "'";
        }
    }

    /** One promptable keyword field, bound to the pinned window. */
    public static final class FieldRow {
        private final QEAuxSchema.Row row;
        private final String version;
        private final List<String> options;

        private FieldRow(QEAuxSchema.Row row, String version) {
            this.row = row;
            this.version = version;
            List<String> present = new ArrayList<>();
            int index = QENamelistSchema.indexOfVersion(version);
            for (QEAuxSchema.OptionLiteral literal : row.getOptions()) {
                if (index >= 0 && (literal.getVersionMask() & (1 << index)) != 0) {
                    present.add(literal.getValue());
                }
            }
            this.options = List.copyOf(present);
        }

        /** The keyword name, verbatim from the grammar. */
        public String getName() { return this.row.getName(); }

        /** The declaring namelist (upper case, grammar-verbatim). */
        public String getNamelist() { return this.row.getNamelist(); }

        /** Declared type (def -type / source decl / UNKNOWN), never invented. */
        public String getType() { return this.row.getType(); }

        /** Whether the machine grammar marks the keyword REQUIRED. */
        public boolean isRequired() { return this.row.isRequired(); }

        /** Declared default text verbatim, or null when none is declared. */
        public String getDefaultText() { return this.row.getDefaultText(); }

        /**
         * Documented option literals present in the pinned window (SOFT doc
         * layer; empty = no documented literals, never \"everything
         * forbidden\").
         */
        public List<String> getOptions() { return this.options; }

        /** The pinned version this field was filtered against. */
        public String getVersionWindow() { return this.version; }

        /** Compact one-line prompt label for dialog list rows. */
        public String promptLabel() {
            StringBuilder label = new StringBuilder();
            label.append(this.row.getName());
            if (this.row.isRequired()) {
                label.append(" *");
            }
            label.append("  (&").append(this.row.getNamelist()).append(") : ")
                    .append(this.row.getType());
            if (this.row.getDefaultText() != null) {
                label.append("  default=").append(this.row.getDefaultText());
            }
            if (!this.options.isEmpty()) {
                label.append("  {").append(String.join(" | ", this.options)).append('}');
            }
            return label.toString();
        }
    }
}
