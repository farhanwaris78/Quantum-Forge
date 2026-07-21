/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Machine-mined thermo_pw &INPUT_THERMO grammar (QE-integration roadmap R6),
 * generated from the thermo_pw project's own Fortran ground truth by
 * {@code scripts/thermo_pw_schema_miner.py} (see QEThermoPwSchemaData for the
 * commit and sha256 fingerprints of every mined byte).
 *
 * <p>Honesty rules carried by this schema:</p>
 * <ul>
 *   <li>the keyword set and its group labels come verbatim from the NAMELIST
 *       declaration in {@code thermo_readin.f90} - the groups are the code's
 *       own bookkeeping comments ("mur_lc", "scf_disp", ...), advisory
 *       provenance, NOT a binding which-keywords-work-with-which-what spec;</li>
 *   <li>declared defaults are the procedural assignments of the defaults
 *       section, last-assignment-wins (execution order - some keywords are
 *       assigned twice by group); array keywords carry NO dimension grammar
 *       (the miner does not invent one);</li>
 *   <li>{@link #whatAcceptedValues()} is a HARD set: the part-1 dispatch
 *       ({@code initialize_thermo_work.f90}) ends in CASE DEFAULT -&gt;
 *       errore('what not recognized'), so an out-of-set value aborts the
 *       binary - mirrored as ERROR severity by the audit;</li>
 *   <li>an UNKNOWN namelist keyword is likewise FATAL
 *       (READ(iun_thermo, input_thermo...) -&gt; errore) - the audit reports
 *       it as ERROR, never a warning;</li>
 *   <li>unlike the pw/ph/hp grammar there is no per-version window HERE: the
 *       single mined grammar carries commit provenance instead, and any
 *       version-window layer must be announced as such when it lands.</li>
 * </ul>
 */
public final class QEThermoPwSchema {

    /** The single namelist this grammar covers (file {@code thermo_control}). */
    public static final String NAMELIST_NAME = "INPUT_THERMO";

    /** Inferred-from-default literal types (never a declared Fortran type). */
    public enum Type { CHARACTER, INTEGER, REAL, LOGICAL, UNKNOWN }

    /** thermo_py's own online documentation index (user guide of record). */
    public static final String DOCS_URL =
            "https://github.com/dalcorso/thermo_pw/blob/master/Doc/user_guide.tex";

    /** One mined keyword of the &INPUT_THERMO grammar. */
    public static final class Entry {
        private final String name;
        private final Type type;
        private final String defaultText;
        private final List<String> groups;

        Entry(String name, Type type, String defaultText, List<String> groups) {
            this.name = name;
            this.type = type;
            this.defaultText = defaultText;
            this.groups = List.copyOf(groups);
        }

        /** Keyword name, lowercase as declared in thermo_readin.f90. */
        public String getName() { return this.name; }

        /** Type INFERRED from the procedural default literal (stated honestly). */
        public Type getType() { return this.type; }

        /**
         * Verbatim procedural default ("1.0_DP", "'output_band.dat'", ".TRUE.");
         * null when the defaults section assigns none. Some keywords receive
         * different defaults per what; the code's execution order wins
         * (documented in QEThermoPwSchemaData's header).
         */
        public String getDefaultText() { return this.defaultText; }

        /** The code's own group-header comments above this keyword, verbatim. */
        public List<String> getGroups() { return this.groups; }

        /** True when the keyword's group labels mention the what value. */
        public boolean groupedUnder(String what) {
            if (what == null) {
                return false;
            }
            String wanted = what.trim().toLowerCase(Locale.ROOT);
            for (String group : this.groups) {
                if (group.toLowerCase(Locale.ROOT).contains(wanted)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final List<Entry> ORDERED;
    private static final Map<String, Entry> BY_NAME;
    private static final List<String> WHAT_VALUES;

    static {
        List<Entry> built = QEThermoPwSchemaData.buildEntries();
        ORDERED = List.copyOf(built);
        Map<String, Entry> byName = new LinkedHashMap<>();
        for (Entry entry : built) {
            byName.put(entry.getName().toLowerCase(Locale.ROOT), entry);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
        WHAT_VALUES = List.copyOf(QEThermoPwSchemaData.buildWhatAcceptedValues());
    }

    private QEThermoPwSchema() { }

    /** Factory used by the generated table only. */
    public static Entry entry(String name, Type type, String defaultText, String groupCsv) {
        List<String> groups = new ArrayList<>();
        if (!groupCsv.isEmpty()) {
            for (String group : groupCsv.split(",", -1)) {
                groups.add(group);
            }
        }
        return new Entry(name, type, defaultText, groups);
    }

    /** Every mined keyword in declaration order (as in thermo_readin.f90). */
    public static List<Entry> entries() { return ORDERED; }

    /** Case-insensitive lookup; unknown stays unknown. */
    public static Optional<Entry> lookup(String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        String base = keyword.trim();
        int paren = base.indexOf('(');
        if (paren > 0 && base.endsWith(")")) {
            base = base.substring(0, paren).trim();
        }
        return Optional.ofNullable(BY_NAME.get(base.toLowerCase(Locale.ROOT)));
    }

    /** Mined keyword count (test-pinned at the generated table). */
    public static int entryCount() { return ORDERED.size(); }

    /**
     * HARD accepted literals of the {@code what} master switch - the part-1
     * dispatch ends in errore for anything else (abort, not remap).
     */
    public static List<String> whatAcceptedValues() { return WHAT_VALUES; }

    /**
     * Keywords whose group labels mention the given what value. This is the
     * code's OWN bookkeeping grouping exposed as an advisory navigation aid -
     * it is NOT a validation law and must not be used to refuse a keyword.
     */
    public static List<Entry> entriesGroupedUnder(String what) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ORDERED) {
            if (entry.groupedUnder(what)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /** Verbatim consistency-check facts mined from thermo_readin (bounded list). */
    public static List<String> consistencyFacts() {
        return QEThermoPwSchemaData.buildConsistencyFacts();
    }
}
