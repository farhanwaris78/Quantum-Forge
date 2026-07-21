/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Machine-mined Quantum ESPRESSO namelist schema: the union of the keyword
 * grammar of QE 7.2, 7.3, 7.4, 7.5 and 7.6 for pw.x, ph.x and hp.x, generated
 * from the projects' own ground-truth grammar files by
 * {@code scripts/qe_schema_miner.py} (see QESchemaData for the exact provenance
 * and sha256 fingerprints of every mined byte).
 *
 * <p>Grounds per fact, stated honestly:</p>
 * <ul>
 *   <li>namelist membership, declared types, declared defaults, REQUIRED flags
 *       and documented option literals come from the {@code INPUT_*.def}
 *       grammar files (the same files QE generates its INPUT_PW.html docs from)
 *       UNION the compilable {@code NAMELIST} declarations in the Fortran
 *       source - the union catches keywords either source lists alone;</li>
 *   <li>HARD accepted-value sets come from the programs' own {@code SELECT CASE}
 *       validation switches whose DEFAULT arm calls {@code errore()} - the
 *       binary STOPS on out-of-set values (Fortran string CASE is exact match,
 *       and the sets are mined per version because they drift: {@code
 *       diagonalization='direct'} joined at 7.6, the uppercase {@code PPCG}/{@code
 *       ParO} aliases exist only from 7.5);</li>
 *   <li>SOFT value sets are switches whose DEFAULT silently remaps or a
 *       DEFAULT-free pass-through, PLUS the .def documented options - the
 *       binary tolerates out-of-set values there, so those are advisory only;</li>
 *   <li>a {@code SELECT CASE} whose arms cannot be enumerated as literals
 *       (ranges, expressions) is dropped rather than guessed;</li>
 *   <li>conditional runtime rules (k-grids, nat/ntyp cross-checks, card
 *       content) live outside this table - this is a keyword grammar.</li>
 * </ul>
 *
 * <p>Descriptions are not vendored: the per-program docs URL points at QE's own
 * online INPUT_*.html pages (which the mined {@code .def} files generate), and
 * {@link Entry#getDescription()} only ever carries a synthesized fact line,
 * never prose copied from QE documentation.</p>
 */
public final class QENamelistSchema {

    /** Supported QE minor versions, oldest first; mask bit i corresponds to entry i. */
    public static final List<String> VERSIONS = List.of("7.2", "7.3", "7.4", "7.5", "7.6");

    /** Mask with every supported version bit set. */
    public static final int ALL_VERSIONS_MASK = (1 << VERSIONS.size()) - 1;

    private static final String DOCS_ROOT = "https://www.quantum-espresso.org/Doc/";

    /** QE's own online pw.x input-docs page. */
    public static final String INPUT_PW_URL = DOCS_ROOT + "INPUT_PW.html";

    /** QE's own online ph.x input-docs page. */
    public static final String INPUT_PH_URL = DOCS_ROOT + "INPUT_PH.html";

    /** QE's own online hp.x input-docs page. */
    public static final String INPUT_HP_URL = DOCS_ROOT + "INPUT_HP.html";

    /** The three mined executables plus their documentation pages. */
    public enum Kind {
        PW("pw.x", INPUT_PW_URL),
        PH("ph.x", INPUT_PH_URL),
        HP("hp.x", INPUT_HP_URL);

        private final String executable;
        private final String docsUrl;

        Kind(String executable, String docsUrl) {
            this.executable = executable;
            this.docsUrl = docsUrl;
        }

        public String getExecutable() { return this.executable; }

        /** QE's own online input-docs page for this program. */
        public String getDocsUrl() { return this.docsUrl; }

        /**
         * Parses a program token ("pw", "pw.x", "ph", "ph.x", "hp", "hp.x";
         * case-insensitive, whitespace-tolerant). Anything else is unknown and
         * stays unknown - callers never fall back to guessing a program.
         */
        public static Optional<Kind> parse(String token) {
            if (token == null) {
                return Optional.empty();
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".x")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            switch (normalized) {
            case "pw":
                return Optional.of(PW);
            case "ph":
                return Optional.of(PH);
            case "hp":
                return Optional.of(HP);
            default:
                return Optional.empty();
            }
        }
    }

    /** Declared Fortran type of a keyword (UNKNOWN = neither grammar states one). */
    public enum Type {
        CHARACTER, INTEGER, REAL, LOGICAL,
        /** QE .def STRUCTURE pseudo-type (ph.x '*_star' helper structures, a small
         *  fixed keyword with its own value grammar - not a scalar Fortran type). */
        STRUCTURE,
        UNKNOWN
    }

    /** One mined literal (HARD-accepted or SOFT-tolerated) with its presence mask. */
    public static final class AcceptedValue {
        private final String literal;
        private final int versionMask;

        AcceptedValue(String literal, int versionMask) {
            this.literal = literal;
            this.versionMask = versionMask;
        }

        /** The literal verbatim as mined from the SELECT CASE arm (case matters). */
        public String getLiteral() { return this.literal; }
        public int getVersionMask() { return this.versionMask; }

        public boolean presentIn(int versionIndex) {
            return versionIndex >= 0 && versionIndex < VERSIONS.size()
                    && (this.versionMask & (1 << versionIndex)) != 0;
        }
    }

    /** One mined keyword of one program's namelist grammar. */
    public static final class Entry {
        private final Kind kind;
        private final String namelist;
        private final String name;
        private final Type type;
        private final String arrayDims;
        private final boolean required;
        private final String defaultText;
        private final int versionMask;
        private final List<String> docOptions;
        private final List<AcceptedValue> acceptedValues;
        private final List<AcceptedValue> toleratedValues;
        private final List<String> documentedCache;

        Entry(Kind kind, String namelist, String name, Type type, String arrayDims,
                boolean required, String defaultText, int versionMask, List<String> docOptions,
                List<AcceptedValue> acceptedValues, List<AcceptedValue> toleratedValues) {
            this.kind = kind;
            this.namelist = namelist;
            this.name = name;
            this.type = type;
            this.arrayDims = arrayDims;
            this.required = required;
            this.defaultText = defaultText;
            this.versionMask = versionMask;
            this.docOptions = List.copyOf(docOptions);
            this.acceptedValues = List.copyOf(acceptedValues);
            this.toleratedValues = List.copyOf(toleratedValues);
            List<String> documented = new ArrayList<>();
            for (AcceptedValue tolerated : toleratedValues) {
                if (!documented.contains(tolerated.getLiteral())) {
                    documented.add(tolerated.getLiteral());
                }
            }
            for (String option : this.docOptions) {
                if (!documented.contains(option)) {
                    documented.add(option);
                }
            }
            this.documentedCache = List.copyOf(documented);
        }

        public Kind getKind() { return this.kind; }

        /** Owning namelist name, uppercase as declared (e.g. "SYSTEM", "INPUTPH"). */
        public String getNamelist() { return this.namelist; }

        /** Keyword name with its original source capitalisation preserved. */
        public String getName() { return this.name; }
        public Type getType() { return this.type; }

        /** Declared dimension text ("nsx", "6", "3, 50") for arrays; null for scalars. */
        public String getArrayDims() { return this.arrayDims; }
        public boolean isArray() { return this.arrayDims != null; }

        /** True when the .def grammar marks the keyword status{REQUIRED}. */
        public boolean isRequired() { return this.required; }

        /**
         * Declared default text (verbatim grammar fragment, e.g. "'scf'", "1.D-6",
         * "'4 * ecutwfc'"); null when the grammar declares none. A bracketed
         * "[7.2: ...; 7.6: ...]" rendering flags a default that DRIFTED between
         * versions (the map is the honest answer, not a formatting quirk).
         */
        public String getDefaultText() { return this.defaultText; }
        public int getVersionMask() { return this.versionMask; }

        /** Documented option literals from the .def options block only. */
        public List<String> getDocOptions() { return this.docOptions; }

        /**
         * HARD accepted literals (the binary aborts otherwise), window union,
         * source order. Empty for free-form, range-checked, and soft-switched
         * keywords alike - callers use {@link #getDocumentedValues()} for the
         * advisory side.
         */
        public List<String> getAcceptedValues() {
            return literals(this.acceptedValues);
        }

        /** HARD accepted literals with their per-version presence masks. */
        public List<AcceptedValue> getAcceptedValueDetails() { return this.acceptedValues; }

        /**
         * The HARD literals the selected version's own switch accepts. An empty
         * result means no hard ground truth for that version (not "everything
         * forbidden") - callers must not escalate on an empty set.
         */
        public List<String> getAcceptedValuesIn(String version) {
            return literalsIn(this.acceptedValues, version);
        }

        /**
         * SOFT-tolerated literals (the binary silently remaps or passes them
         * through) with their presence masks; empty when no switch was mined or
         * the keyword is hard-switched everywhere.
         */
        public List<AcceptedValue> getToleratedValueDetails() { return this.toleratedValues; }

        /**
         * Options the input is TOLERATED to use without stopping: the mined
         * soft-switch literals followed by any additional .def documented
         * options, in that order. An out-of-set value here is a warning-level
         * review point, never a stop.
         */
        public List<String> getDocumentedValues() { return this.documentedCache; }

        /**
         * QE's own online docs page for this keyword's program. The schema
         * deliberately carries no copied prose - the upstream page is the
         * description of record.
         */
        public String getDocsUrl() { return this.kind.getDocsUrl(); }

        /**
         * Synthesized fact line (never copied prose): set only when the declared
         * default text drifts between the mined versions, to flag that pinning a
         * single default would be wrong. Null otherwise.
         */
        public String getDescription() {
            if (this.defaultText != null && this.defaultText.startsWith("[")) {
                return "the declared default text differs between the mined QE versions";
            }
            return null;
        }

        /** Exact-match verdict against the HARD window union (quotes stripped first). */
        public boolean acceptsHardValue(String rawValue) {
            return containsExact(getAcceptedValues(), stripQuotes(rawValue));
        }

        /**
         * Exact-match verdict against the selected version's HARD set. A version
         * with no mined hard literals can only say "unknown", never "rejected",
         * so this returns true for every value when the version set is empty.
         */
        public boolean acceptsHardValueIn(String rawValue, String version) {
            List<String> accepted = getAcceptedValuesIn(version);
            return accepted.isEmpty() || containsExact(accepted, stripQuotes(rawValue));
        }

        /** Exact-match verdict against {@link #getDocumentedValues()} (quotes stripped). */
        public boolean inDocumentedValues(String rawValue) {
            return containsExact(this.documentedCache, stripQuotes(rawValue));
        }

        public boolean presentIn(int versionIndex) {
            return versionIndex >= 0 && versionIndex < VERSIONS.size()
                    && (this.versionMask & (1 << versionIndex)) != 0;
        }

        public boolean presentIn(String version) {
            return presentIn(indexOfVersion(version));
        }

        /** First supported version carrying the keyword ("7.2" if it always existed). */
        public String addedIn() {
            for (int i = 0; i < VERSIONS.size(); i++) {
                if (presentIn(i)) {
                    return VERSIONS.get(i);
                }
            }
            return "?";
        }

        /** Last supported version carrying the keyword. */
        public String lastPresentIn() {
            for (int i = VERSIONS.size() - 1; i >= 0; i--) {
                if (presentIn(i)) {
                    return VERSIONS.get(i);
                }
            }
            return "?";
        }

        /**
         * The supported versions carrying this keyword as contiguous ranges:
         * "7.2-7.6", "7.2-7.4", "7.4-7.6", "7.5" (comma-joined if fragmented).
         */
        public String versionRange() {
            List<String> ranges = new ArrayList<>();
            int start = -1;
            for (int i = 0; i <= VERSIONS.size(); i++) {
                boolean present = i < VERSIONS.size() && presentIn(i);
                if (present && start < 0) {
                    start = i;
                } else if (!present && start >= 0) {
                    ranges.add(start == i - 1 ? VERSIONS.get(start)
                            : VERSIONS.get(start) + "-" + VERSIONS.get(i - 1));
                    start = -1;
                }
            }
            return String.join(", ", ranges);
        }

        private static List<String> literals(List<AcceptedValue> values) {
            List<String> out = new ArrayList<>(values.size());
            for (AcceptedValue value : values) {
                if (!out.contains(value.getLiteral())) {
                    out.add(value.getLiteral());
                }
            }
            return List.copyOf(out);
        }

        private static List<String> literalsIn(List<AcceptedValue> values, String version) {
            int index = indexOfVersion(version);
            List<String> out = new ArrayList<>();
            for (AcceptedValue value : values) {
                if (value.presentIn(index) && !out.contains(value.getLiteral())) {
                    out.add(value.getLiteral());
                }
            }
            return List.copyOf(out);
        }

        private static boolean containsExact(List<String> literals, String value) {
            return value != null && literals.contains(value);
        }

        private static String stripQuotes(String rawValue) {
            if (rawValue == null) {
                return null;
            }
            String value = rawValue.trim();
            if (value.length() >= 2
                    && ((value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')
                    || (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }

    private static final Map<Kind, Map<String, Entry>> BY_KIND;
    private static final Map<Kind, List<Entry>> ORDERED;

    static {
        Map<Kind, Map<String, Entry>> byKind = new EnumMap<>(Kind.class);
        Map<Kind, List<Entry>> ordered = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            byKind.put(kind, new LinkedHashMap<>());
            ordered.put(kind, new ArrayList<>());
        }
        for (Entry entry : QESchemaData.buildEntries()) {
            byKind.get(entry.getKind()).put(
                    entry.getName().toLowerCase(Locale.ROOT), entry);
            ordered.get(entry.getKind()).add(entry);
        }
        Map<Kind, Map<String, Entry>> frozenByKind = new EnumMap<>(Kind.class);
        Map<Kind, List<Entry>> frozenOrdered = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            frozenByKind.put(kind, Collections.unmodifiableMap(byKind.get(kind)));
            frozenOrdered.put(kind, List.copyOf(ordered.get(kind)));
        }
        BY_KIND = frozenByKind;
        ORDERED = frozenOrdered;
    }

    private QENamelistSchema() {
    }

    /** Factory used by the generated table only. */
    public static Entry entry(String kind, String namelist, String name, Type type,
            String arrayDims, boolean required, String defaultText, int versionMask,
            String docOptionsCsv, String hardCsv, String softCsv) {
        Kind owner;
        switch (kind) {
        case "pw":
            owner = Kind.PW;
            break;
        case "ph":
            owner = Kind.PH;
            break;
        case "hp":
            owner = Kind.HP;
            break;
        default:
            throw new IllegalArgumentException("unknown program token in generated table: "
                    + kind);
        }
        List<String> docOptions = new ArrayList<>();
        if (!docOptionsCsv.isEmpty()) {
            for (String option : docOptionsCsv.split(",", -1)) {
                docOptions.add(option);
            }
        }
        return new Entry(owner, namelist, name, type, arrayDims, required, defaultText,
                versionMask, docOptions, parseMasked(hardCsv), parseMasked(softCsv));
    }

    private static List<AcceptedValue> parseMasked(String csv) {
        List<AcceptedValue> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String item : csv.split(",", -1)) {
            int tilde = item.indexOf('~');
            if (tilde >= 0) {
                out.add(new AcceptedValue(item.substring(0, tilde),
                        Integer.decode(item.substring(tilde + 1))));
            } else {
                out.add(new AcceptedValue(item, ALL_VERSIONS_MASK));
            }
        }
        return out;
    }

    /**
     * Case-insensitive keyword lookup; a trailing Fortran index suffix
     * ("hubbard_u(2)", "starting_ns_eigenvalue(2,3)") is stripped to the array
     * base name before lookup. Unknown stays unknown.
     */
    public static Optional<Entry> lookup(Kind kind, String keyword) {
        Objects.requireNonNull(kind, "kind");
        String base = baseKeyword(keyword);
        if (base == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KIND.get(kind).get(base));
    }

    /**
     * Strips a parenthesised index suffix and lowercases; returns null for a
     * blank or syntactically broken keyword token (never throws on user text).
     */
    public static String baseKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        int paren = trimmed.indexOf('(');
        if (paren >= 0) {
            if (!trimmed.endsWith(")")) {
                return null; // broken index syntax - the validator names it instead
            }
            trimmed = trimmed.substring(0, paren).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** Every mined keyword of the program, in mined (namelist-grouped) order. */
    public static List<Entry> entries(Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return ORDERED.get(kind);
    }

    /** Mined keywords of the program present in the requested version label. */
    public static List<Entry> entries(Kind kind, String version) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(version, "version");
        int index = indexOfVersion(version);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ORDERED.get(kind)) {
            if (entry.presentIn(index)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /** Mined keywords of one namelist of the program (case-insensitive name). */
    public static List<Entry> namelist(Kind kind, String namelistName) {
        Objects.requireNonNull(kind, "kind");
        String wanted = namelistName == null ? ""
                : namelistName.trim().toUpperCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ORDERED.get(kind)) {
            if (entry.getNamelist().equalsIgnoreCase(wanted)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Total mined keyword count across programs (test-pinned). */
    public static int entryCount() {
        int total = 0;
        for (Kind kind : Kind.values()) {
            total += ORDERED.get(kind).size();
        }
        return total;
    }

    /** Index into {@link #VERSIONS}; -1 when outside the supported window. */
    public static int indexOfVersion(String version) {
        return version == null ? -1 : VERSIONS.indexOf(version.trim());
    }
}
