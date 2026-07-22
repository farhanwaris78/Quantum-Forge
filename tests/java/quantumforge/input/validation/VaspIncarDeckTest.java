/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Batch-173 pins for {@link VaspIncarDeck}: the INCAR statement grammar
 * (semicolons, comments, continuations, curly groups, quotes, arrays),
 * provenance vasp.at/wiki/index.php/INCAR (fetched 2026-07-22).
 */
class VaspIncarDeckTest {

    @Test
    void semicolonSeparatedStatementsShareOneLine() {
        OperationResult<VaspIncarDeck> parsed = VaspIncarDeck.parse(
                "SYSTEM = fcc Si ; ENCUT = 400; EDIFF = 1E-5\n");
        assertTrue(parsed.isSuccess(), parsed.getMessage());
        VaspIncarDeck deck = parsed.getValue().orElseThrow();
        assertEquals(3, deck.getStatements().size());
        assertEquals(List.of("SYSTEM", "ENCUT", "EDIFF"), deck.distinctTags());
        assertEquals("fcc Si",
                deck.first("SYSTEM").orElseThrow().getRawValue());
        assertEquals(1, deck.first("EDIFF").orElseThrow().getLine(),
                "all three statements keep their shared 1-based line");
        assertEquals(0, deck.getIgnoredLineCount());
    }

    @Test
    void hashAndBangStartCommentsAnywhere() {
        VaspIncarDeck deck = VaspIncarDeck.parse(
                "# full-line comment\n"
                + "ENCUT = 400 # trailing hash comment\n"
                + "EDIFF = 1E-5 ! trailing bang comment\n"
                + "! another one\n"
                + "ISMEAR = 0\n").getValue().orElseThrow();
        assertEquals(3, deck.getStatements().size());
        assertEquals("400", deck.first("ENCUT").orElseThrow().getRawValue());
        assertEquals("1E-5", deck.first("EDIFF").orElseThrow().getRawValue());
        assertEquals(0, deck.getIgnoredLineCount(),
                "comment-only lines are not 'ignored bare text'");
    }

    @Test
    void backslashContinuationJoinsAndBlankAfterIsNoted() {
        // clean join: MAGMOM spans two physical lines
        VaspIncarDeck clean = VaspIncarDeck.parse(
                "MAGMOM = 2*5.0 \\\n"
                + "         2*-5.0\n").getValue().orElseThrow();
        assertEquals(1, clean.getStatements().size());
        assertTrue(clean.getSyntaxNotes().isEmpty(),
                "clean continuation stays quiet: " + clean.getSyntaxNotes());
        assertEquals(Optional.of(List.of(5.0, 5.0, -5.0, -5.0)),
                clean.first("MAGMOM").orElseThrow().realArrayValue());

        // blank after the backslash: the wiki warns some VASP versions
        // cannot parse those - the deck still joins but pins the note
        VaspIncarDeck withBlank = VaspIncarDeck.parse(
                "ALGO = \\   \n" + "  Fast\n").getValue().orElseThrow();
        assertEquals("Fast",
                withBlank.first("ALGO").orElseThrow().getRawValue());
        assertTrue(withBlank.getSyntaxNotes().stream().anyMatch(
                note -> note.contains("Avoid blanks after the backslash")),
                "the wiki quote must be carried: "
                        + withBlank.getSyntaxNotes());
    }

    @Test
    void curlyGroupsPrefixInnerTagsAndUnclosedGroupsAreNoted() {
        VaspIncarDeck deck = VaspIncarDeck.parse(
                "PLUGINS {\n"
                + "  STRUCTURE = T\n"
                + "}\n"
                + "KERNEL_TRUNCATION/LTRUNCATE = T\n").getValue().orElseThrow();
        assertEquals("PLUGINS/STRUCTURE",
                deck.getStatements().get(0).getTag(),
                "the curly group name prefixes inner tags");
        assertEquals("KERNEL_TRUNCATION/LTRUNCATE",
                deck.getStatements().get(1).getTag(),
                "literal nested tags survive verbatim");
        VaspIncarDeck open = VaspIncarDeck.parse(
                "PLUGINS {\n  STRUCTURE = T\n").getValue().orElseThrow();
        assertTrue(open.getSyntaxNotes().stream().anyMatch(
                note -> note.contains("never closed")),
                "an unclosed group is a note, never silent: "
                        + open.getSyntaxNotes());
    }

