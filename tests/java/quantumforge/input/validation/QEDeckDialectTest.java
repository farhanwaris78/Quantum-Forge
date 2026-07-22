/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class QEDeckDialectTest {

    @Test
    void classicDecksAreNeverMisreadAsXml() {
        assertFalse(QEDeckDialect.looksLikeXmlDeck(null));
        assertFalse(QEDeckDialect.looksLikeXmlDeck("   \n"));
        assertFalse(QEDeckDialect.looksLikeXmlDeck(
                "&CONTROL\n   calculation='scf'\n/\n&SYSTEM\n  ibrav=2\n/\n"));
        assertFalse(QEDeckDialect.looksLikeXmlDeck(
                "ATOMIC_POSITIONS {angstrom}\n  Si 0 0 0\n"));
        assertFalse(QEDeckDialect.looksLikeXmlDeck(
                "&INPUT_THERMO\n   what = 'mur_lc_t'\n/\n"),
                "a thermo_control namelist is classic dialect");
        // a leading comment block does not change the dialect:
        assertFalse(QEDeckDialect.looksLikeXmlDeck(
                "! hint: <angle brackets> in a comment\n&CONTROL\n/\n"));
    }

    @Test
    void xmlDocumentsAreDetectedConservatively() {
        assertTrue(QEDeckDialect.looksLikeXmlDeck(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<qes:espresso xmlns:qes=\"http://www.quantum-espresso.org/ns/qes/qes-1.0\"\n"
                        + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "  xsi:schemaLocation=\"... qes.xsd\">\n</qes:espresso>\n"));
        assertTrue(QEDeckDialect.looksLikeXmlDeck(
                "  \n<?xml version=\"1.0\"?>\n<input>\n  <control_variables/>\n</input>\n"),
                "an -xml input deck with declaration after whitespace");
        assertTrue(QEDeckDialect.looksLikeXmlDeck(
                "<espresso>\n  <input>...</input>\n</espresso>"),
                "declarationless QE-family XML still bites via the element root");
        assertTrue(QEDeckDialect.looksLikeXmlDeck("<input>\n</input>"));
        // not XML families:
        assertFalse(QEDeckDialect.looksLikeXmlDeck("<note>\n<text>x</text>\n</note>"),
                "generic non-QE XML without markers stays CLASSIC for this sniff");
        assertFalse(QEDeckDialect.looksLikeXmlDeck(
                "<!-- just an xml comment -->\n&CONTROL\n/\n"));
    }

    @Test
    void boundaryIssueStatesTheScopeOfTheClassicGrammar() {
        ValidationIssue issue = QEDeckDialect.boundaryIssue("test audit",
                "https://example.invalid/docs");
        assertEquals(ValidationSeverity.WARNING, issue.getSeverity());
        assertEquals(QEDeckDialect.CODE_XML_DIALECT, issue.getCode());
        assertTrue(issue.getMessage().contains("*.in"), issue.getMessage());
        assertTrue(issue.getMessage().contains("NO grammar checks ran"),
                "no adjudication may be implied");
        assertTrue(issue.getMessage().contains("test audit"),
                "the declining audit names itself");
    }

    @Test
    void allThreeAuditsBorderTheXmlDialect() {
        String xml = "<?xml version=\"1.0\"?>\n<qes:espresso>\n</qes:espresso>\n";

        List<ValidationIssue> card = new QECardAudit().auditDeckText(xml, "7.6");
        assertEquals(1, card.size(), "one boundary issue, no fabricated findings");
        assertEquals(QEDeckDialect.CODE_XML_DIALECT, card.get(0).getCode());

        List<ValidationIssue> thermo = new QEThermoPwDeckAudit().auditDeckText(xml, "2.1.1");
        assertEquals(1, thermo.size());
        assertEquals(QEDeckDialect.CODE_XML_DIALECT, thermo.get(0).getCode());

        List<ValidationIssue> extension = new QESchemaValidator()
                .validateDeckText(xml, "7.6");
        assertEquals(1, extension.size());
        assertEquals(QEDeckDialect.CODE_XML_DIALECT, extension.get(0).getCode());
    }
}
