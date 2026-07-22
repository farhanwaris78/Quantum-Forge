/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.com.math.QEUnits.Conversion;
import quantumforge.operation.OperationResult;

class QEUnitsTest {

    @Test
    void pinnedConstantsMatchTheStatedProvenance() {
        assertEquals(13.605693122994, QEUnits.EV_PER_RY, 0.0,
                "CODATA-2018, exactly the pinned digits");
        assertEquals(27.211386245988, QEUnits.EV_PER_HA, 0.0);
        assertEquals(96.48533212331002, QEUnits.KJMOL_PER_EV, 0.0, "SI-exact e*N_A/1000");
        assertEquals(1.2398419843320026e-4, QEUnits.EV_PER_WAVENUMBER, 0.0, "SI-exact h*c/e");
        assertEquals(4.135667696923859e-3, QEUnits.EV_PER_THZ, 0.0, "SI-exact h*1e12/e");
        assertEquals(0.529177210903, QEUnits.ANG_PER_BOHR, 0.0,
                "same digits the cube reader pins");
    }

    @Test
    void sameDomainConversionsAreExactFactors() {
        Conversion haToEv = QEUnits.convert(1.0, "ha", "ev").getValue().orElseThrow();
        assertEquals(27.211386245988, haToEv.getValueTo(), 1e-12);
        assertFalse(haToEv.isSpectroscopicBridge());

        Conversion ryToMev = QEUnits.convert(1.0, "ry", "mev").getValue().orElseThrow();
        assertEquals(13605.693122994, ryToMev.getValueTo(), 1e-9);

        Conversion evToKjmol = QEUnits.convert(27.2, "ev", "kJ/mol")
                .getValue().orElseThrow();
        assertEquals(2624.401033754032, evToKjmol.getValueTo(), 1e-6);
        assertEquals("kJ/mol", evToKjmol.getToUnit(), "alias canonicalised");

        Conversion kbarToGpa = QEUnits.convert(250.0, "kbar", "gpa")
                .getValue().orElseThrow();
        assertEquals(25.0, kbarToGpa.getValueTo(), 1e-12,
                "250 kbar = 25 GPa (IEEE 0.1 is not bit-exact)");

        Conversion angToBohr = QEUnits.convert(5.43, "Angstrom", "bohr")
                .getValue().orElseThrow();
        assertEquals(10.261212856717933, angToBohr.getValueTo(), 1e-9);
    }

    @Test
    void spectroscopicBridgeIsFlaggedNotHidden() {
        Conversion thzToCm1 = QEUnits.convert(1.0, "THz", "cm-1")
                .getValue().orElseThrow();
        assertEquals(33.35640951981521, thzToCm1.getValueTo(), 1e-9);
        assertFalse(thzToCm1.isSpectroscopicBridge(),
                "cm-1 and THz share the spectroscopic domain");

        Conversion cm1ToMev = QEUnits.convert(300.0, "wavenumber", "meV")
                .getValue().orElseThrow();
        assertEquals(37.195259529960076, cm1ToMev.getValueTo(), 1e-6);
        assertTrue(cm1ToMev.isSpectroscopicBridge(),
                "crossing into the energy domain must be stated");
    }

    @Test
    void roundTripsHoldAtMachinePrecision() {
        String[][] pairs = {{"ev", "ha"}, {"ev", "kjmol"}, {"ry", "cm-1"},
                {"bohr", "nm"}, {"kbar", "gpa"}, {"thz", "mev"}};
        for (String[] pair : pairs) {
            double forward = QEUnits.convert(1.234567, pair[0], pair[1])
                    .getValue().orElseThrow().getValueTo();
            double back = QEUnits.convert(forward, pair[1], pair[0])
                    .getValue().orElseThrow().getValueTo();
            assertEquals(1.234567, back, 1.234567e-15,
                    "round-trip " + pair[0] + "->" + pair[1] + "->" + pair[0]);
        }
    }

    @Test
    void unknownAndIncompatibleUnitsFailClosed() {
        assertEquals("UNIT_UNKNOWN", QEUnits.convert(1.0, "furlong", "ev").getCode());
        assertEquals("UNIT_UNKNOWN", QEUnits.convert(1.0, "ev", "parsec").getCode());
        OperationResult<Conversion> energyToLength = QEUnits.convert(1.0, "ev", "bohr");
        assertEquals("UNIT_DOMAIN", energyToLength.getCode(),
                "energy and length never mix silently");
        assertEquals("UNIT_DOMAIN", QEUnits.convert(1.0, "gpa", "ry").getCode());
        assertEquals("UNIT_SYNTAX", QEUnits.convert(Double.NaN, "ev", "ry").getCode());
        assertEquals("UNIT_SYNTAX", QEUnits.convert(Double.POSITIVE_INFINITY, "ev", "ha").getCode(),
                "non-finite input is refused before arithmetic");
        assertTrue(QEUnits.convert(1e308, "ha", "ev").getCode().equals("UNIT_SYNTAX"),
                "overflow is refused, not reported as Infinity");
        assertTrue(QEUnits.listUnitTokens().contains("ry")
                && QEUnits.listUnitTokens().contains("kbar"),
                "honest curated registry listing");
    }

    @Test
    void aliasingIsDocumentedAndDeterministic() {
        assertTrue(QEUnits.findUnit("Rydberg").isPresent());
        assertTrue(QEUnits.findUnit(" cm^-1 ").isPresent());
        assertTrue(QEUnits.findUnit("HARTREE").isPresent());
        assertTrue(QEUnits.findUnit("KJ/MOL").isPresent());
        assertTrue(QEUnits.findUnit("Angstrom").isPresent());
        assertTrue(QEUnits.findUnit("made-up").isEmpty());
    }
}