    @Test
    void quotedValuesSpanLinesAndUnterminatedQuotesAreNoted() {
        VaspIncarDeck deck = VaspIncarDeck.parse(
                "WANNIER90_WIN = \"\n"
                + "  begin projections\n"
                + "  end projections\n"
                + "\"\n").getValue().orElseThrow();
        assertEquals(1, deck.getStatements().size());
        assertTrue(deck.first("WANNIER90_WIN").orElseThrow().getRawValue()
                .contains("begin projections"));
        assertTrue(deck.getSyntaxNotes().isEmpty(),
                "a closed multiline quote is clean: " + deck.getSyntaxNotes());
        VaspIncarDeck broken = VaspIncarDeck.parse(
                "WANNIER90_WIN = \"\n  content\n").getValue().orElseThrow();
        assertTrue(broken.getSyntaxNotes().stream().anyMatch(
                note -> note.startsWith("unterminated")),
                "VASP would read the rest of the file as the value");
        assertEquals(1, broken.getStatements().size(),
                "the unterminated statement is still emitted");
    }

    @Test
    void duplicatesAreACensusNeverAVerdict() {
        VaspIncarDeck deck = VaspIncarDeck.parse(
                "ENCUT = 300\nISMEAR = 0\nENCUT = 500\n").getValue()
                .orElseThrow();
        assertEquals(3, deck.getStatements().size());
        assertTrue(deck.hasDuplicates("ENCUT"));
        assertFalse(deck.hasDuplicates("ISMEAR"));
        assertEquals(2, deck.occurrencesOf("encut").size(),
                "occurrence lookup normalizes like VASP");
        assertEquals(1, deck.first("ENCUT").orElseThrow().getLine(),
                "first() is the first file occurrence");
    }

    @Test
    void arrayExpansionHonorsTheWikiRepetitionSyntax() {
        Optional<List<Double>> magmom = VaspIncarDeck.expandArray(
                "2*5.0 2*-5.0", false);
        assertEquals(Optional.of(List.of(5.0, 5.0, -5.0, -5.0)), magmom,
                "MAGMOM wiki example expands verbatim");
        assertEquals(Optional.of(List.of(1.0, 2.0, 3.25)),
                VaspIncarDeck.expandArray("1.0 2.0 3.25", false));
        assertTrue(VaspIncarDeck.expandArray("0*x 1.0", false).isEmpty(),
                "a zero repetition count is malformed");
        assertTrue(VaspIncarDeck.expandArray("two 1.0", false).isEmpty(),
                "any malformed token fails the WHOLE array (fail-closed)");
        assertTrue(VaspIncarDeck.expandArray("", false).isEmpty());
        // expandArray RETURNS DOUBLES always (integersOnly only VERIFIES
        // integrality); the typed Integer list is intArrayValue()'s job:
        assertEquals(Optional.of(List.of(2.0, 2.0, -1.0)),
                VaspIncarDeck.expandArray("2*2 -1", true));
        assertTrue(VaspIncarDeck.expandArray("2*2.5", true).isEmpty(),
                "integer arrays reject non-integral tokens");
        VaspIncarDeck ldaul = VaspIncarDeck.parse(
                "LDAUL = 2*2 -1\n").getValue().orElseThrow();
        assertEquals(Optional.of(List.of(2, 2, -1)),
                ldaul.first("LDAUL").orElseThrow().intArrayValue(),
                "the typed integer-array view pins real integers");
        assertEquals(Optional.of(List.of(10.0)),
                VaspIncarDeck.expandArray("1D1", false),
                "Fortran D exponents parse (VASP is Fortran)");
    }

    @Test
    void bareTextIsCountedNeverReinterpreted() {
        VaspIncarDeck deck = VaspIncarDeck.parse(
                "start parameters for this run\n"
                + "ENCUT = 400\n"
                + "another bare line\n").getValue().orElseThrow();
        assertEquals(1, deck.getStatements().size());
        assertEquals(2, deck.getIgnoredLineCount(),
                "the wiki: VASP ignores text outside the statement format");
        assertEquals(3, deck.getTotalLines());
    }

    @Test
    void boundsRefuseCleanlyTyped() {
        assertEquals("VASP_INCAR_INPUT",
                VaspIncarDeck.parse(null).getCode());
        assertEquals("VASP_INCAR_EMPTY",
                VaspIncarDeck.parse("   \n  \n").getCode());
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 66 * 1024; i++) {
            huge.append("ENCUT = 400         \n"); // > 1 MiB total
        }
        OperationResult<VaspIncarDeck> big = VaspIncarDeck.parse(
                huge.toString());
        assertTrue(big.isSuccess() || "VASP_INCAR_INPUT".equals(big.getCode()),
                "bounded reads refuse or parse, never hang: " + big.getCode());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 17000; i++) {
            many.append("TAG").append(i).append(" = 1\n");
        }
        OperationResult<VaspIncarDeck> crowded = VaspIncarDeck.parse(
                many.toString());
        assertEquals("VASP_INCAR_INPUT", crowded.getCode(),
                "16384 statements is the deliberate audit bound");
        assertTrue(crowded.getMessage().contains("16384"));
    }
}
