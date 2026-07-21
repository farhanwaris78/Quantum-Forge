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
 * Machine-mined pw.x CARD grammar (QE-integration roadmap R4), generated
 * from the QE project's own Fortran ground truth by
 * {@code scripts/qe_card_schema_miner.py} over Modules/read_cards.f90 at
 * tags qe-7.2 .. qe-7.6 (commit/tag sha256 fingerprints are in the header
 * of {@link QECardSchemaData}).
 *
 * <p>Honesty rules carried by this schema:</p>
 * <ul>
 *   <li>the DISPATCH chain is the verbatim
 *       {@code ELSEIF ( trim(card) == 'X' ) THEN} sequence, including the
 *       prog gates ({@code WANNIER_AC .and. ( prog == 'WA' )},
 *       {@code SOLVENTS .AND. trism}) and the two removed cards DIPOLE/ESR
 *       whose arms call errore 'no longer existing';</li>
 *   <li>the dispatch ELSE fallback is a bare
 *       {@code WRITE 'Warning: card ... ignored'} - an UNKNOWN card name is
 *       TOLERATED AND IGNORED by pw.x, never a read error, so the audit
 *       reports it as WARNING (informative), not ERROR;</li>
 *   <li>an input_line is pre-capitalised before dispatch, so option
 *       matching is case-insensitive in practice (HUBBARD even uses
 *       imatches); cards without an option switch NEVER inspect their
 *       option text ({@link Disposition#IGNORED});</li>
 *   <li>option ELSE-arm dispositions are read straight off the mined arm:
 *       FATAL (errore 'unknown option for X'), TOLERATED_DEFAULT
 *       (DEPRECATED infomsg + default), TOLERATED_SILENT_DEFAULT (plain
 *       default assignment); cards that discriminate the bare card name
 *       carry a separate {@link CardGrammar#getBareDisposition()};</li>
 *   <li>HUBBARD sanity traps ({@code -ATOMIC} suffix-misspell and the
 *       dash-less ORTHOATOMIC/NORMATOMIC) abort with
 *       'Wrong name of the Hubbard projectors' - mined as trap literals,
 *       adjudicated ERROR by the audit;</li>
 *   <li>per-literal presence across qe-7.2..qe-7.6 is the same 5-bit mask
 *       scheme as the namelist schema; content rules beyond the mined facts
 *       stay with QEInputValidator and are NOT duplicated here.</li>
 * </ul>
 */
public final class QECardSchema {

    /** Input_card.html of record (option documentation). */
    public static final String INPUT_CARD_URL =
            "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm1406";

    /**
     * ELSE-arm disposition of an option-bearing card, mirroring exactly
     * what read_cards.f90 does with an unrecognised option string.
     */
    public enum Disposition {
        /** ELSE arm calls errore: pw.x aborts. */
        FATAL,
        /** ELSE arm warns DEPRECATED and assigns a default: abort never. */
        TOLERATED_DEFAULT,
        /** ELSE arm assigns a default with no message at all. */
        TOLERATED_SILENT_DEFAULT,
        /** No discrimination: anything unrecognised passes without a message. */
        TOLERATED_SILENT,
        /** Content-only card: the option text is never even read. */
        IGNORED,
        /** The reader subroutine was not found in a tag (honest absence). */
        UNKNOWN
    }

    /** One row of the read_cards dispatch chain (verbatim condition). */
    public static final class Dispatch {
        private final String card;
        private final String condition;
        private final boolean killed;
        private final String warnProg;
        private final int versionMask;

        Dispatch(String card, String condition, boolean killed,
                 String warnProg, int versionMask) {
            this.card = card;
            this.condition = condition;
            this.killed = killed;
            this.warnProg = warnProg == null ? "" : warnProg;
            this.versionMask = versionMask;
        }

        /** Card literal as dispatched (uppercase, e.g. ATOMIC_POSITIONS). */
        public String getCard() { return this.card; }

        /** Verbatim ELSEIF condition, incl. prog gates when mined. */
        public String getCondition() { return this.condition; }

        /** True when matching this card ABORTS (DIPOLE/ESR: no longer existing). */
        public boolean isKilled() { return this.killed; }

        /**
         * The prog under which this arm writes 'Warning: card ... ignored'
         * (K_POINTS warns only under CP, KSOUT under PW), or "" when the arm
         * has no ignored-warning. "ANY" = the arm warns unconditionally.
         */
        public String getWarnProg() { return this.warnProg; }

        /** Presence mask across {@link QENamelistSchema#VERSIONS} (bit i = index i). */
        public int getVersionMask() { return this.versionMask; }

        /** True when this card exists for the given QE version string. */
        public boolean presentIn(String version) {
            int index = QENamelistSchema.indexOfVersion(version);
            return index >= 0 && index < QENamelistSchema.VERSIONS.size()
                    && (this.versionMask & (1 << index)) != 0;
        }
    }

    /** One option-bearing card's mined grammar (literals, traps, suffixes). */
    public static final class CardGrammar {
        private final String card;
        private final Disposition disposition;
        private final int dispositionMask;
        private final Disposition bareDisposition;
        private final int bareMask;
        private final String note;
        private final List<ChainEntry> chain;
        private final List<PresenceValue> suffixes;

        CardGrammar(String card, Disposition disposition, int dispositionMask,
                    Disposition bareDisposition, int bareMask, String note,
                    List<ChainEntry> chain, List<PresenceValue> suffixes) {
            this.card = card;
            this.disposition = disposition;
            this.dispositionMask = dispositionMask;
            this.bareDisposition = bareDisposition;
            this.bareMask = bareMask;
            this.note = note;
            this.chain = List.copyOf(chain);
            this.suffixes = List.copyOf(suffixes);
        }

        /** Card literal (uppercase). */
        public String getCard() { return this.card; }

        /** What pw.x does with an unrecognised NON-bare option string. */
        public Disposition getDisposition() { return this.disposition; }

        /** Presence mask of the disposition reading across versions. */
        public int getDispositionMask() { return this.dispositionMask; }

        /** What pw.x does with the BARE card name (no option at all). */
        public Disposition getBareDisposition() { return this.bareDisposition; }

        /** Presence mask of the bare reading across versions. */
        public int getBareMask() { return this.bareMask; }

        /** Human-readable mined justification (verbatim errore text or default). */
        public String getNote() { return this.note; }

        /** Mined IF-chain in arm order (options and traps interleaved). */
        public List<ChainEntry> getChain() { return this.chain; }

        /** Mined accepted option literals (chain order). */
        public List<PresenceValue> getOptions() {
            List<PresenceValue> out = new ArrayList<>();
            for (ChainEntry entry : this.chain) {
                if (!entry.isTrap()) {
                    out.add(entry.getLiteral());
                }
            }
            return List.copyOf(out);
        }

        /** Mined trap literals (chain order; HUBBARD sanity checks). */
        public List<PresenceValue> getTraps() {
            List<PresenceValue> out = new ArrayList<>();
            for (ChainEntry entry : this.chain) {
                if (entry.isTrap()) {
                    out.add(entry.getLiteral());
                }
            }
            return List.copyOf(out);
        }

        /** Mined suffix flags (K_POINTS _B/_C) - never standalone options. */
        public List<PresenceValue> getSuffixes() { return this.suffixes; }

        /**
         * QE-exact option adjudication: read_cards pre-capitalises the card
         * line, then the FIRST chain arm whose literal appears as a
         * substring of the line wins (matches/imatches semantics). Order is
         * decisive: the HUBBARD {@code -ATOMIC} trap arm precedes the
         * {@code ATOMIC} option, so "PSEUDO-ATOMIC" aborts exactly like it
         * does in pw.x. Returns empty when no arm matches (the card's
         * disposition then decides - never this method).
         */
        public Optional<ChainEntry> firstChainMatch(String optionText) {
            if (optionText == null) {
                return Optional.empty();
            }
            String probe = optionText.toUpperCase(Locale.ROOT);
            for (ChainEntry entry : this.chain) {
                if (probe.contains(entry.getLiteral().getValue()
                        .toUpperCase(Locale.ROOT))) {
                    return Optional.of(entry);
                }
            }
            return Optional.empty();
        }

        /** True when this card HAS an option switch in read_cards. */
        public boolean hasOptionSwitch() {
            return this.disposition != Disposition.IGNORED
                    && !this.chain.isEmpty();
        }
    }

    /** One arm of the mined IF-chain: kind (option/trap) + literal + mask. */
    public static final class ChainEntry {
        private final boolean trap;
        private final PresenceValue literal;

        ChainEntry(boolean trap, PresenceValue literal) {
            this.trap = trap;
            this.literal = literal;
        }

        /** True when this arm aborts (errore) instead of accepting. */
        public boolean isTrap() { return this.trap; }

        /** The matched literal with its per-version presence mask. */
        public PresenceValue getLiteral() { return this.literal; }
    }

    /** One mined literal with its per-version presence mask. */
    public static final class PresenceValue {
        private final String value;
        private final int versionMask;

        PresenceValue(String value, int versionMask) {
            this.value = value;
            this.versionMask = versionMask;
        }

        /** Literal as mined (uppercase; K_POINTS suffixes keep the '_'). */
        public String getValue() { return this.value; }

        /** Presence mask across {@link QENamelistSchema#VERSIONS}. */
        public int getVersionMask() { return this.versionMask; }

        /** True when this literal exists for the given QE version string. */
        public boolean presentIn(String version) {
            int index = QENamelistSchema.indexOfVersion(version);
            return index >= 0 && index < QENamelistSchema.VERSIONS.size()
                    && (this.versionMask & (1 << index)) != 0;
        }
    }

    private static final List<Dispatch> DISPATCH;
    private static final Map<String, Dispatch> DISPATCH_BY_CARD;
    private static final List<CardGrammar> GRAMMARS;
    private static final Map<String, CardGrammar> GRAMMAR_BY_CARD;

    static {
        List<Dispatch> dispatch = QECardSchemaData.buildDispatch();
        DISPATCH = List.copyOf(dispatch);
        Map<String, Dispatch> byCard = new LinkedHashMap<>();
        for (Dispatch record : dispatch) {
            byCard.put(record.getCard(), record);
        }
        DISPATCH_BY_CARD = Collections.unmodifiableMap(byCard);
        List<CardGrammar> grammars = QECardSchemaData.buildGrammars();
        GRAMMARS = List.copyOf(grammars);
        Map<String, CardGrammar> grammarByCard = new LinkedHashMap<>();
        for (CardGrammar grammar : grammars) {
            grammarByCard.put(grammar.getCard(), grammar);
        }
        GRAMMAR_BY_CARD = Collections.unmodifiableMap(grammarByCard);
    }

    private QECardSchema() { }

    /** Factory used by the generated table only (dispatch row). */
    public static Dispatch dispatch(String card, String condition, boolean killed,
                                    String warnProg, int versionMask) {
        return new Dispatch(card, condition, killed, warnProg, versionMask);
    }

    /**
     * Factory used by the generated table only (per-card option grammar).
     * Chain csv entries are {@code KIND:LITERAL~MASK} with KIND O=option,
     * T=trap, in exact IF-arm order; suffix csv entries are {@code LIT~MASK}.
     */
    public static CardGrammar grammar(String card, String disposition,
                                      int dispositionMask, String bareDisposition,
                                      int bareMask, String note, String chainCsv,
                                      String suffixCsv) {
        List<ChainEntry> chain = new ArrayList<>();
        if (chainCsv != null && !chainCsv.isEmpty()) {
            for (String item : chainCsv.split(",", -1)) {
                int colon = item.indexOf(':');
                int tilde = item.lastIndexOf('~');
                if (colon < 0) {
                    continue;
                }
                boolean trap = item.startsWith("T:");
                String literal = tilde > colon
                        ? item.substring(colon + 1, tilde)
                        : item.substring(colon + 1);
                int mask = tilde > colon
                        ? parseMask(item.substring(tilde + 1))
                        : QENamelistSchema.ALL_VERSIONS_MASK;
                chain.add(new ChainEntry(trap, new PresenceValue(literal, mask)));
            }
        }
        return new CardGrammar(card, parseDisposition(disposition), dispositionMask,
                parseDisposition(bareDisposition), bareMask, note, chain,
                parseCsv(suffixCsv));
    }

    private static int parseMask(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException bad) {
            return QENamelistSchema.ALL_VERSIONS_MASK;
        }
    }

    private static Disposition parseDisposition(String token) {
        if (token == null) {
            return Disposition.UNKNOWN;
        }
        try {
            return Disposition.valueOf(token.trim());
        } catch (IllegalArgumentException unknown) {
            return Disposition.UNKNOWN;
        }
    }

    private static List<PresenceValue> parseCsv(String csv) {
        List<PresenceValue> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String item : csv.split(",", -1)) {
            int tilde = item.lastIndexOf('~');
            if (tilde < 0) {
                out.add(new PresenceValue(item,
                        QENamelistSchema.ALL_VERSIONS_MASK));
                continue;
            }
            out.add(new PresenceValue(item.substring(0, tilde),
                    parseMask(item.substring(tilde + 1))));
        }
        return out;
    }

    /** Every dispatch row in chain order (as in read_cards.f90). */
    public static List<Dispatch> dispatchChain() { return DISPATCH; }

    /** Case-insensitive dispatch lookup by card name. */
    public static Optional<Dispatch> lookupDispatch(String card) {
        if (card == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                DISPATCH_BY_CARD.get(card.trim().toUpperCase(Locale.ROOT)));
    }

    /** Every option grammar (option-bearing + content-only cards). */
    public static List<CardGrammar> grammars() { return GRAMMARS; }

    /** Case-insensitive grammar lookup; absent for cards without a mined reader. */
    public static Optional<CardGrammar> lookupGrammar(String card) {
        if (card == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                GRAMMAR_BY_CARD.get(card.trim().toUpperCase(Locale.ROOT)));
    }

    /** Verbatim mined consistency facts (bounded; provenance in the data header). */
    public static List<String> facts() {
        return QECardSchemaData.buildFacts();
    }
}
