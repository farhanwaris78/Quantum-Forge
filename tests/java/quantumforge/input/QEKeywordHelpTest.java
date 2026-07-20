/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class QEKeywordHelpTest {

    @Test
    void lookupFindsKeywordsCaseInsensitively() {
        QEKeywordHelp.KeywordEntry entry = QEKeywordHelp.lookup("ecutwfc").orElseThrow();
        assertEquals("ecutwfc", entry.getName());
        assertEquals(QEInput.NAMELIST_SYSTEM, entry.getSection());
        assertTrue(entry.getSummary().contains("Ry"),
                "Cutoff summaries must carry units: " + entry.getSummary());
        assertTrue(QEKeywordHelp.lookup("  ECUTWFC ").isPresent(),
                "Keywords are matched case-insensitively with surrounding space");
        assertEquals(QEInput.NAMELIST_CONTROL,
                QEKeywordHelp.lookup("calculation").orElseThrow().getSection());
        assertEquals(QEInput.NAMELIST_ELECTRONS,
                QEKeywordHelp.lookup("conv_thr").orElseThrow().getSection());
    }

    @Test
    void lookupFailsClosedOutsideTheCuratedSet() {
        assertTrue(QEKeywordHelp.lookup("hubbard_u").isEmpty(),
                "Unlisted keywords are refused, not improvised (that is Roadmap #22's job)");
        assertTrue(QEKeywordHelp.lookup("notakeyword").isEmpty());
        assertTrue(QEKeywordHelp.lookup("").isEmpty());
        assertTrue(QEKeywordHelp.lookup(null).isEmpty());
    }

    @Test
    void coverageTableIsSortedAndWellFormed() {
        List<QEKeywordHelp.KeywordEntry> entries = QEKeywordHelp.entries();
        assertTrue(entries.size() >= 30, "The curated subset must be substantial");
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i - 1).getName().compareTo(entries.get(i).getName()) < 0,
                    "Entries must be sorted by name");
        }
        for (QEKeywordHelp.KeywordEntry entry : entries) {
            assertFalse(entry.getSummary().isBlank(), entry.getName());
            assertTrue(entry.getSection().startsWith("C") || entry.getSection().startsWith("S")
                    || entry.getSection().startsWith("E") || entry.getSection().startsWith("I"),
                    "Section must be a pw.x namelist: " + entry.getSection());
        }
        assertEquals(entries.size(), QEKeywordHelp.coveredNames().size());
    }
}
