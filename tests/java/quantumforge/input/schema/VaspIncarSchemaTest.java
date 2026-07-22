/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Batch-173 pins for {@link VaspIncarSchema}: the tier-1 census and a set of
 * verbatim default texts mined from the vasp.at wiki window (2026-07-22).
 */
class VaspIncarSchemaTest {

    @Test
    void tier1CensusIsThePinnedFiftyThree() {
        assertEquals(53, VaspIncarSchema.entryCount(),
                "the tier-1 INCAR catalogue is pinned at 53 wiki-mined tags");
        assertEquals(53, VaspIncarSchema.entries().size());
    }

    @Test
    void pinnedDefaultsSurviveVerbatim() {
        assertEquals("10^{-4}",
                VaspIncarSchema.lookup("EDIFF").orElseThrow().getDefaultText());
        assertEquals("60",
                VaspIncarSchema.lookup("NELM").orElseThrow().getDefaultText());
        assertEquals("0.2",
                VaspIncarSchema.lookup("SIGMA").orElseThrow().getDefaultText());
        assertEquals("eV",
                VaspIncarSchema.lookup("SIGMA").orElseThrow().getUnit());
        assertEquals("unknown system",
                VaspIncarSchema.lookup("SYSTEM").orElseThrow().getDefaultText());
        assertEquals("available ranks",
                VaspIncarSchema.lookup("NPAR").orElseThrow().getDefaultText());
        assertEquals("largest ENMAX in the POTCAR file",
                VaspIncarSchema.lookup("ENCUT").orElseThrow().getDefaultText());
    }

    @Test
    void pinnedOptionSetsAreComplete() {
        assertEquals(java.util.List.of("-15", "-14", "-5", "-4", "-3", "-2",
                "-1", "0", ">0"),
                VaspIncarSchema.lookup("ISMEAR").orElseThrow().getOptions());
        assertEquals(java.util.List.of("Normal", "Single", "SingleN",
                "Accurate", "Low", "Medium", "High"),
                VaspIncarSchema.lookup("PREC").orElseThrow().getOptions());
        assertEquals(java.util.List.of("0", "1", "2", "4", "5", "10", "11",
                "12"),
                VaspIncarSchema.lookup("ICHARG").orElseThrow().getOptions());
        assertEquals(java.util.List.of("1", "2", "4"),
                VaspIncarSchema.lookup("LDAUTYPE").orElseThrow().getOptions());
    }

    @Test
    void tierLadderSeparatesCatalogueWindowAndAlien() {
        assertEquals(1, VaspIncarSchema.tierOf("AEXX"), "tier-1 tag");
        assertEquals(2, VaspIncarSchema.tierOf("ELPH_KSPACING"),
                "wiki index page-1 name, not catalogued");
        assertEquals(2, VaspIncarSchema.tierOf("CUTOFF_MU"));
        assertEquals(0, VaspIncarSchema.tierOf("FOOBAR"), "alien name");
        assertEquals(0, VaspIncarSchema.tierOf(null), "null is alien");
        assertTrue(VaspIncarSchema.isRecognizedName("BEXX"));
        assertFalse(VaspIncarSchema.isRecognizedName("NOPE_NOT_A_TAG"));
    }

    @Test
    void lookupNormalizesCaseLikeVasp() {
        assertTrue(VaspIncarSchema.lookup("encut").isPresent());
        assertTrue(VaspIncarSchema.lookup("  IsmEaR  ").isPresent());
        assertEquals("ENCUT",
                VaspIncarSchema.lookup("encut").orElseThrow().getName(),
                "canonical names stay uppercase");
        assertTrue(VaspIncarSchema.lookup(null).isEmpty());
        assertEquals("KERNEL_TRUNCATION/LTRUNCATE",
                VaspIncarSchema.normalize("kernel_truncation/ltruncate"),
                "nested tags keep their slash");
    }

    @Test
    void wikiUrlsPointAtTheTagPages() {
        assertEquals("https://www.vasp.at/wiki/index.php/ENCUT",
                VaspIncarSchema.wikiUrl("ENCUT"));
        assertTrue(VaspIncarSchema.WIKI_WINDOW.contains("2026-07-22"),
                "the pinned window carries its fetch date");
        assertTrue(VaspIncarSchema.WIKI_WINDOW.contains("vasp.at"));
    }

    @Test
    void everyEntryCarriesTheSevenColumnsHonestly() {
        for (VaspIncarSchema.Entry entry : VaspIncarSchema.entries()) {
            assertFalse(entry.getName().isBlank());
            assertTrue(entry.getName().equals(
                    entry.getName().toUpperCase(java.util.Locale.ROOT)),
                    entry.getName() + " must be canonical uppercase");
            assertFalse(entry.getDocLine().isBlank(),
                    entry.getName() + " lost its wiki doc line");
            assertTrue(entry.getType() != null, entry.getName());
            // IDIPOL/EFIELD pin NO default on the wiki: empty stays empty
            // and is never a fabrication - asserted structurally here.
            assertTrue(entry.getDefaultText() != null, entry.getName());
        }
    }
}
