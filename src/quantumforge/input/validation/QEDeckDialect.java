/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

/**
 * Dialect sniffing for QE input deck text (QE-integration roadmap R5):
 * pw.x accepts TWO input surfaces - the classic namelist+card grammar ALL
 * QuantumForge mined grammars describe (INPUT_PW/INPUT_PH/INPUT_HP, the
 * extension decks, the thermo_control namelist, the read_cards card line),
 * and the qexsd XML surface ({@code qe:input} / {@code qes:espresso}
 * documents, e.g. written with {@code -in input.xml} workflows).
 *
 * <p>The mined classic grammar laws hold for {@code *.in}-family decks ONLY.
 * Running a namelist/card audit over an XML deck cannot find truth: at best
 * it is silent (no namelist markers); at worst a future detector misfires on
 * XML text. This sniffer lets every grammar audit state the boundary
 * honestly: one WARNING, no adjudication, no fabricated clean bill.</p>
 *
 * <p>Detection is deliberately conservative and bounded to the first 4096
 * characters: a classic QE deck NEVER begins with {@code <?xml} or an
 * element line, so there are no false positives on the classic dialect;
 * exotic XML without a declaration is caught by the element-root heuristic.</p>
 */
public final class QEDeckDialect {

    /** WARNING code of the single boundary issue every audit reports. */
    public static final String CODE_XML_DIALECT = "DECK_XML_DIALECT";

    /** Bounded sniff surface (chars); no audit reads further for detection. */
    private static final int SNIFF_CHARS = 4096;

    private QEDeckDialect() { }

    /**
     * True when the deck text looks like an XML input document rather than a
     * classic namelist/card deck. Null/blank is CLASSIC-ish (the audits own
     * those boundaries already).
     */
    public static boolean looksLikeXmlDeck(String deckText) {
        if (deckText == null || deckText.isBlank()) {
            return false;
        }
        String head = deckText.length() > SNIFF_CHARS
                ? deckText.substring(0, SNIFF_CHARS) : deckText;
        String trimmed = head.stripLeading();
        if (trimmed.startsWith("<?xml")) {
            return true; // an XML declaration is never a classic namelist
        }
        // Element-root heuristic for declarationless XML: the first non-blank
        // line opens an element AND names the QE/qexsd family or <input>.
        String firstLine = trimmed.lines().findFirst().orElse("");
        if (firstLine.startsWith("<") && !firstLine.startsWith("<!")) {
            String lower = firstLine.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("espresso") || lower.contains("qes:")
                    || lower.startsWith("<input") || lower.contains("xsd");
        }
        return false;
    }

    /**
     * The single boundary WARNING an audit reports for an XML deck: it names
     * which audit declined, states that classic-grammar laws hold for
     * {@code *.in} decks only, and that NO grammar adjudication ran.
     */
    public static ValidationIssue boundaryIssue(String auditName, String docsUrl) {
        return new ValidationIssue(ValidationSeverity.WARNING, CODE_XML_DIALECT,
                "this text looks like an XML input deck (qexsd/-xml family), not a"
                        + " classic namelist+card deck: the " + auditName
                        + " adjudicates the mined classic grammar - INPUT_PW-family"
                        + " namelists and read_cards cards - whose laws hold for *.in"
                        + " decks ONLY. NO grammar checks ran on the XML text; write or"
                        + " convert to a classic deck to use this audit (adjudicating XML"
                        + " against the classic grammar would fabricate findings).",
                docsUrl);
    }
}
