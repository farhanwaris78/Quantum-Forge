/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import quantumforge.input.schema.QENamelistSchema;
import quantumforge.operation.OperationResult;

/**
 * Version-windowed keyword prompt catalog for the pw.x/ph.x/hp.x deck surfaces
 * (QE-integration roadmap R3 seam): the typed list of keywords an edit dialog
 * or a deck planner may offer for ONE pinned QE version, built entirely from
 * the mined {@link QENamelistSchema} grammar (QE 7.2-7.6).
 *
 * <p>Honesty rules carried by every row:</p>
 * <ul>
 *   <li>the catalog NEVER falls back to a different version silently - an
 *       unknown version label is a typed refusal ({@code QES_VERSION}) naming
 *       the supported labels; only an explicitly blank request resolves to the
 *       newest mined window, and the resolved label is always exposed so the
 *       caller can name it in whatever it renders;</li>
 *   <li>{@link KeywordRow#getAcceptedValues()} reports only the HARD literals
 *       the SELECTED version's own aborting switch accepts (an empty list
 *       means "no hard ground truth", never "everything forbidden");</li>
 *   <li>declared defaults, REQUIRED flags and drifted-default text are echoed
 *       verbatim from the mined grammar - never rephrased, never invented.</li>
 * </ul>
 */
public final class QEDeckKeywordCatalog {

    private final QENamelistSchema.Kind kind;
    private final String version;
    private final List<KeywordRow> rows;
    private final List<String> namelistNames;

    private QEDeckKeywordCatalog(QENamelistSchema.Kind kind, String version,
            List<KeywordRow> rows, List<String> namelistNames) {
        this.kind = kind;
        this.version = version;
        this.rows = rows;
        this.namelistNames = namelistNames;
    }

