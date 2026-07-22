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
 *   <li>the per-version window landed in batch 158 (R6 slice 2), announcing
 *       itself here as promised: thermo_pw's OWN release tags 2.0.0 .. 2.1.1
 *       plus the fingerprinted development master form a 7-bit presence mask
 *       per keyword/what/fact (thermo_pw-tag-indexed, never QE-paired);
 *       defaults/types are the NEWEST reading with per-tag drift verbatim.</li>
 * </ul>
 */
public final class QEThermoPwSchema {

    /** The single namelist this grammar covers (file {@code thermo_control}). */
    public static final String NAMELIST_NAME = "INPUT_THERMO";

    /**
     * Version window of this grammar: thermo_pw's OWN release tags, oldest
     * first, with the fingerprinted development master as the newest column
     * (mask bit i corresponds to index i). The window is thermo_pw-tag-indexed
     * and deliberately NOT QE-paired: no thermo_pw source statement pairs the
     * two version lines, so none is claimed here.
     */
    public static final List<String> VERSIONS = List.of(
            "2.0.0", "2.0.1", "2.0.2", "2.0.3", "2.1.0", "2.1.1", "master");

    /** All-window presence mask (7 bits). */
    public static final int ALL_VERSIONS_MASK = (1 << VERSIONS.size()) - 1;

    /** Index into {@link #VERSIONS}; -1 when outside the mined window. */
    public static int indexOfVersion(String version) {
        if (version == null) {
            return -1;
        }
        return VERSIONS.indexOf(version.trim());
    }

