/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.input.QEDeckKeywordCatalog.KeywordRow;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.operation.OperationResult;

class QEDeckKeywordCatalogTest {

    @Test
    void versionResolutionIsFailClosedAndNamesTheFallbackHonestly() {
        OperationResult<String> blank = QEDeckKeywordCatalog.resolveVersion("  ");
        assertTrue(blank.isSuccess(), blank.toString());
        assertEquals("7.6", blank.getValue().orElseThrow(),
                "blank resolves to the newest mined window");
        assertEquals("QES_VERSION_NEWEST", blank.getCode());

        OperationResult<String> bogus = QEDeckKeywordCatalog.resolveVersion("6.6");
        assertFalse(bogus.isSuccess());
        assertEquals("QES_VERSION", bogus.getCode());
        assertTrue(bogus.getMessage().contains("7.2, 7.3, 7.4, 7.5, 7.6"),
                bogus.getMessage());
        assertTrue(bogus.getMessage().contains("falling back to a different grammar silently"),
                "the refusal states the no-silent-fallback rule: " + bogus.getMessage());
    }

    @Test
    void forVersionFailsClosedOnNullKindAndUnknownVersion() {
        assertEquals("QES_KIND",
                QEDeckKeywordCatalog.forVersion(null, "7.6").getCode());
        assertEquals("QES_VERSION",
                QEDeckKeywordCatalog.forVersion(QENamelistSchema.Kind.PH, "8.1").getCode());
    }

    @Test
    void rowCountsMatchTheMinedGrammarWindowPerVersion() {
        // Mined truth (scripts/qe_schema_miner.py over INPUT_*.def + NAMELIST
        // declarations of qe-7.2..7.6): pw gains keywords over the window, ph
        // loses skip_upperfan at 7.5 and gains kx/ky/kz at 7.3, hp gains
        // no_metq0 at 7.5.
        assertEquals(75, rows(QENamelistSchema.Kind.PH, "7.2"));
        assertEquals(78, rows(QENamelistSchema.Kind.PH, "7.3"));
        assertEquals(79, rows(QENamelistSchema.Kind.PH, "7.6"));
        assertEquals(32, rows(QENamelistSchema.Kind.HP, "7.2"));
        assertEquals(33, rows(QENamelistSchema.Kind.HP, "7.6"));
        assertEquals(454, rows(QENamelistSchema.Kind.PW, "7.2"));
        assertEquals(458, rows(QENamelistSchema.Kind.PW, "7.6"));
    }

    @Test
    void versionGatedKeywordsFlipExactlyAtTheMinedBoundaries() {
        QEDeckKeywordCatalog ph72 = catalog(QENamelistSchema.Kind.PH, "7.2");
        QEDeckKeywordCatalog ph74 = catalog(QENamelistSchema.Kind.PH, "7.4");
        QEDeckKeywordCatalog ph75 = catalog(QENamelistSchema.Kind.PH, "7.5");
        QEDeckKeywordCatalog ph76 = catalog(QENamelistSchema.Kind.PH, "7.6");

        assertFalse(ph74.prompts("lmultipole"), "added at 7.5");
        assertTrue(ph75.prompts("lmultipole"));
        assertTrue(ph76.prompts("lmultipole"));
        assertEquals("7.5-7.6", ph74.windowText("lmultipole"));

        assertTrue(ph72.prompts("skip_upperfan"), "removed from 7.5 on");
        assertFalse(ph75.prompts("skip_upperfan"));
        assertEquals("7.2-7.4", ph75.windowText("skip_upperfan"));

        assertTrue(ph72.prompts("ldisp") && ph76.prompts("ldisp"),
                "grammar-stable keyword prompts in every window");
        assertFalse(ph76.prompts("total_fantasy_keyword"),
                "unknown stays unknown - never silently promptable");
    }

    @Test
    void rowsCarryVerbatimMinedFactsAndVersionFilteredHardValues() {
        QEDeckKeywordCatalog ph76 = catalog(QENamelistSchema.Kind.PH, "7.6");
        KeywordRow ldisp = find(ph76, "ldisp");
        assertEquals("INPUTPH", ldisp.getNamelist());
        assertEquals(QENamelistSchema.Type.LOGICAL, ldisp.getType());
        assertEquals(".false.", ldisp.getDefaultText(), "mined default verbatim");
        assertFalse(ldisp.isRequired());
        assertEquals("7.2-7.6", ldisp.getVersionRange());
        assertEquals(QENamelistSchema.INPUT_PH_URL, ldisp.getDocsUrl());

        KeywordRow ahc = find(ph76, "ahc_nbnd");
        assertTrue(ahc.isRequired(), ".def status{REQUIRED} reaches the prompt row");
        assertTrue(ahc.promptLabel().contains("[REQUIRED]"), ahc.promptLabel());

        KeywordRow diag = find(ph76, "diagonalization");
        assertTrue(diag.getAcceptedValues().contains("direct"),
                "7.6-only hard literal is visible: " + diag.getAcceptedValues());
        KeywordRow diag75 = find(catalog(QENamelistSchema.Kind.PH, "7.5"), "diagonalization");
        assertFalse(diag75.getAcceptedValues().contains("direct"),
                "'direct' aborts pre-7.6 ph.x - hidden from its accepted set: "
                        + diag75.getAcceptedValues());
        assertTrue(diag75.getAcceptedValues().contains("david"));
    }

    @Test
    void namelistGroupingFollowsTheMinedNames() {
        QEDeckKeywordCatalog ph = catalog(QENamelistSchema.Kind.PH, "7.6");
        assertEquals(List.of("INPUTPH"), ph.namelistNames());
        QEDeckKeywordCatalog hp = catalog(QENamelistSchema.Kind.HP, "7.6");
        assertEquals(List.of("INPUTHP"), hp.namelistNames());
        assertEquals(79, ph.rows("inputph").size(), "name match is case-insensitive");
        assertTrue(hp.rows("NONSENSE").isEmpty());
    }

    private static int rows(QENamelistSchema.Kind kind, String version) {
        return catalog(kind, version).rows().size();
    }

    private static QEDeckKeywordCatalog catalog(QENamelistSchema.Kind kind, String version) {
        return QEDeckKeywordCatalog.forVersion(kind, version).getValue().orElseThrow();
    }

    private static KeywordRow find(QEDeckKeywordCatalog catalog, String name) {
        for (KeywordRow row : catalog.rows()) {
            if (row.getName().equalsIgnoreCase(name)) {
                return row;
            }
        }
        throw new AssertionError(name + " missing from " + catalog.getKind().getExecutable()
                + " " + catalog.getVersion() + " catalog");
    }
}
