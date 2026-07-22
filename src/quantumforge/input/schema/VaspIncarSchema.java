/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Roadmap #111 (VASP plugin, input side; batch 173): the mined INCAR tag
 * grammar - name, type, pinned options, verbatim-pinned default text and a
 * one-line description, per tag, for the workflow-critical tier-1 window,
 * with a tier-2 name-recognition window behind it.
 *
 * <p><b>Provenance (pinned, read-only):</b> every tier-1 row was extracted
 * from the vasp.at VASP Wiki (MediaWiki API raw wikitext, {@code {{TAGDEF}}}
 * + {@code {{DEF}}} templates and the Description paragraph of each tag
 * page) on 2026-07-22; the wiki covers VASP 6.x (6.4/6.5 era pages). The
 * tier-2 name set is transcribed from the wiki's own
 * {@code Category:INCAR tag} index, first page (200 of 614 tags listed -
 * names on later index pages simply fall into the same INFO/WARNING ladder
 * as unlisted names; the audit states the window verbatim). Nothing was
 * invented: tags whose default the wiki does NOT state carry an EMPTY
 * default text and the audit never prints a fabricated one.</p>
 *
 * <p><b>Honest scope:</b> the wiki documents how VASP interprets INCAR; it
 * is not the code. Conditional defaults (e.g. {@code ISTART} = 1 'if a
 * WAVECAR file exists') are pinned as TEXT, never evaluated. POTCAR is
 * VASP's licensed file: the schema REFERENCES its ENMAX/LEXCH facts exactly
 * as the wiki states them and never bundles or republishes any POTCAR
 * content.</p>
 */
public final class VaspIncarSchema {

    /** The wiki window this schema was pinned against (date + pages). */
    public static final String WIKI_WINDOW =
            "vasp.at/wiki (VASP 6.x tag pages via the MediaWiki API raw wikitext,"
            + " TAGDEF/DEF templates; window fetched 2026-07-22)";

    /** Docs URL builder (audit issues carry it like the thermo_pw audit). */
    public static String wikiUrl(String tagName) {
        return "https://www.vasp.at/wiki/index.php/" + tagName;
    }

    /** Value grammar class a tag belongs to (mirrors the wiki TAGDEF types). */
    public enum TagType {
        /** Plain integer ('[integer]' or an options list of ints). */
        INTEGER,
        /** Plain real ('[real]', '[positive real]'). */
        REAL,
        /** .TRUE./.FALSE. (T/F aliases accepted). */
        LOGICAL,
        /** Free string (SYSTEM, GGA, METAGGA, ALGO ...). */
        STRING,
        /**
         * Enumerated string/logical hybrid (PREC, LREAL): a pinned option
         * set including logical spellings and letter aliases.
         */
        FLAGS,
        /** Whitespace-separated real array; 'N*x' repetition syntax allowed. */
        REAL_ARRAY,
        /** Whitespace-separated integer array; 'N*x' repetition allowed. */
        INTEGER_ARRAY
    }

    /** One tier-1 catalogued INCAR tag. */
    public static final class Entry {
        private final String name;
        private final TagType type;
        private final List<String> options;
        private final String defaultText;
        private final String docLine;
        private final String unit;
        private final String family;

        Entry(String name, TagType type, List<String> options, String defaultText,
                String docLine, String unit, String family) {
            this.name = name;
            this.type = type;
            this.options = options;
            this.defaultText = defaultText;
            this.docLine = docLine;
            this.unit = unit;
            this.family = family;
        }

        /** Tag name, always uppercase (VASP canonicalizes to uppercase). */
        public String getName() { return this.name; }
        /** Wiki-TAGDEF value class. */
        public TagType getType() { return this.type; }
        /** Pinned option set (empty when the wiki constrains none). */
        public List<String> getOptions() { return this.options; }
        /**
         * Verbatim default text from the wiki's DEF template (EMPTY when the
         * wiki does not pin one - never a fabrication).
         */
        public String getDefaultText() { return this.defaultText; }
        /** One-line description quoted/paraphrased from the wiki lead. */
        public String getDocLine() { return this.docLine; }
        /** Named unit when the wiki states one (eV, fs, A^-1, eV/A), else empty. */
        public String getUnit() { return this.unit; }
        /** Coarse family bucket for census output (SCF / IONS / DFT+U / ...). */
        public String getFamily() { return this.family; }
    }

    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();

    static {
        for (String[] row : VaspIncarSchemaData.TIER1_ROWS) {
            Entry entry = new Entry(row[0], TagType.valueOf(row[1]),
                    row[2].isEmpty() ? List.of() : List.of(row[2].split("\\|")),
                    row[3], row[4], row[5], row[6]);
            BY_NAME.put(entry.getName(), entry);
        }
    }

    private VaspIncarSchema() { }

    /** All tier-1 entries, in catalogue order. */
    public static List<Entry> entries() {
        return new ArrayList<>(BY_NAME.values());
    }

    /** Tier-1 catalogue size (pinned by tests + compile_check). */
    public static int entryCount() {
        return BY_NAME.size();
    }

    /** Tier-1 lookup; the name is uppercase-normalized first. */
    public static Optional<Entry> lookup(String tagName) {
        if (tagName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(normalize(tagName)));
    }

    /** Tier-2 name-recognition window (wiki index page 1 of 4). */
    public static boolean isRecognizedName(String tagName) {
        if (tagName == null) {
            return false;
        }
        return VaspIncarSchemaData.TIER2_NAMES.contains(normalize(tagName));
    }

    /** 1 = fully catalogued, 2 = name recognized on the wiki window, 0 = alien. */
    public static int tierOf(String tagName) {
        if (tagName == null) {
            return 0;
        }
        String normalized = normalize(tagName);
        if (BY_NAME.containsKey(normalized)) {
            return 1;
        }
        return VaspIncarSchemaData.TIER2_NAMES.contains(normalized) ? 2 : 0;
    }

    /** Uppercase + trim; nested prefixes keep their '/' (wiki nested tags). */
    public static String normalize(String tagName) {
        return tagName.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