    /** First window label at which a mask bit is set ("" when none). */
    public static String firstPresentLabel(int mask) {
        for (int i = 0; i < VERSIONS.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                return VERSIONS.get(i);
            }
        }
        return "";
    }

    /** Last window label at which a mask bit is set ("" when none). */
    public static String lastPresentLabel(int mask) {
        for (int i = VERSIONS.size() - 1; i >= 0; i--) {
            if ((mask & (1 << i)) != 0) {
                return VERSIONS.get(i);
            }
        }
        return "";
    }

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
        private final int versionMask;
        private final Map<String, Drift> driftByVersion;

        Entry(String name, Type type, String defaultText, List<String> groups,
              int versionMask, Map<String, Drift> driftByVersion) {
            this.name = name;
            this.type = type;
            this.defaultText = defaultText;
            this.groups = List.copyOf(groups);
            this.versionMask = versionMask;
            this.driftByVersion = Map.copyOf(driftByVersion);
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

        /** Presence mask across {@link #VERSIONS} (7-bit, newest = master). */
        public int getVersionMask() { return this.versionMask; }

        /** True when this keyword is declared in the given window version. */
        public boolean presentIn(String version) {
            int index = indexOfVersion(version);
            return index >= 0 && (this.versionMask & (1 << index)) != 0;
        }

        /** Per-tag drift rows (verbatim): tags whose reading differs from newest. */
        public Map<String, Drift> getDriftByVersion() { return this.driftByVersion; }

        /**
         * The default ASSIGNED in the given version: the drift row's text when
         * a drift exists there, the newest reading when the tag is inside the
         * window with no drift, null when the keyword or version is outside
         * the window (honest absence).
         */
        public String defaultAt(String version) {
            String label = version == null ? null : version.trim();
            if (label == null || !presentIn(label)) {
                return null;
            }
            Drift drift = this.driftByVersion.get(label);
            if (drift == null) {
                return this.defaultText;
            }
            return drift.hasDefault() ? drift.getDefaultText() : null;
        }

        /** The INFERRED type in the given version (drift-aware); UNKNOWN when absent. */
        public Type typeAt(String version) {
            String label = version == null ? null : version.trim();
            if (label == null || !presentIn(label)) {
                return Type.UNKNOWN;
            }
            Drift drift = this.driftByVersion.get(label);
            return drift == null ? this.type : drift.getType();
        }

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

    /** One tag's deviation from the newest reading (verbatim default, INFERRED type). */
    public static final class Drift {
        private final Type type;
        private final String defaultText;

        Drift(Type type, String defaultText) {
            this.type = type;
            this.defaultText = defaultText;
        }

        /** INFERRED type at this tag (never a declared Fortran type). */
        public Type getType() { return this.type; }

        /** True when the tag assigned a default ("<none>" marks its removal). */
        public boolean hasDefault() { return this.defaultText != null; }

        /** Verbatim default at this tag, null when none was assigned there. */
        public String getDefaultText() { return this.defaultText; }
    }

    private static final List<Entry> ORDERED;
    private static final Map<String, Entry> BY_NAME;
    private static final List<String> WHAT_VALUES;
    private static final Map<String, Integer> WHAT_MASKS;
    private static final List<MaskedFact> FACTS;

    static {
        List<Entry> built = QEThermoPwSchemaData.buildEntries();
        ORDERED = List.copyOf(built);
        Map<String, Entry> byName = new LinkedHashMap<>();
        for (Entry entry : built) {
            byName.put(entry.getName().toLowerCase(Locale.ROOT), entry);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
        List<String> whatValues = new ArrayList<>();
        Map<String, Integer> whatMasks = new LinkedHashMap<>();
        for (String item : QEThermoPwSchemaData.buildWhatAcceptedValues()) {
            int tilde = item.lastIndexOf('~');
            if (tilde < 0) {
                whatValues.add(item);
                whatMasks.put(item, ALL_VERSIONS_MASK);
                continue;
            }
            String value = item.substring(0, tilde);
            int mask;
            try {
                mask = Integer.decode(item.substring(tilde + 1));
            } catch (NumberFormatException bad) {
                mask = ALL_VERSIONS_MASK;
            }
            whatValues.add(value);
            whatMasks.put(value, mask);
        }
        WHAT_VALUES = List.copyOf(whatValues);
        WHAT_MASKS = Collections.unmodifiableMap(whatMasks);
        List<MaskedFact> facts = new ArrayList<>();
        for (String item : QEThermoPwSchemaData.buildConsistencyFacts()) {
            int tilde = item.lastIndexOf('~');
            if (tilde < 0) {
                facts.add(new MaskedFact(item, ALL_VERSIONS_MASK));
                continue;
            }
            int mask;
            try {
                mask = Integer.decode(item.substring(tilde + 1));
            } catch (NumberFormatException bad) {
                mask = ALL_VERSIONS_MASK;
            }
            facts.add(new MaskedFact(item.substring(0, tilde), mask));
        }
        FACTS = List.copyOf(facts);
    }

    private QEThermoPwSchema() { }

    /**
     * Factory used by the generated table only. driftCsv items look like
     * {@code 2.0.0:LOGICAL:.TRUE.} - a tag whose reading differs from the
     * newest; {@code <none>} marks a tag that assigned no default.
     */
    public static Entry entry(String name, Type type, String defaultText,
                              String groupCsv, int versionMask, String driftCsv) {
        List<String> groups = new ArrayList<>();
        if (!groupCsv.isEmpty()) {
            for (String group : groupCsv.split(",", -1)) {
                groups.add(group);
            }
        }
        Map<String, Drift> drift = new LinkedHashMap<>();
        if (driftCsv != null && !driftCsv.isEmpty()) {
            for (String item : driftCsv.split(",", -1)) {
                String[] parts = item.split(":", 3);
                if (parts.length != 3) {
                    continue;
                }
                Type tagType;
                try {
                    tagType = Type.valueOf(parts[1]);
                } catch (IllegalArgumentException unknown) {
                    tagType = Type.UNKNOWN;
                }
                drift.put(parts[0], new Drift(tagType,
                        "<none>".equals(parts[2]) ? null : parts[2]));
            }
        }
        return new Entry(name, type, defaultText, groups, versionMask, drift);
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

    /** One verbatim consistency fact with its per-version presence mask. */
    public static final class MaskedFact {
        private final String text;
        private final int versionMask;

        MaskedFact(String text, int versionMask) {
            this.text = text;
            this.versionMask = versionMask;
        }

        /** Verbatim fact line from thermo_readin.f90. */
        public String getText() { return this.text; }

        /** Presence mask across {@link #VERSIONS}. */
        public int getVersionMask() { return this.versionMask; }

        /** True when this fact exists in the given window version. */
        public boolean presentIn(String version) {
            int index = indexOfVersion(version);
            return index >= 0 && (this.versionMask & (1 << index)) != 0;
        }
    }

    /**
     * HARD accepted literals of the {@code what} master switch - the part-1
     * dispatch ends in errore for anything else (abort, not remap). The
     * union across the window; presence per version via {@link #whatMask(String)}.
     */
    public static List<String> whatAcceptedValues() { return WHAT_VALUES; }

    /** Presence mask of an accepted what value (0 when not in the union). */
    public static int whatMask(String what) {
        return WHAT_MASKS.getOrDefault(what, 0);
    }

    /** True when this what value is accepted by the given window version. */
    public static boolean whatPresentIn(String what, String version) {
        int index = indexOfVersion(version);
        return index >= 0 && (WHAT_MASKS.getOrDefault(what, 0) & (1 << index)) != 0;
    }

    /**
     * Verbatim consistency-check fact lines visible at the NEWEST window
     * column (the fingerprinted master) - back-compat view; per-version
     * filtering lives in {@link #factsForVersion(String)}.
     */
    public static List<String> consistencyFacts() {
        List<String> out = new ArrayList<>();
        for (MaskedFact fact : FACTS) {
            if ((fact.getVersionMask() & (1 << (VERSIONS.size() - 1))) != 0) {
                out.add(fact.getText());
            }
        }
        return List.copyOf(out);
    }

    /** Every mined fact with its mask (bounded). */
    public static List<MaskedFact> maskedFacts() { return FACTS; }

    /** Facts that exist in the given window version, verbatim order. */
    public static List<String> factsForVersion(String version) {
        List<String> out = new ArrayList<>();
        for (MaskedFact fact : FACTS) {
            if (fact.presentIn(version)) {
                out.add(fact.getText());
            }
        }
        return List.copyOf(out);
    }

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

}