    /**
     * Resolves a requested version label to a supported one: null/blank means
     * "newest mined" (the label is returned for the caller to name); anything
     * outside the mined window is a typed refusal - never a silent fallback.
     * Code: QES_VERSION.
     */
    public static OperationResult<String> resolveVersion(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            String newest = QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
            return OperationResult.success("QES_VERSION_NEWEST",
                    "No version pinned; resolved to the newest mined window " + newest + ".",
                    newest);
        }
        String trimmed = requested.trim();
        if (QENamelistSchema.indexOfVersion(trimmed) >= 0) {
            return OperationResult.success("QES_VERSION_OK", "Version pinned.", trimmed);
        }
        return OperationResult.failed("QES_VERSION",
                "Unsupported QE version label '" + trimmed + "'; the mined grammar window is "
                        + String.join(", ", QENamelistSchema.VERSIONS)
                        + " - refusing instead of falling back to a different grammar silently.",
                null);
    }

    /**
     * Builds the catalog for one program kind and one pinned version window.
     * Codes: QES_VERSION (unsupported label), QES_KIND (null kind).
     */
    public static OperationResult<QEDeckKeywordCatalog> forVersion(
            QENamelistSchema.Kind kind, String version) {
        if (kind == null) {
            return OperationResult.failed("QES_KIND",
                    "No QE program kind supplied (pw/ph/hp); an empty kind is never guessed.",
                    null);
        }
        OperationResult<String> resolved = resolveVersion(version);
        if (!resolved.isSuccess()) {
            return OperationResult.failed(resolved.getCode(), resolved.getMessage(), null);
        }
        String pinnedVersion = resolved.getValue().orElseThrow();
        List<KeywordRow> rows = new ArrayList<>();
        List<String> namelists = new ArrayList<>();
        for (QENamelistSchema.Entry entry : QENamelistSchema.entries(kind, pinnedVersion)) {
            rows.add(new KeywordRow(entry, pinnedVersion));
            if (!namelists.contains(entry.getNamelist())) {
                namelists.add(entry.getNamelist());
            }
        }
        return OperationResult.success("QES_CATALOG_OK",
                rows.size() + " keywords of " + kind.getExecutable() + " present in QE "
                        + pinnedVersion + ".",
                new QEDeckKeywordCatalog(kind, pinnedVersion, List.copyOf(rows),
                        List.copyOf(namelists)));
    }

    /** The program this catalog prompts for. */
    public QENamelistSchema.Kind getKind() { return this.kind; }

    /** The resolved version label every row was filtered against. */
    public String getVersion() { return this.version; }

    /** Every promptable keyword row, mined (namelist-grouped) order. */
    public List<KeywordRow> rows() { return this.rows; }

    /** Rows of one namelist (case-insensitive), mined order. */
    public List<KeywordRow> rows(String namelistName) {
        String wanted = namelistName == null ? ""
                : namelistName.trim().toUpperCase(Locale.ROOT);
        List<KeywordRow> result = new ArrayList<>();
        for (KeywordRow row : this.rows) {
            if (row.getNamelist().equalsIgnoreCase(wanted)) {
                result.add(row);
            }
        }
        return List.copyOf(result);
    }

    /** Distinct namelist names present in this window, mined order. */
    public List<String> namelistNames() { return this.namelistNames; }

    /**
     * True when the keyword may be prompted/emitted for this version. This is
     * the single gate both GUI dialogs and deck planners share, so a prompt
     * surface and a drafted deck can never disagree about the grammar window.
     */
    public boolean prompts(String keyword) {
        return QENamelistSchema.lookup(this.kind, keyword)
                .map(entry -> entry.presentIn(this.version)).orElse(false);
    }

    /**
     * The mined window text of a keyword ("7.5-7.6", "7.2-7.4") for refusal
     * messages; null when the keyword is unknown to the grammar entirely.
     */
    public String windowText(String keyword) {
        return QENamelistSchema.lookup(this.kind, keyword)
                .map(QENamelistSchema.Entry::versionRange).orElse(null);
    }

    /** One typed prompt row: a keyword as the selected version sees it. */
    public static final class KeywordRow {
        private final QENamelistSchema.Entry entry;
        private final String version;

        KeywordRow(QENamelistSchema.Entry entry, String version) {
            this.entry = Objects.requireNonNull(entry, "entry");
            this.version = Objects.requireNonNull(version, "version");
        }

        /** Keyword name with source capitalisation preserved. */
        public String getName() { return this.entry.getName(); }

        /** Owning namelist, uppercase as declared ("SYSTEM", "INPUTPH"). */
        public String getNamelist() { return this.entry.getNamelist(); }

        /** Declared Fortran type (UNKNOWN when neither grammar states one). */
        public QENamelistSchema.Type getType() { return this.entry.getType(); }

        /** True for array keywords; {@link #getArrayDims()} carries the dims text. */
        public boolean isArray() { return this.entry.isArray(); }

        /** Declared dimension text ("3, 50") or null for scalars. */
        public String getArrayDims() { return this.entry.getArrayDims(); }

        /** True when the .def grammar marks the keyword REQUIRED. */
        public boolean isRequired() { return this.entry.isRequired(); }

        /** Declared default text verbatim (drift maps bracketed); null when none. */
        public String getDefaultText() { return this.entry.getDefaultText(); }

        /**
         * HARD accepted literals of the SELECTED version only (the binary
         * aborts otherwise; e.g. ph diagonalization 'direct' appears here only
         * for 7.6). Empty = no hard switch for this keyword/version.
         */
        public List<String> getAcceptedValues() {
            return this.entry.getAcceptedValuesIn(this.version);
        }

        /** Documented option literals (advisory; out-of-set is a warning). */
        public List<String> getDocumentedValues() {
            return this.entry.getDocumentedValues();
        }

        /** The full mined presence window of the keyword ("7.2-7.4"). */
        public String getVersionRange() { return this.entry.versionRange(); }

        /** QE's own INPUT_*.html page for this keyword's program. */
        public String getDocsUrl() { return this.entry.getDocsUrl(); }

        /**
         * One-line prompt rendering for GUI list surfaces: name, namelist,
         * type, REQUIRED marker, and the keyword's full mined window so
         * version-gating is visible at the prompt, not just at validation.
         */
        public String promptLabel() {
            StringBuilder label = new StringBuilder();
            label.append(this.entry.getName());
            if (this.entry.isRequired()) {
                label.append(" [REQUIRED]");
            }
            label.append("  (&").append(this.entry.getNamelist()).append(", ")
                    .append(this.entry.getType());
            if (this.entry.getArrayDims() != null) {
                label.append('[').append(this.entry.getArrayDims()).append(']');
            }
            label.append("; window ").append(this.entry.versionRange()).append(')');
            return label.toString();
        }
    }
}
