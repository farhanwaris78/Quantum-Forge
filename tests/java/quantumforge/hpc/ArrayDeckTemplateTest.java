/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Batch-137 (#100 deck-templating slice): the one-line assignment grammar and
 * the exact per-task rendering, including the refusal points that keep a
 * template from sweeping the wrong thing silently.
 */
class ArrayDeckTemplateTest {

    private static final String DECK = """
            &CONTROL
                calculation = 'scf'
                prefix = 'si'
            /
            &SYSTEM
                ibrav = 2
                celldm(1) = 10.2
                nat = 2
                ntyp = 1
                ecutwfc = 30.0,  ! wavefunction cutoff (Ry)
            /
            &ELECTRONS
            /
            ATOMIC_SPECIES
            Si 28.0855 Si.pbe-n.UPF
            ATOMIC_POSITIONS alat
            Si 0.00 0.00 0.00
            Si 0.25 0.25 0.25
            K_POINTS gamma
            """;

    private static ArraySweepPlanner.SweepPlan sweep() {
        return ArraySweepPlanner.plan("ecutwfc", 30.0, 10.0, 3, "si-cut")
                .getValue().orElseThrow();
    }

    @Test
    void oneLineSubstitutionPreservesSurroundingTextExactly() {
        OperationResult<ArrayDeckTemplate.DeckTemplate> validated =
                ArrayDeckTemplate.validate(sweep(), DECK);
        assertTrue(validated.isSuccess(), validated.getMessage());
        ArrayDeckTemplate.DeckTemplate template = validated.getValue().orElseThrow();
        assertEquals("DECK_TEMPLATE_OK", validated.getCode());
        assertEquals("ecutwfc = 30.0,  ! wavefunction cutoff (Ry)",
                template.getLineBefore(),
                "the found line is trimmed but otherwise untouched");
        assertEquals("ecutwfc = " + ArrayDeckTemplate.PLACEHOLDER
                + ",  ! wavefunction cutoff (Ry)", template.getLineAfter(),
                "the comma and the inline comment survive the substitution");
        String task1 = template.renderTaskDeck(1);
        assertTrue(task1.contains("ecutwfc = 30.0,  ! wavefunction cutoff (Ry)"), task1);
        String task3 = template.renderTaskDeck(3);
        assertTrue(task3.contains("ecutwfc = 50.0,  ! wavefunction cutoff (Ry)"), task3);
        assertFalse(task3.contains(ArrayDeckTemplate.PLACEHOLDER),
                "no placeholder may survive a per-task render");
        // Everything outside the swept line is byte-identical.
        assertEquals(DECK.replace("ecutwfc = 30.0,", "ecutwfc = 50.0,"), task3,
                "exactly one span of one line may change");
        assertEquals("ecutwfc = 40.0,  ! wavefunction cutoff (Ry)",
                template.renderTaskLine(2));
    }

    @Test
    void exactSingleRoundingValuesMatchTheManifest() {
        ArraySweepPlanner.SweepPlan plan = ArraySweepPlanner.plan(
                "ecutwfc", 0.0, 0.1, 10, "frac").getValue().orElseThrow();
        String deck = "&SYSTEM\n    ecutwfc = 0.0\n/\n";
        ArrayDeckTemplate.DeckTemplate template = ArrayDeckTemplate.validate(plan, deck)
                .getValue().orElseThrow();
        assertTrue(template.renderTaskDeck(10).contains("ecutwfc = 1.0\n"),
                "the 10th 0.1-step from 0 renders exactly 1.0 (single rounding; "
                        + "accumulated addition would print 0.9999999999999999): "
                        + template.renderTaskDeck(10));
    }

    @Test
    void refusalsAreFailClosedAndDiagnostic() {
        assertEquals("DECK_TEMPLATE", ArrayDeckTemplate.validate(sweep(), "  ").getCode(),
                "an empty deck cannot be swept");
        String oversized = "ecutwfc = 30.0\n" + " ".repeat(
                ArrayDeckTemplate.MAX_TEMPLATE_CHARS);
        assertEquals("DECK_TEMPLATE", ArrayDeckTemplate.validate(sweep(), oversized).getCode(),
                "the owned size bound protects the review artifact");
        assertEquals("DECK_PLACEHOLDER", ArrayDeckTemplate.validate(sweep(),
                "ecutwfc = " + ArrayDeckTemplate.PLACEHOLDER + "\n").getCode(),
                "our own token must never pre-exist in a template");
        assertEquals("DECK_KEYWORD", ArrayDeckTemplate.validate(sweep(),
                "&SYSTEM\n    celldm(1) = 10.2\n/\n").getCode(),
                "a deck without the keyword would run the SAME deck N times - refused");
        assertEquals("DECK_KEYWORD", ArrayDeckTemplate.validate(sweep(),
                "! ecutwfc = 60.0  (disabled for the vacuum test)\n").getCode(),
                "comment-only occurrences do not substitute - refused, not uncommented for you");
        assertEquals("DECK_KEYWORD", ArrayDeckTemplate.validate(sweep(),
                "ecutwfc = 30.0\necutwfc = 50.0\n").getCode(),
                "two assignments would pick an arbitrary sweep point - refused");
        assertEquals("DECK_VALUE", ArrayDeckTemplate.validate(sweep(),
                "ecutwfc = ,  ! set later\n").getCode(),
                "there is no value to stand in for");
    }

    @Test
    void indexedKeywordsMatchOnceAndRefuseTheirDuplicates() {
        ArraySweepPlanner.SweepPlan hubbard = ArraySweepPlanner.plan(
                "Hubbard_U(1)", 2.0, 1.0, 2, "hub").getValue().orElseThrow();
        String deck = "&SYSTEM\n    lda_plus_u = .true.\n    Hubbard_U(1) = 4.0\n/\n";
        OperationResult<ArrayDeckTemplate.DeckTemplate> validated =
                ArrayDeckTemplate.validate(hubbard, deck);
        assertTrue(validated.isSuccess(), validated.getMessage());
        assertTrue(validated.getValue().orElseThrow().renderTaskDeck(2)
                .contains("Hubbard_U(1) = 3.0\n"));
        assertEquals("DECK_KEYWORD", ArrayDeckTemplate.validate(hubbard,
                "&SYSTEM\n    HUBBARD_U(1) = 2.0\n    hubbard_u(1) = 5.0\n/\n").getCode(),
                "QE keywords are case-insensitive - a differently-cased second assignment "
                        + "is still a second assignment");
    }

    @Test
    void outOfRangeTaskRefusesLoudly() {
        ArrayDeckTemplate.DeckTemplate template = ArrayDeckTemplate.validate(sweep(), DECK)
                .getValue().orElseThrow();
        IndexOutOfBoundsException low = assertThrows(IndexOutOfBoundsException.class,
                () -> template.renderTaskDeck(0));
        assertTrue(low.getMessage().contains("outside 1..3"), low.getMessage());
        assertThrows(IndexOutOfBoundsException.class, () -> template.renderTaskDeck(4));
    }

    @Test
    void nullTemplateRefusesAndNullPlanExplodesPolitely() {
        assertEquals("DECK_TEMPLATE", ArrayDeckTemplate.validate(sweep(), null).getCode());
        assertThrows(NullPointerException.class,
                () -> ArrayDeckTemplate.validate(null, DECK));
    }
}
