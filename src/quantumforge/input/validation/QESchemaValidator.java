/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import quantumforge.input.QEInput;
import quantumforge.input.QEInputReader;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.schema.QENamelistSchema.Kind;

/**
 * Thin QEInput adapter of {@link QESchemaAudit} (Roadmap #22 completeness):
 * extracts the live namelist keyword pairs out of a resolved {@link QEInput}
 * and audits them against the machine-mined QE grammar window (7.2 .. 7.6).
 * Supplements - never replaces - the structural {@link QEInputValidator}.
 *
 * <p>Scope honesty (stated in the class docs because reports inherit it):</p>
 * <ul>
 *   <li>the eight primary surfaces {@code QEInput.listNamelistKeys()} exposes
 *       PLUS the five pw.x extension decks (FCP, RISM, WANNIER, WANNIER_AC,
 *       PRESS_AI - roadmap R1) are reachable: from registered model namelists
 *       via {@link #validate}, and from the rendered deck text via
 *       {@link #validateDeckText} (extension decks only, so a secondary-input
 *       model is never double-judged);</li>
 *   <li>the audit is the pw.x ({@link Kind#PW}) grammar - ph.x/hp.x decks use
 *       that same core with their own Kind and raw texts;</li>
 *   <li>values audit in their STORED (already unquoted) form, which is exactly
 *       the comparison {@link QESchemaAudit} performs after stripping matching
 *       quotes - case remains significant, like the Fortran SELECT CASE;</li>
 *   <li>cards (ATOMIC_POSITIONS, K_POINTS...) stay with the structural
 *       validator - this layer adjudicates namelist keywords only.</li>
 * </ul>
 */
public final class QESchemaValidator {

    /** Bounded deck-text audit surface (roadmap R1); beyond it: refuse loudly. */
    public static final int MAX_DECK_TEXT_CHARS = 4_000_000;

    /** Code carried by the single issue emitted when the text bound trips. */
    public static final String CODE_TEXT_LIMIT = "SCHEMA_TEXT_LIMIT";

    private final QESchemaAudit audit = new QESchemaAudit();

    /**
     * Audits every namelist keyword the input carries against the mined pw.x
     * grammar of the requested version.
     *
     * @param input   the resolved input; null and namelist-less inputs audit to
     *                an EMPTY issue list rather than a fabricated verdict (the
     *                structural validator owns INPUT_NULL/SYSTEM_MISSING)
     * @param version a supported minor version label ("7.2" .. "7.6"); anything
     *                else is refused loudly ({@link IllegalArgumentException}),
     *                never re-interpreted
     */
    public List<ValidationIssue> validate(QEInput input, String version) {
        Objects.requireNonNull(version, "version");
        if (input == null) {
            return List.of();
        }
        Map<String, Map<String, String>> pairs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String namelistKey : QEInput.listAllNamelistKeys()) {
            QENamelist namelist = input.getNamelist(namelistKey);
            if (namelist == null || namelist.numValues() == 0) {
                continue;
            }
            Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (QEValue value : namelist.listQEValues()) {
                if (value != null && value.getName() != null
                        && !value.getName().trim().startsWith("!")) {
                    out.put(value.getName(), value.getCharacterValue());
                }
            }
            if (!out.isEmpty()) {
                pairs.put(namelistKey, out);
            }
        }
        return this.audit.validatePairs(Kind.PW, version, pairs);
    }

    /**
     * Audit of the five pw.x EXTENSION decks (FCP/RISM/WANNIER/WANNIER_AC/
     * PRESS_AI - roadmap R1) read from a RENDERED deck text, through the same
     * {@link QEInputReader}/{@link QENamelist} grammar the model path uses -
     * never a parallel mini-parser. Primary namelists are intentionally NOT
     * read here: {@link #validate} owns the model side and a rendered model
     * can only echo what it already produced.
     *
     * @param deckText text of an input deck (e.g. {@code input.toString()});
     *                 null/blank audits to an EMPTY issue list, like a null
     *                 input does. Texts beyond {@value #MAX_DECK_TEXT_CHARS}
     *                 characters are refused with one WARNING issue rather
     *                 than audited halfway - an unbounded read is not an
     *                 audit surface.
     * @param version  supported minor label; invalid labels throw through
     *                 {@link QESchemaAudit#validatePairs} (never re-interpreted)
     */
    public List<ValidationIssue> validateDeckText(String deckText, String version) {
        Objects.requireNonNull(version, "version");
        if (deckText == null || deckText.isBlank()) {
            return List.of();
        }
        if (deckText.length() > MAX_DECK_TEXT_CHARS) {
            return List.of(new ValidationIssue(ValidationSeverity.WARNING,
                    CODE_TEXT_LIMIT,
                    "deck text of " + deckText.length() + " characters exceeds the "
                            + MAX_DECK_TEXT_CHARS + "-character audit bound; the extension "
                            + "decks were NOT audited - split or trim the deck instead of "
                            + "trusting a silent truncation.",
                    null));
        }
        if (QEDeckDialect.looksLikeXmlDeck(deckText)) {
            return List.of(QEDeckDialect.boundaryIssue(
                    "QE extension-deck grammar audit", null));
        }
        QEInputReader reader = new QEInputReader();
        reader.appendInputData(deckText);
        Map<String, Map<String, String>> pairs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String namelistKey : QEInput.listExtraNamelistKeys()) {
            QENamelist namelist = new QENamelist(namelistKey);
            try {
                reader.readNamelist(namelist);
            } catch (java.io.IOException | RuntimeException problem) {
                // A deck this reader cannot parse is a structural problem owned
                // by QEInputValidator - this layer stays an audit, not a parser
                // of last resort.
                continue;
            }
            Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (QEValue value : namelist.listQEValues()) {
                if (value != null && value.getName() != null
                        && !value.getName().trim().startsWith("!")) {
                    out.put(value.getName(), value.getCharacterValue());
                }
            }
            if (!out.isEmpty()) {
                pairs.put(namelistKey, out);
            }
        }
        return this.audit.validatePairs(Kind.PW, version, pairs);
    }
}
