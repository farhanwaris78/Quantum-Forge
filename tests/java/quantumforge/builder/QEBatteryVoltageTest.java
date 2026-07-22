package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class QEBatteryVoltageTest {

    @Test
    void classicThreePhaseHullYieldsExactPlateaus() {
        List<QEHullThermodynamics.CompetingPhase> phases = List.of(
                new QEHullThermodynamics.CompetingPhase("A", 0.0, 0.0),
                new QEHullThermodynamics.CompetingPhase("AB", 0.5, -1.0),
                new QEHullThermodynamics.CompetingPhase("B", 1.0, 0.0));
        OperationResult<QEBatteryVoltage.VoltageProfile> result = QEBatteryVoltage.build(phases, 1.0);
        assertTrue(result.isSuccess(), result.toString());
        QEBatteryVoltage.VoltageProfile profile = result.getValue().orElseThrow();

        assertEquals(3, profile.getVertices().size());
        assertEquals(2, profile.getPlateaus().size());
        // A -> AB: V = -(-1 - 0)/0.5 = 2.0 V.
        assertEquals(2.0, profile.getPlateaus().get(0).getVoltageV(), 1.0e-12);
        assertEquals("AB", profile.getPlateaus().get(0).getRight().getFormula());
        // AB -> B: V = -(0 - (-1))/0.5 = -2.0 V, kept as-is with a warning note.
        assertEquals(-2.0, profile.getPlateaus().get(1).getVoltageV(), 1.0e-12);
        assertTrue(profile.getNotes().stream().anyMatch(n -> n.contains("negative")),
                profile.getNotes().toString());

        // z = 2 halves the positive plateau.
        QEBatteryVoltage.VoltageProfile divalent =
                QEBatteryVoltage.build(phases, 2.0).getValue().orElseThrow();
        assertEquals(1.0, divalent.getPlateaus().get(0).getVoltageV(), 1.0e-12);
    }

    @Test
    void metastablePhaseNeverProducesAPlateau() {
        List<QEHullThermodynamics.CompetingPhase> phases = List.of(
                new QEHullThermodynamics.CompetingPhase("A", 0.0, 0.0),
                new QEHullThermodynamics.CompetingPhase("AB", 0.5, -1.0),
                new QEHullThermodynamics.CompetingPhase("AB2_meta", 0.6667, -0.1),
                new QEHullThermodynamics.CompetingPhase("B", 1.0, 0.0));
        QEBatteryVoltage.VoltageProfile profile =
                QEBatteryVoltage.build(phases, 1.0).getValue().orElseThrow();
        assertEquals(3, profile.getVertices().size(), "The metastable phase must drop off the hull");
        assertTrue(profile.getVertices().stream().noneMatch(v -> v.getFormula().equals("AB2_meta")));
        assertEquals(2, profile.getPlateaus().size());
    }

    @Test
    void nonZeroReferenceFormationEnergyIsSubtractedAndFlagged() {
        List<QEHullThermodynamics.CompetingPhase> phases = List.of(
                new QEHullThermodynamics.CompetingPhase("A", 0.0, 0.0),
                new QEHullThermodynamics.CompetingPhase("AB", 0.5, -1.0),
                new QEHullThermodynamics.CompetingPhase("B", 1.0, 0.05));
        QEBatteryVoltage.VoltageProfile profile =
                QEBatteryVoltage.build(phases, 1.0).getValue().orElseThrow();
        // AB -> B: -((0.05+1) - 0.5*0.05)/0.5 = -2.05.
        assertEquals(-2.05, profile.getPlateaus().get(1).getVoltageV(), 1.0e-9);
        assertTrue(profile.getNotes().stream().anyMatch(n -> n.contains("mu_B")),
                profile.getNotes().toString());
    }

    @Test
    void failsClosedOnBadInputs() {
        List<QEHullThermodynamics.CompetingPhase> minimal = List.of(
                new QEHullThermodynamics.CompetingPhase("A", 0.0, 0.0),
                new QEHullThermodynamics.CompetingPhase("AB", 0.5, -1.0),
                new QEHullThermodynamics.CompetingPhase("B", 1.0, 0.0));
        assertFalse(QEBatteryVoltage.build(minimal, 0.0).isSuccess(), "z = 0 must be rejected");
        assertFalse(QEBatteryVoltage.build(minimal.subList(0, 2), 1.0).isSuccess(),
                "Fewer than three phases cannot build a hull profile");
        assertFalse(QEBatteryVoltage.build(List.of(
                new QEHullThermodynamics.CompetingPhase("AB", 0.5, -1.0),
                new QEHullThermodynamics.CompetingPhase("AB2", 0.75, -0.5),
                new QEHullThermodynamics.CompetingPhase("B", 1.0, 0.0)), 1.0).isSuccess(),
                "A missing host endmember must be rejected");
        // Fractions outside [0,1] are refused at the model boundary itself: the
        // CompetingPhase constructor throws rather than silently clamping, so no
        // out-of-range phase can ever reach build().
        assertThrows(IllegalArgumentException.class,
                () -> new QEHullThermodynamics.CompetingPhase("X", 1.5, -1.0),
                "Fractions outside [0,1] must be rejected");
    }
}
