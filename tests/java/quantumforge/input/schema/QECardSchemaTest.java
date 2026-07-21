/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class QECardSchemaTest {

    @Test
    void dispatchIsTheVerbatimReadCardsChain() {
        List<QECardSchema.Dispatch> chain = QECardSchema.dispatchChain();
        assertEquals(18, chain.size(), "mined ELSEIF arms, pinned");
        QECardSchema.Dispatch first = chain.get(0);
        assertEquals("ATOMIC_SPECIES", first.getCard(),
                "chain order is read_cards order, not alphabetical");
        assertTrue(first.getCondition().contains("ATOMIC_SPECIES"));
    }

    @Test
    void removedCardsCarryTheKilledFlagEverywhere() {
        for (String card : List.of("DIPOLE", "ESR")) {
            QECardSchema.Dispatch row =
                    QECardSchema.lookupDispatch(card).orElseThrow();
            assertTrue(row.isKilled(), card + " aborts with 'no longer existing'");
            assertTrue(row.presentIn("7.2"));
            assertTrue(row.presentIn("7.6"));
            assertEquals(0x1F, row.getVersionMask(), "stable across qe-7.2..7.6");
        }
    }

    @Test
    void warnProgSplitsKpointsFromKsout() {
        assertEquals("CP", QECardSchema.lookupDispatch("K_POINTS")
                .orElseThrow().getWarnProg(),
                "K_POINTS warns-ignored only under CP");
        assertEquals("PW", QECardSchema.lookupDispatch("KSOUT")
                .orElseThrow().getWarnProg(),
                "KSOUT is read then warned-ignored under PW");
        assertEquals("", QECardSchema.lookupDispatch("ATOMIC_POSITIONS")
                .orElseThrow().getWarnProg());
    }

    @Test
    void gatesAreVerbatimInConditions() {
        assertEquals("trim(card) == 'WANNIER_AC' .and. ( prog == 'WA' )",
                QECardSchema.lookupDispatch("WANNIER_AC")
                        .orElseThrow().getCondition());
        assertEquals("trim(card) == 'SOLVENTS' .AND. trism",
                QECardSchema.lookupDispatch("SOLVENTS")
                        .orElseThrow().getCondition());
    }

    @Test
    void hubbardChainKeepsTheExactArmOrder() {
        QECardSchema.CardGrammar hubbard =
                QECardSchema.lookupGrammar("HUBBARD").orElseThrow();
        List<QECardSchema.ChainEntry> chain = hubbard.getChain();
        assertEquals(8, chain.size(), "5 options + 3 traps, pinned");
        assertFalse(chain.get(0).isTrap());
        assertEquals("ORTHO-ATOMIC", chain.get(0).getLiteral().getValue());
        assertFalse(chain.get(1).isTrap());
        assertEquals("NORM-ATOMIC", chain.get(1).getLiteral().getValue());
        assertTrue(chain.get(2).isTrap());
        assertEquals("-ATOMIC", chain.get(2).getLiteral().getValue(),
                "the suffix-misspell trap sits BEFORE the ATOMIC option");
        assertTrue(chain.get(3).isTrap());
        assertEquals("ORTHOATOMIC", chain.get(3).getLiteral().getValue());
        assertTrue(chain.get(4).isTrap());
        assertEquals("NORMATOMIC", chain.get(4).getLiteral().getValue());
        assertFalse(chain.get(5).isTrap());
        assertEquals("ATOMIC", chain.get(5).getLiteral().getValue());
        assertEquals(QECardSchema.Disposition.FATAL, hubbard.getDisposition());
        assertEquals(QECardSchema.Disposition.FATAL, hubbard.getBareDisposition(),
                "bare HUBBARD errores 'None or wrong Hubbard projectors'");
    }

    @Test
    void substringMatchingIsChainOrderHonest() {
        QECardSchema.CardGrammar hubbard =
                QECardSchema.lookupGrammar("HUBBARD").orElseThrow();
        Optional<QECardSchema.ChainEntry> trap =
                hubbard.firstChainMatch("pseudo-atomic");
        assertTrue(trap.isPresent() && trap.get().isTrap(),
                "pw.x matches the -ATOMIC trap arm before ATOMIC: it aborts");
        Optional<QECardSchema.ChainEntry> valid =
                hubbard.firstChainMatch("ORTHO-ATOMIC");
        assertTrue(valid.isPresent() && !valid.get().isTrap(),
                "the full ORTHO-ATOMIC arm precedes its own -ATOMIC suffix trap");
        Optional<QECardSchema.ChainEntry> dashed =
                hubbard.firstChainMatch("wf");
        assertTrue(dashed.isPresent() && !dashed.get().isTrap(),
                "input_line is pre-capitalised: matching is case-insensitive");
    }

    @Test
    void dispositionsAreTheMinedReadings() {
        QECardSchema.CardGrammar positions =
                QECardSchema.lookupGrammar("ATOMIC_POSITIONS").orElseThrow();
        assertEquals(QECardSchema.Disposition.FATAL, positions.getDisposition(),
                "'unknown option for ATOMIC_POSITION' aborts");
        assertEquals(QECardSchema.Disposition.TOLERATED_DEFAULT,
                positions.getBareDisposition(),
                "bare ATOMIC_POSITIONS: DEPRECATED + prog-dependent default");
        assertTrue(positions.getNote().contains("bohr")
                && positions.getNote().contains("alat"));

        QECardSchema.CardGrammar kpoints =
                QECardSchema.lookupGrammar("K_POINTS").orElseThrow();
        assertEquals(QECardSchema.Disposition.TOLERATED_SILENT_DEFAULT,
                kpoints.getDisposition(), "unknown option silently defaults tpiba");
        assertTrue(kpoints.getNote().contains("tpiba"));
        assertEquals(4, kpoints.getOptions().size(),
                "AUTOMATIC, CRYSTAL, TPIBA, GAMMA - pinned");
        assertEquals(2, kpoints.getSuffixes().size(), "_B and _C suffix flags");

        QECardSchema.CardGrammar cell =
                QECardSchema.lookupGrammar("CELL_PARAMETERS").orElseThrow();
        assertEquals(QECardSchema.Disposition.TOLERATED_DEFAULT,
                cell.getDisposition(), "DEPRECATED + cell_units='none'");

        QECardSchema.CardGrammar reference =
                QECardSchema.lookupGrammar("REF_CELL_PARAMETERS").orElseThrow();
        assertEquals(QECardSchema.Disposition.TOLERATED_SILENT_DEFAULT,
                reference.getDisposition(), "silent default 'alat'");

        assertEquals(QECardSchema.Disposition.IGNORED,
                QECardSchema.lookupGrammar("OCCUPATIONS").orElseThrow()
                        .getDisposition(),
                "content-only card: option text is never even read");
    }

    @Test
    void everyMinedLiteralIsStableAcrossTheWholeWindow() {
        for (QECardSchema.CardGrammar grammar : QECardSchema.grammars()) {
            for (QECardSchema.ChainEntry entry : grammar.getChain()) {
                assertEquals(0x1F, entry.getLiteral().getVersionMask(),
                        grammar.getCard() + " chain literal drift: "
                                + entry.getLiteral().getValue());
            }
        }
        assertEquals(9, QECardSchema.grammars().size(), "mined option grammar count");
    }

    @Test
    void factsCarryTheVerbatimMeshErrores() {
        List<String> facts = QECardSchema.facts();
        assertTrue(facts.size() >= 10 && facts.size() <= 16,
                "bounded verbatim facts, got " + facts.size());
        assertTrue(facts.stream().anyMatch(f
                -> f.contains("invalid offsets: must be 0 or 1")), facts.toString());
        assertTrue(facts.stream().anyMatch(f
                -> f.contains("invalid values for nk1, nk2, nk3")), facts.toString());
        assertTrue(facts.stream().anyMatch(f
                -> f.contains("nk1, nk2, nk3, k1, k2")), "six-integer automatic READ");
        assertTrue(facts.stream().anyMatch(f
                -> f.contains("Wrong name of the Hubbard projectors")), facts.toString());
    }
}
